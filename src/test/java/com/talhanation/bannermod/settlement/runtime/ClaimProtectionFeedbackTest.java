package com.talhanation.bannermod.settlement.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimProtectionFeedbackTest {
    @Test
    void friendlyDeniedFeedbackUsesFriendlyTerritoryKey() {
        assertEquals(
                "chat.bannermod.claim_protection.denied.friendly",
                ClaimProtectionFeedback.deniedMessageKey(ClaimProtectionFeedback.Territory.FRIENDLY)
        );
    }

    @Test
    void hostileDeniedFeedbackUsesHostileTerritoryKey() {
        assertEquals(
                "chat.bannermod.claim_protection.denied.hostile",
                ClaimProtectionFeedback.deniedMessageKey(ClaimProtectionFeedback.Territory.HOSTILE)
        );
    }

    @Test
    void unclaimedDeniedFeedbackUsesWildernessKey() {
        assertEquals(
                "chat.bannermod.claim_protection.denied.unclaimed",
                ClaimProtectionFeedback.deniedMessageKey(ClaimProtectionFeedback.Territory.UNCLAIMED)
        );
    }
}
