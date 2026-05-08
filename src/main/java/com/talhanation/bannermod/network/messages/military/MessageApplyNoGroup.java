package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.*;

public class MessageApplyNoGroup implements BannerModMessage<MessageApplyNoGroup> {

    private UUID owner;
    private UUID groupID;

    public MessageApplyNoGroup(){
    }

    public MessageApplyNoGroup(UUID owner, UUID groupID) {
        this.owner = owner;
        this.groupID = groupID;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            List<AbstractRecruitEntity> recruitList = new ArrayList<>();

            ServerLevel serverLevel = (ServerLevel) player.getCommandSenderWorld();

            List<AbstractRecruitEntity> indexed = RecruitIndex.instance().groupMembers(serverLevel, groupID);
            if (indexed != null) {
                recruitList.addAll(indexed);
            } else {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                recruitList.addAll(serverLevel.getEntitiesOfClass(
                        AbstractRecruitEntity.class,
                        player.getBoundingBox().inflate(256.0D),
                        recruit -> recruit.getGroup() != null && recruit.getGroup().equals(groupID)
                ));
            }

            for(AbstractRecruitEntity recruit : recruitList){
                recruit.setGroupUUID(null);
            }
        });
    }
    public MessageApplyNoGroup fromBytes(FriendlyByteBuf buf) {
        this.owner = buf.readUUID();
        this.groupID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.owner);
        buf.writeUUID(this.groupID);
    }

}
