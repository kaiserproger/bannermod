package com.talhanation.bannermod.entity.military.perks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-owned in-memory perk catalog populated from datapack JSON during reload.
 *
 * <p>Server-authoritative: every {@link PerkNode} lives in the JVM, never on
 * the wire. Identifiers are deliberately stable strings so save data written
 * before SKILLTREE-003 stays valid once richer trees are registered.</p>
 */
public final class PerkRegistry {
    private static final Map<String, PerkNode> BY_ID = new HashMap<>();
    private static final Map<PerkArchetype, List<PerkNode>> BY_ARCHETYPE = new EnumMap<>(PerkArchetype.class);

    static {
        replaceAll(List.of());
    }

    private PerkRegistry() {
    }

    public static synchronized void replaceAll(List<PerkNode> nodes) {
        Map<String, PerkNode> byId = new HashMap<>();
        Map<PerkArchetype, List<PerkNode>> byArchetype = new EnumMap<>(PerkArchetype.class);
        for (PerkArchetype archetype : PerkArchetype.values()) {
            byArchetype.put(archetype, new ArrayList<>());
        }
        for (PerkNode node : nodes) {
            PerkNode existing = byId.putIfAbsent(node.id(), node);
            if (existing != null) {
                throw new IllegalStateException("Duplicate perk id: " + node.id());
            }
            byArchetype.get(node.archetype()).add(node);
        }

        BY_ID.clear();
        BY_ID.putAll(byId);
        BY_ARCHETYPE.clear();
        BY_ARCHETYPE.putAll(byArchetype);
    }

    public static Optional<PerkNode> get(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static List<PerkNode> byArchetype(PerkArchetype archetype) {
        return Collections.unmodifiableList(BY_ARCHETYPE.get(archetype));
    }

    /**
     * Returns true when the id was registered. Persistence uses this on load
     * to silently drop perks removed by a later mod update.
     */
    public static boolean isKnown(String id) {
        return BY_ID.containsKey(id);
    }

}
