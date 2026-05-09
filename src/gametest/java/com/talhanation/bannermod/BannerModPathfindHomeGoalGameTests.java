package com.talhanation.bannermod;

import com.talhanation.bannermod.ai.home.PathfindHomeGoal;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.military.RecruitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Supplier;

/**
 * HOMEASSIGN-003 acceptance tests for {@link PathfindHomeGoal}.
 *
 * <p>The goal triggers at night (or low-stamina) when a homePos is set, and
 * pathfinds the entity to within 3 blocks of that position; on arrival it
 * either enters the bed at homePos or stops navigation as a sleep-on-ground
 * fallback. These tests construct the goal directly against freshly spawned
 * recruits, workers, and citizens so they exercise the home goal without
 * unrelated selector priority or combat/work goals masking the assertion.
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModPathfindHomeGoalGameTests {

    private static long DAY = 1000L;
    // 13_000 game-ticks-of-day is solidly past dusk so {@link
    // net.minecraft.world.level.Level#isNight()} returns true.
    private static long NIGHT = 14_000L;

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 400)
    public static void recruitGoesHomeAtNight(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        prepareFlatPath(helper, new BlockPos(1, 0, 1), new BlockPos(12, 0, 1));

        RecruitEntity recruit = BannerModGameTestSupport.spawnOwnedRecruit(helper, owner, new BlockPos(1, 1, 1));
        // FollowState 0 = idle/free; otherwise the recruit's existing rest predicates
        // suppress non-combat goals and our test would assert nothing meaningful.
        recruit.setFollowState(0);

        BlockPos bedRel = new BlockPos(11, 1, 1);
        BlockPos bedAbs = placeBed(helper, bedRel);
        recruit.setHomePos(bedAbs);

        ServerLevel level = helper.getLevel();
        forceTimeOfDay(level, NIGHT);

        PathfindHomeGoal goal = new PathfindHomeGoal(
                recruit,
                recruit::getHomePos,
                () -> recruit.getShouldRest()
                        || recruit.getMorale() < 45.0F
                        || recruit.getHealth() < recruit.getMaxHealth(),
                1.0D);

        helper.assertTrue(goal.canUse(),
                "PathfindHomeGoal must trigger for recruit at night with home assigned");
        goal.start();

        // Daytime regression: full health, full morale, day-time => goal must NOT trigger.
        // Use a fresh goal instance because canUse() internally throttles at 20 ticks
        // and the gametest does not advance server-tick game time between asserts.
        forceTimeOfDay(level, DAY);
        recruit.setMoral(100.0F);
        recruit.setHealth(recruit.getMaxHealth());
        PathfindHomeGoal dayGoal = new PathfindHomeGoal(
                recruit,
                recruit::getHomePos,
                () -> recruit.getShouldRest()
                        || recruit.getMorale() < 45.0F
                        || recruit.getHealth() < recruit.getMaxHealth(),
                1.0D);
        helper.assertFalse(dayGoal.canUse(),
                "PathfindHomeGoal must stay dormant in daylight for a healthy recruit with home assigned");

        forceTimeOfDay(level, NIGHT);
        helper.succeedWhen(() -> {
            goal.tick();
            assertWithinHomeRange(helper, recruit, bedAbs, "recruit");
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 400)
    public static void workerGoesHomeAtNight(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        prepareFlatPath(helper, new BlockPos(1, 0, 2), new BlockPos(12, 0, 2));

        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, new BlockPos(1, 1, 2));
        worker.setFollowState(0);

        BlockPos bedRel = new BlockPos(11, 1, 2);
        BlockPos bedAbs = placeBed(helper, bedRel);
        worker.setHomePos(bedAbs);

        ServerLevel level = helper.getLevel();
        forceTimeOfDay(level, NIGHT);

        PathfindHomeGoal goal = new PathfindHomeGoal(
                worker,
                worker::getHomePos,
                () -> worker.getShouldRest()
                        || worker.getMorale() < 45.0F
                        || worker.getHealth() < worker.getMaxHealth(),
                1.0D);

        helper.assertTrue(goal.canUse(),
                "PathfindHomeGoal must trigger for worker at night with home assigned");
        goal.start();

        helper.succeedWhen(() -> {
            goal.tick();
            assertWithinHomeRange(helper, worker, bedAbs, "worker");
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 400)
    public static void citizenGoesHomeAtNight(GameTestHelper helper) {
        prepareFlatPath(helper, new BlockPos(1, 0, 3), new BlockPos(12, 0, 3));

        CitizenEntity citizen = BannerModGameTestSupport.spawnEntity(
                helper,
                com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes.CITIZEN.get(),
                new BlockPos(1, 1, 3));

        BlockPos bedRel = new BlockPos(11, 1, 3);
        BlockPos bedAbs = placeBed(helper, bedRel);
        citizen.setHomePos(bedAbs);

        ServerLevel level = helper.getLevel();
        forceTimeOfDay(level, NIGHT);

        PathfindHomeGoal goal = new PathfindHomeGoal(citizen, citizen::getHomePos);
        helper.assertTrue(goal.canUse(),
                "PathfindHomeGoal must trigger for citizen at night with home assigned");
        goal.start();

        // Daytime regression for citizen: no stamina signal, so goal must be silent.
        // Use a fresh goal instance — see recruitGoesHomeAtNight for the throttle rationale.
        forceTimeOfDay(level, DAY);
        PathfindHomeGoal dayGoal = new PathfindHomeGoal(citizen, citizen::getHomePos);
        helper.assertFalse(dayGoal.canUse(),
                "PathfindHomeGoal must stay dormant in daylight for a citizen with home assigned");

        forceTimeOfDay(level, NIGHT);
        helper.succeedWhen(() -> {
            goal.tick();
            assertWithinHomeRange(helper, citizen, bedAbs, "citizen");
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void noHomeAssignedKeepsGoalDormant(GameTestHelper helper) {
        CitizenEntity citizen = BannerModGameTestSupport.spawnEntity(
                helper,
                com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes.CITIZEN.get(),
                BlockPos.ZERO);
        // No setHomePos call.
        ServerLevel level = helper.getLevel();
        forceTimeOfDay(level, NIGHT);

        PathfindHomeGoal goal = new PathfindHomeGoal(citizen, citizen::getHomePos);
        helper.assertFalse(goal.canUse(),
                "PathfindHomeGoal must not trigger when no home is assigned");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void arrivalAtBedTriggersSleep(GameTestHelper helper) {
        // Spawn the citizen directly on top of the bed so a single tick is enough
        // to satisfy the 3-block proximity check inside PathfindHomeGoal#tick.
        BlockPos bedRel = new BlockPos(2, 1, 2);
        BlockPos bedAbs = placeBed(helper, bedRel);

        CitizenEntity citizen = BannerModGameTestSupport.spawnEntity(
                helper,
                com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes.CITIZEN.get(),
                bedRel);
        citizen.setHomePos(bedAbs);

        ServerLevel level = helper.getLevel();
        forceTimeOfDay(level, NIGHT);

        PathfindHomeGoal goal = new PathfindHomeGoal(citizen, citizen::getHomePos);
        helper.assertTrue(goal.canUse(), "Goal must accept the run with home + night");
        goal.start();
        goal.tick();

        helper.assertTrue(citizen.isSleeping(),
                "Citizen at homePos with a vacant bed must enter sleep on first tick");

        helper.succeed();
    }

    /**
     * Restart-preservation contract: PathfindHomeGoal stores no NBT itself.
     * After we discard the goal instance and rebuild it from the entity's still
     * persisted homePos field (which HOMEASSIGN-002 guarantees), the rebuilt
     * goal must pick up exactly where the old one left off. This is the same
     * code path the level loader uses on world reload.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 400)
    public static void rebuiltGoalResumesAfterReload(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        prepareFlatPath(helper, new BlockPos(1, 0, 4), new BlockPos(12, 0, 4));

        RecruitEntity recruit = BannerModGameTestSupport.spawnOwnedRecruit(helper, owner, new BlockPos(1, 1, 4));
        recruit.setFollowState(0);

        BlockPos bedRel = new BlockPos(11, 1, 4);
        BlockPos bedAbs = placeBed(helper, bedRel);
        recruit.setHomePos(bedAbs);

        ServerLevel level = helper.getLevel();
        forceTimeOfDay(level, NIGHT);

        Supplier<Boolean> staminaSignal = () -> recruit.getShouldRest()
                || recruit.getMorale() < 45.0F
                || recruit.getHealth() < recruit.getMaxHealth();

        PathfindHomeGoal first = new PathfindHomeGoal(recruit, recruit::getHomePos, staminaSignal, 1.0D);
        helper.assertTrue(first.canUse(), "Pre-reload goal must trigger");
        first.start();
        first.tick();
        // Clear the pre-reload route so arrival proves the rebuilt goal moved the recruit.
        recruit.getNavigation().stop();

        // Simulate a reload: drop the goal instance and rebuild against the same
        // entity. The entity's homePos survives because it lives on the entity's
        // persistent synched data (HOMEASSIGN-002), so the new goal must accept
        // canUse with no extra wiring.
        forceTimeOfDay(level, NIGHT + 100L);
        PathfindHomeGoal rebuilt = new PathfindHomeGoal(recruit, recruit::getHomePos, staminaSignal, 1.0D);
        helper.assertTrue(rebuilt.canUse(),
                "Rebuilt goal must resume after reload because homePos is persistent");
        rebuilt.start();

        helper.succeedWhen(() -> {
            rebuilt.tick();
            assertWithinHomeRange(helper, recruit, bedAbs, "restarted recruit");
        });
    }

    private static void assertWithinHomeRange(GameTestHelper helper,
                                              PathfinderMob mob,
                                              BlockPos home,
                                              String label) {
        double distSqr = mob.position().distanceToSqr(
                home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D);
        helper.assertTrue(distSqr <= 9.0D,
                "Expected " + label + " to reach within 3 blocks of assigned home; distance squared was " + distSqr);
    }

    private static void prepareFlatPath(GameTestHelper helper, BlockPos fromRel, BlockPos toRel) {
        int minX = Math.min(fromRel.getX(), toRel.getX());
        int maxX = Math.max(fromRel.getX(), toRel.getX());
        int minZ = Math.min(fromRel.getZ(), toRel.getZ());
        int maxZ = Math.max(fromRel.getZ(), toRel.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, fromRel.getY(), z)),
                        Blocks.STONE.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, fromRel.getY() + 1, z)),
                        Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, fromRel.getY() + 2, z)),
                        Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * GameTest harness does not tick {@code Level#tickTime}, so {@code setDayTime}
     * alone leaves {@link net.minecraft.world.level.Level#isNight()} stale because
     * the {@code skyDarken} field is only refreshed inside the per-tick
     * {@code updateSkyBrightness} call. Doing both in one helper keeps the test
     * intent (it really is "night") readable at the call site.
     */
    private static void forceTimeOfDay(ServerLevel level, long dayTime) {
        level.setDayTime(dayTime);
        level.updateSkyBrightness();
    }

    private static BlockPos placeBed(GameTestHelper helper, BlockPos bedRel) {
        BlockPos bedAbs = helper.absolutePos(bedRel);
        BlockState foot = Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT)
                .setValue(BlockStateProperties.OCCUPIED, false);
        BlockState head = foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
        helper.getLevel().setBlock(bedAbs, foot, 3);
        // Place the head along the bed's facing axis so the bed is structurally
        // valid for LivingEntity#startSleeping.
        helper.getLevel().setBlock(
                bedAbs.relative(foot.getValue(BlockStateProperties.HORIZONTAL_FACING)),
                head, 3);
        return bedAbs;
    }
}
