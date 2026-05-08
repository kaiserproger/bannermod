package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementSnapshotTest {

    @Test
    void constructorNormalizesNegativeCountsAndNullSeeds() {
        SettlementSnapshot snapshot = new SettlementSnapshot(
                UUID.randomUUID(),
                4,
                -2,
                "blueguild",
                12L,
                -1,
                -2,
                -3,
                -4,
                -5,
                -6,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(0, snapshot.residentCapacity());
        assertEquals(0, snapshot.workplaceCapacity());
        assertEquals(0, snapshot.assignedWorkerCount());
        assertEquals(0, snapshot.assignedResidentCount());
        assertEquals(0, snapshot.unassignedWorkerCount());
        assertEquals(0, snapshot.missingWorkAreaAssignmentCount());
        assertEquals(SettlementStockpileSummary.empty(), snapshot.stockpileSummary());
        assertEquals(SettlementMarketState.empty(), snapshot.marketState());
        assertEquals(SettlementDesiredGoodsSnapshot.empty(), snapshot.desiredGoodsSnapshot());
        assertEquals(SettlementProjectCandidateSnapshot.empty(), snapshot.projectCandidateSnapshot());
        assertEquals(SettlementTradeRouteHandoffSnapshot.empty(), snapshot.tradeRouteHandoffSnapshot());
        assertEquals(SettlementSupplySignalState.empty(), snapshot.supplySignalState());
        assertTrue(snapshot.residents().isEmpty());
        assertTrue(snapshot.buildings().isEmpty());
    }

    @Test
    void anchorChunkReturnsExpectedChunkPosition() {
        SettlementSnapshot snapshot = SettlementSnapshot.create(UUID.randomUUID(), new ChunkPos(9, -3), "blueguild");

        assertEquals(new ChunkPos(9, -3), snapshot.anchorChunk());
    }

    @Test
    void fromTagFallsBackWhenOptionalFieldsAreMissing() {
        CompoundTag tag = new CompoundTag();
        UUID claimUuid = UUID.randomUUID();
        tag.putUUID("ClaimUuid", claimUuid);
        tag.putInt("AnchorChunkX", 7);
        tag.putInt("AnchorChunkZ", -4);
        tag.putLong("LastRefreshedTick", 33L);
        tag.putInt("ResidentCapacity", 2);
        tag.putInt("WorkplaceCapacity", 1);
        tag.putInt("AssignedWorkerCount", 1);
        tag.putInt("AssignedResidentCount", 1);
        tag.putInt("UnassignedWorkerCount", 0);
        tag.putInt("MissingWorkAreaAssignmentCount", 0);

        SettlementSnapshot snapshot = SettlementSnapshot.fromTag(tag);

        assertEquals(claimUuid, snapshot.claimUuid());
        assertEquals(new ChunkPos(7, -4), snapshot.anchorChunk());
        assertEquals(null, snapshot.settlementFactionId());
        assertEquals(SettlementStockpileSummary.empty(), snapshot.stockpileSummary());
        assertEquals(SettlementMarketState.empty(), snapshot.marketState());
        assertEquals(SettlementDesiredGoodsSnapshot.empty(), snapshot.desiredGoodsSnapshot());
        assertEquals(SettlementProjectCandidateSnapshot.empty(), snapshot.projectCandidateSnapshot());
        assertEquals(SettlementTradeRouteHandoffSnapshot.empty(), snapshot.tradeRouteHandoffSnapshot());
        assertEquals(SettlementSupplySignalState.empty(), snapshot.supplySignalState());
        assertTrue(snapshot.residents().isEmpty());
        assertTrue(snapshot.buildings().isEmpty());
    }

    @Test
    void constructorCopiesResidentAndBuildingListsImmutably() {
        SettlementResidentRecord resident = new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.VILLAGER,
                SettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                SettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                "blueguild",
                null,
                SettlementResidentAssignmentState.NOT_APPLICABLE
        );
        SettlementBuildingRecord building = new SettlementBuildingRecord(
                UUID.randomUUID(),
                "bannermod:storage_area",
                net.minecraft.core.BlockPos.ZERO,
                null,
                "blueguild",
                1,
                0,
                0,
                List.of(),
                false,
                0,
                0,
                false,
                false,
                List.of()
        );

        SettlementSnapshot snapshot = new SettlementSnapshot(
                UUID.randomUUID(),
                0,
                0,
                "blueguild",
                1L,
                1,
                1,
                0,
                0,
                0,
                0,
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(resident),
                List.of(building)
        );

        assertEquals(1, snapshot.residents().size());
        assertEquals(1, snapshot.buildings().size());
    }
}
