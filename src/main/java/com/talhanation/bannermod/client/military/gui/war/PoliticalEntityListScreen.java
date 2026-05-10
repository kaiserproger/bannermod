package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.client.military.gui.widgets.ActionMenuButton;
import com.talhanation.bannermod.client.military.gui.widgets.ContextMenuEntry;
import com.talhanation.bannermod.network.messages.war.MessageCreatePoliticalEntity;
import com.talhanation.bannermod.network.messages.war.MessageRenamePoliticalEntity;
import com.talhanation.bannermod.network.messages.war.MessageSetGovernmentForm;
import com.talhanation.bannermod.network.messages.war.MessageSetPoliticalEntityCapital;
import com.talhanation.bannermod.network.messages.war.MessageSetPoliticalEntityCharter;
import com.talhanation.bannermod.network.messages.war.MessageSetPoliticalEntityColor;
import com.talhanation.bannermod.network.messages.war.MessageSetPoliticalEntityStatus;
import com.talhanation.bannermod.network.messages.war.MessageUpdateCoLeader;
import com.talhanation.bannermod.util.GameProfileUtils;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.registry.GovernmentForm;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import com.talhanation.bannermod.war.registry.PoliticalRegistryValidation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PoliticalEntityListScreen extends Screen {
    private static final int MIN_BOOK_W = 392;
    private static final int MAX_BOOK_W = 760;
    private static final int MIN_BOOK_H = 220;
    private static final int MAX_BOOK_H = 520;
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
    private int listVisible = 8;
    private int scrollOffset;
    private List<PoliticalEntityRecord> entities = List.of();
    private int observedWarStateVersion = -1;
    @Nullable
    private PoliticalEntityRecord selected;
    @Nullable
    private ActionMenuButton manageMenu;

    public PoliticalEntityListScreen(@Nullable Screen parent) {
        super(text("gui.bannermod.states.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        updateGeometry();
        // Collapse 9 same-tier state-management actions under one manage dropdown.
        // Refresh and back stay as plain buttons (different tier: nav controls).
        this.manageMenu = new ActionMenuButton(
                actionButtonX(0), actionButtonY(0), actionButtonW(), BUTTON_H,
                text("gui.bannermod.states.menu.manage"),
                buildManageEntries());
        this.manageMenu.setOpenUpward(true);
        addRenderableWidget(this.manageMenu);
        addRenderableWidget(actionButton(1, text("gui.bannermod.common.refresh"), btn -> refresh()));
        addRenderableWidget(actionButton(2, text("gui.bannermod.common.back"), btn -> onClose()));
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
        this.listVisible = Math.max(1, listH() / ROW_H);
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
        return clamp(available * 2 / 5, 136, Math.max(136, available - 148));
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

    private int actionLedgerTop() {
        return guiTop + guiH - actionLedgerH() - 8;
    }

    private int actionLedgerH() {
        // Header reserves 32px: title strip (~5..14) + status line (~18..27) + 5px gap before buttons.
        return 32 + actionRows() * (BUTTON_H + 4);
    }

    private int actionColumns() {
        return clamp(Math.max(2, actionLedgerW() / 108), 2, 6);
    }

    private int actionRows() {
        int columns = actionColumns();
        // 1 manage dropdown + 2 nav buttons (refresh, back).
        return (3 + columns - 1) / columns;
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

    private void refresh() {
        this.entities = new ArrayList<>(WarClientState.entities());
        if (this.selected != null) {
            this.selected = WarClientState.entityById(this.selected.id());
        }
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, Math.max(0, entities.size() - listVisible)));
        this.observedWarStateVersion = WarClientState.version();
        updateLeaderButtons();
    }

    private void openCreateDialog() {
        Minecraft.getInstance().setScreen(new PoliticalEntityNameInputScreen(
                this,
                text("gui.bannermod.states.dialog.create.title"),
                text("gui.bannermod.states.dialog.create.prompt"),
                "",
                this::sendCreate
        ));
    }

    private void openRenameDialog() {
        if (this.selected == null) return;
        UUID id = this.selected.id();
        Minecraft.getInstance().setScreen(new PoliticalEntityNameInputScreen(
                this,
                text("gui.bannermod.states.dialog.rename.title"),
                text("gui.bannermod.states.dialog.rename.prompt"),
                this.selected.name(),
                newName -> sendRename(id, newName)
        ));
    }

    private void setCapitalHere() {
        if (this.selected == null) return;
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageSetPoliticalEntityCapital(this.selected.id()));
    }

    private void toggleGovernmentForm() {
        if (this.selected == null) return;
        GovernmentForm next = this.selected.governmentForm() == GovernmentForm.MONARCHY
                ? GovernmentForm.REPUBLIC
                : GovernmentForm.MONARCHY;
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageSetGovernmentForm(this.selected.id(), next));
    }

    private void promoteToState() {
        if (selected == null) return;
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageSetPoliticalEntityStatus(selected.id(), PoliticalEntityStatus.STATE));
    }

    private void openColorDialog() {
        if (this.selected == null) return;
        UUID id = this.selected.id();
        Minecraft.getInstance().setScreen(new PoliticalEntityColorPaletteScreen(
                this,
                this.selected.color(),
                value -> sendColor(id, value)
        ));
    }

    private void openCharterDialog() {
        if (this.selected == null) return;
        UUID id = this.selected.id();
        Minecraft.getInstance().setScreen(new PoliticalEntityCharterScreen(
                this,
                this.selected.charter(),
                value -> sendCharter(id, value)
        ));
    }

    private void openAddCoLeaderDialog() {
        if (this.selected == null) return;
        Minecraft.getInstance().setScreen(new PoliticalEntityCoLeaderPickerScreen(
                this,
                this.selected.id(),
                this.selected.leaderUuid(),
                this.selected.coLeaderUuids()
        ));
    }

    private void openRemoveCoLeaderDialog() {
        if (this.selected == null) return;
        UUID id = this.selected.id();
        Minecraft.getInstance().setScreen(new PoliticalEntityNameInputScreen(
                this,
                text("gui.bannermod.states.dialog.co_leader_remove.title"),
                text("gui.bannermod.states.dialog.co_leader.prompt"),
                "",
                value -> sendCoLeader(id, value, false),
                36,
                false
        ));
    }

    private void sendColor(UUID entityId, String newColor) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageSetPoliticalEntityColor(entityId, newColor));
    }

    private void sendCharter(UUID entityId, String newCharter) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageSetPoliticalEntityCharter(entityId, newCharter));
    }

    private void sendCreate(String name) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageCreatePoliticalEntity(name));
    }

    private void sendRename(UUID entityId, String newName) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRenamePoliticalEntity(entityId, newName));
    }

    private void sendCoLeader(UUID entityId, String coLeaderUuidText, boolean add) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageUpdateCoLeader(entityId, coLeaderUuidText, add));
    }

    private void updateLeaderButtons() {
        if (this.manageMenu != null) {
            this.manageMenu.setEntries(buildManageEntries());
        }
    }

    private List<ContextMenuEntry> buildManageEntries() {
        boolean canAct = canLocalPlayerAct(this.selected);
        boolean leader = isLocalPlayerLeader(this.selected);
        boolean canPromote = canAct && this.selected != null && this.selected.status() != PoliticalEntityStatus.STATE;
        boolean canRemoveCoLeader = leader && this.selected != null && !this.selected.coLeaderUuids().isEmpty();
        Component toggleFormLabel = this.selected == null
                ? text("gui.bannermod.states.toggle_form")
                : text(this.selected.governmentForm() == GovernmentForm.MONARCHY
                        ? "gui.bannermod.states.to_republic"
                        : "gui.bannermod.states.to_monarchy");

        List<ContextMenuEntry> entries = new ArrayList<>();
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.create").getString(),
                this::openCreateDialog, true));
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.rename").getString(),
                this::openRenameDialog, canAct));
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.capital_here").getString(),
                this::setCapitalHere, canAct));
        entries.add(new ContextMenuEntry(toggleFormLabel.getString(),
                this::toggleGovernmentForm, leader));
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.color").getString(),
                this::openColorDialog, canAct));
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.charter").getString(),
                this::openCharterDialog, canAct));
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.add_co_leader").getString(),
                this::openAddCoLeaderDialog, leader));
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.remove_co_leader").getString(),
                this::openRemoveCoLeaderDialog, canRemoveCoLeader));
        entries.add(new ContextMenuEntry(text("gui.bannermod.states.promote_state").getString(),
                this::promoteToState, canPromote));
        return entries;
    }

    private static boolean isLocalPlayerLeader(@Nullable PoliticalEntityRecord entity) {
        if (entity == null) return false;
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        UUID leader = entity.leaderUuid();
        return leader != null && leader.equals(player.getUUID());
    }

    private static boolean canLocalPlayerAct(@Nullable PoliticalEntityRecord entity) {
        Player player = Minecraft.getInstance().player;
        return player != null && PoliticalEntityAuthority.canAct(player.getUUID(), false, entity);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x66000000);
        renderBookFrame(graphics);
        renderHeader(graphics);
        renderList(graphics, mouseX, mouseY);
        renderDetails(graphics);
        renderActionLedger(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = listX();
        int listY = listY();
        int listW = listW();
        int listH = listVisible * ROW_H;
        graphics.drawString(font, text("gui.bannermod.states.heading").getString(), listX, contentTop() + 8, INK, false);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0x22FFFFFF);
        graphics.renderOutline(listX, listY, listW, listH, PAGE_SHADE);
        int rendered = Math.min(listVisible, Math.max(0, entities.size() - scrollOffset));
        for (int i = 0; i < rendered; i++) {
            PoliticalEntityRecord entity = entities.get(scrollOffset + i);
            int rowY = listY + i * ROW_H;
            boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean picked = selected != null && selected.id().equals(entity.id());
            if (picked || hovered) {
                graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_H - 1, picked ? 0x669E3A23 : 0x33FFFFFF);
            }
            graphics.drawString(font, localizedStatus(entity.status()), listX + 4, rowY + 4, statusColor(entity.status()), false);
            graphics.drawString(font, font.plainSubstrByWidth(" " + displayName(entity), Math.max(20, listW - 76)), listX + 76, rowY + 4, INK, false);
        }
        if (entities.isEmpty()) {
            String empty = text(WarClientState.hasSnapshot()
                    ? "gui.bannermod.states.empty"
                    : "gui.bannermod.states.waiting_sync").getString();
            graphics.renderOutline(listX + 8, listY + listH / 2 - 14, Math.max(20, listW - 16), 28, INK_MUTED);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(empty, Math.max(20, listW - 20)), listX + listW / 2, listY + listH / 2 - 4, INK_MUTED);
        }
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = rightPageX() + 8;
        int y = contentTop() + 8;
        int w = Math.max(40, rightPageW() - 16);
        graphics.drawString(font, text("gui.bannermod.states.detail"), x, y, INK, false);
        if (selected == null) {
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.select_state").getString(), w), x, y + 14, INK_MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.help.settlement").getString(), w), x, y + 30, INK_MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.help.claim").getString(), w), x, y + 42, INK_MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.help.state").getString(), w), x, y + 54, INK_MUTED, false);
            return;
        }
        String[] lines = {
                text("gui.bannermod.states.detail.name", displayName(selected)).getString(),
                text("gui.bannermod.states.detail.status", localizedStatus(selected.status())).getString(),
                text("gui.bannermod.states.detail.government", localizedGovernmentForm(selected.governmentForm()), text(selected.governmentForm().coLeadersShareAuthority() ? "gui.bannermod.states.authority.shared" : "gui.bannermod.states.authority.leader_only")).getString(),
                text("gui.bannermod.states.detail.leader", playerName(selected.leaderUuid())).getString(),
                text("gui.bannermod.states.detail.co_leaders", coLeaderSummary(selected)).getString(),
                text("gui.bannermod.states.detail.co_leader_authority", text(selected.governmentForm().coLeadersShareAuthority() ? "gui.bannermod.states.co_authority.active" : "gui.bannermod.states.co_authority.locked").getString()).getString(),
                text("gui.bannermod.states.detail.capital", selected.capitalPos() == null ? text("gui.bannermod.common.none").getString() : selected.capitalPos().toShortString()).getString(),
                text("gui.bannermod.states.detail.color", selected.color().isBlank() ? text("gui.bannermod.common.none").getString() : selected.color()).getString(),
                text("gui.bannermod.states.detail.charter", charterSummary(selected)).getString(),
                text("gui.bannermod.states.detail.region", selected.homeRegion().isBlank() ? text("gui.bannermod.common.none").getString() : selected.homeRegion()).getString(),
                text("gui.bannermod.states.detail.wars", involvedWarCount(selected)).getString()
        };
        int maxLines = maxDetailLines(y);
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            graphics.drawString(font, font.plainSubstrByWidth(lines[i], w), x, y + 14 + i * 12, INK, false);
        }
        int reqY = y + 14 + lines.length * 12 + 6;
        if (reqY + 48 <= contentBottom() - 8) {
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.progression.requirements").getString(), w), x, reqY, WAX, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.progression.requirement_fort").getString(), w), x, reqY + 12, INK_MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.progression.requirement_storage").getString(), w), x, reqY + 24, INK_MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.progression.requirement_market").getString(), w), x, reqY + 36, INK_MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.states.progression.server_checked").getString(), w), x, reqY + 48, 0xFF7C7164, false);
        }
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
        graphics.drawCenteredString(font, text("gui.bannermod.states.heading").getString(), guiLeft + guiW / 2, guiTop + 9, GOLD);
        graphics.drawString(font,
                font.plainSubstrByWidth(title.getString(), Math.max(40, innerW() / 2 - 8)),
                innerX() + 4, guiTop + 25, INK_MUTED, false);
        graphics.drawString(font,
                text("gui.bannermod.war_list.ledger_title").getString(),
                actionLedgerX() + 8, actionLedgerTop() + 5, INK_MUTED, false);
    }

    private void renderActionLedger(GuiGraphics graphics) {
        int x = actionLedgerX() + 8;
        int y = actionLedgerTop() + 18;
        int w = Math.max(40, actionLedgerW() - 16);
        Component status = visibleActionStatus();
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), w), x, y, INK, false);
        Component feedback = WarClientState.lastActionFeedback();
        if (feedback != null && !feedback.getString().isBlank()) {
            graphics.drawString(font, font.plainSubstrByWidth(feedback.getString(), w), x, y + 12, WAX, false);
        }
    }

    private Component visibleActionStatus() {
        if (!WarClientState.hasSnapshot()) {
            return text("gui.bannermod.states.waiting_sync");
        }
        if (selected == null) {
            return text("gui.bannermod.states.action.select_realm");
        }
        if (canLocalPlayerAct(selected)) {
            if (selected.status() != PoliticalEntityStatus.STATE) {
                return text("gui.bannermod.states.action.server_checked_promotion");
            }
            return text("gui.bannermod.states.action.authorized");
        }
        if (isLocalPlayerLeader(selected)) {
            return text("gui.bannermod.states.tooltip.need_authority");
        }
        return text("gui.bannermod.states.action.read_only");
    }

    private int maxDetailLines(int titleY) {
        int firstLineY = titleY + 14;
        int detailBottom = contentBottom() - 8;
        if (detailBottom < firstLineY) {
            return 0;
        }
        return ((detailBottom - firstLineY) / 12) + 1;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static Component localizedGovernmentForm(GovernmentForm form) {
        return switch (form) {
            case MONARCHY -> text("gui.bannermod.states.government.monarchy");
            case REPUBLIC -> text("gui.bannermod.states.government.republic");
        };
    }

    private static Component localizedStatus(PoliticalEntityStatus status) {
        return switch (status) {
            case SETTLEMENT -> text("gui.bannermod.states.status.settlement");
            case STATE -> text("gui.bannermod.states.status.state");
            case VASSAL -> text("gui.bannermod.states.status.vassal");
            case PEACEFUL -> text("gui.bannermod.states.status.peaceful");
        };
    }

    private String coLeaderSummary(PoliticalEntityRecord entity) {
        if (entity.coLeaderUuids().isEmpty()) {
            return text("gui.bannermod.common.none").getString();
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(3, entity.coLeaderUuids().size()); i++) {
            names.add(playerName(entity.coLeaderUuids().get(i)));
        }
        String suffix = entity.coLeaderUuids().size() > names.size() ? " +" + (entity.coLeaderUuids().size() - names.size()) : "";
        return String.join(", ", names) + suffix;
    }

    private String charterSummary(PoliticalEntityRecord entity) {
        if (entity.charter().isBlank()) {
            return text("gui.bannermod.common.none").getString();
        }
        return entity.charter().replaceAll("\\s+", " ").trim();
    }

    private static int involvedWarCount(PoliticalEntityRecord entity) {
        int count = 0;
        for (var war : WarClientState.wars()) {
            if (war.involves(entity.id())) {
                count++;
            }
        }
        return count;
    }

    private static String displayName(PoliticalEntityRecord entity) {
        return entity.name().isBlank() ? text("gui.bannermod.states.unnamed").getString() : entity.name();
    }

    private static String playerName(@Nullable UUID id) {
        if (id == null) {
            return text("gui.bannermod.common.unknown").getString();
        }
        String name = GameProfileUtils.getPlayerName(id);
        return name == null || name.isBlank() ? text("gui.bannermod.common.unknown").getString() : name;
    }

    private static int statusColor(PoliticalEntityStatus status) {
        return switch (status) {
            case STATE -> 0xFF55FF55;
            case VASSAL -> 0xFFFFFF55;
            case PEACEFUL -> 0xFF55AAFF;
            case SETTLEMENT -> 0xFFAAAAAA;
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
                int idx = scrollOffset + (int) ((mouseY - listY) / ROW_H);
                if (idx >= 0 && idx < entities.size()) {
                    selected = entities.get(idx);
                    updateLeaderButtons();
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
        int max = Math.max(0, entities.size() - listVisible);
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
