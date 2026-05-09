package com.talhanation.bannermod.settlement.validation;

import com.talhanation.bannermod.settlement.building.BuildingDefinitionRegistry;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public final class BuildingInvalidationRuntime {
    private BuildingInvalidationRuntime() {
    }

    public static void enqueueByBlockChange(ServerLevel level, BlockPos pos, BuildingInvalidationReason reason) {
        if (level == null || pos == null) {
            return;
        }
        ValidatedBuildingRegistryData registry = ValidatedBuildingRegistryData.get(level);
        BuildingInvalidationQueueData queue = BuildingInvalidationQueueData.get(level);
        List<ValidatedBuildingRecord> intersecting = registry.getIntersecting(new ChunkPos(pos));
        if (intersecting.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        for (ValidatedBuildingRecord record : intersecting) {
            if (!containsChangedBlock(record, pos)) {
                continue;
            }
            queue.enqueue(record.buildingId(), reason, gameTime);
        }
    }

    private static boolean containsChangedBlock(ValidatedBuildingRecord record, BlockPos pos) {
        if (record == null || pos == null) {
            return false;
        }
        if (record.bounds().contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) {
            return true;
        }
        return record.zones().stream().anyMatch(zone -> zone.contains(pos));
    }

    public static BatchResult tickBatch(ServerLevel level, int maxPerTick) {
        if (level == null || maxPerTick <= 0) {
            return new BatchResult(0, 0, 0);
        }
        ValidatedBuildingRegistryData registry = ValidatedBuildingRegistryData.get(level);
        BuildingInvalidationQueueData queue = BuildingInvalidationQueueData.get(level);
        List<BuildingInvalidationQueueData.QueueEntry> batch = queue.drainBatch(maxPerTick);
        if (batch.isEmpty()) {
            return new BatchResult(0, 0, queue.size());
        }

        SettlementBuildingValidator validator = new SettlementBuildingValidator(new BuildingDefinitionRegistry());
        int processed = 0;
        int degraded = 0;
        for (BuildingInvalidationQueueData.QueueEntry entry : batch) {
            ValidatedBuildingRecord record = registry.getById(entry.buildingId());
            if (record == null) {
                processed++;
                continue;
            }
            BuildingValidationResult result = validator.revalidate(level, record);
            BuildingValidationState nextState = result.valid()
                    ? BuildingValidationState.VALID
                    : BuildingInvalidationPolicy.stateForFailedRevalidation(record, entry.reason(), level.getGameTime());
            if (!result.valid()) {
                degraded++;
            }
            registry.applyRevalidationResult(record.buildingId(), result, nextState, level.getGameTime());
            processed++;
        }
        return new BatchResult(processed, degraded, queue.size());
    }

    public record BatchResult(int processed, int degraded, int backlogRemaining) {
    }
}
