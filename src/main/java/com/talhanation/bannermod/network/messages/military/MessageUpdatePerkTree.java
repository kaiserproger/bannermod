package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.perks.PerkArchetype;
import com.talhanation.bannermod.entity.military.perks.PerkEffectService;
import com.talhanation.bannermod.entity.military.perks.PerkNode;
import com.talhanation.bannermod.entity.military.perks.PerkProgress;
import com.talhanation.bannermod.entity.military.perks.PerkRegistry;
import com.talhanation.bannermod.entity.military.perks.PlayerPerkProgressService;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

public class MessageUpdatePerkTree implements BannerModMessage<MessageUpdatePerkTree> {
    public static final String ACTION_UNLOCK = "unlock";
    public static final String ACTION_RESPEC = "respec";

    private boolean playerTree;
    @Nullable
    private UUID recruitUuid;
    private String action = "";
    private String perkId = "";

    public MessageUpdatePerkTree() {
    }

    public MessageUpdatePerkTree(boolean playerTree, @Nullable UUID recruitUuid, String action, String perkId) {
        this.playerTree = playerTree;
        this.recruitUuid = recruitUuid;
        this.action = action;
        this.perkId = perkId;
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
                handlePlayer(sender);
                return;
            }
            handleRecruit(sender);
        });
    }

    private void handlePlayer(ServerPlayer sender) {
        String feedback = "gui.bannermod.perk_tree.feedback.denied_unknown";
        if (ACTION_UNLOCK.equals(action)) {
            PerkProgress.UnlockResult result = PlayerPerkProgressService.unlock(sender, perkId);
            if (result == PerkProgress.UnlockResult.OK) PerkEffectService.applyPlayerAttributeBonuses(sender);
            feedback = feedbackKey(result);
        } else if (ACTION_RESPEC.equals(action)) {
            PlayerPerkProgressService.respec(sender);
            PerkEffectService.applyPlayerAttributeBonuses(sender);
            feedback = "gui.bannermod.perk_tree.feedback.respec";
        }
        MessageRequestPerkTreeSnapshot.send(sender, new MessageToClientUpdatePerkTreeSnapshot(true, null,
                PlayerPerkProgressService.progress(sender), feedback));
    }

    private void handleRecruit(ServerPlayer sender) {
        AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(sender, recruitUuid, 16.0D);
        if (!MessageRequestPerkTreeSnapshot.isAuthorized(sender, recruit)) {
            MessageRequestPerkTreeSnapshot.send(sender, new MessageToClientUpdatePerkTreeSnapshot(false, recruitUuid, null,
                    "gui.bannermod.perk_tree.feedback.denied_authority"));
            return;
        }

        String feedback = "gui.bannermod.perk_tree.feedback.denied_unknown";
        if (ACTION_UNLOCK.equals(action)) {
            PerkNode node = PerkRegistry.get(perkId).orElse(null);
            if (node == null || node.id().startsWith("player/")
                    || (node.archetype() != PerkArchetype.UNIVERSAL
                    && node.archetype() != PerkEffectService.recruitArchetype(recruit))) {
                feedback = feedbackKey(PerkProgress.UnlockResult.UNKNOWN_PERK);
            } else {
                PerkProgress.UnlockResult result = recruit.getPerkProgress().unlock(node);
                if (result == PerkProgress.UnlockResult.OK) PerkEffectService.applyRecruitAttributeBonuses(recruit);
                feedback = feedbackKey(result);
            }
        } else if (ACTION_RESPEC.equals(action)) {
            recruit.getPerkProgress().respec();
            PerkEffectService.applyRecruitAttributeBonuses(recruit);
            feedback = "gui.bannermod.perk_tree.feedback.respec";
        }

        MessageRequestPerkTreeSnapshot.send(sender, new MessageToClientUpdatePerkTreeSnapshot(false, recruit.getUUID(),
                recruit.getPerkProgress(), feedback));
    }

    @Override
    public MessageUpdatePerkTree fromBytes(FriendlyByteBuf buf) {
        this.playerTree = buf.readBoolean();
        this.recruitUuid = buf.readBoolean() ? buf.readUUID() : null;
        this.action = buf.readUtf(24);
        this.perkId = buf.readUtf(128);
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.playerTree);
        buf.writeBoolean(this.recruitUuid != null);
        if (this.recruitUuid != null) buf.writeUUID(this.recruitUuid);
        buf.writeUtf(this.action == null ? "" : this.action);
        buf.writeUtf(this.perkId == null ? "" : this.perkId);
    }

    private static String feedbackKey(PerkProgress.UnlockResult result) {
        return switch (result) {
            case OK -> "gui.bannermod.perk_tree.feedback.unlocked";
            case ALREADY_OWNED -> "gui.bannermod.perk_tree.feedback.denied_owned";
            case NOT_ENOUGH_POINTS -> "gui.bannermod.perk_tree.feedback.denied_points";
            case PREREQUISITES_NOT_MET -> "gui.bannermod.perk_tree.feedback.denied_prereq";
            case UNKNOWN_PERK -> "gui.bannermod.perk_tree.feedback.denied_unknown";
        };
    }
}
