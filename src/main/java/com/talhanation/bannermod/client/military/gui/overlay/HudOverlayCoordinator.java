package com.talhanation.bannermod.client.military.gui.overlay;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.civilian.input.AssignHomeTargetSelector;
import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.client.military.api.ClientClaimEvent;
import com.talhanation.bannermod.client.military.api.ClientOverlayEvent;
import com.talhanation.bannermod.config.RecruitsClientConfig;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.config.WarServerConfig;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.runtime.BattleWindowClock;
import com.talhanation.bannermod.war.runtime.BattleWindowSchedule;
import com.talhanation.bannermod.war.runtime.SiegeStandardRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Team;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HudOverlayCoordinator {
    public static final HudOverlayCoordinator INSTANCE = new HudOverlayCoordinator();

    private static final ResourceLocation HUD_LAYER = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "hud_overlay_coordinator");
    private static final long FADE_DURATION = 500;
    private static final long FULL_DISPLAY_DURATION = 5_000;
    private static final int DATA_UPDATE_INTERVAL = 20;
    private static final int CHUNK_CHECK_INTERVAL = 10;
    private static final int CLAIM_PANEL_WIDTH = 150;
    private static final int PANEL_GAP = 4;
    private static final int TOP_SAFE_MARGIN = 28;
    private static final int RIGHT_SAFE_MARGIN = 6;
    private static final int CHIP_PADDING_X = 6;
    private static final int CHIP_PADDING_Y = 4;
    private static final long SCHEDULE_CACHE_MS = 5_000L;

    private OverlayState currentState = OverlayState.HIDDEN;
    private long stateChangeTime = 0;
    private long claimEntryTime = 0;
    private int tickCounter = 0;
    private ChunkPos lastPlayerChunk = null;
    private int lastClaimsVersion = -1;
    private int lastWarStateVersion = -1;
    private boolean lastKnownSiegeState = false;
    private boolean lastKnownOccupationState = false;
    private String lastKnownClaimName = "";
    private String lastKnownFactionName = "";
    private long cachedScheduleAtMs = 0L;
    private List<String> cachedScheduleRaw;
    private BattleWindowSchedule cachedSchedule;

    private final ClaimOverlayRenderer claimRenderer = new ClaimOverlayRenderer();

    public enum OverlayState {
        HIDDEN,
        FULL,
        COMPACT
    }

    private HudOverlayCoordinator() {
    }

    public static void registerOverlays(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, HUD_LAYER, HudOverlayCoordinator::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        INSTANCE.renderOverlays(graphics);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % CHUNK_CHECK_INTERVAL == 0) {
            updateCurrentClaim(mc.player.blockPosition());
        }
        if (tickCounter % DATA_UPDATE_INTERVAL == 0 && ClientManager.currentClaim != null) {
            checkForDataChanges();
        }
        updateOverlayState();
    }

    @SubscribeEvent
    public void onWorldUnload(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    private void renderOverlays(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.options.keyPlayerList.isDown()) return;

        int y = AssignHomeTargetSelector.renderPrompt(graphics, mc, TOP_SAFE_MARGIN);
        y = renderBattleWindow(graphics, mc, y);
        y = renderSiegeZone(graphics, mc, y);
        renderClaim(graphics, mc, y);
    }

    private int renderBattleWindow(GuiGraphics graphics, Minecraft mc, int y) {
        if (!regulatedPvpEnabled()) return y;
        BattleWindowSchedule schedule = schedule();
        if (schedule == null) return y;

        BattleWindowClock.Phase phase = BattleWindowClock.compute(schedule, ZonedDateTime.now(ZoneId.systemDefault()));
        Component label = battleWindowLabel(phase);
        int color = battleWindowColor(phase);
        return renderRightChip(graphics, mc.font, mc.getWindow().getGuiScaledWidth(), y, label, color, 0x80000000);
    }

    private int renderSiegeZone(GuiGraphics graphics, Minecraft mc, int y) {
        if (!WarClientState.hasSnapshot()) return y;
        LocalPlayer player = mc.player;
        SiegeContext context = nearestActiveSiege(player);
        if (context == null) return y;

        Component headline = Component.translatable("gui.bannermod.hud.siege_zone", context.warName);
        Component subline = Component.translatable("gui.bannermod.hud.siege_zone.details", context.sideName, context.siege.radius(), localizedWarState(context.warState));
        Font font = mc.font;
        int width = Math.max(font.width(headline), font.width(subline)) + 12;
        int height = 24;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = Math.max(6, screenWidth - RIGHT_SAFE_MARGIN - width);

        graphics.fill(x, y, x + width, y + height, 0xC0101010);
        graphics.renderOutline(x, y, width, height, 0xFFFFFFFF);
        graphics.drawString(font, headline, x + 6, y + 4, 0xFFFFAA55, false);
        graphics.drawString(font, subline, x + 6, y + 14, 0xFFCCCCCC, false);
        return y + height + PANEL_GAP;
    }

    private void renderClaim(GuiGraphics graphics, Minecraft mc, int y) {
        if (mc.level.dimension() != Level.OVERWORLD) return;
        if (RecruitsClientConfig.DisableClaimGUIOverlay.get()) return;

        float alpha = calculateAlpha();
        if (alpha <= 0.01f) return;

        ClientOverlayEvent.RenderPre renderPre = new ClientOverlayEvent.RenderPre(graphics, ClientManager.currentClaim, currentState, alpha);
        NeoForge.EVENT_BUS.post(renderPre);
        if (renderPre.isCanceled()) return;

        PoliticalEntityRecord ownerEntity = currentOwnerEntity(ClientManager.currentClaim);
        boolean underSiege = WarClientState.hasSnapshot() && WarClientState.isClaimUnderSiege(ClientManager.currentClaim);
        boolean occupied = WarClientState.hasSnapshot() && WarClientState.isClaimChunkOccupied(ClientManager.currentClaim, lastPlayerChunk);
        ClaimAuthorityStatus authorityStatus = ClaimAuthorityStatus.classify(
                mc.player.getUUID(),
                playerTeamName(mc.player),
                ClientManager.currentClaim,
                ownerEntity);
        int x = Math.max(6, mc.getWindow().getGuiScaledWidth() - RIGHT_SAFE_MARGIN - CLAIM_PANEL_WIDTH);
        claimRenderer.render(graphics, mc, ClientManager.currentClaim, currentState, authorityStatus, ownerEntity, alpha, CLAIM_PANEL_WIDTH, x, y, underSiege, occupied);
        NeoForge.EVENT_BUS.post(new ClientOverlayEvent.RenderPost(graphics, ClientManager.currentClaim, currentState, alpha));
    }

    @Nullable
    private static String playerTeamName(Player player) {
        Team team = player.getTeam();
        return team == null ? null : team.getName();
    }

    private static int renderRightChip(GuiGraphics graphics, Font font, int screenWidth, int y, Component label, int textColor, int bgColor) {
        int textWidth = font.width(label);
        int boxWidth = textWidth + CHIP_PADDING_X * 2;
        int boxHeight = font.lineHeight + CHIP_PADDING_Y * 2;
        int x = Math.max(6, screenWidth - RIGHT_SAFE_MARGIN - boxWidth);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, bgColor);
        graphics.drawString(font, label, x + CHIP_PADDING_X, y + CHIP_PADDING_Y, textColor, true);
        return y + boxHeight + PANEL_GAP;
    }

    private void updateCurrentClaim(BlockPos playerPos) {
        ChunkPos currentChunk = new ChunkPos(playerPos);
        if (currentChunk.equals(lastPlayerChunk) && lastClaimsVersion == ClientManager.recruitsClaimsVersion) {
            return;
        }

        lastPlayerChunk = currentChunk;
        lastClaimsVersion = ClientManager.recruitsClaimsVersion;
        RecruitsClaim previousClaim = ClientManager.currentClaim;

        if (ClientManager.recruitsClaimsByChunk.isEmpty()) {
            RuntimeProfilingCounters.increment("claim_overlay.claim_scans");
            RuntimeProfilingCounters.add("claim_overlay.claims_seen", ClientManager.recruitsClaims.size());
        }
        ClientManager.currentClaim = ClientManager.getClaimAtChunk(currentChunk);
        handleClaimTransition(previousClaim, ClientManager.currentClaim);
    }

    private void handleClaimTransition(RecruitsClaim previousClaim, RecruitsClaim newClaim) {
        if (previousClaim == null && newClaim != null) {
            NeoForge.EVENT_BUS.post(new ClientClaimEvent.Enter(newClaim, null));
            claimEntryTime = System.currentTimeMillis();
            transitionToState(OverlayState.FULL, true);
            updateCachedData(newClaim);
            displayClaimBoundaryFeedback(newClaim);
        } else if (previousClaim != null && newClaim == null) {
            NeoForge.EVENT_BUS.post(new ClientClaimEvent.Leave(previousClaim, null));
            claimEntryTime = System.currentTimeMillis();
            transitionToState(OverlayState.FULL, true);
            updateCachedData(null);
            displayClaimBoundaryFeedback(null);
        } else if (previousClaim != null && newClaim != null && !previousClaim.equals(newClaim)) {
            NeoForge.EVENT_BUS.post(new ClientClaimEvent.Leave(previousClaim, newClaim));
            NeoForge.EVENT_BUS.post(new ClientClaimEvent.Enter(newClaim, previousClaim));
            claimEntryTime = System.currentTimeMillis();
            transitionToState(OverlayState.FULL, true);
            updateCachedData(newClaim);
            displayClaimBoundaryFeedback(newClaim);
        }
    }

    @Nullable
    private static PoliticalEntityRecord currentOwnerEntity(@Nullable RecruitsClaim claim) {
        if (claim == null || claim.getOwnerPoliticalEntityId() == null || !WarClientState.hasSnapshot()) {
            return null;
        }
        return WarClientState.entityById(claim.getOwnerPoliticalEntityId());
    }

    private void displayClaimBoundaryFeedback(@Nullable RecruitsClaim claim) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        PoliticalEntityRecord ownerEntity = currentOwnerEntity(claim);
        ClaimAuthorityStatus authorityStatus = ClaimAuthorityStatus.classify(player.getUUID(), playerTeamName(player), claim, ownerEntity);
        String wildernessLabel = Component.translatable("gui.bannermod.claim_overlay.unclaimed").getString();
        String territoryName = ClaimTerritoryText.territoryName(claim, ownerEntity, wildernessLabel);
        Component message = claim == null
                ? Component.translatable(authorityStatus.boundaryMessageKey())
                : Component.translatable(authorityStatus.boundaryMessageKey(), territoryName);
        player.displayClientMessage(message, true);
    }

    private void checkForDataChanges() {
        RecruitsClaim claim = ClientManager.currentClaim;
        if (claim == null) return;

        boolean hasChanges = false;
        if (!claim.getName().equals(lastKnownClaimName)) {
            lastKnownClaimName = claim.getName();
            hasChanges = true;
        }

        String currentOwnerKey = claim.getOwnerPoliticalEntityId() == null ? "" : claim.getOwnerPoliticalEntityId().toString();
        if (!currentOwnerKey.equals(lastKnownFactionName)) {
            lastKnownFactionName = currentOwnerKey;
            hasChanges = true;
        }

        boolean underSiege = WarClientState.hasSnapshot() && WarClientState.isClaimUnderSiege(claim);
        boolean occupied = WarClientState.hasSnapshot() && WarClientState.isClaimChunkOccupied(claim, lastPlayerChunk);
        if (underSiege != lastKnownSiegeState || occupied != lastKnownOccupationState || lastWarStateVersion != WarClientState.version()) {
            lastKnownSiegeState = underSiege;
            lastKnownOccupationState = occupied;
            lastWarStateVersion = WarClientState.version();
            hasChanges = true;
        }

        if (hasChanges) {
            claimRenderer.markDataChanged();
        }
    }

    private void updateCachedData(@Nullable RecruitsClaim claim) {
        if (claim == null) {
            lastKnownClaimName = "";
            lastKnownFactionName = "";
            lastKnownSiegeState = false;
            lastKnownOccupationState = false;
            return;
        }

        lastKnownClaimName = claim.getName();
        lastKnownFactionName = claim.getOwnerPoliticalEntityId() == null ? "" : claim.getOwnerPoliticalEntityId().toString();
        lastKnownSiegeState = WarClientState.hasSnapshot() && WarClientState.isClaimUnderSiege(claim);
        lastKnownOccupationState = WarClientState.hasSnapshot() && WarClientState.isClaimChunkOccupied(claim, lastPlayerChunk);
    }

    private void updateOverlayState() {
        if (ClientManager.currentClaim == null) {
            long timeInUnclaimed = System.currentTimeMillis() - claimEntryTime;
            OverlayState desiredState = timeInUnclaimed < FULL_DISPLAY_DURATION ? OverlayState.FULL : OverlayState.COMPACT;
            if (currentState != desiredState) {
                transitionToState(desiredState, true);
            }
            return;
        }

        if (WarClientState.hasSnapshot() && WarClientState.isClaimUnderSiege(ClientManager.currentClaim)) {
            if (currentState != OverlayState.FULL) transitionToState(OverlayState.FULL, true);
            return;
        }

        if (WarClientState.hasSnapshot() && WarClientState.isClaimChunkOccupied(ClientManager.currentClaim, lastPlayerChunk)) {
            if (currentState != OverlayState.FULL) transitionToState(OverlayState.FULL, true);
            return;
        }

        long timeInClaim = System.currentTimeMillis() - claimEntryTime;
        OverlayState desiredState = timeInClaim < FULL_DISPLAY_DURATION ? OverlayState.FULL : OverlayState.COMPACT;
        if (currentState != desiredState) {
            transitionToState(desiredState, true);
        }
    }

    private void transitionToState(OverlayState newState, boolean fade) {
        if (currentState == newState) return;

        ClientOverlayEvent.StateChanged stateChanged = new ClientOverlayEvent.StateChanged(ClientManager.currentClaim, currentState, newState, calculateAlpha());
        NeoForge.EVENT_BUS.post(stateChanged);
        if (stateChanged.isCanceled()) return;

        stateChangeTime = fade ? System.currentTimeMillis() : System.currentTimeMillis() - FADE_DURATION;
        currentState = newState;
    }

    private float calculateAlpha() {
        if (currentState == OverlayState.HIDDEN) return 0f;
        long elapsed = System.currentTimeMillis() - stateChangeTime;
        if (elapsed >= FADE_DURATION) return 1.0f;
        return (float) elapsed / FADE_DURATION;
    }

    private static Component battleWindowLabel(BattleWindowClock.Phase phase) {
        if (phase instanceof BattleWindowClock.Phase.Open open) {
            return Component.translatable("gui.bannermod.hud.battle_window.open", formatDuration(open.untilClose()));
        }
        BattleWindowClock.Phase.Closed closed = (BattleWindowClock.Phase.Closed) phase;
        if (closed.nextWindow() == null) {
            return Component.translatable("gui.bannermod.hud.battle_window.none");
        }
        Component day = localizedDay(closed.nextWindow().dayOfWeek());
        String time = closed.nextWindow().startsAt().toString();
        return Component.translatable("gui.bannermod.hud.battle_window.next", day, time, formatDuration(closed.untilOpen()));
    }

    private static int battleWindowColor(BattleWindowClock.Phase phase) {
        if (phase instanceof BattleWindowClock.Phase.Open) return 0xFF55FF55;
        Duration until = phase.timeUntilTransition();
        return until.toMinutes() < 5 ? 0xFFFFFF55 : 0xFFAAAAAA;
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long secs = seconds % 60L;
        if (days > 0) return String.format(Locale.ROOT, "%dd %dh", days, hours);
        if (hours > 0) return String.format(Locale.ROOT, "%dh %dm", hours, minutes);
        if (minutes > 0) return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
        return String.format(Locale.ROOT, "0:%02d", secs);
    }

    private static boolean regulatedPvpEnabled() {
        try {
            return WarServerConfig.RegulatedPvpEnabled.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nullable
    private BattleWindowSchedule schedule() {
        long now = System.currentTimeMillis();
        if (cachedSchedule != null && now - cachedScheduleAtMs < SCHEDULE_CACHE_MS) return cachedSchedule;

        List<String> raw;
        try {
            raw = WarServerConfig.BattleWindows.get();
        } catch (Exception ignored) {
            return null;
        }
        if (cachedSchedule != null && cachedScheduleRaw != null && cachedScheduleRaw.equals(raw)) {
            cachedScheduleAtMs = now;
            return cachedSchedule;
        }

        try {
            cachedSchedule = WarServerConfig.resolveSchedule();
        } catch (Exception ignored) {
            return null;
        }
        cachedScheduleRaw = List.copyOf(raw);
        cachedScheduleAtMs = now;
        return cachedSchedule;
    }

    @Nullable
    private static SiegeContext nearestActiveSiege(Player player) {
        SiegeStandardRecord best = null;
        double bestSqr = Double.MAX_VALUE;
        WarDeclarationRecord bestWar = null;
        for (SiegeStandardRecord siege : WarClientState.sieges()) {
            if (siege.pos() == null) continue;
            WarDeclarationRecord war = warById(siege.warId());
            if (war == null || !isActiveState(war.state())) continue;
            double dx = player.getX() - (siege.pos().getX() + 0.5);
            double dz = player.getZ() - (siege.pos().getZ() + 0.5);
            double sqr = dx * dx + dz * dz;
            double radiusSqr = (double) siege.radius() * (double) siege.radius();
            if (sqr > radiusSqr) continue;
            if (sqr < bestSqr) {
                best = siege;
                bestSqr = sqr;
                bestWar = war;
            }
        }
        if (best == null || bestWar == null) return null;
        return new SiegeContext(best, bestWar.state(), warName(bestWar), entityName(best.sidePoliticalEntityId()));
    }

    private static String warName(WarDeclarationRecord war) {
        return Component.translatable("gui.bannermod.hud.war_vs",
                entityName(war.attackerPoliticalEntityId()),
                entityName(war.defenderPoliticalEntityId())).getString();
    }

    @Nullable
    private static WarDeclarationRecord warById(UUID warId) {
        if (warId == null) return null;
        for (WarDeclarationRecord war : WarClientState.wars()) {
            if (warId.equals(war.id())) return war;
        }
        return null;
    }

    private static boolean isActiveState(WarState state) {
        return state == WarState.DECLARED || state == WarState.ACTIVE || state == WarState.IN_SIEGE_WINDOW;
    }

    private static String entityName(UUID id) {
        if (id == null) return Component.translatable("gui.bannermod.common.unknown").getString();
        PoliticalEntityRecord entity = WarClientState.entityById(id);
        if (entity == null) {
            return Component.translatable("gui.bannermod.common.unknown").getString();
        }
        if (entity.name().isBlank()) {
            return Component.translatable("gui.bannermod.states.unnamed").getString();
        }
        return entity.name();
    }

    private static Component localizedWarState(WarState state) {
        return switch (state) {
            case DECLARED -> Component.translatable("gui.bannermod.war_list.state.declared");
            case ACTIVE -> Component.translatable("gui.bannermod.war_list.state.active");
            case IN_SIEGE_WINDOW -> Component.translatable("gui.bannermod.war_list.state.in_siege_window");
            case RESOLVED -> Component.translatable("gui.bannermod.war_list.state.resolved");
            case CANCELLED -> Component.translatable("gui.bannermod.war_list.state.cancelled");
        };
    }

    private static Component localizedDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> Component.translatable("gui.bannermod.day.mon");
            case TUESDAY -> Component.translatable("gui.bannermod.day.tue");
            case WEDNESDAY -> Component.translatable("gui.bannermod.day.wed");
            case THURSDAY -> Component.translatable("gui.bannermod.day.thu");
            case FRIDAY -> Component.translatable("gui.bannermod.day.fri");
            case SATURDAY -> Component.translatable("gui.bannermod.day.sat");
            case SUNDAY -> Component.translatable("gui.bannermod.day.sun");
        };
    }

    private void reset() {
        ClientManager.recruitsClaims.clear();
        ClientManager.markClaimsChanged();
        ClientManager.currentClaim = null;
        currentState = OverlayState.HIDDEN;
        lastPlayerChunk = null;
        lastClaimsVersion = -1;
        lastWarStateVersion = -1;
        tickCounter = 0;
        cachedScheduleAtMs = 0L;
        cachedScheduleRaw = null;
        cachedSchedule = null;
        updateCachedData(null);
        claimRenderer.clearCache();
    }

    private record SiegeContext(SiegeStandardRecord siege,
                                WarState warState,
                                String warName,
                                String sideName) {
    }
}
