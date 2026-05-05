package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.gui.AdminRecruitSpawnScreen;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.client.military.gui.widgets.ActionMenuButton;
import com.talhanation.bannermod.client.military.gui.widgets.ContextMenuEntry;
import com.talhanation.bannermod.network.messages.war.MessagePlaceSiegeStandardHere;
import com.talhanation.bannermod.network.messages.war.MessageResolveRevolt;
import com.talhanation.bannermod.network.messages.war.MessageResolveWarOutcome;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import com.talhanation.bannermod.war.runtime.BattleWindowClock;
import com.talhanation.bannermod.war.runtime.BattleWindowDisplay;
import com.talhanation.bannermod.war.runtime.OccupationRecord;
import com.talhanation.bannermod.war.runtime.RevoltRecord;
import com.talhanation.bannermod.war.runtime.RevoltState;
import com.talhanation.bannermod.war.runtime.SiegeStandardRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarGoalType;
import com.talhanation.bannermod.war.runtime.WarState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only list of every war declaration plus its goal/state and the political entity names.
 * Mirrors {@code /bannermod war list} in graphical form, fed by {@link WarClientState}.
 *
 * <p>Detail panel on the right shows full record metadata when a row is selected, with
 * "Open Attacker / Defender" buttons that route into {@link PoliticalEntityInfoScreen}.</p>
 */
public class WarListScreen extends Screen {
    private static final int MIN_BOOK_W = 392;
    private static final int MAX_BOOK_W = 940;
    private static final int MIN_BOOK_H = 220;
    private static final int MAX_BOOK_H = 560;
    private static final int ROW_H = 18;
    private static final int BUTTON_H = 18;
    private static final int BOOK_BORDER = 10;
    private static final int BOOK_BG = 0xFFE8C98E;
    private static final int PAGE_BG = 0xFFF2D9A3;
    private static final int PAGE_SHADE = 0xFFE0BC78;
    private static final int LEATHER = 0xFF4A2D18;
    private static final int LEATHER_DARK = 0xFF24150D;
    private static final int INK = 0xFF2D1B0F;
    private static final int INK_MUTED = 0xFF6C5030;
    private static final int GOLD = 0xFFFFD36A;
    private static final int WAX = 0xFF8E2E24;

    private final Screen parent;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int listVisible = 10;
    private int scrollOffset = 0;
    private List<WarDeclarationRecord> wars = List.of();
    private int observedWarStateVersion = -1;
    @Nullable
    private WarDeclarationRecord selected;
    @Nullable
    private Component outcomeFeedback;

    private Button openAttackerBtn;
    private Button openDefenderBtn;
    private Button alliesBtn;
    private Button declareBtn;
    private Button statesBtn;
    private Button housingBtn;
    private Button hamletsBtn;
    private Button refreshBtn;
    private Button closeBtn;
    private Button adminRecruitSpawnBtn;
    private ActionMenuButton resolveOutcomeMenu;

