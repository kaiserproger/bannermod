package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.RecruitLifecycleEvents;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.gametest.support.RecruitsCommandGameTestSupport;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import com.talhanation.bannermod.util.FormationDimensionGuard;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FORMATIONDIM-001 acceptance coverage.
 *
 * <p>Two scenarios are exercised:
 * <ol>
 *   <li>Leader spawns in Overworld with a 4-recruit formation, then "transitions"
 *       to the Nether (we simulate by creating the leader fake-player in the
 *       Nether level — {@code recruit.getOwner()} resolves only against the
 *       recruit's own level, so the recruits see a null leader exactly as they
 *       would after a portal traversal). The recruits must hold their formation
 *       positions rather than path toward stale anchors.</li>
 *   <li>Firing {@link PlayerEvent.PlayerChangedDimensionEvent} drives
 *       {@code RecruitLifecycleEvents.onPlayerChangedDimension} and the
 *       {@code formation.cross_dimension_orphan} counter must advance by exactly
 *       the cohort size left behind in the source dimension.</li>
 * </ol>
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModFormationDimensionGuardGameTests {

    private static final UUID FORMATION_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID ORPHAN_COUNTER_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID FORMATION_GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID ORPHAN_GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final UUID TESTDIM_FIVE_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000905");
    private static final UUID TESTDIM_FIVE_GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000906");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void recruitsHoldPositionWhenLeaderCrossesDimension(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null,
                "Expected Nether level to exist for cross-dimension formation guard test");

        // Spawn leader in Overworld next to the squad, then 4 recruits in formation cohort.
        BlockPos leaderPos = helper.absolutePos(new BlockPos(3, 2, 5));
        ServerPlayer overworldLeader = (ServerPlayer) BannerModDedicatedServerGameTestSupport
                .createPositionedFakeServerPlayer(overworld, FORMATION_OWNER_UUID, "formation-leader-ow", leaderPos);

        List<AbstractRecruitEntity> formation = spawnFormationCohort(helper, FORMATION_OWNER_UUID, FORMATION_GROUP_UUID);
        helper.assertTrue(formation.size() == 4,
                "Expected the cross-dimension hold scenario to spawn a 4-recruit formation cohort");

        // Capture starting positions before the leader leaves the dimension.
        List<Vec3> startingPositions = new ArrayList<>();
        for (AbstractRecruitEntity recruit : formation) {
            startingPositions.add(recruit.position());
            // Lock in the hold position to current location so any unguarded
            // formation goal would try to navigate (and we'd see it move).
            recruit.setHoldPos(recruit.position());
        }

        long counterBefore = orphanCounter();

        // Simulate the leader walking through a portal: remove the overworld
        // fake-player and create one with the same UUID in the nether.
        // recruit.getOwner() is keyed against the recruit's own level so the
        // overworld lookup will now return null — which is exactly what
        // happens after a portal traversal.
        overworldLeader.discard();
        BlockPos netherLeaderPos = new BlockPos(0, 64, 0);
        Player netherLeader = BannerModDedicatedServerGameTestSupport
                .createPositionedFakeServerPlayer(nether, FORMATION_OWNER_UUID, "formation-leader-nether", netherLeaderPos);
        helper.assertTrue(netherLeader.level() == nether,
                "Expected the nether-side fake leader to live in the Nether level");

        // Verify recruits no longer see the leader through their level's player lookup.
        for (AbstractRecruitEntity recruit : formation) {
            helper.assertTrue(recruit.getOwner() == null,
                    "Expected overworld recruit to lose owner reference once leader is in Nether");
        }

        // Drive several follow-goal canUse() iterations through FormationDimensionGuard
        // to model the per-tick guard. Each call must report hold-required.
        for (AbstractRecruitEntity recruit : formation) {
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(
                        FormationDimensionGuard.shouldHoldDueToDimensionMismatch(recruit, recruit.getOwner()),
                        "Expected dimension guard to demand hold-position for orphaned formation recruit");
            }
        }

        // Verify recruits did not actually move (they have no leader to follow
        // and the goal would short-circuit; the explicit position check guards
        // against any future regression where a goal forgets the dim guard).
        for (int i = 0; i < formation.size(); i++) {
            Vec3 now = formation.get(i).position();
            Vec3 then = startingPositions.get(i);
            helper.assertTrue(now.distanceToSqr(then) < 1.0E-6D,
                    "Expected recruit " + i + " to hold position after leader cross-dimension transition");
        }

        long counterAfter = orphanCounter();
        helper.assertTrue(counterAfter > counterBefore,
                "Expected per-tick dim-guard checks to advance the cross-dimension orphan counter");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void playerChangedDimensionEventBumpsOrphanCounterByGroupSize(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null,
                "Expected Nether level to exist for orphan-counter event test");

        // Spawn the leader directly in the destination so the event handler's
        // "every other dimension" sweep counts exactly the orphaned cohort.
        BlockPos netherLeaderPos = new BlockPos(0, 64, 0);
        ServerPlayer netherLeader = (ServerPlayer) BannerModDedicatedServerGameTestSupport
                .createPositionedFakeServerPlayer(nether, ORPHAN_COUNTER_OWNER_UUID, "orphan-leader-nether", netherLeaderPos);

        // Spawn 4 recruits owned by the same UUID in the overworld — the cohort
        // that would be orphaned by the leader's portal traversal.
        List<AbstractRecruitEntity> cohort = spawnFormationCohort(helper, ORPHAN_COUNTER_OWNER_UUID, ORPHAN_GROUP_UUID);
        helper.assertTrue(cohort.size() == 4,
                "Expected orphan-counter scenario to register a 4-recruit cohort");

        long before = orphanCounter();

        // Fire the dimension-change event directly so we exercise the handler
        // wired in RecruitLifecycleEvents without depending on portal physics.
        ResourceKey<Level> from = Level.OVERWORLD;
        ResourceKey<Level> to = Level.NETHER;
        PlayerEvent.PlayerChangedDimensionEvent event = new PlayerEvent.PlayerChangedDimensionEvent(netherLeader, from, to);
        new RecruitLifecycleEvents().onPlayerChangedDimension(event);

        long after = orphanCounter();
        long delta = after - before;
        helper.assertTrue(delta == cohort.size(),
                "Expected PlayerChangedDimensionEvent to bump orphan counter by the orphaned cohort size; got delta=" + delta);

        helper.succeed();
    }

    /**
     * TESTDIM-001 acceptance: 5-recruit formation in Overworld; leader "teleports"
     * to Nether; advance ticks; assert recruits hold position (no NPE, no
     * pathfind toward stale anchors). This guards FORMATIONDIM-001 by exercising
     * the full {@code RecruitFollowOwnerGoal} / {@code RecruitHoldPosGoal}
     * dimension-guard contract under live ticks rather than just spot-calling
     * {@link FormationDimensionGuard#shouldHoldDueToDimensionMismatch}.
     *
     * <p>Strategy: gametest harness cannot drive a real
     * {@code ServerPlayer.changeDimension(...)} from inside the test template
     * (the harness runs in a single ServerLevel template; portal traversal would
     * break the helper's structure-relative coordinate frame). Instead we use
     * the same fake-player swap as
     * {@link #recruitsHoldPositionWhenLeaderCrossesDimension(GameTestHelper)} —
     * discard the overworld leader, spawn a same-UUID leader in the Nether,
     * and let {@code recruit.getOwner()} return null in the recruits' level.
     * That is exactly the on-server state after a portal traversal, which is
     * the contract the production fix targets. We then run real ticks via
     * {@code helper.runAfterDelay} and assert the recruits did not move and the
     * orphan counter advanced.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 120)
    public static void fiveRecruitFormationHoldsAcrossDimensionTeleport(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null,
                "Expected Nether level to exist for TESTDIM-001 5-recruit cross-dimension test");

        BlockPos leaderPos = helper.absolutePos(new BlockPos(3, 2, 5));
        ServerPlayer overworldLeader = (ServerPlayer) BannerModDedicatedServerGameTestSupport
                .createPositionedFakeServerPlayer(overworld, TESTDIM_FIVE_OWNER_UUID, "testdim-leader-ow", leaderPos);

        List<AbstractRecruitEntity> formation = spawnFiveRecruitFormationCohort(
                helper, TESTDIM_FIVE_OWNER_UUID, TESTDIM_FIVE_GROUP_UUID);
        helper.assertTrue(formation.size() == 5,
                "TESTDIM-001 acceptance requires a 5-recruit formation; got " + formation.size());

        List<Vec3> startingPositions = new ArrayList<>();
        for (AbstractRecruitEntity recruit : formation) {
            startingPositions.add(recruit.position());
            recruit.setHoldPos(recruit.position());
        }

        long counterBefore = orphanCounter();

        // Simulate the leader's portal traversal: discard the overworld
        // fake-player and create a same-UUID leader in the Nether. From the
        // overworld recruits' view this is observationally identical to a
        // real ServerPlayer.changeDimension(...).
        overworldLeader.discard();
        BlockPos netherLeaderPos = new BlockPos(0, 64, 0);
        Player netherLeader = BannerModDedicatedServerGameTestSupport
                .createPositionedFakeServerPlayer(nether, TESTDIM_FIVE_OWNER_UUID, "testdim-leader-nether", netherLeaderPos);
        helper.assertTrue(netherLeader.level() == nether,
                "TESTDIM-001: nether-side fake leader must live in the Nether level");

        for (AbstractRecruitEntity recruit : formation) {
            helper.assertTrue(recruit.getOwner() == null,
                    "TESTDIM-001: overworld recruit must lose owner reference once leader is in Nether");
        }

        // Advance real ticks so the recruits' goals run their canUse()/tick()
        // paths through FormationDimensionGuard. 40 ticks (~2s) is well past
        // the RecruitFollowOwnerGoal canUse() 10-tick recheck window.
        helper.runAfterDelay(40, () -> {
            // Per-tick guard contract: every recruit must report hold-required.
            for (AbstractRecruitEntity recruit : formation) {
                helper.assertTrue(
                        FormationDimensionGuard.shouldHoldDueToDimensionMismatch(recruit, recruit.getOwner()),
                        "TESTDIM-001: dim guard must demand hold-position for orphaned formation recruit");
            }

            // Recruits must hold position. Allow a tiny epsilon for entity
            // settling/gravity drift (block step) — the real bug is path-driven
            // motion toward stale anchors, which moves several blocks.
            for (int i = 0; i < formation.size(); i++) {
                AbstractRecruitEntity recruit = formation.get(i);
                helper.assertTrue(recruit.isAlive(),
                        "TESTDIM-001: recruit " + i + " must be alive after dim transition (no NPE-induced removal)");
                Vec3 now = recruit.position();
                Vec3 then = startingPositions.get(i);
                double horizontalDelta = Math.hypot(now.x - then.x, now.z - then.z);
                helper.assertTrue(horizontalDelta < 2.0D,
                        "TESTDIM-001: recruit " + i + " must hold horizontal position (delta=" + horizontalDelta + ")");
            }

            long counterAfter = orphanCounter();
            helper.assertTrue(counterAfter > counterBefore,
                    "TESTDIM-001: per-tick dim-guard checks must advance the cross-dimension orphan counter");

            helper.succeed();
        });
    }

    private static List<AbstractRecruitEntity> spawnFiveRecruitFormationCohort(GameTestHelper helper, UUID ownerUuid, UUID groupUuid) {
        List<AbstractRecruitEntity> cohort = new ArrayList<>();
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_FRONTLINE_POS,
                "TESTDIM Recruit A",
                ownerUuid));
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_FLANK_POS,
                "TESTDIM Recruit B",
                ownerUuid));
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS,
                "TESTDIM Recruit C",
                ownerUuid));
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_RANGED_RIGHT_POS,
                "TESTDIM Recruit D",
                ownerUuid));
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_RECOVERY_LEFT_POS,
                "TESTDIM Recruit E",
                ownerUuid));

        for (AbstractRecruitEntity recruit : cohort) {
            RecruitsCommandGameTestSupport.prepareForCommand(recruit, groupUuid);
        }
        RecruitsBattleGameTestSupport.assignFormationCohort(cohort, groupUuid);
        return cohort;
    }

    private static List<AbstractRecruitEntity> spawnFormationCohort(GameTestHelper helper, UUID ownerUuid, UUID groupUuid) {
        List<AbstractRecruitEntity> cohort = new ArrayList<>();
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_FRONTLINE_POS,
                "Formation Hold Recruit A",
                ownerUuid));
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_FLANK_POS,
                "Formation Hold Recruit B",
                ownerUuid));
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS,
                "Formation Hold Recruit C",
                ownerUuid));
        cohort.add(RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_RANGED_RIGHT_POS,
                "Formation Hold Recruit D",
                ownerUuid));

        for (AbstractRecruitEntity recruit : cohort) {
            RecruitsCommandGameTestSupport.prepareForCommand(recruit, groupUuid);
        }
        RecruitsBattleGameTestSupport.assignFormationCohort(cohort, groupUuid);
        return cohort;
    }

    private static long orphanCounter() {
        Map<String, Long> snapshot = RuntimeProfilingCounters.snapshot();
        Long value = snapshot.get(FormationDimensionGuard.COUNTER_KEY);
        return value == null ? 0L : value;
    }
}
