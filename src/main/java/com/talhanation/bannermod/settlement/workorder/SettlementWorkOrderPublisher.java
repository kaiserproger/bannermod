package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.settlement.SettlementBuildingRecord;

/**
 * Building-type-specific pass that converts building state into concrete
 * {@link SettlementWorkOrder}s and deposits them into the supplied runtime.
 *
 * <p>Publishers should be stateless. They are invoked once per settlement tick for each
 * building they match. Duplicate orders (same building + type + target position) are rejected
 * by the runtime, so publishers may re-emit orders idempotently without leaking queue growth.</p>
 */
public interface SettlementWorkOrderPublisher {
    /** Fast-check whether this publisher handles a building record. */
    boolean matches(SettlementBuildingRecord building);

    /** Emit zero or more work orders for {@code ctx.building()} into {@code ctx.runtime()}. */
    void publish(SettlementWorkOrderPublishContext ctx);
}
