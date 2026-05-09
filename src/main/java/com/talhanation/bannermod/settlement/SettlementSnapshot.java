package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SettlementSnapshot(
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
        SettlementStockpileSummary stockpileSummary,
        SettlementMarketState marketState,
        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
        SettlementProjectCandidateSnapshot projectCandidateSnapshot,
        SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
        SettlementSupplySignalState supplySignalState,
        List<SettlementResidentRecord> residents,
        List<SettlementBuildingRecord> buildings
) {
    public SettlementSnapshot {
        residentCapacity = Math.max(0, residentCapacity);
        workplaceCapacity = Math.max(0, workplaceCapacity);
        assignedWorkerCount = Math.max(0, assignedWorkerCount);
        assignedResidentCount = Math.max(0, assignedResidentCount);
        unassignedWorkerCount = Math.max(0, unassignedWorkerCount);
        missingWorkAreaAssignmentCount = Math.max(0, missingWorkAreaAssignmentCount);
        stockpileSummary = stockpileSummary == null ? SettlementStockpileSummary.empty() : stockpileSummary;
        marketState = marketState == null ? SettlementMarketState.empty() : marketState;
        desiredGoodsSnapshot = desiredGoodsSnapshot == null ? SettlementDesiredGoodsSnapshot.empty() : desiredGoodsSnapshot;
        projectCandidateSnapshot = projectCandidateSnapshot == null ? SettlementProjectCandidateSnapshot.empty() : projectCandidateSnapshot;
        tradeRouteHandoffSnapshot = tradeRouteHandoffSnapshot == null ? SettlementTradeRouteHandoffSnapshot.empty() : tradeRouteHandoffSnapshot;
        supplySignalState = supplySignalState == null ? SettlementSupplySignalState.empty() : supplySignalState;
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
        for (SettlementResidentRecord resident : this.residents) {
            residentList.add(resident.toTag());
        }
        tag.put("Residents", residentList);
        ListTag buildingList = new ListTag();
        for (SettlementBuildingRecord building : this.buildings) {
            buildingList.add(building.toTag());
        }
        tag.put("Buildings", buildingList);
        return tag;
    }

    public static SettlementSnapshot fromTag(CompoundTag tag) {
        String settlementFactionId = tag.contains("SettlementFactionId", Tag.TAG_STRING) ? tag.getString("SettlementFactionId") : null;
        return new SettlementSnapshot(
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
                        ? SettlementStockpileSummary.fromTag(tag.getCompound("StockpileSummary"))
                        : SettlementStockpileSummary.empty(),
                tag.contains("MarketState", Tag.TAG_COMPOUND)
                        ? SettlementMarketState.fromTag(tag.getCompound("MarketState"))
                        : SettlementMarketState.empty(),
                tag.contains("DesiredGoodsSeed", Tag.TAG_COMPOUND)
                        ? SettlementDesiredGoodsSnapshot.fromTag(tag.getCompound("DesiredGoodsSeed"))
                        : SettlementDesiredGoodsSnapshot.empty(),
                tag.contains("ProjectCandidateSeed", Tag.TAG_COMPOUND)
                        ? SettlementProjectCandidateSnapshot.fromTag(tag.getCompound("ProjectCandidateSeed"))
                        : SettlementProjectCandidateSnapshot.empty(),
                tag.contains("TradeRouteHandoffSeed", Tag.TAG_COMPOUND)
                        ? SettlementTradeRouteHandoffSnapshot.fromTag(tag.getCompound("TradeRouteHandoffSeed"))
                        : SettlementTradeRouteHandoffSnapshot.empty(),
                tag.contains("SupplySignalState", Tag.TAG_COMPOUND)
                        ? SettlementSupplySignalState.fromTag(tag.getCompound("SupplySignalState"))
                        : SettlementSupplySignalState.empty(),
                readResidents(tag.getList("Residents", Tag.TAG_COMPOUND)),
                readBuildings(tag.getList("Buildings", Tag.TAG_COMPOUND))
        );
    }

    public static SettlementSnapshot create(UUID claimUuid, ChunkPos anchorChunk, @Nullable String settlementFactionId) {
        return new SettlementSnapshot(claimUuid, anchorChunk.x, anchorChunk.z, settlementFactionId, 0L, 0, 0, 0, 0, 0, 0, SettlementStockpileSummary.empty(), SettlementMarketState.empty(), SettlementDesiredGoodsSnapshot.empty(), SettlementProjectCandidateSnapshot.empty(), SettlementTradeRouteHandoffSnapshot.empty(), SettlementSupplySignalState.empty(), List.of(), List.of());
    }

    private static List<SettlementResidentRecord> readResidents(ListTag list) {
        List<SettlementResidentRecord> residents = new ArrayList<>();
        for (Tag entry : list) {
            residents.add(SettlementResidentRecord.fromTag((CompoundTag) entry));
        }
        return residents;
    }

    private static List<SettlementBuildingRecord> readBuildings(ListTag list) {
        List<SettlementBuildingRecord> buildings = new ArrayList<>();
        for (Tag entry : list) {
            buildings.add(SettlementBuildingRecord.fromTag((CompoundTag) entry));
        }
        return buildings;
    }
}
