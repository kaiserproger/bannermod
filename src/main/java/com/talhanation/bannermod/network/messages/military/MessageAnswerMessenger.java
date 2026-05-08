package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.MessengerEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageAnswerMessenger implements BannerModMessage<MessageAnswerMessenger> {

    private UUID recruit;
    public MessageAnswerMessenger() {
    }
    public MessageAnswerMessenger(UUID recruit) {
        this.recruit = recruit;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Entity entity = player.serverLevel().getEntity(this.recruit);
            if (entity instanceof MessengerEntity messenger && messenger.distanceToSqr(player) <= 16D * 16D) {
                messenger.teleportWaitTimer = 100;
                player.sendSystemMessage(messenger.MESSENGER_INFO_ON_MY_WAY());
                messenger.giveDeliverItem(player);

                messenger.setMessengerState(MessengerEntity.MessengerState.TELEPORT_BACK);
            }
        });
    }
    public MessageAnswerMessenger fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
    }
}
