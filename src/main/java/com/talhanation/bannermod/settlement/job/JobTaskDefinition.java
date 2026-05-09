package com.talhanation.bannermod.settlement.job;

import com.talhanation.bannermod.settlement.SettlementJobHandlerSeed;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Declarative description of a job task that a {@link JobHandler} can fulfill.
 *
 * <p>Additive, data-driven contract introduced by slice {@code 25-next-E} to prepare for a future
 * data-loaded job catalog (mirroring Millenaire's {@code GatheringType} without copying GPL code).
 * This record does <em>not</em> drive any existing worker AI yet; it is the shape that later slices
 * (slice-F and later) will use to describe tasks both in code and in JSON.</p>
 */
public record JobTaskDefinition(
        ResourceLocation id,
        SettlementJobHandlerSeed handlerSeed,
        int estimatedTickCost,
        int maxConcurrentAssignments,
        boolean requiresWorkplace,
        boolean requiresToolLoadout
) {
    public JobTaskDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handlerSeed, "handlerSeed");
        if (estimatedTickCost < 0) {
            throw new IllegalArgumentException("estimatedTickCost must be >= 0, got " + estimatedTickCost);
        }
        if (maxConcurrentAssignments < 1) {
            throw new IllegalArgumentException("maxConcurrentAssignments must be >= 1, got " + maxConcurrentAssignments);
        }
    }
}