    public WarListScreen(@Nullable Screen parent) {
        super(text("gui.bannermod.war_list.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        updateGeometry();

        // Nav buttons remain plain (different tier from outcome resolution).
        statesBtn = actionButton(0, text("gui.bannermod.war_list.states"), btn -> this.minecraft.setScreen(new PoliticalEntityListScreen(this)));
        housingBtn = actionButton(1, text("gui.bannermod.war_list.housing"), btn -> this.minecraft.setScreen(new HousingLedgerScreen(this)));
        hamletsBtn = actionButton(2, text("gui.bannermod.war_list.hamlets"), btn -> this.minecraft.setScreen(new HamletListScreen(this)));
        refreshBtn = actionButton(3, text("gui.bannermod.common.refresh"), btn -> refresh());
        declareBtn = actionButton(4, text("gui.bannermod.war_list.declare"), btn -> this.minecraft.setScreen(new WarDeclareScreen(this)));
        alliesBtn = actionButton(5, text("gui.bannermod.war_list.allies"), btn -> openAllies());
        openAttackerBtn = actionButton(6, text("gui.bannermod.war_list.attacker_info"), btn -> openEntity(selected != null ? selected.attackerPoliticalEntityId() : null));
        openDefenderBtn = actionButton(7, text("gui.bannermod.war_list.defender_info"), btn -> openEntity(selected != null ? selected.defenderPoliticalEntityId() : null));
        closeBtn = actionButton(8, text("gui.bannermod.common.close"), btn -> onClose());
        adminRecruitSpawnBtn = actionButton(9, text("gui.bannermod.war_list.admin_recruit_spawn"), btn -> openAdminRecruitSpawner());

        // Resolve-outcome ledger collapses 7 same-tier outcome buttons under one menu.
        resolveOutcomeMenu = new ActionMenuButton(
                actionButtonX(10), actionButtonY(10), actionButtonW(), BUTTON_H,
                text("gui.bannermod.war_list.menu.resolve_outcome"),
                buildResolveOutcomeEntries());
        resolveOutcomeMenu.setOpenUpward(true);

        addRenderableWidget(statesBtn);
        addRenderableWidget(housingBtn);
        addRenderableWidget(hamletsBtn);
        addRenderableWidget(refreshBtn);
        addRenderableWidget(declareBtn);
        addRenderableWidget(alliesBtn);
        addRenderableWidget(openAttackerBtn);
        addRenderableWidget(openDefenderBtn);
        addRenderableWidget(closeBtn);
        addRenderableWidget(adminRecruitSpawnBtn);
        addRenderableWidget(resolveOutcomeMenu);

        refresh();
    }

    private Button actionButton(int index, Component label, Button.OnPress onPress) {
        return new MedievalButton(actionButtonX(index), actionButtonY(index), actionButtonW(), BUTTON_H, label, onPress);
    }

    private void updateGeometry() {
        int viewportW = Math.max(1, this.width - 12);
        int viewportH = Math.max(1, this.height - 12);
        int minW = Math.min(MIN_BOOK_W, viewportW);
        int minH = Math.min(MIN_BOOK_H, viewportH);
        this.guiW = Math.min(MAX_BOOK_W, Math.max(minW, this.width - 28));
        this.guiH = Math.min(MAX_BOOK_H, Math.max(minH, this.height - 24));
        this.guiLeft = (this.width - guiW) / 2;
        this.guiTop = (this.height - guiH) / 2;
        this.listVisible = computeListVisible();
    }

    private int innerX() {
        return guiLeft + BOOK_BORDER + 8;
    }

    private int innerW() {
        return guiW - (BOOK_BORDER + 8) * 2;
    }

    private int pageGap() {
        return Math.max(12, guiW / 54);
    }

    private int contentTop() {
        return guiTop + 38;
    }

    private int contentBottom() {
        return actionLedgerTop() - 8;
    }

    private int leftPageX() {
        return innerX();
    }

    private int leftPageW() {
        int available = innerW() - pageGap();
        int preferred = available * 2 / 5;
        int min = Math.min(136, Math.max(80, available / 2));
        int max = Math.max(min, Math.min(330, available - 136));
        return clamp(preferred, min, max);
    }

    private int rightPageX() {
        return leftPageX() + leftPageW() + pageGap();
    }

    private int rightPageW() {
        return innerW() - leftPageW() - pageGap();
    }

    private int listX() {
        return leftPageX() + 8;
    }

    private int listY() {
        return contentTop() + 24;
    }

    private int listW() {
        return Math.max(80, leftPageW() - 16);
    }

    private int listH() {
        return Math.max(ROW_H, contentBottom() - listY() - 8);
    }

    private int computeListVisible() {
        return Math.max(1, listH() / ROW_H);
    }

    private int actionLedgerTop() {
        return guiTop + guiH - actionLedgerH() - 8;
    }

    private int actionLedgerH() {
        // Header reserves 32px: title strip (~5..14) + status line (~18..27) + 5px gap before buttons.
        return 32 + actionRows() * (BUTTON_H + 4);
    }

    private int actionColumns() {
        return clamp(Math.max(2, actionLedgerW() / 112), 2, 7);
    }

    private int actionRows() {
        int columns = actionColumns();
        return (actionButtonCount() + columns - 1) / columns;
    }

    private int actionButtonCount() {
        // 9 nav buttons + 1 resolve-outcome dropdown trigger.
        return 11;
    }

    private int actionLedgerX() {
        return innerX();
    }

    private int actionLedgerW() {
        return innerW();
    }

    private int actionButtonW() {
        int columns = actionColumns();
        return Math.max(64, (actionLedgerW() - 16 - (columns - 1) * 6) / columns);
    }

    private int actionButtonX(int index) {
        int column = index % actionColumns();
        return actionLedgerX() + 8 + column * (actionButtonW() + 6);
    }

    private int actionButtonY(int index) {
        int row = index / actionColumns();
        // Push first row to +30 so the "Orders" title (+5) and status line (+18) stay readable above it.
        return actionLedgerTop() + 30 + row * (BUTTON_H + 4);
    }

    @Override
    public void tick() {
        super.tick();
        if (observedWarStateVersion != WarClientState.version()) {
            refresh();
        }
    }

    private void placeSiegeHere() {
        UUID sideId = leaderSideOf(selected);
        if (selected == null || sideId == null) return;
        BannerModMain.SIMPLE_CHANNEL.sendToServer(
                new MessagePlaceSiegeStandardHere(selected.id(), sideId, 0));
    }

    private void sendOutcome(MessageResolveWarOutcome.Action action) {
        if (selected == null) return;
        this.outcomeFeedback = text("gui.bannermod.war_list.feedback.pending");
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageResolveWarOutcome(selected.id(), action));
    }

    private void sendRevolt(RevoltState outcome) {
        RevoltRecord revolt = firstPendingRevolt(selected);
        if (revolt == null) return;
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageResolveRevolt(revolt.id(), outcome));
    }

    private void openAllies() {
        if (selected == null) return;
        this.minecraft.setScreen(new WarAlliesScreen(this, selected.id()));
    }

    private void refresh() {
        this.wars = new ArrayList<>(WarClientState.wars());
        if (selected != null) {
            this.selected = wars.stream().filter(w -> w.id().equals(selected.id())).findFirst().orElse(null);
        }
        int maxScroll = Math.max(0, wars.size() - listVisible);
        this.scrollOffset = clamp(this.scrollOffset, 0, maxScroll);
        this.observedWarStateVersion = WarClientState.version();
        updateButtonsState();
    }

    private void openEntity(@Nullable UUID entityId) {
        if (entityId == null) return;
        this.minecraft.setScreen(new PoliticalEntityInfoScreen(this, entityId));
    }

