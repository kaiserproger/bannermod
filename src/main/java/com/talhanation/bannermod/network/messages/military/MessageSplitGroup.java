package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageSplitGroup implements BannerModMessage<MessageSplitGroup> {

    private UUID groupUUID;

    public MessageSplitGroup() {
    }

    public MessageSplitGroup(UUID groupUUID) {
        this.groupUUID = groupUUID;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            RecruitsGroup groupToSplit = RecruitEvents.groupsManager().getGroup(groupUUID);

            if(groupToSplit == null) return;

            RecruitEvents.groupsManager().splitGroup(groupToSplit, player.serverLevel());
        });
    }

    public MessageSplitGroup fromBytes(FriendlyByteBuf buf) {
        this.groupUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(groupUUID);
    }
}
