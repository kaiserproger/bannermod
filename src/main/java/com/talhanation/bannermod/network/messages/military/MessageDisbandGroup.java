package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageDisbandGroup implements BannerModMessage<MessageDisbandGroup> {

    private UUID owner;
    private UUID groupUUID;
    private boolean keepTeam;

    public MessageDisbandGroup() {
    }

    public MessageDisbandGroup(UUID owner, UUID groupUUID, boolean keepTeam) {
        this.owner = owner;
        this.groupUUID = groupUUID;
        this.keepTeam = keepTeam;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            RecruitsGroup group = RecruitEvents.groupsManager().getGroup(groupUUID);
            if(group == null) return;

            group.setDisbandContext(new RecruitsGroup.DisbandContext(true, keepTeam, true));

            RecruitEvents.groupsManager().broadCastGroupsToPlayer(player);

            List<AbstractRecruitEntity> list = null;
            if (player.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
                list = RecruitIndex.instance().allInBox(serverLevel, player.getBoundingBox().inflate(100D), false);
            }
            if (list == null) {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                list = player.getCommandSenderWorld().getEntitiesOfClass(
                        AbstractRecruitEntity.class,
                        player.getBoundingBox().inflate(100D)
                );
            }

            for(AbstractRecruitEntity recruit : list){
                recruit.needsGroupUpdate = true;
            }
        });
    }

    public MessageDisbandGroup fromBytes(FriendlyByteBuf buf) {
        this.owner = buf.readUUID();
        this.groupUUID = buf.readUUID();
        this.keepTeam = buf.readBoolean();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(owner);
        buf.writeUUID(groupUUID);
        buf.writeBoolean(keepTeam);
    }
}
