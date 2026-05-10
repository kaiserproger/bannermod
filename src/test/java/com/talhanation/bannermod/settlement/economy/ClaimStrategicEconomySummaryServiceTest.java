package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.compat.venaterra.VenaterraDepositCategory;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignal;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveRecord;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveState;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveTargetKind;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimStrategicEconomySummaryServiceTest {
    @Test
    void validStaffedBuildingGrantsFullProductionHint() {
        UUID claimUuid = UUID.randomUUID();
        UUID farmUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(building(farmUuid, "bannermod:validated_farm", 2)), 0, 2, 0);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(validated(claimUuid, farmUuid, BuildingType.FARM, BuildingValidationState.VALID)),
                BannerModTreasuryLedgerSnapshot.create(claimUuid, snapshot.anchorChunk(), "blueguild").withDeposit(6, 10L)
        );

        ClaimStrategicEconomySummary.ResourceLine food = line(summary, "food");
        ClaimStrategicEconomySummary.ResourceLine coins = line(summary, "coins");
        assertEquals(4, food.productionHint());
        assertFalse(food.degraded());
        assertFalse(food.unknown());
        assertEquals(6, coins.stockpileHint());
        assertEquals(6, coins.productionHint());
    }

    @Test
    void staleBuildingBindingOnlyGrantsDegradedUnknownCredit() {
        UUID claimUuid = UUID.randomUUID();
        UUID farmUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(building(farmUuid, "bannermod:validated_farm", 2)), 0, 2, 1);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null
        );

        ClaimStrategicEconomySummary.ResourceLine food = line(summary, "food");
        ClaimStrategicEconomySummary.ResourceLine coins = line(summary, "coins");
        assertEquals(1, food.productionHint());
        assertTrue(food.degraded());
        assertTrue(food.unknown());
        assertTrue(coins.unknown());
        assertTrue(summary.degraded());
        assertTrue(summary.unknown());
    }

    @Test
    void invalidValidatedBuildingDoesNotCountAsFullProduction() {
        UUID claimUuid = UUID.randomUUID();
        UUID mineUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(building(mineUuid, "bannermod:validated_mine", 3)), 0, 3, 0);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(validated(claimUuid, mineUuid, BuildingType.MINE, BuildingValidationState.INVALID)),
                BannerModTreasuryLedgerSnapshot.create(claimUuid, snapshot.anchorChunk(), "blueguild")
        );

        ClaimStrategicEconomySummary.ResourceLine iron = line(summary, "iron");
        assertEquals(1, iron.productionHint());
        assertTrue(iron.degraded());
        assertTrue(iron.unknown());
        assertEquals(1, iron.consumptionHint());
    }

    @Test
    void staleMiningAreaRuntimeIdGrantsDegradedUnknownIronCredit() {
        UUID claimUuid = UUID.randomUUID();
        UUID mineUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(building(mineUuid, "bannermod:mining_area", 2)), 0, 2, 1);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null
        );

        ClaimStrategicEconomySummary.ResourceLine iron = line(summary, "iron");
        assertEquals(1, iron.productionHint());
        assertTrue(iron.degraded());
        assertTrue(iron.unknown());
        assertEquals(1, line(summary, "wood").consumptionHint());
    }

    @Test
    void validatedRecordMissingFromSnapshotIsDegradedUnknownHint() {
        UUID claimUuid = UUID.randomUUID();
        UUID lumberUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(), 0, 0, 0);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(validated(claimUuid, lumberUuid, BuildingType.LUMBER_CAMP, BuildingValidationState.VALID)),
                BannerModTreasuryLedgerSnapshot.create(claimUuid, snapshot.anchorChunk(), "blueguild")
        );

        ClaimStrategicEconomySummary.ResourceLine wood = line(summary, "wood");
        assertEquals(1, wood.productionHint());
        assertTrue(wood.degraded());
        assertTrue(wood.unknown());
    }

    @Test
    void strategicMineSitesAddCategoryProductionHints() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(), 0, 0, 0);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null,
                List.of(
                        mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.IRON, 0.75F, false, false),
                        mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.QUARRY_STONE, 0.45F, false, false),
                        mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.PRECIOUS_COIN_VALUE, 1.0F, false, false)
                )
        );

        assertEquals(2, line(summary, "iron").productionHint());
        assertEquals(1, line(summary, "stone").productionHint());
        assertEquals(3, line(summary, "coins").productionHint());
    }

    @Test
    void strategicMineSiteCategoryReplacesGenericMineIronHint() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID mineUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(building(mineUuid, "bannermod:validated_mine", 2)), 0, 2, 0);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(validated(claimUuid, mineUuid, BuildingType.MINE, BuildingValidationState.VALID)),
                null,
                List.of(mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.QUARRY_STONE, 0.75F, false, false))
        );

        assertEquals(0, line(summary, "iron").productionHint());
        assertEquals(2, line(summary, "stone").productionHint());
    }

    @Test
    void unsupportedMineCategoryDoesNotMutateIronLine() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(), 0, 0, 0);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null,
                List.of(mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.UNKNOWN_OTHER, 1.0F, true, true))
        );

        ClaimStrategicEconomySummary.ResourceLine iron = line(summary, "iron");
        assertEquals(0, iron.productionHint());
        assertFalse(iron.degraded());
        assertFalse(iron.unknown());
        assertTrue(summary.degraded());
        assertTrue(summary.unknown());
    }

    @Test
    void removedOrInvalidatedMineSiteStopsContributingOnNextDerivation() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID mineUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(building(mineUuid, "bannermod:validated_mine", 2)), 0, 2, 0);

        ClaimStrategicEconomySummary before = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(validated(claimUuid, mineUuid, BuildingType.MINE, BuildingValidationState.VALID)),
                null,
                List.of(mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.IRON, 0.75F, false, false))
        );
        ClaimStrategicEconomySummary after = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(validated(claimUuid, mineUuid, BuildingType.MINE, BuildingValidationState.INVALID)),
                null,
                List.of()
        );

        assertEquals(2, line(before, "iron").productionHint());
        assertEquals(0, line(after, "iron").productionHint());
    }

    @Test
    void unownedOrOtherClaimMineSiteDoesNotContribute() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(), 0, 0, 0);

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null,
                List.of(
                        mineSite(claimUuid, null, VenaterraDepositCategory.IRON, 1.0F, false, false),
                        mineSite(UUID.randomUUID(), ownerUuid, VenaterraDepositCategory.QUARRY_STONE, 1.0F, false, false)
                )
        );

        assertEquals(0, line(summary, "iron").productionHint());
        assertEquals(0, line(summary, "stone").productionHint());
    }

    @Test
    void unstaffedMineSiteYieldsLessThanStaffedValidMine() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(claimUuid, List.of(), 0, 0, 0);

        ClaimStrategicEconomySummary staffed = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null,
                List.of(mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.IRON, 1.0F, 2, false, false))
        );
        ClaimStrategicEconomySummary unstaffed = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null,
                List.of(mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.IRON, 1.0F, 0, false, false))
        );

        assertEquals(3, line(staffed, "iron").productionHint());
        assertEquals(1, line(unstaffed, "iron").productionHint());
        assertTrue(line(unstaffed, "iron").degraded());
    }

    @Test
    void missingMineMaintenanceInputsDegradeMineYield() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();

        for (String missingGood : List.of("food", "wood", "tools")) {
            SettlementSnapshot snapshot = snapshot(
                    claimUuid,
                    List.of(),
                    0,
                    0,
                    0,
                    List.of(new SettlementSupplySignal(missingGood, 1, 0, 1, 0))
            );

            ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                    snapshot,
                    List.of(),
                    null,
                    List.of(mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.IRON, 1.0F, 2, false, false))
            );

            ClaimStrategicEconomySummary.ResourceLine iron = line(summary, "iron");
            assertEquals(1, iron.productionHint(), missingGood);
            assertTrue(iron.degraded(), missingGood);
        }
    }

    @Test
    void reservedMineDisruptionReducesYieldWithoutRemovingMineRecord() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        SettlementSnapshot snapshot = snapshot(
                claimUuid,
                List.of(),
                0,
                0,
                0,
                List.of(new SettlementSupplySignal("mine_disruption", 0, 0, 0, 1))
        );

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                List.of(),
                null,
                List.of(mineSite(claimUuid, ownerUuid, VenaterraDepositCategory.IRON, 1.0F, 2, false, false))
        );

        ClaimStrategicEconomySummary.ResourceLine iron = line(summary, "iron");
        assertEquals(2, iron.productionHint());
        assertTrue(iron.degraded());
    }

    @Test
    void mineObjectiveMarksMineResourceContestedInReadModel() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID mineSiteId = UUID.randomUUID();
        StrategicMineSite mineSite = mineSite(
                mineSiteId,
                claimUuid,
                ownerUuid,
                VenaterraDepositCategory.IRON,
                1.0F,
                2,
                false,
                false
        );

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot(claimUuid, List.of(), 0, 0, 0),
                List.of(),
                null,
                List.of(mineSite),
                2,
                List.of(objective(EconomicObjectiveType.MINE_DISPUTE, EconomicObjectiveTargetKind.MINE, claimUuid, mineSiteId, 10L, 30L)),
                20L
        );

        assertEquals(2, summary.fortLevel().level());
        assertEquals(EconomicObjectiveState.CONTESTED, line(summary, "iron").objectiveState());
        assertEquals(EconomicObjectiveState.CONTESTED, summary.objectiveState());
    }

    @Test
    void outpostCaptureMarksOnlyClaimAggregateContested() {
        UUID claimUuid = UUID.randomUUID();

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot(claimUuid, List.of(), 0, 0, 0),
                List.of(),
                null,
                List.of(),
                3,
                List.of(objective(EconomicObjectiveType.OUTPOST_CAPTURE, EconomicObjectiveTargetKind.OUTPOST, claimUuid, UUID.randomUUID(), 10L, 30L)),
                20L
        );

        assertEquals(3, summary.fortLevel().level());
        assertEquals(EconomicObjectiveState.CONTESTED, summary.objectiveState());
        assertTrue(summary.resources().stream().allMatch(line -> line.objectiveState() == EconomicObjectiveState.NORMAL));
    }

    @Test
    void missingMineSiteObjectiveMarksOnlyClaimAggregateContested() {
        UUID claimUuid = UUID.randomUUID();

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot(claimUuid, List.of(), 0, 0, 0),
                List.of(),
                null,
                List.of(),
                FortLevelDefinition.MIN_LEVEL,
                List.of(objective(EconomicObjectiveType.MINE_DISPUTE, EconomicObjectiveTargetKind.MINE, claimUuid, UUID.randomUUID(), 10L, 30L)),
                20L
        );

        assertEquals(EconomicObjectiveState.CONTESTED, summary.objectiveState());
        assertTrue(summary.resources().stream().allMatch(line -> line.objectiveState() == EconomicObjectiveState.NORMAL));
    }

    @Test
    void routeObjectiveMarksSupplyBlockedInReadModel() {
        UUID claimUuid = UUID.randomUUID();

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot(claimUuid, List.of(), 0, 0, 0),
                List.of(),
                null,
                List.of(),
                FortLevelDefinition.MIN_LEVEL,
                List.of(objective(EconomicObjectiveType.BLOCKADE, EconomicObjectiveTargetKind.ROUTE, claimUuid, UUID.randomUUID(), 10L, 30L)),
                20L
        );

        assertEquals(EconomicObjectiveState.BLOCKED, summary.objectiveState());
        assertTrue(summary.resources().stream().allMatch(line -> line.objectiveState() == EconomicObjectiveState.BLOCKED));
    }

    @Test
    void expiredObjectiveReturnsReadModelToNormalWithoutRemovingMine() {
        UUID claimUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID mineSiteId = UUID.randomUUID();
        StrategicMineSite mineSite = mineSite(
                mineSiteId,
                claimUuid,
                ownerUuid,
                VenaterraDepositCategory.IRON,
                1.0F,
                2,
                false,
                false
        );

        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot(claimUuid, List.of(), 0, 0, 0),
                List.of(),
                null,
                List.of(mineSite),
                FortLevelDefinition.MIN_LEVEL,
                List.of(objective(EconomicObjectiveType.MINE_DISPUTE, EconomicObjectiveTargetKind.MINE, claimUuid, mineSiteId, 10L, 30L)),
                30L
        );

        assertEquals(3, line(summary, "iron").productionHint());
        assertEquals(EconomicObjectiveState.NORMAL, line(summary, "iron").objectiveState());
        assertEquals(EconomicObjectiveState.NORMAL, summary.objectiveState());
    }

    private static ClaimStrategicEconomySummary.ResourceLine line(ClaimStrategicEconomySummary summary, String resourceId) {
        return summary.resources().stream()
                .filter(line -> line.resourceId().equals(resourceId))
                .findFirst()
                .orElseThrow();
    }

    private static SettlementBuildingRecord building(UUID buildingUuid, String typeId, int assignedWorkers) {
        return new SettlementBuildingRecord(
                buildingUuid,
                typeId,
                new BlockPos(0, 64, 0),
                UUID.randomUUID(),
                "blueguild",
                0,
                1,
                assignedWorkers,
                List.of()
        );
    }

    private static ValidatedBuildingRecord validated(UUID claimUuid,
                                                     UUID buildingUuid,
                                                     BuildingType type,
                                                     BuildingValidationState state) {
        return new ValidatedBuildingRecord(
                buildingUuid,
                claimUuid,
                type,
                Level.OVERWORLD,
                new BlockPos(0, 64, 0),
                List.of(),
                new AABB(0, 64, 0, 4, 68, 4),
                state,
                1,
                100,
                10L,
                20L,
                state == BuildingValidationState.VALID ? 0L : 20L
        );
    }

    private static StrategicMineSite mineSite(UUID claimUuid,
                                              UUID ownerUuid,
                                              VenaterraDepositCategory category,
                                              float richness,
                                              boolean degraded,
                                              boolean unknown) {
        return mineSite(claimUuid, ownerUuid, category, richness, 1, degraded, unknown);
    }

    private static StrategicMineSite mineSite(UUID claimUuid,
                                              UUID ownerUuid,
                                              VenaterraDepositCategory category,
                                              float richness,
                                              int assignedWorkerCount,
                                              boolean degraded,
                                              boolean unknown) {
        return mineSite(
                UUID.randomUUID(),
                claimUuid,
                ownerUuid,
                category,
                richness,
                assignedWorkerCount,
                degraded,
                unknown
        );
    }

    private static StrategicMineSite mineSite(UUID siteId,
                                             UUID claimUuid,
                                             UUID ownerUuid,
                                             VenaterraDepositCategory category,
                                             float richness,
                                             int assignedWorkerCount,
                                             boolean degraded,
                                             boolean unknown) {
        return new StrategicMineSite(
                siteId,
                claimUuid,
                ownerUuid,
                Level.OVERWORLD,
                BlockPos.ZERO,
                8,
                StrategicMineSite.SourceType.VALIDATED_MINE_BUILDING,
                category,
                richness,
                assignedWorkerCount,
                degraded,
                unknown
        );
    }

    private static EconomicObjectiveRecord objective(EconomicObjectiveType type,
                                                     EconomicObjectiveTargetKind targetKind,
                                                     UUID claimUuid,
                                                     UUID strategicObjectId,
                                                     long createdGameTime,
                                                     long expiresGameTime) {
        return new EconomicObjectiveRecord(
                UUID.randomUUID(),
                type,
                targetKind,
                UUID.randomUUID(),
                null,
                claimUuid,
                strategicObjectId,
                createdGameTime,
                expiresGameTime,
                0L
        );
    }

    private static SettlementSnapshot snapshot(UUID claimUuid,
                                               List<SettlementBuildingRecord> buildings,
                                               int assignedResidents,
                                               int assignedWorkers,
                                               int missingAssignments) {
        return snapshot(claimUuid, buildings, assignedResidents, assignedWorkers, missingAssignments, List.of());
    }

    private static SettlementSnapshot snapshot(UUID claimUuid,
                                               List<SettlementBuildingRecord> buildings,
                                               int assignedResidents,
                                               int assignedWorkers,
                                               int missingAssignments,
                                               List<SettlementSupplySignal> supplySignals) {
        return new SettlementSnapshot(
                claimUuid,
                1,
                2,
                "blueguild",
                100L,
                assignedResidents,
                assignedWorkers,
                assignedWorkers,
                assignedResidents,
                0,
                missingAssignments,
                new SettlementStockpileSummary(1, 2, 54, 0, 0, List.of("food", "wood")),
                SettlementMarketState.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                supplySignalState(supplySignals),
                List.of(),
                buildings
        );
    }

    private static SettlementSupplySignalState supplySignalState(List<SettlementSupplySignal> supplySignals) {
        if (supplySignals.isEmpty()) {
            return SettlementSupplySignalState.empty();
        }
        return new SettlementSupplySignalState(
                supplySignals.size(),
                (int) supplySignals.stream().filter(signal -> signal.shortageUnits() > 0).count(),
                supplySignals.stream().mapToInt(SettlementSupplySignal::shortageUnits).sum(),
                supplySignals.stream().mapToInt(SettlementSupplySignal::reservationHintUnits).sum(),
                supplySignals
        );
    }
}
