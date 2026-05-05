package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.network.messages.civilian.MessageApproveHousingRequest;
import com.talhanation.bannermod.network.messages.civilian.MessageDenyHousingRequest;
import com.talhanation.bannermod.network.messages.civilian.MessageRequestHousingSnapshot;
import com.talhanation.bannermod.society.NpcHousingLedgerEntry;
import com.talhanation.bannermod.society.NpcHousingPriorityService;
import com.talhanation.bannermod.society.client.NpcHousingClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HousingLedgerScreen extends Screen {
    private static final int MIN_BOOK_W = 392;
    private static final int MAX_BOOK_W = 760;
    private static final int MIN_BOOK_H = 220;
    private static final int MAX_BOOK_H = 520;
    private static final int ROW_H = 18;
    private static final int BUTTON_H = 18;
    private static final int BOOK_BORDER = 10;
    private static final int PAGE_SHADE = 0xFFE0BC78;
    private static final int LEATHER = 0xFF4A2D18;
    private static final int LEATHER_DARK = 0xFF24150D;
    private static final int INK = 0xFF2D1B0F;
    private static final int INK_MUTED = 0xFF6C5030;
    private static final int GOLD = 0xFFFFD36A;

    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int listVisible = 8;
    private int scrollOffset;
    private int observedVersion = -1;
    private List<NpcHousingLedgerEntry> requests = List.of();
    @Nullable
    private NpcHousingLedgerEntry selected;

    private Button approveBtn;
    private Button denyBtn;
    private Button refreshBtn;
    private Button backBtn;

    public HousingLedgerScreen(@Nullable Screen parent) {
        super(text("gui.bannermod.housing_ledger.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        updateGeometry();
        this.approveBtn = actionButton(0, text("gui.bannermod.housing_ledger.action.approve"), btn -> approveSelected());
        this.denyBtn = actionButton(1, text("gui.bannermod.housing_ledger.action.deny"), btn -> denySelected());
        this.refreshBtn = actionButton(2, text("gui.bannermod.common.refresh"), btn -> requestSnapshot());
        this.backBtn = actionButton(3, text("gui.bannermod.common.back"), btn -> onClose());
        addRenderableWidget(this.approveBtn);
        addRenderableWidget(this.denyBtn);
        addRenderableWidget(this.refreshBtn);
        addRenderableWidget(this.backBtn);
        requestSnapshot();
    }

    private void requestSnapshot() {
        NpcHousingClientState.beginSync();
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRequestHousingSnapshot());
        refreshLocal();
    }

    private void refreshLocal() {
        this.requests = new ArrayList<>(NpcHousingClientState.requests());
        if (this.selected != null) {
            UUID selectedHouseholdId = this.selected.householdId();
            this.selected = this.requests.stream()
                    .filter(entry -> entry.householdId().equals(selectedHouseholdId))
                    .findFirst()
                    .orElse(null);
        }
        this.scrollOffset = clamp(this.scrollOffset, 0, Math.max(0, this.requests.size() - this.listVisible));
        this.observedVersion = NpcHousingClientState.version();
        updateButtons();
    }

    private void approveSelected() {
        if (this.selected == null) {
            return;
        }
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageApproveHousingRequest(this.selected.householdId()));
    }

    private void denySelected() {
        if (this.selected == null) {
            return;
        }
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageDenyHousingRequest(this.selected.householdId()));
    }

    private Button actionButton(int index, Component label, Button.OnPress onPress) {
        return new MedievalButton(actionButtonX(index), actionButtonY(index), actionButtonW(), BUTTON_H, label, onPress);
    }

    private void updateButtons() {
        boolean hasSelection = this.selected != null;
        boolean canManage = NpcHousingClientState.canManage();
        this.approveBtn.active = hasSelection && canManage && NpcHousingPriorityService.canApprove(this.selected);
        this.denyBtn.active = hasSelection && canManage && NpcHousingPriorityService.canDeny(this.selected);
        this.approveBtn.setTooltip(approveTooltip(hasSelection, canManage));
        this.denyBtn.setTooltip(denyTooltip(hasSelection, canManage));
    }

    private @Nullable Tooltip approveTooltip(boolean hasSelection, boolean canManage) {
        if (this.approveBtn.active) {
            return null;
        }
        if (!hasSelection) {
            return Tooltip.create(text("gui.bannermod.housing_ledger.tooltip.select_request"));
        }
        if (!canManage) {
            return Tooltip.create(readOnlyReason());
        }
        return Tooltip.create(text("gui.bannermod.housing_ledger.tooltip.approve_unavailable"));
    }

    private @Nullable Tooltip denyTooltip(boolean hasSelection, boolean canManage) {
        if (this.denyBtn.active) {
            return null;
        }
        if (!hasSelection) {
            return Tooltip.create(text("gui.bannermod.housing_ledger.tooltip.select_request"));
        }
        if (!canManage) {
            return Tooltip.create(readOnlyReason());
        }
        return Tooltip.create(text("gui.bannermod.housing_ledger.tooltip.deny_unavailable"));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.observedVersion != NpcHousingClientState.version()) {
            refreshLocal();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
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

    private void renderBookFrame(GuiGraphics graphics) {
        graphics.fill(this.guiLeft + 4, this.guiTop + 5, this.guiLeft + this.guiW + 4, this.guiTop + this.guiH + 5, 0x66000000);
        MilitaryGuiStyle.parchmentPanel(graphics, this.guiLeft, this.guiTop, this.guiW, this.guiH);

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
        graphics.drawCenteredString(this.font, text("gui.bannermod.housing_ledger.heading").getString(), this.guiLeft + this.guiW / 2, this.guiTop + 9, GOLD);
        graphics.drawString(this.font,
                this.font.plainSubstrByWidth(this.title.getString(), Math.max(40, innerW() / 2 - 8)),
                innerX() + 4, this.guiTop + 25, INK_MUTED, false);
        graphics.drawString(this.font,
                text("gui.bannermod.housing_ledger.ledger_title").getString(),
                actionLedgerX() + 8, actionLedgerTop() + 5, INK_MUTED, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = listX();
        int listY = listY();
        int listW = listW();
        int listH = this.listVisible * ROW_H;
        graphics.drawString(this.font, text("gui.bannermod.housing_ledger.list_title").getString(), listX, contentTop() + 8, INK, false);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0x22FFFFFF);
        graphics.renderOutline(listX, listY, listW, listH, PAGE_SHADE);
        int rendered = Math.min(this.listVisible, Math.max(0, this.requests.size() - this.scrollOffset));
        for (int i = 0; i < rendered; i++) {
            NpcHousingLedgerEntry entry = this.requests.get(this.scrollOffset + i);
            int rowY = listY + i * ROW_H;
            boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean picked = this.selected != null && this.selected.householdId().equals(entry.householdId());
            if (picked || hovered) {
                graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_H - 1, picked ? 0x669E3A23 : 0x33FFFFFF);
            }
            String badge = "#" + entry.queueRank() + " [" + Component.translatable(entry.urgencyTranslationKey()).getString().toUpperCase(Locale.ROOT) + "]";
            graphics.drawString(this.font, badge, listX + 4, rowY + 4, urgencyColor(entry.urgencyTag()), false);
            String label = Component.translatable("gui.bannermod.housing_ledger.list_row", shortId(entry.householdId()), entry.householdSize()).getString();
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(" " + label, Math.max(20, listW - 126)),
                    listX + 98, rowY + 4, INK, false);
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(Component.translatable(entry.statusTranslationKey()).getString(), 58),
                    listX + listW - 60, rowY + 4, statusColor(entry.statusTag()), false);
        }
        if (showEmptyPanel()) {
            String empty = emptyListMessage().getString();
            graphics.renderOutline(listX + 8, listY + listH / 2 - 14, Math.max(20, listW - 16), 28, INK_MUTED);
            graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(empty, Math.max(20, listW - 20)), listX + listW / 2, listY + listH / 2 - 4, INK_MUTED);
        }
    }

    private boolean showEmptyPanel() {
        return this.requests.isEmpty() || NpcHousingClientState.syncPending();
    }

    private Component emptyListMessage() {
        if (NpcHousingClientState.syncPending() || !NpcHousingClientState.hasSnapshot()) {
            return text("gui.bannermod.housing_ledger.waiting_sync");
        }
        if (!NpcHousingClientState.hasClaim()) {
            return text("gui.bannermod.housing_ledger.no_claim");
        }
        if (this.requests.isEmpty()) {
            return text("gui.bannermod.housing_ledger.empty");
        }
        return text("gui.bannermod.housing_ledger.select_request");
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = rightPageX() + 8;
        int y = contentTop() + 8;
        int w = Math.max(40, rightPageW() - 16);
        graphics.drawString(this.font, text("gui.bannermod.housing_ledger.detail").getString(), x, y, INK, false);
        if (this.selected == null) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(text("gui.bannermod.housing_ledger.select_request").getString(), w), x, y + 14, INK_MUTED, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(text("gui.bannermod.housing_ledger.help").getString(), w), x, y + 28, INK_MUTED, false);
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(text("gui.bannermod.housing_ledger.detail.rank", this.selected.queueRank(), this.selected.priorityScore()).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.urgency", Component.translatable(this.selected.urgencyTranslationKey())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.reason", Component.translatable(this.selected.reasonTranslationKey())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.status", Component.translatable(this.selected.statusTranslationKey())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.household", shortId(this.selected.householdId()), shortId(this.selected.headResidentUuid())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.members", this.selected.householdSize(), Component.translatable(this.selected.housingStateTranslationKey())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.wait", this.selected.waitingDays(), this.selected.requestedAtGameTime()).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.resident", shortId(this.selected.residentUuid())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.claim", shortId(this.selected.claimUuid())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.home", shortId(this.selected.homeBuildingUuid())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.build_area", shortId(this.selected.buildAreaUuid())).getString());
        lines.add(text("gui.bannermod.housing_ledger.detail.plot", plotLabel(this.selected)).getString());
        int maxLines = maxDetailLines(y);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(lines.get(i), w), x, y + 14 + i * 12, i >= 8 ? INK_MUTED : INK, false);
        }
    }

    private void renderActionLedger(GuiGraphics graphics) {
        int x = actionLedgerX() + 8;
        int y = actionLedgerTop() + 18;
        int w = Math.max(40, actionLedgerW() - 16);
        Component status = visibleActionStatus();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), w), x, y, INK, false);
    }

    private Component visibleActionStatus() {
        if (NpcHousingClientState.syncPending() || !NpcHousingClientState.hasSnapshot()) {
            return text("gui.bannermod.housing_ledger.waiting_sync");
        }
        if (!NpcHousingClientState.hasClaim()) {
            return text("gui.bannermod.housing_ledger.no_claim");
        }
        if (!NpcHousingClientState.canManage()) {
            return readOnlyReason();
        }
        if (this.selected == null) {
            return text("gui.bannermod.housing_ledger.select_request");
        }
        if (NpcHousingPriorityService.canApprove(this.selected)) {
            return text("gui.bannermod.housing_ledger.action.approve_ready");
        }
        if (NpcHousingPriorityService.canDeny(this.selected)) {
            return text("gui.bannermod.housing_ledger.action.deny_ready");
        }
        return text("gui.bannermod.housing_ledger.action.authorized");
    }

    private Component readOnlyReason() {
        String denialKey = NpcHousingClientState.denialKey();
        return denialKey == null || denialKey.isBlank()
                ? text("gui.bannermod.housing_ledger.action.read_only")
                : Component.translatable(denialKey);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listX = listX();
            int listY = listY();
            int listW = listW();
            int listH = this.listVisible * ROW_H;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
                int idx = this.scrollOffset + (int) ((mouseY - listY) / ROW_H);
                if (idx >= 0 && idx < this.requests.size()) {
                    this.selected = this.requests.get(idx);
                    updateButtons();
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
        int listH = this.listVisible * ROW_H;
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY || mouseY >= listY + listH) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
        }
        int max = Math.max(0, this.requests.size() - this.listVisible);
        this.scrollOffset = clamp(this.scrollOffset - (int) Math.signum(delta), 0, max);
        return true;
    }

    private void updateGeometry() {
        int viewportW = Math.max(1, this.width - 12);
        int viewportH = Math.max(1, this.height - 12);
        int minW = Math.min(MIN_BOOK_W, viewportW);
        int minH = Math.min(MIN_BOOK_H, viewportH);
        this.guiW = Math.min(MAX_BOOK_W, Math.max(minW, this.width - 28));
        this.guiH = Math.min(MAX_BOOK_H, Math.max(minH, this.height - 24));
        this.guiLeft = (this.width - this.guiW) / 2;
        this.guiTop = (this.height - this.guiH) / 2;
        this.listVisible = Math.max(1, listH() / ROW_H);
    }

    private int innerX() {
        return this.guiLeft + BOOK_BORDER + 8;
    }

    private int innerW() {
        return this.guiW - (BOOK_BORDER + 8) * 2;
    }

    private int pageGap() {
        return Math.max(12, this.guiW / 54);
    }

    private int contentTop() {
        return this.guiTop + 38;
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
        return this.guiTop + this.guiH - actionLedgerH() - 8;
    }

    private int actionLedgerH() {
        return 32 + (BUTTON_H + 4);
    }

    private int actionLedgerX() {
        return innerX();
    }

    private int actionLedgerW() {
        return innerW();
    }

    private int actionButtonW() {
        return Math.max(64, (actionLedgerW() - 16 - 3 * 6) / 4);
    }

    private int actionButtonX(int index) {
        return actionLedgerX() + 8 + index * (actionButtonW() + 6);
    }

    private int actionButtonY(int index) {
        return actionLedgerTop() + 30;
    }

    private int maxDetailLines(int titleY) {
        int firstLineY = titleY + 14;
        int detailBottom = contentBottom() - 8;
        if (detailBottom < firstLineY) {
            return 0;
        }
        return ((detailBottom - firstLineY) / 12) + 1;
    }

    private static int urgencyColor(String urgencyTag) {
        return switch (safeTag(urgencyTag)) {
            case "CRITICAL" -> 0xFFD75B4E;
            case "HIGH" -> 0xFFD9A441;
            case "MEDIUM" -> 0xFF79B15A;
            default -> 0xFFAAAAAA;
        };
    }

    private static int statusColor(String statusTag) {
        return switch (safeTag(statusTag)) {
            case "REQUESTED" -> 0xFFD9A441;
            case "DENIED" -> 0xFFD75B4E;
            case "APPROVED" -> 0xFF79B15A;
            default -> 0xFFAAAAAA;
        };
    }

    private static String plotLabel(NpcHousingLedgerEntry entry) {
        if (entry == null || entry.reservedPlotPos() == null) {
            return "-";
        }
        return entry.reservedPlotPos().getX() + " " + entry.reservedPlotPos().getY() + " " + entry.reservedPlotPos().getZ();
    }

    private static String shortId(@Nullable UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        String raw = uuid.toString();
        return raw.length() > 8 ? raw.substring(0, 8) : raw;
    }

    private static String safeTag(@Nullable String value) {
        return value == null || value.isBlank() ? "UNSPECIFIED" : value.toUpperCase(Locale.ROOT);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
            if (font.width(label) <= maxWidth) {
                return label;
            }
            String ellipsis = "...";
            int textWidth = Math.max(1, maxWidth - font.width(ellipsis));
            return font.plainSubstrByWidth(label, textWidth) + ellipsis;
        }
    }
}
