package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.ValidatedBuildingSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Predicate;

public final class BuildingValidationSupport {
    private BuildingValidationSupport() {
    }

    public static EnumMap<ZoneRole, ZoneSelection> toRoleMap(List<ZoneSelection> zones) {
        EnumMap<ZoneRole, ZoneSelection> map = new EnumMap<>(ZoneRole.class);
        for (ZoneSelection zone : zones) {
            if (zone != null) {
                map.putIfAbsent(zone.role(), zone);
            }
        }
        return map;
    }

    public static boolean isAnchorCovered(BlockPos anchor, List<ZoneSelection> zones) {
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

    public static InteriorStats scanInterior(ServerLevel level, ZoneSelection interior) {
        int walkable = 0;
        int roofed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos offsetPos = new BlockPos.MutableBlockPos();
        for (int x = minX(interior); x <= maxX(interior); x++) {
            for (int y = minY(interior); y <= maxY(interior); y++) {
                for (int z = minZ(interior); z <= maxZ(interior); z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    BlockState above = level.getBlockState(offsetPos.setWithOffset(pos, 0, 1, 0));
                    BlockState below = level.getBlockState(offsetPos.setWithOffset(pos, 0, -1, 0));
                    if (state.isAir() && above.isAir() && below.isSolid()) {
                        walkable++;
                        roofed += hasRoofCover(level, pos) ? 1 : 0;
                    }
                }
            }
        }
        return new InteriorStats(walkable, walkable == 0 ? 0.0D : (double) roofed / (double) walkable);
    }

    public static int countBeds(ServerLevel level, ZoneSelection zone) {
        return countMatchingBlocks(level, zone, state -> state.getBlock() instanceof BedBlock);
    }

    public static int countBedsNearZone(ServerLevel level, ZoneSelection zone, int expansion) {
        return countMatchingBlocks(level, zone, expansion, state -> state.getBlock() instanceof BedBlock);
    }

    public static BuildingValidationRequest tryRecoverHouseRequest(ServerLevel level, ValidatedBuildingRecord building) {
        BlockPos bedPos = findNearestBed(level, building.anchorPos(), 12);
        if (bedPos == null) {
            return null;
        }
        ZoneSelection interior = new ZoneSelection(ZoneRole.INTERIOR, bedPos.offset(-1, 0, -1), bedPos.offset(2, 1, 2), bedPos);
        ZoneSelection sleeping = new ZoneSelection(ZoneRole.SLEEPING, bedPos, bedPos, bedPos);
        return new BuildingValidationRequest(building.settlementId(), BuildingType.HOUSE, bedPos, List.of(interior, sleeping), false);
    }

    public static BlockPos findNearestBed(ServerLevel level, BlockPos origin, int radius) {
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int y = origin.getY() - radius; y <= origin.getY() + radius; y++) {
                for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof BedBlock && origin.distSqr(pos) < bestDistance) {
                        bestDistance = origin.distSqr(pos);
                        nearest = pos.immutable();
                    }
                }
            }
        }
        return nearest;
    }

    public static int countFarmlandBlocks(ServerLevel level, ZoneSelection zone) {
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();
        return countMatchingBlocks(level, zone, (pos, state) -> state.is(Blocks.FARMLAND)
                || level.getBlockState(abovePos.setWithOffset(pos, 0, 1, 0)).getBlock() instanceof CropBlock);
    }

    public static int countContainers(ServerLevel level, ZoneSelection zone) {
        int containers = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX(zone); x <= maxX(zone); x++) {
            for (int y = minY(zone); y <= maxY(zone); y++) {
                for (int z = minZ(zone); z <= maxZ(zone); z++) {
                    Object blockEntity = level.getBlockEntity(pos.set(x, y, z));
                    containers += blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity ? 1 : 0;
                }
            }
        }
        return containers;
    }

    public static int countMineFaceBlocks(ServerLevel level, ZoneSelection zone) {
        return countMatchingBlocks(level, zone, state -> state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) || state.is(Blocks.COPPER_ORE)
                || state.is(Blocks.GOLD_ORE) || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.LAPIS_ORE)
                || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.EMERALD_ORE));
    }

    public static int countLogs(ServerLevel level, ZoneSelection zone) {
        return countMatchingBlocks(level, zone, state -> state.getBlock() instanceof RotatedPillarBlock && state.is(net.minecraft.tags.BlockTags.LOGS));
    }

    public static int countSaplings(ServerLevel level, ZoneSelection zone) {
        return countMatchingBlocks(level, zone, state -> state.getBlock() instanceof SaplingBlock);
    }

    public static int countBlocks(ServerLevel level, ZoneSelection zone, Block block) {
        return countMatchingBlocks(level, zone, state -> state.is(block));
    }

    public static List<BlockPos> collectPositions(ServerLevel level, ZoneSelection zone, Predicate<BlockState> predicate) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        forEach(zone, pos, current -> {
            if (predicate.test(level.getBlockState(current))) {
                positions.add(current.immutable());
            }
        });
        return positions;
    }

    public static boolean hasClosePair(List<BlockPos> leftPositions, List<BlockPos> rightPositions, double maxDistance) {
        double maxDistanceSqr = maxDistance * maxDistance;
        for (BlockPos left : leftPositions) {
            for (BlockPos right : rightPositions) {
                if (left.distSqr(right) <= maxDistanceSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    public static double distanceToZone(BlockPos anchorPos, ZoneSelection zone) {
        int cx = clamp(anchorPos.getX(), minX(zone), maxX(zone));
        int cy = clamp(anchorPos.getY(), minY(zone), maxY(zone));
        int cz = clamp(anchorPos.getZ(), minZ(zone), maxZ(zone));
        return Math.sqrt(anchorPos.distSqr(new BlockPos(cx, cy, cz)));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean hasBannerNearAnchor(ServerLevel level, BlockPos anchorPos, int radius) {
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

    public static boolean hasEntrance(ZoneSelection interior, ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();
        for (int x = minX(interior); x <= maxX(interior); x++) {
            for (int y = minY(interior); y <= maxY(interior); y++) {
                for (int z = minZ(interior); z <= maxZ(interior); z++) {
                    if (x != minX(interior) && x != maxX(interior) && z != minZ(interior) && z != maxZ(interior)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir() && level.getBlockState(abovePos.setWithOffset(pos, 0, 1, 0)).isAir()
                            && hasOutsideAirAdjacent(pos, level)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static ValidatedBuildingSnapshot buildSnapshot(BuildingValidationRequest request) {
        if (request.zones().isEmpty()) {
            return new ValidatedBuildingSnapshot(request.anchorPos(), new AABB(request.anchorPos()), List.of());
        }
        AABB bounds = request.zones().stream().map(ZoneSelection::toAabb)
                .min(Comparator.comparingDouble(aabb -> aabb.minX + aabb.minY + aabb.minZ)).orElse(new AABB(request.anchorPos()));
        for (ZoneSelection zone : request.zones()) {
            bounds = bounds.minmax(zone.toAabb());
        }
        return new ValidatedBuildingSnapshot(request.anchorPos(), bounds, request.zones());
    }

    public static boolean zonesOverlapByBlockVolume(ZoneSelection left, ZoneSelection right) {
        return rangesOverlap(minX(left), maxX(left), minX(right), maxX(right))
                && rangesOverlap(minY(left), maxY(left), minY(right), maxY(right))
                && rangesOverlap(minZ(left), maxZ(left), minZ(right), maxZ(right));
    }

    private static int countMatchingBlocks(ServerLevel level, ZoneSelection zone, Predicate<BlockState> predicate) {
        return countMatchingBlocks(level, zone, 0, predicate);
    }

    private static int countMatchingBlocks(ServerLevel level, ZoneSelection zone, int expansion, Predicate<BlockState> predicate) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int x = minX(zone) - expansion; x <= maxX(zone) + expansion; x++) {
            for (int y = minY(zone) - expansion; y <= maxY(zone) + expansion; y++) {
                for (int z = minZ(zone) - expansion; z <= maxZ(zone) + expansion; z++) {
                    count += predicate.test(level.getBlockState(pos.set(x, y, z))) ? 1 : 0;
                }
            }
        }
        return count;
    }

    private static int countMatchingBlocks(ServerLevel level, ZoneSelection zone, PositionedBlockPredicate predicate) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int x = minX(zone); x <= maxX(zone); x++) {
            for (int y = minY(zone); y <= maxY(zone); y++) {
                for (int z = minZ(zone); z <= maxZ(zone); z++) {
                    count += predicate.test(pos.set(x, y, z), level.getBlockState(pos)) ? 1 : 0;
                }
            }
        }
        return count;
    }

    private static void forEach(ZoneSelection zone, BlockPos.MutableBlockPos pos, java.util.function.Consumer<BlockPos> consumer) {
        for (int x = minX(zone); x <= maxX(zone); x++) {
            for (int y = minY(zone); y <= maxY(zone); y++) {
                for (int z = minZ(zone); z <= maxZ(zone); z++) {
                    consumer.accept(pos.set(x, y, z));
                }
            }
        }
    }

    private static boolean hasRoofCover(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos roofPos = new BlockPos.MutableBlockPos();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + 8);
        for (int y = pos.getY() + 2; y <= maxY; y++) {
            if (!level.getBlockState(roofPos.set(pos.getX(), y, pos.getZ())).isAir()) {
                return true;
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

    private static boolean rangesOverlap(int leftMin, int leftMax, int rightMin, int rightMax) {
        return Math.max(leftMin, rightMin) <= Math.min(leftMax, rightMax);
    }

    private static int minX(ZoneSelection zone) { return Math.min(zone.min().getX(), zone.max().getX()); }
    private static int minY(ZoneSelection zone) { return Math.min(zone.min().getY(), zone.max().getY()); }
    private static int minZ(ZoneSelection zone) { return Math.min(zone.min().getZ(), zone.max().getZ()); }
    private static int maxX(ZoneSelection zone) { return Math.max(zone.min().getX(), zone.max().getX()); }
    private static int maxY(ZoneSelection zone) { return Math.max(zone.min().getY(), zone.max().getY()); }
    private static int maxZ(ZoneSelection zone) { return Math.max(zone.min().getZ(), zone.max().getZ()); }

    public record InteriorStats(int walkableBlocks, double roofCoverage) {
    }

    private interface PositionedBlockPredicate {
        boolean test(BlockPos pos, BlockState state);
    }
}
