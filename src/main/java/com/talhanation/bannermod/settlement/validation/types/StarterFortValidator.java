package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;

public final class StarterFortValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection interior = context.zonesByRole().get(ZoneRole.INTERIOR);
        if (interior == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "interior_missing", "Fort interior zone is required.");
        }
        ZoneSelection authorityPoint = context.zonesByRole().get(ZoneRole.AUTHORITY_POINT);
        if (authorityPoint == null || !authorityPoint.contains(request.anchorPos())) {
            context.warnings().add(new ValidationIssue("fort_authority_unclear", "Fort authority point is missing or does not include anchor.", ValidationSeverity.WARNING));
        }
        int bannerRadius = WorkersServerConfig.settlementFortBannerMaxDistance();
        if (!BuildingValidationSupport.hasBannerNearAnchor(context.level(), request.anchorPos(), bannerRadius)) {
            context.warnings().add(new ValidationIssue("banner_missing", "No banner found within " + bannerRadius + " blocks of fort anchor.", ValidationSeverity.WARNING));
        }

        BuildingValidationSupport.InteriorStats stats = BuildingValidationSupport.scanInterior(context.level(), interior);
        if (stats.walkableBlocks() < 64) {
            context.blocking().add(new ValidationIssue("fort_walkable_too_small", "Fort interior needs at least 64 walkable blocks around the courtyard and wings.", ValidationSeverity.BLOCKING));
        }
        if (stats.roofCoverage() < 0.60D) {
            context.warnings().add(new ValidationIssue("fort_roof_too_open", "Fort roof coverage is low. A more sheltered interior is recommended.", ValidationSeverity.WARNING));
        }
        if (!BuildingValidationSupport.hasEntrance(interior, context.level())) {
            context.warnings().add(new ValidationIssue("fort_entrance_unclear", "Fort entrance is unclear for current selection; manual review recommended.", ValidationSeverity.WARNING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }

        int qualityScore = Math.min(100, (int) Math.round((stats.roofCoverage() * 0.70D + Math.min(1.0D, stats.walkableBlocks() / 128.0D) * 0.30D) * 100.0D));
        return BuildingValidationResult.success(request.type(), 4, qualityScore, context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
