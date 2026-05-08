package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageUpkeepEntity implements BannerModMessage<MessageUpkeepEntity> {

    private UUID player_uuid;
    private UUID target;
    private UUID group;

    public MessageUpkeepEntity() {
    }

    public MessageUpkeepEntity(UUID player_uuid, UUID target, UUID group) {
        this.player_uuid = player_uuid;
        this.target = target;
        this.group = group;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            UUID actorUuid = authorizedPlayerUuid(player.getUUID(), this.player_uuid);
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
            recruits.forEach(recruit -> CommandEvents.onUpkeepCommand(actorUuid, recruit, group, true, target, null));
        });
    }

    static UUID authorizedPlayerUuid(UUID senderUuid, UUID ignoredWireUuid) {
        return senderUuid;
    }

    public MessageUpkeepEntity fromBytes(FriendlyByteBuf buf) {
        this.player_uuid = buf.readUUID();
        this.target = buf.readUUID();
        this.group = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(player_uuid);
        buf.writeUUID(target);
        buf.writeUUID(group);
    }
}
