package com.talhanation.bannermod.settlement.dispatch;

/**
 * Live state of a single seller-dispatch flight. The scheduler-facing
 * {@code READY} value mirrors {@code SettlementSellerDispatchState.READY}
 * (seed state, nothing in flight), while the rest track the in-memory progression
 * driven by {@link BannerModSellerDispatchRuntime} once {@code beginDispatch} fires.
 *
 * <p>Intended lifecycle:
 * <pre>
 *   READY -> MOVING_TO_STALL -> AT_STALL -> SELLING -> RETURNING -> RETURNED
 * </pre>
 * {@link #MARKET_CLOSED} and {@link #CANCELLED} are terminal side-exits triggered
 * by {@code forceMarketClose} or external cancellation respectively. A seller in
 * any of {@code READY}, {@code RETURNED}, {@code MARKET_CLOSED}, or
 * {@code CANCELLED} is considered idle and may be re-dispatched.
 */
public enum SellerPhase {
    READY,
    MOVING_TO_STALL,
    AT_STALL,
    SELLING,
    RETURNING,
    RETURNED,
    MARKET_CLOSED,
    CANCELLED
}
