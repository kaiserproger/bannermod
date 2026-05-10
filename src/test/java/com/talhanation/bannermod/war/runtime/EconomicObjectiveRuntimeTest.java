package com.talhanation.bannermod.war.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicObjectiveRuntimeTest {
    @Test
    void mineDisputeMarksMineContestedUntilResolved() {
        EconomicObjectiveRuntime runtime = new EconomicObjectiveRuntime();
        UUID claimUuid = UUID.randomUUID();
        UUID mineSiteId = UUID.randomUUID();

        EconomicObjectiveRecord objective = runtime.createMineDispute(UUID.randomUUID(), null, claimUuid, mineSiteId, 10L, 40L);

        assertEquals(EconomicObjectiveState.NORMAL, runtime.stateForMine(claimUuid, mineSiteId, 9L));
        assertEquals(EconomicObjectiveState.CONTESTED, runtime.stateForMine(claimUuid, mineSiteId, 20L));
        assertTrue(runtime.resolve(objective.id(), 25L));
        assertEquals(EconomicObjectiveState.NORMAL, runtime.stateForMine(claimUuid, mineSiteId, 26L));
    }

    @Test
    void routeAndStorageBlockadesMarkSupplyBlockedUntilExpiry() {
        EconomicObjectiveRuntime runtime = new EconomicObjectiveRuntime();
        UUID claimUuid = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID storageId = UUID.randomUUID();

        runtime.createBlockade(null, UUID.randomUUID(), claimUuid, EconomicObjectiveTargetKind.ROUTE, routeId, 10L, 30L);
        runtime.createBlockade(null, UUID.randomUUID(), claimUuid, EconomicObjectiveTargetKind.STORAGE, storageId, 10L, 30L);

        assertEquals(EconomicObjectiveState.BLOCKED, runtime.stateForRoute(claimUuid, routeId, 20L));
        assertEquals(EconomicObjectiveState.BLOCKED, runtime.stateForStorage(claimUuid, storageId, 20L));
        assertEquals(EconomicObjectiveState.NORMAL, runtime.stateForRoute(claimUuid, routeId, 30L));
        assertEquals(2, runtime.all().size());
        assertEquals(2, runtime.pruneExpired(30L));
        assertEquals(0, runtime.all().size());
    }

    @Test
    void raidStateSurvivesPersistenceRoundTrip() {
        EconomicObjectiveRuntime runtime = new EconomicObjectiveRuntime();
        UUID claimUuid = UUID.randomUUID();
        UUID storageId = UUID.randomUUID();

        runtime.createRaid(UUID.randomUUID(), null, claimUuid, EconomicObjectiveTargetKind.STORAGE, storageId, 1L, 100L);
        EconomicObjectiveRuntime loaded = EconomicObjectiveRuntime.fromTag(runtime.toTag());

        assertEquals(EconomicObjectiveState.RAIDED, loaded.stateForStorage(claimUuid, storageId, 50L));
        assertFalse(loaded.all().isEmpty());
    }
}
