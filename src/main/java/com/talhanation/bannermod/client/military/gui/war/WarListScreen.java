package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.network.messages.war.MessagePlaceSiegeStandardHere;
import com.talhanation.bannermod.network.messages.war.MessageResolveWarOutcome;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.runtime.BattleWindowClock;
import com.talhanation.bannermod.war.runtime.BattleWindowDisplay;
import com.talhanation.bannermod.war.runtime.OccupationRecord;
import com.talhanation.bannermod.war.runtime.RevoltRecord;
import com.talhanation.bannermod.war.runtime.RevoltState;
import com.talhanation.bannermod.war.runtime.SiegeStandardRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
    private int observedWarStateVersion = -1;
    @Nullable
    private WarDeclarationRecord selected;

    private Button openAttackerBtn;
    private Button openDefenderBtn;
    private Button placeSiegeBtn;
    private Button alliesBtn;
    private Button declareBtn;
    private Button cancelWarBtn;
    private Button occupyBtn;
    private Button annexBtn;
    private Button tributeLockedBtn;
    private Button statesBtn;
    private Button refreshBtn;
    private Button closeBtn;

    public WarListScreen(@Nullable Screen parent) {
        super(text("gui.bannermod.war_list.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - W) / 2;
        this.guiTop = (this.height - H) / 2;
        int detailX = guiLeft + 200;
        int detailY = guiTop + H - 60;

        openAttackerBtn = Button.builder(text("gui.bannermod.war_list.attacker_info"), btn -> openEntity(selected != null ? selected.attackerPoliticalEntityId() : null))
                .bounds(detailX, detailY, 80, 18).build();
        openDefenderBtn = Button.builder(text("gui.bannermod.war_list.defender_info"), btn -> openEntity(selected != null ? selected.defenderPoliticalEntityId() : null))
                .bounds(detailX + 88, detailY, 80, 18).build();
        statesBtn = Button.builder(text("gui.bannermod.war_list.states"), btn -> this.minecraft.setScreen(new PoliticalEntityListScreen(this)))
                .bounds(detailX, detailY + 22, 80, 18).build();
        placeSiegeBtn = Button.builder(text("gui.bannermod.war_list.place_siege"), btn -> placeSiegeHere())
                .bounds(detailX, detailY + 44, 80, 18).build();
        refreshBtn = Button.builder(text("gui.bannermod.common.refresh"), btn -> refresh())
                .bounds(detailX + 88, detailY + 22, 80, 18).build();
        closeBtn = Button.builder(text("gui.bannermod.common.close"), btn -> onClose())
                .bounds(detailX + 88, detailY + 44, 80, 18).build();

        int alliesY = guiTop + LIST_TOP_OFFSET + LIST_VISIBLE * ROW_H + 4;
        alliesBtn = Button.builder(text("gui.bannermod.war_list.allies"), btn -> openAllies())
                .bounds(guiLeft + 8, alliesY, 184, 18).build();
        declareBtn = Button.builder(text("gui.bannermod.war_list.declare"), btn -> this.minecraft.setScreen(new WarDeclareScreen(this)))
                .bounds(guiLeft + 8, alliesY + 22, 184, 18).build();
        cancelWarBtn = Button.builder(text("gui.bannermod.war_list.cancel"), btn -> sendOutcome(MessageResolveWarOutcome.Action.CANCEL))
                .bounds(detailX, detailY - 46, 80, 18).build();
        occupyBtn = Button.builder(text("gui.bannermod.war_list.occupy"), btn -> sendOutcome(MessageResolveWarOutcome.Action.OCCUPY))
                .bounds(detailX + 88, detailY - 46, 80, 18).build();
        annexBtn = Button.builder(text("gui.bannermod.war_list.annex"), btn -> sendOutcome(MessageResolveWarOutcome.Action.ANNEX))
                .bounds(detailX, detailY - 24, 80, 18).build();
        tributeLockedBtn = Button.builder(text("gui.bannermod.war_list.tribute_locked"), btn -> sendOutcome(MessageResolveWarOutcome.Action.TRIBUTE))
                .bounds(detailX + 88, detailY - 24, 80, 18).build();

        addRenderableWidget(openAttackerBtn);
        addRenderableWidget(openDefenderBtn);
        addRenderableWidget(statesBtn);
        addRenderableWidget(placeSiegeBtn);
        addRenderableWidget(alliesBtn);
        addRenderableWidget(declareBtn);
        addRenderableWidget(cancelWarBtn);
        addRenderableWidget(occupyBtn);
        addRenderableWidget(annexBtn);
        addRenderableWidget(tributeLockedBtn);
        addRenderableWidget(refreshBtn);
        addRenderableWidget(closeBtn);

        refresh();
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
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageResolveWarOutcome(selected.id(), action));
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
        int maxScroll = Math.max(0, wars.size() - LIST_VISIBLE);
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
        if (placeSiegeBtn != null) {
            placeSiegeBtn.active = has && leaderSideOf(selected) != null
                    && selected.state() != WarState.RESOLVED && selected.state() != WarState.CANCELLED;
            placeSiegeBtn.setTooltip(placeSiegeBtn.active ? null : Tooltip.create(has
                    ? text("gui.bannermod.war_list.tooltip.active_leader_required")
                    : text("gui.bannermod.war_list.tooltip.select_war")));
        }
        if (alliesBtn != null) {
            alliesBtn.active = has;
            alliesBtn.setTooltip(has ? null : Tooltip.create(text("gui.bannermod.war_list.tooltip.select_war")));
        }
        if (declareBtn != null) {
            declareBtn.active = WarClientState.entities().stream().anyMatch(entity -> {
                Player player = Minecraft.getInstance().player;
                return player != null && PoliticalEntityAuthority.canAct(player.getUUID(), false, entity) && entity.status().canDeclareOffensiveWar();
            });
            declareBtn.setTooltip(declareBtn.active ? null : Tooltip.create(text("gui.bannermod.war_list.tooltip.no_declarer")));
        }
        boolean live = has && selected.state() != WarState.RESOLVED && selected.state() != WarState.CANCELLED;
        boolean attackerLeader = has && canLocalPlayerActFor(selected.attackerPoliticalEntityId());
        if (cancelWarBtn != null) {
            cancelWarBtn.active = live && attackerLeader;
            cancelWarBtn.setTooltip(cancelWarBtn.active ? null : Tooltip.create(outcomeLockedTooltip(has, live)));
        }
        if (occupyBtn != null) {
            occupyBtn.active = live && attackerLeader;
            occupyBtn.setTooltip(occupyBtn.active ? null : Tooltip.create(outcomeLockedTooltip(has, live)));
        }
        if (annexBtn != null) {
            annexBtn.active = live && attackerLeader;
            annexBtn.setTooltip(annexBtn.active ? null : Tooltip.create(outcomeLockedTooltip(has, live)));
        }
        if (tributeLockedBtn != null) {
            tributeLockedBtn.active = false;
            tributeLockedBtn.setTooltip(Tooltip.create(text("gui.bannermod.war_list.tooltip.op_only")));
        }
    }

    private static Component outcomeLockedTooltip(boolean hasSelection, boolean liveWar) {
        if (!hasSelection) return text("gui.bannermod.war_list.tooltip.select_war");
        if (!liveWar) return text("gui.bannermod.war_list.tooltip.war_closed");
        return text("gui.bannermod.war_list.tooltip.attacker_leader_required");
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
        renderBackground(graphics);
        graphics.fill(guiLeft, guiTop, guiLeft + W, guiTop + H, 0xC0101010);
        graphics.renderOutline(guiLeft, guiTop, W, H, 0xFFFFFFFF);

        int titleY = guiTop + 6;
        graphics.drawCenteredString(font, text("gui.bannermod.war_list.active_wars", wars.size()).getString(), guiLeft + W / 2, titleY, 0xFFFFFF);

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
            String label = text("gui.bannermod.war_list.row", attackerName, defenderName).getString();
            graphics.drawString(font, font.plainSubstrByWidth(label, listW - 80), listX + 80, rowY + 4, 0xFFFFFF, false);
        }

        if (wars.isEmpty()) {
            String empty = text(WarClientState.hasSnapshot()
                    ? "gui.bannermod.war_list.empty"
                    : "gui.bannermod.war_list.waiting_sync").getString();
            graphics.drawCenteredString(font, empty, listX + listW / 2, listY + listH / 2 - 4, 0xAAAAAA);
        }
    }

    private void renderDetailPanel(GuiGraphics graphics) {
        int x = guiLeft + 200;
        int y = guiTop + DETAIL_TOP_OFFSET;
        int w = W - 200 - 8;

        graphics.drawString(font, text("gui.bannermod.war_list.detail"), x, y, 0xFFFFFF, false);
        if (selected == null) {
            graphics.drawString(font, text("gui.bannermod.war_list.select_war"), x, y + 14, 0xAAAAAA, false);
            graphics.drawString(font, text("gui.bannermod.war_list.outcome_hint"), x, y + 28, 0x777777, false);
            return;
        }

        WarDeclarationRecord war = selected;
        int line = 0;
        String[] body = {
                text("gui.bannermod.war_list.detail.attacker", entityName(war.attackerPoliticalEntityId())).getString(),
                text("gui.bannermod.war_list.detail.defender", entityName(war.defenderPoliticalEntityId())).getString(),
                text("gui.bannermod.war_list.detail.state", war.state().name()).getString(),
                text("gui.bannermod.war_list.detail.goal", war.goalType().name()).getString(),
                text("gui.bannermod.war_list.detail.casus", war.casusBelli().isEmpty() ? text("gui.bannermod.common.none").getString() : war.casusBelli()).getString(),
                text("gui.bannermod.war_list.detail.declared", war.declaredAtGameTime()).getString(),
                text("gui.bannermod.war_list.detail.earliest", war.earliestActivationGameTime()).getString(),
                text("gui.bannermod.war_list.detail.attacker_allies", war.attackerAllyIds().size()).getString(),
                text("gui.bannermod.war_list.detail.defender_allies", war.defenderAllyIds().size()).getString(),
                text("gui.bannermod.war_list.detail.targets", war.targetPositions().size()).getString(),
                text("gui.bannermod.war_list.detail.occupations", occupationSummary(war.id())).getString(),
                text("gui.bannermod.war_list.detail.sieges", activeSiegeCount(war.id())).getString(),
                text("gui.bannermod.war_list.detail.revolts", revoltSummary(war.id())).getString(),
                text("gui.bannermod.war_list.detail.outcome_ui").getString(),
                text("gui.bannermod.war_list.detail.locked").getString(),
                text("gui.bannermod.war_list.detail.id", shortId(war.id())).getString()
        };
        for (String s : body) {
            graphics.drawString(font, font.plainSubstrByWidth(s, w), x, y + 14 + line * 11, 0xFFFFFF, false);
            line++;
        }
        for (RevoltRecord revolt : WarClientState.revoltsForWar(war.id())) {
            int color = revoltLineColor(revolt.state());
            graphics.drawString(font,
                    font.plainSubstrByWidth(revoltPressureLine(revolt), w),
                    x, y + 14 + line * 11, color, false);
            line++;
            if (line >= 18) {
                return;
            }
            graphics.drawString(font,
                    font.plainSubstrByWidth(revoltObjectiveLine(revolt), w),
                    x, y + 14 + line * 11, 0xFFAAAAAA, false);
            line++;
            if (line >= 18) {
                return;
            }
        }
        for (SiegeStandardRecord siege : WarClientState.sieges()) {
            if (!siege.warId().equals(war.id())) {
                continue;
            }
            String side = entityName(siege.sidePoliticalEntityId());
            String pos = siege.pos() == null ? text("gui.bannermod.common.unknown").getString() : siege.pos().toShortString();
            graphics.drawString(font, font.plainSubstrByWidth(text("gui.bannermod.war_list.standard", side, pos, siege.radius()).getString(), w),
                    x, y + 14 + line * 11, 0xFFAAFFAA, false);
            line++;
            if (line >= 18) {
                return;
            }
        }
        for (OccupationRecord occupation : WarClientState.occupationsForWar(war.id())) {
            String firstChunk = occupation.chunks().isEmpty()
                    ? text("gui.bannermod.war_list.chunk_unknown").getString()
                    : text("gui.bannermod.war_list.chunk", occupation.chunks().get(0).x, occupation.chunks().get(0).z).getString();
            String suffix = occupation.chunks().size() > 1 ? " +" + (occupation.chunks().size() - 1) : "";
            graphics.drawString(font,
                    font.plainSubstrByWidth(text("gui.bannermod.war_list.occupation", entityName(occupation.occupierEntityId()), firstChunk, suffix, occupation.lastTaxedAtGameTime()).getString(), w),
                    x, y + 14 + line * 11, 0xFFFFDD88, false);
            line++;
            if (line >= 18) {
                return;
            }
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(key, args);
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
            case PENDING -> 0xFFFFFF55;
            case SUCCESS -> 0xFFAAFFAA;
            case FAILED -> 0xFFFF8888;
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
