package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.client.military.scenario.VisualScenarioClient;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MessageToClientRunVisualScenario implements BannerModMessage<MessageToClientRunVisualScenario> {
    private String scenario = "";
    private int recruitEntityId = -1;

    public MessageToClientRunVisualScenario() {
    }

    public MessageToClientRunVisualScenario(String scenario, int recruitEntityId) {
        this.scenario = scenario;
        this.recruitEntityId = recruitEntityId;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.clientbound();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void executeClientSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> VisualScenarioClient.start(this.scenario, this.recruitEntityId));
    }

    @Override
    public MessageToClientRunVisualScenario fromBytes(FriendlyByteBuf buf) {
        this.scenario = buf.readUtf(48);
        this.recruitEntityId = buf.readInt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.scenario == null ? "" : this.scenario);
        buf.writeInt(this.recruitEntityId);
    }
}
