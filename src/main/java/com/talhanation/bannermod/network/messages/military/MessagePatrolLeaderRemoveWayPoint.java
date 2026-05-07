package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;

import java.util.Objects;
import java.util.UUID;

public class MessagePatrolLeaderRemoveWayPoint implements BannerModMessage<MessagePatrolLeaderRemoveWayPoint> {
    private UUID worker;

    public MessagePatrolLeaderRemoveWayPoint() {
    }

    public MessagePatrolLeaderRemoveWayPoint(UUID recruit) {
        this.worker = recruit;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = Objects.requireNonNull(context.getSender());
            Entity entity = player.serverLevel().getEntity(this.worker);
            if (entity instanceof AbstractLeaderEntity leader
                    && leader.isAlive()
                    && RecruitCommandAuthority.canDirectlyControl(player, leader)
                    && player.getBoundingBox().inflate(100.0D).intersects(leader.getBoundingBox())) {
                this.removeLastWayPoint(player, leader);
            }
        });
    }

    private void removeLastWayPoint(ServerPlayer player, AbstractLeaderEntity leaderEntity) {
        if (!leaderEntity.WAYPOINTS.isEmpty()) leaderEntity.WAYPOINTS.pop();
        if (!leaderEntity.WAYPOINT_ITEMS.isEmpty()) leaderEntity.WAYPOINT_ITEMS.pop();

        BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(() -> player), new MessageToClientUpdateLeaderScreen(leaderEntity.WAYPOINTS, leaderEntity.WAYPOINT_ITEMS, leaderEntity.getArmySize()));
    }

    public MessagePatrolLeaderRemoveWayPoint fromBytes(FriendlyByteBuf buf) {
        this.worker = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.worker);
    }
}
