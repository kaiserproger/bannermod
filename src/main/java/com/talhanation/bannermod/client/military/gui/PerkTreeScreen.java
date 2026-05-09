package com.talhanation.bannermod.client.military.gui;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.perks.PerkArchetype;
import com.talhanation.bannermod.entity.military.perks.PerkEffectService;
import com.talhanation.bannermod.entity.military.perks.PerkNode;
import com.talhanation.bannermod.entity.military.perks.PerkProgress;
import com.talhanation.bannermod.entity.military.perks.PerkRegistry;
import com.talhanation.bannermod.network.messages.military.MessageRequestPerkTreeSnapshot;
import com.talhanation.bannermod.network.messages.military.MessageUpdatePerkTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class PerkTreeScreen extends RecruitsScreenBase {
    private static final MutableComponent TITLE_PLAYER = Component.translatable("gui.bannermod.perk_tree.player.title");
    private static final MutableComponent TITLE_RECRUIT = Component.translatable("gui.bannermod.perk_tree.recruit.title");
    private static final MutableComponent TEXT_LOCKED = Component.translatable("gui.bannermod.perk_tree.state.locked");
    private static final MutableComponent TEXT_AVAILABLE = Component.translatable("gui.bannermod.perk_tree.state.available");
    private static final MutableComponent TEXT_OWNED = Component.translatable("gui.bannermod.perk_tree.state.owned");
    private static final MutableComponent TEXT_UNLOCK = Component.translatable("gui.bannermod.perk_tree.unlock");
    private static final MutableComponent TEXT_RESPEC = Component.translatable("gui.bannermod.perk_tree.respec");
    private static final MutableComponent TEXT_CONFIRM_RESPEC_BUTTON = Component.translatable("gui.bannermod.perk_tree.respec.confirm_button");
    private static final MutableComponent TEXT_BACK = Component.translatable("gui.recruits.button.back");
    private static final MutableComponent TEXT_CONFIRM_RESPEC = Component.translatable("gui.bannermod.perk_tree.respec.confirm");
    private static final MutableComponent TEXT_WAITING = Component.translatable("gui.bannermod.perk_tree.waiting_sync");
    private static final MutableComponent TEXT_NO_PERKS = Component.translatable("gui.bannermod.perk_tree.empty");

    private final boolean playerTree;
    @Nullable
    private final AbstractRecruitEntity recruit;
    private int seenSnapshotVersion;
    private boolean snapshotReady;
    private boolean confirmingRespec;
    private boolean requestedSnapshot;

    public static PerkTreeScreen playerTree() {
        return new PerkTreeScreen(true, null);
    }

    public static PerkTreeScreen recruitTree(AbstractRecruitEntity recruit) {
        return new PerkTreeScreen(false, recruit);
    }

    private PerkTreeScreen(boolean playerTree, @Nullable AbstractRecruitEntity recruit) {
        super(playerTree ? TITLE_PLAYER : TITLE_RECRUIT, 320, 260);
        this.playerTree = playerTree;
        this.recruit = recruit;
    }

    @Override
    protected void init() {
        super.init();
        if (!requestedSnapshot) {
            requestedSnapshot = true;
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRequestPerkTreeSnapshot(playerTree, recruitUuid()));
        }
        PerkProgress progress = progress();
        addRenderableWidget(new ExtendedButton(guiLeft + 10, guiTop + ySize - 28, 70, 20, TEXT_BACK, button -> onClose()));
        Button respecButton = addRenderableWidget(new ExtendedButton(guiLeft + xSize - 106, guiTop + ySize - 28, 96, 20,
                confirmingRespec ? TEXT_CONFIRM_RESPEC_BUTTON : TEXT_RESPEC, button -> confirmRespec()));
        respecButton.active = progress != null;
        if (progress == null) return;

        int y = guiTop + 48;
        for (PerkNode node : visibleNodes()) {
            PerkState state = stateFor(node, progress);
            Button unlock = new ExtendedButton(guiLeft + xSize - 84, y + 3, 66, 18, TEXT_UNLOCK,
                    button -> sendUpdate(MessageUpdatePerkTree.ACTION_UNLOCK, node.id()));
            unlock.active = state == PerkState.AVAILABLE;
            addRenderableWidget(unlock);
            y += 22;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        MilitaryGuiStyle.parchmentPanel(guiGraphics, guiLeft, guiTop, xSize, ySize);
        MilitaryGuiStyle.titleStrip(guiGraphics, guiLeft + 8, guiTop + 6, xSize - 16, 16);
        MilitaryGuiStyle.parchmentInset(guiGraphics, guiLeft + 10, guiTop + 30, xSize - 20, ySize - 66);
    }

    @Override
    public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (seenSnapshotVersion != ClientManager.perkTreeSnapshotVersion) {
            seenSnapshotVersion = ClientManager.perkTreeSnapshotVersion;
            rebuildWidgets();
        }

        MilitaryGuiStyle.drawCenteredTitle(guiGraphics, font, title, guiLeft, guiTop + 10, xSize);
        PerkProgress progress = progress();
        Object points = progress == null ? "..." : progress.getAvailablePoints();
        guiGraphics.drawString(font, Component.translatable("gui.bannermod.perk_tree.points", points), guiLeft + 16, guiTop + 36, MilitaryGuiStyle.TEXT_DARK, false);
        Component feedback = confirmingRespec ? TEXT_CONFIRM_RESPEC : ClientManager.perkTreeFeedback;
        guiGraphics.drawString(font, MilitaryGuiStyle.clampLabel(font, feedback, 170), guiLeft + 110, guiTop + 36, feedbackColor(), false);

        List<PerkNode> nodes = visibleNodes();
        if (nodes.isEmpty()) {
            guiGraphics.drawString(font, TEXT_NO_PERKS, guiLeft + 18, guiTop + 58, MilitaryGuiStyle.TEXT_DARK, false);
            return;
        }
        if (progress == null) {
            guiGraphics.drawString(font, TEXT_WAITING, guiLeft + 18, guiTop + 58, MilitaryGuiStyle.TEXT_DARK, false);
            return;
        }

        int y = guiTop + 50;
        for (PerkNode node : nodes) {
            renderNode(guiGraphics, node, progress, y);
            y += 22;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    public void onSnapshot(boolean playerTree, @Nullable UUID recruitUuid, boolean hasProgress) {
        if (this.playerTree != playerTree) return;
        if (!playerTree && (this.recruit == null || !this.recruit.getUUID().equals(recruitUuid))) return;
        this.snapshotReady = hasProgress;
        this.seenSnapshotVersion = -1;
    }

    private void renderNode(GuiGraphics guiGraphics, PerkNode node, PerkProgress progress, int y) {
        PerkState state = stateFor(node, progress);
        int color = state.color;
        guiGraphics.fill(guiLeft + 16, y, guiLeft + xSize - 92, y + 20, state.background);
        guiGraphics.renderOutline(guiLeft + 16, y, xSize - 108, 20, color | 0xFF000000);
        guiGraphics.drawString(font, MilitaryGuiStyle.clampLabel(font, Component.translatable(node.localizationKey()), 130), guiLeft + 22, y + 3, color, false);
        guiGraphics.drawString(font, state.label, guiLeft + 160, y + 3, color, false);
        guiGraphics.drawString(font, Component.literal(String.valueOf(node.pointCost())), guiLeft + 224, y + 3, MilitaryGuiStyle.TEXT_DARK, false);
    }

    private void confirmRespec() {
        if (!confirmingRespec) {
            confirmingRespec = true;
            rebuildWidgets();
            return;
        }
        confirmingRespec = false;
        sendUpdate(MessageUpdatePerkTree.ACTION_RESPEC, "");
    }

    private void sendUpdate(String action, String perkId) {
        confirmingRespec = false;
        ClientManager.perkTreeFeedback = Component.translatable("gui.bannermod.perk_tree.pending");
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageUpdatePerkTree(playerTree, recruitUuid(), action, perkId));
    }

    @Nullable
    private UUID recruitUuid() {
        return recruit == null ? null : recruit.getUUID();
    }

    @Nullable
    private PerkProgress progress() {
        if (!snapshotReady) return null;
        if (playerTree) {
            return ClientManager.playerPerkSnapshot;
        }
        if (recruit != null && recruit.getUUID().equals(ClientManager.recruitPerkSnapshotUuid)) {
            return ClientManager.recruitPerkSnapshot;
        }
        return null;
    }

    private List<PerkNode> visibleNodes() {
        List<PerkNode> nodes = new ArrayList<>();
        if (playerTree) {
            for (PerkNode node : PerkRegistry.byArchetype(PerkArchetype.UNIVERSAL)) {
                if (node.id().startsWith("player/")) nodes.add(node);
            }
        } else if (recruit != null) {
            PerkArchetype archetype = PerkEffectService.recruitArchetype(recruit);
            for (PerkNode node : PerkRegistry.byArchetype(PerkArchetype.UNIVERSAL)) {
                if (!node.id().startsWith("player/")) nodes.add(node);
            }
            nodes.addAll(PerkRegistry.byArchetype(archetype));
        }
        nodes.sort(Comparator.comparing(PerkNode::id));
        return nodes;
    }

    private static PerkState stateFor(PerkNode node, PerkProgress progress) {
        if (progress.isOwned(node.id())) return PerkState.OWNED;
        if (node.prerequisitesMet(progress.getOwnedPerks()) && progress.getAvailablePoints() >= node.pointCost()) {
            return PerkState.AVAILABLE;
        }
        return PerkState.LOCKED;
    }

    private static int feedbackColor() {
        String text = ClientManager.perkTreeFeedback.getString();
        if (text.isEmpty()) return MilitaryGuiStyle.TEXT_DARK;
        return text.contains("denied") || text.contains("отклон") ? MilitaryGuiStyle.TEXT_DENIED : MilitaryGuiStyle.TEXT_GOOD;
    }

    private enum PerkState {
        LOCKED(TEXT_LOCKED, MilitaryGuiStyle.TEXT_MUTED, 0x60301810),
        AVAILABLE(TEXT_AVAILABLE, MilitaryGuiStyle.TEXT_WARN, 0x70D7B98C),
        OWNED(TEXT_OWNED, MilitaryGuiStyle.TEXT_GOOD, 0x70304C22);

        final Component label;
        final int color;
        final int background;

        PerkState(Component label, int color, int background) {
            this.label = label;
            this.color = color;
            this.background = background;
        }
    }
}
