package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageClearTarget implements BannerModMessage<MessageClearTarget> {
    private UUID uuid;
    private UUID group;

    public MessageClearTarget(){
    }

    public MessageClearTarget(UUID uuid, UUID group) {
        this.uuid = uuid;
        this.group = group;

    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            dispatchToServer(player, this.uuid, this.group);
        });
    }

    public static void dispatchToServer(ServerPlayer player, UUID playerUuid, UUID group) {
        List<AbstractRecruitEntity> list = RecruitCommandTargetResolver.resolveGroupTargets(player, playerUuid, group, "clear-target");
        for (AbstractRecruitEntity recruits : list) {
            CommandEvents.onClearTargetButton(recruits.getOwnerUUID(), recruits, group);
        }
    }
    public MessageClearTarget fromBytes(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.group = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUUID(group);
    }

}
