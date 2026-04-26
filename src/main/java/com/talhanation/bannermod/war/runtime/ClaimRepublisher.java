package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;

@FunctionalInterface
public interface ClaimRepublisher {
    /** Persist + broadcast the (mutated) claim. Production wires to claimManager.addOrUpdateClaim(level, claim). */
    void republish(RecruitsClaim claim);

    ClaimRepublisher NOOP = c -> { };
}
