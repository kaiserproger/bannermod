package com.talhanation.bannermod.settlement.validation;

import com.talhanation.bannermod.settlement.building.BuildingDefinition;
import com.talhanation.bannermod.settlement.building.BuildingDefinitionRegistry;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.types.BuildingTypeValidatorDispatcher;
import com.talhanation.bannermod.settlement.validation.types.BuildingValidationContext;
import com.talhanation.bannermod.settlement.validation.types.BuildingValidationSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SettlementBuildingValidator implements BuildingValidator {
    private static final int MAX_ZONE_VOLUME = 262_144;
    private static final Set<RolePair> PROHIBITED_OVERLAP_ROLE_PAIRS = prohibitedRolePairs();

    private final BuildingDefinitionRegistry definitionRegistry;
    private final BuildingTypeValidatorDispatcher typeValidatorDispatcher;

    public SettlementBuildingValidator(BuildingDefinitionRegistry definitionRegistry) {
        this(definitionRegistry, new BuildingTypeValidatorDispatcher());
    }

    public SettlementBuildingValidator(BuildingDefinitionRegistry definitionRegistry,
                                       BuildingTypeValidatorDispatcher typeValidatorDispatcher) {
        this.definitionRegistry = definitionRegistry;
        this.typeValidatorDispatcher = typeValidatorDispatcher;
    }

    @Override
    public BuildingValidationResult validate(ServerLevel level, Player player, BuildingValidationRequest request) {
        if (level == null || request == null) {
            return BuildingValidationResult.blockingFailure(null, "invalid_request", "Validation request is missing.");
        }
        Optional<BuildingDefinition> optionalDefinition = this.definitionRegistry.get(request.type());
        if (optionalDefinition.isEmpty()) {
            return BuildingValidationResult.blockingFailure(request.type(), "unknown_building_type", "No building definition is registered.");
        }

        List<ValidationIssue> blocking = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        validateSelection(request, optionalDefinition.get(), blocking);
        if (request.enforceOverlapChecks()) {
            validateOverlap(level, request, blocking);
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, null);
        }

        BuildingValidationContext context = new BuildingValidationContext(
                level, player, request, BuildingValidationSupport.toRoleMap(request.zones()), warnings, blocking);
        return this.typeValidatorDispatcher.validate(context, missing -> BuildingValidationResult.blockingFailure(
                missing.request().type(), "validator_not_implemented", "Validator pipeline for this building type is not implemented yet."));
    }

    @Override
    public BuildingValidationResult revalidate(ServerLevel level, ValidatedBuildingRecord building) {
        if (level == null || building == null) {
            return BuildingValidationResult.blockingFailure(null, "invalid_revalidation_request", "Revalidation request is missing.");
        }
        BuildingValidationRequest request = new BuildingValidationRequest(
                building.settlementId(), building.type(), building.anchorPos(), building.zones(), false);
        BuildingValidationResult result = validate(level, null, request);
        if (result.valid() || building.type() != BuildingType.HOUSE || building.zones().isEmpty()) {
            return result;
        }

        BuildingValidationRequest recoveredHouseRequest = BuildingValidationSupport.tryRecoverHouseRequest(level, building);
        if (recoveredHouseRequest == null) {
            return result;
        }
        BuildingValidationResult recovered = validate(level, null, recoveredHouseRequest);
        return recovered.valid() ? recovered : result;
    }

    private void validateSelection(BuildingValidationRequest request,
                                   BuildingDefinition definition,
                                   List<ValidationIssue> blocking) {
        if (request.zones().isEmpty()) {
            blocking.add(new ValidationIssue("selection_missing", "No zones selected.", ValidationSeverity.BLOCKING));
            return;
        }
        for (ZoneRole requiredRole : definition.requiredZones()) {
            if (!BuildingValidationSupport.toRoleMap(request.zones()).containsKey(requiredRole)) {
                blocking.add(new ValidationIssue("required_zone_missing", "Required zone is missing: " + requiredRole.name(), ValidationSeverity.BLOCKING));
            }
        }
        for (ZoneSelection zone : request.zones()) {
            if (zone.volume() <= 0) {
                blocking.add(new ValidationIssue("invalid_zone_volume", "Zone has invalid volume.", ValidationSeverity.BLOCKING));
            } else if (zone.volume() > MAX_ZONE_VOLUME) {
                blocking.add(new ValidationIssue("zone_too_large", "Zone exceeds max MVP volume: " + zone.volume(), ValidationSeverity.BLOCKING));
            }
        }
        if (!BuildingValidationSupport.isAnchorCovered(request.anchorPos(), request.zones())) {
            blocking.add(new ValidationIssue("anchor_outside_selection", "Anchor must be inside at least one selected zone.", ValidationSeverity.BLOCKING));
        }
    }

    private void validateOverlap(ServerLevel level, BuildingValidationRequest request, List<ValidationIssue> blocking) {
        ValidatedBuildingSnapshot snapshot = BuildingValidationSupport.buildSnapshot(request);
        List<ValidatedBuildingRecord> intersecting = ValidatedBuildingRegistryData.get(level).findIntersecting(snapshot.bounds());
        for (ValidatedBuildingRecord existing : intersecting) {
            if (!existing.settlementId().equals(request.settlementId()) || existing.type() == request.type()) {
                continue;
            }
            for (ZoneSelection incomingZone : request.zones()) {
                for (ZoneSelection existingZone : existing.zones()) {
                    if (incomingZone == null || existingZone == null
                            || !PROHIBITED_OVERLAP_ROLE_PAIRS.contains(new RolePair(incomingZone.role(), existingZone.role()))
                            || !BuildingValidationSupport.zonesOverlapByBlockVolume(incomingZone, existingZone)) {
                        continue;
                    }
                    blocking.add(new ValidationIssue("overlap_conflict", "Building overlaps conflicting zones (" + incomingZone.role().name()
                            + " vs " + existingZone.role().name() + ") with existing " + existing.type().name() + ".", ValidationSeverity.BLOCKING));
                    return;
                }
            }
        }
    }

    private static Set<RolePair> prohibitedRolePairs() {
        Set<RolePair> pairs = new HashSet<>();
        List<ZoneRole> primaryRoles = Arrays.asList(ZoneRole.INTERIOR, ZoneRole.SLEEPING, ZoneRole.WORK_ZONE);
        for (ZoneRole left : primaryRoles) {
            for (ZoneRole right : primaryRoles) {
                pairs.add(new RolePair(left, right));
            }
        }
        return Set.copyOf(pairs);
    }

    private record RolePair(ZoneRole left, ZoneRole right) {
    }
}
