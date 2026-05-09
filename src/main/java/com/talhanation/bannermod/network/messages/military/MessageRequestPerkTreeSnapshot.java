package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.perks.PlayerPerkProgressService;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

public class MessageRequestPerkTreeSnapshot implements BannerModMessage<MessageRequestPerkTreeSnapshot> {
    private boolean playerTree;
    @Nullable
    private UUID recruitUuid;

    public MessageRequestPerkTreeSnapshot() {
    }

    public MessageRequestPerkTreeSnapshot(boolean playerTree, @Nullable UUID recruitUuid) {
        this.playerTree = playerTree;
        this.recruitUuid = recruitUuid;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            if (playerTree) {
                send(sender, new MessageToClientUpdatePerkTreeSnapshot(true, null,
                        PlayerPerkProgressService.progress(sender), "gui.bannermod.perk_tree.feedback.synced"));
                return;
            }
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(sender, recruitUuid, 16.0D);
            if (!isAuthorized(sender, recruit)) {
                send(sender, new MessageToClientUpdatePerkTreeSnapshot(false, recruitUuid, null,
                        "gui.bannermod.perk_tree.feedback.denied_authority"));
                return;
            }
            send(sender, new MessageToClientUpdatePerkTreeSnapshot(false, recruitUuid,
                    recruit.getPerkProgress(), "gui.bannermod.perk_tree.feedback.synced"));
        });
    }

    @Override
    public MessageRequestPerkTreeSnapshot fromBytes(FriendlyByteBuf buf) {
        this.playerTree = buf.readBoolean();
        this.recruitUuid = buf.readBoolean() ? buf.readUUID() : null;
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.playerTree);
        buf.writeBoolean(this.recruitUuid != null);
        if (this.recruitUuid != null) buf.writeUUID(this.recruitUuid);
    }

    static boolean isAuthorized(ServerPlayer sender, @Nullable AbstractRecruitEntity recruit) {
        if (recruit == null || !recruit.isAlive()) return false;
        return sender.hasPermissions(2) || sender.getUUID().equals(recruit.getOwnerUUID());
    }

    static void send(ServerPlayer sender, MessageToClientUpdatePerkTreeSnapshot message) {
        BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(() -> sender), message);
    }
}
