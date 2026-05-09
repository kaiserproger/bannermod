package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.settlement.growth.PendingProject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementProjectRuntimeTest {

    @Test
    void nullClaimUuidReturnsEmptyWithoutTouchingQueue() {
        SettlementProjectRuntime runtime = SettlementProjectRuntime.detached();

        Optional<ProjectAssignment> assignment = runtime.tickClaim(
                null,
                null,
                List.of(ProjectTestFactory.general(40, 5)),
                new BannerModBuildAreaProjectBridge.NoopBuildAreaResolver(),
                10L
        );

        assertTrue(assignment.isEmpty());
    }

    @Test
    void buildAreaResolverFallsBackToNoopWhenLevelOrClaimManagerIsMissing() {
        BannerModBuildAreaProjectBridge.BuildAreaResolver resolver = SettlementProjectRuntime.buildAreaResolver(null);

        assertInstanceOf(BannerModBuildAreaProjectBridge.NoopBuildAreaResolver.class, resolver);
    }

    @Test
    void nullResolverAndNullGrowthQueueUseSafeFallbacks() {
        SettlementProjectRuntime runtime = SettlementProjectRuntime.detached();
        UUID claim = UUID.randomUUID();

        Optional<ProjectAssignment> assignment = runtime.tickClaim(null, claim, null, null, 20L);

        assertTrue(assignment.isEmpty());
        assertTrue(runtime.snapshot(claim).isEmpty());
    }

    @Test
    void assignmentLookupHandlesNullAndUnknownBuildAreas() {
        SettlementProjectRuntime runtime = SettlementProjectRuntime.detached();

        assertTrue(runtime.assignmentForBuildArea(null).isEmpty());
        assertTrue(runtime.assignmentForBuildArea(UUID.randomUUID()).isEmpty());
    }

    @Test
    void buildAreaLifecycleTransitionsToStartedAndCompletedAndStaysCompleted() {
        SettlementProjectRuntime runtime = SettlementProjectRuntime.detached();
        UUID claim = UUID.randomUUID();
        UUID buildArea = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(55, 4);
        BannerModBuildAreaProjectBridge.BuildAreaResolver resolver =
                (c, p) -> Optional.of(new BannerModBuildAreaProjectBridge.BuildAreaBinding(buildArea, true, 3));

        ProjectAssignment assigned = runtime.tickClaim(null, claim, List.of(project), resolver, 30L).orElseThrow();
        assertEquals(AssignmentPhase.SEARCHING_BUILDER, assigned.phase());
        assertSame(assigned.project(), runtime.assignmentForBuildArea(buildArea).orElseThrow().project());

        ProjectAssignment started = runtime.onBuildAreaStarted(buildArea).orElseThrow();
        assertEquals(AssignmentPhase.IN_PROGRESS, started.phase());

        ProjectAssignment completed = runtime.onBuildAreaCompleted(buildArea).orElseThrow();
        assertEquals(AssignmentPhase.COMPLETED, completed.phase());

        ProjectAssignment sticky = runtime.onBuildAreaStarted(buildArea).orElseThrow();
        assertEquals(AssignmentPhase.COMPLETED, sticky.phase());
    }

    @Test
    void buildAreaLifecycleIgnoresUnknownOrNullBuildAreas() {
        SettlementProjectRuntime runtime = SettlementProjectRuntime.detached();

        assertFalse(runtime.onBuildAreaStarted(null).isPresent());
        assertFalse(runtime.onBuildAreaCompleted(UUID.randomUUID()).isPresent());
    }

    @Test
    void snapshotReturnsDefensiveCopyOfSchedulerState() {
        SettlementProjectRuntime runtime = SettlementProjectRuntime.detached();
        UUID claim = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(80, 5);

        runtime.scheduler().submit(claim, project);
        List<PendingProject> snapshot = runtime.snapshot(claim);
        snapshot.clear();

        assertEquals(1, runtime.scheduler().pendingCount(claim));
        assertSame(project, runtime.scheduler().peek(claim).orElseThrow());
    }

    @Test
    void queuedProjectSurvivesNoopTickAndDedupesOnRetryAssignment() {
        SettlementProjectRuntime runtime = SettlementProjectRuntime.detached();
        UUID claim = UUID.randomUUID();
        UUID buildArea = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(65, 4);

        Optional<ProjectAssignment> first = runtime.tickClaim(
                null,
                claim,
                List.of(project),
                new BannerModBuildAreaProjectBridge.NoopBuildAreaResolver(),
                10L
        );
        assertTrue(first.isEmpty());
        assertEquals(1, runtime.scheduler().pendingCount(claim));
        assertSame(project, runtime.scheduler().peek(claim).orElseThrow());

        BannerModBuildAreaProjectBridge.BuildAreaResolver resolver =
                (c, p) -> Optional.of(new BannerModBuildAreaProjectBridge.BuildAreaBinding(buildArea, true, 4));

        Optional<ProjectAssignment> second = runtime.tickClaim(
                null,
                claim,
                java.util.Arrays.asList(project, null),
                resolver,
                11L
        );

        assertTrue(second.isPresent());
        assertEquals(project.projectId(), second.get().projectId());
        assertEquals(buildArea, second.get().buildAreaUuid());
        assertEquals(0, runtime.scheduler().pendingCount(claim));
    }

    @Test
    void staticConvenienceMethodsIgnoreNullInputs() {
        assertTrue(SettlementProjectRuntime.tickClaim(null, UUID.randomUUID(), List.of(ProjectTestFactory.general(20, 2))).isEmpty());

        SettlementProjectRuntime.onBuildAreaStarted(null, UUID.randomUUID());
        SettlementProjectRuntime.onBuildAreaStarted(null, null);
        SettlementProjectRuntime.onBuildAreaCompleted(null, UUID.randomUUID());
        SettlementProjectRuntime.onBuildAreaCompleted(null, null);
    }
}
