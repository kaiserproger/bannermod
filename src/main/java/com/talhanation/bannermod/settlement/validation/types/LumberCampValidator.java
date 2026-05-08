package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;

public final class LumberCampValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection workZone = context.zonesByRole().get(ZoneRole.WORK_ZONE);
        if (workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "lumber_work_zone_missing", "Lumber camp requires a WORK_ZONE.");
        }
        if (BuildingValidationSupport.distanceToZone(request.anchorPos(), workZone) > 32.0D) {
            context.blocking().add(new ValidationIssue("lumber_anchor_too_far", "Lumber camp anchor must be within 32 blocks of work zone.", ValidationSeverity.BLOCKING));
        }
        int productivity = BuildingValidationSupport.countLogs(context.level(), workZone)
                + (BuildingValidationSupport.countSaplings(context.level(), workZone) / 2);
        if (productivity < 12) {
            context.blocking().add(new ValidationIssue("lumber_resources_too_small", "Lumber camp requires enough logs/saplings in zone.", ValidationSeverity.BLOCKING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }
        int capacity = BuildingValidationSupport.clamp(productivity / 12, 1, 3);
        return BuildingValidationResult.success(request.type(), capacity, Math.min(100, productivity * 2), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
