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

class BannerModSettlementManagerTest {

    @Test
    void managerRoundTripsResidentAndBuildingSeedDataByClaimUuid() {
        RecruitsClaim claim = claim(new ChunkPos(8, 4), "blueguild");
        UUID workerUuid = UUID.randomUUID();
        UUID workAreaUuid = UUID.randomUUID();

        BannerModSettlementSnapshot original = new BannerModSettlementSnapshot(
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
                new BannerModSettlementStockpileSummary(1, 2, 54, 1, 0, List.of("farmers", "merchants")),
                new BannerModSettlementMarketState(
                        1,
                        1,
                        27,
                        9,
                        1,
                        1,
                        List.of(new BannerModSettlementMarketRecord(workAreaUuid, "Harbor Square", true, 27, 9)),
                        List.of(new BannerModSettlementSellerDispatchRecord(workerUuid, workAreaUuid, "Harbor Square", BannerModSettlementSellerDispatchState.READY))
                ),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("food", 1),
                        new BannerModSettlementDesiredGoodSnapshot("market_goods", 1),
                        new BannerModSettlementDesiredGoodSnapshot("storage_type:merchants", 1)
                )),
                new BannerModSettlementProjectCandidateSnapshot(
                        "storage_foundation",
                        BannerModSettlementBuildingProfileSeed.STORAGE,
                        4,
                        true,
                        true,
                        List.of("storage_missing", "goods_pressure", "market_access_present")
                ),
                new BannerModSettlementTradeRouteHandoffSnapshot(
                        1,
                        1,
                        1,
                        0,
                        1,
                        16,
                        List.of(
                                new BannerModSettlementDesiredGoodSnapshot("food", 1),
                                new BannerModSettlementDesiredGoodSnapshot("market_goods", 1),
                                new BannerModSettlementDesiredGoodSnapshot("storage_type:merchants", 1)
                        ),
                        List.of(new BannerModSettlementSellerDispatchRecord(workerUuid, workAreaUuid, "Harbor Square", BannerModSettlementSellerDispatchState.READY)),
                        List.of()
                ),
                new BannerModSettlementSupplySignalState(
                        3,
                        1,
                        1,
                        5,
                        List.of(
                                new BannerModSettlementSupplySignal("food", 1, 1, 0, 2),
                                new BannerModSettlementSupplySignal("market_goods", 1, 0, 1, 2),
                                new BannerModSettlementSupplySignal("storage_type:merchants", 1, 1, 0, 1)
                        )
                ),
                List.of(
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.VILLAGER, BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE, BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE, BannerModSettlementResidentServiceContract.notServiceActor(), BannerModSettlementResidentMode.SETTLEMENT_RESIDENT, null, "blueguild", null, BannerModSettlementResidentAssignmentState.NOT_APPLICABLE),
                        new BannerModSettlementResidentRecord(workerUuid, BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, workAreaUuid, "bannermod:storage_area"), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", workAreaUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.GOVERNOR_RECRUIT, BannerModSettlementResidentScheduleSeed.GOVERNING, BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY, BannerModSettlementResidentRuntimeRoleState.GOVERNANCE, BannerModSettlementResidentServiceContract.notServiceActor(), BannerModSettlementResidentMode.SETTLEMENT_RESIDENT, UUID.randomUUID(), "blueguild", null, BannerModSettlementResidentAssignmentState.NOT_APPLICABLE)
                ),
                List.of(
                        new BannerModSettlementBuildingRecord(workAreaUuid, "bannermod:storage_area", new BlockPos(12, 64, 12), UUID.randomUUID(), "blueguild", 4, 1, 1, List.of(workerUuid), true, 2, 54, true, false, List.of("farmers", "merchants"))
                )
        );

        BannerModSettlementManager manager = new BannerModSettlementManager();
        manager.putSnapshot(original);

        CompoundTag persisted = manager.save(new CompoundTag(), null);
        BannerModSettlementManager reloaded = BannerModSettlementManager.load(persisted, null);
        BannerModSettlementSnapshot restored = reloaded.getSnapshot(claim.getUUID());

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

        BannerModSettlementManager manager = new BannerModSettlementManager();
        manager.putSnapshot(BannerModSettlementSnapshot.create(keptClaim.getUUID(), keptClaim.getCenter(), ownerKey(keptClaim)));
        manager.putSnapshot(BannerModSettlementSnapshot.create(staleClaim.getUUID(), staleClaim.getCenter(), ownerKey(staleClaim)));

        manager.pruneMissingClaims(Set.of(keptClaim.getUUID()));

        assertNotNull(manager.getSnapshot(keptClaim.getUUID()));
        assertNull(manager.getSnapshot(staleClaim.getUUID()));
    }

    @Test
    void putSnapshotDoesNotMarkSavedDataDirtyWhenSnapshotIsUnchanged() {
        RecruitsClaim claim = claim(new ChunkPos(4, 4), "blueguild");
        BannerModSettlementSnapshot snapshot = BannerModSettlementSnapshot.create(claim.getUUID(), claim.getCenter(), ownerKey(claim));
        BannerModSettlementManager manager = new BannerModSettlementManager();
        manager.putSnapshot(snapshot);
        BannerModSettlementManager reloaded = BannerModSettlementManager.load(manager.save(new CompoundTag(), null), null);

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
