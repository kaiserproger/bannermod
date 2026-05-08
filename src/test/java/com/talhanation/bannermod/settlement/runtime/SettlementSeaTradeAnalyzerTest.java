package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodSeed;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsItemFilter;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionRecord;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionState;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementSeaTradeAnalyzerTest {
    @Test
    void statusLinesExposeExecutionProgressAndFailures() {
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000003101");
        UUID destinationId = UUID.fromString("00000000-0000-0000-0000-000000003102");
        BannerModLogisticsItemFilter wheat = BannerModLogisticsItemFilter.ofItemIds(List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat")));

        List<String> lines = SettlementSeaTradeAnalyzer.statusLines(
                BannerModSeaTradeSummary.summarise(List.of()),
                List.of(
                        seaTradeRecord("00000000-0000-0000-0000-000000003201", sourceId, destinationId, wheat, 16, 0, BannerModSeaTradeExecutionState.LOADING, ""),
                        seaTradeRecord("00000000-0000-0000-0000-000000003202", sourceId, destinationId, wheat, 16, 8, BannerModSeaTradeExecutionState.TRAVELLING, ""),
                        seaTradeRecord("00000000-0000-0000-0000-000000003203", sourceId, destinationId, wheat, 16, 8, BannerModSeaTradeExecutionState.UNLOADING, ""),
                        seaTradeRecord("00000000-0000-0000-0000-000000003204", sourceId, destinationId, wheat, 16, 0, BannerModSeaTradeExecutionState.COMPLETE, ""),
                        seaTradeRecord("00000000-0000-0000-0000-000000003205", sourceId, destinationId, wheat, 16, 0, BannerModSeaTradeExecutionState.FAILED, BannerModSeaTradeExecutionRecord.FAILURE_NO_CARRIER),
                        seaTradeRecord("00000000-0000-0000-0000-000000003206", sourceId, destinationId, wheat, 16, 4, BannerModSeaTradeExecutionState.FAILED, BannerModSeaTradeExecutionRecord.FAILURE_DESTINATION_FULL)
                )
        );

        assertEquals(List.of(
                "gui.bannermod.governor.logistics.sea_trade.loading 3201 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 0 16",
                "gui.bannermod.governor.logistics.sea_trade.travelling 3202 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 8 16",
                "gui.bannermod.governor.logistics.sea_trade.unloading 3203 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 8 16",
                "gui.bannermod.governor.logistics.sea_trade.completed 3204 2201 gui.bannermod.governor.logistics.sea_trade.reason.none minecraft:wheat 0 16",
                "gui.bannermod.governor.logistics.sea_trade.missing_ship 3205 unassigned gui.bannermod.governor.logistics.sea_trade.reason.no_carrier minecraft:wheat 0 16",
                "gui.bannermod.governor.logistics.sea_trade.blocked_cargo 3206 2201 gui.bannermod.governor.logistics.sea_trade.reason.destination_full minecraft:wheat 4 16"
        ), lines.subList(0, 6));
    }

    @Test
    void statusLocalizationCoversSuccessfulAndBlockedRoutes() throws Exception {
        String enUs = Files.readString(Path.of("src/main/resources/assets/bannermod/lang/en_us.json"));
        String ruRu = Files.readString(Path.of("src/main/resources/assets/bannermod/lang/ru_ru.json"));

        for (String lang : List.of(enUs, ruRu)) {
            assertTrue(lang.contains("gui.bannermod.governor.logistics.sea_trade.completed"));
            assertTrue(lang.contains("gui.bannermod.governor.logistics.sea_trade.blocked_cargo"));
            assertTrue(lang.contains("gui.bannermod.governor.logistics.sea_trade.reason.destination_full"));
        }
    }

    @Test
    void desiredGoodsIncludeSeaTradeImportAndExportDrivers() {
        BannerModSeaTradeSummary.Summary seaTradeSummary = new BannerModSeaTradeSummary.Summary(
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), 4),
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 2),
                List.of()
        );

        assertEquals(List.of(
                new BannerModSettlementDesiredGoodSeed("sea_import:minecraft:iron_ingot", 2),
                new BannerModSettlementDesiredGoodSeed("sea_export:minecraft:wheat", 4)
        ), SettlementSeaTradeAnalyzer.desiredGoods(seaTradeSummary));
    }

    @Test
    void coverageCountsSeaTradeImportAndExportCapacity() {
        BannerModSeaTradeSummary.Summary seaTradeSummary = new BannerModSeaTradeSummary.Summary(
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), 5),
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 4),
                List.of()
        );

        assertEquals(4, SettlementSeaTradeAnalyzer.addCoverageUnits("sea_import:minecraft:iron_ingot", 0, seaTradeSummary));
        assertEquals(5, SettlementSeaTradeAnalyzer.addCoverageUnits("sea_export:minecraft:wheat", 0, seaTradeSummary));
        assertEquals(2, SettlementSeaTradeAnalyzer.addCoverageUnits("market_goods", 2, seaTradeSummary));
    }

    @Test
    void statusLinesIncludeBenefitsBottlenecksAndFallbackLabels() {
        BannerModSeaTradeSummary.Summary seaTradeSummary = new BannerModSeaTradeSummary.Summary(
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), 5),
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 3),
                List.of(BannerModSeaTradeSummary.BOTTLENECK_ONLY_EXPORTS, BannerModSeaTradeSummary.BOTTLENECK_UNFILTERED_ROUTE)
        );

        List<String> lines = SettlementSeaTradeAnalyzer.statusLines(
                seaTradeSummary,
                List.of(seaTradeRecord(
                        "00000000-0000-0000-0000-000000003207",
                        UUID.fromString("00000000-0000-0000-0000-000000003101"),
                        UUID.fromString("00000000-0000-0000-0000-000000003102"),
                        BannerModLogisticsItemFilter.any(),
                        6,
                        1,
                        BannerModSeaTradeExecutionState.FAILED,
                        "unexpected_failure"
                ))
        );

        assertEquals(
                "gui.bannermod.governor.logistics.sea_trade.blocked_cargo 3207 2201 gui.bannermod.governor.logistics.sea_trade.reason.carrier_failed any 1 6",
                lines.get(0)
        );
        assertTrue(lines.contains("Sea import benefit: minecraft:iron_ingot x3"));
        assertTrue(lines.contains("Sea export benefit: minecraft:wheat x5"));
        assertTrue(lines.contains("Sea trade bottleneck: only_exports"));
        assertTrue(lines.contains("Sea trade bottleneck: unfiltered_route"));
    }

    private static BannerModSeaTradeExecutionRecord seaTradeRecord(String routeId,
                                                                   UUID sourceId,
                                                                   UUID destinationId,
                                                                   BannerModLogisticsItemFilter filter,
                                                                   int requestedCount,
                                                                   int cargoCount,
                                                                   BannerModSeaTradeExecutionState state,
                                                                   String failureReason) {
        return new BannerModSeaTradeExecutionRecord(
                UUID.fromString(routeId),
                BannerModSeaTradeExecutionRecord.FAILURE_NO_CARRIER.equals(failureReason)
                        ? null
                        : UUID.fromString("00000000-0000-0000-0000-000000002201"),
                sourceId,
                destinationId,
                filter,
                requestedCount,
                cargoCount,
                state,
                failureReason
        );
    }
}
