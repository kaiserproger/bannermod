package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.compat.workers.IVillagerWorker;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.ICompanion;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageOpenSpecialScreen implements BannerModMessage<MessageOpenSpecialScreen> {

    private UUID player;
    private UUID recruit;

    public MessageOpenSpecialScreen() {
        this.player = new UUID(0, 0);
    }

    public MessageOpenSpecialScreen(Player player, UUID recruit) {
        this.player = player.getUUID();
        this.recruit = recruit;
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
            if (!player.getUUID().equals(this.player)) {
                return;
            }

            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.recruit, 16.0D);
            if (recruit != null) {
                tryToOpenSpecialGUI(recruit, player);
            }
        });
    }

    private void tryToOpenSpecialGUI(AbstractRecruitEntity recruit, ServerPlayer player) {
        if(recruit instanceof ICompanion companion) {
            companion.openSpecialGUI(player);
        }
        else if (recruit instanceof IVillagerWorker worker){
            worker.openSpecialGUI(player);
        }
    }

    @Override
    public MessageOpenSpecialScreen fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.recruit = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeUUID(recruit);
    }
}
