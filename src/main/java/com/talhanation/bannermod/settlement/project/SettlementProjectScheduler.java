package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side in-memory queue of {@link PendingProject} entries, keyed by claim UUID.
 *
 * <p>Slice C-only concerns: bounded ingestion from the growth evaluator, bookkeeping,
 * hand-off to {@link BannerModBuildAreaProjectBridge}, and NBT round-trip persistence
 * through {@link SettlementProjectSavedData}.
 *
 * <p>Thread model: expected to be touched from the server thread only. No internal
 * synchronization is provided.
 */
public final class SettlementProjectScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Upper bound on queued projects per claim; extra {@link #submit} calls are rejected. */
    public static final int PER_CLAIM_QUEUE_CAP = 16;

    @Nullable
    private final ServerLevel level;

    private final Map<UUID, Deque<PendingProject>> queues = new HashMap<>();

    /** Optional record of the last cancellation per project, handy for diagnostics. */
    private final Map<UUID, ProjectCancellationReason> cancellationLog = new HashMap<>();
    private Runnable dirtyListener = () -> {
    };

    private SettlementProjectScheduler(@Nullable ServerLevel level) {
        this.level = level;
    }

    /** Production entrypoint. One scheduler per {@link ServerLevel}. */
    public static SettlementProjectScheduler forServer(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        return new SettlementProjectScheduler(level);
    }

    /** Package-private factory for unit tests that cannot instantiate a {@link ServerLevel}. */
    static SettlementProjectScheduler detached() {
        return new SettlementProjectScheduler(null);
    }

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    /** Submit a project into priority order. Overflow retains the highest-priority entries. */
    public void submit(UUID claimUuid, PendingProject project) {
        if (claimUuid == null || project == null) {
            return;
        }
        Deque<PendingProject> queue = queues.computeIfAbsent(claimUuid, k -> new ArrayDeque<>());
        for (PendingProject existing : queue) {
            if (existing.projectId().equals(project.projectId())) {
                return;
            }
        }
        List<PendingProject> ordered = new ArrayList<>(queue);
        ordered.add(project);
        ordered.sort(PROJECT_PRIORITY_ORDER);
        if (ordered.size() > PER_CLAIM_QUEUE_CAP) {
            ordered = new ArrayList<>(ordered.subList(0, PER_CLAIM_QUEUE_CAP));
        }
        if (!ordered.contains(project)) {
            // Claim heartbeats resubmit deterministic growth candidates every cycle. When the
            // queue is already capped, lower-priority retries should drop quietly instead of
            // emitting the same warning every heartbeat.
            return;
        }
        queue.clear();
        queue.addAll(ordered);
        markDirty();
    }

    /** Non-destructive look at the head of {@code claimUuid}'s queue. */
    public Optional<PendingProject> peek(UUID claimUuid) {
        Deque<PendingProject> queue = queues.get(claimUuid);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(queue.peekFirst());
    }

    /** Remove and return the head of {@code claimUuid}'s queue. */
    public Optional<PendingProject> pollNext(UUID claimUuid) {
        Deque<PendingProject> queue = queues.get(claimUuid);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        Optional<PendingProject> polled = Optional.ofNullable(queue.pollFirst());
        if (polled.isPresent()) {
            if (queue.isEmpty()) {
                queues.remove(claimUuid);
            }
            markDirty();
        }
        return polled;
    }

    /**
     * Push {@code project} back onto the head of {@code claimUuid}'s queue.
     * Used by the bridge when a resolver cannot yet bind the project to a BuildArea.
     * Respects {@link #PER_CLAIM_QUEUE_CAP}.
     */
    public void requeueFront(UUID claimUuid, PendingProject project) {
        if (claimUuid == null || project == null) {
            return;
        }
        Deque<PendingProject> queue = queues.computeIfAbsent(claimUuid, k -> new ArrayDeque<>());
        if (queue.size() >= PER_CLAIM_QUEUE_CAP) {
            reportOverflow(claimUuid, project, queue.size(), "requeueFront");
            return;
        }
        queue.addFirst(project);
        markDirty();
    }

    public int pendingCount(UUID claimUuid) {
        Deque<PendingProject> queue = queues.get(claimUuid);
        return queue == null ? 0 : queue.size();
    }

