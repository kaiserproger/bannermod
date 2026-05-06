package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageProtectEntity implements BannerModMessage<MessageProtectEntity> {

    private UUID uuid;
    private UUID target;
    private UUID group;

    public MessageProtectEntity(){

    }

    public MessageProtectEntity(UUID uuid, UUID target, UUID group) {
        this.uuid = uuid;
        this.target = target;
        this.group = group;

    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer player = Objects.requireNonNull(context.getSender());
            UUID actorUuid = authorizedPlayerUuid(player.getUUID(), this.uuid);
            List<AbstractRecruitEntity> recruits = this.group == null
                    ? RecruitIndex.instance().ownerInRange(player.getCommandSenderWorld(), actorUuid, player.position(), 100.0D)
                    : RecruitIndex.instance().groupInRange(player.getCommandSenderWorld(), this.group, player.position(), 100.0D);
            if (recruits == null) {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                recruits = player.getCommandSenderWorld().getEntitiesOfClass(
                        AbstractRecruitEntity.class,
                        player.getBoundingBox().inflate(100)
                );
            }
            if (this.group == null) {
                recruits = recruits.stream()
                        .filter(recruit -> recruit != null && isAuthorizedOwner(recruit.getOwnerUUID(), actorUuid))
                        .toList();
            }
            recruits.forEach((recruit) -> CommandEvents.onProtectButton(actorUuid, recruit, target, group));
        });
    }

    static UUID authorizedPlayerUuid(UUID senderUuid, UUID ignoredWireUuid) {
        return senderUuid;
    }

    static boolean isAuthorizedOwner(UUID recruitOwnerUuid, UUID actorUuid) {
        return actorUuid != null && actorUuid.equals(recruitOwnerUuid);
    }

    public MessageProtectEntity fromBytes(FriendlyByteBuf buf) {
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
