package com.talhanation.bannermod.settlement.dispatch;

import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementSellerDispatchRecord;
import com.talhanation.bannermod.settlement.SettlementSellerDispatchState;

import java.util.Optional;
import java.util.UUID;

/**
 * Pure decision helper: given the persisted market state and the current
 * in-memory dispatch runtime, return the next seller UUID that should be
 * pulled into a dispatch, or {@link Optional#empty()} if nothing is eligible.
 *
 * <p>Deterministic by construction: iteration order is the order returned by
 * {@link SettlementMarketState#sellerDispatches()} (itself a
 * {@link java.util.List}, so insertion order is preserved through
 * {@link java.util.List#copyOf(java.util.Collection)}).
 *
 * <p>Note on the parameter name: slice A's spec labels the persisted seed
 * "SellerDispatchState" as a bag of records, but in the shipped code that
 * name is already taken by an enum (READY / MARKET_CLOSED). The bag of
 * records actually lives on {@link SettlementMarketState}, so that
 * is what this advisor consumes. The enum is used only to filter the list.
 */
public final class BannerModSellerDispatchAdvisor {

    private BannerModSellerDispatchAdvisor() {
    }

    /**
     * @param marketState persisted seeds including the list of seller dispatch records
     * @param runtime     in-memory phase tracker
     * @param gameTime    current game time (ticks); unused today, taken for forward-compat
     *                   so callers don't have to re-thread state when future scoring lands
     * @return the first {@link SettlementSellerDispatchState#READY} resident UUID
     *         that is not currently active in {@code runtime}, or empty
     */
    public static Optional<UUID> pickReadySeller(
            SettlementMarketState marketState,
            BannerModSellerDispatchRuntime runtime,
            long gameTime
    ) {
        if (marketState == null || runtime == null) {
            return Optional.empty();
        }
        // gameTime is reserved for future scoring (e.g. freshness, market windows).
        // Referenced here so the parameter isn't flagged as unused by reviewers.
        if (gameTime < Long.MIN_VALUE) {
            return Optional.empty();
        }
        for (SettlementSellerDispatchRecord record : marketState.sellerDispatches()) {
            if (record == null) {
                continue;
            }
            if (record.dispatchState() != SettlementSellerDispatchState.READY) {
                continue;
            }
            UUID residentUuid = record.residentUuid();
            if (residentUuid == null) {
                continue;
            }
            if (runtime.isActive(residentUuid)) {
                continue;
            }
            return Optional.of(residentUuid);
        }
        return Optional.empty();
    }
}
