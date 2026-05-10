package com.talhanation.bannermod.settlement.goal;

import net.minecraft.resources.ResourceLocation;

public record ResidentTaskOutcome(
        ResourceLocation goalId,
        ResidentStopReason stopReason,
        long finishedGameTime
) {
    public ResidentTaskOutcome {
        if (goalId == null) {
            throw new IllegalArgumentException("goalId must not be null");
        }
        if (stopReason == null) {
            throw new IllegalArgumentException("stopReason must not be null");
        }
    }

    public boolean isFailure() {
        return this.stopReason == ResidentStopReason.TIMED_OUT
                || this.stopReason == ResidentStopReason.CONTEXT_INVALID;
    }
}
