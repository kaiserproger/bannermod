package com.talhanation.bannermod.settlement.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementBootstrapServiceTest {
    @Test
    void starterWorkerReadinessMessageNamesReadyAndWaitingJobs() {
        String message = SettlementBootstrapService.starterWorkerReadinessMessage(4, 4);

        assertTrue(message.contains("Starter households seeded: 4 residents"));
        assertTrue(message.contains("Adult free citizens can fill vacancies"));
        assertTrue(message.contains("Starter workers wait for player-marked or validated work areas"));
        assertTrue(message.contains("fort founding no longer auto-ploughs a field"));
        assertTrue(message.contains("farmer needs a crop area"));
        assertTrue(message.contains("miner needs a mine"));
        assertTrue(message.contains("lumberjack needs a lumber camp"));
        assertTrue(message.contains("builder needs an architect workshop/build area"));
        assertTrue(message.contains("If vacancies remain empty"));
    }
}