    private void updateButtonsState() {
        boolean has = selected != null;
        openAttackerBtn.active = has;
        openAttackerBtn.setTooltip(has ? null : Tooltip.create(text("gui.bannermod.war_list.tooltip.select_war")));
        openDefenderBtn.active = has;
        openDefenderBtn.setTooltip(has ? null : Tooltip.create(text("gui.bannermod.war_list.tooltip.select_war")));
        if (alliesBtn != null) {
            alliesBtn.active = has;
            alliesBtn.setTooltip(has ? null : Tooltip.create(text("gui.bannermod.war_list.tooltip.select_war")));
        }
        if (declareBtn != null) {
            declareBtn.active = WarClientState.entities().stream().anyMatch(entity -> {
                Player player = Minecraft.getInstance().player;
                return player != null && PoliticalEntityAuthority.canAct(player.getUUID(), false, entity);
            });
            declareBtn.setTooltip(declareBtn.active ? null : Tooltip.create(declareDenial()));
        }
        if (resolveOutcomeMenu != null) {
            resolveOutcomeMenu.setEntries(buildResolveOutcomeEntries());
            // Trigger stays clickable so players can browse outcomes; per-entry enabled flags gate execution.
            resolveOutcomeMenu.active = has;
            resolveOutcomeMenu.setTooltip(has ? null : Tooltip.create(text("gui.bannermod.war_list.tooltip.select_war")));
        }
        if (adminRecruitSpawnBtn != null) {
            boolean adminCreative = isAdminCreative();
            adminRecruitSpawnBtn.visible = adminCreative;
            adminRecruitSpawnBtn.active = adminCreative;
            adminRecruitSpawnBtn.setTooltip(adminCreative ? Tooltip.create(text("gui.bannermod.war_list.admin_recruit_spawn.tooltip")) : null);
        }
    }

    private List<ContextMenuEntry> buildResolveOutcomeEntries() {
        boolean has = selected != null;
        boolean live = has && selected.state() != WarState.RESOLVED && selected.state() != WarState.CANCELLED;
        boolean attackerLeader = has && canLocalPlayerActFor(selected.attackerPoliticalEntityId());
        Player player = Minecraft.getInstance().player;
        boolean localOp = player != null && player.hasPermissions(2);
        boolean canPlaceSiege = has && leaderSideOf(selected) != null
                && selected.state() != WarState.RESOLVED && selected.state() != WarState.CANCELLED;
        boolean occupySupported = has && selected.goalType() == WarGoalType.OCCUPATION;
        boolean annexSupported = has && selected.goalType() == WarGoalType.ANNEX_LIMITED_CHUNKS;
        boolean tributeSupported = has && selected.goalType() == WarGoalType.TRIBUTE;
        RevoltRecord pendingRevolt = firstPendingRevolt(selected);
        Component tributeLabel = localOp ? text("gui.bannermod.war_list.tribute") : text("gui.bannermod.war_list.tribute_locked");

        List<ContextMenuEntry> entries = new ArrayList<>();
        entries.add(new ContextMenuEntry(text("gui.bannermod.war_list.place_siege").getString(),
                this::placeSiegeHere, canPlaceSiege));
        entries.add(new ContextMenuEntry(text("gui.bannermod.war_list.cancel").getString(),
                () -> sendOutcome(MessageResolveWarOutcome.Action.CANCEL), live && attackerLeader));
        entries.add(new ContextMenuEntry(text("gui.bannermod.war_list.occupy").getString(),
                () -> sendOutcome(MessageResolveWarOutcome.Action.OCCUPY), live && attackerLeader && occupySupported));
        entries.add(new ContextMenuEntry(text("gui.bannermod.war_list.annex").getString(),
                () -> sendOutcome(MessageResolveWarOutcome.Action.ANNEX), live && attackerLeader && annexSupported));
        entries.add(new ContextMenuEntry(tributeLabel.getString(),
                () -> sendOutcome(MessageResolveWarOutcome.Action.TRIBUTE), live && attackerLeader && tributeSupported && localOp));
        entries.add(new ContextMenuEntry(text("gui.bannermod.war_list.revolt.resolve_success").getString(),
                () -> sendRevolt(RevoltState.SUCCESS), pendingRevolt != null && localOp));
        entries.add(new ContextMenuEntry(text("gui.bannermod.war_list.revolt.resolve_fail").getString(),
                () -> sendRevolt(RevoltState.FAILED), pendingRevolt != null && localOp));
        return entries;
    }

    private boolean isAdminCreative() {
        Player player = Minecraft.getInstance().player;
        return player != null && player.hasPermissions(2) && player.isCreative();
    }

