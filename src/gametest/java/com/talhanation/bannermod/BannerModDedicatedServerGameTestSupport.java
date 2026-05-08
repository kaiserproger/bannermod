package com.talhanation.bannermod;

import com.mojang.authlib.GameProfile;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingCategory;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BannerModDedicatedServerGameTestSupport {

    private BannerModDedicatedServerGameTestSupport() {
    }

    public static Player createFakeServerPlayer(ServerLevel level, UUID playerId, String name) {
        Player existingPlayer = level.getPlayerByUUID(playerId);
        if (existingPlayer != null) {
            return existingPlayer;
        }

        FakePlayer player = new FakePlayer(level, new GameProfile(playerId, name));
        level.addFreshEntity(player);
        return player;
    }

    public static Player createPositionedFakeServerPlayer(ServerLevel level, UUID playerId, String name, BlockPos pos) {
        Player player = createFakeServerPlayer(level, playerId, name);
        Vec3 spawnPos = Vec3.atBottomCenterOf(pos);
        player.moveTo(spawnPos.x(), spawnPos.y(), spawnPos.z(), 0.0F, 0.0F);
        return player;
    }

    public static PlayerTeam joinTeam(ServerLevel level, String teamName, Entity... members) {
        PlayerTeam team = level.getScoreboard().getPlayerTeam(teamName);
        if (team == null) {
            team = level.getScoreboard().addPlayerTeam(teamName);
        }

        for (Entity member : members) {
            if (member instanceof Player player) {
                level.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
                continue;
            }
            level.getScoreboard().addPlayerToTeam(member.getStringUUID(), team);
        }

        return team;
    }

    public static UUID ensureFaction(ServerLevel level, String factionId, UUID leaderId, String leaderName) {
        return WarRuntimeContext.registry(level)
                .byName(factionId)
                .or(() -> WarRuntimeContext.registry(level).create(
                        factionId,
                        leaderId,
                        BlockPos.ZERO,
                        "white",
                        "",
                        "",
                        "",
                        level.getGameTime()))
                .map(PoliticalEntityRecord::id)
                .orElseThrow(() -> new IllegalStateException("Could not create political entity " + factionId));
    }

    public static RecruitsClaim seedClaim(ServerLevel level, BlockPos pos, String factionId, UUID leaderId, String leaderName) {
        ensureClaimManager(level);
        UUID politicalEntityId = ensureFaction(level, factionId, leaderId, leaderName);
        ChunkPos chunkPos = new ChunkPos(pos);
        RecruitsClaim claim = ClaimEvents.claimManager().getClaim(chunkPos);
        if (claim == null) {
            claim = new RecruitsClaim(factionId, politicalEntityId);
            claim.setCenter(chunkPos);
        }
        claim.isRemoved = false;
        claim.setName(factionId);
        claim.setOwnerPoliticalEntityId(politicalEntityId);
        claim.setPlayer(new RecruitsPlayerInfo(leaderId, leaderName));
        claim.setBlockInteractionAllowed(false);
        claim.setBlockPlacementAllowed(false);
        claim.setBlockBreakingAllowed(false);
        claim.addChunk(chunkPos);
        ClaimEvents.claimManager().addOrUpdateClaim(level, claim);
        return claim;
    }

    public static void removeClaim(ServerLevel level, RecruitsClaim claim) {
        ensureClaimManager(level);
        ClaimEvents.claimManager().removeClaim(claim);
    }

    /**
     * Seed a synthetic housing snapshot for {@code claim} so the
     * worker-spawn rules see free {@code residentCapacity} during tests.
     * Without this, every spawn rule that runs through
     * {@code WorkerSettlementClaimPolicy.housingSlackForClaim} denies with
     * {@code NO_FREE_HOUSING}, because dedicated GameTest harnesses do not
     * stand up real prefab housing buildings.
     */
    public static BannerModSettlementSnapshot seedHousingSnapshot(ServerLevel level,
                                                                  RecruitsClaim claim,
                                                                  int residentCapacity) {
        BannerModSettlementManager manager = BannerModSettlementManager.get(level);
        BannerModSettlementSnapshot existing = manager.getSnapshot(claim.getUUID());
        BannerModSettlementBuildingRecord housing = new BannerModSettlementBuildingRecord(
                UUID.randomUUID(),
                "bannermod_test:housing",
                claim.getCenter().getWorldPosition(),
                claim.getPlayerInfo() == null ? null : claim.getPlayerInfo().getUUID(),
                claim.getName(),
                Math.max(1, residentCapacity),
                0,
                0,
                List.of(),
                false,
                0,
                0,
                false,
                false,
                List.of(),
                BannerModSettlementBuildingCategory.GENERAL,
                BannerModSettlementBuildingProfileSeed.GENERAL
        );
        List<BannerModSettlementBuildingRecord> buildings = existing == null
                ? new ArrayList<>()
                : new ArrayList<>(existing.buildings());
        buildings.add(housing);
        BannerModSettlementSnapshot snapshot = existing == null
                ? new BannerModSettlementSnapshot(
                        claim.getUUID(),
                        claim.getCenter().x,
                        claim.getCenter().z,
                        claim.getOwnerPoliticalEntityId() == null ? null : claim.getOwnerPoliticalEntityId().toString(),
                        level.getGameTime(),
                        Math.max(1, residentCapacity),
                        0, 0, 0, 0, 0,
                        com.talhanation.bannermod.settlement.BannerModSettlementStockpileSummary.empty(),
                        com.talhanation.bannermod.settlement.BannerModSettlementMarketState.empty(),
                        com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodsSnapshot.empty(),
                        com.talhanation.bannermod.settlement.BannerModSettlementProjectCandidateSnapshot.empty(),
                        com.talhanation.bannermod.settlement.BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                        com.talhanation.bannermod.settlement.BannerModSettlementSupplySignalState.empty(),
                        List.of(),
                        buildings)
                : new BannerModSettlementSnapshot(
                        existing.claimUuid(),
                        existing.anchorChunkX(),
                        existing.anchorChunkZ(),
                        existing.settlementFactionId(),
                        existing.lastRefreshedTick(),
                        existing.residentCapacity() + Math.max(1, residentCapacity),
                        existing.workplaceCapacity(),
                        existing.assignedWorkerCount(),
                        existing.assignedResidentCount(),
                        existing.unassignedWorkerCount(),
                        existing.missingWorkAreaAssignmentCount(),
                        existing.stockpileSummary(),
                        existing.marketState(),
                        existing.desiredGoodsSnapshot(),
                        existing.projectCandidateSnapshot(),
                        existing.tradeRouteHandoffSnapshot(),
                        existing.supplySignalState(),
                        existing.residents(),
                        buildings);
        manager.putSnapshot(snapshot);
        return snapshot;
    }

    /**
     * Seeds a friendly claim for the given leader and ensures the leader is both factioned in and team-joined.
     * Additive helper shared by Phase 23 governor GameTests and later logistics/treasury phases so each test
     * does not duplicate the fake-player/faction/team/claim bring-up sequence.
     */
    public static RecruitsClaim seedFriendlyLeaderClaim(ServerLevel level, Player leader, BlockPos claimPos, String factionId) {
        String leaderName = leader.getName().getString();
        ensureFaction(level, factionId, leader.getUUID(), leaderName);
        joinTeam(level, factionId, leader);
        return seedClaim(level, claimPos, factionId, leader.getUUID(), leaderName);
    }

    public static RecruitsClaim swapClaimFaction(ServerLevel level, RecruitsClaim claim, String factionId, UUID leaderId, String leaderName) {
        ensureClaimManager(level);
        UUID politicalEntityId = ensureFaction(level, factionId, leaderId, leaderName);
        claim.isRemoved = false;
        claim.setName(factionId);
        claim.setOwnerPoliticalEntityId(politicalEntityId);
        claim.setPlayer(new RecruitsPlayerInfo(leaderId, leaderName));
        ClaimEvents.claimManager().addOrUpdateClaim(level, claim);
        return claim;
    }

    public static String politicalEntityIdString(ServerLevel level, String factionId) {
        return WarRuntimeContext.registry(level)
                .byName(factionId)
                .map(PoliticalEntityRecord::id)
                .map(UUID::toString)
                .orElse(factionId);
    }

    public static ItemStack recruitCurrencyStack() {
        ResourceLocation id = ResourceLocation.tryParse(RecruitsServerConfig.RecruitCurrency.get());
        Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        return item.map(Item::getDefaultInstance).orElseGet(Items.EMERALD::getDefaultInstance);
    }

    public static void assignDetachedOwnership(AbstractRecruitEntity recruit, UUID ownerId) {
        recruit.setOwnerUUID(Optional.of(ownerId));
        recruit.setIsOwned(true);
    }

    public static void assignRecruitToLeader(ServerLevel level, AbstractRecruitEntity recruit, Player leader, String teamId) {
        assignDetachedOwnership(recruit, leader.getUUID());
        recruit.setFollowState(1);
        joinTeam(level, teamId, leader, recruit);
    }

    public static void assignDetachedOwnership(AbstractWorkerEntity worker, UUID ownerId) {
        worker.setOwnerUUID(Optional.of(ownerId));
        worker.setIsOwned(true);
    }

    public static void assignDetachedOwnership(AbstractWorkAreaEntity workArea, UUID ownerId, String ownerName) {
        workArea.setPlayerUUID(ownerId);
        workArea.setPlayerName(ownerName);
    }

    public static CompoundTag saveEntity(Entity entity) {
        return entity.saveWithoutId(new CompoundTag());
    }

    public static <T extends Entity> T loadEntity(GameTestHelper helper, EntityType<T> entityType, BlockPos relativePos, CompoundTag savedData) {
        T entity = BannerModGameTestSupport.spawnEntity(helper, entityType, relativePos);
        entity.load(savedData);
        if (entity instanceof AbstractRecruitEntity) {
            RecruitIndex.instance().onEntityJoin(entity);
        }
        return entity;
    }

    private static void ensureClaimManager(ServerLevel level) {
        if (ClaimEvents.claimManager() == null) {
            RecruitsClaimManager claimManager = new RecruitsClaimManager();
            claimManager.load(level);
            ClaimEvents.installClaimManagerForTests(claimManager);
        }
    }
}
