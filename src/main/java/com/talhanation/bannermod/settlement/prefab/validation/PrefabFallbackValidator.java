package com.talhanation.bannermod.settlement.prefab.validation;

import com.talhanation.bannermod.settlement.prefab.BuildingPrefab;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic fallback used when a prefab has no specialised validator. Enforces only a
 * minimum footprint and minimum solid-block count, so player-built warehouses still get
 * an honest pass/fail even if nobody has written a dedicated ruleset yet.
 */
public final class PrefabFallbackValidator implements BuildingValidator {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("bannermod", "default");

    @Override
    public ResourceLocation prefabId() {
        return ID;
    }

    @Override
    public ValidationResult validate(BuildingPrefab prefab, ServerLevel level, AABB bounds, BuildingInspectionView view) {
        List<ValidationIssue> issues = new ArrayList<>();

        int footprint = view.width() * view.depth();
        if (footprint < 9) {
            issues.add(ValidationIssue.blocker("bannermod.prefab.validate.too_small", footprint, 9));
        }
        if (view.solidCount() < 20) {
            issues.add(ValidationIssue.blocker("bannermod.prefab.validate.not_enough_blocks", view.solidCount(), 20));
        }
        if (view.distinctBlockCount() < 2) {
            issues.add(ValidationIssue.major("bannermod.prefab.validate.no_variety", view.distinctBlockCount()));
        }

        int score = ArchitectureScorer.score(level, view);
        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.BLOCKER);
        return new ValidationResult(passed, score, issues);
    }
}
