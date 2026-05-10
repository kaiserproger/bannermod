package com.talhanation.bannermod.society;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSocietyAnchorGoalTest {

    @Test
    void stalledRouteRequiresRepeatedFailuresBeforeInvalidation() {
        assertFalse(NpcSocietyAnchorGoal.shouldInvalidateStalledRoute(2, 49.0D, 8.0D));
        assertTrue(NpcSocietyAnchorGoal.shouldInvalidateStalledRoute(3, 49.0D, 8.0D));
    }

    @Test
    void meaningfulRouteProgressNeedsRealDistanceGain() {
        assertFalse(NpcSocietyAnchorGoal.madeMeaningfulRouteProgress(36.0D, 35.5D));
        assertTrue(NpcSocietyAnchorGoal.madeMeaningfulRouteProgress(36.0D, 34.5D));
    }

    @Test
    void routeInvalidationSignalMatchesIntentAndExpiresQuickly() {
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        NpcSocietyAnchorGoal.signalRouteInvalidation(residentId, NpcIntent.WORK, 200L);

        assertFalse(NpcSocietyAnchorGoal.consumeRouteInvalidation(residentId, NpcIntent.GO_HOME, 200L));
        assertTrue(NpcSocietyAnchorGoal.consumeRouteInvalidation(residentId, NpcIntent.WORK, 200L));

        NpcSocietyAnchorGoal.signalRouteInvalidation(residentId, NpcIntent.WORK, 200L);
        assertFalse(NpcSocietyAnchorGoal.consumeRouteInvalidation(residentId, NpcIntent.WORK, 202L));
    }
}
