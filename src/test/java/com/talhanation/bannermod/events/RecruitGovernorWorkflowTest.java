package com.talhanation.bannermod.events;

import com.talhanation.bannermod.governance.runtime.RecruitGovernorWorkflow;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Envelope;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.RefreshTrigger;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.SnapshotState;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RecruitGovernorWorkflowTest {

    @Test
    void screenOpenLoginAndMutationRefreshBuildReadySettlementMirrorPayloads() {
        RecruitsClaim claim = new RecruitsClaim("sync-004", UUID.randomUUID());
        claim.setCenter(new ChunkPos(2, 3));
        SettlementSnapshot settlement = SettlementSnapshot.create(claim.getUUID(), new ChunkPos(2, 3), "sync-team");
        BannerModGovernorSnapshot governor = BannerModGovernorSnapshot.create(claim.getUUID(), new ChunkPos(2, 3), "sync-team")
                .withGovernor(UUID.randomUUID(), UUID.randomUUID());

        assertEnvelope(RecruitGovernorWorkflow.buildEnvelope(claim, settlement, governor, 40L, RefreshTrigger.SCREEN_OPEN),
                claim.getUUID(), settlement, governor, RefreshTrigger.SCREEN_OPEN);
        assertEnvelope(RecruitGovernorWorkflow.buildEnvelope(claim, settlement, governor, 41L, RefreshTrigger.LOGIN),
                claim.getUUID(), settlement, governor, RefreshTrigger.LOGIN);
        assertEnvelope(RecruitGovernorWorkflow.buildEnvelope(claim, settlement, governor, 42L, RefreshTrigger.MUTATION_REFRESH),
                claim.getUUID(), settlement, governor, RefreshTrigger.MUTATION_REFRESH);
    }

    private static void assertEnvelope(Envelope envelope,
                                       UUID claimId,
                                       SettlementSnapshot settlement,
                                       BannerModGovernorSnapshot governor,
                                       RefreshTrigger trigger) {
        assertEquals(SnapshotState.READY, envelope.state());
        assertEquals(trigger, envelope.trigger());
        assertEquals(claimId, envelope.payload().claimUuid());
        assertSame(settlement, envelope.payload().settlementSnapshot());
        assertSame(governor, envelope.payload().governorSnapshot());
    }
}
