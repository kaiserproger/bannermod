package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandIntent;
import com.talhanation.bannermod.army.command.CommandIntentDispatcher;
import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.army.command.CommandIntentPriority;
import com.talhanation.bannermod.army.command.MovementCommandState;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.events.CommandEvents;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageFormationMapMoveOrder implements Message<MessageFormationMapMoveOrder> {
    private UUID contactId;
    private UUID groupId;
    private BlockPos target;

    public MessageFormationMapMoveOrder() {
    }

    public MessageFormationMapMoveOrder(UUID contactId, UUID groupId, BlockPos target) {
        this.contactId = contactId;
        this.groupId = groupId;
        this.target = target;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || target == null || contactId == null) return;
        ServerLevel level = sender.serverLevel();
        BlockPos moveTarget = new BlockPos(
                target.getX(),
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ()),
                target.getZ()
        );

        List<AbstractRecruitEntity> recruits = new ArrayList<>(resolveTargets(sender));
        recruits.removeIf(recruit -> !CommandHierarchy.canCommand(sender, recruit));
        if (recruits.isEmpty()) return;

        int formation = CommandEvents.getSavedFormation(sender);
        CommandIntent intent = new CommandIntent.Movement(
                level.getGameTime(),
                CommandIntentPriority.NORMAL,
                false,
                MovementCommandState.MOVE_TO_POSITION,
                formation,
                false,
                Vec3.atCenterOf(moveTarget)
        );
        CommandIntentDispatcher.dispatch(sender, intent, recruits);
    }

    private List<AbstractRecruitEntity> resolveTargets(ServerPlayer sender) {
        if (groupId != null) {
            return RecruitCommandTargetResolver.resolveGroupTargets(sender, sender.getUUID(), groupId, "formation-map-move");
        }
        AbstractRecruitEntity recruit = RecruitIndex.instance().get(sender.serverLevel(), contactId);
        if (recruit == null || !CommandHierarchy.canCommand(sender, recruit)) {
            return List.of();
        }
        return List.of(recruit);
    }

    @Override
    public MessageFormationMapMoveOrder fromBytes(FriendlyByteBuf buf) {
        this.contactId = buf.readUUID();
        this.groupId = buf.readBoolean() ? buf.readUUID() : null;
        this.target = buf.readBlockPos();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(contactId);
        buf.writeBoolean(groupId != null);
        if (groupId != null) buf.writeUUID(groupId);
        buf.writeBlockPos(target);
    }
}
