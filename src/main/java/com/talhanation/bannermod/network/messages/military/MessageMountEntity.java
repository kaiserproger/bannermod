package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandIntent;
import com.talhanation.bannermod.army.command.CommandIntentDispatcher;
import com.talhanation.bannermod.army.command.CommandIntentPriority;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageMountEntity implements BannerModMessage<MessageMountEntity> {

    private UUID uuid;
    private UUID target;
    private UUID group;

    public MessageMountEntity() {
    }

    public MessageMountEntity(UUID uuid, UUID target, UUID group) {
        this.uuid = uuid;
        this.target = target;
        this.group = group;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = Objects.requireNonNull(context.getSender());
            Entity mount = player.serverLevel().getEntity(target);
            if (mount == null
                    || mount.distanceToSqr(player) > 100.0D * 100.0D
                    || !RecruitsServerConfig.MountWhiteList.get().contains(mount.getEncodeId())) {
                return;
            }

            UUID actorUuid = authorizedPlayerUuid(player.getUUID(), this.uuid);
            List<AbstractRecruitEntity> recruits = this.group == null
                    ? RecruitIndex.instance().ownerInRange(player.getCommandSenderWorld(), actorUuid, player.position(), 100.0D)
                    : RecruitIndex.instance().groupInRange(player.getCommandSenderWorld(), this.group, player.position(), 100.0D);
            if (recruits == null) {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                recruits = player.getCommandSenderWorld().getEntitiesOfClass(
                        AbstractRecruitEntity.class,
                        player.getBoundingBox().inflate(100),
                        (recruit) -> recruit.isEffectedByCommand(actorUuid, group)
                );
            }
            recruits = recruits.stream()
                    .filter(recruit -> recruit != null
                            && isAuthorizedOwner(recruit.getOwnerUUID(), actorUuid)
                            && recruit.isEffectedByCommand(actorUuid, group))
                    .toList();
            CommandIntentDispatcher.dispatch(player, new CommandIntent.SiegeMachine(
                    player.level().getGameTime(), CommandIntentPriority.HIGH, false, target, group, false), recruits);
        });
    }

    static UUID authorizedPlayerUuid(UUID senderUuid, UUID ignoredWireUuid) {
        return senderUuid;
    }

    static boolean isAuthorizedOwner(UUID recruitOwnerUuid, UUID actorUuid) {
        return actorUuid != null && actorUuid.equals(recruitOwnerUuid);
    }

    public MessageMountEntity fromBytes(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.target = buf.readUUID();
        this.group = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUUID(target);
        buf.writeUUID(group);
    }
}
