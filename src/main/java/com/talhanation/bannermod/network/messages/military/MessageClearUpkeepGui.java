package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageClearUpkeepGui implements BannerModMessage<MessageClearUpkeepGui> {

    private UUID uuid;

    public MessageClearUpkeepGui(){
    }

    public MessageClearUpkeepGui(UUID uuid) {
        this.uuid = uuid;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.uuid, 16.0D);
            if (recruit != null) {
                recruit.clearUpkeepPos();
                recruit.clearUpkeepEntity();
            }
        });
    }

    public MessageClearUpkeepGui fromBytes(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
    }

}
