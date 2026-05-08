package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageAggroGui implements BannerModMessage<MessageAggroGui> {

    private int state;
    private UUID uuid;

    public MessageAggroGui() {
    }

    public MessageAggroGui(int state, UUID uuid) {
        this.state = state;
        this.uuid = uuid;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.uuid, 16.0D);
            if (RecruitCommandAuthority.canDirectlyControl(player, recruit)) {
                recruit.setAggroState(this.state);
            }
        });
    }

    public MessageAggroGui fromBytes(FriendlyByteBuf buf) {
        this.state = buf.readInt();
        this.uuid = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(state);
        buf.writeUUID(uuid);
    }
}
