package com.talhanation.bannermod.client.settlement;

import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.governance.BannerModGovernorRecommendation;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStrategicSignals;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Envelope;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Payload;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.RefreshTrigger;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.SnapshotState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BannerModSettlementClientMirror {
    private final Map<UUID, Envelope> governorEnvelopes = new HashMap<>();

    public void beginGovernorRequest(UUID recruitId, long gameTime) {
        this.governorEnvelopes.put(recruitId, Envelope.loading(gameTime, RefreshTrigger.SCREEN_OPEN));
    }

    public void applyGovernorUpdate(UUID recruitId, Envelope envelope) {
        this.governorEnvelopes.put(recruitId, envelope);
    }

    public void markGovernorStale(UUID recruitId, long gameTime) {
        Envelope current = this.governorEnvelopes.get(recruitId);
        if (current == null || current.payload() == null) {
            this.beginGovernorRequest(recruitId, gameTime);
            return;
        }
        this.governorEnvelopes.put(recruitId, Envelope.stale(current.serverVersion(), gameTime,
                RefreshTrigger.MUTATION_REFRESH, current.serverVersion(), current.payload()));
    }

    public GovernorView governorView(UUID recruitId) {
        Envelope envelope = this.governorEnvelopes.get(recruitId);
        if (envelope == null || envelope.state() == SnapshotState.LOADING) {
            return GovernorView.loading();
        }
        if (!envelope.isCurrentContract()) {
            return GovernorView.empty("gui.bannermod.governor.state.outdated_contract");
        }
        if (envelope.state() == SnapshotState.EMPTY || envelope.payload() == null) {
            return GovernorView.empty("gui.bannermod.governor.state.empty");
        }

        Payload payload = envelope.payload();
        SettlementSnapshot settlement = payload.settlementSnapshot();
        BannerModGovernorSnapshot governor = payload.governorSnapshot();
        boolean stale = envelope.isStale();
        List<String> recommendations = governor == null ? List.of() : new ArrayList<>(governor.recommendationTokens());
        if (settlement != null) {
            recommendations = new ArrayList<>(recommendations);
            recommendations.addAll(settlement.tradeRouteHandoffSnapshot().seaTradeStatusLines());
        }

        return new GovernorView(
                stale ? SnapshotState.STALE : SnapshotState.READY,
                stale ? "gui.bannermod.governor.state.stale" : "gui.bannermod.governor.state.fresh",
                stale ? 0x8A6D1D : 0x2E5D32,
                settlement == null ? "gui.bannermod.governor.settlement.missing" : "gui.bannermod.governor.settlement.ready",
                governor == null ? 0 : governor.citizenCount(),
                governor == null ? 0 : governor.taxesDue(),
                governor == null ? 0 : governor.taxesCollected(),
                governor == null ? 0L : governor.lastHeartbeatTick(),
                recommendationLabel(governor, true),
                recommendationLabel(governor, false),
                governor == null ? BannerModGovernorPolicy.DEFAULT_VALUE : governor.garrisonPriority(),
                governor == null ? BannerModGovernorPolicy.DEFAULT_VALUE : governor.fortificationPriority(),
                governor == null ? BannerModGovernorPolicy.DEFAULT_VALUE : governor.taxPressure(),
                governor == null ? 0 : governor.treasuryBalance(),
                governor == null ? 0 : governor.lastTreasuryNet(),
                governor == null ? 0 : governor.projectedTreasuryBalance(),
                governor == null ? List.of() : governor.incidentTokens(),
                recommendations,
                buildLogisticsLines(settlement),
                !stale && governor != null,
                stale ? "gui.bannermod.governor.action.disabled.stale" : governor == null
                        ? "gui.bannermod.governor.action.disabled.no_governor"
                        : "gui.bannermod.governor.action.enabled"
        );
    }

    private static List<String> buildLogisticsLines(@Nullable SettlementSnapshot settlement) {
        if (settlement == null) {
            return List.of("gui.bannermod.governor.logistics.none");
        }
        List<String> lines = new ArrayList<>();
        lines.add("gui.bannermod.governor.logistics.workers " + settlement.assignedWorkerCount() + " " + settlement.workplaceCapacity());
        lines.add("gui.bannermod.governor.logistics.residents " + settlement.assignedResidentCount() + " " + settlement.residentCapacity());
        lines.add("gui.bannermod.governor.logistics.blocked "
                + settlement.supplySignalState().shortageSignalCount() + " "
                + settlement.supplySignalState().shortageUnitCount());
        lines.add("gui.bannermod.governor.logistics.stockpile "
                + settlement.stockpileSummary().containerCount() + " "
                + settlement.stockpileSummary().slotCapacity());
        SettlementStrategicSignals signals = SettlementStrategicSignals.fromSnapshot(settlement);
        lines.add("gui.bannermod.governor.logistics.role " + signals.roleId());
        List<SettlementDesiredGoodSnapshot> desiredGoods = settlement.desiredGoodsSnapshot().desiredGoods();
        lines.add(desiredGoods.isEmpty()
                ? "gui.bannermod.governor.logistics.goods_none"
                : "gui.bannermod.governor.logistics.goods " + desiredGoods.get(0).desiredGoodId() + " " + desiredGoods.get(0).driverCount());
        lines.addAll(settlement.tradeRouteHandoffSnapshot().seaTradeStatusLines());
        return lines;
    }

    private static String recommendationLabel(@Nullable BannerModGovernorSnapshot snapshot, boolean garrison) {
        if (snapshot == null) {
            return BannerModGovernorRecommendation.HOLD_COURSE.token();
        }
        for (String token : snapshot.recommendationTokens()) {
            if (garrison && BannerModGovernorRecommendation.INCREASE_GARRISON.token().equals(token)) {
                return token;
            }
            if (!garrison && BannerModGovernorRecommendation.STRENGTHEN_FORTIFICATIONS.token().equals(token)) {
                return token;
            }
        }
        return BannerModGovernorRecommendation.HOLD_COURSE.token();
    }

    public record GovernorView(SnapshotState state,
                               String stateKey,
                               int stateColor,
                               String settlementKey,
                               int citizenCount,
                               int taxesDue,
                               int taxesCollected,
                               long lastHeartbeatTick,
                               String garrisonRecommendation,
                               String fortificationRecommendation,
                               int garrisonPriority,
                               int fortificationPriority,
                               int taxPressure,
                               int treasuryBalance,
                               int lastTreasuryNet,
                               int projectedTreasuryBalance,
                               List<String> incidents,
                               List<String> recommendations,
                               List<String> logisticsLines,
                               boolean canUpdatePolicy,
                               String actionReasonKey) {
        private static GovernorView loading() {
            return new GovernorView(SnapshotState.LOADING, "gui.bannermod.governor.state.loading", 0x555555,
                    "gui.bannermod.governor.settlement.loading", 0, 0, 0, 0L,
                    BannerModGovernorRecommendation.HOLD_COURSE.token(), BannerModGovernorRecommendation.HOLD_COURSE.token(),
                    BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE,
                    0, 0, 0, List.of(), List.of(), List.of("gui.bannermod.governor.logistics.loading"),
                    false, "gui.bannermod.governor.action.disabled.loading");
        }

        private static GovernorView empty(String stateKey) {
            return new GovernorView(SnapshotState.EMPTY, stateKey, 0x555555,
                    "gui.bannermod.governor.settlement.empty", 0, 0, 0, 0L,
                    BannerModGovernorRecommendation.HOLD_COURSE.token(), BannerModGovernorRecommendation.HOLD_COURSE.token(),
                    BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE,
                    0, 0, 0, List.of(), List.of(), List.of("gui.bannermod.governor.logistics.none"),
                    false, "gui.bannermod.governor.action.disabled.empty");
        }
    }
}
