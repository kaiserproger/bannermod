package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.runtime.OccupationRecord;
import com.talhanation.bannermod.war.runtime.OccupationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * CLAIMFANOUT-001 acceptance gametests.
 *
 * <p>Verifies the per-claim cleanup fanout that runs when {@code RecruitsClaimManager#removeClaim}
 * fires (network handler, admin command, or internal call). The fanout must:
 * <ul>
 *   <li>drop the treasury ledger keyed on the deleted claim,</li>
 *   <li>drop the settlement snapshot keyed on the deleted claim,</li>
 *   <li>release worker bindings whose work area lives inside the deleted claim,</li>
 *   <li>remove war occupation records whose chunks intersect the deleted claim.</li>
 * </ul>
 * A parity test confirms that a sibling claim's state is untouched by the cleanup.
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModClaimRemovalFanoutGameTests {

    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000004001");
    private static final UUID OTHER_UUID = UUID.fromString("00000000-0000-0000-0000-000000004002");
    private static final String OWNER_TEAM_ID = "claimfanout_owner";
    private static final String OTHER_TEAM_ID = "claimfanout_other";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimDeletionCascadesAllPerClaimRuntimeState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = createPlayer(helper, level, OWNER_UUID, "fanout-owner", OWNER_TEAM_ID);
        BlockPos claimPos = new BlockPos(2, 2, 2);
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(
                level, helper.absolutePos(claimPos), OWNER_TEAM_ID, OWNER_UUID, owner.getScoreboardName());
        UUID claimUuid = claim.getUUID();
        ChunkPos anchorChunk = new ChunkPos(helper.absolutePos(claimPos));

        // 1) Treasury ledger seeded with a non-zero balance
        BannerModTreasuryManager treasury = BannerModTreasuryManager.get(level);
        treasury.depositTaxes(claimUuid, anchorChunk, OWNER_TEAM_ID, 25, level.getGameTime());
        helper.assertTrue(treasury.getLedger(claimUuid) != null,
                "Expected treasury ledger to exist after seeding a deposit");

        // 2) Settlement snapshot persisted for this claim
        SettlementManager settlements = SettlementManager.get(level);
        settlements.putSnapshot(SettlementSnapshot.create(claimUuid, anchorChunk, OWNER_TEAM_ID));
        helper.assertTrue(settlements.getSnapshot(claimUuid) != null,
                "Expected settlement snapshot to exist after putSnapshot");

        // 3) Worker bound to a work area inside the claim
        CropArea cropArea = BannerModGameTestSupport.spawnOwnedCropArea(helper, owner, claimPos);
        cropArea.setTeamStringID(OWNER_TEAM_ID);
        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, claimPos.offset(1, 0, 0));
        BannerModDedicatedServerGameTestSupport.joinTeam(level, OWNER_TEAM_ID, worker);
        worker.setCurrentWorkArea(cropArea);
        helper.assertTrue(worker.getBoundWorkAreaUUID() != null,
                "Expected worker to be bound to a work area before claim removal");

        // 4) Occupation record covering this claim's chunks
        OccupationRuntime occupations = WarRuntimeContext.occupations(level);
        UUID warId = UUID.randomUUID();
        UUID attackerEntityId = UUID.randomUUID();
        UUID defenderEntityId = UUID.randomUUID();
        OccupationRecord placed = occupations.place(
                warId, attackerEntityId, defenderEntityId, List.of(anchorChunk), level.getGameTime())
                .orElseThrow();
        helper.assertTrue(occupations.byId(placed.id()).isPresent(),
                "Expected occupation record to exist before claim removal");

        // Trigger the claim removal — same code path as MessageDeleteClaim.
        ClaimEvents.claimManager().removeClaim(claim);

        helper.assertTrue(treasury.getLedger(claimUuid) == null,
                "Expected treasury ledger to be removed in the same tick as claim deletion");
        helper.assertTrue(settlements.getSnapshot(claimUuid) == null,
                "Expected settlement snapshot to be removed in the same tick as claim deletion");
        helper.assertTrue(worker.getBoundWorkAreaUUID() == null,
                "Expected worker.boundWorkAreaUUID to be cleared after its hosting claim was removed");
        helper.assertTrue(occupations.byId(placed.id()).isEmpty(),
                "Expected occupation record to be removed after its target claim was deleted");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void unrelatedClaimStateIsPreservedWhenSiblingClaimIsDeleted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = createPlayer(helper, level, OWNER_UUID, "fanout-owner-parity", OWNER_TEAM_ID);
        Player otherOwner = createPlayer(helper, level, OTHER_UUID, "fanout-other", OTHER_TEAM_ID);

        BlockPos targetPos = new BlockPos(2, 2, 2);
        BlockPos siblingPos = new BlockPos(13, 2, 13); // different chunk

        RecruitsClaim targetClaim = BannerModDedicatedServerGameTestSupport.seedClaim(
                level, helper.absolutePos(targetPos), OWNER_TEAM_ID, OWNER_UUID, owner.getScoreboardName());
        RecruitsClaim siblingClaim = BannerModDedicatedServerGameTestSupport.seedClaim(
                level, helper.absolutePos(siblingPos), OTHER_TEAM_ID, OTHER_UUID, otherOwner.getScoreboardName());

        UUID targetUuid = targetClaim.getUUID();
        UUID siblingUuid = siblingClaim.getUUID();
        ChunkPos targetChunk = new ChunkPos(helper.absolutePos(targetPos));
        ChunkPos siblingChunk = new ChunkPos(helper.absolutePos(siblingPos));

        // Seed BOTH claims with treasury, snapshot, occupation; bind a worker only to sibling.
        BannerModTreasuryManager treasury = BannerModTreasuryManager.get(level);
        treasury.depositTaxes(targetUuid, targetChunk, OWNER_TEAM_ID, 10, level.getGameTime());
        treasury.depositTaxes(siblingUuid, siblingChunk, OTHER_TEAM_ID, 17, level.getGameTime());

        SettlementManager settlements = SettlementManager.get(level);
        settlements.putSnapshot(SettlementSnapshot.create(targetUuid, targetChunk, OWNER_TEAM_ID));
        settlements.putSnapshot(SettlementSnapshot.create(siblingUuid, siblingChunk, OTHER_TEAM_ID));

        CropArea siblingArea = BannerModGameTestSupport.spawnOwnedCropArea(helper, otherOwner, siblingPos);
        siblingArea.setTeamStringID(OTHER_TEAM_ID);
        FarmerEntity siblingWorker = BannerModGameTestSupport.spawnOwnedFarmer(helper, otherOwner, siblingPos.offset(1, 0, 0));
        BannerModDedicatedServerGameTestSupport.joinTeam(level, OTHER_TEAM_ID, siblingWorker);
        siblingWorker.setCurrentWorkArea(siblingArea);
        UUID siblingBoundBefore = siblingWorker.getBoundWorkAreaUUID();
        helper.assertTrue(siblingBoundBefore != null, "Expected sibling worker to be bound prior to the test mutation");

        OccupationRuntime occupations = WarRuntimeContext.occupations(level);
        UUID warId = UUID.randomUUID();
        UUID attackerEntityId = UUID.randomUUID();
        UUID defenderEntityId = UUID.randomUUID();
        OccupationRecord targetOccupation = occupations.place(
                warId, attackerEntityId, defenderEntityId, List.of(targetChunk), level.getGameTime()).orElseThrow();
        OccupationRecord siblingOccupation = occupations.place(
                warId, attackerEntityId, defenderEntityId, List.of(siblingChunk), level.getGameTime()).orElseThrow();

        // Delete only the target claim.
        ClaimEvents.claimManager().removeClaim(targetClaim);

        // Target gone (sanity)
        helper.assertTrue(treasury.getLedger(targetUuid) == null, "target treasury ledger should be cleared");
        helper.assertTrue(occupations.byId(targetOccupation.id()).isEmpty(), "target occupation should be cleared");

        // Parity: sibling state untouched
        helper.assertTrue(treasury.getLedger(siblingUuid) != null,
                "Sibling treasury ledger must survive deletion of an unrelated claim");
        helper.assertTrue(treasury.getLedger(siblingUuid).accruedTaxes() == 17,
                "Sibling treasury balance must be unchanged");
        helper.assertTrue(settlements.getSnapshot(siblingUuid) != null,
                "Sibling settlement snapshot must survive deletion of an unrelated claim");
        helper.assertTrue(occupations.byId(siblingOccupation.id()).isPresent(),
                "Sibling occupation must survive deletion of an unrelated claim");
        helper.assertTrue(siblingBoundBefore.equals(siblingWorker.getBoundWorkAreaUUID()),
                "Sibling worker binding must survive deletion of an unrelated claim");

        helper.succeed();
    }

    private static Player createPlayer(GameTestHelper helper, ServerLevel level, UUID playerId, String name, String teamId) {
        Player player = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, playerId, name);
        BannerModDedicatedServerGameTestSupport.ensureFaction(level, teamId, playerId, name);
        BannerModDedicatedServerGameTestSupport.joinTeam(level, teamId, player);
        BlockPos spawnPos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }
}
