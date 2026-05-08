package com.talhanation.bannermod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Cross-dimension safety helper for formation goals.
 *
 * <p>The owner reference for a formation/follow goal lives in the recruit's
 * {@code OwnableEntity} contract, which resolves by UUID against the global player
 * list. Once a player walks through a portal the resolved owner is now in a
 * different {@link Level}, but the recruit is still in the original level. Without
 * a guard, follow / regroup / move-to-leader logic would either path toward stale
 * coordinates from another dimension or repeatedly attempt navigation against an
 * entity that does not exist in the recruit's level — both produce visible
 * gameplay bugs and pathing churn.
 *
 * <p>Goal entry points should call {@link #shouldHoldDueToDimensionMismatch} at the
 * top of {@code canUse()} and {@code tick()}; when the result is {@code true}, the
 * goal must short-circuit to a hold-position behaviour rather than blind-rebind.
 *
 * <p>The dim-check increments the {@code formation.cross_dimension_orphan} runtime
 * profiling counter so we can observe orphan churn from
 * {@link RuntimeProfilingCounters#snapshot()} and the in-game debug command.
 */
public final class FormationDimensionGuard {

    /** Public counter key surfaced in {@link RuntimeProfilingCounters} snapshots. */
    public static final String COUNTER_KEY = "formation.cross_dimension_orphan";

    private FormationDimensionGuard() {
    }

    /**
     * Pure dimension-equality check on two {@link ResourceKey} dimension keys.
     * Returns {@code true} iff the leader cannot be safely followed from the
     * recruit's current dimension (null recruit-key, null leader-key, or
     * mismatched keys). Does NOT touch the counter so unit tests can compose
     * checks freely without leaking state between cases.
     */
    public static boolean dimensionMismatch(@Nullable ResourceKey<Level> recruitDim, @Nullable ResourceKey<Level> leaderDim) {
        if (recruitDim == null || leaderDim == null) {
            return true;
        }
        return !recruitDim.equals(leaderDim);
    }

    /**
     * Pure dimension-equality check on two {@link Level} references. Convenience
     * overload for callsites that already have {@code Level} in hand. Returns
     * {@code true} iff the leader cannot be safely followed (any null, or
     * mismatched dimension keys). Does NOT touch the counter.
     */
    public static boolean leaderInDifferentDimension(@Nullable Level recruitLevel, @Nullable Level leaderLevel) {
        if (recruitLevel == null || leaderLevel == null) {
            return true;
        }
        return dimensionMismatch(recruitLevel.dimension(), leaderLevel.dimension());
    }

    /**
     * Goal-side wrapper. Pass the recruit's level and the resolved leader entity
     * (typically {@code recruit.getOwner()}). When the leader is missing or in a
     * different dimension this method increments the orphan counter and returns
     * {@code true}; callers must then hold position rather than continue
     * leader-bound navigation.
     *
     * <p>Returning {@code true} when the leader is null is intentional: a null
     * leader is observationally identical to a leader in another dimension from
     * the formation goal's perspective (both block follow / regroup behaviours).
     * Goals that already short-circuit on null leader can still call this safely
     * — the counter increment is a useful signal either way.
     */
    public static boolean shouldHoldDueToDimensionMismatch(@Nullable Level recruitLevel, @Nullable LivingEntity leader) {
        if (leader != null && leader.isRemoved()) {
            RuntimeProfilingCounters.increment(COUNTER_KEY);
            return true;
        }
        Level leaderLevel = leader == null ? null : leader.level();
        if (leaderInDifferentDimension(recruitLevel, leaderLevel)) {
            RuntimeProfilingCounters.increment(COUNTER_KEY);
            return true;
        }
        return false;
    }

    /**
     * Increment the cross-dimension orphan counter by an explicit group size.
     * Intended for callbacks like {@code PlayerChangedDimensionEvent} that already
     * know the affected cohort up front: the formation goals will each tick once
     * and increment too, but a one-shot bump on the dimension-change event lets
     * us attribute orphan events to a leader transition rather than per-recruit
     * tick cadence.
     */
    public static void recordOrphanedGroup(int groupSize) {
        if (groupSize <= 0) {
            return;
        }
        RuntimeProfilingCounters.add(COUNTER_KEY, groupSize);
    }

    /** Convenience overload for goal callsites that have the recruit entity in hand. */
    public static boolean shouldHoldDueToDimensionMismatch(@Nullable Entity recruit, @Nullable LivingEntity leader) {
        Level recruitLevel = recruit == null ? null : recruit.level();
        return shouldHoldDueToDimensionMismatch(recruitLevel, leader);
    }

    /**
     * Helper for goals that hold a cached "anchor" / hold position. Returns the
     * supplied anchor unchanged so callers can wire it inline. This is purely a
     * documentation marker that the goal is opting into hold-position semantics
     * for the cross-dimension case.
     */
    @Nullable
    public static BlockPos preserveAnchor(@Nullable BlockPos current) {
        return current;
    }
}
