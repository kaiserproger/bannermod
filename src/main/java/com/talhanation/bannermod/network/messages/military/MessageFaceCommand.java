package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandIntent;
import com.talhanation.bannermod.army.command.CommandIntentDispatcher;
import com.talhanation.bannermod.army.command.CommandIntentPriority;
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

public class MessageFaceCommand implements Message<MessageFaceCommand> {

    private UUID player_uuid;
    private UUID group;
    private int formation;
    private boolean tight;

    public MessageFaceCommand(){
    }

    public MessageFaceCommand(UUID player_uuid, UUID group, int formation, boolean tight) {
        this.player_uuid = player_uuid;
        this.group = group;
        this.formation = formation;
        this.tight = tight;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context){
        ServerPlayer sender = Objects.requireNonNull(context.getSender());
        dispatchToServer(sender, this.player_uuid, this.group, this.formation, this.tight);
    }

    public static void dispatchToServer(ServerPlayer sender, UUID playerUuid, UUID group, int formation, boolean tight) {
        AABB commandBox = sender.getBoundingBox().inflate(100);
        List<AbstractRecruitEntity> list = RecruitIndex.instance().groupInRange(
                sender.getCommandSenderWorld(),
                group,
                sender.position(),
                200.0D
        );
        if (list == null) {
            RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
            list = sender.getCommandSenderWorld().getEntitiesOfClass(
                AbstractRecruitEntity.class, commandBox);
        } else {
            list.removeIf(recruit -> !recruit.getBoundingBox().intersects(commandBox));
        }
        UUID actorUuid = authorizedPlayerUuid(sender.getUUID(), playerUuid);
        list.removeIf(recruit -> !recruit.isEffectedByCommand(actorUuid, group));

        long gameTime = sender.getCommandSenderWorld().getGameTime();
        CommandIntent intent = new CommandIntent.Face(
                gameTime,
                CommandIntentPriority.NORMAL,
                false,
                formation,
                tight
        );
        CommandIntentDispatcher.dispatch(sender, intent, list);
    }

    static UUID authorizedPlayerUuid(UUID senderUuid, UUID ignoredWireUuid) {
        return senderUuid;
    }

    public MessageFaceCommand fromBytes(FriendlyByteBuf buf) {
        this.player_uuid = buf.readUUID();
        this.group = buf.readUUID();
        this.formation = buf.readInt();
        this.tight = buf.readBoolean();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player_uuid);
        buf.writeUUID(this.group);
        buf.writeInt(this.formation);
        buf.writeBoolean(this.tight);
    }

}
