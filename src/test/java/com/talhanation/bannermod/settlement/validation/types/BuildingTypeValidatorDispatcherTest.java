package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingTypeValidatorDispatcherTest {
    @Test
    void returnsRegisteredValidatorForEveryBuildingType() {
        BuildingTypeValidatorDispatcher dispatcher = new BuildingTypeValidatorDispatcher();
        Map<BuildingType, BuildingTypeValidator> validators = new EnumMap<>(BuildingType.class);

        for (BuildingType type : BuildingType.values()) {
            BuildingTypeValidator validator = context -> BuildingValidationResult.blockingFailure(
                    context.request().type(),
                    "registered",
                    "Registered validator used."
            );
            validators.put(type, validator);
            dispatcher.register(type, validator);
        }

        for (BuildingType type : BuildingType.values()) {
            assertTrue(dispatcher.validatorFor(type).isPresent());
            assertSame(validators.get(type), dispatcher.validatorFor(type).orElseThrow());
        }
    }

    @Test
    void fallsBackForUnregisteredBuildingType() {
        BuildingTypeValidatorDispatcher dispatcher = new BuildingTypeValidatorDispatcher(Map.of());
        AtomicBoolean fallbackUsed = new AtomicBoolean(false);
        BuildingValidationResult fallbackResult = BuildingValidationResult.blockingFailure(
                BuildingType.FARM,
                "fallback",
                "Fallback validator used."
        );

        BuildingValidationResult result = dispatcher.validate(context(BuildingType.FARM), context -> {
            fallbackUsed.set(true);
            return fallbackResult;
        });

        assertFalse(dispatcher.validatorFor(BuildingType.FARM).isPresent());
        assertTrue(fallbackUsed.get());
        assertSame(fallbackResult, result);
    }

    private static BuildingValidationContext context(BuildingType type) {
        BuildingValidationRequest request = new BuildingValidationRequest(
                new UUID(0L, 0L),
                type,
                BlockPos.ZERO,
                List.of()
        );
        return new BuildingValidationContext(null, null, request, Map.of(), List.of(), List.of());
    }
}
