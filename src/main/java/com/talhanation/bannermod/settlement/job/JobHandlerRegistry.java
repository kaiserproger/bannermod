package com.talhanation.bannermod.settlement.job;

import com.talhanation.bannermod.settlement.SettlementJobHandlerSeed;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory catalogue of {@link JobHandler}s, keyed by both
 * {@link SettlementJobHandlerSeed} and {@link ResourceLocation} id.
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li>The registry is not thread-safe. Callers (typically the server tick loop or bootstrap
 *       code) are expected to register handlers at construction time and then treat the
 *       registry as read-mostly.</li>
 *   <li>Registering a second handler for the same {@link SettlementJobHandlerSeed}
 *       replaces the previous seed binding (<em>last registration wins</em>). Both handlers
 *       remain reachable by their unique {@link ResourceLocation} id, so the older handler is
 *       not evicted from the id lookup unless its id is re-used.</li>
 *   <li>{@link #all()} returns handlers in insertion order, with duplicates removed.</li>
 * </ul>
 */
public final class JobHandlerRegistry {

    private final Map<ResourceLocation, JobHandler> byId = new LinkedHashMap<>();
    private final Map<SettlementJobHandlerSeed, JobHandler> bySeed =
            new EnumMap<>(SettlementJobHandlerSeed.class);

    /** Build a registry pre-populated with the built-in handlers declared by this slice. */
    public static JobHandlerRegistry defaults() {
        JobHandlerRegistry registry = new JobHandlerRegistry();
        registry.register(new HarvestJobHandler());
        registry.register(new BuildJobHandler());
        return registry;
    }

    public void register(JobHandler handler) {
        Objects.requireNonNull(handler, "handler");
        ResourceLocation id = Objects.requireNonNull(handler.id(), "handler.id()");
        SettlementJobHandlerSeed seed = Objects.requireNonNull(handler.handles(), "handler.handles()");
        byId.put(id, handler);
        bySeed.put(seed, handler);
    }

    public Optional<JobHandler> lookup(SettlementJobHandlerSeed seed) {
        if (seed == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySeed.get(seed));
    }

    public Optional<JobHandler> lookupById(ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    /** Snapshot of all registered handlers in insertion order, de-duplicated by identity. */
    public List<JobHandler> all() {
        return List.copyOf(byId.values());
    }

    public void clear() {
        byId.clear();
        bySeed.clear();
    }

    public int size() {
        return byId.size();
    }

    /** Read-only view of the seed-to-handler bindings, primarily for diagnostics. */
    public Map<SettlementJobHandlerSeed, JobHandler> seedBindings() {
        return Collections.unmodifiableMap(bySeed);
    }
}
