package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortUpgradeServiceTest {
    @Test
    void upgradeDebitsRequirementOnceAndIncreasesFortLevel() {
        UUID owner = UUID.randomUUID();
        RecruitsClaim claim = ownedClaim(owner);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        StrategicResourceAccountingManager accounting = fundedAccounting(claim.getUUID(), 200, 80, 250, 180);
        treasury.depositTaxes(claim.getUUID(), new ChunkPos(0, 0), "state", 400, 10L);

        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasury,
                accounting,
                claim,
                owner,
                false,
                null
        );

        assertTrue(result.upgraded());
        assertEquals(1, claim.getFortLevel());
        assertEquals(200, accounting.getAccount(claim.getUUID()).food());

        assertTrue(FortUpgradeService.applyUpgrade(treasury, accounting, claim, result, 20L));

        assertEquals(2, claim.getFortLevel());
        assertEquals(80, accounting.getAccount(claim.getUUID()).food());
        assertEquals(40, accounting.getAccount(claim.getUUID()).iron());
        assertEquals(90, accounting.getAccount(claim.getUUID()).wood());
        assertEquals(60, accounting.getAccount(claim.getUUID()).stone());
        assertEquals(150, treasury.getLedger(claim.getUUID()).treasuryBalance());
    }

    @Test
    void missingResourceDeniesWithoutPartialDebit() {
        UUID owner = UUID.randomUUID();
        RecruitsClaim claim = ownedClaim(owner);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        StrategicResourceAccountingManager accounting = fundedAccounting(claim.getUUID(), 200, 80, 250, 119);
        treasury.depositTaxes(claim.getUUID(), new ChunkPos(0, 0), "state", 400, 10L);

        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasury,
                accounting,
                claim,
                owner,
                false,
                null
        );

        assertFalse(result.upgraded());
        assertEquals(FortUpgradeService.DenialReason.MISSING_RESOURCES, result.denialReason());
        assertEquals(1, claim.getFortLevel());
        assertEquals(200, accounting.getAccount(claim.getUUID()).food());
        assertEquals(80, accounting.getAccount(claim.getUUID()).iron());
        assertEquals(250, accounting.getAccount(claim.getUUID()).wood());
        assertEquals(119, accounting.getAccount(claim.getUUID()).stone());
        assertEquals(400, treasury.getLedger(claim.getUUID()).treasuryBalance());
    }

    @Test
    void stalePlanDoesNotDebitOrUpgradeWhenResourceBalanceChangesBeforeApply() {
        UUID owner = UUID.randomUUID();
        RecruitsClaim claim = ownedClaim(owner);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        StrategicResourceAccountingManager accounting = fundedAccounting(claim.getUUID(), 200, 80, 250, 180);
        treasury.depositTaxes(claim.getUUID(), new ChunkPos(0, 0), "state", 400, 10L);
        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasury,
                accounting,
                claim,
                owner,
                false,
                null
        );

        accounting.debit(claim.getUUID(), StrategicResourceBucket.STONE, 61, 15L);

        assertFalse(FortUpgradeService.applyUpgrade(treasury, accounting, claim, result, 20L));
        assertEquals(1, claim.getFortLevel());
        assertEquals(200, accounting.getAccount(claim.getUUID()).food());
        assertEquals(80, accounting.getAccount(claim.getUUID()).iron());
        assertEquals(250, accounting.getAccount(claim.getUUID()).wood());
        assertEquals(119, accounting.getAccount(claim.getUUID()).stone());
        assertEquals(400, treasury.getLedger(claim.getUUID()).treasuryBalance());
    }

    @Test
    void missingAuthorityDeniesBeforeDebit() {
        UUID owner = UUID.randomUUID();
        RecruitsClaim claim = ownedClaim(owner);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        StrategicResourceAccountingManager accounting = fundedAccounting(claim.getUUID(), 200, 80, 250, 180);
        treasury.depositTaxes(claim.getUUID(), new ChunkPos(0, 0), "state", 400, 10L);

        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasury,
                accounting,
                claim,
                UUID.randomUUID(),
                false,
                null
        );

        assertFalse(result.upgraded());
        assertEquals(FortUpgradeService.DenialReason.MISSING_AUTHORITY, result.denialReason());
        assertEquals(1, claim.getFortLevel());
        assertEquals(200, accounting.getAccount(claim.getUUID()).food());
        assertEquals(400, treasury.getLedger(claim.getUUID()).treasuryBalance());
    }

    @Test
    void politicalLeaderCanUpgradeClaimOwnedByState() {
        UUID leader = UUID.randomUUID();
        UUID politicalId = UUID.randomUUID();
        RecruitsClaim claim = new RecruitsClaim("Fort", politicalId);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        StrategicResourceAccountingManager accounting = fundedAccounting(claim.getUUID(), 200, 80, 250, 180);
        treasury.depositTaxes(claim.getUUID(), new ChunkPos(0, 0), "state", 400, 10L);

        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasury,
                accounting,
                claim,
                leader,
                false,
                politicalEntity(politicalId, leader)
        );

        assertTrue(result.upgraded());
        assertTrue(FortUpgradeService.applyUpgrade(treasury, accounting, claim, result, 20L));
        assertEquals(2, claim.getFortLevel());
    }

    @Test
    void stateOwnedClaimRejectsStaleDirectPlayerOwnerWithoutPoliticalAuthority() {
        UUID staleOwner = UUID.randomUUID();
        UUID politicalId = UUID.randomUUID();
        RecruitsClaim claim = new RecruitsClaim("Fort", politicalId);
        claim.setPlayer(new RecruitsPlayerInfo(staleOwner, "stale"));
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        StrategicResourceAccountingManager accounting = fundedAccounting(claim.getUUID(), 200, 80, 250, 180);
        treasury.depositTaxes(claim.getUUID(), new ChunkPos(0, 0), "state", 400, 10L);

        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasury,
                accounting,
                claim,
                staleOwner,
                false,
                null
        );

        assertFalse(result.upgraded());
        assertEquals(FortUpgradeService.DenialReason.MISSING_AUTHORITY, result.denialReason());
        assertEquals(1, claim.getFortLevel());
        assertEquals(200, accounting.getAccount(claim.getUUID()).food());
        assertEquals(400, treasury.getLedger(claim.getUUID()).treasuryBalance());
    }

    @Test
    void rollbackRestoresResourcesAndFortLevelAfterAppliedUpgrade() {
        UUID owner = UUID.randomUUID();
        RecruitsClaim claim = ownedClaim(owner);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        StrategicResourceAccountingManager accounting = fundedAccounting(claim.getUUID(), 200, 80, 250, 180);
        treasury.depositTaxes(claim.getUUID(), new ChunkPos(0, 0), "state", 400, 10L);
        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasury,
                accounting,
                claim,
                owner,
                false,
                null
        );

        assertTrue(FortUpgradeService.applyUpgrade(treasury, accounting, claim, result, 20L));
        FortUpgradeService.rollbackUpgrade(treasury, accounting, claim, 1, result, 21L);

        assertEquals(1, claim.getFortLevel());
        assertEquals(200, accounting.getAccount(claim.getUUID()).food());
        assertEquals(80, accounting.getAccount(claim.getUUID()).iron());
        assertEquals(250, accounting.getAccount(claim.getUUID()).wood());
        assertEquals(180, accounting.getAccount(claim.getUUID()).stone());
        assertEquals(400, treasury.getLedger(claim.getUUID()).treasuryBalance());
    }

    @Test
    void maxLevelDeniesAsInvalidState() {
        UUID owner = UUID.randomUUID();
        RecruitsClaim claim = ownedClaim(owner);
        claim.setFortLevel(FortLevelDefinition.MAX_LEVEL);

        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                new BannerModTreasuryManager(),
                new StrategicResourceAccountingManager(),
                claim,
                owner,
                false,
                null
        );

        assertFalse(result.upgraded());
        assertEquals(FortUpgradeService.DenialReason.INVALID_STATE, result.denialReason());
        assertEquals(FortLevelDefinition.MAX_LEVEL, claim.getFortLevel());
    }

    private static RecruitsClaim ownedClaim(UUID owner) {
        RecruitsClaim claim = new RecruitsClaim("Fort", null);
        claim.setPlayer(new RecruitsPlayerInfo(owner, "owner"));
        return claim;
    }

    private static StrategicResourceAccountingManager fundedAccounting(UUID claimUuid,
                                                                       int food,
                                                                       int iron,
                                                                       int wood,
                                                                       int stone) {
        StrategicResourceAccountingManager accounting = new StrategicResourceAccountingManager();
        accounting.credit(claimUuid, StrategicResourceBucket.FOOD, food, 10L);
        accounting.credit(claimUuid, StrategicResourceBucket.IRON, iron, 10L);
        accounting.credit(claimUuid, StrategicResourceBucket.WOOD, wood, 10L);
        accounting.credit(claimUuid, StrategicResourceBucket.STONE, stone, 10L);
        return accounting;
    }

    private static PoliticalEntityRecord politicalEntity(UUID politicalId, UUID leader) {
        return new PoliticalEntityRecord(
                politicalId,
                "State",
                PoliticalEntityStatus.STATE,
                leader,
                List.of(),
                BlockPos.ZERO,
                "",
                "",
                "",
                "",
                0L
        );
    }
}
