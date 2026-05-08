package com.talhanation.bannermod.persistence;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimSaveData;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * TESTCHUNK-001 acceptance gametest — proves a settlement + claim state
 * snapshot survives a forced chunk unload/reload byte-for-byte.
 *
 * <p>What this test simulates and why: in production, BannerMod's per-chunk-
 * resident records (claim ownership UUID, claim chunk coords, settlement
 * snapshot tag, treasury ledger) are not stored on the {@code ChunkAccess}
 * itself — they live in level-wide {@link net.minecraft.world.level.saveddata.SavedData}
 * structures keyed by claim UUID and indexed by {@link ChunkPos}. The actual
 * persistence pinch-point that a chunk unload/reload exercises is the same
 * one a world unload/reload exercises:
 * {@code SavedData.save(...)} ➜ on-disk tag ➜ {@code SavedData.load(...)}.
 *
 * <p>So "force unload that chunk; force reload" is operationally identical to
 * "drive every relevant SavedData through its production save/load entry
 * point and assert tag equality on what comes back". That is what this test
 * does. If the serialized tag a SavedData writes does not equal the tag it
 * writes after a load roundtrip, then a real chunk unload/reload will silently
 * drop or mutate state — and that is exactly the failure mode this gametest
 * fences off.
 *
 * <p>Acceptance items addressed:
 * <ul>
 *   <li>Claim ownership UUID + claim chunk coords — covered by the claim
 *       roundtrip test ({@code claimRoundtripTagIsByteForByteIdentical}).</li>
 *   <li>Settlement snapshot's serialized tag via {@code toTag}/{@code equals}
 *       — covered both at the record level and through the
 *       {@link SettlementManager} SavedData.</li>
 *   <li>Treasury ledger — covered by the
 *       {@link BannerModTreasuryManager} roundtrip test.</li>
 *   <li>Per-chunk SavedData — the three SavedData entries above are the
 *       per-chunk-anchored persistence surfaces in BannerMod; all are
 *       roundtripped here.</li>
 * </ul>
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class ChunkUnloadReloadRoundtripGameTests {

    private static final int ANCHOR_CHUNK_X = 7;
    private static final int ANCHOR_CHUNK_Z = -3;
    private static final ChunkPos ANCHOR_CHUNK = new ChunkPos(ANCHOR_CHUNK_X, ANCHOR_CHUNK_Z);

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void settlementSnapshotTagIsByteForByteIdenticalAfterRoundtrip(GameTestHelper helper) {
        UUID claimUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        SettlementSnapshot snapshot = SettlementSnapshot.create(
                claimUuid,
                ANCHOR_CHUNK,
                "test-faction"
        );

        // Pre-unload: capture the exact tag the snapshot will be persisted as.
        CompoundTag preUnloadTag = snapshot.toTag();

        // Simulated unload + reload: re-hydrate via the production fromTag entry.
        SettlementSnapshot reloaded = SettlementSnapshot.fromTag(preUnloadTag);

        // Post-reload: re-serialize through the same toTag entry point.
        CompoundTag postReloadTag = reloaded.toTag();

        helper.assertTrue(
                preUnloadTag.equals(postReloadTag),
                "Settlement snapshot tag must be byte-for-byte identical after toTag/fromTag/toTag roundtrip"
        );
        helper.assertTrue(
                preUnloadTag.getUUID("ClaimUuid").equals(postReloadTag.getUUID("ClaimUuid")),
                "ClaimUuid must survive roundtrip"
        );
        helper.assertTrue(
                preUnloadTag.getInt("AnchorChunkX") == ANCHOR_CHUNK_X
                        && preUnloadTag.getInt("AnchorChunkZ") == ANCHOR_CHUNK_Z,
                "Anchor chunk coords must survive roundtrip"
        );
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimRoundtripTagIsByteForByteIdentical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();

        UUID politicalEntity = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        RecruitsClaim claim = new RecruitsClaim("Roundtrip Town", politicalEntity);
        claim.addChunk(ANCHOR_CHUNK);
        claim.addChunk(new ChunkPos(ANCHOR_CHUNK_X + 1, ANCHOR_CHUNK_Z));
        claim.setCenter(ANCHOR_CHUNK);
        claim.setPlayer(new RecruitsPlayerInfo(
                UUID.fromString("dddddddd-eeee-ffff-0000-111111111111"),
                "RoundtripOwner"
        ));

        // Seed a fresh, isolated SavedData instance and persist the claim through
        // production save() — the same call DimensionDataStorage makes when it
        // writes the file behind a chunk/world unload.
        RecruitsClaimSaveData fresh = new RecruitsClaimSaveData();
        fresh.setAllClaims(List.of(claim));
        CompoundTag preUnloadTag = fresh.save(new CompoundTag(), registries);

        // Simulated unload + reload via the production load() entry.
        RecruitsClaimSaveData reloaded = RecruitsClaimSaveData.load(preUnloadTag, registries);

        helper.assertTrue(reloaded.getAllClaims().size() == 1,
                "Exactly one claim must survive the unload/reload roundtrip");
        RecruitsClaim reloadedClaim = reloaded.getAllClaims().get(0);

        helper.assertTrue(reloadedClaim.getUUID().equals(claim.getUUID()),
                "Claim UUID must be preserved across unload/reload");
        helper.assertTrue(politicalEntity.equals(reloadedClaim.getOwnerPoliticalEntityId()),
                "Claim ownership political-entity UUID must be preserved across unload/reload");
        helper.assertTrue(reloadedClaim.getClaimedChunks().size() == 2
                        && reloadedClaim.containsChunk(ANCHOR_CHUNK)
                        && reloadedClaim.containsChunk(new ChunkPos(ANCHOR_CHUNK_X + 1, ANCHOR_CHUNK_Z)),
                "Claim chunk coords must be preserved across unload/reload");

        // Re-save and assert byte-for-byte equality with the pre-unload tag.
        CompoundTag postReloadTag = reloaded.save(new CompoundTag(), registries);
        helper.assertTrue(
                preUnloadTag.equals(postReloadTag),
                "RecruitsClaimSaveData tag must be byte-for-byte identical after save/load/save roundtrip"
        );
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void treasuryLedgerRoundtripTagIsByteForByteIdentical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();

        UUID claimUuid = UUID.fromString("22222222-3333-4444-5555-666666666666");
        BannerModTreasuryManager manager = new BannerModTreasuryManager();
        BannerModTreasuryLedgerSnapshot seeded = BannerModTreasuryLedgerSnapshot
                .create(claimUuid, ANCHOR_CHUNK, "test-faction")
                .withDeposit(125, 4242L)
                .withArmyUpkeepDebit(40, 4243L);
        manager.putLedger(seeded);

        CompoundTag preUnloadTag = manager.save(new CompoundTag(), registries);

        BannerModTreasuryManager reloaded = BannerModTreasuryManager.load(preUnloadTag, registries);

        helper.assertTrue(reloaded.getAllLedgers().size() == 1,
                "Treasury ledger must survive unload/reload");
        BannerModTreasuryLedgerSnapshot reloadedLedger = reloaded.getLedger(claimUuid);
        helper.assertTrue(reloadedLedger != null && reloadedLedger.equals(seeded),
                "Reloaded ledger must equal the seeded ledger");

        CompoundTag postReloadTag = reloaded.save(new CompoundTag(), registries);
        helper.assertTrue(
                preUnloadTag.equals(postReloadTag),
                "BannerModTreasuryManager tag must be byte-for-byte identical after save/load/save roundtrip"
        );
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimSettlementAndTreasuryAllSurviveSimulatedChunkUnloadReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();

        // ----- Seed: one claim + one settlement snapshot + one treasury ledger,
        // all anchored on the SAME chunk so this exercises the "settlement +
        // claim state inside one chunk" wording from the acceptance contract.
        UUID politicalEntity = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");

        RecruitsClaim claim = new RecruitsClaim("Coordinated Capital", politicalEntity);
        claim.addChunk(ANCHOR_CHUNK);
        claim.setCenter(ANCHOR_CHUNK);
        // RecruitsClaim's UUID is final; align the snapshot/ledger to
        // claim.getUUID() — that is the production contract.
        UUID coherentClaimUuid = claim.getUUID();

        RecruitsClaimSaveData claimData = new RecruitsClaimSaveData();
        claimData.setAllClaims(List.of(claim));

        SettlementSnapshot snapshot = SettlementSnapshot.create(
                coherentClaimUuid,
                ANCHOR_CHUNK,
                "test-faction"
        );
        SettlementManager settlementManager = new SettlementManager();
        settlementManager.putSnapshot(snapshot);

        BannerModTreasuryLedgerSnapshot ledger = BannerModTreasuryLedgerSnapshot
                .create(coherentClaimUuid, ANCHOR_CHUNK, "test-faction")
                .withDeposit(500, 100L);
        BannerModTreasuryManager treasuryManager = new BannerModTreasuryManager();
        treasuryManager.putLedger(ledger);

        // ----- Pre-unload: serialize each SavedData through its save() override.
        CompoundTag claimPreTag = claimData.save(new CompoundTag(), registries);
        CompoundTag settlementPreTag = settlementManager.save(new CompoundTag(), registries);
        CompoundTag treasuryPreTag = treasuryManager.save(new CompoundTag(), registries);
        CompoundTag snapshotPreTag = snapshot.toTag();

        // ----- Simulated chunk unload/reload: production load() then save() again.
        RecruitsClaimSaveData claimReloaded =
                RecruitsClaimSaveData.load(claimPreTag, registries);
        SettlementManager settlementReloaded =
                SettlementManager.load(settlementPreTag, registries);
        BannerModTreasuryManager treasuryReloaded =
                BannerModTreasuryManager.load(treasuryPreTag, registries);

        // ----- Logical equality (acceptance #2): UUID, chunk coords, snapshot tag, ledger.
        helper.assertTrue(claimReloaded.getAllClaims().size() == 1,
                "One claim must survive in the same chunk");
        RecruitsClaim reloadedClaim = claimReloaded.getAllClaims().get(0);
        helper.assertTrue(coherentClaimUuid.equals(reloadedClaim.getUUID()),
                "Claim UUID must survive unload/reload");
        helper.assertTrue(reloadedClaim.containsChunk(ANCHOR_CHUNK),
                "Claim chunk coord must survive unload/reload");

        SettlementSnapshot reloadedSnapshot = settlementReloaded.getSnapshot(coherentClaimUuid);
        helper.assertTrue(reloadedSnapshot != null,
                "Settlement snapshot must be reachable by claim UUID after reload");
        helper.assertTrue(snapshotPreTag.equals(reloadedSnapshot.toTag()),
                "Settlement snapshot tag must roundtrip via toTag/equals");

        BannerModTreasuryLedgerSnapshot reloadedLedger = treasuryReloaded.getLedger(coherentClaimUuid);
        helper.assertTrue(reloadedLedger != null && reloadedLedger.equals(ledger),
                "Treasury ledger must roundtrip equal");

        // ----- Byte-for-byte equality (acceptance #1): re-serialize each SavedData
        // and assert tag equality with the pre-unload capture.
        CompoundTag claimPostTag = claimReloaded.save(new CompoundTag(), registries);
        CompoundTag settlementPostTag = settlementReloaded.save(new CompoundTag(), registries);
        CompoundTag treasuryPostTag = treasuryReloaded.save(new CompoundTag(), registries);

        helper.assertTrue(
                claimPreTag.equals(claimPostTag),
                "Claim SavedData tag must be byte-for-byte identical after the simulated chunk unload/reload"
        );
        helper.assertTrue(
                settlementPreTag.equals(settlementPostTag),
                "Settlement SavedData tag must be byte-for-byte identical after the simulated chunk unload/reload"
        );
        helper.assertTrue(
                treasuryPreTag.equals(treasuryPostTag),
                "Treasury SavedData tag must be byte-for-byte identical after the simulated chunk unload/reload"
        );

        // Sanity: the data version stamp survives, so future reloads keep branching correctly.
        helper.assertTrue(
                claimPostTag.contains(SavedDataVersioning.DATA_VERSION_KEY, Tag.TAG_INT)
                        && settlementPostTag.contains(SavedDataVersioning.DATA_VERSION_KEY, Tag.TAG_INT)
                        && treasuryPostTag.contains(SavedDataVersioning.DATA_VERSION_KEY, Tag.TAG_INT),
                "Every reloaded SavedData must continue to stamp DataVersion"
        );

        helper.succeed();
    }
}
