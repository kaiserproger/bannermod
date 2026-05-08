package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;

public final class MineValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection workZone = context.zonesByRole().get(ZoneRole.WORK_ZONE);
        if (workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "mine_work_zone_missing", "Mine requires a WORK_ZONE.");
        }
        if (BuildingValidationSupport.distanceToZone(request.anchorPos(), workZone) > 32.0D) {
            context.blocking().add(new ValidationIssue("mine_anchor_too_far", "Mine anchor must be within 32 blocks of work zone.", ValidationSeverity.BLOCKING));
        }
        if (context.level().canSeeSky(request.anchorPos().above())) {
            context.warnings().add(new ValidationIssue("mine_anchor_unsheltered", "Mine anchor is exposed to sky. A covered shed is recommended.", ValidationSeverity.WARNING));
        }
        int validMineFaceBlocks = BuildingValidationSupport.countMineFaceBlocks(context.level(), workZone);
        if (validMineFaceBlocks < 24) {
            context.blocking().add(new ValidationIssue("mine_face_too_small", "Mine requires at least 24 exposed stone/ore/deepslate blocks.", ValidationSeverity.BLOCKING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }
        int capacity = BuildingValidationSupport.clamp(1 + (validMineFaceBlocks / 64), 1, 4);
        return BuildingValidationResult.success(request.type(), capacity, Math.min(100, validMineFaceBlocks), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
