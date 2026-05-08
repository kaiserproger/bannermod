package com.talhanation.bannermod.events.runtime;

import com.talhanation.bannermod.combat.runtime.RecruitTargetAuthority;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecruitTargetAuthorityTest {
    @Test
    void claimOwnerCanTargetInsideOwnClaim() {
        UUID owner = UUID.randomUUID();

        assertTrue(RecruitTargetAuthority.claimAuthorityAllowsTarget(owner, owner, false, false));
    }

    @Test
    void warOrOccupationAuthorizesTargetingInsideEnemyClaim() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();

        assertTrue(RecruitTargetAuthority.claimAuthorityAllowsTarget(attacker, defender, false, true));
        assertTrue(RecruitTargetAuthority.claimAuthorityAllowsTarget(attacker, defender, true, false));
    }

    @Test
    void neutralOrUnaffiliatedAttackerCannotTargetProtectedClaimEntities() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();

        assertFalse(RecruitTargetAuthority.claimAuthorityAllowsTarget(attacker, defender, false, false));
        assertFalse(RecruitTargetAuthority.claimAuthorityAllowsTarget(null, defender, false, false));
    }

    @Test
    void unownedClaimDoesNotBlockTargeting() {
        assertTrue(RecruitTargetAuthority.claimAuthorityAllowsTarget(null, null, false, false));
    }
}
