package com.talhanation.bannermod.settlement.prefab.staffing;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AnimalPenArea;
import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.entity.civilian.workarea.FishingArea;
import com.talhanation.bannermod.entity.civilian.workarea.LumberArea;
import com.talhanation.bannermod.entity.civilian.workarea.MiningArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.citizen.CitizenProfession;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.registry.civilian.ModEntityTypes;
import com.talhanation.bannermod.settlement.prefab.BuildingPrefab;
import com.talhanation.bannermod.settlement.prefab.BuildingPrefabProfession;
import com.talhanation.bannermod.settlement.prefab.BuildingPrefabRegistry;
import com.talhanation.bannermod.settlement.prefab.PrefabBuildAreaTracker;
import com.talhanation.bannermod.settlement.prefab.impl.BarracksPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.FarmPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.LumberCampPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.MinePrefab;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.society.NpcLifeStage;
import com.talhanation.bannermod.society.NpcSocietyAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-staffing hook for prefab-backed BuildAreas. When a BuildArea that was spawned via a
 * {@link BuildingPrefab} completes, this runtime:
 *
 * <ol>
 *   <li>Consumes the tracker entry so the hook only fires once.</li>
 *   <li>Looks up the prefab's declared {@link BuildingPrefabProfession}.</li>
 *   <li>Spawns the matching worker or recruit entity on top of the BuildArea.</li>
 *   <li>Binds the worker to the embedded work-area entity (if the prefab embedded one).</li>
 *   <li>Transfers the BuildArea's owner UUID and team onto the new staffer.</li>
 * </ol>
 *
 * <p>Server-only, in-memory. Not persisted.</p>
 */
public final class PrefabAutoStaffingRuntime {
    public static final String TAG_PENDING_WORKER_PROFESSION = "BannerModPendingWorkerProfession";
    public static final String TAG_ASSIGNMENT_PAUSE_UNTIL = "BannerModAssignmentPauseUntil";
    private static final Map<UUID, VacancyRecord> VACANCIES = new ConcurrentHashMap<>();
    private static final double VACANCY_ASSIGN_RADIUS_SQR = 96.0D * 96.0D;
    private static OccupancySnapshot occupancySnapshot;

    private PrefabAutoStaffingRuntime() {
    }

    /**
     * Hook called from {@code BuildArea} once it transitions to {@code isDone == true}.
     * Safe to call multiple times: the tracker entry is consumed on the first call so
     * subsequent invocations are no-ops.
     */
    public static void onBuildAreaCompleted(ServerLevel level, BuildArea buildArea) {
        if (level == null || buildArea == null) {
            return;
        }
        if (!com.talhanation.bannermod.settlement.prefab.BuildingPlacementService.isPrefabPipelineEnabledForGate()) {
            // Prefab auto-staffing disabled. Drain any tracker entry so it doesn't
            // leak across config toggles, but spawn nothing — the player builds the
            // structure manually and draws the work zone with the surveyor; citizens
            // bind to the zone via ensureStandaloneWorkAreaVacancies below.
            PrefabBuildAreaTracker.consume(buildArea.getUUID());
            return;
        }

        ResourceLocation prefabId = PrefabBuildAreaTracker.consume(buildArea.getUUID()).orElse(null);
        if (prefabId == null) {
            return;
        }

        Optional<BuildingPrefab> prefabOpt = BuildingPrefabRegistry.instance().lookup(prefabId);
        if (prefabOpt.isEmpty()) {
            System.err.println("[PrefabAutoStaffing] Unknown prefab id=" + prefabId + " for BuildArea " + buildArea.getUUID());
            return;
        }

        BuildingPrefabProfession profession = prefabOpt.get().descriptor().profession();
        if (profession == null || profession == BuildingPrefabProfession.NONE) {
            return;
        }

        AbstractWorkAreaEntity workArea = findEmbeddedWorkArea(level, buildArea);
        registerVacancy(buildArea, prefabId, profession, workArea);
    }

    public static void registerValidatedBuildingVacancy(ValidatedBuildingRecord record) {
        if (record == null || record.state() != com.talhanation.bannermod.settlement.building.BuildingValidationState.VALID) {
            return;
        }
        BuildingPrefabProfession profession = professionForManualBuilding(record.type());
        int slots = vacancySlotsForManualBuilding(record.type());
        if (profession == BuildingPrefabProfession.NONE || slots <= 0) {
            return;
        }
        VACANCIES.put(record.buildingId(), new VacancyRecord(record.buildingId(), profession, slots, record.anchorPos()));
    }

