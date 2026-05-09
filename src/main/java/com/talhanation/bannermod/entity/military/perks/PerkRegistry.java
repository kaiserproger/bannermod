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
        replaceAll(defaultNodes());
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

    private static List<PerkNode> defaultNodes() {
        return List.of(
                PerkNode.leaf("universal/toughness_i", PerkArchetype.UNIVERSAL, 1,
                        new PerkBonus(PerkStat.MAX_HEALTH, 2.0D)),
                PerkNode.leaf("universal/iron_skin_i", PerkArchetype.UNIVERSAL, 1,
                        new PerkBonus(PerkStat.KNOCKBACK_RESIST, 0.05D)),
                PerkNode.leaf("universal/weapon_training_i", PerkArchetype.UNIVERSAL, 1,
                        new PerkBonus(PerkStat.ATTACK_DAMAGE, 0.25D)),
                PerkNode.leaf("universal/quick_hands_i", PerkArchetype.UNIVERSAL, 1,
                        new PerkBonus(PerkStat.ATTACK_SPEED, 0.10D)),
                PerkNode.leaf("universal/marching_drill_i", PerkArchetype.UNIVERSAL, 1,
                        new PerkBonus(PerkStat.MOVEMENT_SPEED, 0.01D)),
                PerkNode.leaf("universal/steady_aim_i", PerkArchetype.UNIVERSAL, 1,
                        new PerkBonus(PerkStat.RANGED_ACCURACY, 0.05D)),
                PerkNode.leaf("universal/strong_draw_i", PerkArchetype.UNIVERSAL, 1,
                        new PerkBonus(PerkStat.RANGED_VELOCITY, 0.05D)),
                PerkNode.leaf("swordsman/iron_grip_i", PerkArchetype.SWORDSMAN, 1,
                        new PerkBonus(PerkStat.ATTACK_DAMAGE, 0.5D)),
                PerkNode.leaf("bowman/steady_aim_i", PerkArchetype.BOWMAN, 1,
                        new PerkBonus(PerkStat.RANGED_ACCURACY, 0.05D)),
                PerkNode.leaf("crossbowman/heavy_bolts_i", PerkArchetype.CROSSBOWMAN, 1,
                        new PerkBonus(PerkStat.RANGED_VELOCITY, 0.1D)),
                PerkNode.leaf("pikeman/braced_stance_i", PerkArchetype.PIKEMAN, 1,
                        new PerkBonus(PerkStat.KNOCKBACK_RESIST, 0.1D)),
                PerkNode.leaf("cavalry/swift_charge_i", PerkArchetype.CAVALRY, 1,
                        new PerkBonus(PerkStat.MOVEMENT_SPEED, 0.01D))
        );
    }

}
