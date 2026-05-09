package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageOpenGovernorScreen implements BannerModMessage<MessageOpenGovernorScreen> {
    private UUID recruit;
    private boolean openMenu;

    public MessageOpenGovernorScreen() {
    }

    public MessageOpenGovernorScreen(UUID recruit, boolean openMenu) {
        this.recruit = recruit;
        this.openMenu = openMenu;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            AbstractRecruitEntity recruitEntity = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.recruit, 16.0D);
            if (recruitEntity != null) {
                if (this.openMenu) {
                    RecruitEvents.openGovernorScreen(player, recruitEntity);
                } else {
                    RecruitEvents.syncGovernorScreen(player, recruitEntity);
                }
            }
        });
    }

    @Override
    public MessageOpenGovernorScreen fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.openMenu = buf.readBoolean();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeBoolean(this.openMenu);
    }
}
