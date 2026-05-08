package com.talhanation.bannermod.entity.military.perks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-actor (recruit, eventually player) perk state: which perks are unlocked
 * and how many skill-points are still available to spend. NBT round-trip is
 * implemented here so {@code RecruitPersistenceBridge} can stay a thin caller.
 *
 * <p>Phase 1 invariants:</p>
 * <ul>
 *   <li>Every mutating method is server-authoritative; the data model contains
 *       no client-trust assumptions.</li>
 *   <li>{@link #unlock(PerkNode)} validates point budget and prerequisites
 *       against {@link PerkRegistry}, returning a typed result so callers can
 *       surface UX feedback later without re-implementing the rules.</li>
 *   <li>{@link #respec()} refunds the full point cost of every unlocked perk
 *       and clears the unlocked set; XP/level live elsewhere and are not
 *       touched.</li>
 * </ul>
 */
public final class PerkProgress implements INBTSerializable<CompoundTag> {
    private static final String NBT_POINTS = "AvailablePoints";
    private static final String NBT_OWNED = "OwnedPerks";

    /** Outcome of an {@link #unlock(PerkNode)} attempt. */
    public enum UnlockResult {
        OK,
        ALREADY_OWNED,
        NOT_ENOUGH_POINTS,
        PREREQUISITES_NOT_MET,
        UNKNOWN_PERK
    }

    private final Set<String> owned = new LinkedHashSet<>();
    private int availablePoints;

    public int getAvailablePoints() {
        return availablePoints;
    }

    public Set<String> getOwnedPerks() {
        return Collections.unmodifiableSet(owned);
    }

    public boolean isOwned(String perkId) {
        return owned.contains(perkId);
    }

    /**
     * Grants {@code amount} skill-points. Negative or zero is a no-op so the
     * level-up hook can call this unconditionally.
     */
    public void grantPoints(int amount) {
        if (amount <= 0) return;
        availablePoints += amount;
    }

    /**
     * Attempts to unlock {@code node}; mutates state only on {@link UnlockResult#OK}.
     * Caller is responsible for archetype-matching the node against the actor.
     */
    public UnlockResult unlock(PerkNode node) {
        if (node == null || !PerkRegistry.isKnown(node.id())) return UnlockResult.UNKNOWN_PERK;
        if (owned.contains(node.id())) return UnlockResult.ALREADY_OWNED;
        if (!node.prerequisitesMet(owned)) return UnlockResult.PREREQUISITES_NOT_MET;
        if (availablePoints < node.pointCost()) return UnlockResult.NOT_ENOUGH_POINTS;
        availablePoints -= node.pointCost();
        owned.add(node.id());
        return UnlockResult.OK;
    }

    /**
     * Clears every unlocked perk and refunds their point cost. Returns the
     * number of points refunded so UI/log code can render a summary later.
     */
    public int respec() {
        int refund = 0;
        for (String id : owned) {
            PerkNode node = PerkRegistry.get(id).orElse(null);
            if (node != null) refund += node.pointCost();
        }
        owned.clear();
        availablePoints += refund;
        return refund;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_POINTS, availablePoints);
        ListTag list = new ListTag();
        for (String id : owned) {
            list.add(StringTag.valueOf(id));
        }
        tag.put(NBT_OWNED, list);
        return tag;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return toNbt();
    }

    public void fromNbt(CompoundTag tag) {
        owned.clear();
        availablePoints = 0;
        if (tag == null) return;
        availablePoints = tag.getInt(NBT_POINTS);
        if (tag.contains(NBT_OWNED, Tag.TAG_LIST)) {
            ListTag list = tag.getList(NBT_OWNED, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String id = list.getString(i);
                if (PerkRegistry.isKnown(id)) {
                    owned.add(id);
                }
                // Unknown ids are dropped silently — ids removed by a later mod
                // update must not corrupt the actor's save.
            }
        }
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        fromNbt(nbt);
    }
}
