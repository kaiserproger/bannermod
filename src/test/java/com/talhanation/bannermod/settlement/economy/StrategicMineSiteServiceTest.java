package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.compat.venaterra.VenaterraDepositCandidate;
import com.talhanation.bannermod.compat.venaterra.VenaterraDepositCategory;
import com.talhanation.bannermod.compat.venaterra.VenaterraDepositProvider;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicMineSiteServiceTest {
    @Test
    void validMineBuildingCreatesSite() {
        RecruitsClaim claim = claim(UUID.randomUUID());
        UUID mineId = UUID.randomUUID();

        List<StrategicMineSite> sites = StrategicMineSiteService.derive(
                null,
                claim,
                snapshot(claim.getUUID(), List.of()),
                List.of(validated(claim.getUUID(), mineId, BuildingType.MINE, BuildingValidationState.VALID)),
                emptyDeposits()
        );

        assertEquals(1, sites.size());
        StrategicMineSite site = sites.getFirst();
        assertEquals(mineId, site.siteId());
        assertEquals(claim.getUUID(), site.claimUuid());
        assertEquals(claim.getOwnerPoliticalEntityId(), site.ownerPoliticalEntityId());
        assertEquals(Level.OVERWORLD, site.dimension());
        assertEquals(StrategicMineSite.SourceType.VALIDATED_MINE_BUILDING, site.sourceType());
        assertTrue(site.degraded());
        assertTrue(site.unknown());
    }

    @Test
    void venaterraIronCandidateMapsCategoryAndRichness() {
        RecruitsClaim claim = claim(UUID.randomUUID());
        UUID mineId = UUID.randomUUID();

        List<StrategicMineSite> sites = StrategicMineSiteService.derive(
                null,
                claim,
                snapshot(claim.getUUID(), List.of()),
                List.of(validated(claim.getUUID(), mineId, BuildingType.MINE, BuildingValidationState.VALID)),
                deposits(List.of(new VenaterraDepositCandidate(
                        VenaterraDepositCategory.IRON,
                        ResourceLocation.withDefaultNamespace("iron_ore"),
                        ResourceLocation.withDefaultNamespace("raw_iron"),
                        new BlockPos(2, 64, 2),
                        0.75F,
                        0.8D,
                        new VenaterraDepositCandidate.SourceMetadata("venaterra", "test", Level.OVERWORLD.location())
                )))
        );

        StrategicMineSite site = sites.getFirst();
        assertEquals(VenaterraDepositCategory.IRON, site.resourceCategory());
        assertEquals(0.75F, site.richness());
        assertFalse(site.degraded());
        assertFalse(site.unknown());
    }

    @Test
    void nearestReliableDepositWinsOverFartherHigherConfidenceDeposit() {
        RecruitsClaim claim = claim(UUID.randomUUID());
        UUID mineId = UUID.randomUUID();

        List<StrategicMineSite> sites = StrategicMineSiteService.derive(
                null,
                claim,
                snapshot(claim.getUUID(), List.of()),
                List.of(validated(claim.getUUID(), mineId, BuildingType.MINE, BuildingValidationState.VALID)),
                deposits(List.of(
                        new VenaterraDepositCandidate(
                                VenaterraDepositCategory.IRON,
                                ResourceLocation.withDefaultNamespace("iron_ore"),
                                ResourceLocation.withDefaultNamespace("raw_iron"),
                                new BlockPos(2, 64, 2),
                                0.35F,
                                0.2D,
                                new VenaterraDepositCandidate.SourceMetadata("venaterra", "test", Level.OVERWORLD.location())
                        ),
                        new VenaterraDepositCandidate(
                                VenaterraDepositCategory.INDUSTRIAL_FUEL,
                                ResourceLocation.withDefaultNamespace("coal_ore"),
                                ResourceLocation.withDefaultNamespace("coal"),
                                new BlockPos(4, 64, 2),
                                0.9F,
                                0.95D,
                                new VenaterraDepositCandidate.SourceMetadata("venaterra", "test", Level.OVERWORLD.location())
                        )
                ))
        );

        StrategicMineSite site = sites.getFirst();
        assertEquals(VenaterraDepositCategory.IRON, site.resourceCategory());
        assertEquals(0.35F, site.richness());
    }

    @Test
    void crossDimensionDepositDoesNotAttachToMineSite() {
        RecruitsClaim claim = claim(UUID.randomUUID());
        UUID mineId = UUID.randomUUID();

        List<StrategicMineSite> sites = StrategicMineSiteService.derive(
                null,
                claim,
                snapshot(claim.getUUID(), List.of()),
                List.of(validated(claim.getUUID(), mineId, BuildingType.MINE, BuildingValidationState.VALID)),
                deposits(List.of(new VenaterraDepositCandidate(
                        VenaterraDepositCategory.IRON,
                        ResourceLocation.withDefaultNamespace("iron_ore"),
                        ResourceLocation.withDefaultNamespace("raw_iron"),
                        new BlockPos(2, 64, 2),
                        0.75F,
                        0.8D,
                        new VenaterraDepositCandidate.SourceMetadata("venaterra", "test", Level.NETHER.location())
                )))
        );

        StrategicMineSite site = sites.getFirst();
        assertEquals(VenaterraDepositCategory.UNKNOWN_OTHER, site.resourceCategory());
        assertTrue(site.degraded());
        assertTrue(site.unknown());
    }

    @Test
    void noReliableDepositGivesUnknownDegradedFallback() {
        RecruitsClaim claim = claim(UUID.randomUUID());

        List<StrategicMineSite> sites = StrategicMineSiteService.derive(
                null,
                claim,
                snapshot(claim.getUUID(), List.of(miningArea(UUID.randomUUID()))),
                List.of(),
                deposits(List.of(new VenaterraDepositCandidate(
                        VenaterraDepositCategory.UNKNOWN_OTHER,
                        null,
                        null,
                        new BlockPos(0, 64, 0),
                        0.5F,
                        0.7D,
                        new VenaterraDepositCandidate.SourceMetadata("venaterra", "test", Level.OVERWORLD.location())
                )))
        );

        StrategicMineSite site = sites.getFirst();
        assertEquals(StrategicMineSite.SourceType.CLAIM_MINE_WORK_AREA, site.sourceType());
        assertEquals(VenaterraDepositCategory.UNKNOWN_OTHER, site.resourceCategory());
        assertEquals(0.0F, site.richness());
        assertTrue(site.degraded());
        assertTrue(site.unknown());
    }

    @Test
    void invalidValidatedMineDoesNotReappearThroughSnapshotFallback() {
        RecruitsClaim claim = claim(UUID.randomUUID());
        UUID mineId = UUID.randomUUID();

        List<StrategicMineSite> sites = StrategicMineSiteService.derive(
                null,
                claim,
                snapshot(claim.getUUID(), List.of(miningArea(mineId))),
                List.of(validated(claim.getUUID(), mineId, BuildingType.MINE, BuildingValidationState.INVALID)),
                deposits(List.of(new VenaterraDepositCandidate(
                        VenaterraDepositCategory.IRON,
                        ResourceLocation.withDefaultNamespace("iron_ore"),
                        ResourceLocation.withDefaultNamespace("raw_iron"),
                        new BlockPos(0, 64, 0),
                        0.75F,
                        0.8D,
                        new VenaterraDepositCandidate.SourceMetadata("venaterra", "test", Level.OVERWORLD.location())
                )))
        );

        assertTrue(sites.isEmpty());
    }

    @Test
    void nonMineBuildingIsNotIncluded() {
        RecruitsClaim claim = claim(UUID.randomUUID());

        List<StrategicMineSite> sites = StrategicMineSiteService.derive(
                null,
                claim,
                snapshot(claim.getUUID(), List.of(building(UUID.randomUUID(), "bannermod:validated_farm"))),
                List.of(validated(claim.getUUID(), UUID.randomUUID(), BuildingType.FARM, BuildingValidationState.VALID)),
                emptyDeposits()
        );

        assertTrue(sites.isEmpty());
    }

    private static RecruitsClaim claim(UUID owner) {
        RecruitsClaim claim = new RecruitsClaim("claim", owner);
        claim.addChunk(new ChunkPos(0, 0));
        return claim;
    }

    private static ValidatedBuildingRecord validated(UUID claimUuid,
                                                     UUID buildingUuid,
                                                     BuildingType type,
                                                     BuildingValidationState state) {
        return new ValidatedBuildingRecord(
                buildingUuid,
                claimUuid,
                type,
                Level.OVERWORLD,
                new BlockPos(0, 64, 0),
                List.of(),
                new AABB(0, 64, 0, 4, 68, 4),
                state,
                1,
                100,
                10L,
                20L,
                state == BuildingValidationState.VALID ? 0L : 20L
        );
    }

    private static SettlementBuildingRecord miningArea(UUID buildingUuid) {
        return building(buildingUuid, "bannermod:mining_area");
    }

    private static SettlementBuildingRecord building(UUID buildingUuid, String typeId) {
        return new SettlementBuildingRecord(
                buildingUuid,
                typeId,
                new BlockPos(0, 64, 0),
                UUID.randomUUID(),
                "blueguild",
                0,
                1,
                1,
                List.of()
        );
    }

    private static SettlementSnapshot snapshot(UUID claimUuid, List<SettlementBuildingRecord> buildings) {
        return new SettlementSnapshot(
                claimUuid,
                0,
                0,
                "blueguild",
                100L,
                0,
                0,
                0,
                0,
                0,
                0,
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(),
                buildings
        );
    }

    private static VenaterraDepositProvider emptyDeposits() {
        return deposits(List.of());
    }

    private static VenaterraDepositProvider deposits(List<VenaterraDepositCandidate> candidates) {
        return (level, claim) -> candidates;
    }
}
