package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.ScoutEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageScoutTask implements BannerModMessage<MessageScoutTask> {

    private UUID recruit;
    private int state;

    public MessageScoutTask() {
    }
    public MessageScoutTask(UUID recruit, int state) {
        this.recruit = recruit;
        this.state = state;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;

            dispatchToServer(sender, this.recruit, this.state);
        });
    }

    public static void dispatchToServer(Player player, UUID recruitId, int state) {
        if (!(player.getCommandSenderWorld() instanceof ServerLevel level)) return;
        Entity entity = level.getEntity(recruitId);
        ScoutEntity targetScout = entity instanceof ScoutEntity scout
                && scout.distanceToSqr(player) <= CommandTargeting.GROUP_COMMAND_RADIUS * CommandTargeting.GROUP_COMMAND_RADIUS
                ? scout
                : null;
        List<ScoutEntity> list = targetScout == null ? List.of() : List.of(targetScout);
        CommandTargeting.SingleRecruitSelection selection = CommandTargeting.forSingleRecruit(
                player.getUUID(),
                player.getTeam() == null ? null : player.getTeam().getName(),
                player.hasPermissions(2),
                recruitId,
                list.stream().map(scout -> new CommandTargeting.RecruitSnapshot(
                        scout.getUUID(),
                        scout.getOwnerUUID(),
                        scout.getGroup(),
                        scout.getTeam() == null ? null : scout.getTeam().getName(),
                        scout.isOwned(),
                        scout.isAlive(),
                        scout.getListen(),
                        scout.distanceToSqr(player)
                )).toList()
        );

        ValidationResult result = validateSelection(selection, state);
        if (result != ValidationResult.OK) {
            BannerModMain.LOGGER.debug("Ignored scout task command: {}", result);
            return;
        }

        targetScout.startTask(ScoutEntity.State.fromIndex(state));
    }

    static ValidationResult validateSelection(CommandTargeting.SingleRecruitSelection selection, int state) {
        if (!selection.isSuccess()) {
            return ValidationResult.INVALID_TARGET;
        }

        try {
            ScoutEntity.State.fromIndex(state);
            return ValidationResult.OK;
        }
        catch (IllegalArgumentException e) {
            return ValidationResult.INVALID_STATE;
        }
    }
    public MessageScoutTask fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.state = buf.readInt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
        buf.writeInt(state);
    }

    enum ValidationResult {
        OK,
        INVALID_TARGET,
        INVALID_STATE
    }
}
