package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandIntent;
import com.talhanation.bannermod.army.command.CommandIntentDispatcher;
import com.talhanation.bannermod.army.command.CommandIntentPriority;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MessageAttack implements BannerModMessage<MessageAttack> {

    private UUID playerUuid;
    private UUID group;

    public MessageAttack() {
    }

    public MessageAttack(UUID playerUuid, UUID group) {
        this.playerUuid = playerUuid;
        this.group = group;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        ServerPlayer serverPlayer = context.getSender();
        if (serverPlayer == null) return;
        if (!com.talhanation.bannermod.network.throttle.PacketRateLimiter.shared()
                .tryAcquire(serverPlayer.getUUID(), MessageAttack.class)) {
            RuntimeProfilingCounters.increment("network.rate_limit.dropped.attack");
            return;
        }
        context.enqueueWork(() -> {
            dispatchToServer(serverPlayer, this.playerUuid, this.group);
        });
    }

    public static void dispatchToServer(ServerPlayer serverPlayer, UUID playerUuid, UUID group) {
        dispatchToServer((Player) serverPlayer, playerUuid, group);
    }

    public static void dispatchToServer(Player player, UUID playerUuid, UUID group) {
        List<AbstractRecruitEntity> list = resolveTargets(player, playerUuid, group);
        if (list.isEmpty()) {
            return;
        }

        long gameTime = player.getCommandSenderWorld().getGameTime();
        CommandIntent intent = new CommandIntent.Attack(
                gameTime,
                CommandIntentPriority.NORMAL,
                false,
                group
        );
        ServerPlayer serverSender = player instanceof ServerPlayer sp ? sp : null;
        CommandIntentDispatcher.dispatch(serverSender, intent, list);
    }

    private static List<AbstractRecruitEntity> resolveTargets(Player player, UUID playerUuid, UUID group) {
        return RecruitCommandTargetResolver.resolveGroupTargets(player, playerUuid, group, "attack");
    }

    public MessageAttack fromBytes(FriendlyByteBuf buf) {
        this.playerUuid = buf.readUUID();
        this.group = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.playerUuid);
        buf.writeUUID(this.group);
    }
}
