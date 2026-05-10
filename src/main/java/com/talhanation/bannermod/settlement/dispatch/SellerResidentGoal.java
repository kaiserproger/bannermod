package com.talhanation.bannermod.settlement.dispatch;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseTwoIntentScorer;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.SettlementSellerDispatchRecord;
import com.talhanation.bannermod.settlement.SettlementSellerDispatchState;
import com.talhanation.bannermod.settlement.SettlementServiceActorState;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resident goal that wires a seller's READY dispatch seed into a live
 * {@link BannerModSellerDispatchRuntime} flight. Lives under
 * {@code settlement.dispatch} rather than {@code settlement.goal.impl} so it
 * doesn't pollute slice A's namespace; wiring into the scheduler is deferred
 * to a later integration slice.
 *
 * <p>Priority gate:
 * <ul>
 *   <li>Resident must hold a market-profile service contract
 *       (actor state == LOCAL_BUILDING_SERVICE).</li>
 *   <li>A {@link BannerModSettlementSellerDispatchRecord} with
 *       {@link BannerModSettlementSellerDispatchState#READY} and a matching
 *       resident UUID must exist in the supplied market state.</li>
 *   <li>The runtime must not already have the seller in a non-idle phase.</li>
 * </ul>
 *
 * <p>FIXME(marketStateSupplier): the spec reads
 * {@code Supplier<BannerModSettlementSellerDispatchState>} but the shipped
 * name is an enum (READY / MARKET_CLOSED); the actual bag of seed records
 * lives on {@link BannerModSettlementMarketState}, so we take a
 * {@code Supplier<BannerModSettlementMarketState>} here. A later slice can
 * replace this with a dedicated facade if naming ambiguity bites.
 */
public final class SellerResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/seller");

    /**
     * High enough to outrank WorkResidentGoal (60) so a service actor with a
     * READY dispatch seed prefers selling over generic labor.
     */
    public static final int SELLER_PRIORITY = 75;

    /** Cooldown to prevent a completed dispatch from being picked again on the next tick. */
    public static final int SELLER_COOLDOWN_TICKS = 400;

    /** Total envelope = move + selling + return budget. */
    public static final int SELLER_TASK_MAX_TICKS =
            BannerModSellerDispatchRuntime.MOVE_TO_STALL_MAX_TICKS
                    + BannerModSellerDispatchRuntime.SELLING_MAX_TICKS
                    + BannerModSellerDispatchRuntime.RETURNING_MAX_TICKS;

    private final Supplier<SettlementMarketState> marketStateSupplier;
    private final BannerModSellerDispatchRuntime runtime;

    /**
     * Default: empty market state, fresh runtime. Integration slices wire a
     * concrete supplier to the settlement manager's live state.
     */
    public SellerResidentGoal() {
        this(SettlementMarketState::empty, new BannerModSellerDispatchRuntime());
    }

    public SellerResidentGoal(
            Supplier<SettlementMarketState> marketStateSupplier,
            BannerModSellerDispatchRuntime runtime
    ) {
        this.marketStateSupplier = marketStateSupplier != null
                ? marketStateSupplier
                : SettlementMarketState::empty;
        this.runtime = runtime != null ? runtime : new BannerModSellerDispatchRuntime();
    }

    public BannerModSellerDispatchRuntime runtime() {
        return this.runtime;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        if (ctx == null || !ctx.isActivePhase()) {
            return 0;
        }
        if (this.findReadyMarketUuid(ctx) == null) {
            return 0;
        }
        int score = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.WORK) + 8;
        if (score <= 8) {
            return 0;
        }
        return Math.max(SELLER_PRIORITY, score);
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        if (ctx == null || !ctx.isActivePhase()) {
            return false;
        }
        UUID residentUuid = ctx.residentId();
        if (residentUuid == null) {
            return false;
        }
        if (this.runtime.isActive(residentUuid)) {
            return false;
        }
        SettlementResidentServiceContract contract = ctx.resident().serviceContract();
        return contract != null && contract.actorState() == SettlementServiceActorState.LOCAL_BUILDING_SERVICE;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        UUID residentUuid = ctx.residentId();
        UUID marketUuid = this.findReadyMarketUuid(ctx);
        if (residentUuid != null && marketUuid != null && !this.runtime.isActive(residentUuid)) {
            this.runtime.beginDispatch(residentUuid, marketUuid, ctx.gameTime());
        }
        return new ResidentTask(ID, ctx.gameTime(), SELLER_TASK_MAX_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return SELLER_COOLDOWN_TICKS;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    @Nullable
    private UUID findReadyMarketUuid(ResidentGoalContext ctx) {
        SettlementResidentServiceContract contract = ctx.resident().serviceContract();
        if (contract == null || contract.actorState() != SettlementServiceActorState.LOCAL_BUILDING_SERVICE) {
            return null;
        }
        SettlementMarketState state = this.marketStateSupplier.get();
        if (state == null) {
            return null;
        }
        UUID residentUuid = ctx.residentId();
        for (SettlementSellerDispatchRecord record : state.sellerDispatches()) {
            if (record == null) {
                continue;
            }
            if (record.dispatchState() != SettlementSellerDispatchState.READY) {
                continue;
            }
            if (residentUuid.equals(record.residentUuid())) {
                return record.marketUuid();
            }
        }
        return null;
    }
}
