package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageRest implements BannerModMessage<MessageRest> {

    private UUID player;
    private UUID group;
    private boolean should;

    public MessageRest(){
    }

    public MessageRest(UUID player, UUID group, boolean should) {
        this.player = player;
        this.group = group;
        this.should = should;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null) return;
            dispatchToServer(serverPlayer, this.player, this.group, this.should);
        });
    }

    public static void dispatchToServer(ServerPlayer serverPlayer, UUID playerUuid, UUID group, boolean should) {
        List<AbstractRecruitEntity> list = RecruitCommandTargetResolver.resolveGroupTargets(serverPlayer, playerUuid, group, "rest");
        for (AbstractRecruitEntity recruits : list) {
                CommandEvents.onRestCommand(serverPlayer, recruits.getOwnerUUID(), recruits, group, should);
        }
    }
    public MessageRest fromBytes(FriendlyByteBuf buf) {
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
