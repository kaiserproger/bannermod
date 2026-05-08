package com.talhanation.bannermod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModAdminRecoveryCommandGameTests {
    private static final UUID SETTLEMENT_CLAIM_UUID = UUID.fromString("00000000-0000-0000-0000-00ad00010001");
    private static final UUID TREASURY_CLAIM_UUID = UUID.fromString("00000000-0000-0000-0000-00ad00010002");
    private static final UUID TRUSTED_UUID = UUID.fromString("00000000-0000-0000-0000-00ad00010003");
    private static final UUID TRUST_CLAIM_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-00ad00010004");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void settlementPruneRemovesSnapshotByClaimUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BannerModSettlementManager settlements = BannerModSettlementManager.get(level);
        settlements.putSnapshot(BannerModSettlementSnapshot.create(SETTLEMENT_CLAIM_UUID, new ChunkPos(30, 30), "admincmds"));

        int result = runCommand(level, "bannermod settlement prune " + SETTLEMENT_CLAIM_UUID);

        helper.assertTrue(result == 1, "Expected settlement prune command to report one removed snapshot");
        helper.assertTrue(settlements.getSnapshot(SETTLEMENT_CLAIM_UUID) == null,
                "Expected settlement snapshot to be removed by /bannermod settlement prune");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void treasurySetWritesRequestedBalance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BannerModTreasuryManager treasury = BannerModTreasuryManager.get(level);
        treasury.removeLedger(TREASURY_CLAIM_UUID);

        int result = runCommand(level, "bannermod treasury set " + TREASURY_CLAIM_UUID + " 42");

        BannerModTreasuryLedgerSnapshot ledger = treasury.getLedger(TREASURY_CLAIM_UUID);
        helper.assertTrue(result == 1, "Expected treasury set command to succeed");
        helper.assertTrue(ledger != null, "Expected treasury set command to create a ledger");
        helper.assertTrue(ledger.treasuryBalance() == 42,
                "Expected /bannermod treasury set to write balance 42, got " + (ledger == null ? "null" : ledger.treasuryBalance()));
        treasury.removeLedger(TREASURY_CLAIM_UUID);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void treasuryShowReportsExistingBalance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BannerModTreasuryManager treasury = BannerModTreasuryManager.get(level);
        treasury.removeLedger(TREASURY_CLAIM_UUID);
        treasury.depositTaxes(TREASURY_CLAIM_UUID, new ChunkPos(31, 31), "admincmds", 17, level.getGameTime());

        int result = runCommand(level, "bannermod treasury show " + TREASURY_CLAIM_UUID);

        helper.assertTrue(result == 1, "Expected treasury show command to succeed for an existing ledger");
        BannerModTreasuryLedgerSnapshot ledger = treasury.getLedger(TREASURY_CLAIM_UUID);
        helper.assertTrue(ledger != null && ledger.treasuryBalance() == 17,
                "Expected treasury show to leave existing balance unchanged at 17");
        treasury.removeLedger(TREASURY_CLAIM_UUID);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimTrustPruneDeadUuidsRemovesInvalidTrustedEntries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecruitsClaim claim = new RecruitsClaim("admincmds-trust", TRUST_CLAIM_OWNER_UUID);
        claim.addChunk(new ChunkPos(32, 32));
        claim.setCenter(new ChunkPos(32, 32));
        claim.getTrustedPlayers().add(new RecruitsPlayerInfo(TRUSTED_UUID, "Trusted"));
        claim.getTrustedPlayers().add(null);
        claim.getTrustedPlayers().add(new RecruitsPlayerInfo(TRUSTED_UUID, "Trusted duplicate"));
        ClaimEvents.claimManager().testInsertClaim(claim);

        int result = runCommand(level, "bannermod claim trust prune-dead-uuids");

        helper.assertTrue(result >= 2, "Expected trust prune command to remove invalid and duplicate entries");
        helper.assertTrue(claim.getTrustedPlayers().size() == 1,
                "Expected trust prune to leave exactly one trusted player entry");
        helper.assertTrue(TRUSTED_UUID.equals(claim.getTrustedPlayers().getFirst().getUUID()),
                "Expected trust prune to preserve the valid trusted UUID");
        ClaimEvents.claimManager().removeClaim(claim);
        helper.succeed();
    }

    private static int runCommand(ServerLevel level, String command) {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withPermission(2);
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("Command failed: " + command, exception);
        }
    }
}
