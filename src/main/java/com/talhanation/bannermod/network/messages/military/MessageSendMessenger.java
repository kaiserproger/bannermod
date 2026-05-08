package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.MessengerEntity;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageSendMessenger implements BannerModMessage<MessageSendMessenger> {

    private UUID recruit;
    private boolean start;
    private CompoundTag nbt;
    private String message;

    public MessageSendMessenger() {
    }

    public MessageSendMessenger(UUID recruit, RecruitsPlayerInfo targetPlayer, String message, boolean start) {
        this.recruit = recruit;
        this.message = message;
        this.start = start;

        if(targetPlayer != null){
            this.nbt = targetPlayer.toNBT();
        }
        else {
            this.nbt = new CompoundTag();
        }

    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Entity entity = player.serverLevel().getEntity(this.recruit);
            if (entity instanceof MessengerEntity messenger
                    && player.getBoundingBox().inflate(16D).intersects(messenger.getBoundingBox())) {
                if (!player.getUUID().equals(messenger.getOwnerUUID()) && !player.hasPermissions(2)) {
                    return;
                }

                messenger.setMessage(this.message);

                if(!this.nbt.isEmpty()){
                    messenger.setTargetPlayerInfo(RecruitsPlayerInfo.getFromNBT(this.nbt));
                }

                if(start){
                    messenger.setIsTreatyMessenger(false);
                    messenger.start();
                }
            }
        });
    }

    public MessageSendMessenger fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.start = buf.readBoolean();
        this.message = buf.readUtf();
        this.nbt = buf.readNbt();

        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
        buf.writeBoolean(start);
        buf.writeUtf(message);
        buf.writeNbt(nbt);
    }
}
