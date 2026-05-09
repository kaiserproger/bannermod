package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.client.military.gui.PerkTreeScreen;
import com.talhanation.bannermod.entity.military.perks.PerkProgress;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.UUID;

public class MessageToClientUpdatePerkTreeSnapshot implements BannerModMessage<MessageToClientUpdatePerkTreeSnapshot> {
    private boolean playerTree;
    @Nullable
    private UUID recruitUuid;
    @Nullable
    private CompoundTag progressTag;
    private String feedbackKey = "";

    public MessageToClientUpdatePerkTreeSnapshot() {
    }

    public MessageToClientUpdatePerkTreeSnapshot(boolean playerTree, @Nullable UUID recruitUuid,
                                                 @Nullable PerkProgress progress, String feedbackKey) {
        this.playerTree = playerTree;
        this.recruitUuid = recruitUuid;
        this.progressTag = progress == null ? null : progress.toNbt();
        this.feedbackKey = feedbackKey;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.clientbound();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void executeClientSide(BannerModNetworkContext context) {
        boolean hasProgress = progressTag != null;
        if (hasProgress) {
            PerkProgress progress = new PerkProgress();
            progress.fromNbt(progressTag);
            if (playerTree) {
                ClientManager.playerPerkSnapshot = progress;
            } else {
                ClientManager.recruitPerkSnapshotUuid = recruitUuid;
                ClientManager.recruitPerkSnapshot = progress;
            }
        }
        ClientManager.perkTreeFeedback = feedbackKey == null || feedbackKey.isEmpty()
                ? Component.empty()
                : Component.translatable(feedbackKey);
        ClientManager.perkTreeSnapshotVersion++;
        if (Minecraft.getInstance().screen instanceof PerkTreeScreen screen) {
            screen.onSnapshot(playerTree, recruitUuid, hasProgress);
        }
    }

    @Override
    public MessageToClientUpdatePerkTreeSnapshot fromBytes(FriendlyByteBuf buf) {
        this.playerTree = buf.readBoolean();
        this.recruitUuid = buf.readBoolean() ? buf.readUUID() : null;
        this.progressTag = buf.readNbt();
        this.feedbackKey = buf.readUtf(128);
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.playerTree);
        buf.writeBoolean(this.recruitUuid != null);
        if (this.recruitUuid != null) buf.writeUUID(this.recruitUuid);
        buf.writeNbt(this.progressTag);
        buf.writeUtf(this.feedbackKey == null ? "" : this.feedbackKey);
    }
}
