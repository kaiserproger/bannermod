package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.FurnaceBlock;

import java.util.List;

public final class SmithyValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection interior = context.zonesByRole().get(ZoneRole.INTERIOR);
        ZoneSelection workZone = context.zonesByRole().get(ZoneRole.WORK_ZONE);
        if (interior == null || workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "smithy_zones_missing", "Smithy requires INTERIOR and WORK_ZONE zones.");
        }

        BuildingValidationSupport.InteriorStats interiorStats = BuildingValidationSupport.scanInterior(context.level(), interior);
        if (interiorStats.roofCoverage() < 0.70D) {
            context.blocking().add(new ValidationIssue("smithy_roof_too_open", "Smithy requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }
        List<BlockPos> anvilPositions = BuildingValidationSupport.collectPositions(context.level(), workZone, state -> state.getBlock() instanceof AnvilBlock);
        List<BlockPos> furnacePositions = BuildingValidationSupport.collectPositions(context.level(), workZone, state -> state.getBlock() instanceof FurnaceBlock || state.getBlock() instanceof BlastFurnaceBlock);
        if (anvilPositions.isEmpty()) {
            context.blocking().add(new ValidationIssue("smithy_anvil_missing", "Smithy requires at least one anvil in work zone.", ValidationSeverity.BLOCKING));
        }
        if (furnacePositions.isEmpty()) {
            context.blocking().add(new ValidationIssue("smithy_furnace_missing", "Smithy requires at least one furnace or blast furnace in work zone.", ValidationSeverity.BLOCKING));
        }
        if (!anvilPositions.isEmpty() && !furnacePositions.isEmpty() && !BuildingValidationSupport.hasClosePair(anvilPositions, furnacePositions, 4.0D)) {
            context.blocking().add(new ValidationIssue("smithy_anchor_set_too_far", "Anvil must be within 4 blocks of a furnace or blast furnace.", ValidationSeverity.BLOCKING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }
        int anchorSets = Math.min(anvilPositions.size(), furnacePositions.size());
        int capacity = Math.min(Math.min(anchorSets, interiorStats.walkableBlocks() / 16), 2);
        if (capacity < 1) {
            capacity = 1;
            context.warnings().add(new ValidationIssue("smithy_capacity_clamped", "Smithy passed with minimum capacity due to limited interior space.", ValidationSeverity.WARNING));
        }
        return BuildingValidationResult.success(request.type(), capacity, Math.min(100, (int) Math.round(interiorStats.roofCoverage() * 100.0D)), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
