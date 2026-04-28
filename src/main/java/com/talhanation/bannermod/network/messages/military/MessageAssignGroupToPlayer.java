package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class MessageAssignGroupToPlayer implements Message<MessageAssignGroupToPlayer> {

    private UUID owner;
    private CompoundTag tag;
    private UUID groupUUID;
    private boolean keepTeam;

    public MessageAssignGroupToPlayer() {
    }

    public MessageAssignGroupToPlayer(UUID owner, RecruitsPlayerInfo newOwner, UUID groupUUID) {
        this.owner = owner;
        this.tag = newOwner.toNBT();
        this.groupUUID = groupUUID;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        RecruitsPlayerInfo newOwner = RecruitsPlayerInfo.getFromNBT(tag);
        transferGroupToPlayer(player, groupUUID, newOwner);
    }

    static boolean transferGroupToPlayer(ServerPlayer sender, UUID groupUUID, RecruitsPlayerInfo requestedNewOwner) {
        if (sender == null || requestedNewOwner == null) {
            return false;
        }

        ServerLevel serverLevel = sender.serverLevel();
        RecruitsGroup group = RecruitCommandAuthority.ownedGroup(sender, groupUUID);
        Player trustedNewOwner = serverLevel.getPlayerByUUID(requestedNewOwner.getUUID());
        if (group == null || trustedNewOwner == null) {
            return false;
        }

        List<AbstractRecruitEntity> list = loadedGroupMembers(serverLevel, sender, group);
        for (AbstractRecruitEntity recruit : list) {
            if (!RecruitCommandAuthority.canDirectlyControl(sender, recruit)) {
                return false;
            }
        }

        group.setPlayer(trustedNewOwner);
        for(AbstractRecruitEntity recruit : list){
            recruit.setOwnerUUID(Optional.of(trustedNewOwner.getUUID()));
            recruit.needsGroupUpdate = true;
        }

        RecruitEvents.recruitsGroupsManager.save(serverLevel);
        RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(sender);
        RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(serverLevel, trustedNewOwner.getUUID());
        return true;
    }

    private static List<AbstractRecruitEntity> loadedGroupMembers(ServerLevel serverLevel, ServerPlayer sender, RecruitsGroup group) {
        List<AbstractRecruitEntity> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID memberId : group.members) {
            Entity entity = serverLevel.getEntity(memberId);
            if (entity instanceof AbstractRecruitEntity recruit && group.getUUID().equals(recruit.getGroup())) {
                result.add(recruit);
                seen.add(recruit.getUUID());
            }
        }

        List<AbstractRecruitEntity> indexed = RecruitIndex.instance().groupMembersInRange(
                serverLevel,
                group.getUUID(),
                sender.position(),
                100.0D
        );
        if (indexed == null) {
            RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
            indexed = serverLevel.getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    sender.getBoundingBox().inflate(100D)
            );
        }

        for (AbstractRecruitEntity recruit : indexed) {
            if (group.getUUID().equals(recruit.getGroup()) && seen.add(recruit.getUUID())) {
                result.add(recruit);
            }
        }

        return result;
    }

    public MessageAssignGroupToPlayer fromBytes(FriendlyByteBuf buf) {
        this.owner = buf.readUUID();
        this.tag = buf.readNbt();
        this.groupUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(owner);
        buf.writeNbt(tag);
        buf.writeUUID(groupUUID);
    }
}
