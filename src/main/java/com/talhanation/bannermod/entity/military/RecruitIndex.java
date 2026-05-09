package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Minimal server-side index for loaded recruits. Query results are always revalidated. */
public final class RecruitIndex {
    private static final RecruitIndex INSTANCE = new RecruitIndex();

    private final Map<ResourceKey<Level>, LevelIndex> byLevel = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, AtomicLong> versions = new ConcurrentHashMap<>();
    private final Counters counters = new Counters();

    private RecruitIndex() {
    }

    public static RecruitIndex instance() {
        return INSTANCE;
    }

    public void onEntityJoin(Entity entity) {
        if (!(entity instanceof AbstractRecruitEntity recruit)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        levelIndex(level).add(recruit);
        incrementVersion(level.dimension());
    }

    public void onEntityLeave(Entity entity) {
        if (!(entity instanceof AbstractRecruitEntity recruit)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        LevelIndex index = byLevel.get(level.dimension());
        if (index != null) {
            index.remove(recruit);
            incrementVersion(level.dimension());
        }
    }

    public void onOwnerChanged(AbstractRecruitEntity recruit, @Nullable UUID oldOwner, @Nullable UUID newOwner) {
        if (!(recruit.level() instanceof ServerLevel level)) return;
        LevelIndex index = levelIndex(level);
        index.byUuid.put(recruit.getUUID(), recruit.getUUID());
        index.move(index.byOwner, oldOwner, newOwner, recruit.getUUID());
        incrementVersion(level.dimension());
    }

    public void onGroupChanged(AbstractRecruitEntity recruit, @Nullable UUID oldGroup, @Nullable UUID newGroup) {
        if (!(recruit.level() instanceof ServerLevel level)) return;
        LevelIndex index = levelIndex(level);
        index.byUuid.put(recruit.getUUID(), recruit.getUUID());
        index.move(index.byGroup, oldGroup, newGroup, recruit.getUUID());
        incrementVersion(level.dimension());
    }

    @Nullable
    public AbstractRecruitEntity get(ServerLevel level, UUID recruitUuid) {
        if (level == null || recruitUuid == null) return null;
        LevelIndex index = byLevel.get(level.dimension());
        if (index == null || !index.byUuid.containsKey(recruitUuid)) {
            counters.uuidMisses.increment();
            return null;
        }
        Entity entity = level.getEntity(recruitUuid);
        if (entity instanceof AbstractRecruitEntity recruit && recruit.isAlive()) {
            counters.uuidHits.increment();
            return recruit;
        }
        counters.uuidMisses.increment();
        index.removeUuid(recruitUuid);
        return null;
    }

    @Nullable
    public List<AbstractRecruitEntity> all(ServerLevel level, boolean aliveOnly) {
        if (level == null) {
            counters.fallbacks.increment();
            return null;
        }
        LevelIndex index = byLevel.get(level.dimension());
        if (index == null) {
            counters.fallbacks.increment();
            return null;
        }

        List<AbstractRecruitEntity> recruits = new ArrayList<>();
        for (UUID uuid : index.byUuid.keySet()) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof AbstractRecruitEntity recruit)) continue;
            if (aliveOnly && !recruit.isAlive()) continue;
            recruits.add(recruit);
        }
        counters.indexedCandidates.add(recruits.size());
        return recruits;
    }

    @Nullable
    public List<AbstractRecruitEntity> groupMembers(ServerLevel level, UUID groupUuid) {
        if (level == null || groupUuid == null) {
            counters.fallbacks.increment();
            return null;
        }
        LevelIndex index = byLevel.get(level.dimension());
        if (index == null) {
            counters.fallbacks.increment();
            return null;
        }
        Set<UUID> candidates = index.byGroup.get(groupUuid);
        if (candidates == null) {
            return List.of();
        }

        List<AbstractRecruitEntity> recruits = new ArrayList<>();
        for (UUID uuid : candidates) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractRecruitEntity recruit) {
                recruits.add(recruit);
            }
        }
        counters.indexedCandidates.add(recruits.size());
        return recruits;
    }

    @Nullable
    public List<AbstractRecruitEntity> allInRange(Level level, Vec3 center, double radius, boolean aliveOnly) {
        if (!(level instanceof ServerLevel serverLevel) || center == null) {
            counters.fallbacks.increment();
            return null;
        }
        LevelIndex index = byLevel.get(serverLevel.dimension());
        if (index == null) {
            counters.fallbacks.increment();
            return null;
        }

        ChunkPos centerChunk = new ChunkPos(BlockPos.containing(center));
        int chunkRadius = Math.max(1, (int) Math.ceil(radius / 16.0D));
        double radiusSqr = radius * radius;
        List<AbstractRecruitEntity> results = new ArrayList<>();
        // Walk only chunks the radius covers — the chunk-keyed bucket pre-filters to
        // recruits, so we never visit non-recruit entities or recruits outside the box.
        for (int cx = centerChunk.x - chunkRadius; cx <= centerChunk.x + chunkRadius; cx++) {
            for (int cz = centerChunk.z - chunkRadius; cz <= centerChunk.z + chunkRadius; cz++) {
                Set<UUID> bucket = index.byChunk.get(new ChunkPos(cx, cz));
                if (bucket == null) continue;
                for (UUID uuid : bucket) {
                    Entity entity = serverLevel.getEntity(uuid);
                    if (!(entity instanceof AbstractRecruitEntity recruit)) continue;
                    if (aliveOnly && !recruit.isAlive()) continue;
                    if (recruit.distanceToSqr(center) > radiusSqr) continue;
                    results.add(recruit);
                }
            }
        }
        counters.indexedCandidates.add(results.size());
        return results;
    }

    @Nullable
    public List<AbstractRecruitEntity> allInBox(Level level, AABB box, boolean aliveOnly) {
        if (!(level instanceof ServerLevel serverLevel) || box == null) {
            counters.fallbacks.increment();
            return null;
        }
        LevelIndex index = byLevel.get(serverLevel.dimension());
        if (index == null) {
            counters.fallbacks.increment();
            return null;
        }

        int minCx = (int) Math.floor(box.minX) >> 4;
        int maxCx = (int) Math.floor(box.maxX) >> 4;
        int minCz = (int) Math.floor(box.minZ) >> 4;
        int maxCz = (int) Math.floor(box.maxZ) >> 4;
        List<AbstractRecruitEntity> results = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Set<UUID> bucket = index.byChunk.get(new ChunkPos(cx, cz));
                if (bucket == null) continue;
                for (UUID uuid : bucket) {
                    Entity entity = serverLevel.getEntity(uuid);
                    if (!(entity instanceof AbstractRecruitEntity recruit)) continue;
                    if (aliveOnly && !recruit.isAlive()) continue;
                    if (!recruit.getBoundingBox().intersects(box)) continue;
                    results.add(recruit);
                }
            }
        }
        counters.indexedCandidates.add(results.size());
        return results;
    }

    public Optional<List<AbstractRecruitEntity>> queryInClaim(ServerLevel level, RecruitsClaim claim) {
        if (level == null || claim == null) return Optional.empty();
        LevelIndex index = byLevel.get(level.dimension());
        if (index == null) {
            counters.fallbacks.increment();
            return Optional.empty();
        }

        List<AbstractRecruitEntity> recruits = new ArrayList<>();
        for (UUID uuid : index.byUuid.keySet()) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof AbstractRecruitEntity recruit)) continue;
            if (!recruit.isAlive() || !claim.containsChunk(recruit.chunkPosition())) continue;
            recruits.add(recruit);
        }
        counters.indexedCandidates.add(recruits.size());
        return Optional.of(List.copyOf(recruits));
    }

    @Nullable
    public List<AbstractRecruitEntity> groupInRange(Level level, UUID groupUuid, Vec3 center, double radius) {
        return queryInRange(level, groupUuid, center, radius, true, true);
    }

    @Nullable
    public List<AbstractRecruitEntity> groupMembersInRange(Level level, UUID groupUuid, Vec3 center, double radius) {
        return queryInRange(level, groupUuid, center, radius, true, false);
    }

    @Nullable
    public List<AbstractRecruitEntity> ownerInRange(Level level, UUID ownerUuid, Vec3 center, double radius) {
        return queryInRange(level, ownerUuid, center, radius, false, true);
    }

    /**
     * Count loaded recruits in a specific level owned by {@code ownerUuid}. Used by
     * cross-dimension orphan accounting (FORMATIONDIM-001) to attribute the size of
     * the cohort that a leader's portal transition just stranded.
     */
    public int countOwnedInLevel(ServerLevel level, UUID ownerUuid) {
        if (level == null || ownerUuid == null) return 0;
        LevelIndex index = byLevel.get(level.dimension());
        if (index == null) return 0;
        Set<UUID> candidates = index.byOwner.get(ownerUuid);
        if (candidates == null || candidates.isEmpty()) return 0;
        int count = 0;
        for (UUID uuid : candidates) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractRecruitEntity recruit && recruit.isAlive()) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private List<AbstractRecruitEntity> queryInRange(Level level, UUID key, Vec3 center, double radius, boolean groupQuery, boolean aliveOnly) {
        if (!(level instanceof ServerLevel serverLevel) || key == null || center == null) {
            counters.fallbacks.increment();
            return null;
        }

        LevelIndex index = byLevel.get(serverLevel.dimension());
        if (index == null) {
            counters.fallbacks.increment();
            return null;
        }

        if (groupQuery) {
            counters.groupQueries.increment();
        }
        Set<UUID> candidates = groupQuery ? index.byGroup.get(key) : index.byOwner.get(key);
        if (candidates == null) {
            return List.of();
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        ChunkPos centerChunk = new ChunkPos(BlockPos.containing(center));
        int chunkRadius = Math.max(1, (int) Math.ceil(radius / 16.0D));
        double radiusSqr = radius * radius;
        List<AbstractRecruitEntity> results = new ArrayList<>();
        for (UUID uuid : candidates) {
            Entity entity = serverLevel.getEntity(uuid);
            if (!(entity instanceof AbstractRecruitEntity recruit)) continue;
            if (aliveOnly && !recruit.isAlive()) continue;
            ChunkPos recruitChunk = recruit.chunkPosition();
            if (Math.abs(recruitChunk.x - centerChunk.x) > chunkRadius || Math.abs(recruitChunk.z - centerChunk.z) > chunkRadius) continue;
            if (recruit.distanceToSqr(center) > radiusSqr) continue;
            results.add(recruit);
        }
        counters.indexedCandidates.add(results.size());
        return results;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                counters.uuidHits.sum(),
                counters.uuidMisses.sum(),
                counters.groupQueries.sum(),
                counters.indexedCandidates.sum(),
                counters.fallbacks.sum()
        );
    }

    public long version(ServerLevel level) {
        if (level == null) return 0L;
        AtomicLong version = versions.get(level.dimension());
        return version == null ? 0L : version.get();
    }

    public int countInChunk(ServerLevel level, ChunkPos chunkPos, boolean aliveOnly) {
        if (level == null || chunkPos == null) return 0;
        LevelIndex index = byLevel.get(level.dimension());
        if (index == null) return 0;
        Set<UUID> uuids = index.byChunk.get(chunkPos);
        if (uuids == null || uuids.isEmpty()) return 0;
        if (!aliveOnly) return uuids.size();
        int count = 0;
        for (UUID uuid : uuids) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractRecruitEntity recruit && recruit.isAlive()) {
                count++;
            }
        }
        return count;
    }

    public void resetCounters() {
        counters.reset();
    }

    public void clear(ResourceKey<Level> dimension) {
        if (dimension != null) {
            byLevel.remove(dimension);
            incrementVersion(dimension);
        }
    }

    private LevelIndex levelIndex(ServerLevel level) {
        return byLevel.computeIfAbsent(level.dimension(), ignored -> new LevelIndex());
    }

    private void incrementVersion(ResourceKey<Level> dimension) {
        if (dimension != null) {
            versions.computeIfAbsent(dimension, ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    public record Snapshot(long uuidHits, long uuidMisses, long groupQueries, long indexedCandidates, long fallbacks) {
    }

    private static final class Counters {
        private final LongAdder uuidHits = new LongAdder();
        private final LongAdder uuidMisses = new LongAdder();
        private final LongAdder groupQueries = new LongAdder();
        private final LongAdder indexedCandidates = new LongAdder();
        private final LongAdder fallbacks = new LongAdder();

        private void reset() {
            uuidHits.reset();
            uuidMisses.reset();
            groupQueries.reset();
            indexedCandidates.reset();
            fallbacks.reset();
        }
    }

    /**
     * Per-tick hook so the chunk-keyed bucket follows recruits as they cross chunk
     * borders. Vanilla does not raise an "entity changed chunk" event, so we resync
     * lazily on the recruit tick: if {@link #lastKnownChunk} disagrees with the
     * recruit's current chunk position we re-bucket. The cost is one map lookup per
     * recruit tick — cheap and self-healing if chunks unload mid-flight.
     */
    public void onRecruitTick(AbstractRecruitEntity recruit) {
        if (recruit == null) return;
        if (!(recruit.level() instanceof ServerLevel level)) return;
        if (levelIndex(level).touch(recruit)) {
            incrementVersion(level.dimension());
        }
    }

    private static final class LevelIndex {
        private final Map<UUID, UUID> byUuid = new ConcurrentHashMap<>();
        private final Map<UUID, Set<UUID>> byOwner = new ConcurrentHashMap<>();
        private final Map<UUID, Set<UUID>> byGroup = new ConcurrentHashMap<>();
        /**
         * Chunk-keyed recruit bucket. Lets {@link #allInBox} and {@link #allInRange}
         * iterate only the chunks intersecting the query AABB, with a recruit-only
         * candidate set inside each — strictly fewer iterations than vanilla's
         * {@code getEntitiesOfClass(AbstractRecruitEntity.class, box)} when chunks
         * also contain non-recruit entities (mobs / items / players), and equal when
         * they don't.
         */
        private final Map<ChunkPos, Set<UUID>> byChunk = new ConcurrentHashMap<>();
        /** Last chunk we bucketed each recruit into; used by {@link #touch} to detect crossings. */
        private final Map<UUID, ChunkPos> lastKnownChunk = new ConcurrentHashMap<>();

        private void add(AbstractRecruitEntity recruit) {
            UUID uuid = recruit.getUUID();
            byUuid.put(uuid, uuid);
            addTo(byOwner, recruit.getOwnerUUID(), uuid);
            addTo(byGroup, recruit.getGroup(), uuid);
            ChunkPos cp = recruit.chunkPosition();
            addToChunk(cp, uuid);
            lastKnownChunk.put(uuid, cp);
        }

        private void remove(AbstractRecruitEntity recruit) {
            UUID uuid = recruit.getUUID();
            removeUuid(uuid);
            removeFrom(byOwner, recruit.getOwnerUUID(), uuid);
            removeFrom(byGroup, recruit.getGroup(), uuid);
            ChunkPos cp = lastKnownChunk.remove(uuid);
            if (cp == null) {
                cp = recruit.chunkPosition();
            }
            removeFromChunk(cp, uuid);
        }

        private boolean touch(AbstractRecruitEntity recruit) {
            UUID uuid = recruit.getUUID();
            if (!byUuid.containsKey(uuid)) {
                // Index missed onJoin (e.g. captured before subscriber registered) — heal lazily.
                add(recruit);
                return true;
            }
            ChunkPos current = recruit.chunkPosition();
            ChunkPos previous = lastKnownChunk.get(uuid);
            if (previous != null && previous.equals(current)) {
                return false;
            }
            if (previous != null) {
                removeFromChunk(previous, uuid);
            }
            addToChunk(current, uuid);
            lastKnownChunk.put(uuid, current);
            return true;
        }

        private void removeUuid(UUID uuid) {
            byUuid.remove(uuid);
            for (Set<UUID> uuids : byOwner.values()) {
                uuids.remove(uuid);
            }
            for (Set<UUID> uuids : byGroup.values()) {
                uuids.remove(uuid);
            }
        }

        private void move(Map<UUID, Set<UUID>> index, @Nullable UUID oldKey, @Nullable UUID newKey, UUID recruitUuid) {
            removeFrom(index, oldKey, recruitUuid);
            addTo(index, newKey, recruitUuid);
        }

        private void addTo(Map<UUID, Set<UUID>> index, @Nullable UUID key, UUID recruitUuid) {
            if (key == null) return;
            index.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(recruitUuid);
        }

        private void removeFrom(Map<UUID, Set<UUID>> index, @Nullable UUID key, UUID recruitUuid) {
            if (key == null) return;
            Set<UUID> uuids = index.get(key);
            if (uuids == null) return;
            uuids.remove(recruitUuid);
            if (uuids.isEmpty()) {
                index.remove(key);
            }
        }

        private void addToChunk(ChunkPos cp, UUID recruitUuid) {
            if (cp == null) return;
            byChunk.computeIfAbsent(cp, ignored -> ConcurrentHashMap.newKeySet()).add(recruitUuid);
        }

        private void removeFromChunk(ChunkPos cp, UUID recruitUuid) {
            if (cp == null) return;
            Set<UUID> uuids = byChunk.get(cp);
            if (uuids == null) return;
            uuids.remove(recruitUuid);
            if (uuids.isEmpty()) {
                byChunk.remove(cp);
            }
        }
    }
}
