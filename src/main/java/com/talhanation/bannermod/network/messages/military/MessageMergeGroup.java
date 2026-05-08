package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageMergeGroup implements BannerModMessage<MessageMergeGroup> {

    private UUID groupUUID;
    private UUID mergeUUID;

    public MessageMergeGroup() {
    }

    public MessageMergeGroup(UUID mergeUUID, UUID groupUUID) {
        this.mergeUUID = mergeUUID;
        this.groupUUID = groupUUID;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            RecruitsGroup groupToMerge = RecruitEvents.groupsManager().getGroup(mergeUUID);
            RecruitsGroup baseGroup = RecruitEvents.groupsManager().getGroup(groupUUID);

            if(groupToMerge == null || baseGroup == null) return;

            RecruitEvents.groupsManager().mergeGroups(groupToMerge, baseGroup, player.serverLevel());
        });
    }

    public MessageMergeGroup fromBytes(FriendlyByteBuf buf) {
        this.groupUUID = buf.readUUID();
        this.mergeUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(groupUUID);
        buf.writeUUID(mergeUUID);
    }
}
