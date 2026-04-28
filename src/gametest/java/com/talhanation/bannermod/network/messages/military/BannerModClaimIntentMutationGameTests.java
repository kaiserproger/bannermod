package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModClaimIntentMutationGameTests {
    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000003301");
    private static final UUID OTHER_UUID = UUID.fromString("00000000-0000-0000-0000-000000003302");
    private static final String OWNER_TEAM = "test_003_claim_owner";
    private static final String OTHER_TEAM = "test_003_claim_other";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void ownerCanAddAndRemoveClaimChunkWithCurrencyAndPersistence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer owner = createPlayer(level, basePos, OWNER_UUID, "test-003-owner", OWNER_TEAM);
        ChunkPos addChunk = new ChunkPos(owner.chunkPosition().x + 1, owner.chunkPosition().z);
        RecruitsClaim claim = freshClaim(level, basePos, OWNER_TEAM, owner, addChunk);
        giveCurrency(owner, 64);
        int beforeAdd = currencyCount(owner);
        int cost = RecruitsServerConfig.ChunkCost.get();

        boolean added = MessageClaimIntent.applyServerSide(owner, MessageClaimIntent.Action.ADD_CHUNK, claim.getUUID(), addChunk);

        helper.assertTrue(added, "Expected owner add intent to be accepted");
        helper.assertTrue(claim.containsChunk(addChunk), "Expected added chunk to be visible on the claim state");
        helper.assertTrue(ClaimEvents.recruitsClaimManager.getClaim(addChunk) == claim, "Expected manager lookup to resolve the added chunk");
        helper.assertTrue(currencyCount(owner) == beforeAdd - cost, "Expected accepted add to charge configured claim currency");
        assertSavedClaimContains(helper, level, claim.getUUID(), addChunk, true);

        boolean removed = MessageClaimIntent.applyServerSide(owner, MessageClaimIntent.Action.REMOVE_CHUNK, claim.getUUID(), addChunk);

        helper.assertTrue(removed, "Expected owner remove intent to be accepted");
        helper.assertFalse(claim.containsChunk(addChunk), "Expected removed chunk to disappear from claim state");
        helper.assertTrue(ClaimEvents.recruitsClaimManager.getClaim(addChunk) == null, "Expected removed chunk to disappear from manager lookup");
        assertSavedClaimContains(helper, level, claim.getUUID(), addChunk, false);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void deniedAddRemoveAndDeleteKeepClaimStateAndCurrency(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer owner = createPlayer(level, basePos, OWNER_UUID, "test-003-owner", OWNER_TEAM);
        ServerPlayer other = createPlayer(level, basePos, OTHER_UUID, "test-003-other", OTHER_TEAM);
        ChunkPos baseChunk = new ChunkPos(basePos);
        ChunkPos addChunk = new ChunkPos(baseChunk.x + 1, baseChunk.z);
        RecruitsClaim claim = freshClaim(level, basePos, OWNER_TEAM, owner, addChunk);
        giveCurrency(other, 64);
        int beforeDeniedAdd = currencyCount(other);

        boolean deniedAdd = MessageClaimIntent.applyServerSide(other, MessageClaimIntent.Action.ADD_CHUNK, claim.getUUID(), addChunk);
        boolean deniedRemove = MessageClaimIntent.applyServerSide(other, MessageClaimIntent.Action.REMOVE_CHUNK, claim.getUUID(), baseChunk);
        boolean deniedDelete = MessageClaimIntent.applyServerSide(other, MessageClaimIntent.Action.DELETE, claim.getUUID(), baseChunk);

        helper.assertFalse(deniedAdd, "Expected non-owner add intent to be denied");
        helper.assertFalse(deniedRemove, "Expected non-owner remove intent to be denied");
        helper.assertFalse(deniedDelete, "Expected non-owner delete intent to be denied");
        helper.assertTrue(currencyCount(other) == beforeDeniedAdd, "Expected denied add to leave currency untouched");
        helper.assertFalse(claim.containsChunk(addChunk), "Expected denied add to leave target chunk unclaimed");
        helper.assertTrue(claim.containsChunk(baseChunk), "Expected denied remove/delete to leave original chunk claimed");
        helper.assertTrue(ClaimEvents.recruitsClaimManager.getClaim(baseChunk) == claim, "Expected denied delete to leave claim registered");
        assertSavedClaimContains(helper, level, claim.getUUID(), baseChunk, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void farAwayAndNetherClaimAddsAreDeniedWithoutCharging(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Expected Nether level to exist for Overworld-only claim edit test");
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer owner = createPlayer(level, basePos, OWNER_UUID, "test-003-owner", OWNER_TEAM);
        ChunkPos farChunk = new ChunkPos(owner.chunkPosition().x + 5, owner.chunkPosition().z);
        ChunkPos nearChunk = new ChunkPos(owner.chunkPosition().x + 1, owner.chunkPosition().z);
        RecruitsClaim claim = freshClaim(level, basePos, OWNER_TEAM, owner, farChunk, nearChunk);
        giveCurrency(owner, 64);
        int beforeFar = currencyCount(owner);

        boolean deniedFar = MessageClaimIntent.applyServerSide(owner, MessageClaimIntent.Action.ADD_CHUNK, claim.getUUID(), farChunk);

        helper.assertFalse(deniedFar, "Expected add intent more than four chunks away to be denied");
        helper.assertFalse(claim.containsChunk(farChunk), "Expected distance denial to leave target chunk unclaimed");
        helper.assertTrue(currencyCount(owner) == beforeFar, "Expected distance denial to leave currency untouched");

        ServerPlayer netherOwner = createPlayer(nether, basePos, OWNER_UUID, "test-003-owner-nether", OWNER_TEAM);
        giveCurrency(netherOwner, 64);
        int beforeNether = currencyCount(netherOwner);
        boolean deniedNether = MessageClaimIntent.applyServerSide(netherOwner, MessageClaimIntent.Action.ADD_CHUNK, claim.getUUID(), nearChunk);

        helper.assertFalse(deniedNether, "Expected Nether add intent to be denied by Overworld-only rule");
        helper.assertFalse(claim.containsChunk(nearChunk), "Expected Nether denial to leave Overworld claim state unchanged");
        helper.assertTrue(currencyCount(netherOwner) == beforeNether, "Expected Nether denial to leave currency untouched");
        assertSavedClaimContains(helper, level, claim.getUUID(), nearChunk, false);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void ownerCanDeleteClaimAndRemovalIsPersistenceVisible(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer owner = createPlayer(level, basePos, OWNER_UUID, "test-003-owner", OWNER_TEAM);
        ChunkPos baseChunk = new ChunkPos(basePos);
        RecruitsClaim claim = freshClaim(level, basePos, OWNER_TEAM, owner);

        boolean deleted = MessageClaimIntent.applyServerSide(owner, MessageClaimIntent.Action.DELETE, claim.getUUID(), baseChunk);

        helper.assertTrue(deleted, "Expected owner delete intent to be accepted");
        helper.assertTrue(ClaimEvents.recruitsClaimManager.getClaim(baseChunk) == null, "Expected deleted claim chunk to be removed from manager lookup");
        helper.assertFalse(ClaimEvents.recruitsClaimManager.getAllClaims().stream().anyMatch(saved -> saved.getUUID().equals(claim.getUUID())),
                "Expected deleted claim to be absent from manager claim list");
        assertSavedClaimExists(helper, level, claim.getUUID(), false);
        helper.succeed();
    }

    private static ServerPlayer createPlayer(ServerLevel level, BlockPos spawnPos, UUID playerId, String name, String teamId) {
        ServerPlayer player = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(level, playerId, name, spawnPos);
        BannerModDedicatedServerGameTestSupport.ensureFaction(level, teamId, playerId, name);
        BannerModDedicatedServerGameTestSupport.joinTeam(level, teamId, player);
        return player;
    }

    private static RecruitsClaim freshClaim(ServerLevel level, BlockPos basePos, String factionId, Player owner, ChunkPos... extraChunksToClear) {
        RecruitsClaim initial = BannerModDedicatedServerGameTestSupport.seedClaim(level, basePos, factionId, owner.getUUID(), owner.getScoreboardName());
        removeClaimAt(level, new ChunkPos(basePos));
        for (ChunkPos chunk : extraChunksToClear) {
            removeClaimAt(level, chunk);
        }
        return BannerModDedicatedServerGameTestSupport.seedClaim(level, basePos, factionId, owner.getUUID(), owner.getScoreboardName());
    }

    private static void removeClaimAt(ServerLevel level, ChunkPos chunk) {
        RecruitsClaim existing = ClaimEvents.recruitsClaimManager.getClaim(chunk);
        if (existing != null) {
            BannerModDedicatedServerGameTestSupport.removeClaim(level, existing);
        }
    }

    private static void giveCurrency(ServerPlayer player, int count) {
        ItemStack stack = BannerModDedicatedServerGameTestSupport.recruitCurrencyStack();
        stack.setCount(count);
        player.getInventory().add(stack);
    }

    private static int currencyCount(ServerPlayer player) {
        return player.getInventory().countItem(BannerModDedicatedServerGameTestSupport.recruitCurrencyStack().getItem());
    }

    private static void assertSavedClaimContains(GameTestHelper helper, ServerLevel level, UUID claimUuid, ChunkPos chunk, boolean expected) {
        ClaimEvents.recruitsClaimManager.save(level);
        boolean actual = RecruitsClaimSaveData.get(level).getAllClaims().stream()
                .filter(claim -> claim.getUUID().equals(claimUuid))
                .anyMatch(claim -> claim.containsChunk(chunk));
        helper.assertTrue(actual == expected, "Expected saved claim chunk membership to be " + expected);
    }

    private static void assertSavedClaimExists(GameTestHelper helper, ServerLevel level, UUID claimUuid, boolean expected) {
        ClaimEvents.recruitsClaimManager.save(level);
        boolean actual = RecruitsClaimSaveData.get(level).getAllClaims().stream()
                .anyMatch(claim -> claim.getUUID().equals(claimUuid));
        helper.assertTrue(actual == expected, "Expected saved claim existence to be " + expected);
    }
}