    private void openAdminRecruitSpawner() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !isAdminCreative()) {
            return;
        }
        AdminRecruitSpawnScreen.openLocal(player);
    }

    private Component placeSiegeDenial(boolean hasSelection) {
        if (!hasSelection) return text("gui.bannermod.war_list.tooltip.select_war");
        if (selected.state() == WarState.RESOLVED || selected.state() == WarState.CANCELLED) return text("gui.bannermod.war_list.tooltip.war_closed");
        return sideAuthorityDenial(selected);
    }

    private Component declareDenial() {
        Player player = Minecraft.getInstance().player;
        UUID actor = player == null ? null : player.getUUID();
        for (PoliticalEntityRecord entity : WarClientState.entities()) {
            if (PoliticalEntityAuthority.canAct(actor, false, entity) && !entity.status().canDeclareOffensiveWar()) {
                return text("gui.bannermod.war.denial.attacker_status", localizedPoliticalStatus(entity.status()));
            }
        }
        if (!WarClientState.entities().isEmpty()) {
            return PoliticalEntityAuthority.denialReason(actor, false, WarClientState.entities().get(0));
        }
        return text("gui.bannermod.war_list.tooltip.no_declarer");
    }

    private Component sideAuthorityDenial(WarDeclarationRecord war) {
        Player player = Minecraft.getInstance().player;
        UUID actor = player == null ? null : player.getUUID();
        PoliticalEntityRecord attacker = WarClientState.entityById(war.attackerPoliticalEntityId());
        PoliticalEntityRecord defender = WarClientState.entityById(war.defenderPoliticalEntityId());
        if (PoliticalEntityAuthority.canAct(actor, false, attacker)) return PoliticalEntityAuthority.denialReason(actor, false, defender);
        return PoliticalEntityAuthority.denialReason(actor, false, attacker);
    }

    private Component outcomeLockedTooltip(boolean hasSelection, boolean liveWar, @Nullable WarDeclarationRecord war) {
        return outcomeLockedTooltip(hasSelection, liveWar, war, true, true);
    }

    private Component outcomeLockedTooltip(boolean hasSelection, boolean liveWar, @Nullable WarDeclarationRecord war, boolean supportedGoal) {
        return outcomeLockedTooltip(hasSelection, liveWar, war, supportedGoal, true);
    }

    private Component outcomeLockedTooltip(boolean hasSelection, boolean liveWar, @Nullable WarDeclarationRecord war, boolean supportedGoal, boolean operatorRequiredMet) {
        if (!hasSelection) return text("gui.bannermod.war_list.tooltip.select_war");
        if (!liveWar) return text("gui.bannermod.war_list.tooltip.war_closed");
        if (!supportedGoal) return text("gui.bannermod.war_list.tooltip.unsupported_outcome");
        if (!operatorRequiredMet) return text("gui.bannermod.war_list.tooltip.op_only");
        PoliticalEntityRecord attacker = war == null ? null : WarClientState.entityById(war.attackerPoliticalEntityId());
        Player player = Minecraft.getInstance().player;
        return PoliticalEntityAuthority.denialReason(player == null ? null : player.getUUID(), false, attacker);
    }

    private static Component revoltLockedTooltip(boolean hasSelection, boolean hasPendingRevolt, boolean op) {
        if (!hasSelection) return text("gui.bannermod.war_list.tooltip.select_war");
        if (!hasPendingRevolt) return text("gui.bannermod.war_list.tooltip.revolt_none_pending");
        if (!op) return text("gui.bannermod.war_list.tooltip.op_only");
        return text("gui.bannermod.war_list.tooltip.revolt_select_pending");
    }

    @Nullable
    private RevoltRecord firstPendingRevolt(@Nullable WarDeclarationRecord war) {
        if (war == null) return null;
        for (RevoltRecord revolt : WarClientState.revoltsForWar(war.id())) {
            if (revolt.state() == RevoltState.PENDING) {
                return revolt;
            }
        }
        return null;
    }

    @Nullable
    private UUID leaderSideOf(@Nullable WarDeclarationRecord war) {
        if (war == null) return null;
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        UUID attacker = war.attackerPoliticalEntityId();
        UUID defender = war.defenderPoliticalEntityId();
        if (attacker != null && canLocalPlayerActFor(attacker)) return attacker;
        if (defender != null && canLocalPlayerActFor(defender)) return defender;
        return null;
    }

    private static boolean canLocalPlayerActFor(UUID entityId) {
        PoliticalEntityRecord entity = WarClientState.entityById(entityId);
        if (entity == null) return false;
        Player player = Minecraft.getInstance().player;
        return player != null && PoliticalEntityAuthority.canAct(player.getUUID(), false, entity);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x66000000);
        renderBookFrame(graphics);
        renderHeader(graphics);

        renderBattleWindowBanner(graphics);
        renderList(graphics, mouseX, mouseY);
        renderDetailPanel(graphics);
        renderActionStatus(graphics);
        renderActionFeedback(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderBookFrame(GuiGraphics graphics) {
        graphics.fill(guiLeft + 4, guiTop + 5, guiLeft + guiW + 4, guiTop + guiH + 5, 0x66000000);
        MilitaryGuiStyle.parchmentPanel(graphics, guiLeft, guiTop, guiW, guiH);

        int pageY = contentTop();
        int pageH = Math.max(36, contentBottom() - pageY);
        MilitaryGuiStyle.parchmentInset(graphics, leftPageX(), pageY, leftPageW(), pageH);
        MilitaryGuiStyle.parchmentInset(graphics, rightPageX(), pageY, rightPageW(), pageH);

        int spineX = leftPageX() + leftPageW() + pageGap() / 2 - 1;
        graphics.fill(spineX, pageY + 3, spineX + 2, pageY + pageH - 3, PAGE_SHADE);
        graphics.fill(spineX + 2, pageY + 3, spineX + 3, pageY + pageH - 3, 0x88FFF3C5);

        MilitaryGuiStyle.parchmentInset(graphics, actionLedgerX(), actionLedgerTop(), actionLedgerW(), actionLedgerH());
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawCenteredString(font, text("gui.bannermod.war_list.book_title").getString(), guiLeft + guiW / 2, guiTop + 9, GOLD);
        int half = Math.max(40, innerW() / 2 - 8);
        graphics.drawString(font,
                font.plainSubstrByWidth(text("gui.bannermod.war_list.active_wars", wars.size()).getString(), half),
                innerX() + 4, guiTop + 25, INK_MUTED, false);
        graphics.drawString(font,
                text("gui.bannermod.war_list.ledger_title").getString(),
                actionLedgerX() + 8, actionLedgerTop() + 5, INK_MUTED, false);
        renderTitleOrnaments(graphics);
    }

    private void renderTitleOrnaments(GuiGraphics graphics) {
        int titleY = guiTop + 12;
        renderSigil(graphics, guiLeft + 28, titleY, WAX);
        renderSigil(graphics, guiLeft + guiW - 28, titleY, 0xFF2F6E2E);
        renderDividerFlourish(graphics, guiLeft + guiW / 2 - 76, guiTop + 26, 50);
        renderDividerFlourish(graphics, guiLeft + guiW / 2 + 26, guiTop + 26, 50);
    }

    private void renderSigil(GuiGraphics graphics, int centerX, int topY, int accent) {
        graphics.fill(centerX - 6, topY, centerX + 6, topY + 2, GOLD);
        graphics.fill(centerX - 4, topY + 2, centerX + 4, topY + 5, accent);
        graphics.fill(centerX - 2, topY + 5, centerX + 2, topY + 9, accent);
        graphics.fill(centerX - 1, topY + 9, centerX + 1, topY + 11, GOLD);
        graphics.renderOutline(centerX - 6, topY, 12, 11, LEATHER_DARK);
    }

    private void renderDividerFlourish(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0xAA7A4C24);
        graphics.fill(x + width / 2 - 1, y - 2, x + width / 2 + 1, y + 3, GOLD);
        graphics.fill(x + 6, y - 1, x + 10, y + 2, 0x667A4C24);
        graphics.fill(x + width - 10, y - 1, x + width - 6, y + 2, 0x667A4C24);
    }

    private void renderActionFeedback(GuiGraphics graphics) {
        Component feedback = WarClientState.lastActionFeedback();
        if (feedback == null || feedback.getString().isBlank()) return;
        int labelRight = actionLedgerX() + 8 + font.width(text("gui.bannermod.war_list.ledger_title")) + 8;
        int maxW = Math.max(40, actionLedgerX() + actionLedgerW() - labelRight - 8);
        graphics.drawString(font, font.plainSubstrByWidth(feedback.getString(), maxW), labelRight, actionLedgerTop() + 5, WAX, false);
    }

    private void renderActionStatus(GuiGraphics graphics) {
        graphics.drawString(font,
                font.plainSubstrByWidth(visibleOrderStatus().getString(), Math.max(40, actionLedgerW() - 16)),
                actionLedgerX() + 8, actionLedgerTop() + 18, INK, false);
        renderLedgerSeal(graphics);
    }

    private void renderLedgerSeal(GuiGraphics graphics) {
        int sealSize = 12;
        int x = actionLedgerX() + actionLedgerW() - sealSize - 10;
        int y = actionLedgerTop() + 4;
        graphics.fill(x, y, x + sealSize, y + sealSize, WAX);
        graphics.renderOutline(x, y, sealSize, sealSize, LEATHER_DARK);
        graphics.fill(x + 3, y + 2, x + sealSize - 3, y + 3, GOLD);
        graphics.fill(x + 2, y + 5, x + sealSize - 2, y + 6, GOLD);
        graphics.fill(x + 4, y + 8, x + sealSize - 4, y + 9, GOLD);
    }

    private Component visibleOrderStatus() {
        if (!WarClientState.hasSnapshot()) {
            return text("gui.bannermod.war_list.waiting_sync");
        }
        if (selected == null) {
            return text("gui.bannermod.war_list.status.select_war");
        }
        if (selected.state() == WarState.RESOLVED || selected.state() == WarState.CANCELLED) {
            return text("gui.bannermod.war_list.status.closed");
        }
        UUID commandSide = leaderSideOf(selected);
        if (commandSide != null) {
            if (selected.state() == WarState.DECLARED) {
                return text("gui.bannermod.war_list.status.pre_active");
            }
            return text("gui.bannermod.war_list.status.authorized");
        }
        return sideAuthorityDenial(selected);
    }

    private void renderBattleWindowBanner(GuiGraphics graphics) {
        BattleWindowClock.Phase phase = BattleWindowClock.compute(
                WarClientState.schedule(), ZonedDateTime.now());
        String text = font.plainSubstrByWidth(BattleWindowDisplay.formatPhase(phase), Math.max(40, innerW() / 2 - 8));
        int color = phase instanceof BattleWindowClock.Phase.Open ? 0xFF276E28 : INK_MUTED;
        graphics.drawString(font,
                text,
                innerX() + innerW() - font.width(text) - 4, guiTop + 25, color, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = listX();
        int listY = listY();
        int listW = listW();
        int listH = listVisible * ROW_H;
        graphics.drawString(font, text("gui.bannermod.war_list.list_title").getString(), listX, contentTop() + 8, INK, false);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0x22FFFFFF);
        graphics.renderOutline(listX, listY, listW, listH, PAGE_SHADE);

        int rendered = Math.min(listVisible, Math.max(0, wars.size() - scrollOffset));
        for (int i = 0; i < rendered; i++) {
            WarDeclarationRecord war = wars.get(scrollOffset + i);
            int rowY = listY + i * ROW_H;
            boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean isSelected = selected != null && selected.id().equals(war.id());

            int rowBg = isSelected ? 0x669E3A23 : (hovered ? 0x33FFFFFF : 0);
            if (rowBg != 0) {
                graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_H - 1, rowBg);
            }

            String stateBadge = "[" + localizedWarState(war.state()).getString() + "]";
            int stateColor = stateColor(war.state());
            graphics.drawString(font, stateBadge, listX + 4, rowY + 5, stateColor, false);

            String attackerName = entityName(war.attackerPoliticalEntityId());
            String defenderName = entityName(war.defenderPoliticalEntityId());
            String label = text("gui.bannermod.war_list.row", attackerName, defenderName).getString();
            int labelX = listX + 8 + font.width(stateBadge) + 4;
            graphics.drawString(font, font.plainSubstrByWidth(label, Math.max(20, listX + listW - labelX - 4)), labelX, rowY + 5, INK, false);
        }

        if (wars.isEmpty()) {
            boolean hasSnapshot = WarClientState.hasSnapshot();
            int color = hasSnapshot ? INK_MUTED : 0xFF7C7164;
            String empty = text(hasSnapshot
                    ? "gui.bannermod.war_list.empty"
                    : "gui.bannermod.war_list.waiting_sync").getString();
            graphics.renderOutline(listX + 8, listY + listH / 2 - 14, Math.max(20, listW - 16), 28, color);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(empty, Math.max(20, listW - 20)), listX + listW / 2, listY + listH / 2 - 4, color);
        }
    }

    private void renderDetailPanel(GuiGraphics graphics) {
        int x = rightPageX() + 8;
        int y = contentTop() + 8;
        int w = Math.max(40, rightPageW() - 16);

        graphics.drawString(font, text("gui.bannermod.war_list.detail"), x, y, INK, false);
        if (selected == null) {
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.war_list.select_war").getString(), w), x, y + 14, INK_MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.war_list.outcome_hint").getString(), w), x, y + 28, INK_MUTED, false);
            return;
        }

        WarDeclarationRecord war = selected;
        int maxLines = maxDetailLines(y);
        if (maxLines <= 0) {
            return;
        }
        int line = 0;
        String[] body = {
                text("gui.bannermod.war_list.detail.attacker", entityName(war.attackerPoliticalEntityId())).getString(),
                text("gui.bannermod.war_list.detail.defender", entityName(war.defenderPoliticalEntityId())).getString(),
                text("gui.bannermod.war_list.detail.state", localizedWarState(war.state())).getString(),
                text("gui.bannermod.war_list.detail.goal", localizedWarGoal(war.goalType())).getString(),
                text("gui.bannermod.war_list.detail.casus", war.casusBelli().isEmpty() ? text("gui.bannermod.common.none").getString() : war.casusBelli()).getString(),
                text("gui.bannermod.war_list.detail.declared", war.declaredAtGameTime()).getString(),
                text("gui.bannermod.war_list.detail.earliest", war.earliestActivationGameTime()).getString(),
                text("gui.bannermod.war_list.detail.attacker_allies", war.attackerAllyIds().size()).getString(),
                text("gui.bannermod.war_list.detail.defender_allies", war.defenderAllyIds().size()).getString(),
                text("gui.bannermod.war_list.detail.targets", war.targetPositions().size()).getString(),
                text("gui.bannermod.war_list.detail.occupations", occupationSummary(war.id())).getString(),
                text("gui.bannermod.war_list.detail.sieges", activeSiegeCount(war.id())).getString(),
                text("gui.bannermod.war_list.detail.revolts", revoltSummary(war.id())).getString(),
                text("gui.bannermod.war_list.detail.revolt_ui").getString(),
                text("gui.bannermod.war_list.detail.outcome_ui", outcomeStatus(war)).getString(),
                text("gui.bannermod.war_list.detail.consequences", consequenceSummary(war)).getString()
        };
        for (String s : body) {
            if (line >= maxLines) {
                return;
            }
            graphics.drawString(font, font.plainSubstrByWidth(s, w), x, y + 14 + line * 11, INK, false);
            line++;
        }
        if (outcomeFeedback != null && line < maxLines) {
            graphics.drawString(font, font.plainSubstrByWidth(outcomeFeedback.getString(), w), x, y + 14 + line * 11, WAX, false);
            line++;
        }
        for (RevoltRecord revolt : WarClientState.revoltsForWar(war.id())) {
            if (line >= maxLines) {
                return;
            }
            int color = revoltLineColor(revolt.state());
            graphics.drawString(font,
                    font.plainSubstrByWidth(revoltPressureLine(revolt), w),
                    x, y + 14 + line * 11, color, false);
            line++;
            if (line >= maxLines) {
                return;
            }
            graphics.drawString(font,
                    font.plainSubstrByWidth(revoltObjectiveLine(revolt), w),
                    x, y + 14 + line * 11, INK_MUTED, false);
            line++;
            if (line >= maxLines) {
                return;
            }
        }
        for (SiegeStandardRecord siege : WarClientState.sieges()) {
            if (!siege.warId().equals(war.id())) {
                continue;
            }
            if (line >= maxLines) {
                return;
            }
            String side = entityName(siege.sidePoliticalEntityId());
            String pos = siege.pos() == null ? text("gui.bannermod.common.unknown").getString() : siege.pos().toShortString();
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.war_list.standard", side, pos, siege.radius()).getString(), w),
                    x, y + 14 + line * 11, 0xFF2F6E2E, false);
            line++;
            if (line >= maxLines) {
                return;
            }
        }
        for (OccupationRecord occupation : WarClientState.occupationsForWar(war.id())) {
            if (line >= maxLines) {
                return;
            }
            String firstChunk = occupation.chunks().isEmpty()
                    ? text("gui.bannermod.war_list.chunk_unknown").getString()
                    : text("gui.bannermod.war_list.chunk", occupation.chunks().get(0).x, occupation.chunks().get(0).z).getString();
            String suffix = occupation.chunks().size() > 1 ? " +" + (occupation.chunks().size() - 1) : "";
            graphics.drawString(font,
                    font.plainSubstrByWidth(text("gui.bannermod.war_list.occupation", entityName(occupation.occupierEntityId()), firstChunk, suffix, occupation.lastTaxedAtGameTime()).getString(), w),
                    x, y + 14 + line * 11, 0xFF7A4C24, false);
            line++;
            if (line >= maxLines) {
                return;
            }
        }
    }

    private int maxDetailLines(int titleY) {
        int firstLineY = titleY + 14;
        int detailBottom = contentBottom() - 8;
        if (detailBottom < firstLineY) {
            return 0;
        }
        return ((detailBottom - firstLineY) / 11) + 1;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static Component localizedWarState(WarState state) {
        return switch (state) {
            case DECLARED -> text("gui.bannermod.war_list.state.declared");
            case ACTIVE -> text("gui.bannermod.war_list.state.active");
            case IN_SIEGE_WINDOW -> text("gui.bannermod.war_list.state.in_siege_window");
            case RESOLVED -> text("gui.bannermod.war_list.state.resolved");
            case CANCELLED -> text("gui.bannermod.war_list.state.cancelled");
        };
    }

    private static Component localizedWarGoal(WarGoalType goalType) {
        return switch (goalType) {
            case TRIBUTE -> text("gui.bannermod.war_list.goal.tribute");
            case OCCUPATION -> text("gui.bannermod.war_list.goal.occupation");
            case ANNEX_LIMITED_CHUNKS -> text("gui.bannermod.war_list.goal.annex_limited_chunks");
            case VASSALIZATION -> text("gui.bannermod.war_list.goal.vassalization");
            case REGIME_CHANGE -> text("gui.bannermod.war_list.goal.regime_change");
            case WHITE_PEACE -> text("gui.bannermod.war_list.goal.white_peace");
        };
    }

    private static Component localizedPoliticalStatus(PoliticalEntityStatus status) {
        return switch (status) {
            case SETTLEMENT -> text("gui.bannermod.states.status.settlement");
            case STATE -> text("gui.bannermod.states.status.state");
            case VASSAL -> text("gui.bannermod.states.status.vassal");
            case PEACEFUL -> text("gui.bannermod.states.status.peaceful");
        };
    }

    private String occupationSummary(UUID warId) {
        List<OccupationRecord> records = WarClientState.occupationsForWar(warId);
        if (records.isEmpty()) return text("gui.bannermod.common.none").getString();
        int chunks = 0;
        for (OccupationRecord record : records) {
            chunks += record.chunks().size();
        }
        return text("gui.bannermod.war_list.occupation_summary", records.size(), chunks).getString();
    }

    private String revoltSummary(UUID warId) {
        int pending = 0;
        int success = 0;
        int failed = 0;
        for (RevoltRecord revolt : WarClientState.revoltsForWar(warId)) {
            switch (revolt.state()) {
                case PENDING -> pending++;
                case SUCCESS -> success++;
                case FAILED -> failed++;
            }
        }
        if (pending == 0 && success == 0 && failed == 0) return text("gui.bannermod.common.none").getString();
        return text("gui.bannermod.war_list.revolt.summary", pending, success, failed).getString();
    }

    private String objectiveLabel(RevoltRecord revolt) {
        OccupationRecord occupation = WarClientState.occupationById(revolt.occupationId());
        if (occupation == null || occupation.chunks().isEmpty()) return text("gui.bannermod.common.unknown").getString();
        ChunkPos chunk = occupation.chunks().get(0);
        return text("gui.bannermod.war_list.chunk", chunk.x, chunk.z).getString();
    }

    private String consequenceSummary(WarDeclarationRecord war) {
        boolean hasOccupations = !WarClientState.occupationsForWar(war.id()).isEmpty();
        boolean hasRevolts = !WarClientState.revoltsForWar(war.id()).isEmpty();
        if (war.goalType() == WarGoalType.TRIBUTE && war.state() == WarState.RESOLVED) {
            return text("gui.bannermod.war_list.consequence.tribute_resolved").getString();
        }
        if (hasOccupations && hasRevolts) {
            return text("gui.bannermod.war_list.consequence.occupation_revolt_tax").getString();
        }
        if (hasOccupations) {
            return text("gui.bannermod.war_list.consequence.occupation_tax").getString();
        }
        if (hasRevolts) {
            return text("gui.bannermod.war_list.consequence.revolt").getString();
        }
        if (war.goalType() == WarGoalType.TRIBUTE) {
            return text("gui.bannermod.war_list.consequence.tribute_pending").getString();
        }
        return text("gui.bannermod.common.none").getString();
    }

    private String outcomeStatus(WarDeclarationRecord war) {
        return switch (war.goalType()) {
            case TRIBUTE -> text("gui.bannermod.war_list.outcome_status.tribute").getString();
            case OCCUPATION -> text("gui.bannermod.war_list.outcome_status.occupation").getString();
            case ANNEX_LIMITED_CHUNKS -> text("gui.bannermod.war_list.outcome_status.annex").getString();
            case VASSALIZATION, REGIME_CHANGE -> text("gui.bannermod.war_list.outcome_status.admin_only").getString();
            case WHITE_PEACE -> text("gui.bannermod.war_list.outcome_status.unsupported").getString();
        };
    }

    private String revoltPressureLine(RevoltRecord revolt) {
        String rebel = entityName(revolt.rebelEntityId());
        return switch (revolt.state()) {
            case PENDING -> text("gui.bannermod.war_list.revolt.pending_pressure", rebel, revolt.scheduledAtGameTime()).getString();
            case SUCCESS -> text("gui.bannermod.war_list.revolt.success_aftermath", rebel, revolt.resolvedAtGameTime()).getString();
            case FAILED -> text("gui.bannermod.war_list.revolt.failed_aftermath", rebel, revolt.resolvedAtGameTime()).getString();
        };
    }

    private String revoltObjectiveLine(RevoltRecord revolt) {
        return switch (revolt.state()) {
            case PENDING -> text("gui.bannermod.war_list.revolt.pending_objective", objectiveLabel(revolt)).getString();
            case SUCCESS -> text("gui.bannermod.war_list.revolt.success_result").getString();
            case FAILED -> text("gui.bannermod.war_list.revolt.failed_result", objectiveLabel(revolt)).getString();
        };
    }

    private int revoltLineColor(RevoltState state) {
        return switch (state) {
            case PENDING -> 0xFF806016;
            case SUCCESS -> 0xFF2F6E2E;
            case FAILED -> WAX;
        };
    }

    private int activeSiegeCount(UUID warId) {
        int count = 0;
        for (SiegeStandardRecord siege : WarClientState.sieges()) {
            if (siege.warId().equals(warId)) count++;
        }
        return count;
    }

    private String entityName(UUID id) {
        if (id == null) return text("gui.bannermod.common.unknown").getString();
        PoliticalEntityRecord entity = WarClientState.entityById(id);
        if (entity == null) return text("gui.bannermod.common.unknown").getString();
        return entity.name().isBlank() ? text("gui.bannermod.states.unnamed").getString() : entity.name();
    }

    private static int stateColor(WarState state) {
        return switch (state) {
            case ACTIVE -> 0xFF9E2F1D;
            case IN_SIEGE_WINDOW -> ChatFormatting.RED.getColor() == null ? 0xFF8E2E24 : ChatFormatting.RED.getColor();
            case DECLARED -> 0xFF806016;
            case RESOLVED, CANCELLED -> 0xFF6C6455;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listX = listX();
            int listY = listY();
            int listW = listW();
            int listH = listVisible * ROW_H;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
                int row = (int) ((mouseY - listY) / ROW_H);
                int idx = scrollOffset + row;
                if (idx >= 0 && idx < wars.size()) {
                    selected = wars.get(idx);
                    outcomeFeedback = null;
                    updateButtonsState();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        int listX = listX();
        int listY = listY();
        int listW = listW();
        int listH = listVisible * ROW_H;
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY || mouseY >= listY + listH) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
        }
        int max = Math.max(0, wars.size() - listVisible);
        scrollOffset = clamp(scrollOffset - (int) Math.signum(delta), 0, max);
        return true;
    }

    private static class MedievalButton extends Button {
        MedievalButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hovered = isHoveredOrFocused();
            int border = active ? (hovered ? GOLD : PAGE_SHADE) : 0xFF7C6C55;
            int fill = active ? (hovered ? 0xFF6A3D1F : LEATHER) : 0xFF4C3A28;

            graphics.fill(x, y, x + w, y + h, LEATHER_DARK);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
            graphics.fill(x + 2, y + 2, x + w - 2, y + 4, 0x557A4C24);
            graphics.renderOutline(x, y, w, h, border);
            graphics.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x661A100A);

            Font font = Minecraft.getInstance().font;
            String label = clippedLabel(font, getMessage().getString(), Math.max(4, w - 10));
            int textColor = active ? GOLD : 0xFFB8A17A;
            graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, textColor);
        }

        private static String clippedLabel(Font font, String label, int maxWidth) {
            if (font.width(label) <= maxWidth) return label;
            String ellipsis = "...";
            int textWidth = Math.max(1, maxWidth - font.width(ellipsis));
            return font.plainSubstrByWidth(label, textWidth) + ellipsis;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onClose() {
        if (parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
