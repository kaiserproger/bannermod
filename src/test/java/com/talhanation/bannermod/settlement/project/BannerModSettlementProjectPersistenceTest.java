package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingCategory;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectBlocker;
import com.talhanation.bannermod.settlement.growth.ProjectKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the persistence contract for the project-queue subsystem of SETTLEMENT-004
 * ("Reload does not lose active meaningful settlement work state"). Covers both leaf
 * ({@link PendingProject#toTag} / {@link PendingProject#fromTag}) and aggregate
 * ({@link BannerModSettlementProjectScheduler#toTag} / {@link BannerModSettlementProjectScheduler#fromTag})
 * roundtrips, plus forward-compat fallbacks for every embedded enum and the
 * per-claim queue cap regression guard.
 *
 * <p>Same shape as {@code BannerModSellerDispatchPersistenceTest} and
 * {@code BannerModHomeAssignmentPersistenceTest} — completing the third leg of the
 * SETTLEMENT-004 audit pattern.</p>
 */
class BannerModSettlementProjectPersistenceTest {

    private static final UUID CLAIM_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CLAIM_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PROJECT_X = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT_Y = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TARGET_BUILDING = UUID.fromString("33333333-3333-3333-3333-333333333333");

    // ------------------------------------------------------------------
    // PendingProject leaf roundtrip
    // ------------------------------------------------------------------

    @Test
    void pendingProjectRoundTripPreservesAllFields() {
        // Use non-default values for every field so a regression that silently swapped two
        // fields (e.g. priority/cost) would visibly fail rather than coincidentally pass.
        PendingProject original = new PendingProject(
                PROJECT_X,
                ProjectKind.UPGRADE,
                TARGET_BUILDING,
                ResourceLocation.fromNamespaceAndPath("bannermod", "mine"),
                BannerModSettlementBuildingCategory.GENERAL,
                BannerModSettlementBuildingProfileSeed.GENERAL,
                420,
                12_345L,
                7,
                ProjectBlocker.NO_MATERIALS
        );

        PendingProject decoded = PendingProject.fromTag(original.toTag());

        assertEquals(original, decoded,
                "every PendingProject field must survive the NBT roundtrip");
    }

    @Test
    void pendingProjectNewBuildingDropsTargetEvenAcrossRoundTrip() {
        // Constructor invariant: NEW_BUILDING has no target binding. The roundtrip must
        // preserve that — a regression that re-emitted the dropped target via Tag would
        // cause downstream code to treat the project as an UPGRADE binding.
        PendingProject original = new PendingProject(
                PROJECT_X,
                ProjectKind.NEW_BUILDING,
                TARGET_BUILDING, // ctor will null this out
                ResourceLocation.fromNamespaceAndPath("bannermod", "house"),
                BannerModSettlementBuildingCategory.GENERAL,
                BannerModSettlementBuildingProfileSeed.GENERAL,
                500,
                0L,
                3,
                ProjectBlocker.NONE
        );
        assertNull(original.targetBuildingUuid(),
                "ctor invariant: NEW_BUILDING projects carry no target");

        PendingProject decoded = PendingProject.fromTag(original.toTag());

        assertNull(decoded.targetBuildingUuid(),
                "roundtrip must preserve the null target — toTag must not emit a Target key for NEW_BUILDING");
    }

    @Test
    void everyProjectKindRoundTripsExactly() {
        // Defends against accidental enum churn — a renamed kind would silently fall back
        // to NEW_BUILDING via PendingProject.kindFromTagName, masking the breakage.
        for (ProjectKind kind : ProjectKind.values()) {
            UUID target = kind == ProjectKind.NEW_BUILDING ? null : TARGET_BUILDING;
            PendingProject original = new PendingProject(
                    PROJECT_X, kind, target, null,
                    BannerModSettlementBuildingCategory.GENERAL,
                    BannerModSettlementBuildingProfileSeed.GENERAL,
                    100, 0L, 1, ProjectBlocker.NONE
            );
            PendingProject decoded = PendingProject.fromTag(original.toTag());
            assertEquals(kind, decoded.kind(),
                    "ProjectKind." + kind.name() + " must roundtrip to itself, not fall back to NEW_BUILDING");
        }
    }

    @Test
    void unknownProjectKindFallsBackToNewBuilding() {
        // Forward-compat: a future build that adds a kind must not crash older loaders. The
        // contract is "fall back to NEW_BUILDING" rather than "throw".
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", PROJECT_X);
        tag.putString("Kind", "KIND_FROM_THE_FUTURE");
        tag.putString("Category", BannerModSettlementBuildingCategory.GENERAL.name());
        tag.putString("Profile", BannerModSettlementBuildingProfileSeed.GENERAL.name());
        tag.putInt("Priority", 1);
        tag.putLong("ProposedAt", 0L);
        tag.putInt("Cost", 1);
        tag.putString("Blocker", ProjectBlocker.NONE.name());

        PendingProject decoded = PendingProject.fromTag(tag);

        assertEquals(ProjectKind.NEW_BUILDING, decoded.kind(),
                "unknown ProjectKind name must fall back to NEW_BUILDING");
        assertEquals(PROJECT_X, decoded.projectId(),
                "fallback must still preserve the rest of the record");
    }

    @Test
    void everyProjectBlockerRoundTripsExactly() {
        for (ProjectBlocker blocker : ProjectBlocker.values()) {
            PendingProject original = new PendingProject(
                    PROJECT_X, ProjectKind.NEW_BUILDING, null, null,
                    BannerModSettlementBuildingCategory.GENERAL,
                    BannerModSettlementBuildingProfileSeed.GENERAL,
                    1, 0L, 1, blocker
            );
            PendingProject decoded = PendingProject.fromTag(original.toTag());
            assertEquals(blocker, decoded.blockerReason(),
                    "ProjectBlocker." + blocker.name() + " must roundtrip exactly");
        }
    }

    @Test
    void unknownProjectBlockerFallsBackToNone() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", PROJECT_X);
        tag.putString("Kind", ProjectKind.NEW_BUILDING.name());
        tag.putString("Category", BannerModSettlementBuildingCategory.GENERAL.name());
        tag.putString("Profile", BannerModSettlementBuildingProfileSeed.GENERAL.name());
        tag.putInt("Priority", 1);
        tag.putLong("ProposedAt", 0L);
        tag.putInt("Cost", 1);
        tag.putString("Blocker", "BLOCKED_BY_THE_FUTURE");

        PendingProject decoded = PendingProject.fromTag(tag);

        assertEquals(ProjectBlocker.NONE, decoded.blockerReason(),
                "unknown ProjectBlocker name must fall back to NONE");
    }

    // ------------------------------------------------------------------
    // Scheduler aggregate roundtrip
    // ------------------------------------------------------------------

    @Test
    void emptySchedulerRoundTripsToEmptyScheduler() {
        BannerModSettlementProjectScheduler original = BannerModSettlementProjectScheduler.detached();

        BannerModSettlementProjectScheduler decoded =
                BannerModSettlementProjectScheduler.fromTag(original.toTag());

        assertTrue(decoded.snapshot(CLAIM_A).isEmpty(),
                "empty scheduler must roundtrip to empty — no spurious decoded entries");
        assertEquals(0, decoded.pendingCount(CLAIM_A),
                "empty scheduler must report zero pending");
    }

    @Test
    void multiClaimRoundTripPreservesQueuesAndPriorityOrder() {
        // Two claims, each with mixed-priority queues. Submission order is intentionally
        // non-priority-sorted on the input side so the priority-sort contract is the
        // observable check, not the insertion contract.
        BannerModSettlementProjectScheduler original = BannerModSettlementProjectScheduler.detached();
        original.submit(CLAIM_A, ProjectTestFactory.general(100, 5));
        original.submit(CLAIM_A, ProjectTestFactory.general(500, 3));
        original.submit(CLAIM_A, ProjectTestFactory.general(300, 4));
        original.submit(CLAIM_B, ProjectTestFactory.general(900, 1));

        List<PendingProject> claimABefore = original.snapshot(CLAIM_A);
        List<PendingProject> claimBBefore = original.snapshot(CLAIM_B);
        assertEquals(500, claimABefore.get(0).priorityScore(),
                "highest priority must lead claimA after submit() priority sort — guards the test premise");

        BannerModSettlementProjectScheduler decoded =
                BannerModSettlementProjectScheduler.fromTag(original.toTag());

        assertEquals(claimABefore, decoded.snapshot(CLAIM_A),
                "claimA queue must roundtrip in priority-sorted order");
        assertEquals(claimBBefore, decoded.snapshot(CLAIM_B),
                "claimB queue must roundtrip");
    }

    @Test
    void cancellationLogRoundTripsEntries() {
        BannerModSettlementProjectScheduler original = BannerModSettlementProjectScheduler.detached();
        original.cancel(PROJECT_X, ProjectCancellationReason.SUPERSEDED);
        original.cancel(PROJECT_Y, ProjectCancellationReason.BLOCKED);

        BannerModSettlementProjectScheduler decoded =
                BannerModSettlementProjectScheduler.fromTag(original.toTag());

        assertEquals(ProjectCancellationReason.SUPERSEDED, decoded.lastCancellationReason(PROJECT_X),
                "SUPERSEDED cancellation must survive the roundtrip");
        assertEquals(ProjectCancellationReason.BLOCKED, decoded.lastCancellationReason(PROJECT_Y),
                "BLOCKED cancellation must survive the roundtrip");
    }

    @Test
    void everyCancellationReasonRoundTripsExactly() {
        // Defends against accidental enum churn on the cancellation side, mirroring the
        // ProjectKind / ProjectBlocker checks above.
        for (ProjectCancellationReason reason : ProjectCancellationReason.values()) {
            BannerModSettlementProjectScheduler original = BannerModSettlementProjectScheduler.detached();
            original.cancel(PROJECT_X, reason);

            BannerModSettlementProjectScheduler decoded =
                    BannerModSettlementProjectScheduler.fromTag(original.toTag());

            assertEquals(reason, decoded.lastCancellationReason(PROJECT_X),
                    "ProjectCancellationReason." + reason.name() + " must roundtrip exactly");
        }
    }

    @Test
    void unknownCancellationReasonFallsBackToManual() {
        // Hand-craft NBT with a future-named cancellation reason. The scheduler's loader
        // must fall back to MANUAL rather than throw.
        CompoundTag tag = new CompoundTag();
        tag.put("Queues", new ListTag());
        ListTag cancellations = new ListTag();
        CompoundTag cancellationTag = new CompoundTag();
        cancellationTag.putUUID("Project", PROJECT_X);
        cancellationTag.putString("Reason", "FUTURE_REASON");
        cancellations.add(cancellationTag);
        tag.put("Cancellations", cancellations);

        BannerModSettlementProjectScheduler decoded =
                BannerModSettlementProjectScheduler.fromTag(tag);

        assertEquals(ProjectCancellationReason.MANUAL, decoded.lastCancellationReason(PROJECT_X),
                "unknown cancellation reason must fall back to MANUAL");
    }

    @Test
    void roundTripEnforcesPerClaimQueueCap() {
        // Hand-craft NBT carrying more than PER_CLAIM_QUEUE_CAP entries for one claim — the
        // loader must truncate, not blow past the cap. A regression that lifted the cap on
        // load would let saved data slowly grow unbounded.
        CompoundTag tag = new CompoundTag();
        ListTag queues = new ListTag();
        CompoundTag queueTag = new CompoundTag();
        queueTag.putUUID("Claim", CLAIM_A);
        ListTag projectTags = new ListTag();
        int overshoot = BannerModSettlementProjectScheduler.PER_CLAIM_QUEUE_CAP + 5;
        for (int i = 0; i < overshoot; i++) {
            // Build a unique-id project; reuse the toTag emission path so a regression in
            // PendingProject.toTag would fail the cap test instead of silently passing.
            PendingProject project = new PendingProject(
                    UUID.randomUUID(),
                    ProjectKind.NEW_BUILDING,
                    null,
                    null,
                    BannerModSettlementBuildingCategory.GENERAL,
                    BannerModSettlementBuildingProfileSeed.GENERAL,
                    100, i, 1, ProjectBlocker.NONE
            );
            projectTags.add(project.toTag());
        }
        queueTag.put("Projects", projectTags);
        queues.add(queueTag);
        tag.put("Queues", queues);
        tag.put("Cancellations", new ListTag());

        BannerModSettlementProjectScheduler decoded =
                BannerModSettlementProjectScheduler.fromTag(tag);

        assertEquals(BannerModSettlementProjectScheduler.PER_CLAIM_QUEUE_CAP,
                decoded.pendingCount(CLAIM_A),
                "loader must truncate to PER_CLAIM_QUEUE_CAP, not lift the cap on load");
    }

    // ------------------------------------------------------------------
    // Dirty-listener semantics around restoreFromTag
    // ------------------------------------------------------------------

    @Test
    void identicalRestoreFromTagDoesNotDirty() {
        // Same content via NBT roundtrip must not mark dirty — that's the "no false dirty
        // churn on identical reload/restore" half of the SETTLEMENT-004 acceptance.
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        scheduler.submit(CLAIM_A, ProjectTestFactory.general(200, 1));
        scheduler.cancel(PROJECT_X, ProjectCancellationReason.MANUAL);

        AtomicInteger dirtyCount = new AtomicInteger();
        scheduler.setDirtyListener(dirtyCount::incrementAndGet);

        scheduler.restoreFromTag(scheduler.toTag());

        assertEquals(0, dirtyCount.get(),
                "restoring identical content must not trigger the dirty listener");
    }

    @Test
    void differentRestoreFromTagDoesDirty() {
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        scheduler.submit(CLAIM_A, ProjectTestFactory.general(200, 1));

        AtomicInteger dirtyCount = new AtomicInteger();
        scheduler.setDirtyListener(dirtyCount::incrementAndGet);

        // Restore from an empty tag → content changed (non-empty -> empty) → must dirty.
        CompoundTag emptyTag = new CompoundTag();
        emptyTag.put("Queues", new ListTag());
        emptyTag.put("Cancellations", new ListTag());
        scheduler.restoreFromTag(emptyTag);

        assertEquals(1, dirtyCount.get(),
                "restoring different content must trigger the dirty listener exactly once");
    }

    @Test
    void decodedSchedulerIsIndependentOfSource() {
        // Defensive: a regression where fromTag returned a scheduler that shared internal
        // collections with the source would silently bleed mutations across saved-data
        // boundaries. PendingProject is a record so this is unlikely, but the test is cheap.
        BannerModSettlementProjectScheduler original = BannerModSettlementProjectScheduler.detached();
        original.submit(CLAIM_A, ProjectTestFactory.general(100, 1));

        BannerModSettlementProjectScheduler decoded =
                BannerModSettlementProjectScheduler.fromTag(original.toTag());

        decoded.pollNext(CLAIM_A);

        assertEquals(0, decoded.pendingCount(CLAIM_A),
                "decoded scheduler must be mutable in isolation");
        assertEquals(1, original.pendingCount(CLAIM_A),
                "mutating the decoded scheduler must not bleed into the source scheduler");
        assertNotEquals(original.snapshot(CLAIM_A), decoded.snapshot(CLAIM_A),
                "source and decoded snapshots must diverge after the mutation");
    }

    @Test
    void schedulerToTagSkipsEmptyQueues() {
        // A claim that becomes empty (e.g. polled to zero) must not leak into the on-disk
        // representation as a zero-entry queue tag — that would produce save bloat over
        // long-running worlds and break the `if (queue.isEmpty()) queues.remove` invariant
        // on the runtime side.
        BannerModSettlementProjectScheduler scheduler = BannerModSettlementProjectScheduler.detached();
        scheduler.submit(CLAIM_A, ProjectTestFactory.general(100, 1));
        scheduler.pollNext(CLAIM_A);

        CompoundTag tag = scheduler.toTag();

        ListTag queues = tag.getList("Queues", Tag.TAG_COMPOUND);
        assertNotNull(queues);
        assertTrue(queues.isEmpty(),
                "an emptied claim must not emit a stale queue tag in the saved data");
        assertFalse(scheduler.peek(CLAIM_A).isPresent(),
                "scheduler must report no head for an emptied claim — guards the test premise");
    }
}