    public static String describeManualVacancy(BuildingType type) {
        BuildingPrefabProfession profession = professionForManualBuilding(type);
        int slots = vacancySlotsForManualBuilding(type);
        return profession == BuildingPrefabProfession.NONE || slots <= 0
                ? "Vacancy: none"
                : "Vacancy: " + profession.name() + " x" + slots;
    }

    /**
     * Maps a {@link BuildingPrefabProfession} to the corresponding {@link EntityType} to spawn.
     * {@code null} is returned for {@link BuildingPrefabProfession#NONE}.
     *
     * <p>Visible for unit tests — must stay pure (no Level/World access).</p>
     */
    @Nullable
    public static EntityType<?> entityTypeFor(BuildingPrefabProfession profession) {
        if (profession == null) {
            return null;
        }
        return switch (profession) {
            case NONE -> null;
            case FARMER -> ModEntityTypes.FARMER.get();
            case LUMBERJACK -> ModEntityTypes.LUMBERJACK.get();
            case MINER -> ModEntityTypes.MINER.get();
            case BUILDER -> ModEntityTypes.BUILDER.get();
            case MERCHANT -> ModEntityTypes.MERCHANT.get();
            case FISHERMAN -> ModEntityTypes.FISHERMAN.get();
            case ANIMAL_FARMER -> ModEntityTypes.ANIMAL_FARMER.get();
            // SHEPHERD reuses animal farmer for now — we don't have a dedicated shepherd type yet.
            case SHEPHERD -> ModEntityTypes.ANIMAL_FARMER.get();
            case RECRUIT_SWORDSMAN -> com.talhanation.bannermod.registry.military.ModEntityTypes.RECRUIT.get();
            case RECRUIT_ARCHER -> com.talhanation.bannermod.registry.military.ModEntityTypes.BOWMAN.get();
            case RECRUIT_PIKEMAN -> com.talhanation.bannermod.registry.military.ModEntityTypes.RECRUIT_SHIELDMAN.get();
            case RECRUIT_CROSSBOW -> com.talhanation.bannermod.registry.military.ModEntityTypes.CROSSBOWMAN.get();
            case RECRUIT_CAVALRY -> com.talhanation.bannermod.registry.military.ModEntityTypes.HORSEMAN.get();
        };
    }

    /**
     * Returns true if the profession represents a civilian worker (bindable to a work-area).
     * Visible for tests.
     */
    public static boolean isWorkerProfession(BuildingPrefabProfession profession) {
        if (profession == null) {
            return false;
        }
        return switch (profession) {
            case FARMER, LUMBERJACK, MINER, BUILDER, MERCHANT, FISHERMAN, ANIMAL_FARMER, SHEPHERD -> true;
            default -> false;
        };
    }

    @Nullable
    private static AbstractWorkAreaEntity findEmbeddedWorkArea(ServerLevel level, BuildArea buildArea) {
        AABB queryBox = buildArea.getBoundingBox().inflate(4);
        double queryRadius = Math.sqrt(queryBox.getXsize() * queryBox.getXsize()
                + queryBox.getYsize() * queryBox.getYsize()
                + queryBox.getZsize() * queryBox.getZsize()) / 2.0D;
        List<AbstractWorkAreaEntity> candidates = WorkAreaIndex.instance().queryInRange(
                level,
                queryBox.getCenter(),
                queryRadius,
                AbstractWorkAreaEntity.class
        );
        AbstractWorkAreaEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (AbstractWorkAreaEntity wa : candidates) {
            if (wa == null || !wa.isAlive()) continue;
            if (wa == buildArea) continue;
            if (wa instanceof BuildArea) continue;
            double dsq = wa.distanceToSqr(buildArea);
            if (dsq < nearestDistSq) {
                nearestDistSq = dsq;
                nearest = wa;
            }
        }
        return nearest;
    }

    private static void registerVacancy(BuildArea buildArea,
                                        ResourceLocation prefabId,
                                        BuildingPrefabProfession profession,
                                        @Nullable AbstractWorkAreaEntity workArea) {
        UUID anchorUuid = workArea != null ? workArea.getUUID() : buildArea.getUUID();
        int slots = vacancySlotsFor(prefabId, profession);
        if (slots <= 0) {
            return;
        }
        VACANCIES.put(anchorUuid, new VacancyRecord(anchorUuid, profession, slots, buildArea.blockPosition()));
    }

