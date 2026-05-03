package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingCategory;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectBlocker;
import com.talhanation.bannermod.settlement.growth.ProjectKind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModSettlementProjectSchedulerTest {

    @Test
    void submitThenPollRoundTripsPreservesProject() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(100, 10);

        scheduler.submit(claim, project);

        assertEquals(1, scheduler.pendingCount(claim));
        Optional<PendingProject> peeked = scheduler.peek(claim);
        assertTrue(peeked.isPresent());
        assertSame(project, peeked.get());

        Optional<PendingProject> polled = scheduler.pollNext(claim);
        assertTrue(polled.isPresent());
        assertSame(project, polled.get());
        assertEquals(0, scheduler.pendingCount(claim));
        assertTrue(scheduler.pollNext(claim).isEmpty());
    }

    @Test
    void overflowBeyondCapKeepsHighestPriorityProjects() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        int over = BannerModSettlementProjectScheduler.PER_CLAIM_QUEUE_CAP + 5;

        PendingProject[] submitted = new PendingProject[over];
        for (int i = 0; i < over; i++) {
            submitted[i] = ProjectTestFactory.general(i, 1);
            scheduler.submit(claim, submitted[i]);
        }

        assertEquals(BannerModSettlementProjectScheduler.PER_CLAIM_QUEUE_CAP,
                scheduler.pendingCount(claim),
                "queue must clamp to per-claim cap");

        List<PendingProject> kept = scheduler.snapshot(claim);
        for (int i = 0; i < kept.size(); i++) {
            assertSame(submitted[over - 1 - i], kept.get(i),
                    "overflow must retain highest priority submissions first");
        }
    }

    @Test
    void higherPrioritySubmitMovesAheadOfExistingQueue() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        PendingProject low = ProjectTestFactory.general(10, 5);
        PendingProject high = ProjectTestFactory.general(90, 5);

        scheduler.submit(claim, low);
        scheduler.submit(claim, high);

        assertSame(high, scheduler.peek(claim).orElseThrow());
        assertEquals(List.of(high, low), scheduler.snapshot(claim));
    }

    @Test
    void cancelByProjectIdRemovesFromQueue() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        PendingProject head = ProjectTestFactory.general(50, 5);
        PendingProject mid = ProjectTestFactory.general(40, 5);
        PendingProject tail = ProjectTestFactory.general(30, 5);

        scheduler.submit(claim, head);
        scheduler.submit(claim, mid);
        scheduler.submit(claim, tail);

        scheduler.cancel(mid.projectId(), ProjectCancellationReason.SUPERSEDED);

        List<PendingProject> remaining = scheduler.snapshot(claim);
        assertEquals(2, remaining.size());
        assertSame(head, remaining.get(0));
        assertSame(tail, remaining.get(1));
        assertEquals(ProjectCancellationReason.SUPERSEDED,
                scheduler.lastCancellationReason(mid.projectId()));
    }

    @Test
    void perClaimQueuesStayIsolated() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claimX = UUID.randomUUID();
        UUID claimY = UUID.randomUUID();

        PendingProject projectX = ProjectTestFactory.general(80, 5);
        PendingProject projectY = ProjectTestFactory.general(90, 5);

        scheduler.submit(claimX, projectX);
        scheduler.submit(claimY, projectY);

        assertEquals(1, scheduler.pendingCount(claimX));
        assertEquals(1, scheduler.pendingCount(claimY));

        scheduler.pollNext(claimX);
        assertEquals(0, scheduler.pendingCount(claimX));
        assertEquals(1, scheduler.pendingCount(claimY),
                "polling claim X must not drain claim Y");
        assertSame(projectY, scheduler.peek(claimY).orElseThrow());
    }

    @Test
    void snapshotReturnsStableDefensiveCopy() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        PendingProject first = ProjectTestFactory.general(10, 5);
        PendingProject second = ProjectTestFactory.general(20, 5);

        scheduler.submit(claim, first);
        scheduler.submit(claim, second);

        List<PendingProject> before = scheduler.snapshot(claim);
        assertEquals(List.of(second, first), before);

        // Mutations to the scheduler after the snapshot must not change the snapshot.
        scheduler.pollNext(claim);
        assertEquals(2, before.size(), "snapshot must be independent of later polls");
        assertSame(second, before.get(0));

        // And two successive snapshots must be independent lists with equal content.
        scheduler.submit(claim, first);
        List<PendingProject> after = scheduler.snapshot(claim);
        List<PendingProject> afterAgain = scheduler.snapshot(claim);
        assertEquals(after, afterAgain);
        assertNotSame(after, afterAgain);
    }

    @Test
    void duplicateSubmitsAreDroppedByProjectId() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        PendingProject project = new PendingProject(
                projectId,
                ProjectKind.NEW_BUILDING,
                null,
                null,
                BannerModSettlementBuildingCategory.GENERAL,
                BannerModSettlementBuildingProfileSeed.GENERAL,
                100,
                0L,
                5,
                ProjectBlocker.NONE
        );
        scheduler.submit(claim, project);
        scheduler.submit(claim, project);
        assertEquals(1, scheduler.pendingCount(claim),
                "identical projectId must not be double-queued");
    }

    @Test
    void resetDropsEverything() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        scheduler.submit(claim, ProjectTestFactory.general(10, 5));
        scheduler.cancel(UUID.randomUUID(), ProjectCancellationReason.MANUAL);

        scheduler.reset();

        assertEquals(0, scheduler.pendingCount(claim));
        assertTrue(scheduler.snapshot(claim).isEmpty());
    }

    @Test
    void pollingLastProjectRemovesNonPersistedEmptyQueue() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        AtomicInteger dirtyCount = new AtomicInteger();
        UUID claim = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(50, 5);

        scheduler.setDirtyListener(dirtyCount::incrementAndGet);
        scheduler.submit(claim, project);
        scheduler.pollNext(claim);
        dirtyCount.set(0);

        scheduler.reset();

        assertEquals(0, dirtyCount.get());
    }

    @Test
    void restoreFromTagMarksDirtyOnlyWhenPersistedSchedulerStateChanges() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        AtomicInteger dirtyCount = new AtomicInteger();
        UUID claim = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(50, 5);

        scheduler.setDirtyListener(dirtyCount::incrementAndGet);

        scheduler.restoreFromTag(new CompoundTag());
        assertEquals(0, dirtyCount.get());

        BannerModSettlementProjectScheduler source = BannerModSettlementProjectScheduler.detached();
        source.submit(claim, project);
        CompoundTag tag = source.toTag();

        scheduler.restoreFromTag(tag);
        assertEquals(1, dirtyCount.get());

        scheduler.restoreFromTag(tag);
        assertEquals(1, dirtyCount.get());
    }

    @Test
    void duplicateUnknownCancellationDoesNotDirtyAgain() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        AtomicInteger dirtyCount = new AtomicInteger();
        UUID projectId = UUID.randomUUID();

        scheduler.setDirtyListener(dirtyCount::incrementAndGet);

        scheduler.cancel(projectId, ProjectCancellationReason.MANUAL);
        scheduler.cancel(projectId, ProjectCancellationReason.MANUAL);

        assertEquals(1, dirtyCount.get());
    }

    @Test
    void nbtRoundTripRestoresQueuesAndCancellations() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        UUID claim = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        PendingProject project = new PendingProject(
                UUID.randomUUID(),
                ProjectKind.REPAIR,
                target,
                null,
                BannerModSettlementBuildingCategory.STORAGE,
                BannerModSettlementBuildingProfileSeed.STORAGE,
                1200,
                44L,
                9,
                ProjectBlocker.NO_MATERIALS
        );
        UUID cancelled = UUID.randomUUID();

        scheduler.submit(claim, project);
        scheduler.cancel(cancelled, ProjectCancellationReason.BLOCKED);

        BannerModSettlementProjectScheduler restored = BannerModSettlementProjectScheduler.fromTag(scheduler.toTag());

        assertEquals(1, restored.pendingCount(claim));
        PendingProject restoredProject = restored.peek(claim).orElseThrow();
        assertEquals(project.projectId(), restoredProject.projectId());
        assertEquals(ProjectKind.REPAIR, restoredProject.kind());
        assertEquals(target, restoredProject.targetBuildingUuid());
        assertEquals(BannerModSettlementBuildingCategory.STORAGE, restoredProject.buildingCategory());
        assertEquals(BannerModSettlementBuildingProfileSeed.STORAGE, restoredProject.profileSeed());
        assertEquals(1000, restoredProject.priorityScore());
        assertEquals(44L, restoredProject.proposedAtGameTime());
        assertEquals(9, restoredProject.estimatedTickCost());
        assertEquals(ProjectBlocker.NO_MATERIALS, restoredProject.blockerReason());
        assertEquals(ProjectCancellationReason.BLOCKED, restored.lastCancellationReason(cancelled));
    }

    @Test
    void savedDataRoundTripRestoresRuntimeQueue() {
        BannerModSettlementProjectSavedData source = new BannerModSettlementProjectSavedData();
        UUID claim = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(75, 6);

        source.runtime().scheduler().submit(claim, project);

        BannerModSettlementProjectSavedData restored = BannerModSettlementProjectSavedData.load(source.save(new CompoundTag(), null), null);

        assertEquals(1, restored.runtime().scheduler().pendingCount(claim));
        assertEquals(project.projectId(), restored.runtime().scheduler().peek(claim).orElseThrow().projectId());
    }

    @Test
    void dirtyListenerRunsOnlyForEffectiveMutations() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        AtomicInteger dirtyCount = new AtomicInteger();
        UUID claim = UUID.randomUUID();
        PendingProject project = ProjectTestFactory.general(50, 5);

        scheduler.setDirtyListener(dirtyCount::incrementAndGet);
        scheduler.submit(claim, project);
        scheduler.submit(claim, project);
        scheduler.peek(claim);
        scheduler.pollNext(claim);
        scheduler.pollNext(claim);
        scheduler.cancel(UUID.randomUUID(), ProjectCancellationReason.MANUAL);

        assertEquals(3, dirtyCount.get());
    }

    @Test
    void forServerRejectsNullLevel() {
        assertThrows(IllegalArgumentException.class, () -> BannerModSettlementProjectScheduler.forServer(null));
    }

    @Test
    void requeueFrontPrependsProjectAndMarksDirty() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        AtomicInteger dirtyCount = new AtomicInteger();
        UUID claim = UUID.randomUUID();
        PendingProject queued = ProjectTestFactory.general(50, 5);
        PendingProject urgentRetry = ProjectTestFactory.general(10, 1);

        scheduler.submit(claim, queued);
        scheduler.setDirtyListener(dirtyCount::incrementAndGet);
        dirtyCount.set(0);

        scheduler.requeueFront(claim, urgentRetry);

        assertEquals(List.of(urgentRetry, queued), scheduler.snapshot(claim));
        assertEquals(1, dirtyCount.get());
    }

    @Test
    void requeueFrontRespectsCapAndLeavesQueueUntouchedWhenFull() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        AtomicInteger dirtyCount = new AtomicInteger();
        UUID claim = UUID.randomUUID();
        for (int i = 0; i < BannerModSettlementProjectScheduler.PER_CLAIM_QUEUE_CAP; i++) {
            scheduler.submit(claim, ProjectTestFactory.general(200 - i, 1));
        }
        List<PendingProject> before = scheduler.snapshot(claim);
        scheduler.setDirtyListener(dirtyCount::incrementAndGet);
        dirtyCount.set(0);

        scheduler.requeueFront(claim, ProjectTestFactory.general(999, 1));

        assertEquals(before, scheduler.snapshot(claim));
        assertEquals(0, dirtyCount.get());
    }
}
