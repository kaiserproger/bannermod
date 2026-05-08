package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.client.military.gui.GovernorScreen;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Envelope;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Payload;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.RefreshTrigger;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.SnapshotState;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;

import java.util.UUID;

public class MessageToClientUpdateGovernorScreen implements BannerModMessage<MessageToClientUpdateGovernorScreen> {
    private UUID recruit;
    private Envelope envelope;

    public MessageToClientUpdateGovernorScreen() {
    }

    public MessageToClientUpdateGovernorScreen(UUID recruit, Envelope envelope) {
        this.recruit = recruit;
        this.envelope = envelope;
    }

    public UUID recruit() {
        return this.recruit;
    }

    public Envelope envelope() {
        return this.envelope;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.clientbound();
    }

    @Override
    public void executeClientSide(BannerModNetworkContext context) {
        GovernorScreen.applyUpdate(this.recruit, this.envelope);
    }

    @Override
    public MessageToClientUpdateGovernorScreen fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        int contractVersion = buf.readVarInt();
        long serverVersion = buf.readLong();
        long createdAtGameTime = buf.readLong();
        RefreshTrigger trigger = buf.readEnum(RefreshTrigger.class);
        SnapshotState state = buf.readEnum(SnapshotState.class);
        long staleSinceServerVersion = buf.readLong();
        Payload payload = null;
        if (buf.readBoolean()) {
            UUID claimUuid = buf.readUUID();
            SettlementSnapshot settlementSnapshot = null;
            if (buf.readBoolean()) {
                CompoundTag tag = buf.readNbt();
                settlementSnapshot = tag == null ? null : SettlementSnapshot.fromTag(tag);
            }
            BannerModGovernorSnapshot governorSnapshot = null;
            if (buf.readBoolean()) {
                CompoundTag tag = buf.readNbt();
                governorSnapshot = tag == null ? null : BannerModGovernorSnapshot.fromTag(tag);
            }
            payload = new Payload(claimUuid, settlementSnapshot, governorSnapshot);
        }
        this.envelope = new Envelope(contractVersion, serverVersion, createdAtGameTime, trigger, state, staleSinceServerVersion, payload);
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeVarInt(this.envelope.contractVersion());
        buf.writeLong(this.envelope.serverVersion());
        buf.writeLong(this.envelope.createdAtGameTime());
        buf.writeEnum(this.envelope.trigger());
        buf.writeEnum(this.envelope.state());
        buf.writeLong(this.envelope.staleSinceServerVersion());
        Payload payload = this.envelope.payload();
        buf.writeBoolean(payload != null);
        if (payload != null) {
            buf.writeUUID(payload.claimUuid());
            buf.writeBoolean(payload.settlementSnapshot() != null);
            if (payload.settlementSnapshot() != null) {
                buf.writeNbt(payload.settlementSnapshot().toTag());
            }
            buf.writeBoolean(payload.governorSnapshot() != null);
            if (payload.governorSnapshot() != null) {
                buf.writeNbt(payload.governorSnapshot().toTag());
            }
        }
    }
}