    /** Stable, defensive copy of the queue contents for UI/diagnostics. */
    public List<PendingProject> snapshot(UUID claimUuid) {
        Deque<PendingProject> queue = queues.get(claimUuid);
        if (queue == null || queue.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(queue);
    }

    /**
     * Remove the project with {@code projectId} from whichever claim holds it.
     * Records the {@code reason} in the cancellation log. No-op if the project is not queued.
     */
    public void cancel(UUID projectId, ProjectCancellationReason reason) {
        if (projectId == null) {
            return;
        }
        ProjectCancellationReason effectiveReason = reason == null ? ProjectCancellationReason.MANUAL : reason;
        for (Iterator<Map.Entry<UUID, Deque<PendingProject>>> queueIt = queues.entrySet().iterator(); queueIt.hasNext(); ) {
            Map.Entry<UUID, Deque<PendingProject>> entry = queueIt.next();
            Deque<PendingProject> queue = entry.getValue();
            Iterator<PendingProject> it = queue.iterator();
            while (it.hasNext()) {
                PendingProject project = it.next();
                if (project.projectId().equals(projectId)) {
                    it.remove();
                    if (queue.isEmpty()) {
                        queueIt.remove();
                    }
                    cancellationLog.put(projectId, effectiveReason);
                    markDirty();
                    return;
                }
            }
        }
        // Not queued but still worth remembering — a later slice may cancel an in-progress
        // assignment whose PendingProject has already been polled.
        if (cancellationLog.get(projectId) == effectiveReason) {
            return;
        }
        cancellationLog.put(projectId, effectiveReason);
        markDirty();
    }

    /** Diagnostic accessor; returns null if the project was never cancelled via this scheduler. */
    @Nullable
    public ProjectCancellationReason lastCancellationReason(UUID projectId) {
        return cancellationLog.get(projectId);
    }

    /** Drop all queues and cancellation entries; intended for world unload / reset. */
    public void reset() {
        if (queues.isEmpty() && cancellationLog.isEmpty()) {
            return;
        }
        queues.clear();
        cancellationLog.clear();
        markDirty();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag queueTags = new ListTag();
        for (Map.Entry<UUID, Deque<PendingProject>> entry : queues.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag queueTag = new CompoundTag();
            queueTag.putUUID("Claim", entry.getKey());
            ListTag projectTags = new ListTag();
            for (PendingProject project : entry.getValue()) {
                projectTags.add(project.toTag());
            }
            queueTag.put("Projects", projectTags);
            queueTags.add(queueTag);
        }
        tag.put("Queues", queueTags);

        ListTag cancellationTags = new ListTag();
        for (Map.Entry<UUID, ProjectCancellationReason> entry : cancellationLog.entrySet()) {
            CompoundTag cancellationTag = new CompoundTag();
            cancellationTag.putUUID("Project", entry.getKey());
            cancellationTag.putString("Reason", entry.getValue().name());
            cancellationTags.add(cancellationTag);
        }
        tag.put("Cancellations", cancellationTags);
        return tag;
    }

    public static SettlementProjectScheduler fromTag(CompoundTag tag) {
        SettlementProjectScheduler scheduler = detached();
        scheduler.restoreFromTag(tag);
        return scheduler;
    }

    public void restoreFromTag(CompoundTag tag) {
        Map<UUID, List<PendingProject>> beforeQueues = queueSnapshot();
        Map<UUID, ProjectCancellationReason> beforeCancellations = new HashMap<>(cancellationLog);
        queues.clear();
        cancellationLog.clear();
        if (tag != null) {
            for (Tag queueEntry : tag.getList("Queues", Tag.TAG_COMPOUND)) {
                CompoundTag queueTag = (CompoundTag) queueEntry;
                if (!queueTag.hasUUID("Claim")) {
                    continue;
                }
                UUID claimUuid = queueTag.getUUID("Claim");
                Deque<PendingProject> queue = queues.computeIfAbsent(claimUuid, ignored -> new ArrayDeque<>());
                int dropped = 0;
                for (Tag projectEntry : queueTag.getList("Projects", Tag.TAG_COMPOUND)) {
                    if (queue.size() >= PER_CLAIM_QUEUE_CAP) {
                        dropped++;
                        continue;
                    }
                    queue.addLast(PendingProject.fromTag((CompoundTag) projectEntry));
                }
                if (dropped > 0) {
                    LOGGER.warn(
                            "Settlement project scheduler dropped {} persisted queue entries for claim {} while restoring; cap={}",
                            dropped,
                            claimUuid,
                            PER_CLAIM_QUEUE_CAP
                    );
                }
                if (queue.isEmpty()) {
                    queues.remove(claimUuid);
                }
            }
            for (Tag cancellationEntry : tag.getList("Cancellations", Tag.TAG_COMPOUND)) {
                CompoundTag cancellationTag = (CompoundTag) cancellationEntry;
                if (cancellationTag.hasUUID("Project")) {
                    cancellationLog.put(
                            cancellationTag.getUUID("Project"),
                            cancellationReasonFromTagName(cancellationTag.getString("Reason"))
                    );
                }
            }
        }
        if (!beforeQueues.equals(queueSnapshot()) || !beforeCancellations.equals(cancellationLog)) {
            markDirty();
        }
    }

    private Map<UUID, List<PendingProject>> queueSnapshot() {
        Map<UUID, List<PendingProject>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Deque<PendingProject>> entry : queues.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        return snapshot;
    }

    private static ProjectCancellationReason cancellationReasonFromTagName(String name) {
        try {
            return ProjectCancellationReason.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ProjectCancellationReason.MANUAL;
        }
    }

    private static final Comparator<PendingProject> PROJECT_PRIORITY_ORDER = Comparator
            .comparingInt(PendingProject::priorityScore).reversed()
            .thenComparingLong(PendingProject::proposedAtGameTime)
            .thenComparing(project -> project.projectId().toString());

    /** Package-private accessor for the bridge and tests. Returns {@code null} in detached mode. */
    @Nullable
    ServerLevel level() {
        return level;
    }

    private void reportOverflow(UUID claimUuid, PendingProject project, int existingQueueSize, String source) {
        LOGGER.warn(
                "Settlement project scheduler rejected project {} for claim {} from {} because queue cap {} is full (existing={})",
                project.projectId(),
                claimUuid,
                source,
                PER_CLAIM_QUEUE_CAP,
                existingQueueSize
        );
    }

    private void markDirty() {
        dirtyListener.run();
    }
}
