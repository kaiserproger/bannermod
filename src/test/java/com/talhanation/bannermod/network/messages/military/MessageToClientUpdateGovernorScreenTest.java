package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Envelope;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Payload;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.RefreshTrigger;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.SnapshotState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageToClientUpdateGovernorScreenTest {

    @Test
    void roundTripsSettlementSnapshotEnvelopeForMutationRefresh() {
        UUID recruitId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        SettlementSnapshot settlement = SettlementSnapshot.create(claimId, new ChunkPos(4, 5), "sync-team");
        BannerModGovernorSnapshot governor = BannerModGovernorSnapshot.create(claimId, new ChunkPos(4, 5), "sync-team")
                .withHeartbeatReport(50L, 50L, 6, 4, 2, java.util.List.of("incident"), java.util.List.of("recommend"));
        Envelope envelope = Envelope.ready(50L, 50L, RefreshTrigger.MUTATION_REFRESH,
                new Payload(claimId, settlement, governor));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new MessageToClientUpdateGovernorScreen(recruitId, envelope).toBytes(buf);
        MessageToClientUpdateGovernorScreen decoded = new MessageToClientUpdateGovernorScreen().fromBytes(buf);

        assertEquals(recruitId, decoded.recruit());
        assertEquals(SnapshotState.READY, decoded.envelope().state());
        assertEquals(RefreshTrigger.MUTATION_REFRESH, decoded.envelope().trigger());
        assertEquals(claimId, decoded.envelope().payload().claimUuid());
        assertEquals(settlement, decoded.envelope().payload().settlementSnapshot());
        assertEquals(governor, decoded.envelope().payload().governorSnapshot());
    }
}
