package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.ICompanion;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.NPCArmy;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageAssignGroupToCompanion implements BannerModMessage<MessageAssignGroupToCompanion> {

    private UUID ownerUUID;
    private UUID companionUUID;
    public MessageAssignGroupToCompanion(){
    }

    public MessageAssignGroupToCompanion(UUID owner, UUID companionUUID) {
        this.ownerUUID = owner;
        this.companionUUID = companionUUID;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null) return;
            ServerLevel serverLevel =  serverPlayer.serverLevel();

            Entity entity = serverLevel.getEntity(this.companionUUID);
            if (!(entity instanceof AbstractLeaderEntity companionEntity)
                    || !serverPlayer.getBoundingBox().inflate(100).intersects(companionEntity.getBoundingBox())
                    || !canAssignCompanionGroup(serverPlayer, companionEntity)) {
                return;
            }


            RecruitsGroup group = RecruitCommandAuthority.ownedGroup(serverPlayer, companionEntity.getGroup());
            if(group == null) return;

            List<AbstractRecruitEntity> recruits = RecruitIndex.instance().groupMembersInRange(
                    serverLevel,
                    group.getUUID(),
                    serverPlayer.position(),
                    100.0D
            );
            List<AbstractRecruitEntity> list;
            if (recruits == null) {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                list = serverLevel.getEntitiesOfClass(AbstractRecruitEntity.class, serverPlayer.getBoundingBox().inflate(100));
                list.removeIf(recruit -> (recruit.getGroup() == null || !recruit.getGroup().equals(group.getUUID()))
                        || recruit.getUUID().equals(this.companionUUID));
            } else {
                list = new ArrayList<>();
                for (AbstractRecruitEntity recruit : recruits) {
                    if (!recruit.getUUID().equals(this.companionUUID)) {
                        list.add(recruit);
                    }
                }
            }

            list.removeIf(recruit -> !RecruitCommandAuthority.canDirectlyControl(serverPlayer, recruit));
            if (list.isEmpty()) {
                return;
            }

            for (AbstractRecruitEntity recruit : list) {
                ICompanion.assignToLeaderCompanion(companionEntity, recruit);
            }
            List<LivingEntity> armyMembers = new ArrayList<>(list);
            companionEntity.army = new NPCArmy(serverLevel, armyMembers, null);
            group.leaderUUID = companionUUID;
            companionEntity.setGroupUUID(group.getUUID());

            RecruitEvents.groupsManager().broadCastGroupsToPlayer(serverPlayer);
        });
    }

    static boolean canAssignCompanionGroup(ServerPlayer player, AbstractLeaderEntity companionEntity) {
        return RecruitCommandAuthority.canDirectlyControl(player, companionEntity)
                && RecruitCommandAuthority.ownedGroup(player, companionEntity == null ? null : companionEntity.getGroup()) != null;
    }

    public MessageAssignGroupToCompanion fromBytes(FriendlyByteBuf buf) {
        this.ownerUUID = buf.readUUID();
        this.companionUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.ownerUUID);
        buf.writeUUID(this.companionUUID);
    }

}
