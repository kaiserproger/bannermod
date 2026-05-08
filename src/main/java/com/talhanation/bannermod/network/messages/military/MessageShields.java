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
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MessageShields implements BannerModMessage<MessageShields> {

    private UUID player;
    private UUID group;
    private boolean should;

    public MessageShields() {
    }

    public MessageShields(UUID player, UUID group, boolean shields) {
        this.player = player;
        this.group = group;
        this.should = shields;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            dispatchToServer(player, this.player, this.group, this.should);
        });
    }

    public static void dispatchToServer(ServerPlayer player, UUID playerUuid, UUID group, boolean should) {
        dispatchToServer((Player) player, playerUuid, group, should);
    }

    public static void dispatchToServer(Player player, UUID playerUuid, UUID group, boolean should) {
        List<AbstractRecruitEntity> recruits = resolveTargets(player, playerUuid, group);
        if (recruits.isEmpty()) {
            return;
        }

        for (AbstractRecruitEntity recruit : recruits) {
            CommandEvents.onShieldsCommand(player, player.getUUID(), recruit, group, should);
        }
    }

    private static List<AbstractRecruitEntity> resolveTargets(Player player, UUID playerUuid, UUID group) {
        return RecruitCommandTargetResolver.resolveGroupTargets(player, playerUuid, group, "shields");
    }

    public MessageShields fromBytes(FriendlyByteBuf buf) {
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
