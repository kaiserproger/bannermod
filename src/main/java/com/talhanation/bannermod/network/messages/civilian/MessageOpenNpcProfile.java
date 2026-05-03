package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class MessageOpenNpcProfile implements BannerModMessage<MessageOpenNpcProfile> {
    private UUID targetUuid;

    public MessageOpenNpcProfile() {
    }

    public MessageOpenNpcProfile(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        ServerPlayer player = context.getSender();
        if (player == null || this.targetUuid == null) {
            return;
        }
        if (player.serverLevel().getEntity(this.targetUuid) instanceof CitizenEntity citizen
                && citizen.isAlive()
                && citizen.canOpenProfile(player)
                && player.distanceToSqr(citizen) <= 16.0D * 16.0D) {
            citizen.openProfileGui(player);
            return;
        }
        if (player.serverLevel().getEntity(this.targetUuid) instanceof AbstractWorkerEntity worker
                && worker.isAlive()
                && player.distanceToSqr(worker) <= 16.0D * 16.0D) {
            worker.openDepositsGUI(player);
        }
    }

    @Override
    public MessageOpenNpcProfile fromBytes(FriendlyByteBuf buf) {
        this.targetUuid = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.targetUuid);
    }
}
