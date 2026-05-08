package com.talhanation.bannermod.settlement.job;

import com.talhanation.bannermod.settlement.SettlementJobHandlerSeed;
import net.minecraft.resources.ResourceLocation;

/**
 * Executable side of a job task. Implementations are registered by
 * {@link JobHandlerRegistry} and selected via a {@link SettlementJobHandlerSeed}.
 *
 * <p>Handlers are stateless singletons with respect to a given registry; per-resident state
 * lives on the resident record or later on scheduler data, not on the handler instance.</p>
 */
public interface JobHandler {
    /** Stable identifier for this handler, e.g. {@code bannermod:harvest}. */
    ResourceLocation id();

    /** Seed enum value this handler claims ownership of. */
    SettlementJobHandlerSeed handles();

    /** Fast precondition check used by the scheduler before calling {@link #runOneStep}. */
    boolean canHandle(JobExecutionContext ctx);

    /** Execute a single scheduling step and return a coarse outcome. */
    JobExecutionResult runOneStep(JobExecutionContext ctx);

    /** Minimum ticks between steps for the same resident. Default is zero (run every tick). */
    default int cooldownTicks() {
        return 0;
    }
}
