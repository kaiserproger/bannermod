package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageHire implements BannerModMessage<MessageHire> {

    private UUID player;
    private UUID recruit;
    private UUID groupUUID;

    public MessageHire() {
    }

    public MessageHire(UUID player, UUID recruit, UUID groupUUID) {
        this.player = player;
        this.recruit = recruit;
        this.groupUUID = groupUUID;
    }

    public PacketFlow getExecutingSide()  {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            RecruitsGroup group = RecruitCommandAuthority.ownedGroup(player, groupUUID);
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitWithinDistance(player, this.recruit, 16.0D * 16.0D);
            if (recruit != null) {
                CommandEvents.handleRecruiting(player, group, recruit, true);
            }
        });
    }

    public MessageHire fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.recruit = buf.readUUID();
        this.groupUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUUID(this.recruit);
        buf.writeUUID(this.groupUUID);
    }
}
