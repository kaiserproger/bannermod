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

public class MessagePatrolLeaderSetWaitTime implements BannerModMessage<MessagePatrolLeaderSetWaitTime> {

    private UUID recruit;
    private int time;

    public MessagePatrolLeaderSetWaitTime() {
    }

    public MessagePatrolLeaderSetWaitTime(UUID recruit, int time) {
        this.recruit = recruit;
        this.time = time;
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
                    && RecruitCommandAuthority.canDirectlyControl(player, leader)
                    && player.getBoundingBox().inflate(100.0D).intersects(leader.getBoundingBox())) {
                leader.setWaitTimeInMin(this.time);
            }
        });
    }

    public MessagePatrolLeaderSetWaitTime fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.time = buf.readInt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeInt(this.time);
    }
}
