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


public class MessagePatrolLeaderSetCycle implements BannerModMessage<MessagePatrolLeaderSetCycle> {

    private UUID recruit;
    private boolean cycle;

    public MessagePatrolLeaderSetCycle() {
    }

    public MessagePatrolLeaderSetCycle(UUID recruit, boolean cycle) {
        this.recruit = recruit;
        this.cycle = cycle;
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
                    && leader.distanceToSqr(player) <= 100.0D * 100.0D) {
                leader.setCycle(this.cycle);
            }
        });
    }

    public MessagePatrolLeaderSetCycle fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.cycle = buf.readBoolean();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeBoolean(this.cycle);
    }
}
