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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
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
        bindExistingClaimWorkArea(level, claim, worker);
        reportInitialAssignmentStatus(worker, profession);
        BannerModSettlementRefreshSupport.refreshSnapshot(level, worker.blockPosition());

        return worker;
    }

    public static void reportInitialAssignmentStatus(@Nullable AbstractWorkerEntity worker,
                                                     @Nullable WorkerSettlementSpawnRules.WorkerProfession profession) {
        if (worker == null || worker.getCurrentWorkArea() != null || profession == null) {
            return;
        }
        String reasonToken = switch (profession) {
            case FARMER -> "farmer_no_area";
            case LUMBERJACK -> "lumberjack_no_area";
            case MINER -> "miner_no_area";
            case BUILDER -> "builder_no_area";
            case MERCHANT -> "merchant_no_market";
            case FISHERMAN -> "fisherman_no_area";
            case ANIMAL_FARMER -> "animal_farmer_no_pen";
        };
        worker.reportIdleReason(reasonToken, null);
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
