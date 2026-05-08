package com.talhanation.bannermod.settlement.job;

import com.talhanation.bannermod.settlement.BannerModSettlementJobHandlerSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentMode;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Building-bound work handler bound to {@link BannerModSettlementJobHandlerSeed#LOCAL_BUILDING_LABOR}.
 *
 * <p>Claim lifecycle mirrors {@link HarvestJobHandler} but scopes accepted order types to work
 * emitted by the resident's assigned local building.</p>
 */
public final class BuildJobHandler implements JobHandler {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("bannermod", "build");

    public static final Set<SettlementWorkOrderType> SUPPORTED_TYPES = EnumSet.of(
            SettlementWorkOrderType.BREAK_BLOCK,
            SettlementWorkOrderType.BUILD_BLOCK,
            SettlementWorkOrderType.ANIMAL_BREED,
            SettlementWorkOrderType.ANIMAL_SPECIAL_TASK,
            SettlementWorkOrderType.ANIMAL_SLAUGHTER
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public BannerModSettlementJobHandlerSeed handles() {
        return BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR;
    }

    @Override
    public boolean canHandle(JobExecutionContext ctx) {
        if (ctx == null || ctx.resident() == null) {
            return false;
        }
        return ctx.resident().residentMode() == BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER;
    }

    @Override
    public JobExecutionResult runOneStep(JobExecutionContext ctx) {
        BannerModSettlementResidentRecord resident = ctx.resident();
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
        return 10;
    }
}
