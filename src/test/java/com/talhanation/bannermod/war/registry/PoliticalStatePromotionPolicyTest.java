package com.talhanation.bannermod.war.registry;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalStatePromotionPolicyTest {
    private static final UUID CLAIM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void rejectsPromotionWithoutRequiredInfrastructure() {
        BannerModSettlementSnapshot snapshot = snapshot(List.of(building("bannermod:storage_area")));

        PoliticalStatePromotionPolicy.Result result = PoliticalStatePromotionPolicy.evaluate(snapshot);

        assertFalse(result.allowed());
        assertTrue(result.missingRequirements().contains("town_hall_or_starter_fort"));
        assertTrue(result.missingRequirements().contains("market"));
        assertTrue(result.denialReason().startsWith("infrastructure_insufficient"));
    }

    @Test
    void allowsPromotionWithCoreStorageAndMarket() {
        BannerModSettlementSnapshot snapshot = snapshot(List.of(
                building("bannermod:starter_fort"),
                building("bannermod:storage_area"),
                building("bannermod:market_area")
        ));

        PoliticalStatePromotionPolicy.Result result = PoliticalStatePromotionPolicy.evaluate(snapshot);

        assertTrue(result.allowed());
    }

    private static BannerModSettlementSnapshot snapshot(List<BannerModSettlementBuildingRecord> buildings) {
        BannerModSettlementSnapshot empty = BannerModSettlementSnapshot.create(CLAIM, new ChunkPos(0, 0), null);
        return new BannerModSettlementSnapshot(
                empty.claimUuid(),
                empty.anchorChunkX(),
                empty.anchorChunkZ(),
                empty.settlementFactionId(),
                empty.lastRefreshedTick(),
                empty.residentCapacity(),
                empty.workplaceCapacity(),
                empty.assignedWorkerCount(),
                empty.assignedResidentCount(),
                empty.unassignedWorkerCount(),
                empty.missingWorkAreaAssignmentCount(),
                empty.stockpileSummary(),
                empty.marketState(),
                empty.desiredGoodsSnapshot(),
                empty.projectCandidateSnapshot(),
                empty.tradeRouteHandoffSnapshot(),
                empty.supplySignalState(),
                empty.residents(),
                buildings
        );
    }

    private static BannerModSettlementBuildingRecord building(String typeId) {
        return new BannerModSettlementBuildingRecord(
                UUID.randomUUID(),
                typeId,
                BlockPos.ZERO,
                null,
                null,
                0,
                1,
                0,
                List.of()
        );
    }
}
