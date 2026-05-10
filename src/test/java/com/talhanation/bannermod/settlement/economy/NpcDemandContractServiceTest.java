package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketRecord;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementSellerDispatchRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDemandContractServiceTest {
    private static final UUID CLAIM = UUID.fromString("00000000-0000-0000-0000-000000000291");

    @Test
    void isolatedClaimSpawnsContractWithoutOnlinePlayerInput() {
        NpcDemandContractService service = new NpcDemandContractService();

        service.tickClaim(snapshot(SettlementMarketState.empty(), SettlementTradeRouteHandoffSnapshot.empty()), 100L);

        List<NpcDemandContract> contracts = service.contracts(CLAIM);
        assertEquals(1, contracts.size());
        assertEquals(NpcDemandContract.Status.OPEN, contracts.get(0).status());
    }

    @Test
    void marketAccessImprovesFrequencyAndQualityComparedWithIsolatedFort() {
        NpcDemandContractService isolated = new NpcDemandContractService();
        NpcDemandContractService market = new NpcDemandContractService();
        SettlementSnapshot isolatedSnapshot = snapshot(SettlementMarketState.empty(), SettlementTradeRouteHandoffSnapshot.empty());
        SettlementSnapshot marketSnapshot = snapshot(openMarketState(), SettlementTradeRouteHandoffSnapshot.empty());

        isolated.tickClaim(isolatedSnapshot, 0L);
        market.tickClaim(marketSnapshot, 0L);
        isolated.tickClaim(isolatedSnapshot, 12_000L);
        market.tickClaim(marketSnapshot, 12_000L);

        assertEquals(1, isolated.contracts(CLAIM).size(), "isolated forts wait a full day before another contract");
        assertEquals(2, market.contracts(CLAIM).size(), "market claims can receive contracts twice as often");
        assertTrue(market.contracts(CLAIM).get(0).rewardCoins() > isolated.contracts(CLAIM).get(0).rewardCoins());
        assertTrue(market.contracts(CLAIM).get(0).amount() > isolated.contracts(CLAIM).get(0).amount());
    }

    @Test
    void tradingPostAccessFurtherImprovesContractCadence() {
        NpcDemandContractService market = new NpcDemandContractService();
        NpcDemandContractService tradingPost = new NpcDemandContractService();
        SettlementSnapshot marketSnapshot = snapshot(openMarketState(), SettlementTradeRouteHandoffSnapshot.empty());
        SettlementSnapshot tradingPostClaim = snapshot(SettlementMarketState.empty(), tradingPostHandoffSnapshot());

        market.tickClaim(marketSnapshot, 0L);
        tradingPost.tickClaim(tradingPostClaim, 0L);
        market.tickClaim(marketSnapshot, 9_000L);
        tradingPost.tickClaim(tradingPostClaim, 9_000L);

        assertEquals(1, market.contracts(CLAIM).size());
        assertEquals(2, tradingPost.contracts(CLAIM).size());
        assertNotEquals(market.contracts(CLAIM).get(0).rewardCoins(), tradingPost.contracts(CLAIM).get(0).rewardCoins());
    }

    @Test
    void statusLinesExposeServerBackedContractState() {
        NpcDemandContractService service = new NpcDemandContractService();
        service.tickClaim(snapshot(SettlementMarketState.empty(), SettlementTradeRouteHandoffSnapshot.empty()), 0L);

        List<String> lines = service.statusLines(CLAIM);

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("NPC demand contracts for claim"));
        assertTrue(lines.get(1).contains("status=OPEN"));
        assertTrue(lines.get(1).contains("buyer="));
        assertTrue(lines.get(1).contains("reward="));
    }

    @Test
    void contractsAndScheduleSurviveNbtRoundTrip() {
        SettlementSnapshot isolatedSnapshot = snapshot(SettlementMarketState.empty(), SettlementTradeRouteHandoffSnapshot.empty());
        NpcDemandContractService service = new NpcDemandContractService();
        service.tickClaim(isolatedSnapshot, 0L);

        CompoundTag tag = new CompoundTag();
        service.saveToTag(tag);
        NpcDemandContractService loaded = new NpcDemandContractService();
        loaded.loadFromTag(tag);

        assertEquals(1, loaded.contracts(CLAIM).size());
        loaded.tickClaim(isolatedSnapshot, 12_000L);
        assertEquals(1, loaded.contracts(CLAIM).size(), "next eligible tick should persist across reload");
        loaded.tickClaim(isolatedSnapshot, 24_000L);
        assertEquals(2, loaded.contracts(CLAIM).size());
    }

    private static SettlementSnapshot snapshot(SettlementMarketState marketState,
                                               SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot) {
        return new SettlementSnapshot(
                CLAIM,
                1,
                2,
                "blueguild",
                0L,
                0,
                0,
                0,
                0,
                0,
                0,
                SettlementStockpileSummary.empty(),
                marketState,
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                tradeRouteHandoffSnapshot,
                SettlementSupplySignalState.empty(),
                List.of(),
                List.of()
        );
    }

    private static SettlementMarketState openMarketState() {
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-000000000292");
        return new SettlementMarketState(
                1,
                1,
                16,
                8,
                0,
                0,
                List.of(new SettlementMarketRecord(marketUuid, "Market", true, 16, 8)),
                List.of()
        );
    }

    private static SettlementTradeRouteHandoffSnapshot tradingPostHandoffSnapshot() {
        return new SettlementTradeRouteHandoffSnapshot(
                1,
                1,
                1,
                1,
                0,
                0,
                List.of(),
                List.of(new SettlementSellerDispatchRecord(
                        UUID.fromString("00000000-0000-0000-0000-000000000293"),
                        UUID.fromString("00000000-0000-0000-0000-000000000294"),
                        "Trading Post",
                        com.talhanation.bannermod.settlement.SettlementSellerDispatchState.READY
                )),
                List.of()
        );
    }
}
