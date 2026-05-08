package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandIntent;
import com.talhanation.bannermod.army.command.CommandIntentDispatcher;
import com.talhanation.bannermod.army.command.CommandIntentPriority;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageFollowGui implements BannerModMessage<MessageFollowGui> {

    private int state;
    private UUID uuid;

    public MessageFollowGui() {
    }

    public MessageFollowGui(int state, UUID uuid) {
        this.state = state;
        this.uuid = uuid;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null) return;
            dispatchToServer(serverPlayer, this.uuid, this.state);
        });
    }

    public static void dispatchToServer(ServerPlayer serverPlayer, UUID recruitUuid, int state) {
        AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(serverPlayer, recruitUuid, 16.0D);
        if (recruit == null) {
            return;
        }

        CommandTargeting.SingleRecruitSelection selection = CommandTargeting.forSingleRecruit(
                serverPlayer.getUUID(),
                serverPlayer.getTeam() == null ? null : serverPlayer.getTeam().getName(),
                serverPlayer.hasPermissions(2),
                recruitUuid,
                List.of(new CommandTargeting.RecruitSnapshot(
                        recruit.getUUID(),
                        recruit.getOwnerUUID(),
                        recruit.getGroup(),
                        recruit.getTeam() == null ? null : recruit.getTeam().getName(),
                        recruit.isOwned(),
                        recruit.isAlive(),
                        recruit.getListen(),
                        recruit.distanceToSqr(serverPlayer)
                ))
        );

        if (!selection.isSuccess()) {
            return;
        }

        CommandIntent intent = new CommandIntent.Movement(
                serverPlayer.getCommandSenderWorld().getGameTime(),
                CommandIntentPriority.NORMAL,
                false,
                state,
                0,
                false,
                null
        );
        CommandIntentDispatcher.dispatch(serverPlayer, intent, List.of(recruit));
    }

    public MessageFollowGui fromBytes(FriendlyByteBuf buf) {
        this.state = buf.readInt();
        this.uuid = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(state);
        buf.writeUUID(uuid);
    }
}
