package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;

public final class FarmValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection workZone = context.zonesByRole().get(ZoneRole.WORK_ZONE);
        if (workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "farm_work_zone_missing", "Farm requires a WORK_ZONE.");
        }
        if (BuildingValidationSupport.distanceToZone(request.anchorPos(), workZone) > 24.0D) {
            context.blocking().add(new ValidationIssue("farm_anchor_too_far", "Farm anchor must be within 24 blocks of work zone.", ValidationSeverity.BLOCKING));
        }
        int farmlandBlocks = BuildingValidationSupport.countFarmlandBlocks(context.level(), workZone);
        if (farmlandBlocks < 24) {
            context.blocking().add(new ValidationIssue("farm_farmland_too_small", "Farm requires at least 24 farmland/crop-capable blocks.", ValidationSeverity.BLOCKING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }
        int capacity = BuildingValidationSupport.clamp(farmlandBlocks / 48, 1, 4);
        return BuildingValidationResult.success(request.type(), capacity, Math.min(100, farmlandBlocks), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
