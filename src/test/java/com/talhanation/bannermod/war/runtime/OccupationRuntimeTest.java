package com.talhanation.bannermod.war.runtime;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupationRuntimeTest {

    @Test
    void rejectsSelfOccupation() {
        OccupationRuntime runtime = new OccupationRuntime();
        UUID a = UUID.randomUUID();
        Optional<OccupationRecord> placed = runtime.place(
                UUID.randomUUID(), a, a, List.of(new ChunkPos(0, 0)), 0L);
        assertTrue(placed.isEmpty());
    }

    @Test
    void rejectsEmptyChunks() {
        OccupationRuntime runtime = new OccupationRuntime();
        Optional<OccupationRecord> placed = runtime.place(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0L);
        assertTrue(placed.isEmpty());
    }

    @Test
    void filtersByOccupier() {
        OccupationRuntime runtime = new OccupationRuntime();
        UUID occupier = UUID.randomUUID();
        UUID occupied = UUID.randomUUID();
        runtime.place(UUID.randomUUID(), occupier, occupied, List.of(new ChunkPos(0, 0)), 0L);
        runtime.place(UUID.randomUUID(), UUID.randomUUID(), occupied, List.of(new ChunkPos(1, 1)), 0L);
        assertEquals(1, runtime.forOccupier(occupier).size());
        assertEquals(2, runtime.forOccupied(occupied).size());
    }

    @Test
    void roundTripPersistsChunks() {
        OccupationRuntime runtime = new OccupationRuntime();
        UUID warId = UUID.randomUUID();
        runtime.place(warId, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ChunkPos(2, 3), new ChunkPos(4, 5)), 100L);
        OccupationRuntime restored = OccupationRuntime.fromTag(runtime.toTag());
        assertEquals(1, restored.all().size());
        OccupationRecord record = restored.all().iterator().next();
        assertEquals(2, record.chunks().size());
        assertEquals(new ChunkPos(2, 3), record.chunks().get(0));
        assertEquals(100L, record.startedAtGameTime());
    }

    @Test
    void removeDropsRecord() {
        OccupationRuntime runtime = new OccupationRuntime();
        OccupationRecord record = runtime.place(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(new ChunkPos(0, 0)), 0L).orElseThrow();
        assertTrue(runtime.remove(record.id()));
        assertFalse(runtime.remove(record.id()));
        assertEquals(0, runtime.all().size());
    }

    @Test
    void occupiedClaimStateStartsStaysActiveAndClears() {
        OccupationRuntime runtime = new OccupationRuntime();
        UUID occupied = UUID.randomUUID();
        List<ChunkPos> claimChunks = List.of(new ChunkPos(0, 0), new ChunkPos(1, 0));

        assertTrue(runtime.forOccupiedClaim(occupied, claimChunks).isEmpty());

        OccupationRecord record = runtime.place(UUID.randomUUID(), UUID.randomUUID(), occupied,
                List.of(new ChunkPos(1, 0)), 20L).orElseThrow();
        assertEquals(1, runtime.forOccupiedClaim(occupied, claimChunks).size());
        assertEquals(record.id(), runtime.forOccupiedClaim(occupied, claimChunks).iterator().next().id());
        assertEquals(record.id(), runtime.forOccupiedClaimChunk(occupied, new ChunkPos(1, 0)).iterator().next().id());
        assertTrue(runtime.forOccupiedClaimChunk(occupied, new ChunkPos(0, 0)).isEmpty());

        assertTrue(runtime.remove(record.id()));
        assertTrue(runtime.forOccupiedClaim(occupied, claimChunks).isEmpty());
        assertTrue(runtime.forOccupiedClaimChunk(occupied, new ChunkPos(1, 0)).isEmpty());
    }
}
