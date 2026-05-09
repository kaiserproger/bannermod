package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.settlement.SettlementDesiredGoodSnapshot;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionRecord;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SettlementSeaTradeAnalyzer {
    private SettlementSeaTradeAnalyzer() {
    }

    public static List<SettlementDesiredGoodSnapshot> desiredGoods(BannerModSeaTradeSummary.Summary seaTradeSummary) {
        List<SettlementDesiredGoodSnapshot> desiredGoods = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : seaTradeSummary.importableByItem().entrySet()) {
            desiredGoods.add(new SettlementDesiredGoodSnapshot("sea_import:" + entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<ResourceLocation, Integer> entry : seaTradeSummary.exportableByItem().entrySet()) {
            desiredGoods.add(new SettlementDesiredGoodSnapshot("sea_export:" + entry.getKey(), entry.getValue()));
        }
        return desiredGoods;
    }

    public static int addCoverageUnits(String goodId,
                                       int coverageUnits,
                                       BannerModSeaTradeSummary.Summary seaTradeSummary) {
        if (goodId == null || goodId.isBlank()) {
            return coverageUnits;
        }
        if (goodId.startsWith("sea_import:")) {
            ResourceLocation itemId = ResourceLocation.tryParse(goodId.substring("sea_import:".length()));
            return coverageUnits + BannerModSeaTradeSummary.totalImportableCount(seaTradeSummary, itemId);
        }
        if (goodId.startsWith("sea_export:")) {
            ResourceLocation itemId = ResourceLocation.tryParse(goodId.substring("sea_export:".length()));
            return coverageUnits + BannerModSeaTradeSummary.totalExportableCount(seaTradeSummary, itemId);
        }
        return coverageUnits;
    }

    public static List<String> statusLines(BannerModSeaTradeSummary.Summary seaTradeSummary,
                                           List<BannerModSeaTradeExecutionRecord> executionRecords) {
        List<String> lines = new ArrayList<>();
        for (BannerModSeaTradeExecutionRecord record : executionRecords) {
            lines.add(executionStatusLine(record));
        }
        for (Map.Entry<ResourceLocation, Integer> entry : seaTradeSummary.importableByItem().entrySet()) {
            lines.add("Sea import benefit: " + entry.getKey() + " x" + entry.getValue());
        }
        for (Map.Entry<ResourceLocation, Integer> entry : seaTradeSummary.exportableByItem().entrySet()) {
            lines.add("Sea export benefit: " + entry.getKey() + " x" + entry.getValue());
        }
        for (String bottleneck : seaTradeSummary.bottlenecks()) {
            lines.add("Sea trade bottleneck: " + bottleneck.toLowerCase(Locale.ROOT));
        }
        return lines;
    }

    private static String executionStatusLine(BannerModSeaTradeExecutionRecord record) {
        String statusKey = switch (record.state()) {
            case LOADING -> "loading";
            case TRAVELLING -> "travelling";
            case UNLOADING -> "unloading";
            case COMPLETE -> "completed";
            case FAILED -> BannerModSeaTradeExecutionRecord.FAILURE_NO_CARRIER.equals(record.failureReason())
                    ? "missing_ship"
                    : "blocked_cargo";
        };
        return "gui.bannermod.governor.logistics.sea_trade." + statusKey + " "
                + shortRouteId(record.routeId()) + " "
                + carrierLabel(record.boundCarrierId()) + " "
                + failureReasonKey(record.failureReason()) + " "
                + filterLabel(record) + " "
                + record.cargoCount() + " "
                + record.requestedCount();
    }

    private static String carrierLabel(@Nullable UUID carrierId) {
        return carrierId == null ? "unassigned" : shortRouteId(carrierId);
    }

    private static String failureReasonKey(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "gui.bannermod.governor.logistics.sea_trade.reason.none";
        }
        return switch (failureReason) {
            case BannerModSeaTradeExecutionRecord.FAILURE_NO_CARRIER -> "gui.bannermod.governor.logistics.sea_trade.reason.no_carrier";
            case BannerModSeaTradeExecutionRecord.FAILURE_NO_CARGO_LOADED -> "gui.bannermod.governor.logistics.sea_trade.reason.no_cargo_loaded";
            case BannerModSeaTradeExecutionRecord.FAILURE_SOURCE_SHORTAGE -> "gui.bannermod.governor.logistics.sea_trade.reason.source_shortage";
            case BannerModSeaTradeExecutionRecord.FAILURE_DESTINATION_FULL -> "gui.bannermod.governor.logistics.sea_trade.reason.destination_full";
            default -> "gui.bannermod.governor.logistics.sea_trade.reason.carrier_failed";
        };
    }

    private static String shortRouteId(UUID routeId) {
        String value = routeId.toString().replace("-", "");
        return value.substring(Math.max(0, value.length() - 4));
    }

    private static String filterLabel(BannerModSeaTradeExecutionRecord record) {
        return record.filter().itemIds().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(ResourceLocation::toString)
                .findFirst()
                .orElse("any");
    }
}
