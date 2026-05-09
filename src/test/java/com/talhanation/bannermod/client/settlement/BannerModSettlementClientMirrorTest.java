package com.talhanation.bannermod.client.settlement;

import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Envelope;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Payload;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.RefreshTrigger;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.SnapshotState;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModSettlementClientMirrorTest {

    @Test
    void governorViewMovesThroughLoadingEmptyFreshAndStaleStates() {
        UUID recruitId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        BannerModSettlementClientMirror mirror = new BannerModSettlementClientMirror();

        mirror.beginGovernorRequest(recruitId, 10L);
        BannerModSettlementClientMirror.GovernorView loading = mirror.governorView(recruitId);
        assertEquals(SnapshotState.LOADING, loading.state());
        assertFalse(loading.canUpdatePolicy());
        assertEquals("gui.bannermod.governor.action.disabled.loading", loading.actionReasonKey());

        mirror.applyGovernorUpdate(recruitId, Envelope.empty(11L, 11L, RefreshTrigger.SCREEN_OPEN));
        BannerModSettlementClientMirror.GovernorView empty = mirror.governorView(recruitId);
        assertEquals(SnapshotState.EMPTY, empty.state());
        assertFalse(empty.canUpdatePolicy());

        BannerModGovernorSnapshot governor = BannerModGovernorSnapshot.create(claimId, new ChunkPos(3, 4), "blue")
                .withHeartbeatReport(30L, 30L, 7, 5, 3, List.of("low_food"), List.of("increase_garrison"))
                .withPolicies(4, 2, 1);
        SettlementSnapshot settlement = SettlementSnapshot.create(claimId, new ChunkPos(3, 4), "blue");
        mirror.applyGovernorUpdate(recruitId, Envelope.ready(12L, 12L, RefreshTrigger.SCREEN_OPEN,
                new Payload(claimId, settlement, governor)));
        BannerModSettlementClientMirror.GovernorView fresh = mirror.governorView(recruitId);
        assertEquals(SnapshotState.READY, fresh.state());
        assertTrue(fresh.canUpdatePolicy());
        assertEquals(7, fresh.citizenCount());
        assertEquals(BannerModGovernorPolicy.GARRISON_PRIORITY.clamp(4), fresh.garrisonPriority());
        assertEquals(BannerModGovernorPolicy.FORTIFICATION_PRIORITY.clamp(2), fresh.fortificationPriority());
        assertEquals("gui.bannermod.governor.action.enabled", fresh.actionReasonKey());

        mirror.markGovernorStale(recruitId, 13L);
        BannerModSettlementClientMirror.GovernorView stale = mirror.governorView(recruitId);
        assertEquals(SnapshotState.STALE, stale.state());
        assertFalse(stale.canUpdatePolicy());
        assertEquals(7, stale.citizenCount());
        assertEquals("gui.bannermod.governor.action.disabled.stale", stale.actionReasonKey());
    }

    @Test
    void loginAndMutationRefreshUpdatesReplaceGovernorSnapshot() {
        UUID recruitId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        BannerModSettlementClientMirror mirror = new BannerModSettlementClientMirror();
        SettlementSnapshot settlement = SettlementSnapshot.create(claimId, new ChunkPos(6, 7), "green");
        BannerModGovernorSnapshot loginGovernor = BannerModGovernorSnapshot.create(claimId, new ChunkPos(6, 7), "green")
                .withHeartbeatReport(20L, 20L, 2, 1, 0, List.of(), List.of());
        BannerModGovernorSnapshot mutationGovernor = loginGovernor
                .withHeartbeatReport(30L, 30L, 5, 3, 1, List.of("new_resident"), List.of("expand_housing"));

        mirror.applyGovernorUpdate(recruitId, Envelope.ready(20L, 20L, RefreshTrigger.LOGIN,
                new Payload(claimId, settlement, loginGovernor)));
        BannerModSettlementClientMirror.GovernorView login = mirror.governorView(recruitId);
        assertEquals(SnapshotState.READY, login.state());
        assertEquals(2, login.citizenCount());

        mirror.applyGovernorUpdate(recruitId, Envelope.ready(30L, 30L, RefreshTrigger.MUTATION_REFRESH,
                new Payload(claimId, settlement, mutationGovernor)));
        BannerModSettlementClientMirror.GovernorView mutation = mirror.governorView(recruitId);
        assertEquals(SnapshotState.READY, mutation.state());
        assertEquals(5, mutation.citizenCount());
        assertEquals(3, mutation.taxesDue());
    }

    @Test
    void governorViewCopiesSeaTradeStatusLinesIntoLogisticsLines() {
        UUID recruitId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        BannerModSettlementClientMirror mirror = new BannerModSettlementClientMirror();
        List<String> seaTradeLines = List.of(
                "gui.bannermod.governor.logistics.sea_trade.loading 3201 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 0 16",
                "gui.bannermod.governor.logistics.sea_trade.travelling 3202 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 8 16",
                "gui.bannermod.governor.logistics.sea_trade.unloading 3203 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 8 16",
                "gui.bannermod.governor.logistics.sea_trade.completed 3204 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 0 16",
                "gui.bannermod.governor.logistics.sea_trade.missing_ship 3205 unassigned gui.bannermod.governor.logistics.sea_trade.reason.no_carrier minecraft:wheat 0 16",
                "gui.bannermod.governor.logistics.sea_trade.blocked_cargo 3206 2201 gui.bannermod.governor.logistics.sea_trade.reason.destination_full minecraft:wheat 4 16"
        );
        SettlementSnapshot settlement = settlementWithSeaTradeLines(claimId, seaTradeLines);
        BannerModGovernorSnapshot governor = BannerModGovernorSnapshot.create(claimId, new ChunkPos(3, 4), "blue");

        mirror.applyGovernorUpdate(recruitId, Envelope.ready(12L, 12L, RefreshTrigger.SCREEN_OPEN,
                new Payload(claimId, settlement, governor)));

        BannerModSettlementClientMirror.GovernorView view = mirror.governorView(recruitId);
        assertTrue(view.logisticsLines().containsAll(seaTradeLines));
    }

    private static SettlementSnapshot settlementWithSeaTradeLines(UUID claimId, List<String> seaTradeLines) {
        return new SettlementSnapshot(
                claimId,
                3,
                4,
                "blue",
                12L,
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
                new SettlementTradeRouteHandoffSnapshot(0, 0, 0, 0, 0, 0, List.of(), List.of(), seaTradeLines),
                SettlementSupplySignalState.empty(),
                List.of(),
                List.of()
        );
    }
}
