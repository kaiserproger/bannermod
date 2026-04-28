package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageUpkeepPos implements Message<MessageUpkeepPos> {

    private UUID player;
    private UUID group;
    private BlockPos pos;

    public MessageUpkeepPos() {
    }

    public MessageUpkeepPos(UUID player, UUID group, BlockPos pos) {
        this.player = player;
        this.group = group;
        this.pos = pos;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        dispatchToServer(player, this.player, this.group, this.pos);
    }

    public static void dispatchToServer(ServerPlayer sender, UUID playerUuid, UUID group, BlockPos pos) {
        UUID actorUuid = authorizedPlayerUuid(sender.getUUID(), playerUuid);
        List<AbstractRecruitEntity> recruits = group == null
                ? RecruitIndex.instance().ownerInRange(sender.getCommandSenderWorld(), actorUuid, sender.position(), 100.0D)
                : RecruitCommandTargetResolver.resolveGroupTargets(sender, playerUuid, group, "upkeep-pos");
        if (recruits == null) {
            RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
            recruits = sender.getCommandSenderWorld().getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    sender.getBoundingBox().inflate(100)
            );
        }
        recruits.forEach((recruit) -> CommandEvents.onUpkeepCommand(
                actorUuid,
                recruit,
                group,
                false,
                null,
                pos)
        );
    }

    static UUID authorizedPlayerUuid(UUID senderUuid, UUID ignoredWireUuid) {
        return senderUuid;
    }

    public MessageUpkeepPos fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.group = buf.readUUID();
        this.pos = buf.readBlockPos();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUUID(this.group);
        buf.writeBlockPos(this.pos);
    }
}
