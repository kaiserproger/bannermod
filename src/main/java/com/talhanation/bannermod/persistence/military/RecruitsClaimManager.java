package com.talhanation.bannermod.persistence.military;

import com.talhanation.bannermod.events.ClaimEvent;
import net.minecraftforge.common.MinecraftForge;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateClaim;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateClaims;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
public class RecruitsClaimManager {
    private final Map<ChunkPos, RecruitsClaim> claims = new HashMap<>();

    /** Test-only direct seeding bypassing Forge events and persistence. */
    public void testInsertClaim(RecruitsClaim claim) {
        if (claim == null) return;
        for (ChunkPos pos : claim.getClaimedChunks()) {
            this.claims.put(pos, claim);
        }
    }

    public void load(ServerLevel level) {
        RecruitsClaimSaveData data = RecruitsClaimSaveData.get(level);
        this.claims.clear();
        for (RecruitsClaim claim : data.getAllClaims()) {
            for (ChunkPos pos : claim.getClaimedChunks()) {
                this.claims.put(pos, claim);
            }
        }
    }

    public void save(ServerLevel level) {
        RecruitsClaimSaveData data = RecruitsClaimSaveData.get(level);
        persistClaims(data);
    }

    void persistClaims(RecruitsClaimSaveData data) {
        data.setAllClaims(new ArrayList<>(new HashSet<>(this.claims.values())));
        data.setDirty();
    }

    public void addOrUpdateClaim(ServerLevel level, RecruitsClaim claim) {
        if (claim == null) return;

        // ClaimEvent.Updated feuern – cancelable
        boolean isNew = claims.values().stream().noneMatch(c -> c.getUUID().equals(claim.getUUID()));
        ClaimEvent.Updated updateEvent = new ClaimEvent.Updated(claim, level, isNew);
        if (MinecraftForge.EVENT_BUS.post(updateEvent)) return;

        claims.entrySet().removeIf(entry -> entry.getValue().getUUID().equals(claim.getUUID()));

        if(!claim.isRemoved){
            for (ChunkPos pos : claim.getClaimedChunks()) {
                this.claims.put(pos, claim);
            }
        }

        persistClaims(RecruitsClaimSaveData.get(level));
        this.broadcastClaimsToAll(level);
    }

    public void removeClaim(RecruitsClaim claim) {
        if (claim != null) {
            // ClaimEvent.Removed feuern
            ServerLevel level = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
            MinecraftForge.EVENT_BUS.post(new ClaimEvent.Removed(claim, level));

            claims.entrySet().removeIf(entry -> entry.getValue().equals(claim));
            persistClaims(RecruitsClaimSaveData.get(level));
        }
    }

    // -------------------------------------------------------------------------

    @Nullable
    public RecruitsClaim getClaim(ChunkPos chunkPos) {
        return this.claims.get(chunkPos);
    }

    @Nullable
    public RecruitsClaim getClaim(int chunkX, int chunkZ) {
        return this.getClaim(new ChunkPos(chunkX, chunkZ));
    }

    public List<RecruitsClaim> getAllClaims() {
        return new ArrayList<>(new HashSet<>(this.claims.values()));
    }

    public boolean isTownTooCloseToSameNationTown(RecruitsClaim candidate, @Nullable RecruitsClaim ignoredClaim, int minDistanceBlocks) {
        if (candidate == null || candidate.isRemoved || minDistanceBlocks <= 0) return false;
        UUID candidateOwner = candidate.getOwnerPoliticalEntityId();
        if (candidateOwner == null) return false;
        ChunkPos candidateCenter = resolveCenter(candidate);
        if (candidateCenter == null) return false;

        long minDistanceSqr = (long) minDistanceBlocks * (long) minDistanceBlocks;
        for (RecruitsClaim claim : getAllClaims()) {
            if (claim == null || claim.isRemoved) continue;
            if (ignoredClaim != null && claim.getUUID().equals(ignoredClaim.getUUID())) continue;
            if (!candidateOwner.equals(claim.getOwnerPoliticalEntityId())) continue;
            ChunkPos center = resolveCenter(claim);
            if (center == null) continue;

            long dx = (long) candidateCenter.getMiddleBlockX() - center.getMiddleBlockX();
            long dz = (long) candidateCenter.getMiddleBlockZ() - center.getMiddleBlockZ();
            if (dx * dx + dz * dz < minDistanceSqr) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ChunkPos resolveCenter(RecruitsClaim claim) {
        if (claim.getCenter() != null) return claim.getCenter();
        List<ChunkPos> chunks = claim.getClaimedChunks();
        return chunks.isEmpty() ? null : chunks.get(0);
    }

    public boolean claimExists(RecruitsClaim claim, List<ChunkPos> allPos) {
        for (ChunkPos pos : allPos) {
            if (claims.containsKey(pos)) {
                return true;
            }
        }
        return false;
    }

    public static RecruitsClaim getClaimAt(ChunkPos pos, List<RecruitsClaim> allClaims) {
        for (RecruitsClaim claim : allClaims) {
            if (claim.containsChunk(pos)) {
                return claim;
            }
        }
        return null;
    }

    public void broadcastClaimsToAll(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            BannerModMain.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new MessageToClientUpdateClaims(
                            this.getAllClaims(),
                            RecruitsServerConfig.ClaimingCost.get(),
                            RecruitsServerConfig.ChunkCost.get(),
                            RecruitsServerConfig.CascadeThePriceOfClaims.get(),
                            RecruitsServerConfig.AllowClaiming.get(),
                            RecruitsServerConfig.FogOfWarEnabled.get(),
                            getRecruitCurrency()
                    ));
        }
    }

    private static ItemStack getRecruitCurrency() {
        String currencyId = RecruitsServerConfig.RecruitCurrency.get();
        return ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(currencyId))
                .map(Holder::value)
                .map(Item::getDefaultInstance)
                .orElseGet(Items.EMERALD::getDefaultInstance);
    }

    public void broadcastClaimUpdateTo(RecruitsClaim claim, List<ServerPlayer> players) {
        if (claim == null || players == null || players.isEmpty()) return;

        for (ServerPlayer player : players) {
            BannerModMain.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new MessageToClientUpdateClaim(claim));
        }
    }
}
