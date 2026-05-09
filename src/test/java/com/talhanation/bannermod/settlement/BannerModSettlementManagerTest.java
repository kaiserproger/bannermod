package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettlementManagerTest {

    @Test
    void managerRoundTripsResidentAndBuildingSeedDataByClaimUuid() {
        RecruitsClaim claim = claim(new ChunkPos(8, 4), "blueguild");
        UUID workerUuid = UUID.randomUUID();
        UUID workAreaUuid = UUID.randomUUID();

        SettlementSnapshot original = new SettlementSnapshot(
                claim.getUUID(),
                claim.getCenter().x,
                claim.getCenter().z,
                ownerKey(claim),
                240L,
                4,
                1,
                1,
                1,
                0,
                0,
                new SettlementStockpileSummary(1, 2, 54, 1, 0, List.of("farmers", "merchants")),
                new SettlementMarketState(
                        1,
                        1,
                        27,
                        9,
                        1,
                        1,
                        List.of(new SettlementMarketRecord(workAreaUuid, "Harbor Square", true, 27, 9)),
                        List.of(new SettlementSellerDispatchRecord(workerUuid, workAreaUuid, "Harbor Square", SettlementSellerDispatchState.READY))
                ),
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("food", 1),
                        new SettlementDesiredGoodSnapshot("market_goods", 1),
                        new SettlementDesiredGoodSnapshot("storage_type:merchants", 1)
                )),
                new SettlementProjectCandidateSnapshot(
                        "storage_foundation",
                        SettlementBuildingProfileSeed.STORAGE,
                        4,
                        true,
                        true,
                        List.of("storage_missing", "goods_pressure", "market_access_present")
                ),
                new SettlementTradeRouteHandoffSnapshot(
                        1,
                        1,
                        1,
                        0,
                        1,
                        16,
                        List.of(
                                new SettlementDesiredGoodSnapshot("food", 1),
                                new SettlementDesiredGoodSnapshot("market_goods", 1),
                                new SettlementDesiredGoodSnapshot("storage_type:merchants", 1)
                        ),
                        List.of(new SettlementSellerDispatchRecord(workerUuid, workAreaUuid, "Harbor Square", SettlementSellerDispatchState.READY)),
                        List.of()
                ),
                new SettlementSupplySignalState(
                        3,
                        1,
                        1,
                        5,
                        List.of(
                                new SettlementSupplySignal("food", 1, 1, 0, 2),
                                new SettlementSupplySignal("market_goods", 1, 0, 1, 2),
                                new SettlementSupplySignal("storage_type:merchants", 1, 1, 0, 1)
                        )
                ),
                List.of(
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.VILLAGER, SettlementResidentScheduleSeed.SETTLEMENT_IDLE, SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, SettlementResidentRuntimeRoleState.VILLAGE_LIFE, SettlementResidentServiceContract.notServiceActor(), SettlementResidentMode.SETTLEMENT_RESIDENT, null, "blueguild", null, SettlementResidentAssignmentState.NOT_APPLICABLE),
                        new SettlementResidentRecord(workerUuid, SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, workAreaUuid, "bannermod:storage_area"), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", workAreaUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.GOVERNOR_RECRUIT, SettlementResidentScheduleSeed.GOVERNING, SettlementResidentScheduleWindowSeed.CIVIC_DAY, SettlementResidentRuntimeRoleState.GOVERNANCE, SettlementResidentServiceContract.notServiceActor(), SettlementResidentMode.SETTLEMENT_RESIDENT, UUID.randomUUID(), "blueguild", null, SettlementResidentAssignmentState.NOT_APPLICABLE)
                ),
                List.of(
                        new SettlementBuildingRecord(workAreaUuid, "bannermod:storage_area", new BlockPos(12, 64, 12), UUID.randomUUID(), "blueguild", 4, 1, 1, List.of(workerUuid), true, 2, 54, true, false, List.of("farmers", "merchants"))
                )
        );

        SettlementManager manager = new SettlementManager();
        manager.putSnapshot(original);

        CompoundTag persisted = manager.save(new CompoundTag(), null);
        SettlementManager reloaded = SettlementManager.load(persisted, null);
        SettlementSnapshot restored = reloaded.getSnapshot(claim.getUUID());

        assertNotNull(restored);
        assertEquals(claim.getUUID(), restored.claimUuid());
        assertEquals(original.settlementFactionId(), restored.settlementFactionId());
        assertEquals(original.lastRefreshedTick(), restored.lastRefreshedTick());
        assertEquals(original.residentCapacity(), restored.residentCapacity());
        assertEquals(original.workplaceCapacity(), restored.workplaceCapacity());
        assertEquals(original.assignedWorkerCount(), restored.assignedWorkerCount());
        assertEquals(original.assignedResidentCount(), restored.assignedResidentCount());
        assertEquals(original.unassignedWorkerCount(), restored.unassignedWorkerCount());
        assertEquals(original.missingWorkAreaAssignmentCount(), restored.missingWorkAreaAssignmentCount());
        assertEquals(original.stockpileSummary(), restored.stockpileSummary());
        assertEquals(original.marketState(), restored.marketState());
        assertEquals(original.desiredGoodsSnapshot(), restored.desiredGoodsSnapshot());
        assertEquals(original.projectCandidateSnapshot(), restored.projectCandidateSnapshot());
        assertEquals(original.tradeRouteHandoffSnapshot(), restored.tradeRouteHandoffSnapshot());
        assertEquals(original.supplySignalState(), restored.supplySignalState());
        assertEquals(original.residents(), restored.residents());
        assertEquals(original.buildings(), restored.buildings());
        assertNull(reloaded.getSnapshot(UUID.randomUUID()));
    }

    @Test
    void pruneMissingClaimsRemovesStaleSnapshots() {
        RecruitsClaim keptClaim = claim(new ChunkPos(2, 2), "blueguild");
        RecruitsClaim staleClaim = claim(new ChunkPos(3, 3), "blueguild");

        SettlementManager manager = new SettlementManager();
        manager.putSnapshot(SettlementSnapshot.create(keptClaim.getUUID(), keptClaim.getCenter(), ownerKey(keptClaim)));
        manager.putSnapshot(SettlementSnapshot.create(staleClaim.getUUID(), staleClaim.getCenter(), ownerKey(staleClaim)));

        manager.pruneMissingClaims(Set.of(keptClaim.getUUID()));

        assertNotNull(manager.getSnapshot(keptClaim.getUUID()));
        assertNull(manager.getSnapshot(staleClaim.getUUID()));
    }

    @Test
    void putSnapshotDoesNotMarkSavedDataDirtyWhenSnapshotIsUnchanged() {
        RecruitsClaim claim = claim(new ChunkPos(4, 4), "blueguild");
        SettlementSnapshot snapshot = SettlementSnapshot.create(claim.getUUID(), claim.getCenter(), ownerKey(claim));
        SettlementManager manager = new SettlementManager();
        manager.putSnapshot(snapshot);
        SettlementManager reloaded = SettlementManager.load(manager.save(new CompoundTag(), null), null);

        reloaded.putSnapshot(snapshot);

        assertFalse(reloaded.isDirty());
    }

    private static RecruitsClaim claim(ChunkPos chunkPos, String factionId) {
        RecruitsClaim claim = new RecruitsClaim(factionId, UUID.nameUUIDFromBytes(factionId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        claim.addChunk(chunkPos);
        claim.setCenter(chunkPos);
        return claim;
    }

    private static String ownerKey(RecruitsClaim claim) {
        return claim.getOwnerPoliticalEntityId().toString();
    }
}
