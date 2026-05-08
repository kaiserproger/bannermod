package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Client → Server: assigns a group UUID to a leader entity so the
 * subsequent {@link MessageAssignGroupToCompanion} knows which group to use.
 */
public class MessageSetLeaderGroup implements BannerModMessage<MessageSetLeaderGroup> {

    private UUID leaderUUID;
    @Nullable
    private UUID groupUUID;

    public MessageSetLeaderGroup() {}

    public MessageSetLeaderGroup(UUID leaderUUID, @Nullable UUID groupUUID) {
        this.leaderUUID = leaderUUID;
        this.groupUUID  = groupUUID;
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
            Entity entity = player.serverLevel().getEntity(this.leaderUUID);
            if (!(entity instanceof AbstractLeaderEntity leader)
                    || !leader.isAlive()
                    || !player.getBoundingBox().inflate(100D).intersects(leader.getBoundingBox())) {
                return;
            }
            if (!canApplyLeaderGroup(player, leader, groupUUID)) {
                return;
            }
            if (groupUUID == null) {
                leader.setGroupUUID(null);
                return;
            }
            RecruitsGroup group = RecruitCommandAuthority.ownedGroup(player, groupUUID);
            if (group == null) return;
            leader.setGroupUUID(group.getUUID());
            RecruitEvents.groupsManager().broadCastGroupsToPlayer(player);
        });
    }

    static boolean canApplyLeaderGroup(ServerPlayer player, AbstractLeaderEntity leader, @Nullable UUID groupUUID) {
        if (!RecruitCommandAuthority.canDirectlyControl(player, leader)) {
            return false;
        }
        return groupUUID == null || RecruitCommandAuthority.ownedGroup(player, groupUUID) != null;
    }

    @Override
    public MessageSetLeaderGroup fromBytes(FriendlyByteBuf buf) {
        this.leaderUUID = buf.readUUID();
        boolean hasGroup = buf.readBoolean();
        this.groupUUID  = hasGroup ? buf.readUUID() : null;
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.leaderUUID);
        buf.writeBoolean(this.groupUUID != null);
        if (this.groupUUID != null) buf.writeUUID(this.groupUUID);
    }
}
