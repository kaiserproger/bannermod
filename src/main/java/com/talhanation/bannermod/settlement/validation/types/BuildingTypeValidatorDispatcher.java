package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class BuildingTypeValidatorDispatcher {
    private final EnumMap<BuildingType, BuildingTypeValidator> validators = new EnumMap<>(BuildingType.class);

    public BuildingTypeValidatorDispatcher() {
        register(BuildingType.STARTER_FORT, new StarterFortValidator());
        register(BuildingType.HOUSE, new HouseValidator());
        register(BuildingType.FARM, new FarmValidator());
        register(BuildingType.MINE, new MineValidator());
        register(BuildingType.LUMBER_CAMP, new LumberCampValidator());
        register(BuildingType.SMITHY, new SmithyValidator());
        register(BuildingType.STORAGE, new StorageValidator());
        register(BuildingType.ARCHITECT_WORKSHOP, new ArchitectWorkshopValidator());
        register(BuildingType.BARRACKS, new BarracksValidator());
    }

    public BuildingTypeValidatorDispatcher(Map<BuildingType, BuildingTypeValidator> validators) {
        validators.forEach(this::register);
    }

    public void register(BuildingType type, BuildingTypeValidator validator) {
        this.validators.put(Objects.requireNonNull(type), Objects.requireNonNull(validator));
    }

    public Optional<BuildingTypeValidator> validatorFor(BuildingType type) {
        return Optional.ofNullable(this.validators.get(type));
    }

    public BuildingValidationResult validate(BuildingValidationContext context,
                                             Function<BuildingValidationContext, BuildingValidationResult> fallback) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(fallback);

        BuildingTypeValidator validator = this.validators.get(context.request().type());
        return validator == null ? fallback.apply(context) : validator.validate(context);
    }
}
