package com.talhanation.bannermod.governance;

import com.talhanation.bannermod.shared.logistics.BannerModSupplyStatus;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModGovernorHeartbeatTest {

    @Test
    void friendlySettlementProducesLocalTaxAccrualWithoutHostileOrDegradedIncident() {
        BannerModGovernorHeartbeat.HeartbeatReport report = BannerModGovernorHeartbeat.evaluate(new BannerModGovernorHeartbeat.HeartbeatInput(
                BannerModSettlementBinding.Status.FRIENDLY_CLAIM,
                false,
                4,
                2,
                6,
                new BannerModSupplyStatus.WorkerSupplyStatus(false, null, null),
                new BannerModSupplyStatus.RecruitSupplyStatus(
                        BannerModSupplyStatus.RecruitSupplyState.READY,
                        false,
                        false,
                        false,
                        null,
                        BannerModSupplyStatus.armyUpkeepStatus(false, false, 100.0F)
                ),
                100L,
                80L,
                BannerModGovernorSnapshot.create(UUID.randomUUID(), new net.minecraft.world.level.ChunkPos(4, 7), "blueguild")
        ));

        assertTrue(report.taxesDue() > 0);
        assertTrue(report.taxesCollected() > 0);
        assertFalse(report.incidents().contains(BannerModGovernorIncident.HOSTILE_CLAIM));
        assertFalse(report.incidents().contains(BannerModGovernorIncident.DEGRADED_SETTLEMENT));
    }

    @Test
    void friendlySettlementUnderSiegeKeepsTaxObligationVisibleButUnpaid() {
        BannerModGovernorSnapshot previousSnapshot = BannerModGovernorSnapshot.create(UUID.randomUUID(), new net.minecraft.world.level.ChunkPos(4, 7), "blueguild")
                .withHeartbeatReport(90L, 80L, 6, 12, 12, List.of(), List.of());

        BannerModGovernorHeartbeat.HeartbeatReport report = BannerModGovernorHeartbeat.evaluate(new BannerModGovernorHeartbeat.HeartbeatInput(
                BannerModSettlementBinding.Status.FRIENDLY_CLAIM,
                true,
                4,
                2,
                6,
                new BannerModSupplyStatus.WorkerSupplyStatus(false, null, null),
                new BannerModSupplyStatus.RecruitSupplyStatus(
                        BannerModSupplyStatus.RecruitSupplyState.READY,
                        false,
                        false,
                        false,
                        null,
                        BannerModSupplyStatus.armyUpkeepStatus(false, false, 100.0F)
                ),
                100L,
                80L,
                previousSnapshot
        ));

        assertEquals(12, report.taxesDue());
        assertEquals(0, report.taxesCollected());
        assertEquals(80L, report.collectionTick());
        assertTrue(report.incidents().contains(BannerModGovernorIncident.UNDER_SIEGE));
    }

    @Test
    void degradedOrHostileSettlementProducesInstabilityIncidentsAndNoNormalCollection() {
        BannerModGovernorHeartbeat.HeartbeatReport hostile = BannerModGovernorHeartbeat.evaluate(new BannerModGovernorHeartbeat.HeartbeatInput(
                BannerModSettlementBinding.Status.HOSTILE_CLAIM,
                false,
                3,
                1,
                2,
                new BannerModSupplyStatus.WorkerSupplyStatus(false, null, null),
                new BannerModSupplyStatus.RecruitSupplyStatus(
                        BannerModSupplyStatus.RecruitSupplyState.READY,
                        false,
                        false,
                        false,
                        null,
                        BannerModSupplyStatus.armyUpkeepStatus(false, false, 100.0F)
                ),
                120L,
                100L,
                BannerModGovernorSnapshot.create(UUID.randomUUID(), new net.minecraft.world.level.ChunkPos(2, 9), "blueguild")
        ));
        BannerModGovernorHeartbeat.HeartbeatReport degraded = BannerModGovernorHeartbeat.evaluate(new BannerModGovernorHeartbeat.HeartbeatInput(
                BannerModSettlementBinding.Status.DEGRADED_MISMATCH,
                true,
                3,
                1,
                2,
                new BannerModSupplyStatus.WorkerSupplyStatus(false, null, null),
                new BannerModSupplyStatus.RecruitSupplyStatus(
                        BannerModSupplyStatus.RecruitSupplyState.READY,
                        false,
                        false,
                        false,
                        null,
                        BannerModSupplyStatus.armyUpkeepStatus(false, false, 100.0F)
                ),
                120L,
                100L,
                BannerModGovernorSnapshot.create(UUID.randomUUID(), new net.minecraft.world.level.ChunkPos(3, 5), "blueguild")
        ));

        assertEquals(0, hostile.taxesCollected());
        assertEquals(0, degraded.taxesCollected());
        assertTrue(hostile.incidents().contains(BannerModGovernorIncident.HOSTILE_CLAIM));
        assertTrue(degraded.incidents().contains(BannerModGovernorIncident.DEGRADED_SETTLEMENT));
        assertTrue(degraded.incidents().contains(BannerModGovernorIncident.UNDER_SIEGE));
    }

    @Test
    void shortageAndLowDefenseMapToExplicitRecommendationAndIncidentVocabulary() {
        BannerModGovernorHeartbeat.HeartbeatReport report = BannerModGovernorHeartbeat.evaluate(new BannerModGovernorHeartbeat.HeartbeatInput(
                BannerModSettlementBinding.Status.FRIENDLY_CLAIM,
                false,
                5,
                0,
                1,
                new BannerModSupplyStatus.WorkerSupplyStatus(true, "worker_storage_blocked", "blocked"),
                new BannerModSupplyStatus.RecruitSupplyStatus(
                        BannerModSupplyStatus.RecruitSupplyState.NEEDS_FOOD_AND_PAYMENT,
                        true,
                        true,
                        true,
                        "recruit_upkeep_missing_food_and_payment",
                        BannerModSupplyStatus.armyUpkeepStatus(true, true, 0.0F)
                ),
                160L,
                100L,
                BannerModGovernorSnapshot.create(UUID.randomUUID(), new net.minecraft.world.level.ChunkPos(6, 3), "blueguild")
        ));

        assertTrue(report.incidents().contains(BannerModGovernorIncident.WORKER_SHORTAGE));
        assertTrue(report.incidents().contains(BannerModGovernorIncident.SUPPLY_BLOCKED));
        assertTrue(report.incidents().contains(BannerModGovernorIncident.RECRUIT_UPKEEP_BLOCKED));
        assertTrue(report.recommendations().contains(BannerModGovernorRecommendation.INCREASE_GARRISON));
        assertTrue(report.recommendations().contains(BannerModGovernorRecommendation.STRENGTHEN_FORTIFICATIONS));
        assertTrue(report.recommendations().contains(BannerModGovernorRecommendation.RELIEVE_SUPPLY_PRESSURE));

        assertEquals(
                List.of("worker_shortage", "supply_blocked", "recruit_upkeep_blocked"),
                BannerModGovernorHeartbeat.incidentTokens(List.of(
                        BannerModGovernorIncident.WORKER_SHORTAGE,
                        BannerModGovernorIncident.SUPPLY_BLOCKED,
                        BannerModGovernorIncident.RECRUIT_UPKEEP_BLOCKED
                ))
        );
        assertEquals(
                List.of("increase_garrison", "strengthen_fortifications", "relieve_supply_pressure"),
                BannerModGovernorHeartbeat.recommendationTokens(List.of(
                        BannerModGovernorRecommendation.INCREASE_GARRISON,
                        BannerModGovernorRecommendation.STRENGTHEN_FORTIFICATIONS,
                        BannerModGovernorRecommendation.RELIEVE_SUPPLY_PRESSURE
                ))
        );
    }

}
