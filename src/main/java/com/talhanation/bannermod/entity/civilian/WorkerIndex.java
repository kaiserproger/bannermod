package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-level chunk-indexed registry of loaded worker UUIDs. */
public final class WorkerIndex {
    private static final WorkerIndex INSTANCE = new WorkerIndex();

    private final Map<ResourceKey<Level>, Map<ChunkPos, Set<UUID>>> byLevel = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Map<UUID, ChunkPos>> chunksByWorker = new ConcurrentHashMap<>();

    private WorkerIndex() {
    }

    public static WorkerIndex instance() {
        return INSTANCE;
    }

    public void onEntityJoin(Entity entity) {
        if (!(entity instanceof AbstractWorkerEntity)) return;
        if (entity.level() == null || entity.level().isClientSide()) return;
        addOrMove(entity);
    }

    public void onWorkerTick(AbstractWorkerEntity worker) {
        if (worker == null || worker.level() == null || worker.level().isClientSide()) return;
        addOrMove(worker);
    }

    public void onEntityLeave(Entity entity) {
        if (!(entity instanceof AbstractWorkerEntity)) return;
        if (entity.level() == null || entity.level().isClientSide()) return;
        ResourceKey<Level> dimension = entity.level().dimension();
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(dimension);
        if (chunks == null) return;

        ChunkPos oldChunk = null;
        Map<UUID, ChunkPos> workerChunks = chunksByWorker.get(dimension);
        if (workerChunks != null) {
            oldChunk = workerChunks.remove(entity.getUUID());
        }
        removeFromChunk(chunks, oldChunk == null ? entity.chunkPosition() : oldChunk, entity.getUUID());
        for (Set<UUID> other : chunks.values()) {
            other.remove(entity.getUUID());
        }
    }

    private void addOrMove(Entity entity) {
        ResourceKey<Level> dimension = entity.level().dimension();
        ChunkPos chunkPosition = entity.chunkPosition();
        UUID uuid = entity.getUUID();
        Map<ChunkPos, Set<UUID>> chunks = byLevel.computeIfAbsent(dimension, key -> new ConcurrentHashMap<>());
        Map<UUID, ChunkPos> workerChunks = chunksByWorker.computeIfAbsent(dimension, key -> new ConcurrentHashMap<>());
        ChunkPos previousChunk = workerChunks.put(uuid, chunkPosition);
        if (previousChunk != null && !previousChunk.equals(chunkPosition)) {
            removeFromChunk(chunks, previousChunk, uuid);
        }
        chunks.computeIfAbsent(chunkPosition, chunk -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    private static void removeFromChunk(Map<ChunkPos, Set<UUID>> chunks, ChunkPos chunkPosition, UUID uuid) {
        if (chunkPosition == null) return;
        Set<UUID> uuids = chunks.get(chunkPosition);
        if (uuids == null) return;
        uuids.remove(uuid);
        if (uuids.isEmpty()) {
            chunks.remove(chunkPosition);
        }
    }

    public Optional<List<AbstractWorkerEntity>> queryInClaim(ServerLevel level, RecruitsClaim claim) {
        RuntimeProfilingCounters.increment("worker.index.claim_queries");
        if (level == null || claim == null) {
            RuntimeProfilingCounters.increment("worker.index.fallbacks");
            return Optional.empty();
        }
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(level.dimension());
        if (chunks == null) {
            RuntimeProfilingCounters.increment("worker.index.misses");
            RuntimeProfilingCounters.increment("worker.index.fallbacks");
            return Optional.empty();
        }

        List<AbstractWorkerEntity> workers = new ArrayList<>();
        for (ChunkPos chunkPosition : claim.getClaimedChunks()) {
            Set<UUID> uuids = chunks.get(chunkPosition);
            if (uuids == null) {
                RuntimeProfilingCounters.increment("worker.index.misses");
                continue;
            }
            RuntimeProfilingCounters.increment("worker.index.hits");
            for (UUID uuid : uuids) {
                Entity entity = level.getEntity(uuid);
                if (!(entity instanceof AbstractWorkerEntity worker)) continue;
                if (!worker.isAlive() || !claim.containsChunk(worker.chunkPosition())) continue;
                workers.add(worker);
            }
        }
        RuntimeProfilingCounters.add("worker.index.indexed_candidates", workers.size());
        return Optional.of(List.copyOf(workers));
    }

    public int countInChunk(ServerLevel level, ChunkPos chunkPos, boolean aliveOnly) {
        if (level == null || chunkPos == null) return 0;
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(level.dimension());
        if (chunks == null) return 0;
        Set<UUID> uuids = chunks.get(chunkPos);
        if (uuids == null || uuids.isEmpty()) return 0;
        if (!aliveOnly) return uuids.size();
        int count = 0;
        for (UUID uuid : uuids) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractWorkerEntity worker && worker.isAlive()) {
                count++;
            }
        }
        return count;
    }

    public void clear(ResourceKey<Level> dimension) {
        if (dimension == null) return;
        byLevel.remove(dimension);
        chunksByWorker.remove(dimension);
    }

    public void clearAllForTest() {
        byLevel.clear();
        chunksByWorker.clear();
    }
}
