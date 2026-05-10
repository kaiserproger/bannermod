package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.economy.FortLevelDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUpdateClaimTest {
    @Test
    void genericClaimUpdatePreservesExistingServerFortLevel() {
        RecruitsClaim existing = new RecruitsClaim("Fort", UUID.randomUUID());
        existing.setFortLevel(3);
        RecruitsClaim updatedFromClient = new RecruitsClaim("Fort", existing.getOwnerPoliticalEntityId());
        updatedFromClient.setFortLevel(4);

        MessageUpdateClaim.preserveServerOwnedFields(updatedFromClient, existing);

        assertEquals(3, updatedFromClient.getFortLevel());
    }

    @Test
    void newGenericClaimUpdateUsesMinimumServerFortLevel() {
        RecruitsClaim updatedFromClient = new RecruitsClaim("Fort", UUID.randomUUID());
        updatedFromClient.setFortLevel(4);

        MessageUpdateClaim.preserveServerOwnedFields(updatedFromClient, null);

        assertEquals(FortLevelDefinition.MIN_LEVEL, updatedFromClient.getFortLevel());
    }
}
