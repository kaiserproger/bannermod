package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;

public final class BarracksValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection interior = context.zonesByRole().get(ZoneRole.INTERIOR);
        ZoneSelection sleeping = context.zonesByRole().get(ZoneRole.SLEEPING);
        ZoneSelection storage = context.zonesByRole().get(ZoneRole.STORAGE);
        if (interior == null || sleeping == null || storage == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "barracks_zones_missing", "Barracks requires INTERIOR, SLEEPING, and STORAGE zones.");
        }

        BuildingValidationSupport.InteriorStats interiorStats = BuildingValidationSupport.scanInterior(context.level(), interior);
        if (interiorStats.walkableBlocks() < 16) {
            context.blocking().add(new ValidationIssue("barracks_walkable_too_small", "Barracks requires at least 16 walkable interior blocks.", ValidationSeverity.BLOCKING));
        }
        if (interiorStats.roofCoverage() < 0.70D) {
            context.blocking().add(new ValidationIssue("barracks_roof_too_open", "Barracks requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }
        int beds = BuildingValidationSupport.countBeds(context.level(), sleeping);
        if (beds < 2) beds = Math.max(beds, BuildingValidationSupport.countBedsNearZone(context.level(), sleeping, 1));
        if (beds < 2) {
            context.blocking().add(new ValidationIssue("barracks_beds_missing", "Barracks requires at least two beds or bunks in the sleeping zone.", ValidationSeverity.BLOCKING));
        }
        if (BuildingValidationSupport.countContainers(context.level(), storage) < 1) {
            context.blocking().add(new ValidationIssue("barracks_storage_missing", "Barracks requires at least one chest or barrel in the storage zone.", ValidationSeverity.BLOCKING));
        }
        if (!BuildingValidationSupport.hasEntrance(interior, context.level())) {
            context.warnings().add(new ValidationIssue("barracks_entrance_unclear", "Barracks entrance is unclear for current selection.", ValidationSeverity.WARNING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }

        int capacity = BuildingValidationSupport.clamp(Math.max(1, beds), 1, 4);
        int qualityScore = Math.min(100, (int) Math.round(interiorStats.roofCoverage() * 100.0D));
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
