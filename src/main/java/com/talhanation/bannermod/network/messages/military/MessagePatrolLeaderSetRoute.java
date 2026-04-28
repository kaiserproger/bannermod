package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Client → Server: assigns a route to the leader AND delivers the full waypoint
 * data (positions + per-waypoint wait seconds). Routes are stored client-side,
 * so the positions must be included in the packet — the server cannot read the
 * client's filesystem.
 */
public class MessagePatrolLeaderSetRoute implements Message<MessagePatrolLeaderSetRoute> {

    private UUID recruit;
    @Nullable private UUID routeId;       // null = clear route
    private List<BlockPos> waypoints;     // ordered waypoint positions
    private List<Integer>  waitSeconds;   // parallel: wait time per waypoint (0 = none)

    public MessagePatrolLeaderSetRoute() {}

    /** Assign a route with its waypoint data. */
    public MessagePatrolLeaderSetRoute(UUID recruit,
                                       @Nullable UUID routeId,
                                       List<BlockPos> waypoints,
                                       List<Integer> waitSeconds) {
        this.recruit     = recruit;
        this.routeId     = routeId;
        this.waypoints   = waypoints;
        this.waitSeconds = waitSeconds;
    }

    /** Clear the route. */
    public MessagePatrolLeaderSetRoute(UUID recruit) {
        this(recruit, null, List.of(), List.of());
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        dispatchToServer(player, this.recruit, this.routeId, this.waypoints, this.waitSeconds);
    }

    public static void dispatchToServer(Player player, UUID recruitId, @Nullable UUID routeId, List<BlockPos> waypoints, List<Integer> waitSeconds) {
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

        ValidationResult result = validateSelection(selection, routeId, waypoints, waitSeconds);
        if (result != ValidationResult.OK) {
            BannerModMain.LOGGER.debug("Ignored patrol route command: {}", result);
            return;
        }

        if (routeId != null) {
            targetLeader.setRouteID(routeId);
        }
        else {
            targetLeader.clearRouteID();
        }
        targetLeader.loadRouteWaypointsFromData(waypoints, waitSeconds);
    }

    static ValidationResult validateSelection(CommandTargeting.SingleRecruitSelection selection, @Nullable UUID routeId, List<BlockPos> waypoints, List<Integer> waitSeconds) {
        if (!selection.isSuccess()) {
            return ValidationResult.INVALID_TARGET;
        }

        List<BlockPos> safeWaypoints = waypoints == null ? List.of() : waypoints;
        List<Integer> safeWaits = waitSeconds == null ? List.of() : waitSeconds;
        if (routeId == null) {
            if (!safeWaypoints.isEmpty() || !safeWaits.isEmpty()) {
                return ValidationResult.INVALID_ROUTE_DATA;
            }
            return ValidationResult.OK;
        }

        if (safeWaypoints.isEmpty() || safeWaypoints.size() != safeWaits.size()) {
            return ValidationResult.INVALID_ROUTE_DATA;
        }

        return ValidationResult.OK;
    }

    @Override
    public MessagePatrolLeaderSetRoute fromBytes(FriendlyByteBuf buf) {
        this.recruit     = buf.readUUID();
        boolean hasRoute = buf.readBoolean();
        this.routeId     = hasRoute ? buf.readUUID() : null;
        this.waypoints   = buf.readList(FriendlyByteBuf::readBlockPos);
        this.waitSeconds = buf.readList(FriendlyByteBuf::readVarInt);
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeBoolean(this.routeId != null);
        if (this.routeId != null) buf.writeUUID(this.routeId);
        buf.writeCollection(this.waypoints, FriendlyByteBuf::writeBlockPos);
        buf.writeCollection(this.waitSeconds, FriendlyByteBuf::writeVarInt);
    }

    enum ValidationResult {
        OK,
        INVALID_TARGET,
        INVALID_ROUTE_DATA
    }
}
