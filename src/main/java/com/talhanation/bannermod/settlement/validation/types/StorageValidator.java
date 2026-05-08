package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import com.talhanation.bannermod.settlement.validation.ValidationSeverity;

public final class StorageValidator implements BuildingTypeValidator {
    @Override
    public BuildingValidationResult validate(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        ZoneSelection storageZone = context.zonesByRole().get(ZoneRole.STORAGE);
        if (storageZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "storage_zone_missing", "Storage requires a STORAGE zone.");
        }
        int containerCount = BuildingValidationSupport.countContainers(context.level(), storageZone);
        if (containerCount < 1) {
            context.blocking().add(new ValidationIssue("storage_containers_missing", "Storage requires at least one container (chest/barrel).", ValidationSeverity.BLOCKING));
        }
        if (!context.blocking().isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, context.blocking(), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
        }
        return BuildingValidationResult.success(request.type(), 0, Math.min(100, containerCount * 10), context.warnings(), BuildingValidationSupport.buildSnapshot(request));
    }
}
