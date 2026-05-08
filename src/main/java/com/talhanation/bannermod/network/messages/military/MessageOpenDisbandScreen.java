package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageOpenDisbandScreen implements BannerModMessage<MessageOpenDisbandScreen> {

    private UUID player;
    private UUID recruit;

    public MessageOpenDisbandScreen() {
        this.player = new UUID(0, 0);
    }

    public MessageOpenDisbandScreen(Player player, UUID recruit) {
        this.player = player.getUUID();
        this.recruit = recruit;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!player.getUUID().equals(this.player)) {
                return;
            }
        });
    }

    @Override
    public MessageOpenDisbandScreen fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.recruit= buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeUUID(recruit);
    }

}