    /**
     * Surveyor-drawn work areas (CropArea, MiningArea, LumberArea, FishingArea, AnimalPenArea)
     * are not tied to a prefab BuildArea, so they never call {@link #onBuildAreaCompleted}
     * and never appear in {@link #VACANCIES}. Without a vacancy entry, idle citizens have
     * nothing to apply for and just wander. Walk the index near the citizen and lift any
     * unbound standalone work area into VACANCIES so the regular assignment loop below
     * can pick it up.
     *
     * <p>Synthetic vacancies use 1 slot and the area's UUID as anchor, matching the embedded
     * work-area branch of {@link #registerVacancy}.
     */
    private static void ensureStandaloneWorkAreaVacancies(ServerLevel level, CitizenEntity citizen) {
        double scanRadius = Math.sqrt(VACANCY_ASSIGN_RADIUS_SQR);
        List<AbstractWorkAreaEntity> areas = WorkAreaIndex.instance().queryInRange(citizen, scanRadius, AbstractWorkAreaEntity.class);
        for (AbstractWorkAreaEntity area : areas) {
            if (area == null || !area.isAlive()) continue;
            UUID anchor = area.getUUID();
            if (VACANCIES.containsKey(anchor)) continue;
            BuildingPrefabProfession profession = professionForStandaloneWorkArea(area);
            if (profession == BuildingPrefabProfession.NONE) continue;
            VACANCIES.put(anchor, new VacancyRecord(anchor, profession, 1, area.blockPosition()));
        }
    }

    private static BuildingPrefabProfession professionForStandaloneWorkArea(AbstractWorkAreaEntity area) {
        if (area instanceof CropArea) return BuildingPrefabProfession.FARMER;
        if (area instanceof MiningArea) return BuildingPrefabProfession.MINER;
        if (area instanceof LumberArea) return BuildingPrefabProfession.LUMBERJACK;
        if (area instanceof FishingArea) return BuildingPrefabProfession.FISHERMAN;
        if (area instanceof AnimalPenArea) return BuildingPrefabProfession.ANIMAL_FARMER;
        // BuildArea / StorageArea / MarketArea are intentionally not auto-staffed:
        // BuildArea is transient (consumed by onBuildAreaCompleted), StorageArea and
        // MarketArea are logistics anchors with no worker profession.
        return BuildingPrefabProfession.NONE;
    }

    /**
     * Manually pin a citizen to a specific work-area UUID, bypassing the distance and
     * "nearest vacancy" picking logic. Used by the player-driven Assign-Vacancy button.
     *
     * <p>The work area must already exist (or have been lifted into VACANCIES via the
     * standalone-work-area helper). If no profession can be derived (BuildArea / Storage
     * / Market), this is a no-op so we don't bind a citizen to a vacancy that produces
     * NONE profession.
     *
     * @return {@code true} if the binding was applied.
     */
    public static boolean assignCitizenToSpecificVacancy(ServerLevel level, CitizenEntity citizen, UUID anchorUuid) {
        if (level == null || citizen == null || anchorUuid == null
                || !citizen.isAlive() || citizen.isRemoved()) {
            return false;
        }
        // Lift the requested anchor if it's a standalone work-area we haven't tracked
        // yet — manual assign should work for fresh surveyor-drawn zones too.
        Entity entity = level.getEntity(anchorUuid);
        if (!VACANCIES.containsKey(anchorUuid) && entity instanceof AbstractWorkAreaEntity wa && wa.isAlive()) {
            BuildingPrefabProfession profession = professionForStandaloneWorkArea(wa);
            if (profession != BuildingPrefabProfession.NONE) {
                VACANCIES.put(anchorUuid, new VacancyRecord(anchorUuid, profession, 1, wa.blockPosition()));
            }
        }
        VacancyRecord vacancy = VACANCIES.get(anchorUuid);
        if (vacancy == null) {
            return false;
        }
        CitizenProfession pending = toCitizenProfession(vacancy.profession());
        if (pending == CitizenProfession.NONE) {
            return false;
        }
        // Clear any stale assignment pause that would block the binding from taking effect.
        citizen.getPersistentData().remove(TAG_ASSIGNMENT_PAUSE_UNTIL);
        citizen.getPersistentData().putString(TAG_PENDING_WORKER_PROFESSION, pending.name());
        citizen.setBoundWorkAreaUUID(anchorUuid);
        incrementOccupancy(level, anchorUuid);
        return true;
    }

