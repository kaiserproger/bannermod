package com.talhanation.bannermod.settlement.validation;

import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.settlement.building.BuildingDefinition;
import com.talhanation.bannermod.settlement.building.BuildingDefinitionRegistry;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.types.BuildingTypeValidatorDispatcher;
import com.talhanation.bannermod.settlement.validation.types.BuildingValidationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultBuildingValidator implements BuildingValidator {
    private static final int MAX_ZONE_VOLUME = 262_144;
    private static final Set<RolePair> PROHIBITED_OVERLAP_ROLE_PAIRS = prohibitedRolePairs();

    private final BuildingDefinitionRegistry definitionRegistry;
    private final BuildingTypeValidatorDispatcher typeValidatorDispatcher;

    public DefaultBuildingValidator(BuildingDefinitionRegistry definitionRegistry) {
        this(definitionRegistry, new BuildingTypeValidatorDispatcher());
    }

    public DefaultBuildingValidator(BuildingDefinitionRegistry definitionRegistry,
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

        BuildingDefinition definition = optionalDefinition.get();
        List<ValidationIssue> blocking = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();

        validateSelection(request, definition, blocking);
        if (request.enforceOverlapChecks()) {
            validateOverlap(level, request, blocking);
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, null);
        }

        EnumMap<ZoneRole, ZoneSelection> zonesByRole = toRoleMap(request.zones());
        BuildingValidationContext context = new BuildingValidationContext(level, player, request, zonesByRole, warnings, blocking);
        return this.typeValidatorDispatcher.validate(context, this::validateByTypeFallback);
    }

    private BuildingValidationResult validateByTypeFallback(BuildingValidationContext context) {
        BuildingValidationRequest request = context.request();
        Map<ZoneRole, ZoneSelection> zonesByRole = context.zonesByRole();
        List<ValidationIssue> warnings = context.warnings();
        List<ValidationIssue> blocking = context.blocking();

        return switch (request.type()) {
            case STARTER_FORT -> validateStarterFort(context.level(), request, zonesByRole, warnings, blocking);
            case HOUSE -> validateHouse(context.level(), request, zonesByRole, warnings, blocking);
            case FARM -> validateFarm(context.level(), request, zonesByRole, warnings, blocking);
            case MINE -> validateMine(context.level(), request, zonesByRole, warnings, blocking);
            case LUMBER_CAMP -> validateLumberCamp(context.level(), request, zonesByRole, warnings, blocking);
            case SMITHY -> validateSmithy(context.level(), request, zonesByRole, warnings, blocking);
            case STORAGE -> validateStorage(context.level(), request, zonesByRole, warnings, blocking);
            case ARCHITECT_WORKSHOP -> validateArchitectWorkshop(context.level(), request, zonesByRole, warnings, blocking);
            case BARRACKS -> validateBarracks(context.level(), request, zonesByRole, warnings, blocking);
            default -> BuildingValidationResult.blockingFailure(request.type(), "validator_not_implemented", "Validator pipeline for this building type is not implemented yet.");
        };
    }

    @Override
    public BuildingValidationResult revalidate(ServerLevel level, ValidatedBuildingRecord building) {
        if (level == null || building == null) {
            return BuildingValidationResult.blockingFailure(null, "invalid_revalidation_request", "Revalidation request is missing.");
        }
        BuildingValidationRequest request = new BuildingValidationRequest(
                building.settlementId(),
                building.type(),
                building.anchorPos(),
                building.zones(),
                false
        );
        BuildingValidationResult result = validate(level, null, request);
        if (result.valid() || building.type() != BuildingType.HOUSE) {
            return result;
        }
        // Recovery is meant to repair a previously-validated house whose zones became stale;
        // a record with no zones at all (e.g. test fixture or corrupted persistence) has nothing
        // to repair, so latching onto any nearby bed in the world would be a false positive.
        if (building.zones().isEmpty()) {
            return result;
        }

        BuildingValidationRequest recoveredHouseRequest = tryRecoverHouseRequest(level, building);
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
        EnumMap<ZoneRole, ZoneSelection> zonesByRole = toRoleMap(request.zones());
        for (ZoneRole requiredRole : definition.requiredZones()) {
            if (!zonesByRole.containsKey(requiredRole)) {
                blocking.add(new ValidationIssue(
                        "required_zone_missing",
                        "Required zone is missing: " + requiredRole.name(),
                        ValidationSeverity.BLOCKING
                ));
            }
        }
        for (ZoneSelection zone : request.zones()) {
            if (zone.volume() <= 0) {
                blocking.add(new ValidationIssue("invalid_zone_volume", "Zone has invalid volume.", ValidationSeverity.BLOCKING));
                continue;
            }
            if (zone.volume() > MAX_ZONE_VOLUME) {
                blocking.add(new ValidationIssue(
                        "zone_too_large",
                        "Zone exceeds max MVP volume: " + zone.volume(),
                        ValidationSeverity.BLOCKING
                ));
            }
        }
        if (!isAnchorCovered(request.anchorPos(), request.zones())) {
            blocking.add(new ValidationIssue(
                    "anchor_outside_selection",
                    "Anchor must be inside at least one selected zone.",
                    ValidationSeverity.BLOCKING
            ));
        }
    }

    private void validateOverlap(ServerLevel level,
                                 BuildingValidationRequest request,
                                 List<ValidationIssue> blocking) {
        ValidatedBuildingSnapshot snapshot = buildSnapshot(request);
        List<ValidatedBuildingRecord> intersecting = ValidatedBuildingRegistryData.get(level).findIntersecting(snapshot.bounds());
        for (ValidatedBuildingRecord existing : intersecting) {
            if (!existing.settlementId().equals(request.settlementId())) {
                continue;
            }
            if (existing.type() == request.type()) {
                continue;
            }
            for (ZoneSelection incomingZone : request.zones()) {
                if (incomingZone == null) {
                    continue;
                }
                for (ZoneSelection existingZone : existing.zones()) {
                    if (existingZone == null) {
                        continue;
                    }
                    if (!PROHIBITED_OVERLAP_ROLE_PAIRS.contains(new RolePair(incomingZone.role(), existingZone.role()))) {
                        continue;
                    }
                    if (!zonesOverlapByBlockVolume(incomingZone, existingZone)) {
                        continue;
                    }
                    blocking.add(new ValidationIssue(
                            "overlap_conflict",
                            "Building overlaps conflicting zones (" + incomingZone.role().name() + " vs " + existingZone.role().name() + ") with existing " + existing.type().name() + ".",
                            ValidationSeverity.BLOCKING
                    ));
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

    private BuildingValidationResult validateStarterFort(ServerLevel level,
                                                         BuildingValidationRequest request,
                                                         Map<ZoneRole, ZoneSelection> zonesByRole,
                                                         List<ValidationIssue> warnings,
                                                         List<ValidationIssue> blocking) {
        ZoneSelection interior = zonesByRole.get(ZoneRole.INTERIOR);
        if (interior == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "interior_missing", "Fort interior zone is required.");
        }
        ZoneSelection authorityPoint = zonesByRole.get(ZoneRole.AUTHORITY_POINT);
        if (authorityPoint == null || !authorityPoint.contains(request.anchorPos())) {
            warnings.add(new ValidationIssue("fort_authority_unclear", "Fort authority point is missing or does not include anchor.", ValidationSeverity.WARNING));
        }

        int bannerRadius = WorkersServerConfig.settlementFortBannerMaxDistance();
        if (!hasBannerNearAnchor(level, request.anchorPos(), bannerRadius)) {
            warnings.add(new ValidationIssue("banner_missing", "No banner found within " + bannerRadius + " blocks of fort anchor.", ValidationSeverity.WARNING));
        }

        InteriorStats stats = scanInterior(level, interior);
        if (stats.walkableBlocks < 64) {
            blocking.add(new ValidationIssue("fort_walkable_too_small", "Fort interior needs at least 64 walkable blocks around the courtyard and wings.", ValidationSeverity.BLOCKING));
        }
        if (stats.roofCoverage < 0.60D) {
            warnings.add(new ValidationIssue("fort_roof_too_open", "Fort roof coverage is low. A more sheltered interior is recommended.", ValidationSeverity.WARNING));
        }
        if (!hasEntrance(interior, level)) {
            warnings.add(new ValidationIssue("fort_entrance_unclear", "Fort entrance is unclear for current selection; manual review recommended.", ValidationSeverity.WARNING));
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }

        int qualityScore = Math.min(100, (int) Math.round((stats.roofCoverage * 0.70D + Math.min(1.0D, stats.walkableBlocks / 128.0D) * 0.30D) * 100.0D));
        return BuildingValidationResult.success(request.type(), 4, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateHouse(ServerLevel level,
                                                   BuildingValidationRequest request,
                                                   Map<ZoneRole, ZoneSelection> zonesByRole,
                                                   List<ValidationIssue> warnings,
                                                   List<ValidationIssue> blocking) {
        ZoneSelection interior = zonesByRole.get(ZoneRole.INTERIOR);
        ZoneSelection sleeping = zonesByRole.get(ZoneRole.SLEEPING);
        if (interior == null || sleeping == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "house_zones_missing", "House requires INTERIOR and SLEEPING zones.");
        }

        InteriorStats stats = scanInterior(level, interior);
        int validBeds = countBeds(level, sleeping);
        if (stats.walkableBlocks < 8) {
            blocking.add(new ValidationIssue("house_walkable_too_small", "House requires at least 8 walkable interior blocks.", ValidationSeverity.BLOCKING));
        }
        if (stats.roofCoverage < 0.70D) {
            blocking.add(new ValidationIssue("house_roof_too_open", "House requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }
        if (validBeds < 1) {
            validBeds = countBedsNearZone(level, sleeping, 1);
        }
        if (validBeds < 1) {
            validBeds = countBeds(level, interior);
        }
        if (validBeds < 1) {
            validBeds = countBedsNearZone(level, interior, 1);
        }
        if (validBeds < 1 && findNearestBed(level, request.anchorPos(), 12) != null) {
            validBeds = 1;
        }
        if (validBeds < 1) {
            blocking.add(new ValidationIssue("house_bed_missing", "House requires at least one bed in sleeping zone.", ValidationSeverity.BLOCKING));
        }
        if (!hasEntrance(interior, level)) {
            warnings.add(new ValidationIssue("house_entrance_missing", "House entrance is unclear for current selection.", ValidationSeverity.WARNING));
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }

        int capacity = Math.min(validBeds, stats.walkableBlocks / 8);
        if (capacity < 1) {
            capacity = 1;
            warnings.add(new ValidationIssue("house_capacity_clamped", "House passed with minimum capacity due to tight interior space.", ValidationSeverity.WARNING));
        }
        int qualityScore = Math.min(100, (int) Math.round(stats.roofCoverage * 100.0D));
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateFarm(ServerLevel level,
                                                  BuildingValidationRequest request,
                                                  Map<ZoneRole, ZoneSelection> zonesByRole,
                                                  List<ValidationIssue> warnings,
                                                  List<ValidationIssue> blocking) {
        ZoneSelection workZone = zonesByRole.get(ZoneRole.WORK_ZONE);
        if (workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "farm_work_zone_missing", "Farm requires a WORK_ZONE.");
        }
        double anchorDistance = distanceToZone(request.anchorPos(), workZone);
        if (anchorDistance > 24.0D) {
            blocking.add(new ValidationIssue("farm_anchor_too_far", "Farm anchor must be within 24 blocks of work zone.", ValidationSeverity.BLOCKING));
        }

        int farmlandBlocks = countFarmlandBlocks(level, workZone);
        if (farmlandBlocks < 24) {
            blocking.add(new ValidationIssue("farm_farmland_too_small", "Farm requires at least 24 farmland/crop-capable blocks.", ValidationSeverity.BLOCKING));
        }

        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }
        int capacity = clamp(farmlandBlocks / 48, 1, 4);
        int qualityScore = Math.min(100, farmlandBlocks);
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateMine(ServerLevel level,
                                                  BuildingValidationRequest request,
                                                  Map<ZoneRole, ZoneSelection> zonesByRole,
                                                  List<ValidationIssue> warnings,
                                                  List<ValidationIssue> blocking) {
        ZoneSelection workZone = zonesByRole.get(ZoneRole.WORK_ZONE);
        if (workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "mine_work_zone_missing", "Mine requires a WORK_ZONE.");
        }
        double anchorDistance = distanceToZone(request.anchorPos(), workZone);
        if (anchorDistance > 32.0D) {
            blocking.add(new ValidationIssue("mine_anchor_too_far", "Mine anchor must be within 32 blocks of work zone.", ValidationSeverity.BLOCKING));
        }
        if (level.canSeeSky(request.anchorPos().above())) {
            warnings.add(new ValidationIssue("mine_anchor_unsheltered", "Mine anchor is exposed to sky. A covered shed is recommended.", ValidationSeverity.WARNING));
        }

        int validMineFaceBlocks = countMineFaceBlocks(level, workZone);
        if (validMineFaceBlocks < 24) {
            blocking.add(new ValidationIssue("mine_face_too_small", "Mine requires at least 24 exposed stone/ore/deepslate blocks.", ValidationSeverity.BLOCKING));
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }
        int capacity = clamp(1 + (validMineFaceBlocks / 64), 1, 4);
        int qualityScore = Math.min(100, validMineFaceBlocks);
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateLumberCamp(ServerLevel level,
                                                        BuildingValidationRequest request,
                                                        Map<ZoneRole, ZoneSelection> zonesByRole,
                                                        List<ValidationIssue> warnings,
                                                        List<ValidationIssue> blocking) {
        ZoneSelection workZone = zonesByRole.get(ZoneRole.WORK_ZONE);
        if (workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "lumber_work_zone_missing", "Lumber camp requires a WORK_ZONE.");
        }
        double anchorDistance = distanceToZone(request.anchorPos(), workZone);
        if (anchorDistance > 32.0D) {
            blocking.add(new ValidationIssue("lumber_anchor_too_far", "Lumber camp anchor must be within 32 blocks of work zone.", ValidationSeverity.BLOCKING));
        }

        int logCount = countLogs(level, workZone);
        int saplingCount = countSaplings(level, workZone);
        int productivity = logCount + (saplingCount / 2);
        if (productivity < 12) {
            blocking.add(new ValidationIssue("lumber_resources_too_small", "Lumber camp requires enough logs/saplings in zone.", ValidationSeverity.BLOCKING));
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }
        int capacity = clamp(productivity / 12, 1, 3);
        int qualityScore = Math.min(100, productivity * 2);
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateSmithy(ServerLevel level,
                                                    BuildingValidationRequest request,
                                                    Map<ZoneRole, ZoneSelection> zonesByRole,
                                                    List<ValidationIssue> warnings,
                                                    List<ValidationIssue> blocking) {
        ZoneSelection interior = zonesByRole.get(ZoneRole.INTERIOR);
        ZoneSelection workZone = zonesByRole.get(ZoneRole.WORK_ZONE);
        if (interior == null || workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "smithy_zones_missing", "Smithy requires INTERIOR and WORK_ZONE zones.");
        }

        InteriorStats interiorStats = scanInterior(level, interior);
        if (interiorStats.roofCoverage < 0.70D) {
            blocking.add(new ValidationIssue("smithy_roof_too_open", "Smithy requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }

        List<BlockPos> anvilPositions = collectPositions(level, workZone, state -> state.getBlock() instanceof AnvilBlock);
        List<BlockPos> furnacePositions = collectPositions(level, workZone, state -> state.getBlock() instanceof FurnaceBlock || state.getBlock() instanceof BlastFurnaceBlock);
        if (anvilPositions.isEmpty()) {
            blocking.add(new ValidationIssue("smithy_anvil_missing", "Smithy requires at least one anvil in work zone.", ValidationSeverity.BLOCKING));
        }
        if (furnacePositions.isEmpty()) {
            blocking.add(new ValidationIssue("smithy_furnace_missing", "Smithy requires at least one furnace or blast furnace in work zone.", ValidationSeverity.BLOCKING));
        }
        if (!anvilPositions.isEmpty() && !furnacePositions.isEmpty() && !hasCloseAnvilFurnacePair(anvilPositions, furnacePositions, 4.0D)) {
            blocking.add(new ValidationIssue("smithy_anchor_set_too_far", "Anvil must be within 4 blocks of a furnace or blast furnace.", ValidationSeverity.BLOCKING));
        }

        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }
        int anchorSets = Math.min(anvilPositions.size(), furnacePositions.size());
        int capacity = Math.min(Math.min(anchorSets, interiorStats.walkableBlocks / 16), 2);
        if (capacity < 1) {
            capacity = 1;
            warnings.add(new ValidationIssue("smithy_capacity_clamped", "Smithy passed with minimum capacity due to limited interior space.", ValidationSeverity.WARNING));
        }
        int qualityScore = Math.min(100, (int) Math.round(interiorStats.roofCoverage * 100.0D));
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateStorage(ServerLevel level,
                                                     BuildingValidationRequest request,
                                                     Map<ZoneRole, ZoneSelection> zonesByRole,
                                                     List<ValidationIssue> warnings,
                                                     List<ValidationIssue> blocking) {
        ZoneSelection storageZone = zonesByRole.get(ZoneRole.STORAGE);
        if (storageZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "storage_zone_missing", "Storage requires a STORAGE zone.");
        }

        int containerCount = countContainers(level, storageZone);
        if (containerCount < 1) {
            blocking.add(new ValidationIssue("storage_containers_missing", "Storage requires at least one container (chest/barrel).", ValidationSeverity.BLOCKING));
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }
        int qualityScore = Math.min(100, containerCount * 10);
        return BuildingValidationResult.success(request.type(), 0, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateArchitectWorkshop(ServerLevel level,
                                                               BuildingValidationRequest request,
                                                               Map<ZoneRole, ZoneSelection> zonesByRole,
                                                               List<ValidationIssue> warnings,
                                                               List<ValidationIssue> blocking) {
        ZoneSelection interior = zonesByRole.get(ZoneRole.INTERIOR);
        ZoneSelection workZone = zonesByRole.get(ZoneRole.WORK_ZONE);
        if (interior == null || workZone == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "architect_zones_missing", "Architect workshop requires INTERIOR and WORK_ZONE zones.");
        }
        InteriorStats interiorStats = scanInterior(level, interior);
        if (interiorStats.walkableBlocks < 16) {
            blocking.add(new ValidationIssue("architect_walkable_too_small", "Architect workshop requires at least 16 walkable interior blocks.", ValidationSeverity.BLOCKING));
        }
        if (interiorStats.roofCoverage < 0.70D) {
            blocking.add(new ValidationIssue("architect_roof_too_open", "Architect workshop requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }

        int draftingTables = countBlocks(level, workZone, Blocks.CRAFTING_TABLE);
        if (draftingTables < 1) {
            blocking.add(new ValidationIssue("architect_table_missing", "Architect workshop requires at least one drafting table (crafting table placeholder).", ValidationSeverity.BLOCKING));
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }

        int capacityByArea = Math.max(1, interiorStats.walkableBlocks / 24);
        int capacity = Math.min(draftingTables, capacityByArea);
        int qualityScore = Math.min(100, (int) Math.round(interiorStats.roofCoverage * 100.0D));
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, warnings, buildSnapshot(request));
    }

    private BuildingValidationResult validateBarracks(ServerLevel level,
                                                      BuildingValidationRequest request,
                                                      Map<ZoneRole, ZoneSelection> zonesByRole,
                                                      List<ValidationIssue> warnings,
                                                      List<ValidationIssue> blocking) {
        ZoneSelection interior = zonesByRole.get(ZoneRole.INTERIOR);
        ZoneSelection sleeping = zonesByRole.get(ZoneRole.SLEEPING);
        ZoneSelection storage = zonesByRole.get(ZoneRole.STORAGE);
        if (interior == null || sleeping == null || storage == null) {
            return BuildingValidationResult.blockingFailure(request.type(), "barracks_zones_missing", "Barracks requires INTERIOR, SLEEPING, and STORAGE zones.");
        }

        InteriorStats interiorStats = scanInterior(level, interior);
        if (interiorStats.walkableBlocks < 16) {
            blocking.add(new ValidationIssue("barracks_walkable_too_small", "Barracks requires at least 16 walkable interior blocks.", ValidationSeverity.BLOCKING));
        }
        if (interiorStats.roofCoverage < 0.70D) {
            blocking.add(new ValidationIssue("barracks_roof_too_open", "Barracks requires at least 70% roof coverage.", ValidationSeverity.BLOCKING));
        }

        int beds = countBeds(level, sleeping);
        if (beds < 2) {
            beds = Math.max(beds, countBedsNearZone(level, sleeping, 1));
        }
        if (beds < 2) {
            blocking.add(new ValidationIssue("barracks_beds_missing", "Barracks requires at least two beds or bunks in the sleeping zone.", ValidationSeverity.BLOCKING));
        }

        int containers = countContainers(level, storage);
        if (containers < 1) {
            blocking.add(new ValidationIssue("barracks_storage_missing", "Barracks requires at least one chest or barrel in the storage zone.", ValidationSeverity.BLOCKING));
        }
        if (!hasEntrance(interior, level)) {
            warnings.add(new ValidationIssue("barracks_entrance_unclear", "Barracks entrance is unclear for current selection.", ValidationSeverity.WARNING));
        }
        if (!blocking.isEmpty()) {
            return new BuildingValidationResult(false, request.type(), 0, 0, blocking, warnings, buildSnapshot(request));
        }

        int capacity = clamp(Math.max(1, beds), 1, 4);
        int qualityScore = Math.min(100, (int) Math.round(interiorStats.roofCoverage * 100.0D));
        return BuildingValidationResult.success(request.type(), capacity, qualityScore, warnings, buildSnapshot(request));
    }

    private static EnumMap<ZoneRole, ZoneSelection> toRoleMap(List<ZoneSelection> zones) {
        EnumMap<ZoneRole, ZoneSelection> map = new EnumMap<>(ZoneRole.class);
        for (ZoneSelection zone : zones) {
            if (zone == null) {
                continue;
            }
            map.putIfAbsent(zone.role(), zone);
        }
        return map;
    }

    private static boolean isAnchorCovered(BlockPos anchor, List<ZoneSelection> zones) {
        if (anchor == null) {
            return false;
        }
        for (ZoneSelection zone : zones) {
            if (zone != null && zone.contains(anchor)) {
                return true;
            }
        }
        return false;
    }

    private static InteriorStats scanInterior(ServerLevel level, ZoneSelection interior) {
        int minX = Math.min(interior.min().getX(), interior.max().getX());
        int minY = Math.min(interior.min().getY(), interior.max().getY());
        int minZ = Math.min(interior.min().getZ(), interior.max().getZ());
        int maxX = Math.max(interior.min().getX(), interior.max().getX());
        int maxY = Math.max(interior.min().getY(), interior.max().getY());
        int maxZ = Math.max(interior.min().getZ(), interior.max().getZ());

        int walkable = 0;
        int roofed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos offsetPos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    BlockState above = level.getBlockState(offsetPos.setWithOffset(pos, 0, 1, 0));
                    BlockState below = level.getBlockState(offsetPos.setWithOffset(pos, 0, -1, 0));
                    if (!state.isAir() || !above.isAir() || !below.isSolid()) {
                        continue;
                    }
                    walkable++;
                    if (hasRoofCover(level, pos)) {
                        roofed++;
                    }
                }
            }
        }
        double roofCoverage = walkable == 0 ? 0.0D : (double) roofed / (double) walkable;
        return new InteriorStats(walkable, roofCoverage);
    }

    private static boolean hasRoofCover(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos roofPos = new BlockPos.MutableBlockPos();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + 8);
        for (int y = pos.getY() + 2; y <= maxY; y++) {
            roofPos.set(pos.getX(), y, pos.getZ());
            if (!level.getBlockState(roofPos).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static int countBeds(ServerLevel level, ZoneSelection zone) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());

        int beds = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (level.getBlockState(pos.set(x, y, z)).getBlock() instanceof BedBlock) {
                        beds++;
                    }
                }
            }
        }
        return beds;
    }

    private static int countBedsNearZone(ServerLevel level, ZoneSelection zone, int expansion) {
        int minX = Math.min(zone.min().getX(), zone.max().getX()) - expansion;
        int minY = Math.min(zone.min().getY(), zone.max().getY()) - expansion;
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ()) - expansion;
        int maxX = Math.max(zone.min().getX(), zone.max().getX()) + expansion;
        int maxY = Math.max(zone.min().getY(), zone.max().getY()) + expansion;
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ()) + expansion;
        int beds = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (level.getBlockState(pos.set(x, y, z)).getBlock() instanceof BedBlock) {
                        beds++;
                    }
                }
            }
        }
        return beds;
    }

    private static BuildingValidationRequest tryRecoverHouseRequest(ServerLevel level, ValidatedBuildingRecord building) {
        BlockPos bedPos = findNearestBed(level, building.anchorPos(), 12);
        if (bedPos == null) {
            return null;
        }
        ZoneSelection interior = new ZoneSelection(
                ZoneRole.INTERIOR,
                bedPos.offset(-1, 0, -1),
                bedPos.offset(2, 1, 2),
                bedPos
        );
        ZoneSelection sleeping = new ZoneSelection(
                ZoneRole.SLEEPING,
                bedPos,
                bedPos,
                bedPos
        );
        return new BuildingValidationRequest(
                building.settlementId(),
                BuildingType.HOUSE,
                bedPos,
                List.of(interior, sleeping),
                false
        );
    }

    private static BlockPos findNearestBed(ServerLevel level, BlockPos origin, int radius) {
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int y = origin.getY() - radius; y <= origin.getY() + radius; y++) {
                for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                    pos.set(x, y, z);
                    if (!(level.getBlockState(pos).getBlock() instanceof BedBlock)) {
                        continue;
                    }
                    double distance = origin.distSqr(pos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearest = pos.immutable();
                    }
                }
            }
        }
        return nearest;
    }

    private static int countFarmlandBlocks(ServerLevel level, ZoneSelection zone) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());

        int farmland = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    BlockState above = level.getBlockState(abovePos.setWithOffset(pos, 0, 1, 0));
                    if (state.is(Blocks.FARMLAND) || above.getBlock() instanceof CropBlock) {
                        farmland++;
                    }
                }
            }
        }
        return farmland;
    }

    private static int countContainers(ServerLevel level, ZoneSelection zone) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());

        int containers = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Object blockEntity = level.getBlockEntity(pos.set(x, y, z));
                    if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity) {
                        containers++;
                    }
                }
            }
        }
        return containers;
    }

    private static int countMineFaceBlocks(ServerLevel level, ZoneSelection zone) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());

        int blocks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLESTONE)
                            || state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) || state.is(Blocks.COPPER_ORE)
                            || state.is(Blocks.GOLD_ORE) || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.LAPIS_ORE)
                            || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.EMERALD_ORE)) {
                        blocks++;
                    }
                }
            }
        }
        return blocks;
    }

    private static int countLogs(ServerLevel level, ZoneSelection zone) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());
        int logs = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.getBlock() instanceof RotatedPillarBlock && state.is(net.minecraft.tags.BlockTags.LOGS)) {
                        logs++;
                    }
                }
            }
        }
        return logs;
    }

    private static int countSaplings(ServerLevel level, ZoneSelection zone) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());
        int saplings = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.getBlock() instanceof SaplingBlock) {
                        saplings++;
                    }
                }
            }
        }
        return saplings;
    }

    private static int countBlocks(ServerLevel level, ZoneSelection zone, net.minecraft.world.level.block.Block block) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());

        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (level.getBlockState(pos.set(x, y, z)).is(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static List<BlockPos> collectPositions(ServerLevel level, ZoneSelection zone, java.util.function.Predicate<BlockState> predicate) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());

        List<BlockPos> positions = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (predicate.test(level.getBlockState(pos))) {
                        positions.add(pos.immutable());
                    }
                }
            }
        }
        return positions;
    }

    private static boolean hasCloseAnvilFurnacePair(List<BlockPos> anvils, List<BlockPos> furnaces, double maxDistance) {
        double maxDistanceSqr = maxDistance * maxDistance;
        for (BlockPos anvil : anvils) {
            for (BlockPos furnace : furnaces) {
                if (anvil.distSqr(furnace) <= maxDistanceSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double distanceToZone(BlockPos anchorPos, ZoneSelection zone) {
        int minX = Math.min(zone.min().getX(), zone.max().getX());
        int minY = Math.min(zone.min().getY(), zone.max().getY());
        int minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        int maxX = Math.max(zone.min().getX(), zone.max().getX());
        int maxY = Math.max(zone.min().getY(), zone.max().getY());
        int maxZ = Math.max(zone.min().getZ(), zone.max().getZ());

        int cx = clamp(anchorPos.getX(), minX, maxX);
        int cy = clamp(anchorPos.getY(), minY, maxY);
        int cz = clamp(anchorPos.getZ(), minZ, maxZ);
        return Math.sqrt(anchorPos.distSqr(new BlockPos(cx, cy, cz)));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hasBannerNearAnchor(ServerLevel level, BlockPos anchorPos, int radius) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = anchorPos.getX() - radius; x <= anchorPos.getX() + radius; x++) {
            for (int y = anchorPos.getY() - radius; y <= anchorPos.getY() + radius; y++) {
                for (int z = anchorPos.getZ() - radius; z <= anchorPos.getZ() + radius; z++) {
                    if (level.getBlockState(pos.set(x, y, z)).getBlock() instanceof BannerBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasEntrance(ZoneSelection interior, ServerLevel level) {
        int minX = Math.min(interior.min().getX(), interior.max().getX());
        int minY = Math.min(interior.min().getY(), interior.max().getY());
        int minZ = Math.min(interior.min().getZ(), interior.max().getZ());
        int maxX = Math.max(interior.min().getX(), interior.max().getX());
        int maxY = Math.max(interior.min().getY(), interior.max().getY());
        int maxZ = Math.max(interior.min().getZ(), interior.max().getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean boundary = x == minX || x == maxX || z == minZ || z == maxZ;
                    if (!boundary) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    BlockState above = level.getBlockState(abovePos.setWithOffset(pos, 0, 1, 0));
                    if (!state.isAir() || !above.isAir()) {
                        continue;
                    }
                    if (hasOutsideAirAdjacent(pos, level)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasOutsideAirAdjacent(BlockPos pos, ServerLevel level) {
        for (BlockPos adjacent : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
            if (level.getBlockState(adjacent).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static ValidatedBuildingSnapshot buildSnapshot(BuildingValidationRequest request) {
        if (request.zones().isEmpty()) {
            return new ValidatedBuildingSnapshot(request.anchorPos(), new AABB(request.anchorPos()), List.of());
        }
        AABB bounds = request.zones().stream()
                .map(ZoneSelection::toAabb)
                .min(Comparator.comparingDouble(aabb -> aabb.minX + aabb.minY + aabb.minZ))
                .orElse(new AABB(request.anchorPos()));
        for (ZoneSelection zone : request.zones()) {
            bounds = bounds.minmax(zone.toAabb());
        }
        return new ValidatedBuildingSnapshot(request.anchorPos(), bounds, request.zones());
    }

    private static boolean zonesOverlapByBlockVolume(ZoneSelection left, ZoneSelection right) {
        return rangesOverlap(
                Math.min(left.min().getX(), left.max().getX()),
                Math.max(left.min().getX(), left.max().getX()),
                Math.min(right.min().getX(), right.max().getX()),
                Math.max(right.min().getX(), right.max().getX()))
                && rangesOverlap(
                Math.min(left.min().getY(), left.max().getY()),
                Math.max(left.min().getY(), left.max().getY()),
                Math.min(right.min().getY(), right.max().getY()),
                Math.max(right.min().getY(), right.max().getY()))
                && rangesOverlap(
                Math.min(left.min().getZ(), left.max().getZ()),
                Math.max(left.min().getZ(), left.max().getZ()),
                Math.min(right.min().getZ(), right.max().getZ()),
                Math.max(right.min().getZ(), right.max().getZ()));
    }

    private static boolean rangesOverlap(int leftMin, int leftMax, int rightMin, int rightMax) {
        return Math.max(leftMin, rightMin) <= Math.min(leftMax, rightMax);
    }

    private record InteriorStats(int walkableBlocks, double roofCoverage) {
    }

    private record RolePair(ZoneRole left, ZoneRole right) {
    }
}
