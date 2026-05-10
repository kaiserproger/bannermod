package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.compat.venaterra.VenaterraDepositCandidate;
import com.talhanation.bannermod.compat.venaterra.VenaterraDepositCategory;
import com.talhanation.bannermod.compat.venaterra.VenaterraDepositProvider;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StrategicMineSiteService {
    private static final int SNAPSHOT_MINE_RADIUS = 8;

    private StrategicMineSiteService() {
    }

    public static List<StrategicMineSite> derive(ServerLevel level,
                                                 RecruitsClaim claim,
                                                 @Nullable SettlementSnapshot snapshot,
                                                 List<ValidatedBuildingRecord> validatedBuildings) {
        return derive(level, claim, snapshot, validatedBuildings, VenaterraDepositProvider.create());
    }

    static List<StrategicMineSite> derive(@Nullable ServerLevel level,
                                          RecruitsClaim claim,
                                          @Nullable SettlementSnapshot snapshot,
                                          List<ValidatedBuildingRecord> validatedBuildings,
                                          VenaterraDepositProvider depositProvider) {
        if (claim == null) {
            return List.of();
        }

        List<ValidatedBuildingRecord> records = validatedBuildings == null ? List.of() : validatedBuildings;
        List<VenaterraDepositCandidate> deposits = depositProvider == null ? List.of() : depositProvider.findClaimDeposits(level, claim);
        deposits = deposits == null ? List.of() : deposits;
        List<StrategicMineSite> sites = new ArrayList<>();
        Set<UUID> knownMineBuildingIds = new HashSet<>();
        Map<UUID, Integer> assignedWorkerCounts = assignedWorkersByBuildingId(snapshot);

        for (ValidatedBuildingRecord record : records) {
            if (!record.settlementId().equals(claim.getUUID()) || record.type() != BuildingType.MINE) {
                continue;
            }
            SettlementBuildingRecord snapshotMine = snapshotMineForRecord(record, snapshot);
            knownMineBuildingIds.add(record.buildingId());
            if (snapshotMine != null) {
                knownMineBuildingIds.add(snapshotMine.buildingUuid());
            }
            if (record.state() != BuildingValidationState.VALID) {
                continue;
            }
            sites.add(createSite(
                    record.buildingId(),
                    claim,
                    record.dimension(),
                    centerOf(record.bounds(), record.anchorPos()),
                    radiusOf(record.bounds()),
                    StrategicMineSite.SourceType.VALIDATED_MINE_BUILDING,
                    snapshotMine == null ? assignedWorkerCounts.getOrDefault(record.buildingId(), 0) : snapshotMine.assignedWorkerCount(),
                    deposits
            ));
        }

        if (snapshot != null && snapshot.claimUuid().equals(claim.getUUID())) {
            ResourceKey<Level> dimension = level == null ? Level.OVERWORLD : level.dimension();
            for (SettlementBuildingRecord building : snapshot.buildings()) {
                if (knownMineBuildingIds.contains(building.buildingUuid()) || !isMineLike(building.buildingTypeId())) {
                    continue;
                }
                sites.add(createSite(
                        building.buildingUuid(),
                        claim,
                        dimension,
                        building.originPos(),
                        SNAPSHOT_MINE_RADIUS,
                        StrategicMineSite.SourceType.CLAIM_MINE_WORK_AREA,
                        building.assignedWorkerCount(),
                        deposits
                ));
            }
        }

        return List.copyOf(sites);
    }

    @Nullable
    private static SettlementBuildingRecord snapshotMineForRecord(ValidatedBuildingRecord record,
                                                                  @Nullable SettlementSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (record.buildingId().equals(building.buildingUuid())) {
                return building;
            }
        }
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (isMineLike(building.buildingTypeId()) && contains(record.bounds(), building.originPos())) {
                return building;
            }
        }
        return null;
    }

    private static StrategicMineSite createSite(UUID siteId,
                                                RecruitsClaim claim,
                                                ResourceKey<Level> dimension,
                                                BlockPos center,
                                                int radius,
                                                StrategicMineSite.SourceType sourceType,
                                                int assignedWorkerCount,
                                                List<VenaterraDepositCandidate> deposits) {
        VenaterraDepositCandidate deposit = closestReliableDeposit(dimension, center, radius, deposits);
        if (deposit == null) {
            return new StrategicMineSite(
                    siteId,
                    claim.getUUID(),
                    claim.getOwnerPoliticalEntityId(),
                    dimension,
                    center,
                    radius,
                    sourceType,
                    VenaterraDepositCategory.UNKNOWN_OTHER,
                    0.0F,
                    assignedWorkerCount,
                    true,
                    true
            );
        }

        return new StrategicMineSite(
                siteId,
                claim.getUUID(),
                claim.getOwnerPoliticalEntityId(),
                dimension,
                center,
                radius,
                sourceType,
                deposit.category(),
                deposit.richness(),
                assignedWorkerCount,
                false,
                false
        );
    }

    private static Map<UUID, Integer> assignedWorkersByBuildingId(@Nullable SettlementSnapshot snapshot) {
        if (snapshot == null) {
            return Map.of();
        }
        Map<UUID, Integer> assignedWorkers = new HashMap<>();
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            assignedWorkers.put(building.buildingUuid(), building.assignedWorkerCount());
        }
        return assignedWorkers;
    }

    @Nullable
    private static VenaterraDepositCandidate closestReliableDeposit(ResourceKey<Level> dimension,
                                                                   BlockPos center,
                                                                   int radius,
                                                                   List<VenaterraDepositCandidate> deposits) {
        int radiusSquared = radius * radius;
        return deposits.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.category() != VenaterraDepositCategory.UNKNOWN_OTHER)
                .filter(candidate -> candidate.confidence() > 0.0D)
                .filter(candidate -> horizontalDistanceSquared(center, candidate.center()) <= radiusSquared)
                .filter(candidate -> candidate.source().dimension() == null || candidate.source().dimension().equals(dimension.location()))
                .min(Comparator.comparingInt((VenaterraDepositCandidate candidate) -> horizontalDistanceSquared(center, candidate.center()))
                        .thenComparing(Comparator.comparingDouble(VenaterraDepositCandidate::confidence).reversed())
                        .thenComparing(Comparator.comparingDouble(VenaterraDepositCandidate::richness).reversed()))
                .orElse(null);
    }

    private static int horizontalDistanceSquared(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return (dx * dx) + (dz * dz);
    }

    private static boolean contains(AABB bounds, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        return x >= bounds.minX && x <= bounds.maxX
                && y >= bounds.minY && y <= bounds.maxY
                && z >= bounds.minZ && z <= bounds.maxZ;
    }

    private static boolean isMineLike(@Nullable String buildingTypeId) {
        if (buildingTypeId == null || buildingTypeId.isBlank()) {
            return false;
        }
        String normalized = buildingTypeId.toLowerCase(Locale.ROOT);
        return normalized.contains("mine") || normalized.contains("mining_area");
    }

    private static BlockPos centerOf(AABB bounds, BlockPos fallback) {
        if (bounds == null) {
            return fallback;
        }
        return BlockPos.containing(
                bounds.minX + ((bounds.maxX - bounds.minX) / 2.0D),
                bounds.minY + ((bounds.maxY - bounds.minY) / 2.0D),
                bounds.minZ + ((bounds.maxZ - bounds.minZ) / 2.0D)
        );
    }

    private static int radiusOf(AABB bounds) {
        if (bounds == null) {
            return SNAPSHOT_MINE_RADIUS;
        }
        double xRadius = (bounds.maxX - bounds.minX) / 2.0D;
        double zRadius = (bounds.maxZ - bounds.minZ) / 2.0D;
        return Math.max(1, (int) Math.ceil(Math.max(xRadius, zRadius)));
    }
}