    public static void assignCitizenToNearestVacancy(ServerLevel level, CitizenEntity citizen) {
        if (level == null || citizen == null || !citizen.isAlive() || citizen.isRemoved()) {
            return;
        }
        NpcLifeStage lifeStage = NpcSocietyAccess.ensureResident(level, citizen.getUUID(), level.getGameTime()).lifeStage();
        if (lifeStage != NpcLifeStage.ADULT && lifeStage != NpcLifeStage.ELDER) {
            return;
        }
        long pausedUntil = citizen.getPersistentData().getLong(TAG_ASSIGNMENT_PAUSE_UNTIL);
        if (pausedUntil > level.getGameTime()) {
            return;
        }
        if (pausedUntil != 0L) {
            citizen.getPersistentData().remove(TAG_ASSIGNMENT_PAUSE_UNTIL);
        }
        if (citizen.getPersistentData().contains(TAG_PENDING_WORKER_PROFESSION)) {
            return;
        }
        // Lift any standalone (surveyor-drawn) work area in range into VACANCIES
        // so citizens take farms/mines/etc. that were never tied to a prefab build.
        ensureStandaloneWorkAreaVacancies(level, citizen);
        VacancyRecord best = null;
        double bestDistanceSqr = Double.POSITIVE_INFINITY;
        for (VacancyRecord vacancy : VACANCIES.values()) {
            Entity anchor = level.getEntity(vacancy.anchorUuid());
            int freeSlots = vacancy.totalSlots() - currentOccupancy(level, vacancy.anchorUuid());
            if (freeSlots <= 0) {
                continue;
            }
            double distSqr = anchor != null && anchor.isAlive()
                    ? citizen.distanceToSqr(anchor)
                    : citizen.distanceToSqr(Vec3.atCenterOf(vacancy.anchorPos()));
            if (distSqr > VACANCY_ASSIGN_RADIUS_SQR) {
                continue;
            }
            if (distSqr < bestDistanceSqr) {
                bestDistanceSqr = distSqr;
                best = vacancy;
            }
        }
        if (best == null) {
            return;
        }
        CitizenProfession pending = toCitizenProfession(best.profession());
        if (pending == CitizenProfession.NONE) {
            return;
        }
        citizen.getPersistentData().putString(TAG_PENDING_WORKER_PROFESSION, pending.name());
        citizen.setBoundWorkAreaUUID(best.anchorUuid());
        incrementOccupancy(level, best.anchorUuid());
    }

    public static boolean hasConversionSlot(ServerLevel level, UUID anchorUuid, UUID convertingCitizenUuid) {
        if (level == null || anchorUuid == null) {
            return false;
        }
        VacancyRecord vacancy = VACANCIES.get(anchorUuid);
        int totalSlots = vacancy == null ? 1 : vacancy.totalSlots();
        return currentOccupancy(level, anchorUuid, convertingCitizenUuid) < totalSlots;
    }

    @Nullable
    public static BlockPos conversionAnchorPosition(UUID anchorUuid) {
        VacancyRecord vacancy = anchorUuid == null ? null : VACANCIES.get(anchorUuid);
        return vacancy == null ? null : vacancy.anchorPos();
    }

    private static int currentOccupancy(ServerLevel level, UUID anchorUuid) {
        return currentOccupancy(level, anchorUuid, null);
    }

    private static int currentOccupancy(ServerLevel level, UUID anchorUuid, @Nullable UUID excludingCitizenUuid) {
        int count = occupancyByAnchor(level).getOrDefault(anchorUuid, 0);
        return excludingCitizenUuid != null && anchorUuid.equals(boundCitizen(level, excludingCitizenUuid))
                ? Math.max(0, count - 1)
                : count;
    }

    private static void incrementOccupancy(ServerLevel level, UUID anchorUuid) {
        occupancyByAnchor(level).merge(anchorUuid, 1, Integer::sum);
    }

    @Nullable
    private static UUID boundCitizen(ServerLevel level, UUID citizenUuid) {
        Entity entity = level.getEntity(citizenUuid);
        return entity instanceof CitizenEntity citizen ? citizen.getBoundWorkAreaUUID() : null;
    }

    private static Map<UUID, Integer> occupancyByAnchor(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (occupancySnapshot == null
                || occupancySnapshot.gameTime() != gameTime
                || !occupancySnapshot.dimension().equals(level.dimension())) {
            occupancySnapshot = new OccupancySnapshot(level.dimension(), gameTime, scanOccupancy(level));
        }
        return occupancySnapshot.occupancyByAnchor();
    }

