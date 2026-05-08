package com.talhanation.bannermod.settlement.project;

/**
 * Reason a {@link com.talhanation.bannermod.settlement.growth.PendingProject} was removed
 * from a {@link SettlementProjectScheduler} queue. Slice C is concerned only with
 * queue bookkeeping; downstream slices may persist or broadcast these transitions.
 */
public enum ProjectCancellationReason {
    /** Project finished successfully and was retired. */
    COMPLETED,
    /** A higher-priority project took this one's slot. */
    SUPERSEDED,
    /** Execution prerequisites (builder, materials, site) are persistently missing. */
    BLOCKED,
    /** Siege or other emergency interrupted the project before it could continue. */
    SIEGE_INTERRUPT,
    /** Explicitly cancelled by a player or admin tooling. */
    MANUAL
}
