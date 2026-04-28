package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessagePatrolLeaderSetPatrolState implements Message<MessagePatrolLeaderSetPatrolState> {
    private UUID recruit;
    private byte state;

    public MessagePatrolLeaderSetPatrolState() {
    }

    public MessagePatrolLeaderSetPatrolState(UUID recruit, byte state) {
        this.recruit = recruit;
        this.state = state;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        dispatchToServer(player, this.recruit, this.state);
    }

    public static void dispatchToServer(Player player, UUID recruitId, byte state) {
        if (!(player.getCommandSenderWorld() instanceof ServerLevel level)) return;
        Entity entity = level.getEntity(recruitId);
        AbstractLeaderEntity targetLeader = entity instanceof AbstractLeaderEntity leader
                && leader.distanceToSqr(player) <= CommandTargeting.GROUP_COMMAND_RADIUS * CommandTargeting.GROUP_COMMAND_RADIUS
                ? leader
                : null;
        List<AbstractLeaderEntity> leaders = targetLeader == null ? List.of() : List.of(targetLeader);
        CommandTargeting.SingleRecruitSelection selection = CommandTargeting.forSingleRecruit(
                player.getUUID(),
                player.getTeam() == null ? null : player.getTeam().getName(),
                player.hasPermissions(2),
                recruitId,
                leaders.stream().map(leader -> new CommandTargeting.RecruitSnapshot(
                        leader.getUUID(),
                        leader.getOwnerUUID(),
                        leader.getGroup(),
                        leader.getTeam() == null ? null : leader.getTeam().getName(),
                        leader.isOwned(),
                        leader.isAlive(),
                        leader.getListen(),
                        leader.distanceToSqr(player)
                )).toList()
        );

        ValidationResult result = validateSelection(selection, state);
        if (result != ValidationResult.OK) {
            BannerModMain.LOGGER.debug("Ignored patrol state command: {}", result);
            return;
        }

        setState(targetLeader, state);
    }

    static ValidationResult validateSelection(CommandTargeting.SingleRecruitSelection selection, byte state) {
        if (!selection.isSuccess()) {
            return ValidationResult.INVALID_TARGET;
        }

        try {
            AbstractLeaderEntity.State.fromIndex(state);
            return ValidationResult.OK;
        }
        catch (IllegalArgumentException e) {
            return ValidationResult.INVALID_STATE;
        }
    }

    private static void setState(AbstractLeaderEntity leader, byte state) {
        AbstractLeaderEntity.State leaderState = AbstractLeaderEntity.State.fromIndex(state);
        switch (leaderState) {
            case PATROLLING -> leader.setFollowState(0);
            case STOPPED, PAUSED -> leader.setFollowState(1);
        }
        leader.setPatrolState(leaderState);
        leader.currentWaypoint = null;
    }

    public MessagePatrolLeaderSetPatrolState fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.state = buf.readByte();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeByte(this.state);
    }

    enum ValidationResult {
        OK,
        INVALID_TARGET,
        INVALID_STATE
    }
}
