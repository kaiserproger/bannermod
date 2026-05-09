package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.events.civilian.SettlementWorkOrderClaimReleaseEvents;
import com.talhanation.bannermod.settlement.SettlementOrchestrator;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderStatus;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Live coverage for SETTLEMENT-005's "claim release on entity death mid-route" gap.
 *
 * <p>Verifies that the {@link SettlementWorkOrderClaimReleaseEvents} subscriber actually
 * releases a worker's active {@link SettlementWorkOrder} claim back to PENDING when the
 * worker is killed or force-removed via the live Forge event bus. The pure-JUnit suite
 * locks the runtime mutator in isolation; this test exercises the wiring through real
 * {@code LivingDeathEvent} / {@code EntityLeaveLevelEvent} paths.</p>
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModWorkOrderClaimReleaseGameTests {

    private static final UUID LEADER_UUID = UUID.fromString("00000000-0000-0000-0000-000000005001");
    private static final UUID DEATH_CLAIM_UUID = UUID.fromString("00000000-0000-0000-0000-000000005a01");
    private static final UUID DEATH_BUILDING_UUID = UUID.fromString("00000000-0000-0000-0000-000000005b01");
    private static final UUID DISCARD_CLAIM_UUID = UUID.fromString("00000000-0000-0000-0000-000000005a02");
    private static final UUID DISCARD_BUILDING_UUID = UUID.fromString("00000000-0000-0000-0000-000000005b02");
    private static final String TEAM_ID = "settlement_005_release_team";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", batch = "workorder_release_death")
    public static void workerDeathReleasesActiveWorkOrderClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SettlementWorkOrderClaimReleaseEvents.resetInvocationCountForTests();
        Player leader = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(
                level, LEADER_UUID, "settlement-005-leader");
        BannerModDedicatedServerGameTestSupport.ensureFaction(
                level, TEAM_ID, LEADER_UUID, "settlement-005-leader");
        BannerModDedicatedServerGameTestSupport.joinTeam(level, TEAM_ID, leader);
        BlockPos claimPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BannerModDedicatedServerGameTestSupport.seedClaim(
                level, claimPos, TEAM_ID, LEADER_UUID, "settlement-005-leader");

        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(
                helper, leader, new BlockPos(2, 2, 2));
        BannerModDedicatedServerGameTestSupport.assignDetachedOwnership(worker, LEADER_UUID);
        BannerModDedicatedServerGameTestSupport.joinTeam(level, TEAM_ID, worker);

        SettlementWorkOrderRuntime runtime = SettlementOrchestrator.workOrderRuntime(level);
        helper.assertTrue(runtime != null, "Expected the level to expose a SettlementWorkOrderRuntime.");
        SettlementWorkOrder published = runtime.publish(SettlementWorkOrder.pending(
                DEATH_CLAIM_UUID, DEATH_BUILDING_UUID, SettlementWorkOrderType.HARVEST_CROP,
                claimPos, null, 70, level.getGameTime()
        )).orElseThrow();
        SettlementWorkOrder claimed = runtime.claim(
                DEATH_CLAIM_UUID, worker.getUUID(), null, level.getGameTime(), 0L
        ).orElseThrow();
        helper.assertTrue(claimed.orderUuid().equals(published.orderUuid()),
                "Expected the worker to claim the published order.");

        worker.kill();

        helper.runAfterDelay(2, () -> {
            SettlementWorkOrder afterDeath = runtime.find(published.orderUuid()).orElseThrow();
            helper.assertTrue(afterDeath.status() == SettlementWorkOrderStatus.PENDING,
                    "Expected LivingDeathEvent on a worker to release its claim back to PENDING; got "
                            + afterDeath.status());
            helper.assertTrue(runtime.currentClaim(worker.getUUID()).isEmpty(),
                    "Expected the runtime to drop the dead worker's claim mapping.");
            helper.assertTrue(SettlementWorkOrderClaimReleaseEvents.invocationCount() > 0L,
                    "Expected the SettlementWorkOrderClaimReleaseEvents subscriber to bump its invocation counter.");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", batch = "workorder_release_discard")
    public static void workerForcedRemovalReleasesActiveWorkOrderClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SettlementWorkOrderClaimReleaseEvents.resetInvocationCountForTests();
        Player leader = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(
                level, LEADER_UUID, "settlement-005-leader");
        BannerModDedicatedServerGameTestSupport.ensureFaction(
                level, TEAM_ID, LEADER_UUID, "settlement-005-leader");
        BannerModDedicatedServerGameTestSupport.joinTeam(level, TEAM_ID, leader);
        BlockPos claimPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BannerModDedicatedServerGameTestSupport.seedClaim(
                level, claimPos, TEAM_ID, LEADER_UUID, "settlement-005-leader");

        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(
                helper, leader, new BlockPos(2, 2, 2));
        BannerModDedicatedServerGameTestSupport.assignDetachedOwnership(worker, LEADER_UUID);
        BannerModDedicatedServerGameTestSupport.joinTeam(level, TEAM_ID, worker);

        SettlementWorkOrderRuntime runtime = SettlementOrchestrator.workOrderRuntime(level);
        helper.assertTrue(runtime != null, "Expected the level to expose a SettlementWorkOrderRuntime.");
        SettlementWorkOrder published = runtime.publish(SettlementWorkOrder.pending(
                DISCARD_CLAIM_UUID, DISCARD_BUILDING_UUID, SettlementWorkOrderType.HARVEST_CROP,
                claimPos, null, 70, level.getGameTime()
        )).orElseThrow();
        runtime.claim(DISCARD_CLAIM_UUID, worker.getUUID(), null, level.getGameTime(), 0L).orElseThrow();

        worker.remove(Entity.RemovalReason.DISCARDED);

        helper.runAfterDelay(2, () -> {
            SettlementWorkOrder afterRemoval = runtime.find(published.orderUuid()).orElseThrow();
            helper.assertTrue(afterRemoval.status() == SettlementWorkOrderStatus.PENDING,
                    "Expected EntityLeaveLevelEvent (DISCARDED) to release the worker's claim back to PENDING; got "
                            + afterRemoval.status());
            helper.assertTrue(runtime.currentClaim(worker.getUUID()).isEmpty(),
                    "Expected the runtime to drop the discarded worker's claim mapping.");
            helper.assertTrue(SettlementWorkOrderClaimReleaseEvents.invocationCount() > 0L,
                    "Expected the SettlementWorkOrderClaimReleaseEvents subscriber to bump its invocation counter.");
            helper.succeed();
        });
    }
}
