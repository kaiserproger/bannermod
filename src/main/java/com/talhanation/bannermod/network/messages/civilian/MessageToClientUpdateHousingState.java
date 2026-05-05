package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import com.talhanation.bannermod.society.client.NpcHousingClientState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MessageToClientUpdateHousingState implements BannerModMessage<MessageToClientUpdateHousingState> {
    private CompoundTag payload;

    public MessageToClientUpdateHousingState() {
    }

    public MessageToClientUpdateHousingState(CompoundTag payload) {
        this.payload = payload;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.clientbound();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void executeClientSide(BannerModNetworkContext context) {
        NpcHousingClientState.applyFromNbt(this.payload);
    }

    @Override
    public MessageToClientUpdateHousingState fromBytes(FriendlyByteBuf buf) {
        this.payload = buf.readNbt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.payload);
    }
}
