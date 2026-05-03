package com.talhanation.bannermod.settlement.civilian;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.AnimalFarmerEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.FishermanEntity;
import com.talhanation.bannermod.entity.civilian.LumberjackEntity;
import com.talhanation.bannermod.entity.civilian.MinerEntity;
import com.talhanation.bannermod.ai.civilian.FarmerPlantingPreparation;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AnimalPenArea;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.entity.civilian.workarea.FishingArea;
import com.talhanation.bannermod.entity.civilian.workarea.LumberArea;
import com.talhanation.bannermod.entity.civilian.workarea.MiningArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.registry.civilian.ModEntityTypes;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementRefreshSupport;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WorkerSettlementSpawner {

    private WorkerSettlementSpawner() {
    }

    @Nullable
    public static AbstractWorkerEntity spawnWorkerFromVillager(ServerLevel level,
                                                               Villager villager,
                                                               WorkerSettlementSpawnRules.Decision decision,
                                                               @Nullable RecruitsClaim claim) {
        if (level == null || villager == null) {
            return null;
        }

        AbstractWorkerEntity worker = spawnWorker(level, villager.blockPosition(), decision, claim);
        if (worker == null) {
            return null;
        }

        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack itemStack = villager.getInventory().getItem(i);
            worker.getInventory().addItem(itemStack.copy());
        }

        Component name = villager.getCustomName();
        if (name != null && !name.getString().isEmpty()) {
            worker.setCustomName(name);
        }

        if (RecruitsServerConfig.RecruitTablesPOIReleasing.get()) {
            villager.releasePoi(MemoryModuleType.JOB_SITE);
        }
        villager.releasePoi(MemoryModuleType.HOME);
        villager.releasePoi(MemoryModuleType.MEETING_POINT);
        villager.discard();

        return worker;
    }

    @Nullable
    public static AbstractWorkerEntity spawnClaimWorker(ServerLevel level,
                                                        BlockPos spawnPos,
                                                        WorkerSettlementSpawnRules.Decision decision,
                                                        @Nullable RecruitsClaim claim) {
        if (level == null || spawnPos == null) {
            return null;
        }

        return spawnWorker(level, spawnPos, decision, claim);
    }

    @Nullable
    private static AbstractWorkerEntity spawnWorker(ServerLevel level,
                                                    BlockPos spawnPos,
                                                    WorkerSettlementSpawnRules.Decision decision,
                                                    @Nullable RecruitsClaim claim) {
        if (decision == null || !decision.allowed() || claim == null) {
            return null;
        }

        WorkerSettlementSpawnRules.WorkerProfession profession = decision.profession();
        if (profession == null) {
            return null;
        }

        UUID politicalEntityId = claim.getOwnerPoliticalEntityId();
        if (politicalEntityId == null) {
            return null;
        }
        PoliticalEntityRecord owner = WarRuntimeContext.registry(level).byId(politicalEntityId).orElse(null);
        UUID leaderId = owner == null ? null : owner.leaderUuid();
        if (owner == null || leaderId == null) {
            return null;
        }

        EntityType<? extends AbstractWorkerEntity> workerType = resolveWorkerType(profession);
        if (workerType == null) {
            return null;
        }

        AbstractWorkerEntity worker = workerType.create(level);
        if (worker == null) {
            return null;
        }

        BlockPos safeSpawnPos = resolveSafeSpawnPos(level, spawnPos);
        worker.moveTo(safeSpawnPos.getX() + 0.5D, safeSpawnPos.getY(), safeSpawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        worker.finalizeSpawn(level, level.getCurrentDifficultyAt(safeSpawnPos), MobSpawnType.EVENT, null, null);
        worker.setIsOwned(true);
        worker.setOwnerUUID(Optional.of(leaderId));
        worker.setFollowState(0);

        level.addFreshEntity(worker);
        PlayerTeam team = level.getScoreboard().getPlayerTeam(owner.name());
        if (team != null) {
            level.getScoreboard().addPlayerToTeam(worker.getScoreboardName(), team);
        }
        seedClaimWorkAreaDefaults(level, worker, claim, owner, safeSpawnPos);
        BannerModSettlementRefreshSupport.refreshSnapshot(level, worker.blockPosition());
        if (worker instanceof FarmerEntity farmer && farmer.getCurrentCropArea() == null) {
            seedClaimWorkAreaDefaults(level, farmer, claim, owner, safeSpawnPos);
        }

        return worker;
    }

    private static BlockPos resolveSafeSpawnPos(ServerLevel level, BlockPos preferredPos) {
        if (isSpawnSpaceClear(level, preferredPos)) {
            return preferredPos;
        }
        BlockPos abovePreferred = preferredPos.above();
        if (isSpawnSpaceClear(level, abovePreferred)) {
            return abovePreferred;
        }
        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, preferredPos);
        if (isSpawnSpaceClear(level, surfacePos)) {
            return surfacePos;
        }
        return surfacePos.above();
    }

    private static boolean isSpawnSpaceClear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }

    private static void seedClaimWorkAreaDefaults(ServerLevel level,
                                                  AbstractWorkerEntity worker,
                                                  RecruitsClaim claim,
                                                  PoliticalEntityRecord owner,
                                                  BlockPos spawnPos) {
        if (worker == null || claim == null || owner == null) {
            return;
        }

        if (bindExistingClaimWorkArea(level, claim, worker)) {
            return;
        }

        if (!(worker instanceof FarmerEntity farmer)) {
            return;
        }

        CropArea existingArea = findClaimCropArea(level, claim, farmer);
        if (existingArea != null) {
            farmer.setCurrentWorkArea(existingArea);
            return;
        }

        BlockPos fieldCenter = findFieldCenter(level, claim, spawnPos);
        if (fieldCenter == null) {
            return;
        }

        CropArea cropArea = new CropArea(ModEntityTypes.CROPAREA.get(), level);
        cropArea.setWidthSize(9);
        cropArea.setHeightSize(2);
        cropArea.setDepthSize(9);
        cropArea.setFacing(Direction.NORTH);
        cropArea.moveTo(fieldCenter.getX() - 4, fieldCenter.getY(), fieldCenter.getZ() + 4, 0.0F, 0.0F);
        cropArea.createArea();
        cropArea.setDone(false);
        cropArea.setTeamStringID(owner.name());
        cropArea.setPlayerUUID(owner.leaderUuid());
        cropArea.setPlayerName(owner.name());
        cropArea.setCustomName(Component.literal(""));

        ItemStack seedStack = resolveFieldSeed(level, fieldCenter);
        if (!seedStack.isEmpty()) {
            cropArea.setSeedStack(seedStack);
            cropArea.updateType();
        }

        level.addFreshEntity(cropArea);
        WorkAreaIndex.instance().onEntityJoin(cropArea);
        farmer.setCurrentWorkArea(cropArea);
    }

    private static boolean bindExistingClaimWorkArea(ServerLevel level,
                                                     RecruitsClaim claim,
                                                     AbstractWorkerEntity worker) {
        if (worker instanceof FarmerEntity farmer) {
            CropArea area = findClaimCropArea(level, claim, farmer);
            if (area != null) {
                farmer.setCurrentWorkArea(area);
                return true;
            }
            return false;
        }
        if (worker instanceof LumberjackEntity lumberjack) {
            LumberArea area = findClaimArea(level, claim, LumberArea.class, lumberjack);
            if (area != null) {
                lumberjack.setCurrentWorkArea(area);
                return true;
            }
            return false;
        }
        if (worker instanceof MinerEntity miner) {
            MiningArea area = findClaimArea(level, claim, MiningArea.class, miner);
            if (area != null) {
                miner.setCurrentWorkArea(area);
                return true;
            }
            return false;
        }
        if (worker instanceof AnimalFarmerEntity animalFarmer) {
            AnimalPenArea area = findClaimArea(level, claim, AnimalPenArea.class, animalFarmer);
            if (area != null) {
                animalFarmer.setCurrentWorkArea(area);
                return true;
            }
            return false;
        }
        if (worker instanceof FishermanEntity fisherman) {
            FishingArea area = findClaimArea(level, claim, FishingArea.class, fisherman);
            if (area != null) {
                fisherman.setCurrentWorkArea(area);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static CropArea findClaimCropArea(ServerLevel level, RecruitsClaim claim, FarmerEntity farmer) {
        for (CropArea cropArea : getClaimCropAreas(level, claim)) {
            if (cropArea.canWorkHere(farmer)) {
                return cropArea;
            }
        }
        return null;
    }

    private static List<CropArea> getClaimCropAreas(ServerLevel level, RecruitsClaim claim) {
        return WorkAreaIndex.instance().queryInChunks(level, claim.getClaimedChunks(), CropArea.class);
    }

    @Nullable
    private static <T extends AbstractWorkAreaEntity> T findClaimArea(ServerLevel level,
                                                                      RecruitsClaim claim,
                                                                      Class<T> areaType,
                                                                      AbstractWorkerEntity worker) {
        for (T area : WorkAreaIndex.instance().queryInChunks(level, claim.getClaimedChunks(), areaType)) {
            if (area != null && area.canWorkHere(worker)) {
                return area;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findFieldCenter(ServerLevel level, RecruitsClaim claim, BlockPos spawnPos) {
        BlockPos waterCenteredField = findWaterCenteredField(level, claim, spawnPos);
        if (waterCenteredField != null) {
            return waterCenteredField;
        }

        return findPreparedFieldFallbackCenter(level, claim);
    }

    @Nullable
    private static BlockPos findWaterCenteredField(ServerLevel level, RecruitsClaim claim, BlockPos spawnPos) {
        BlockPos bestPos = null;
        int bestScore = 0;
        double bestDistance = Double.MAX_VALUE;
        int minChunkX = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.x).min().orElse(claim.getCenter().x);
        int maxChunkX = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.x).max().orElse(claim.getCenter().x);
        int minChunkZ = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.z).min().orElse(claim.getCenter().z);
        int maxChunkZ = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.z).max().orElse(claim.getCenter().z);
        int minX = minChunkX << 4;
        int maxX = (maxChunkX << 4) + 15;
        int minZ = minChunkZ << 4;
        int maxZ = (maxChunkZ << 4) + 15;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight() - 1; y++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!level.getBlockState(candidate).is(Blocks.WATER)) {
                        continue;
                    }

                    int score = scoreFieldAround(level, candidate);
                    if (score < 12) {
                        continue;
                    }

                    double distance = candidate.distSqr(spawnPos);
                    if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                        bestScore = score;
                        bestDistance = distance;
                        bestPos = candidate;
                    }
                }
            }
        }

        return bestPos;
    }

    @Nullable
    private static BlockPos findPreparedFieldFallbackCenter(ServerLevel level, RecruitsClaim claim) {
        int minChunkX = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.x).min().orElse(claim.getCenter().x);
        int maxChunkX = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.x).max().orElse(claim.getCenter().x);
        int minChunkZ = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.z).min().orElse(claim.getCenter().z);
        int maxChunkZ = claim.getClaimedChunks().stream().mapToInt(chunk -> chunk.z).max().orElse(claim.getCenter().z);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int fieldY = Integer.MIN_VALUE;

        for (int x = minChunkX << 4; x <= (maxChunkX << 4) + 15; x++) {
            for (int z = minChunkZ << 4; z <= (maxChunkZ << 4) + 15; z++) {
                for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight() - 1; y++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!isPreparedFieldBlock(level, candidate)) {
                        continue;
                    }
                    if (fieldY == Integer.MIN_VALUE) {
                        fieldY = y;
                    }
                    if (y != fieldY) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }

        if (fieldY == Integer.MIN_VALUE) {
            return null;
        }

        return new BlockPos((minX + maxX) / 2, fieldY, (minZ + maxZ) / 2);
    }

    private static int scoreFieldAround(ServerLevel level, BlockPos center) {
        int score = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                BlockPos groundPos = center.offset(dx, 0, dz);
                BlockState groundState = level.getBlockState(groundPos);
                BlockState cropState = level.getBlockState(groundPos.above());
                if (groundState.is(Blocks.FARMLAND)
                        || cropState.getBlock() instanceof CropBlock
                        || cropState.getBlock() instanceof StemBlock
                        || cropState.getBlock() instanceof BushBlock) {
                    score++;
                }
            }
        }
        return score;
    }

    private static boolean isPreparedFieldBlock(ServerLevel level, BlockPos pos) {
        BlockState groundState = level.getBlockState(pos);
        BlockState cropState = level.getBlockState(pos.above());
        return groundState.is(Blocks.FARMLAND)
                || cropState.getBlock() instanceof CropBlock
                || cropState.getBlock() instanceof StemBlock
                || cropState.getBlock() instanceof BushBlock;
    }

    private static ItemStack resolveFieldSeed(ServerLevel level, BlockPos center) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                ItemStack seed = resolveSeedFromCropState(level.getBlockState(center.offset(dx, 1, dz)));
                if (!seed.isEmpty()) {
                    return seed;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack resolveSeedFromCropState(BlockState cropState) {
        if (cropState == null || cropState.isAir()) {
            return ItemStack.EMPTY;
        }

        Item item = cropState.getBlock().asItem();
        if (FarmerPlantingPreparation.isSupportedSeedItem(item)) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static EntityType<? extends AbstractWorkerEntity> resolveWorkerType(WorkerSettlementSpawnRules.WorkerProfession profession) {
        return switch (profession) {
            case FARMER -> ModEntityTypes.FARMER.get();
            case LUMBERJACK -> ModEntityTypes.LUMBERJACK.get();
            case MINER -> ModEntityTypes.MINER.get();
            case BUILDER -> ModEntityTypes.BUILDER.get();
            case MERCHANT -> ModEntityTypes.MERCHANT.get();
            case FISHERMAN -> ModEntityTypes.FISHERMAN.get();
            case ANIMAL_FARMER -> ModEntityTypes.ANIMAL_FARMER.get();
        };
    }
}
