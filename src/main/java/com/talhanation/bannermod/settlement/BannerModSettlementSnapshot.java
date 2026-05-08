package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BannerModSettlementSnapshot(
        UUID claimUuid,
        int anchorChunkX,
        int anchorChunkZ,
        @Nullable String settlementFactionId,
        long lastRefreshedTick,
        int residentCapacity,
        int workplaceCapacity,
        int assignedWorkerCount,
        int assignedResidentCount,
        int unassignedWorkerCount,
        int missingWorkAreaAssignmentCount,
        BannerModSettlementStockpileSummary stockpileSummary,
        BannerModSettlementMarketState marketState,
        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
        BannerModSettlementProjectCandidateSnapshot projectCandidateSnapshot,
        BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
        BannerModSettlementSupplySignalState supplySignalState,
        List<BannerModSettlementResidentRecord> residents,
        List<BannerModSettlementBuildingRecord> buildings
) {
    public BannerModSettlementSnapshot {
        residentCapacity = Math.max(0, residentCapacity);
        workplaceCapacity = Math.max(0, workplaceCapacity);
        assignedWorkerCount = Math.max(0, assignedWorkerCount);
        assignedResidentCount = Math.max(0, assignedResidentCount);
        unassignedWorkerCount = Math.max(0, unassignedWorkerCount);
        missingWorkAreaAssignmentCount = Math.max(0, missingWorkAreaAssignmentCount);
        stockpileSummary = stockpileSummary == null ? BannerModSettlementStockpileSummary.empty() : stockpileSummary;
        marketState = marketState == null ? BannerModSettlementMarketState.empty() : marketState;
        desiredGoodsSnapshot = desiredGoodsSnapshot == null ? BannerModSettlementDesiredGoodsSnapshot.empty() : desiredGoodsSnapshot;
        projectCandidateSnapshot = projectCandidateSnapshot == null ? BannerModSettlementProjectCandidateSnapshot.empty() : projectCandidateSnapshot;
        tradeRouteHandoffSnapshot = tradeRouteHandoffSnapshot == null ? BannerModSettlementTradeRouteHandoffSnapshot.empty() : tradeRouteHandoffSnapshot;
        supplySignalState = supplySignalState == null ? BannerModSettlementSupplySignalState.empty() : supplySignalState;
        residents = List.copyOf(residents == null ? List.of() : residents);
        buildings = List.copyOf(buildings == null ? List.of() : buildings);
    }

    public ChunkPos anchorChunk() {
        return new ChunkPos(this.anchorChunkX, this.anchorChunkZ);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ClaimUuid", this.claimUuid);
        tag.putInt("AnchorChunkX", this.anchorChunkX);
        tag.putInt("AnchorChunkZ", this.anchorChunkZ);
        if (this.settlementFactionId != null && !this.settlementFactionId.isBlank()) {
            tag.putString("SettlementFactionId", this.settlementFactionId);
        }
        tag.putLong("LastRefreshedTick", this.lastRefreshedTick);
        tag.putInt("ResidentCapacity", this.residentCapacity);
        tag.putInt("WorkplaceCapacity", this.workplaceCapacity);
        tag.putInt("AssignedWorkerCount", this.assignedWorkerCount);
        tag.putInt("AssignedResidentCount", this.assignedResidentCount);
        tag.putInt("UnassignedWorkerCount", this.unassignedWorkerCount);
        tag.putInt("MissingWorkAreaAssignmentCount", this.missingWorkAreaAssignmentCount);
        tag.put("StockpileSummary", this.stockpileSummary.toTag());
        tag.put("MarketState", this.marketState.toTag());
        tag.put("DesiredGoodsSeed", this.desiredGoodsSnapshot.toTag());
        tag.put("ProjectCandidateSeed", this.projectCandidateSnapshot.toTag());
        tag.put("TradeRouteHandoffSeed", this.tradeRouteHandoffSnapshot.toTag());
        tag.put("SupplySignalState", this.supplySignalState.toTag());
        ListTag residentList = new ListTag();
        for (BannerModSettlementResidentRecord resident : this.residents) {
            residentList.add(resident.toTag());
        }
        tag.put("Residents", residentList);
        ListTag buildingList = new ListTag();
        for (BannerModSettlementBuildingRecord building : this.buildings) {
            buildingList.add(building.toTag());
        }
        tag.put("Buildings", buildingList);
        return tag;
    }

    public static BannerModSettlementSnapshot fromTag(CompoundTag tag) {
        String settlementFactionId = tag.contains("SettlementFactionId", Tag.TAG_STRING) ? tag.getString("SettlementFactionId") : null;
        return new BannerModSettlementSnapshot(
                tag.getUUID("ClaimUuid"),
                tag.getInt("AnchorChunkX"),
                tag.getInt("AnchorChunkZ"),
                settlementFactionId,
                tag.getLong("LastRefreshedTick"),
                tag.getInt("ResidentCapacity"),
                tag.getInt("WorkplaceCapacity"),
                tag.getInt("AssignedWorkerCount"),
                tag.getInt("AssignedResidentCount"),
                tag.getInt("UnassignedWorkerCount"),
                tag.getInt("MissingWorkAreaAssignmentCount"),
                tag.contains("StockpileSummary", Tag.TAG_COMPOUND)
                        ? BannerModSettlementStockpileSummary.fromTag(tag.getCompound("StockpileSummary"))
                        : BannerModSettlementStockpileSummary.empty(),
                tag.contains("MarketState", Tag.TAG_COMPOUND)
                        ? BannerModSettlementMarketState.fromTag(tag.getCompound("MarketState"))
                        : BannerModSettlementMarketState.empty(),
                tag.contains("DesiredGoodsSeed", Tag.TAG_COMPOUND)
                        ? BannerModSettlementDesiredGoodsSnapshot.fromTag(tag.getCompound("DesiredGoodsSeed"))
                        : BannerModSettlementDesiredGoodsSnapshot.empty(),
                tag.contains("ProjectCandidateSeed", Tag.TAG_COMPOUND)
                        ? BannerModSettlementProjectCandidateSnapshot.fromTag(tag.getCompound("ProjectCandidateSeed"))
                        : BannerModSettlementProjectCandidateSnapshot.empty(),
                tag.contains("TradeRouteHandoffSeed", Tag.TAG_COMPOUND)
                        ? BannerModSettlementTradeRouteHandoffSnapshot.fromTag(tag.getCompound("TradeRouteHandoffSeed"))
                        : BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                tag.contains("SupplySignalState", Tag.TAG_COMPOUND)
                        ? BannerModSettlementSupplySignalState.fromTag(tag.getCompound("SupplySignalState"))
                        : BannerModSettlementSupplySignalState.empty(),
                readResidents(tag.getList("Residents", Tag.TAG_COMPOUND)),
                readBuildings(tag.getList("Buildings", Tag.TAG_COMPOUND))
        );
    }

    public static BannerModSettlementSnapshot create(UUID claimUuid, ChunkPos anchorChunk, @Nullable String settlementFactionId) {
        return new BannerModSettlementSnapshot(claimUuid, anchorChunk.x, anchorChunk.z, settlementFactionId, 0L, 0, 0, 0, 0, 0, 0, BannerModSettlementStockpileSummary.empty(), BannerModSettlementMarketState.empty(), BannerModSettlementDesiredGoodsSnapshot.empty(), BannerModSettlementProjectCandidateSnapshot.empty(), BannerModSettlementTradeRouteHandoffSnapshot.empty(), BannerModSettlementSupplySignalState.empty(), List.of(), List.of());
    }

    private static List<BannerModSettlementResidentRecord> readResidents(ListTag list) {
        List<BannerModSettlementResidentRecord> residents = new ArrayList<>();
        for (Tag entry : list) {
            residents.add(BannerModSettlementResidentRecord.fromTag((CompoundTag) entry));
        }
        return residents;
    }

    private static List<BannerModSettlementBuildingRecord> readBuildings(ListTag list) {
        List<BannerModSettlementBuildingRecord> buildings = new ArrayList<>();
        for (Tag entry : list) {
            buildings.add(BannerModSettlementBuildingRecord.fromTag((CompoundTag) entry));
        }
        return buildings;
    }
}
