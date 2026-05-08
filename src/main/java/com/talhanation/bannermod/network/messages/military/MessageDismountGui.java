package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageDismountGui implements BannerModMessage<MessageDismountGui> {

    private UUID uuid;
    private UUID player;

    public MessageDismountGui() {
    }

    public MessageDismountGui(UUID player, UUID uuid) {
        this.player = player;
        this.uuid = uuid;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null) return;
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(serverPlayer, this.uuid, 16.0D);
            if (recruit != null && RecruitCommandAuthority.canDirectlyControl(serverPlayer, recruit)) {
                CommandEvents.onDismountButton(recruit.getOwnerUUID(), recruit, null);
            }
        });
    }

    public MessageDismountGui fromBytes(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.player = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUUID(player);
    }
}
