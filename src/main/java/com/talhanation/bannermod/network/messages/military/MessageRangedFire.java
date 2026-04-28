package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageRangedFire implements Message<MessageRangedFire> {

    private UUID player;
    private UUID group;
    private boolean should;

    public MessageRangedFire(){
    }

    public MessageRangedFire(UUID player, UUID group, boolean shields) {
        this.player = player;
        this.group = group;
        this.should = shields;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer sender = Objects.requireNonNull(context.getSender());
        dispatchToServer(sender, this.player, this.group, this.should);
    }

    public static void dispatchToServer(ServerPlayer sender, UUID player, UUID group, boolean should) {
        UUID actorUuid = authorizedPlayerUuid(sender.getUUID(), player);
        AABB commandBox = sender.getBoundingBox().inflate(100);
        List<AbstractRecruitEntity> list = RecruitIndex.instance().groupInRange(
                sender.getCommandSenderWorld(),
                group,
                sender.position(),
                200.0D
        );
        if (list == null) {
            RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
            list = sender.getCommandSenderWorld().getEntitiesOfClass(AbstractRecruitEntity.class, commandBox);
        } else {
            list.removeIf(recruit -> !recruit.getBoundingBox().intersects(commandBox));
        }
        for (AbstractRecruitEntity recruits : list) {
            CommandEvents.onRangedFireCommand(sender, actorUuid, recruits, group, should);
        }
    }

    static UUID authorizedPlayerUuid(UUID senderUuid, UUID ignoredWireUuid) {
        return senderUuid;
    }

    public MessageRangedFire fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.group = buf.readUUID();
        this.should = buf.readBoolean();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUUID(this.group);
        buf.writeBoolean(this.should);
    }

}
