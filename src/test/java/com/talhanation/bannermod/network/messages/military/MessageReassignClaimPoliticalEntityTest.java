package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageReassignClaimPoliticalEntityTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void authorizedActorTransfersClaimToTargetPoliticalEntity() {
        RecruitsClaim claim = new RecruitsClaim("border claim", SOURCE_ID);
        PoliticalEntityRecord source = entity(SOURCE_ID, "Source", ACTOR);
        PoliticalEntityRecord target = entity(TARGET_ID, "Target", ACTOR);

        MessageReassignClaimPoliticalEntity.TransferResult result = MessageReassignClaimPoliticalEntity.reassignClaimPoliticalEntity(
                ACTOR,
                false,
                claim,
                source,
                target,
                TARGET_ID);

        assertTrue(result.transferred());
        assertNull(result.denialKey());
        assertEquals(TARGET_ID, claim.getOwnerPoliticalEntityId());
    }

    @Test
    void actorWithoutTargetAuthorityIsDeniedAndClaimOwnerRemainsUnchanged() {
        RecruitsClaim claim = new RecruitsClaim("border claim", SOURCE_ID);
        PoliticalEntityRecord source = entity(SOURCE_ID, "Source", ACTOR);
        PoliticalEntityRecord target = entity(TARGET_ID, "Target", OTHER);

        MessageReassignClaimPoliticalEntity.TransferResult result = MessageReassignClaimPoliticalEntity.reassignClaimPoliticalEntity(
                ACTOR,
                false,
                claim,
                source,
                target,
                TARGET_ID);

        assertFalse(result.transferred());
        assertEquals("chat.bannermod.claim.transfer.denied.no_target_authority", result.denialKey());
        assertEquals(PoliticalEntityAuthority.DENIAL_LEADER_ONLY_KEY, result.denialReasonKey());
        assertEquals(SOURCE_ID, claim.getOwnerPoliticalEntityId());
    }

    private static PoliticalEntityRecord entity(UUID id, String name, UUID leader) {
        return new PoliticalEntityRecord(
                id,
                name,
                PoliticalEntityStatus.STATE,
                leader,
                List.of(),
                BlockPos.ZERO,
                "",
                "",
                "",
                "",
                0L);
    }
}
