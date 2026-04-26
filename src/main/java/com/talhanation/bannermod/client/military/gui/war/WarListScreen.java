package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.network.messages.war.MessagePlaceSiegeStandardHere;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.runtime.BattleWindowClock;
import com.talhanation.bannermod.war.runtime.BattleWindowDisplay;
import com.talhanation.bannermod.war.runtime.SiegeStandardRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final int W = 380;
    private static final int H = 252;
    private static final int ROW_H = 16;
    private static final int LIST_VISIBLE = 10;
    private static final int LIST_TOP_OFFSET = 36;
    private static final int DETAIL_TOP_OFFSET = 36;

    private final Screen parent;

    private int guiLeft;
    private int guiTop;
    private int scrollOffset = 0;
    private List<WarDeclarationRecord> wars = List.of();
    @Nullable
    private WarDeclarationRecord selected;

    private Button openAttackerBtn;
    private Button openDefenderBtn;
    private Button placeSiegeBtn;
    private Button alliesBtn;
    private Button statesBtn;
    private Button refreshBtn;
    private Button closeBtn;

    public WarListScreen(@Nullable Screen parent) {
        super(Component.literal("Wars"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - W) / 2;
        this.guiTop = (this.height - H) / 2;
        this.wars = new ArrayList<>(WarClientState.wars());
        if (this.selected != null) {
            this.selected = WarClientState.wars().stream()
                    .filter(w -> w.id().equals(this.selected.id()))
                    .findFirst()
                    .orElse(null);
        }

        int detailX = guiLeft + 200;
        int detailY = guiTop + H - 60;

        openAttackerBtn = Button.builder(Component.literal("Attacker info"), btn -> openEntity(selected != null ? selected.attackerPoliticalEntityId() : null))
                .bounds(detailX, detailY, 80, 18).build();
        openDefenderBtn = Button.builder(Component.literal("Defender info"), btn -> openEntity(selected != null ? selected.defenderPoliticalEntityId() : null))
                .bounds(detailX + 88, detailY, 80, 18).build();
        statesBtn = Button.builder(Component.literal("States"), btn -> this.minecraft.setScreen(new PoliticalEntityListScreen(this)))
                .bounds(detailX, detailY + 22, 80, 18).build();
        placeSiegeBtn = Button.builder(Component.literal("Place siege here"), btn -> placeSiegeHere())
                .bounds(detailX, detailY + 44, 80, 18).build();
        refreshBtn = Button.builder(Component.literal("Refresh"), btn -> refresh())
                .bounds(detailX + 88, detailY + 22, 80, 18).build();
        closeBtn = Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(detailX + 88, detailY + 44, 80, 18).build();

        int alliesY = guiTop + LIST_TOP_OFFSET + LIST_VISIBLE * ROW_H + 4;
        alliesBtn = Button.builder(Component.literal("Allies for selected war"), btn -> openAllies())
                .bounds(guiLeft + 8, alliesY, 184, 18).build();

        addRenderableWidget(openAttackerBtn);
        addRenderableWidget(openDefenderBtn);
        addRenderableWidget(statesBtn);
        addRenderableWidget(placeSiegeBtn);
        addRenderableWidget(alliesBtn);
        addRenderableWidget(refreshBtn);
        addRenderableWidget(closeBtn);

        updateButtonsState();
    }

    private void placeSiegeHere() {
        UUID sideId = leaderSideOf(selected);
        if (selected == null || sideId == null) return;
        BannerModMain.SIMPLE_CHANNEL.sendToServer(
                new MessagePlaceSiegeStandardHere(selected.id(), sideId, 0));
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
        updateButtonsState();
    }

    private void openEntity(@Nullable UUID entityId) {
        if (entityId == null) return;
        this.minecraft.setScreen(new PoliticalEntityInfoScreen(this, entityId));
    }

    private void updateButtonsState() {
        boolean has = selected != null;
        openAttackerBtn.active = has;
        openDefenderBtn.active = has;
        if (placeSiegeBtn != null) {
            placeSiegeBtn.active = has && leaderSideOf(selected) != null
                    && selected.state() != WarState.RESOLVED && selected.state() != WarState.CANCELLED;
        }
        if (alliesBtn != null) {
            alliesBtn.active = has;
        }
    }

    @Nullable
    private UUID leaderSideOf(@Nullable WarDeclarationRecord war) {
        if (war == null) return null;
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        UUID local = player.getUUID();
        UUID attacker = war.attackerPoliticalEntityId();
        UUID defender = war.defenderPoliticalEntityId();
        if (attacker != null && isLeaderOf(attacker, local)) return attacker;
        if (defender != null && isLeaderOf(defender, local)) return defender;
        return null;
    }

    private static boolean isLeaderOf(UUID entityId, UUID playerUuid) {
        PoliticalEntityRecord entity = WarClientState.entityById(entityId);
        if (entity == null) return false;
        UUID leader = entity.leaderUuid();
        return leader != null && leader.equals(playerUuid);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(guiLeft, guiTop, guiLeft + W, guiTop + H, 0xC0101010);
        graphics.renderOutline(guiLeft, guiTop, W, H, 0xFFFFFFFF);

        int titleY = guiTop + 6;
        graphics.drawCenteredString(font, "Active Wars (" + wars.size() + ")", guiLeft + W / 2, titleY, 0xFFFFFF);

        renderBattleWindowBanner(graphics);
        renderList(graphics, mouseX, mouseY);
        renderDetailPanel(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBattleWindowBanner(GuiGraphics graphics) {
        BattleWindowClock.Phase phase = BattleWindowClock.compute(
                WarClientState.schedule(), ZonedDateTime.now());
        String text = BattleWindowDisplay.formatPhase(phase);
        int color = phase instanceof BattleWindowClock.Phase.Open ? 0xFF55FF55 : 0xFFAAAAAA;
        graphics.drawString(font,
                font.plainSubstrByWidth(text, W - 16),
                guiLeft + 8, guiTop + 20, color, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = guiLeft + 8;
        int listY = guiTop + LIST_TOP_OFFSET;
        int listW = 184;
        int listH = LIST_VISIBLE * ROW_H;
        graphics.fill(listX, listY, listX + listW, listY + listH, 0x60000000);

        int rendered = Math.min(LIST_VISIBLE, Math.max(0, wars.size() - scrollOffset));
        for (int i = 0; i < rendered; i++) {
            WarDeclarationRecord war = wars.get(scrollOffset + i);
            int rowY = listY + i * ROW_H;
            boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean isSelected = selected != null && selected.id().equals(war.id());

            int rowBg = isSelected ? 0xFF3B5BFF : (hovered ? 0x60FFFFFF : 0);
            if (rowBg != 0) {
                graphics.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, rowBg);
            }

            String stateBadge = "[" + war.state().name() + "]";
            int stateColor = stateColor(war.state());
            graphics.drawString(font, stateBadge, listX + 4, rowY + 4, stateColor, false);

            String attackerName = entityName(war.attackerPoliticalEntityId());
            String defenderName = entityName(war.defenderPoliticalEntityId());
            String label = " " + attackerName + " → " + defenderName;
            graphics.drawString(font, font.plainSubstrByWidth(label, listW - 80), listX + 80, rowY + 4, 0xFFFFFF, false);
        }

        if (wars.isEmpty()) {
            graphics.drawCenteredString(font, "No active wars", listX + listW / 2, listY + listH / 2 - 4, 0xAAAAAA);
        }
    }

    private void renderDetailPanel(GuiGraphics graphics) {
        int x = guiLeft + 200;
        int y = guiTop + DETAIL_TOP_OFFSET;
        int w = W - 200 - 8;

        graphics.drawString(font, "Detail", x, y, 0xFFFFFF, false);
        if (selected == null) {
            graphics.drawString(font, "Select a war.", x, y + 14, 0xAAAAAA, false);
            return;
        }

        WarDeclarationRecord war = selected;
        int line = 0;
        String[] body = {
                "Attacker: " + entityName(war.attackerPoliticalEntityId()),
                "Defender: " + entityName(war.defenderPoliticalEntityId()),
                "State: " + war.state().name(),
                "Goal: " + war.goalType().name(),
                "Casus belli: " + (war.casusBelli().isEmpty() ? "(none)" : war.casusBelli()),
                "Declared: t=" + war.declaredAtGameTime(),
                "Earliest: t=" + war.earliestActivationGameTime(),
                "Allies attacker: " + war.attackerAllyIds().size(),
                "Allies defender: " + war.defenderAllyIds().size(),
                "Targets: " + war.targetPositions().size(),
                "Sieges: " + activeSiegeCount(war.id()),
                "Id: " + shortId(war.id())
        };
        for (String s : body) {
            graphics.drawString(font, font.plainSubstrByWidth(s, w), x, y + 14 + line * 11, 0xFFFFFF, false);
            line++;
        }
        for (SiegeStandardRecord siege : WarClientState.sieges()) {
            if (!siege.warId().equals(war.id())) {
                continue;
            }
            String side = entityName(siege.sidePoliticalEntityId());
            String pos = siege.pos() == null ? "?" : siege.pos().toShortString();
            String radius = " r=" + siege.radius() + " blocks";
            graphics.drawString(font, font.plainSubstrByWidth("Standard: " + side + " @ " + pos + radius, w),
                    x, y + 14 + line * 11, 0xFFAAFFAA, false);
            line++;
            if (line >= 14) {
                break;
            }
        }
    }

    private int activeSiegeCount(UUID warId) {
        int count = 0;
        for (SiegeStandardRecord siege : WarClientState.sieges()) {
            if (siege.warId().equals(warId)) count++;
        }
        return count;
    }

    private String entityName(UUID id) {
        if (id == null) return "(unknown)";
        PoliticalEntityRecord entity = WarClientState.entityById(id);
        if (entity == null) return shortId(id);
        return entity.name().isBlank() ? shortId(id) : entity.name();
    }

    private static String shortId(UUID id) {
        if (id == null) return "?";
        String s = id.toString();
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    private static int stateColor(WarState state) {
        return switch (state) {
            case ACTIVE -> 0xFFFF5555;
            case IN_SIEGE_WINDOW -> ChatFormatting.RED.getColor() == null ? 0xFFFF0000 : ChatFormatting.RED.getColor();
            case DECLARED -> 0xFFFFFF55;
            case RESOLVED, CANCELLED -> 0xFFAAAAAA;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listX = guiLeft + 8;
            int listY = guiTop + LIST_TOP_OFFSET;
            int listW = 184;
            int listH = LIST_VISIBLE * ROW_H;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
                int row = (int) ((mouseY - listY) / ROW_H);
                int idx = scrollOffset + row;
                if (idx >= 0 && idx < wars.size()) {
                    selected = wars.get(idx);
                    updateButtonsState();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, wars.size() - LIST_VISIBLE);
        scrollOffset = clamp(scrollOffset - (int) Math.signum(delta), 0, max);
        return true;
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
