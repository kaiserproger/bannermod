package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;
import net.minecraft.world.level.block.Blocks;

public final class ArchitectWorkshopValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection interior = context.zonesByRole().get(ZoneRole.INTERIOR);
        ZoneSelection workZone = context.zonesByRole().get(ZoneRole.WORK_ZONE);
        if (interior == null || workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "architect_zones_missing", "Architect workshop requires INTERIOR and WORK_ZONE zones.");
        }
        BuildingValidationSupport.InteriorStats interiorStats = BuildingValidationSupport.scanInterior(context.level(), interior);
        if (interiorStats.walkableBlocks() < 16) {
            context.blocking().add(new ValidationIssue("architect_walkable_too_small", "Architect workshop requires at least 16 walkable interior blocks.", ValidationSeverity.BLOCKING));
        }
        if (interiorStats.roofCoverage() < 0.70D) {
            context.blocking().add(new ValidationIssue("architect_roof_too_open", "Architect workshop requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }
        int draftingTables = BuildingValidationSupport.countBlocks(context.level(), workZone, Blocks.CRAFTING_TABLE);
        if (draftingTables < 1) {
            context.blocking().add(new ValidationIssue("architect_table_missing", "Architect workshop requires at least one drafting table (crafting table placeholder).", ValidationSeverity.BLOCKING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }
        int capacity = Math.min(draftingTables, Math.max(1, interiorStats.walkableBlocks() / 24));
        int qualityScore = Math.min(100, (int) Math.round(interiorStats.roofCoverage() * 100.0D));
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