    private static Map<UUID, Integer> scanOccupancy(ServerLevel level) {
        AABB search = new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000, 30_000_000, level.getMaxBuildHeight(), 30_000_000);
        Map<UUID, Integer> occupancy = new HashMap<>();
        for (CitizenEntity citizen : level.getEntitiesOfClass(CitizenEntity.class, search)) {
            UUID bound = citizen.getBoundWorkAreaUUID();
            if (bound != null) {
                occupancy.merge(bound, 1, Integer::sum);
            }
        }
        for (AbstractWorkerEntity worker : level.getEntitiesOfClass(AbstractWorkerEntity.class, search)) {
            UUID bound = worker.getCitizenCore().getBoundWorkAreaUUID();
            if (bound != null) {
                occupancy.merge(bound, 1, Integer::sum);
            }
        }
        java.util.List<AbstractRecruitEntity> recruits = com.talhanation.bannermod.entity.military.RecruitIndex
                .instance().all(level, true);
        for (AbstractRecruitEntity recruit : recruits) {
            UUID bound = recruit.getCitizenCore().getBoundWorkAreaUUID();
            if (bound != null) {
                occupancy.merge(bound, 1, Integer::sum);
            }
        }
        return occupancy;
    }

    static int vacancySlotsFor(ResourceLocation prefabId, BuildingPrefabProfession profession) {
        if (profession == null || profession == BuildingPrefabProfession.NONE) {
            return 0;
        }
        boolean recruitRole = switch (profession) {
            case RECRUIT_SWORDSMAN, RECRUIT_ARCHER, RECRUIT_PIKEMAN, RECRUIT_CROSSBOW, RECRUIT_CAVALRY -> true;
            default -> false;
        };
        if (!recruitRole) {
            return 1;
        }
        return BarracksPrefab.ID.equals(prefabId) ? 4 : 1;
    }

    public static BuildingPrefabProfession professionForManualBuilding(BuildingType type) {
        if (type == null) {
            return BuildingPrefabProfession.NONE;
        }
        return switch (type) {
            case FARM -> BuildingPrefabProfession.FARMER;
            case MINE -> BuildingPrefabProfession.MINER;
            case LUMBER_CAMP -> BuildingPrefabProfession.LUMBERJACK;
            case ARCHITECT_WORKSHOP -> BuildingPrefabProfession.BUILDER;
            case BARRACKS -> BuildingPrefabProfession.RECRUIT_SWORDSMAN;
            default -> BuildingPrefabProfession.NONE;
        };
    }

    public static int vacancySlotsForManualBuilding(BuildingType type) {
        BuildingPrefabProfession profession = professionForManualBuilding(type);
        if (type == BuildingType.FARM) {
            return vacancySlotsFor(FarmPrefab.ID, profession);
        }
        if (type == BuildingType.MINE) {
            return vacancySlotsFor(MinePrefab.ID, profession);
        }
        if (type == BuildingType.LUMBER_CAMP) {
            return vacancySlotsFor(LumberCampPrefab.ID, profession);
        }
        if (type == BuildingType.BARRACKS) {
            return vacancySlotsFor(BarracksPrefab.ID, profession);
        }
        return profession == BuildingPrefabProfession.NONE ? 0 : 1;
    }

    private static CitizenProfession toCitizenProfession(BuildingPrefabProfession profession) {
        if (profession == null) {
            return CitizenProfession.NONE;
        }
        return switch (profession) {
            case FARMER -> CitizenProfession.FARMER;
            case LUMBERJACK -> CitizenProfession.LUMBERJACK;
            case MINER -> CitizenProfession.MINER;
            case BUILDER -> CitizenProfession.BUILDER;
            case MERCHANT -> CitizenProfession.MERCHANT;
            case FISHERMAN -> CitizenProfession.FISHERMAN;
            case ANIMAL_FARMER, SHEPHERD -> CitizenProfession.ANIMAL_FARMER;
            case RECRUIT_SWORDSMAN -> CitizenProfession.RECRUIT_SPEAR;
            case RECRUIT_ARCHER -> CitizenProfession.RECRUIT_BOWMAN;
            case RECRUIT_PIKEMAN -> CitizenProfession.RECRUIT_SHIELDMAN;
            case RECRUIT_CROSSBOW -> CitizenProfession.RECRUIT_CROSSBOWMAN;
            case RECRUIT_CAVALRY -> CitizenProfession.RECRUIT_HORSEMAN;
            case NONE -> CitizenProfession.NONE;
        };
    }

    private record VacancyRecord(UUID anchorUuid, BuildingPrefabProfession profession, int totalSlots, BlockPos anchorPos) {
    }

    private record OccupancySnapshot(net.minecraft.resources.ResourceKey<Level> dimension, long gameTime, Map<UUID, Integer> occupancyByAnchor) {
    }
}
