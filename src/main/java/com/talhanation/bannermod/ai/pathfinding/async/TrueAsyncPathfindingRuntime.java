package com.talhanation.bannermod.ai.pathfinding.async;

import com.talhanation.bannermod.ai.pathfinding.AsyncPathNavigation;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.util.AdaptiveRuntimeBudgets;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TrueAsyncPathfindingRuntime {
    private static final String METRICS_PREFIX = "pathfinding.true_async.runtime";
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final TrueAsyncPathfindingRuntime INSTANCE = new TrueAsyncPathfindingRuntime();

    private final RegionSnapshotBuilder snapshotBuilder = new RegionSnapshotBuilder();
    private final PathCommitter committer = new PathCommitter();
    private final Map<UUID, PendingCommitTarget> pendingTargets = new ConcurrentHashMap<>();
    private final CoarsePathCache coarsePathCache = new CoarsePathCache();

    private volatile AsyncPathScheduler scheduler;

    private TrueAsyncPathfindingRuntime() {
    }

    public static TrueAsyncPathfindingRuntime instance() {
        return INSTANCE;
    }

    public boolean enqueue(AsyncPathNavigation navigation,
                           ServerLevel level,
                           Set<BlockPos> targets,
                           int reachRange,
                           float followRange,
                           long requestEpoch,
                           PathPriority priority) {
        Objects.requireNonNull(navigation, "navigation");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(targets, "targets");
        if (targets.isEmpty()) {
            return false;
        }
        if (navigation.hasUnsupportedTrueAsyncPassability()) {
            RuntimeProfilingCounters.increment(METRICS_PREFIX + ".fallback.unsupported_passability");
            return false;
        }

        AsyncPathScheduler activeScheduler = ensureScheduler();
        if (activeScheduler == null) {
            RuntimeProfilingCounters.increment(METRICS_PREFIX + ".fallback.no_scheduler");
            return false;
        }

        long requestId = REQUEST_IDS.incrementAndGet();
        BlockPos primaryTarget = targets.iterator().next();
        PathRequestSnapshot snapshotRequest = new PathRequestSnapshot(
                navigation.getMobUuid(),
                requestId,
                requestEpoch,
                navigation.currentBlockPos(),
                List.copyOf(targets),
                0,
                followRange,
                navigation.agentWidth(),
                navigation.agentHeight(),
                navigation.stepHeight(),
                false,
                false,
                false,
                priority,
                level.getGameTime(),
                System.nanoTime() + RecruitsServerConfig.AsyncPathfindingSolveDeadlineMillis.get() * 1_000_000L
        );
        CoarsePathKey coarseKey = null;
        if (RecruitsServerConfig.AsyncPathfindingUseCoarseCache.get()) {
            coarseKey = CoarsePathKey.of(level.dimension().location().toString(), snapshotRequest.start(), primaryTarget, priority);
            PathResult cached = coarsePathCache.lookup(
                    coarseKey,
                    snapshotRequest,
                    primaryTarget,
                    level.getGameTime(),
                    RecruitsServerConfig.AsyncPathfindingCoarseCacheMaxAgeTicks.get(),
                    RecruitsServerConfig.AsyncPathfindingCoarseRefineDistance.get()
            ).orElse(null);
            if (cached != null) {
                navigation.applyTrueAsyncPathResult(cached, reachRange);
                RuntimeProfilingCounters.increment(METRICS_PREFIX + ".coarse_cache.hit");
                return true;
            }
            RuntimeProfilingCounters.increment(METRICS_PREFIX + ".coarse_cache.miss");
        }

        if (!activeScheduler.canAccept(priority)) {
            RuntimeProfilingCounters.increment(METRICS_PREFIX + ".fallback.scheduler_rejected_before_snapshot");
            return false;
        }

        SnapshotBuildResult buildResult = snapshotBuilder.build(
                level,
                snapshotRequest,
                AdaptiveRuntimeBudgets.longBudget(
                        "pathfinding.true_async.snapshot_nanos",
                        RecruitsServerConfig.AsyncPathfindingSnapshotBudgetNanos.get(),
                        Math.max(1L, RecruitsServerConfig.AsyncPathfindingSnapshotBudgetNanos.get() / 4L)
                )
        );
        RuntimeProfilingCounters.add(METRICS_PREFIX + ".snapshot.build_nanos", buildResult.buildNanos());
        if (buildResult.status() != SnapshotStatus.OK) {
            RuntimeProfilingCounters.increment(METRICS_PREFIX + ".snapshot.reject." + buildResult.status().name().toLowerCase());
            return false;
        }

        boolean accepted = activeScheduler.submit(snapshotRequest, buildResult.region(), CancellationToken.NONE);
        if (!accepted) {
            RuntimeProfilingCounters.increment(METRICS_PREFIX + ".fallback.scheduler_rejected");
            return false;
        }

        pendingTargets.put(snapshotRequest.entityUuid(), new PendingCommitTarget(navigation, requestId, reachRange, coarseKey, snapshotRequest, primaryTarget));
        RuntimeProfilingCounters.increment(METRICS_PREFIX + ".submit.accepted");
        navigation.recordSubmitAccepted();
        return true;
    }

    public void tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        AsyncPathScheduler activeScheduler = scheduler;
        if (activeScheduler == null) {
            return;
        }

        long startedAt = System.nanoTime();
        long configuredBudgetNanos = Math.max(1L, RecruitsServerConfig.AsyncPathfindingCommitBudgetNanos.get());
        long budgetNanos = AdaptiveRuntimeBudgets.longBudget(
                "pathfinding.true_async.commit_nanos",
                configuredBudgetNanos,
                Math.max(1L, configuredBudgetNanos / 4L)
        );
        while (System.nanoTime() - startedAt < budgetNanos) {
            List<PathResult> batch = activeScheduler.pollCompleted(32);
            if (batch.isEmpty()) {
                break;
            }
            committer.commit(batch, this::resolveCommitTarget, this::removeCommitTarget, batch.size());
        }
    }

    /**
     * Test-only seam. Bypasses {@code AsyncPathScheduler} so GameTests can deterministically
     * exercise the commit pipeline without depending on the async solver finishing in time
     * for a test's tick budget. Production code never calls this — only the gametest sources.
     */
    public PathCommitter.CommitSummary commitForTesting(List<PathResult> results) {
        Objects.requireNonNull(results, "results");
        return committer.commit(results, this::resolveCommitTarget, this::removeCommitTarget, results.size());
    }

    /**
     * Test-only seam. Re-registers a pending commit target without going through the scheduler,
     * so a GameTest can drive {@link #commitForTesting(List)} after the production auto-tick
     * has already polled and committed the real solver result (and therefore removed the
     * original pending entry). Production code never calls this.
     */
    public void registerPendingTargetForTesting(AsyncPathNavigation navigation,
                                                long requestId,
                                                int reachRange,
                                                BlockPos primaryTarget) {
        Objects.requireNonNull(navigation, "navigation");
        pendingTargets.put(
                navigation.getMobUuid(),
                new PendingCommitTarget(navigation, requestId, reachRange, null, null, primaryTarget)
        );
    }

    public void shutdown() {
        AsyncPathScheduler activeScheduler = scheduler;
        scheduler = null;
        pendingTargets.clear();
        if (activeScheduler != null) {
            activeScheduler.close();
        }
    }

    private PathCommitter.CommitTarget resolveCommitTarget(UUID entityUuid) {
        PendingCommitTarget target = pendingTargets.get(entityUuid);
        if (target == null) {
            return null;
        }
        if (!target.navigation().isTrueAsyncCommitTargetAlive()) {
            // Charge the discard to this navigation's per-instance counter so GameTests can
            // assert "this recruit lost a commit because its entity was gone" without racing
            // against unrelated parallel tests on the global RuntimeProfilingCounters map.
            target.navigation().recordCommitDiscardEntityGone();
            pendingTargets.remove(entityUuid, target);
            return null;
        }
        return target;
    }

    private void removeCommitTarget(UUID entityUuid, PathCommitter.CommitTarget target) {
        if (target instanceof PendingCommitTarget pendingTarget) {
            pendingTargets.remove(entityUuid, pendingTarget);
        }
    }

    private AsyncPathScheduler ensureScheduler() {
        AsyncPathScheduler local = scheduler;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            local = scheduler;
            if (local != null) {
                return local;
            }
            int workerThreads = Math.max(1, RecruitsServerConfig.AsyncPathfindingWorkerThreads.get());
            int maxQueued = Math.max(1, RecruitsServerConfig.AsyncPathfindingMaxQueuedJobs.get());
            Map<PathPriority, Integer> perPriority = new EnumMap<>(PathPriority.class);
            for (PathPriority priority : PathPriority.values()) {
                perPriority.put(priority, maxQueued);
            }
            local = new AsyncPathScheduler(new GridAStarPathSolver(), workerThreads, maxQueued, perPriority);
            scheduler = local;
            return local;
        }
    }

    private record PendingCommitTarget(AsyncPathNavigation navigation,
                                       long requestId,
                                       int reachRange,
                                       CoarsePathKey coarseKey,
                                       PathRequestSnapshot request,
                                       BlockPos target)
            implements PathCommitter.CommitTarget {
        @Override
        public long currentEpoch() {
            return navigation.currentPathEpoch();
        }

        @Override
        public boolean isAliveAndLoaded() {
            boolean alive = navigation.isTrueAsyncCommitTargetAlive();
            if (!alive) {
                // Charge the per-instance discard counter here too — covers the race window
                // between resolveCommitTarget (saw the entity alive) and PathCommitter's
                // own second isAliveAndLoaded() check, where the entity died in between.
                navigation.recordCommitDiscardEntityGone();
            }
            return alive;
        }

        @Override
        public void apply(PathResult result) {
            if (result.requestId() != requestId) {
                RuntimeProfilingCounters.increment(METRICS_PREFIX + ".commit.discard.request_mismatch");
                return;
            }
            navigation.applyTrueAsyncPathResult(result, reachRange);
            if (coarseKey != null && RecruitsServerConfig.AsyncPathfindingUseCoarseCache.get()) {
                INSTANCE.coarsePathCache.store(
                        coarseKey,
                        request,
                        result,
                        target,
                        request.createdAtGameTime(),
                        RecruitsServerConfig.AsyncPathfindingCoarseCacheMaxEntries.get()
                );
                RuntimeProfilingCounters.increment(METRICS_PREFIX + ".coarse_cache.store");
            }
        }
    }
}
