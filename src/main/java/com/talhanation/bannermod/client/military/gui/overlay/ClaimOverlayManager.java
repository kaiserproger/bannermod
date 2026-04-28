package com.talhanation.bannermod.client.military.gui.overlay;

import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.client.military.api.ClientClaimEvent;
import com.talhanation.bannermod.client.military.api.ClientOverlayEvent;
import com.talhanation.bannermod.config.RecruitsClientConfig;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.war.client.WarClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClaimOverlayManager {
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

    private static final long FADE_DURATION = 500;
    private static final long FULL_DISPLAY_DURATION = 5000;
    private static final int DATA_UPDATE_INTERVAL = 20;
    private static final int CHUNK_CHECK_INTERVAL = 10;
    private static final int PANEL_WIDTH = 150;

    private final ClaimOverlayRenderer renderer = new ClaimOverlayRenderer();

    public enum OverlayState {
        HIDDEN,
        FULL,
        COMPACT
    }

    public ClaimOverlayManager() {

    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        tickCounter++;

        if (tickCounter % CHUNK_CHECK_INTERVAL == 0) {
            updateCurrentClaim(mc.player.blockPosition());
        }

        boolean needsUpdate = tickCounter % DATA_UPDATE_INTERVAL == 0;

        if (needsUpdate && ClientManager.currentClaim != null) {
            checkForDataChanges();
        }

        updateOverlayState();
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.gui.getTabList().visible) return;

        if (!mc.gui.getBossOverlay().events.isEmpty()) {
            return;
        }

        if (mc.level != null && mc.level.dimension() != Level.OVERWORLD)return;

        if(RecruitsClientConfig.DisableClaimGUIOverlay.get()) return;

        float alpha = calculateAlpha();
        if (alpha <= 0.01f) return;

        if (ClientManager.currentClaim != null) {
            boolean cancelled = MinecraftForge.EVENT_BUS.post(new ClientOverlayEvent.RenderPre(event.getGuiGraphics(), ClientManager.currentClaim, currentState, alpha));
            if (!cancelled) {
                boolean underSiege = WarClientState.isClaimUnderSiege(ClientManager.currentClaim);
                boolean occupied = WarClientState.isClaimChunkOccupied(ClientManager.currentClaim, lastPlayerChunk);
                renderer.render(event.getGuiGraphics(), mc, ClientManager.currentClaim, currentState, alpha, getPanelWidth(), underSiege, occupied);

                MinecraftForge.EVENT_BUS.post(new ClientOverlayEvent.RenderPost(event.getGuiGraphics(), ClientManager.currentClaim, currentState, alpha));
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
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
            MinecraftForge.EVENT_BUS.post(new ClientClaimEvent.Enter(newClaim, null));
            claimEntryTime = System.currentTimeMillis();
            transitionToState(OverlayState.FULL, true);
            updateCachedData(newClaim);
        }
        else if (previousClaim != null && newClaim == null) {
            MinecraftForge.EVENT_BUS.post(new ClientClaimEvent.Leave(previousClaim, null));
            transitionToState(OverlayState.HIDDEN, true);
        }
        else if (previousClaim != null && newClaim != null && !previousClaim.equals(newClaim)) {
            MinecraftForge.EVENT_BUS.post(new ClientClaimEvent.Leave(previousClaim, newClaim));
            MinecraftForge.EVENT_BUS.post(new ClientClaimEvent.Enter(newClaim, previousClaim));
            claimEntryTime = System.currentTimeMillis();
            transitionToState(OverlayState.FULL, true);
            updateCachedData(newClaim);
        }
    }

    private void checkForDataChanges() {
        if (ClientManager.currentClaim == null) return;

        RecruitsClaim claim = ClientManager.currentClaim;
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

        boolean underSiege = WarClientState.isClaimUnderSiege(claim);
        boolean occupied = WarClientState.isClaimChunkOccupied(claim, lastPlayerChunk);
        if (underSiege != lastKnownSiegeState || occupied != lastKnownOccupationState || lastWarStateVersion != WarClientState.version()) {
            lastKnownSiegeState = underSiege;
            lastKnownOccupationState = occupied;
            lastWarStateVersion = WarClientState.version();
            hasChanges = true;
        }

        if (hasChanges) {
            renderer.markDataChanged();
        }
    }

    private void updateCachedData(RecruitsClaim claim) {
        if (claim == null) {
            lastKnownClaimName = "";
            lastKnownFactionName = "";
            lastKnownSiegeState = false;
            lastKnownOccupationState = false;
            return;
        }

        lastKnownClaimName = claim.getName();
        lastKnownFactionName = (claim.getOwnerPoliticalEntityId() == null ? "" : claim.getOwnerPoliticalEntityId().toString());
        lastKnownSiegeState = WarClientState.isClaimUnderSiege(claim);
        lastKnownOccupationState = WarClientState.isClaimChunkOccupied(claim, lastPlayerChunk);
    }

    private void updateOverlayState() {
        if (ClientManager.currentClaim == null) {
            return;
        }

        if (WarClientState.isClaimUnderSiege(ClientManager.currentClaim)) {
            if (currentState != OverlayState.FULL) {
                transitionToState(OverlayState.FULL, true);
            }
            return;
        }

        if (WarClientState.isClaimChunkOccupied(ClientManager.currentClaim, lastPlayerChunk)) {
            if (currentState != OverlayState.FULL) {
                transitionToState(OverlayState.FULL, true);
            }
            return;
        }

        long timeInClaim = System.currentTimeMillis() - claimEntryTime;
        OverlayState desiredState = (timeInClaim < FULL_DISPLAY_DURATION) ?
                OverlayState.FULL : OverlayState.COMPACT;

        if (currentState != desiredState) {
            transitionToState(desiredState, true);
        }
    }

    private void transitionToState(OverlayState newState, boolean fade) {
        if (currentState == newState) return;

        boolean cancelled = MinecraftForge.EVENT_BUS.post(new ClientOverlayEvent.StateChanged(ClientManager.currentClaim, currentState, newState, calculateAlpha()));
        if (cancelled) return;

        if (fade) {
            stateChangeTime = System.currentTimeMillis();
        } else {
            stateChangeTime = System.currentTimeMillis() - FADE_DURATION;
        }

        currentState = newState;
    }

    private float calculateAlpha() {
        if (currentState == OverlayState.HIDDEN) return 0f;

        long elapsed = System.currentTimeMillis() - stateChangeTime;

        if (elapsed >= FADE_DURATION) {
            return 1.0f;
        }

        float progress = (float) elapsed / FADE_DURATION;

        if (currentState == OverlayState.HIDDEN) {
            return 1.0f - progress;
        } else {
            return progress;
        }
    }

    public int getPanelWidth() {
        return PANEL_WIDTH;
    }

    public OverlayState getCurrentState() {
        return currentState;
    }

    private void reset() {
        ClientManager.recruitsClaims.clear();
        ClientManager.markClaimsChanged();
        ClientManager.currentClaim = null;
        currentState = OverlayState.HIDDEN;
        lastPlayerChunk = null;
        lastClaimsVersion = -1;
        tickCounter = 0;
        updateCachedData(null);
        renderer.clearCache();
    }
}
