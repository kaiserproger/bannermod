package com.talhanation.bannermod.settlement.prefab.validation;

import com.talhanation.bannermod.settlement.prefab.BuildingPrefab;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Central registry mapping prefab id → {@link BuildingValidator}. If no specific validator
 * is registered for a prefab, {@link PrefabFallbackValidator} is used as a fallback so the
 * player always gets a reasonable result.
 */
public final class BuildingValidatorRegistry {
    private static final BuildingValidatorRegistry INSTANCE = new BuildingValidatorRegistry();

    private final Map<ResourceLocation, BuildingValidator> validators = new LinkedHashMap<>();
    private final BuildingValidator fallback = new PrefabFallbackValidator();
    private boolean defaultsLoaded;

    private BuildingValidatorRegistry() {
    }

    public static BuildingValidatorRegistry instance() {
        return INSTANCE;
    }

    public synchronized void register(BuildingValidator validator) {
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(validator.prefabId(), "validator.prefabId()");
        validators.put(validator.prefabId(), validator);
    }

    public synchronized Optional<BuildingValidator> lookup(ResourceLocation prefabId) {
        if (prefabId == null) return Optional.empty();
        return Optional.ofNullable(validators.get(prefabId));
    }

    public synchronized BuildingValidator resolve(ResourceLocation prefabId) {
        BuildingValidator found = prefabId == null ? null : validators.get(prefabId);
        return found == null ? fallback : found;
    }

    public synchronized int size() {
        return validators.size();
    }

    public synchronized void ensureDefaultsLoaded() {
        if (defaultsLoaded) return;
        defaultsLoaded = true;
        BuildingValidatorCatalog.registerDefaults(this);
    }

    public ValidationResult run(BuildingPrefab prefab, ServerLevel level, AABB bounds) {
        ensureDefaultsLoaded();
        BuildingValidator validator = resolve(prefab == null ? null : prefab.id());
        BuildingInspectionView view = BuildingInspectionView.scan(level, bounds);
        return validator.validate(prefab, level, bounds, view);
    }

    /** Visible for tests. */
    public synchronized void clearForTest() {
        validators.clear();
        defaultsLoaded = false;
    }
}
