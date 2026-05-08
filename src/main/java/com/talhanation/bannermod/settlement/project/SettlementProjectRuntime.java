package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectKind;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal facade that binds one {@link SettlementProjectScheduler} and
 * one {@link BannerModBuildAreaProjectBridge} per {@link ServerLevel}.
 *
 * <p>Settlement-service code (arriving in slice D) feeds freshly scored
 * {@link PendingProject growth queues} in and receives {@link ProjectAssignment}s
 * back. Queue state is persisted through {@link SettlementProjectSavedData}.
 */
public final class SettlementProjectRuntime {

    private final SettlementProjectScheduler scheduler;
    private final BannerModBuildAreaProjectBridge bridge;
    private final Map<UUID, ProjectAssignment> assignmentsByBuildArea = new HashMap<>();

    SettlementProjectRuntime(SettlementProjectScheduler scheduler,
                                      BannerModBuildAreaProjectBridge bridge) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    /** Lazy per-level singleton for production use. */
    public static synchronized SettlementProjectRuntime forServer(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return SettlementProjectSavedData.get(level).runtime();
    }

    /** Package-private factory for tests and detached callers. */
    static SettlementProjectRuntime detached() {
        return new SettlementProjectRuntime(
                SettlementProjectScheduler.detached(),
                new BannerModBuildAreaProjectBridge()
        );
    }

    /** Public detached factory for cross-package unit tests. */
    public static SettlementProjectRuntime detachedForTests() {
        return detached();
    }

    public SettlementProjectScheduler scheduler() {
        return scheduler;
    }

    public BannerModBuildAreaProjectBridge bridge() {
        return bridge;
    }

    public static BannerModBuildAreaProjectBridge.BuildAreaResolver buildAreaResolver(ServerLevel level) {
        if (level == null || ClaimEvents.claimManager() == null) {
            return new BannerModBuildAreaProjectBridge.NoopBuildAreaResolver();
        }
        return new BannerModBuildAreaProjectBridge.ClaimManagerBuildAreaResolver(level, ClaimEvents.claimManager());
    }

    /**
     * Feed a newly scored growth queue into the scheduler and attempt to bind the head project
     * to a BuildArea. Returns the resulting {@link ProjectAssignment} when binding succeeds.
     *
     * <p>Entries already queued under {@code claimUuid} are preserved; duplicate project IDs
     * are ignored. Overflow beyond {@link SettlementProjectScheduler#PER_CLAIM_QUEUE_CAP}
     * drops silently.
     */
    public Optional<ProjectAssignment> tickClaim(
            @Nullable ServerLevel ignoredLevel,
            UUID claimUuid,
            List<PendingProject> growthQueue,
            BannerModBuildAreaProjectBridge.BuildAreaResolver resolver,
            long gameTime
    ) {
        if (claimUuid == null) {
            return Optional.empty();
        }
        List<PendingProject> safeQueue = growthQueue == null ? List.of() : growthQueue;
        for (PendingProject candidate : safeQueue) {
            if (candidate != null) {
                scheduler.submit(claimUuid, candidate);
            }
        }
        BannerModBuildAreaProjectBridge.BuildAreaResolver safeResolver = resolver == null
                ? new BannerModBuildAreaProjectBridge.NoopBuildAreaResolver()
                : resolver;
        Optional<ProjectAssignment> assignment = bridge.attemptAssignment(scheduler, claimUuid, gameTime, safeResolver);
        if (assignment.isEmpty()
                && ignoredLevel != null
                && scheduler.peek(claimUuid)
                .filter(project -> project.kind() == ProjectKind.NEW_BUILDING)
                .isPresent()
                && SettlementProjectWorldExecution.ensureExecutableTarget(
                        ignoredLevel,
                        claimUuid,
                        scheduler.peek(claimUuid).orElse(null))) {
            assignment = bridge.attemptAssignment(scheduler, claimUuid, gameTime, buildAreaResolver(ignoredLevel));
        }
        assignment.ifPresent(resolved -> assignmentsByBuildArea.put(resolved.buildAreaUuid(), resolved));
        return assignment;
    }

    /**
     * Static convenience overload matching the signature promised to downstream callers.
     * Resolves or creates the runtime for {@code level} and delegates to the instance method
     * with a live BuildArea resolver when claim state is available.
     */
    public static Optional<ProjectAssignment> tickClaim(ServerLevel level, UUID claimUuid, List<PendingProject> growthQueue) {
        if (level == null || claimUuid == null) {
            return Optional.empty();
        }
        SettlementProjectRuntime runtime = forServer(level);
        long gameTime = level.getGameTime();
        return runtime.tickClaim(level, claimUuid, growthQueue, buildAreaResolver(level), gameTime);
    }

    /** Defensive copy of the scheduler's current queue for {@code claimUuid}. */
    public List<PendingProject> snapshot(UUID claimUuid) {
        return new ArrayList<>(scheduler.snapshot(claimUuid));
    }

    public Optional<ProjectAssignment> assignmentForBuildArea(UUID buildAreaUuid) {
        if (buildAreaUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(assignmentsByBuildArea.get(buildAreaUuid));
    }

    public Optional<ProjectAssignment> onBuildAreaStarted(UUID buildAreaUuid) {
        return updateBuildAreaPhase(buildAreaUuid, AssignmentPhase.IN_PROGRESS);
    }

    public Optional<ProjectAssignment> onBuildAreaCompleted(UUID buildAreaUuid) {
        return updateBuildAreaPhase(buildAreaUuid, AssignmentPhase.COMPLETED);
    }

    public static void onBuildAreaStarted(ServerLevel level, UUID buildAreaUuid) {
        if (level != null && buildAreaUuid != null) {
            forServer(level).onBuildAreaStarted(buildAreaUuid);
        }
    }

    public static void onBuildAreaCompleted(ServerLevel level, UUID buildAreaUuid) {
        if (level != null && buildAreaUuid != null) {
            forServer(level).onBuildAreaCompleted(buildAreaUuid);
        }
    }

    private Optional<ProjectAssignment> updateBuildAreaPhase(UUID buildAreaUuid, AssignmentPhase phase) {
        ProjectAssignment current = assignmentsByBuildArea.get(buildAreaUuid);
        if (current == null) {
            return Optional.empty();
        }
        if (current.phase() == AssignmentPhase.COMPLETED) {
            return Optional.of(current);
        }
        ProjectAssignment updated = current.withPhase(phase);
        assignmentsByBuildArea.put(buildAreaUuid, updated);
        return Optional.of(updated);
    }
}
