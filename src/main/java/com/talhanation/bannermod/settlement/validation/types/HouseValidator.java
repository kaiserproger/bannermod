package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;

public final class HouseValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection interior = context.zonesByRole().get(ZoneRole.INTERIOR);
        ZoneSelection sleeping = context.zonesByRole().get(ZoneRole.SLEEPING);
        if (interior == null || sleeping == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "house_zones_missing", "House requires INTERIOR and SLEEPING zones.");
        }

        BuildingValidationSupport.InteriorStats stats = BuildingValidationSupport.scanInterior(context.level(), interior);
        int validBeds = BuildingValidationSupport.countBeds(context.level(), sleeping);
        if (stats.walkableBlocks() < 8) {
            context.blocking().add(new ValidationIssue("house_walkable_too_small", "House requires at least 8 walkable interior blocks.", ValidationSeverity.BLOCKING));
        }
        if (stats.roofCoverage() < 0.70D) {
            context.blocking().add(new ValidationIssue("house_roof_too_open", "House requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }
        if (validBeds < 1) {
            validBeds = BuildingValidationSupport.countBedsNearZone(context.level(), sleeping, 1);
        }
        if (validBeds < 1) {
            validBeds = BuildingValidationSupport.countBeds(context.level(), interior);
        }
        if (validBeds < 1) {
            validBeds = BuildingValidationSupport.countBedsNearZone(context.level(), interior, 1);
        }
        if (validBeds < 1 && BuildingValidationSupport.findNearestBed(context.level(), request.anchorPos(), 12) != null) {
            validBeds = 1;
        }
        if (validBeds < 1) {
            context.blocking().add(new ValidationIssue("house_bed_missing", "House requires at least one bed in sleeping zone.", ValidationSeverity.BLOCKING));
        }
        if (!BuildingValidationSupport.hasEntrance(interior, context.level())) {
            context.warnings().add(new ValidationIssue("house_entrance_missing", "House entrance is unclear for current selection.", ValidationSeverity.WARNING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }

        int capacity = Math.min(validBeds, stats.walkableBlocks() / 8);
        if (capacity < 1) {
            capacity = 1;
            context.warnings().add(new ValidationIssue("house_capacity_clamped", "House passed with minimum capacity due to tight interior space.", ValidationSeverity.WARNING));
        }
        return BuildingValidationResult.success(request.type(), capacity, Math.min(100, (int) Math.round(stats.roofCoverage() * 100.0D)), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
