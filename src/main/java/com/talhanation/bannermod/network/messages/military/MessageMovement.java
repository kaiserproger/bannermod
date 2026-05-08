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

import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class MessageMovement implements BannerModMessage<MessageMovement> {

    private UUID player_uuid;
    private int state;
    private UUID group;
    private int formation;
    private boolean tight;

    public MessageMovement(){
    }

    public MessageMovement(UUID player_uuid, int state, UUID group, int formation, boolean tight) {
        this.player_uuid = player_uuid;
        this.state  = state;
        this.group  = group;
        this.formation = formation;
        this.tight = tight;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        ServerPlayer sender = context.getSender();
        if (sender == null) return;
        if (!com.talhanation.bannermod.network.throttle.PacketRateLimiter.shared()
                .tryAcquire(sender.getUUID(), MessageMovement.class)) {
            RuntimeProfilingCounters.increment("network.rate_limit.dropped.movement");
            return;
        }
        context.enqueueWork(() -> {
            dispatchToServer(sender, this.player_uuid, this.group, this.state, this.formation, this.tight);
        });
    }

    public static void dispatchToServer(Player sender, UUID playerUuid, UUID group, int state, int formation, boolean tight) {
        List<AbstractRecruitEntity> list = resolveTargets(sender, playerUuid, group);
        if (list.isEmpty()) {
            return;
        }
        long gameTime = sender.getCommandSenderWorld().getGameTime();
        CommandIntent intent = new CommandIntent.Movement(
                gameTime,
                CommandIntentPriority.NORMAL,
                false,
                state,
                formation,
                tight,
                null
        );
        ServerPlayer serverSender = sender instanceof ServerPlayer sp ? sp : null;
        CommandIntentDispatcher.dispatch(serverSender, intent, list);
    }

    private static List<AbstractRecruitEntity> resolveTargets(Player sender, UUID playerUuid, UUID group) {
        return RecruitCommandTargetResolver.resolveGroupTargets(sender, playerUuid, group, "movement");
    }

    public MessageMovement fromBytes(FriendlyByteBuf buf) {
        this.player_uuid = buf.readUUID();
        this.state = buf.readInt();
        this.group = buf.readUUID();
        this.formation = buf.readInt();
        this.tight = buf.readBoolean();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player_uuid);
        buf.writeInt(this.state);
        buf.writeUUID(this.group);
        buf.writeInt(this.formation);
        buf.writeBoolean(this.tight);
    }

}
