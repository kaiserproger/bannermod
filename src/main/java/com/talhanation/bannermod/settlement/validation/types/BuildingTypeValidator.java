package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;

public interface BuildingTypeValidator {
    BuildingValidationResult validate(BuildingValidationContext context);
}
