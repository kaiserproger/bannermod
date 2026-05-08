package com.talhanation.bannermod.entity.civilian.workarea;

import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level chunk-indexed registry of work-area entity UUIDs.
 *
 * <p>Replaces the common {@code level.getEntitiesOfClass(AbstractWorkAreaEntity.class, ...)}
 * pattern in worker AI. With hundreds of work areas per world, broad-class world queries
 * scan every entity in every chunk the AABB touches; this index narrows lookup to the
 * chunks in range and then does an O(1) UUID resolve against the server level.</p>
 *
 * <p>Populated by {@link WorkAreaIndexEvents} on
 * {@code EntityJoinLevelEvent} / {@code EntityLeaveLevelEvent}. Stale entries are fine:
 * {@link #queryInRange} rechecks liveness and distance before returning. If a work area
 * moves via the GUI move-buttons its chunk position may change; the index auto-corrects
 * whenever the next event cycle re-fires.</p>
 *
 * <p>Thread-safety: index maps use {@link ConcurrentHashMap} so server-bus events and the
 * server tick loop never corrupt each other even though Minecraft normally serialises
 * them.</p>
 */
public final class WorkAreaIndex {
    private static final WorkAreaIndex INSTANCE = new WorkAreaIndex();

    private final Map<ResourceKey<Level>, Map<ChunkPos, Set<UUID>>> byLevel = new ConcurrentHashMap<>();

    private WorkAreaIndex() {
    }

    public static WorkAreaIndex instance() {
        return INSTANCE;
    }

    public void onEntityJoin(Entity entity) {
        if (!(entity instanceof AbstractWorkAreaEntity)) return;
        if (entity.level() == null || entity.level().isClientSide()) return;
        ResourceKey<Level> key = entity.level().dimension();
        // Use the entity's authoritative block position instead of the stale chunkPosition()
        // field: EntityJoinLevelEvent fires before vanilla updates chunkPosition() via the
        // chunk's setLevelCallback, so chunkPosition() can still be the default (0,0) here.
        ChunkPos cp = new ChunkPos(entity.blockPosition());
        byLevel.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(cp, c -> ConcurrentHashMap.newKeySet())
                .add(entity.getUUID());
    }

    public void onEntityLeave(Entity entity) {
        if (!(entity instanceof AbstractWorkAreaEntity)) return;
        if (entity.level() == null || entity.level().isClientSide()) return;
        ResourceKey<Level> key = entity.level().dimension();
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(key);
        if (chunks == null) return;
        ChunkPos cp = entity.chunkPosition();
        Set<UUID> uuids = chunks.get(cp);
        if (uuids != null) {
            uuids.remove(entity.getUUID());
            if (uuids.isEmpty()) {
                chunks.remove(cp);
            }
        }
        // Belt-and-braces: also remove from every other chunk in case the entity moved
        // without firing a leave-join cycle. Usually a no-op.
        for (Set<UUID> other : chunks.values()) {
            other.remove(entity.getUUID());
        }
    }

    /**
     * Query work areas of a specific concrete type within {@code radius} blocks of
     * {@code center}. Returns an empty list on client levels (no server-side index there).
     */
    public <T extends AbstractWorkAreaEntity> List<T> queryInRange(Level level,
                                                                   Vec3 center,
                                                                   double radius,
                                                                   Class<T> type) {
        if (level == null || center == null || type == null) return List.of();
        if (!(level instanceof ServerLevel serverLevel)) {
            // On the client we don't maintain the index, so fall back to the broad scan.
            return fallbackQueryInRange(level, center, radius, type);
        }
        ResourceKey<Level> key = level.dimension();
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(key);
        if (chunks == null || chunks.isEmpty()) {
            return fallbackQueryInRange(level, center, radius, type);
        }
        int chunkRadius = Math.max(1, (int) Math.ceil(radius / 16.0));
        ChunkPos centerChunk = new ChunkPos(BlockPos.containing(center));
        double radiusSqr = radius * radius;

        List<T> results = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos cp = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                Set<UUID> uuids = chunks.get(cp);
                if (uuids == null) continue;
                for (UUID uuid : uuids) {
                    Entity entity = serverLevel.getEntity(uuid);
                    if (!type.isInstance(entity)) continue;
                    if (!entity.isAlive()) continue;
                    if (entity.distanceToSqr(center) > radiusSqr) continue;
                    results.add(type.cast(entity));
                }
            }
        }
        if (results.isEmpty()) {
            return fallbackQueryInRange(level, center, radius, type);
        }
        return results;
    }

    private <T extends AbstractWorkAreaEntity> List<T> fallbackQueryInRange(Level level,
                                                                           Vec3 center,
                                                                           double radius,
                                                                           Class<T> type) {
        AABB aabb = new AABB(center, center).inflate(radius);
        List<T> fallback = level.getEntitiesOfClass(type, aabb);
        RuntimeProfilingCounters.increment("work_area.index.fallback_scans");
        RuntimeProfilingCounters.add("work_area.index.fallback_results", fallback.size());
        return fallback;
    }

    /** Convenience overload — uses an entity's position as the query center. */
    public <T extends AbstractWorkAreaEntity> List<T> queryInRange(Entity near,
                                                                    double radius,
                                                                    Class<T> type) {
        if (near == null) return List.of();
        return queryInRange(near.level(), near.position(), radius, type);
    }

    /** Query live work areas in exact chunk buckets, without applying a distance filter. */
    public <T extends AbstractWorkAreaEntity> List<T> queryInChunks(ServerLevel level,
                                                                    Iterable<ChunkPos> chunkPositions,
                                                                    Class<T> type) {
        if (level == null || chunkPositions == null || type == null) return List.of();
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(level.dimension());
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<T> results = new ArrayList<>();
        for (ChunkPos chunkPosition : chunkPositions) {
            Set<UUID> uuids = chunks.get(chunkPosition);
            if (uuids == null) continue;
            for (UUID uuid : uuids) {
                Entity entity = level.getEntity(uuid);
                if (!type.isInstance(entity)) continue;
                if (!entity.isAlive()) continue;
                results.add(type.cast(entity));
            }
        }
        return results;
    }

    public int countInChunk(ServerLevel level, ChunkPos chunkPos, Class<? extends AbstractWorkAreaEntity> type) {
        if (level == null || chunkPos == null || type == null) return 0;
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(level.dimension());
        if (chunks == null) return 0;
        Set<UUID> uuids = chunks.get(chunkPos);
        if (uuids == null || uuids.isEmpty()) return 0;
        int count = 0;
        for (UUID uuid : uuids) {
            Entity entity = level.getEntity(uuid);
            if (type.isInstance(entity) && entity.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /** Total entries tracked in a given level (diagnostics only). */
    public int sizeFor(ResourceKey<Level> dimension) {
        Map<ChunkPos, Set<UUID>> chunks = byLevel.get(dimension);
        if (chunks == null) return 0;
        int sum = 0;
        for (Set<UUID> set : chunks.values()) {
            sum += set.size();
        }
        return sum;
    }

    /** Drop every entry for a level (e.g. on world unload). */
    public void clear(ResourceKey<Level> dimension) {
        if (dimension == null) return;
        byLevel.remove(dimension);
    }

    /** Visible for tests. */
    public void clearAllForTest() {
        byLevel.clear();
    }
}
