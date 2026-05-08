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

public class MessageDismount implements BannerModMessage<MessageDismount> {

    private UUID uuid;
    private UUID group;

    public MessageDismount(){
    }

    public MessageDismount(UUID uuid, UUID group) {
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
        List<AbstractRecruitEntity> recruits = RecruitCommandTargetResolver.resolveGroupTargets(player, playerUuid, group, "dismount");
        recruits.forEach((recruit) -> CommandEvents.onDismountButton(recruit.getOwnerUUID(), recruit, group));
    }
    public MessageDismount fromBytes(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.group = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUUID(group);
    }

}
