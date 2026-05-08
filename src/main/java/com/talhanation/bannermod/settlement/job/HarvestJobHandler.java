package com.talhanation.bannermod.settlement.job;

import com.talhanation.bannermod.settlement.SettlementJobHandlerSeed;
import com.talhanation.bannermod.settlement.SettlementResidentMode;
import com.talhanation.bannermod.settlement.SettlementResidentRecord;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Floating-labor work handler bound to {@link SettlementJobHandlerSeed#FLOATING_LABOR_POOL}.
 *
 * <p>On each step the handler:</p>
 * <ol>
 *   <li>Checks whether the resident already holds a published work-order claim. If so it
 *       reports {@link JobExecutionResult#COMPLETED} — the per-tick claim is alive and the
 *       real block-level action is executed by the bound worker entity via
 *       {@code SettlementOrderWorkGoal}.</li>
 *   <li>Otherwise it attempts to claim the highest-priority PENDING order matching the
 *       supported gathering or transport types for the resident's current settlement claim.</li>
 *   <li>If no order is available it reports {@link JobExecutionResult#BLOCKED_NO_TARGET}.</li>
 * </ol>
 *
 * <p>The handler is a pure coordination step — it never mutates blocks directly. Completion
 * of the order is reported by the worker AI through
 * {@link SettlementWorkOrderRuntime#complete(UUID)}.</p>
 *
 * <p>Transport orders ({@code FETCH_INPUT} / {@code HAUL_RESOURCE}) are intentionally
 * included here so a floating-laborer assigned to a building can also drive the transport
 * state machine in {@code SettlementOrderWorkGoal} when no harvest/till/plant work is
 * pending. Without this, transport orders sit unclaimed even though the runtime, the
 * container exchange, and the AI goal that consumes them are all wired up.</p>
 */
public final class HarvestJobHandler implements JobHandler {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("bannermod", "harvest");

    /** Order types this handler is willing to claim on behalf of its residents. */
    public static final Set<SettlementWorkOrderType> SUPPORTED_TYPES = EnumSet.of(
            SettlementWorkOrderType.HARVEST_CROP,
            SettlementWorkOrderType.PLANT_CROP,
            SettlementWorkOrderType.TILL_SOIL,
            SettlementWorkOrderType.FELL_TREE,
            SettlementWorkOrderType.REPLANT_TREE,
            SettlementWorkOrderType.MINE_BLOCK,
            SettlementWorkOrderType.FETCH_INPUT,
            SettlementWorkOrderType.HAUL_RESOURCE
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public SettlementJobHandlerSeed handles() {
        return SettlementJobHandlerSeed.FLOATING_LABOR_POOL;
    }

    @Override
    public boolean canHandle(JobExecutionContext ctx) {
        if (ctx == null || ctx.resident() == null) {
            return false;
        }
        return ctx.resident().residentMode() == SettlementResidentMode.PROJECTED_CONTROLLED_WORKER;
    }

    @Override
    public JobExecutionResult runOneStep(JobExecutionContext ctx) {
        SettlementResidentRecord resident = ctx.resident();
        SettlementWorkOrderRuntime runtime = ctx.workOrderRuntime();
        if (runtime == null || resident.residentUuid() == null) {
            return JobExecutionResult.COMPLETED;
        }

        Optional<SettlementWorkOrder> current = runtime.currentClaim(resident.residentUuid());
        if (current.isPresent()) {
            return JobExecutionResult.COMPLETED;
        }

        UUID workplaceUuid = ctx.workplace().orElse(null);
        if (workplaceUuid == null) {
            return JobExecutionResult.BLOCKED_NO_TARGET;
        }

        Optional<SettlementWorkOrder> picked = runtime.claimForBuilding(
                workplaceUuid,
                resident.residentUuid(),
                order -> SUPPORTED_TYPES.contains(order.type()),
                ctx.gameTime(),
                SettlementWorkOrderRuntime.DEFAULT_CLAIM_EXPIRY_TICKS
        );
        return picked.isPresent() ? JobExecutionResult.COMPLETED : JobExecutionResult.BLOCKED_NO_TARGET;
    }

    @Override
    public int cooldownTicks() {
        return 5;
    }
}
