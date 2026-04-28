package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.army.command.CommandIntent;
import com.talhanation.bannermod.army.command.CommandIntentDispatcher;
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

/**
 * World-map engagement order. Sent when the player picks an attack-style action
 * from the right-click context menu over a hostile {@link com.talhanation.bannermod.army.map.FormationMapContact}.
 *
 * <p>The semantics of each {@link Mode}:</p>
 * <ul>
 *   <li>{@link Mode#ADVANCE_IN_FORMATION} — march to the enemy position keeping the player's
 *       saved formation. Recruits engage when their normal combat AI picks a target in range.</li>
 *   <li>{@link Mode#FREE_CHARGE} — drop formation, set aggro to AGGRESSIVE, and assign each
 *       recruit a target from the AABB around the enemy formation's position. Recruits will
 *       chase their assigned target via the existing combat-follow AI.</li>
 * </ul>
 */
public class MessageFormationMapEngage implements Message<MessageFormationMapEngage> {

    public enum Mode {
        ADVANCE_IN_FORMATION,
        FREE_CHARGE,
        OPEN_FIRE;

        static Mode fromOrdinal(int ord) {
            Mode[] values = values();
            if (ord < 0 || ord >= values.length) return ADVANCE_IN_FORMATION;
            return values[ord];
        }
    }

    private static final double FREE_CHARGE_TARGET_RADIUS = 16.0D;
    private static final int AGGRO_AGGRESSIVE = 1;

    private UUID actorContactId;
    private UUID actorGroupId;
    private UUID enemyContactId;
    private BlockPos target;
    private Mode mode;

    public MessageFormationMapEngage() {
    }

    public MessageFormationMapEngage(UUID actorContactId, UUID actorGroupId, UUID enemyContactId, BlockPos target, Mode mode) {
        this.actorContactId = actorContactId;
        this.actorGroupId = actorGroupId;
        this.enemyContactId = enemyContactId;
        this.target = target;
        this.mode = mode;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || target == null || actorContactId == null || mode == null) return;

        ServerLevel level = sender.serverLevel();
        BlockPos engagementTarget = new BlockPos(
                target.getX(),
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ()),
                target.getZ()
        );

        List<AbstractRecruitEntity> recruits = new ArrayList<>(resolveActors(sender));
        recruits.removeIf(recruit -> !CommandHierarchy.canCommand(sender, recruit));
        if (recruits.isEmpty()) return;

        switch (mode) {
            case ADVANCE_IN_FORMATION -> dispatchAdvance(sender, level, recruits, engagementTarget);
            case FREE_CHARGE -> dispatchFreeCharge(sender, recruits, engagementTarget);
            case OPEN_FIRE -> dispatchOpenFire(sender, recruits, engagementTarget);
        }
    }

    private void dispatchOpenFire(ServerPlayer sender, List<AbstractRecruitEntity> recruits, BlockPos engagementTarget) {
        CommandEvents.onStrategicFireCommandAt(sender.getUUID(), recruits, actorGroupId, engagementTarget, true);
    }

    private void dispatchAdvance(ServerPlayer sender, ServerLevel level, List<AbstractRecruitEntity> recruits, BlockPos engagementTarget) {
        int formation = CommandEvents.getSavedFormation(sender);
        CommandIntent intent = new CommandIntent.Movement(
                level.getGameTime(),
                CommandIntentPriority.NORMAL,
                false,
                MovementCommandState.MOVE_TO_POSITION,
                formation,
                false,
                Vec3.atCenterOf(engagementTarget)
        );
        CommandIntentDispatcher.dispatch(sender, intent, recruits);
    }

    private void dispatchFreeCharge(ServerPlayer sender, List<AbstractRecruitEntity> recruits, BlockPos engagementTarget) {
        for (AbstractRecruitEntity recruit : recruits) {
            recruit.setAggroState(AGGRO_AGGRESSIVE);
        }
        CommandEvents.onAttackCommandAt(sender, sender.getUUID(), recruits, actorGroupId,
                engagementTarget, FREE_CHARGE_TARGET_RADIUS);
    }

    private List<AbstractRecruitEntity> resolveActors(ServerPlayer sender) {
        if (actorGroupId != null) {
            return RecruitCommandTargetResolver.resolveGroupTargets(sender, sender.getUUID(), actorGroupId, "formation-map-engage");
        }
        AbstractRecruitEntity recruit = RecruitIndex.instance().get(sender.serverLevel(), actorContactId);
        if (recruit == null || !CommandHierarchy.canCommand(sender, recruit)) {
            return List.of();
        }
        return List.of(recruit);
    }

    @Override
    public MessageFormationMapEngage fromBytes(FriendlyByteBuf buf) {
        this.actorContactId = buf.readUUID();
        this.actorGroupId = buf.readBoolean() ? buf.readUUID() : null;
        this.enemyContactId = buf.readBoolean() ? buf.readUUID() : null;
        this.target = buf.readBlockPos();
        this.mode = Mode.fromOrdinal(buf.readByte());
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(actorContactId);
        buf.writeBoolean(actorGroupId != null);
        if (actorGroupId != null) buf.writeUUID(actorGroupId);
        buf.writeBoolean(enemyContactId != null);
        if (enemyContactId != null) buf.writeUUID(enemyContactId);
        buf.writeBlockPos(target);
        buf.writeByte(mode.ordinal());
    }
}
