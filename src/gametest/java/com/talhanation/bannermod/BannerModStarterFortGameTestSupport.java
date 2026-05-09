package com.talhanation.bannermod;

import com.talhanation.bannermod.settlement.building.BuildingDefinitionRegistry;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.SettlementBuildingValidator;
import com.talhanation.bannermod.settlement.validation.StarterFortPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public final class BannerModStarterFortGameTestSupport {
    private BannerModStarterFortGameTestSupport() {
    }

    public static void buildValidFort(ServerLevel level, BlockPos anchor) {
        if (level == null || anchor == null) {
            return;
        }
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState palisade = Blocks.OAK_LOG.defaultBlockState();
        BlockState wing = Blocks.OAK_PLANKS.defaultBlockState();

        fillFloor(level, anchor, floor);
        clearVolume(level, anchor, new StarterFortPlan.RelativeBox(-10, 1, -10, 10, StarterFortPlan.TOWER_HEIGHT, 10));
        for (StarterFortPlan.RelativeBox box : StarterFortPlan.WINGS) {
            buildShell(level, anchor, box, wing);
        }
        for (StarterFortPlan.RelativeBox box : StarterFortPlan.PALISADE_SEGMENTS) {
            fillBox(level, anchor, box, palisade);
        }
        for (StarterFortPlan.RelativeBox box : StarterFortPlan.TOWERS) {
            buildShell(level, anchor, box, palisade);
        }
        for (StarterFortPlan.RelativeBox box : StarterFortPlan.GATE_ARCH) {
            fillBox(level, anchor, box, palisade);
        }
        clearVolume(level, anchor, StarterFortPlan.GATE_OPENING);
        level.setBlockAndUpdate(anchor.offset(0, 1, -2), Blocks.WHITE_BANNER.defaultBlockState());
    }

    public static BuildingValidationResult validateStarterFort(ServerLevel level, BlockPos anchor) {
        return new SettlementBuildingValidator(new BuildingDefinitionRegistry()).validate(
                level,
                null,
                new BuildingValidationRequest(
                        new UUID(0L, 0L),
                        BuildingType.STARTER_FORT,
                        anchor,
                        List.of(
                                new ZoneSelection(ZoneRole.AUTHORITY_POINT,
                                        StarterFortPlan.AUTHORITY_GUIDE.minPos(anchor),
                                        StarterFortPlan.AUTHORITY_GUIDE.maxPos(anchor),
                                        anchor),
                                new ZoneSelection(ZoneRole.INTERIOR,
                                        StarterFortPlan.INTERIOR_GUIDE.minPos(anchor),
                                        StarterFortPlan.INTERIOR_GUIDE.maxPos(anchor),
                                        anchor)
                        )
                )
        );
    }

    private static void fillFloor(ServerLevel level, BlockPos anchor, BlockState state) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlockAndUpdate(anchor.offset(x, 0, z), state);
            }
        }
    }

    private static void fillBox(ServerLevel level, BlockPos anchor, StarterFortPlan.RelativeBox box, BlockState state) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    level.setBlockAndUpdate(anchor.offset(x, y, z), state);
                }
            }
        }
    }

    private static void buildShell(ServerLevel level, BlockPos anchor, StarterFortPlan.RelativeBox box, BlockState state) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = anchor.offset(x, y, z);
                    if (x == box.minX() || x == box.maxX()
                            || y == box.minY() || y == box.maxY()
                            || z == box.minZ() || z == box.maxZ()) {
                        level.setBlockAndUpdate(pos, state);
                    } else {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void clearVolume(ServerLevel level, BlockPos anchor, StarterFortPlan.RelativeBox box) {
        fillBox(level, anchor, box, Blocks.AIR.defaultBlockState());
    }
}
