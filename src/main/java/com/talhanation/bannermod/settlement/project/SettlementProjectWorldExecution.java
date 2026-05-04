package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectBlocker;
import com.talhanation.bannermod.settlement.prefab.BuildingPlacementService;
import com.talhanation.bannermod.settlement.prefab.impl.BarracksPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.FarmPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.HousePrefab;
import com.talhanation.bannermod.settlement.prefab.impl.LumberCampPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.MarketStallPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.StoragePrefab;
import com.talhanation.bannermod.society.NpcHousingRequestAccess;
import com.talhanation.bannermod.society.NpcHousingRequestRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.UUID;

final class BannerModSettlementProjectWorldExecution {

    private BannerModSettlementProjectWorldExecution() {
    }

    static boolean ensureExecutableTarget(ServerLevel level, UUID claimUuid, PendingProject project) {
        if (level == null
                || claimUuid == null
                || project == null
                || ClaimEvents.claimManager() == null
                || (project.blockerReason() != ProjectBlocker.NONE && project.blockerReason() != ProjectBlocker.NO_SITE)) {
            return false;
        }
        RecruitsClaim claim = resolveClaim(claimUuid);
        if (claim == null) {
            return false;
        }
        List<BuildArea> buildAreas = BannerModBuildAreaProjectBridge.collectBuildAreas(level, claim);
        if (buildAreas.stream().anyMatch(buildArea -> buildArea != null && buildArea.isAlive() && !buildArea.isDone())) {
            return false;
        }
        NpcHousingRequestRecord housingRequest = NpcHousingRequestAccess.requestForProject(level, project.projectId());
        BuildingPlacementService.Result result = BuildingPlacementService.placeForClaim(
                level,
                claim,
                prefabIdFor(project),
                housingRequest != null && housingRequest.reservedPlotPos() != null
                        ? housingRequest.reservedPlotPos()
                        : choosePlacementPos(level, claim, buildAreas.size()),
                Direction.SOUTH
        );
        if (result != BuildingPlacementService.Result.PLACED) {
            return false;
        }
        if (project != null && project.prefabId() != null) {
            BuildArea placedArea = BannerModBuildAreaProjectBridge.collectBuildAreas(level, claim).stream()
                    .filter(buildArea -> buildArea != null && buildArea.isAlive() && !buildArea.isDone())
                    .max(java.util.Comparator.comparingInt(net.minecraft.world.entity.Entity::getId))
                    .orElse(null);
            if (placedArea != null) {
                if (housingRequest != null) {
                    NpcHousingRequestAccess.bindBuildArea(level, housingRequest.householdId(), placedArea.getUUID(), level.getGameTime());
                }
                // First autonomous livelihood slice: once the ruler approves the request,
                // material bootstrap is granted immediately so the new workplace can start
                // supporting the settlement instead of deadlocking on missing resources.
                placedArea.setStartBuild(true);
            }
        }
        return true;
    }

    private static RecruitsClaim resolveClaim(UUID claimUuid) {
        for (RecruitsClaim claim : ClaimEvents.claimManager().getAllClaims()) {
            if (claim != null && claimUuid.equals(claim.getUUID())) {
                return claim;
            }
        }
        return null;
    }

    private static ResourceLocation prefabIdFor(PendingProject project) {
        if (project != null && project.prefabId() != null) {
            return project.prefabId();
        }
        BannerModSettlementBuildingProfileSeed profileSeed = project == null ? null : project.profileSeed();
        return switch (profileSeed == null ? BannerModSettlementBuildingProfileSeed.GENERAL : profileSeed) {
            case FOOD_PRODUCTION -> FarmPrefab.ID;
            case MATERIAL_PRODUCTION -> LumberCampPrefab.ID;
            case STORAGE -> StoragePrefab.ID;
            case MARKET -> MarketStallPrefab.ID;
            case CONSTRUCTION -> BarracksPrefab.ID;
            case GENERAL -> HousePrefab.ID;
        };
    }

    private static BlockPos choosePlacementPos(ServerLevel level, RecruitsClaim claim, int existingBuildAreaCount) {
        java.util.List<ChunkPos> claimedChunks = claim.getClaimedChunks();
        ChunkPos anchorChunk = claim.getCenter();
        if ((anchorChunk == null || !claim.containsChunk(anchorChunk)) && !claimedChunks.isEmpty()) {
            anchorChunk = claimedChunks.get(Math.floorMod(existingBuildAreaCount, claimedChunks.size()));
        }
        if (anchorChunk == null) {
            return BlockPos.ZERO;
        }

        int lane = claimedChunks.isEmpty() ? 0 : (existingBuildAreaCount / Math.max(1, claimedChunks.size())) % 4;
        int localX = (lane == 1 || lane == 3) ? 11 : 4;
        int localZ = lane >= 2 ? 11 : 4;
        return level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(anchorChunk.getMinBlockX() + localX, level.getSeaLevel(), anchorChunk.getMinBlockZ() + localZ)
        );
    }
}
