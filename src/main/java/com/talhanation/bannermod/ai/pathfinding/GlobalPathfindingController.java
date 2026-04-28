package com.talhanation.bannermod.ai.pathfinding;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.util.AdaptiveRuntimeBudgets;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

public final class GlobalPathfindingController {
    private static final double FLOW_FIELD_JOIN_DISTANCE = 3.5D;
    private static final ProfilingCounters PROFILING = new ProfilingCounters();
    private static final Map<Long, DeferredPathTicket> DEFERRED_TICKETS = new LinkedHashMap<>();
    @Nullable
    private static volatile ReusablePathCandidate reusablePathCandidate;
    @Nullable
    private static volatile FlowFieldPrototypeCandidate flowFieldPrototypeCandidate;
    private static volatile long nextDeferredTicketId = 1L;
    @Nullable
    private static volatile BudgetSettings budgetSettingsOverride;

    private GlobalPathfindingController() {
    }

    public static <T> PathRequestResult<T> requestPath(PathRequest request, @Nullable DeferredPathTicket deferredTicket,
                                                       Supplier<T> requestSupplier) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requestSupplier, "requestSupplier");
        PROFILING.record(request);

        BudgetSettings settings = currentBudgetSettings();
        pruneExpiredDeferred(request.requestGameTime(), settings.maxDeferredTicks());
        PROFILING.beginTickWindow(request.requestGameTime(), settings.requestBudgetPerTick(), DEFERRED_TICKETS.size());

        if (deferredTicket != null && !DEFERRED_TICKETS.containsKey(deferredTicket.id())) {
            RuntimeProfilingCounters.increment("pathfinding.request.status.dropped");
            return PathRequestResult.dropped(DeferredDropReason.INVALIDATED);
        }

        if (!PROFILING.tryConsumeBudget()) {
            if (deferredTicket != null) {
                PROFILING.updateQueueDepth(DEFERRED_TICKETS.size());
                RuntimeProfilingCounters.increment("pathfinding.request.status.deferred");
                return PathRequestResult.deferred(deferredTicket);
            }
            if (settings.maxDeferredBacklog() <= 0 || DEFERRED_TICKETS.size() >= settings.maxDeferredBacklog()) {
                PROFILING.recordDeferredDrop(DeferredDropReason.BACKLOG_CAP, DEFERRED_TICKETS.size());
                RuntimeProfilingCounters.increment("pathfinding.request.status.dropped");
                return PathRequestResult.dropped(DeferredDropReason.BACKLOG_CAP);
            }

            DeferredPathTicket newTicket = new DeferredPathTicket(nextDeferredTicketId++, request.requestGameTime());
            DEFERRED_TICKETS.put(newTicket.id(), newTicket);
            PROFILING.recordDeferred(DEFERRED_TICKETS.size());
            RuntimeProfilingCounters.increment("pathfinding.request.status.deferred");
            return PathRequestResult.deferred(newTicket);
        }

        Path flowFieldPath = tryFlowFieldPrototype(request);
        if (flowFieldPath != null) {
            if (deferredTicket != null) {
                DEFERRED_TICKETS.remove(deferredTicket.id());
                PROFILING.recordDeferredResume(request.requestGameTime() - deferredTicket.firstDeferredGameTime(), DEFERRED_TICKETS.size());
            }
            @SuppressWarnings("unchecked")
            T castResult = (T) flowFieldPath;
            RuntimeProfilingCounters.increment("pathfinding.request.status.executed");
            return PathRequestResult.executed(castResult);
        }

        Path reusedPath = tryReuse(request);
        if (reusedPath != null) {
            if (deferredTicket != null) {
                DEFERRED_TICKETS.remove(deferredTicket.id());
                PROFILING.recordDeferredResume(request.requestGameTime() - deferredTicket.firstDeferredGameTime(), DEFERRED_TICKETS.size());
            }
            @SuppressWarnings("unchecked")
            T castResult = (T) reusedPath;
            RuntimeProfilingCounters.increment("pathfinding.request.status.executed");
            return PathRequestResult.executed(castResult);
        }

        T result = requestSupplier.get();
        if (deferredTicket != null) {
            DEFERRED_TICKETS.remove(deferredTicket.id());
            PROFILING.recordDeferredResume(request.requestGameTime() - deferredTicket.firstDeferredGameTime(), DEFERRED_TICKETS.size());
        }
        rememberCandidate(request, result);
        RuntimeProfilingCounters.increment("pathfinding.request.status.executed");
        return PathRequestResult.executed(result);
    }

    public static void discardDeferred(DeferredPathTicket deferredTicket, long requestGameTime, DeferredDropReason reason) {
        Objects.requireNonNull(deferredTicket, "deferredTicket");
        Objects.requireNonNull(reason, "reason");

        if (reason == DeferredDropReason.BACKLOG_CAP) {
            throw new IllegalArgumentException("Backlog-cap drops cannot target an existing deferred ticket");
        }

        if (DEFERRED_TICKETS.remove(deferredTicket.id()) != null) {
            PROFILING.beginTickWindow(requestGameTime, currentBudgetSettings().requestBudgetPerTick(), DEFERRED_TICKETS.size());
            PROFILING.recordDeferredDrop(reason, DEFERRED_TICKETS.size());
        }
    }

    public static void resetProfiling() {
        PROFILING.reset();
        DEFERRED_TICKETS.clear();
        reusablePathCandidate = null;
        flowFieldPrototypeCandidate = null;
        nextDeferredTicketId = 1L;
    }

    public static ProfilingSnapshot profilingSnapshot() {
        return PROFILING.snapshot();
    }

    public enum RequestKind {
        BLOCK_TARGETS,
        ENTITY_TARGET
    }

    public enum RequestStatus {
        EXECUTED,
        DEFERRED,
        DROPPED
    }

    public enum DeferredDropReason {
        BACKLOG_CAP,
        MAX_AGE,
        INVALIDATED
    }

    public record DeferredPathTicket(long id, long firstDeferredGameTime) {
    }

    public record FlowFieldPrototypeRequest(boolean enabled, boolean eligible, int cohortSize) {
        public FlowFieldPrototypeRequest {
            if (cohortSize < 0) {
                throw new IllegalArgumentException("cohortSize must be non-negative");
            }
        }

        public boolean shouldAttempt() {
            return enabled && eligible;
        }
    }

    public record PathRequestResult<T>(RequestStatus status, @Nullable T result,
                                       @Nullable DeferredPathTicket deferredTicket,
                                       @Nullable DeferredDropReason dropReason) {
        public PathRequestResult {
            Objects.requireNonNull(status, "status");
            if (status == RequestStatus.DEFERRED && deferredTicket == null) {
                throw new IllegalArgumentException("Deferred result requires a deferred ticket");
            }
            if (status == RequestStatus.DROPPED && dropReason == null) {
                throw new IllegalArgumentException("Dropped result requires a drop reason");
            }
        }

        public static <T> PathRequestResult<T> executed(@Nullable T result) {
            return new PathRequestResult<>(RequestStatus.EXECUTED, result, null, null);
        }

        public static <T> PathRequestResult<T> deferred(DeferredPathTicket deferredTicket) {
            return new PathRequestResult<>(RequestStatus.DEFERRED, null, deferredTicket, null);
        }

        public static <T> PathRequestResult<T> dropped(DeferredDropReason dropReason) {
            return new PathRequestResult<>(RequestStatus.DROPPED, null, null, dropReason);
        }
    }

    public record PathRequest(RequestKind requestKind, boolean asyncPathfindingEnabled, int targetCount, long requestGameTime,
                              @Nullable ReuseContext reuseContext, @Nullable FlowFieldPrototypeRequest flowFieldPrototypeRequest) {
        public PathRequest(RequestKind requestKind, boolean asyncPathfindingEnabled, int targetCount, long requestGameTime) {
            this(requestKind, asyncPathfindingEnabled, targetCount, requestGameTime, null, null);
        }

        public PathRequest(RequestKind requestKind, boolean asyncPathfindingEnabled, int targetCount, long requestGameTime,
                           @Nullable ReuseContext reuseContext) {
            this(requestKind, asyncPathfindingEnabled, targetCount, requestGameTime, reuseContext, null);
        }

        public PathRequest {
            Objects.requireNonNull(requestKind, "requestKind");
            if (targetCount < 0) {
                throw new IllegalArgumentException("targetCount must be non-negative");
            }
        }

        public boolean reuseRequested() {
            return reuseContext != null;
        }

        public boolean flowFieldEligible() {
            return flowFieldPrototypeRequest != null && flowFieldPrototypeRequest.eligible();
        }

        public boolean flowFieldAttemptRequested() {
            return flowFieldPrototypeRequest != null && flowFieldPrototypeRequest.shouldAttempt();
        }
    }

    public record ReuseContext(BlockPos requesterPos, BlockPos primaryTargetPos, long requestGameTime,
                               int maxRequesterDistance, int maxTargetDistance, int maxAgeTicks) {
        public ReuseContext {
            Objects.requireNonNull(requesterPos, "requesterPos");
            Objects.requireNonNull(primaryTargetPos, "primaryTargetPos");
            if (maxRequesterDistance < 0) {
                throw new IllegalArgumentException("maxRequesterDistance must be non-negative");
            }
            if (maxTargetDistance < 0) {
                throw new IllegalArgumentException("maxTargetDistance must be non-negative");
            }
            if (maxAgeTicks < 0) {
                throw new IllegalArgumentException("maxAgeTicks must be non-negative");
            }
        }

        private boolean isSpatiallyCompatibleWith(ReuseContext candidate) {
            return requesterPos.closerThan(candidate.requesterPos, (double) maxRequesterDistance + 1.0D)
                    && primaryTargetPos.closerThan(candidate.primaryTargetPos, (double) maxTargetDistance + 1.0D);
        }

        private boolean isFreshComparedTo(ReuseContext candidate) {
            return Math.abs(requestGameTime - candidate.requestGameTime) <= maxAgeTicks;
        }
    }

    public record ProfilingSnapshot(
            long totalRequests,
            long blockTargetRequests,
            long entityTargetRequests,
            long asyncEnabledRequests,
            long asyncDisabledRequests,
            long targetPositionsObserved,
            long reuseAttempts,
            long reuseHits,
            long reuseMisses,
            long reuseMissesNoCandidate,
            long reuseDropsNullCandidate,
            long reuseDropsUnprocessedCandidate,
            long reuseDropsDoneCandidate,
            long reuseDropsIncompatibleCandidate,
            long reuseDropsStaleCandidate,
            long flowFieldEligibleRequests,
            long flowFieldPrototypeAttempts,
            long flowFieldPrototypeHits,
            long flowFieldPrototypeFallbacks,
            int requestBudgetPerTick,
            int budgetUsedThisTick,
            long deferredRequests,
            long deferredResumes,
            long deferredDrops,
            long deferredDropsBacklogCap,
            long deferredDropsMaxAge,
            long deferredDropsInvalidated,
            int currentDeferredQueueDepth,
            int maxDeferredQueueDepth,
            long totalDeferredLatencyTicks,
            long maxDeferredLatencyTicks
    ) {
    }

    @Nullable
    static Path tryReuseForTests(PathRequest request) {
        return tryReuse(request);
    }

    static void rememberCandidateForTests(PathRequest request, @Nullable Path path) {
        rememberCandidate(request, path);
    }

    @Nullable
    static Path tryFlowFieldPrototypeForTests(PathRequest request) {
        return tryFlowFieldPrototype(request);
    }

    static void configureBudgetForTests(int requestBudgetPerTick, int maxDeferredBacklog, int maxDeferredTicks) {
        budgetSettingsOverride = new BudgetSettings(requestBudgetPerTick, maxDeferredBacklog, maxDeferredTicks);
    }

    static void clearBudgetOverrideForTests() {
        budgetSettingsOverride = null;
    }

    private static void rememberCandidate(PathRequest request, @Nullable Object result) {
        if (!request.reuseRequested()) {
            return;
        }

        Path rememberedPath = result instanceof Path path ? path : null;
        reusablePathCandidate = new ReusablePathCandidate(request, rememberedPath);
        flowFieldPrototypeCandidate = new FlowFieldPrototypeCandidate(request, rememberedPath == null ? null : copyPath(rememberedPath));
    }

    @Nullable
    private static Path tryFlowFieldPrototype(PathRequest request) {
        if (!request.flowFieldAttemptRequested() || request.requestKind() != RequestKind.BLOCK_TARGETS || request.targetCount() != 1) {
            return null;
        }

        PROFILING.recordFlowFieldAttempt();

        FlowFieldPrototypeCandidate candidate = flowFieldPrototypeCandidate;
        if (candidate == null || !isCompatible(request, candidate.request()) || !isFresh(request, candidate.request())) {
            PROFILING.recordFlowFieldFallback();
            return null;
        }

        Path derivedPath = deriveFlowFieldPath(request, candidate.path());
        if (derivedPath == null) {
            PROFILING.recordFlowFieldFallback();
            return null;
        }

        PROFILING.recordFlowFieldHit();
        return derivedPath;
    }

    @Nullable
    private static Path tryReuse(PathRequest request) {
        if (!request.reuseRequested()) {
            return null;
        }

        PROFILING.reuseAttempts.increment();
        ReusablePathCandidate candidate = reusablePathCandidate;
        if (candidate == null) {
            PROFILING.recordReuseMiss(ReuseDropReason.NO_CANDIDATE);
            return null;
        }

        Path candidatePath = candidate.path();
        if (candidatePath == null) {
            reusablePathCandidate = null;
            PROFILING.recordReuseMiss(ReuseDropReason.NULL_CANDIDATE);
            return null;
        }

        if (candidatePath instanceof AsyncPath asyncPath && !asyncPath.isProcessed()) {
            reusablePathCandidate = null;
            PROFILING.recordReuseMiss(ReuseDropReason.UNPROCESSED_CANDIDATE);
            return null;
        }

        if (candidatePath.isDone()) {
            reusablePathCandidate = null;
            PROFILING.recordReuseMiss(ReuseDropReason.DONE_CANDIDATE);
            return null;
        }

        if (!isCompatible(request, candidate.request())) {
            reusablePathCandidate = null;
            PROFILING.recordReuseMiss(ReuseDropReason.INCOMPATIBLE_CANDIDATE);
            return null;
        }

        if (!isFresh(request, candidate.request())) {
            reusablePathCandidate = null;
            PROFILING.recordReuseMiss(ReuseDropReason.STALE_CANDIDATE);
            return null;
        }

        Path copiedPath = copyPath(candidatePath);
        if (copiedPath == null) {
            reusablePathCandidate = null;
            PROFILING.recordReuseMiss(ReuseDropReason.STALE_CANDIDATE);
            return null;
        }

        PROFILING.reuseHits.increment();
        return copiedPath;
    }

    private static boolean isCompatible(PathRequest request, PathRequest candidate) {
        if (request.requestKind() != candidate.requestKind()) {
            return false;
        }
        if (request.asyncPathfindingEnabled() != candidate.asyncPathfindingEnabled()) {
            return false;
        }
        if (request.targetCount() != candidate.targetCount()) {
            return false;
        }
        if (request.reuseContext() == null || candidate.reuseContext() == null) {
            return false;
        }
        return request.reuseContext().isSpatiallyCompatibleWith(candidate.reuseContext());
    }

    private static boolean isFresh(PathRequest request, PathRequest candidate) {
        if (request.reuseContext() == null || candidate.reuseContext() == null) {
            return false;
        }
        return request.reuseContext().isFreshComparedTo(candidate.reuseContext());
    }

    @Nullable
    private static Path copyPath(Path path) {
        int nodeCount = path.getNodeCount();
        if (nodeCount <= 0) {
            return null;
        }

        java.util.List<Node> copiedNodes = new java.util.ArrayList<>(nodeCount);
        for (int index = 0; index < nodeCount; index++) {
            Node node = path.getNode(index);
            copiedNodes.add(node.cloneAndMove(node.x, node.y, node.z));
        }

        Path copiedPath = new Path(copiedNodes, path.getTarget(), path.canReach());
        copiedPath.setNextNodeIndex(path.getNextNodeIndex());
        return copiedPath;
    }

    @Nullable
    private static Path deriveFlowFieldPath(PathRequest request, @Nullable Path candidatePath) {
        if (candidatePath == null || candidatePath.isDone() || request.reuseContext() == null) {
            return null;
        }

        int nearestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        BlockPos requesterPos = request.reuseContext().requesterPos();
        for (int index = 0; index < candidatePath.getNodeCount(); index++) {
            Node node = candidatePath.getNode(index);
            double distance = requesterPos.distSqr(new BlockPos(node.x, node.y, node.z));
            if (distance <= FLOW_FIELD_JOIN_DISTANCE * FLOW_FIELD_JOIN_DISTANCE && distance < bestDistance) {
                nearestIndex = index;
                bestDistance = distance;
            }
        }

        if (nearestIndex < 0) {
            return null;
        }

        java.util.List<Node> nodes = new java.util.ArrayList<>();
        Node firstNode = candidatePath.getNode(nearestIndex);
        if (firstNode.x != requesterPos.getX() || firstNode.y != requesterPos.getY() || firstNode.z != requesterPos.getZ()) {
            nodes.add(new Node(requesterPos.getX(), requesterPos.getY(), requesterPos.getZ()));
        }

        for (int index = nearestIndex; index < candidatePath.getNodeCount(); index++) {
            Node node = candidatePath.getNode(index);
            nodes.add(node.cloneAndMove(node.x, node.y, node.z));
        }

        if (nodes.isEmpty()) {
            return null;
        }

        return new Path(nodes, candidatePath.getTarget(), candidatePath.canReach());
    }

    private record ReusablePathCandidate(PathRequest request, @Nullable Path path) {
    }

    private record FlowFieldPrototypeCandidate(PathRequest request, @Nullable Path path) {
    }

    private record BudgetSettings(int requestBudgetPerTick, int maxDeferredBacklog, int maxDeferredTicks) {
        private BudgetSettings {
            if (requestBudgetPerTick < 0) {
                throw new IllegalArgumentException("requestBudgetPerTick must be non-negative");
            }
            if (maxDeferredBacklog < 0) {
                throw new IllegalArgumentException("maxDeferredBacklog must be non-negative");
            }
            if (maxDeferredTicks < 0) {
                throw new IllegalArgumentException("maxDeferredTicks must be non-negative");
            }
        }
    }

    private enum ReuseDropReason {
        NO_CANDIDATE,
        NULL_CANDIDATE,
        UNPROCESSED_CANDIDATE,
        DONE_CANDIDATE,
        INCOMPATIBLE_CANDIDATE,
        STALE_CANDIDATE
    }

    private static BudgetSettings currentBudgetSettings() {
        BudgetSettings override = budgetSettingsOverride;
        if (override != null) {
            return override;
        }
        return new BudgetSettings(
                AdaptiveRuntimeBudgets.intBudget(
                        "pathfinding.request_budget_per_tick",
                        RecruitsServerConfig.PathfindingRequestBudgetPerTick.get(),
                        1
                ),
                RecruitsServerConfig.PathfindingMaxDeferredBacklog.get(),
                RecruitsServerConfig.PathfindingMaxDeferredTicks.get()
        );
    }

    private static void pruneExpiredDeferred(long requestGameTime, int maxDeferredTicks) {
        java.util.Iterator<DeferredPathTicket> iterator = DEFERRED_TICKETS.values().iterator();
        while (iterator.hasNext()) {
            DeferredPathTicket ticket = iterator.next();
            if (requestGameTime - ticket.firstDeferredGameTime() > maxDeferredTicks) {
                iterator.remove();
                PROFILING.recordDeferredDrop(DeferredDropReason.MAX_AGE, DEFERRED_TICKETS.size());
            }
        }
    }

    private static final class ProfilingCounters {
        private final LongAdder totalRequests = new LongAdder();
        private final LongAdder blockTargetRequests = new LongAdder();
        private final LongAdder entityTargetRequests = new LongAdder();
        private final LongAdder asyncEnabledRequests = new LongAdder();
        private final LongAdder asyncDisabledRequests = new LongAdder();
        private final LongAdder targetPositionsObserved = new LongAdder();
        private final LongAdder reuseAttempts = new LongAdder();
        private final LongAdder reuseHits = new LongAdder();
        private final LongAdder reuseMisses = new LongAdder();
        private final LongAdder reuseMissesNoCandidate = new LongAdder();
        private final LongAdder reuseDropsNullCandidate = new LongAdder();
        private final LongAdder reuseDropsUnprocessedCandidate = new LongAdder();
        private final LongAdder reuseDropsDoneCandidate = new LongAdder();
        private final LongAdder reuseDropsIncompatibleCandidate = new LongAdder();
        private final LongAdder reuseDropsStaleCandidate = new LongAdder();
        private final LongAdder flowFieldEligibleRequests = new LongAdder();
        private final LongAdder flowFieldPrototypeAttempts = new LongAdder();
        private final LongAdder flowFieldPrototypeHits = new LongAdder();
        private final LongAdder flowFieldPrototypeFallbacks = new LongAdder();
        private final LongAdder deferredRequests = new LongAdder();
        private final LongAdder deferredResumes = new LongAdder();
        private final LongAdder deferredDrops = new LongAdder();
        private final LongAdder deferredDropsBacklogCap = new LongAdder();
        private final LongAdder deferredDropsMaxAge = new LongAdder();
        private final LongAdder deferredDropsInvalidated = new LongAdder();
        private final LongAdder totalDeferredLatencyTicks = new LongAdder();
        private final AtomicLong maxDeferredLatencyTicks = new AtomicLong();
        private volatile long currentBudgetTick = Long.MIN_VALUE;
        private volatile int requestBudgetPerTick;
        private volatile int budgetUsedThisTick;
        private volatile int currentDeferredQueueDepth;
        private volatile int maxDeferredQueueDepth;

        private void record(PathRequest request) {
            totalRequests.increment();
            targetPositionsObserved.add(request.targetCount());
            if (request.requestKind() == RequestKind.ENTITY_TARGET) {
                entityTargetRequests.increment();
            }
            else {
                blockTargetRequests.increment();
            }

            if (request.asyncPathfindingEnabled()) {
                asyncEnabledRequests.increment();
            }
            else {
                asyncDisabledRequests.increment();
            }

            if (request.flowFieldEligible()) {
                flowFieldEligibleRequests.increment();
            }
        }

        private void reset() {
            totalRequests.reset();
            blockTargetRequests.reset();
            entityTargetRequests.reset();
            asyncEnabledRequests.reset();
            asyncDisabledRequests.reset();
            targetPositionsObserved.reset();
            reuseAttempts.reset();
            reuseHits.reset();
            reuseMisses.reset();
            reuseMissesNoCandidate.reset();
            reuseDropsNullCandidate.reset();
            reuseDropsUnprocessedCandidate.reset();
            reuseDropsDoneCandidate.reset();
            reuseDropsIncompatibleCandidate.reset();
            reuseDropsStaleCandidate.reset();
            flowFieldEligibleRequests.reset();
            flowFieldPrototypeAttempts.reset();
            flowFieldPrototypeHits.reset();
            flowFieldPrototypeFallbacks.reset();
            deferredRequests.reset();
            deferredResumes.reset();
            deferredDrops.reset();
            deferredDropsBacklogCap.reset();
            deferredDropsMaxAge.reset();
            deferredDropsInvalidated.reset();
            totalDeferredLatencyTicks.reset();
            maxDeferredLatencyTicks.set(0L);
            currentBudgetTick = Long.MIN_VALUE;
            requestBudgetPerTick = 0;
            budgetUsedThisTick = 0;
            currentDeferredQueueDepth = 0;
            maxDeferredQueueDepth = 0;
        }

        private void beginTickWindow(long requestGameTime, int budgetCapacity, int queueDepth) {
            requestBudgetPerTick = budgetCapacity;
            if (currentBudgetTick != requestGameTime) {
                currentBudgetTick = requestGameTime;
                budgetUsedThisTick = 0;
            }
            updateQueueDepth(queueDepth);
        }

        private boolean tryConsumeBudget() {
            if (budgetUsedThisTick >= requestBudgetPerTick) {
                return false;
            }
            budgetUsedThisTick++;
            return true;
        }

        private void recordDeferred(int queueDepth) {
            deferredRequests.increment();
            updateQueueDepth(queueDepth);
        }

        private void recordDeferredResume(long deferredLatencyTicks, int queueDepth) {
            deferredResumes.increment();
            totalDeferredLatencyTicks.add(Math.max(0L, deferredLatencyTicks));
            maxDeferredLatencyTicks.accumulateAndGet(Math.max(0L, deferredLatencyTicks), Math::max);
            updateQueueDepth(queueDepth);
        }

        private void recordDeferredDrop(DeferredDropReason reason, int queueDepth) {
            deferredDrops.increment();
            switch (reason) {
                case BACKLOG_CAP -> deferredDropsBacklogCap.increment();
                case MAX_AGE -> deferredDropsMaxAge.increment();
                case INVALIDATED -> deferredDropsInvalidated.increment();
            }
            updateQueueDepth(queueDepth);
        }

        private void updateQueueDepth(int queueDepth) {
            currentDeferredQueueDepth = queueDepth;
            if (queueDepth > maxDeferredQueueDepth) {
                maxDeferredQueueDepth = queueDepth;
            }
        }

        private void recordReuseMiss(ReuseDropReason reason) {
            reuseMisses.increment();
            switch (reason) {
                case NO_CANDIDATE -> reuseMissesNoCandidate.increment();
                case NULL_CANDIDATE -> reuseDropsNullCandidate.increment();
                case UNPROCESSED_CANDIDATE -> reuseDropsUnprocessedCandidate.increment();
                case DONE_CANDIDATE -> reuseDropsDoneCandidate.increment();
                case INCOMPATIBLE_CANDIDATE -> reuseDropsIncompatibleCandidate.increment();
                case STALE_CANDIDATE -> reuseDropsStaleCandidate.increment();
            }
        }

        private void recordFlowFieldAttempt() {
            flowFieldPrototypeAttempts.increment();
        }

        private void recordFlowFieldHit() {
            flowFieldPrototypeHits.increment();
        }

        private void recordFlowFieldFallback() {
            flowFieldPrototypeFallbacks.increment();
        }

        private ProfilingSnapshot snapshot() {
            return new ProfilingSnapshot(
                    totalRequests.sum(),
                    blockTargetRequests.sum(),
                    entityTargetRequests.sum(),
                    asyncEnabledRequests.sum(),
                    asyncDisabledRequests.sum(),
                    targetPositionsObserved.sum(),
                    reuseAttempts.sum(),
                    reuseHits.sum(),
                    reuseMisses.sum(),
                    reuseMissesNoCandidate.sum(),
                    reuseDropsNullCandidate.sum(),
                    reuseDropsUnprocessedCandidate.sum(),
                    reuseDropsDoneCandidate.sum(),
                    reuseDropsIncompatibleCandidate.sum(),
                    reuseDropsStaleCandidate.sum(),
                    flowFieldEligibleRequests.sum(),
                    flowFieldPrototypeAttempts.sum(),
                    flowFieldPrototypeHits.sum(),
                    flowFieldPrototypeFallbacks.sum(),
                    requestBudgetPerTick,
                    budgetUsedThisTick,
                    deferredRequests.sum(),
                    deferredResumes.sum(),
                    deferredDrops.sum(),
                    deferredDropsBacklogCap.sum(),
                    deferredDropsMaxAge.sum(),
                    deferredDropsInvalidated.sum(),
                    currentDeferredQueueDepth,
                    maxDeferredQueueDepth,
                    totalDeferredLatencyTicks.sum(),
                    maxDeferredLatencyTicks.get()
            );
        }
    }
}
