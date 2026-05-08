package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageAssignNearbyRecruitsInGroup implements BannerModMessage<MessageAssignNearbyRecruitsInGroup> {

    private UUID groupUUID;

    public MessageAssignNearbyRecruitsInGroup() {
    }

    public MessageAssignNearbyRecruitsInGroup(UUID group) {
        this.groupUUID = group;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            RecruitsGroup newGroup = RecruitEvents.groupsManager().getGroup(groupUUID);
            if(newGroup == null) return;

            List<AbstractRecruitEntity> recruits = RecruitIndex.instance().ownerInRange(
                    player.getCommandSenderWorld(),
                    player.getUUID(),
                    player.position(),
                    100.0D
            );
            if (recruits == null) {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                recruits = player.getCommandSenderWorld().getEntitiesOfClass(
                        AbstractRecruitEntity.class,
                        player.getBoundingBox().inflate(100),
                        (recruit) -> recruit.isEffectedByCommand(player.getUUID())
                );
            }
            recruits.forEach((recruit) -> {
                if (recruit.isEffectedByCommand(player.getUUID())) {
                    this.setGroup(recruit, newGroup);
                }
            });

            RecruitEvents.groupsManager().addOrUpdateGroup(player.serverLevel(), player, newGroup);

            RecruitEvents.groupsManager().broadCastGroupsToPlayer(player);
        });
    }

    public void setGroup(AbstractRecruitEntity recruit, RecruitsGroup group){
        if(recruit.getGroupUUID().isPresent() && recruit.getGroupUUID().get().equals(group)){
            return;
        }

        group.addMember(recruit.getUUID());
        RecruitsGroup oldGroup = RecruitEvents.groupsManager().getGroup(recruit.getGroup());
        if(oldGroup != null) oldGroup.removeMember(recruit.getUUID());

        recruit.setGroupUUID(groupUUID);
    }

    public MessageAssignNearbyRecruitsInGroup fromBytes(FriendlyByteBuf buf) {
        this.groupUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(groupUUID);
    }
}
