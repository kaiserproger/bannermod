package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessagePatrolLeaderSetInfoMode implements BannerModMessage<MessagePatrolLeaderSetInfoMode> {
    private UUID recruit;
    private byte state;

    public MessagePatrolLeaderSetInfoMode() {
    }

    public MessagePatrolLeaderSetInfoMode(UUID recruit, byte state) {
        this.recruit = recruit;
        this.state = state;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Entity entity = player.serverLevel().getEntity(this.recruit);
            if (entity instanceof AbstractLeaderEntity leader
                    && leader.isAlive()
                    && RecruitCommandAuthority.canDirectlyControl(player, leader)
                    && player.getBoundingBox().inflate(16.0D).intersects(leader.getBoundingBox())) {
                leader.setInfoMode(state);
            }
        });
    }

    public MessagePatrolLeaderSetInfoMode fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.state = buf.readByte();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeByte(this.state);
    }
}
