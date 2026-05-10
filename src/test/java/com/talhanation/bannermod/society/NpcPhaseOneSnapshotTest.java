package com.talhanation.bannermod.society;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NpcPhaseOneSnapshotTest {
    @Test
    void knownGoalIdsExposeTranslatedGoalLabels() {
        NpcPhaseOneSnapshot snapshot = snapshot(
                "EXECUTING",
                "bannermod:resident/goal/go_home",
                "bannermod:resident/goal/seek_supplies",
                "NONE"
        );

        assertEquals("gui.bannermod.society.ai.goal.go_home", translationKey(snapshot.aiCurrentGoalComponent()));
        assertEquals("gui.bannermod.society.ai.goal.seek_supplies", translationKey(snapshot.aiBlockedGoalComponent()));
    }

    @Test
    void recoveringSnapshotsExposeRecoveryRouteHelpers() {
        NpcPhaseOneSnapshot snapshot = snapshot(
                "RECOVERING",
                "bannermod:resident/goal/go_home",
                "bannermod:resident/goal/work",
                NpcSocietyDecisionSnapshot.BLOCKED_REASON_CONTEXT_INVALIDATED
        );

        assertEquals("gui.bannermod.society.ai.route.recovery_detail", translationKey(snapshot.aiRouteSecondaryComponent()));
        assertEquals("gui.bannermod.society.ai.route.recovery_summary", translationKey(snapshot.aiReadableRoutineReasonComponent()));
    }

    @Test
    void blockedSnapshotsExposeBlockedRouteHelpers() {
        NpcPhaseOneSnapshot snapshot = snapshot(
                "BLOCKED",
                null,
                "bannermod:resident/goal/work",
                "NO_WORK_ASSIGNMENT"
        );

        assertEquals("gui.bannermod.society.ai.route.blocked_detail", translationKey(snapshot.aiRouteSecondaryComponent()));
        assertEquals("gui.bannermod.society.ai.route.blocked_summary", translationKey(snapshot.aiReadableRoutineReasonComponent()));
    }

    @Test
    void blockedSnapshotsKeepBlockedHelpersEvenWithFallbackGoal() {
        NpcPhaseOneSnapshot snapshot = snapshot(
                "BLOCKED",
                "bannermod:resident/goal/idle",
                "bannermod:resident/goal/work",
                "NO_WORK_ASSIGNMENT"
        );

        assertEquals("gui.bannermod.society.ai.route.blocked_detail", translationKey(snapshot.aiRouteSecondaryComponent()));
        assertEquals("gui.bannermod.society.ai.route.blocked_summary", translationKey(snapshot.aiReadableRoutineReasonComponent()));
    }

    private static NpcPhaseOneSnapshot snapshot(String aiStateTag,
                                                String currentGoalId,
                                                String blockedGoalId,
                                                String blockedReasonTag) {
        return new NpcPhaseOneSnapshot(
                NpcLifeStage.ADULT.name(),
                NpcSex.FEMALE.name(),
                UUID.fromString("00000000-0000-0000-0000-00000000f001"),
                UUID.fromString("00000000-0000-0000-0000-00000000f002"),
                UUID.fromString("00000000-0000-0000-0000-00000000f003"),
                UUID.fromString("00000000-0000-0000-0000-00000000f004"),
                null,
                null,
                NpcDailyPhase.RETURNING_HOME.name(),
                NpcIntent.GO_HOME.name(),
                NpcAnchorType.HOME.name(),
                aiStateTag,
                currentGoalId,
                "RETURNING_TO_HOUSEHOLD",
                "REGROUPING_AT_HOME",
                blockedGoalId,
                blockedReasonTag,
                4,
                NpcHouseholdHousingState.NORMAL.name(),
                10,
                55,
                18,
                NpcHousingRequestStatus.NONE.name(),
                "LOW",
                "STABLE",
                0
        );
    }

    private static String translationKey(Component component) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        return contents.getKey();
    }
}
