package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import com.talhanation.bannermod.entity.military.CaptainEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;

import java.util.UUID;

public class MessagePatrolLeaderAddWayPoint implements BannerModMessage<MessagePatrolLeaderAddWayPoint> {
    private UUID worker;
    private int x;
    private int y;
    private int z;

    public MessagePatrolLeaderAddWayPoint() {
    }

    public MessagePatrolLeaderAddWayPoint(UUID recruit, int x, int y, int z) {
        this.worker = recruit;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Entity entity = player.serverLevel().getEntity(this.worker);
            if (entity instanceof AbstractLeaderEntity leader
                    && leader.isAlive()
                    && RecruitCommandAuthority.canDirectlyControl(player, leader)
                    && player.getBoundingBox().inflate(100.0D).intersects(leader.getBoundingBox())) {
                this.addWayPoint(new BlockPos(x, y, z), player, leader);
            }
        });
    }

    private void addWayPoint(BlockPos pos, Player player, AbstractLeaderEntity leaderEntity) {
        BlockState state = leaderEntity.getCommandSenderWorld().getBlockState(pos);
        while (state.isAir()) {
            pos = pos.below();
            state = leaderEntity.getCommandSenderWorld().getBlockState(pos);
        }

        if (leaderEntity instanceof CaptainEntity captain && !state.is(Blocks.WATER)) {
            player.sendSystemMessage(TEXT_NOT_WATER_WAYPOINT(captain.getName().getString()));
        } else {
            leaderEntity.addWaypoint(pos);
            BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new MessageToClientUpdateLeaderScreen(leaderEntity.WAYPOINTS, leaderEntity.WAYPOINT_ITEMS, leaderEntity.getArmySize()));
        }
    }

    public MessagePatrolLeaderAddWayPoint fromBytes(FriendlyByteBuf buf) {
        this.worker = buf.readUUID();
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.worker);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
    }

    private MutableComponent TEXT_NOT_WATER_WAYPOINT(String name) {
        return Component.translatable("chat.recruits.text.notWaterWaypoint", name);
    }
}
