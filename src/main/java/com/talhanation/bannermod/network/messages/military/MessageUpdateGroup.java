package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.persistence.military.RecruitsGroupsManager;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;


public class MessageUpdateGroup implements BannerModMessage<MessageUpdateGroup> {

    private CompoundTag groupNBT;

    public MessageUpdateGroup(){

    }

    public MessageUpdateGroup(RecruitsGroup group) {
        this.groupNBT = group.toNBT();
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer serverPLayer = context.getSender();
            if (serverPLayer == null || this.groupNBT == null) return;

            RecruitsGroup updatedGroup = RecruitsGroup.fromNBT(this.groupNBT);
            RecruitEvents.groupsManager().addOrUpdateGroup((ServerLevel) serverPLayer.getCommandSenderWorld(), serverPLayer, updatedGroup);
        });
    }
    public MessageUpdateGroup fromBytes(FriendlyByteBuf buf) {
        this.groupNBT = buf.readNbt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(groupNBT);
    }
}
