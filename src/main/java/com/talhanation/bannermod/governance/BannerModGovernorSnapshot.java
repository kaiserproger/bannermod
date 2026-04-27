package com.talhanation.bannermod.governance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record BannerModGovernorSnapshot(
        UUID claimUuid,
        int anchorChunkX,
        int anchorChunkZ,
        @Nullable String settlementFactionId,
        @Nullable UUID governorRecruitUuid,
        @Nullable UUID governorOwnerUuid,
        long lastHeartbeatTick,
        long lastCollectionTick,
        int citizenCount,
        int taxesDue,
        int taxesCollected,
        int treasuryBalance,
        int lastTreasuryNet,
        int projectedTreasuryBalance,
        int garrisonPriority,
        int fortificationPriority,
        int taxPressure,
        boolean autoManage,
        int maxContractReward,
        List<String> incidentTokens,
        List<String> recommendationTokens
) {
    public BannerModGovernorSnapshot {
        incidentTokens = copyTokens(incidentTokens);
        recommendationTokens = copyTokens(recommendationTokens);
    }

    public ChunkPos anchorChunk() {
        return new ChunkPos(this.anchorChunkX, this.anchorChunkZ);
    }

    public boolean hasGovernor() {
        return this.governorRecruitUuid != null && this.governorOwnerUuid != null;
    }

    public BannerModGovernorSnapshot withGovernor(@Nullable UUID governorRecruitUuid, @Nullable UUID governorOwnerUuid) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunkX, anchorChunkZ, settlementFactionId,
                governorRecruitUuid, governorOwnerUuid,
                lastHeartbeatTick, lastCollectionTick, citizenCount, taxesDue, taxesCollected,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                garrisonPriority, fortificationPriority, taxPressure,
                autoManage, maxContractReward, incidentTokens, recommendationTokens
        );
    }

    public BannerModGovernorSnapshot withSettlementFactionId(@Nullable String settlementFactionId) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunkX, anchorChunkZ, settlementFactionId,
                governorRecruitUuid, governorOwnerUuid,
                lastHeartbeatTick, lastCollectionTick, citizenCount, taxesDue, taxesCollected,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                garrisonPriority, fortificationPriority, taxPressure,
                autoManage, maxContractReward, incidentTokens, recommendationTokens
        );
    }

    public BannerModGovernorSnapshot withPolicies(int garrisonPriority, int fortificationPriority, int taxPressure) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunkX, anchorChunkZ, settlementFactionId,
                governorRecruitUuid, governorOwnerUuid,
                lastHeartbeatTick, lastCollectionTick, citizenCount, taxesDue, taxesCollected,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                BannerModGovernorPolicy.GARRISON_PRIORITY.clamp(garrisonPriority),
                BannerModGovernorPolicy.FORTIFICATION_PRIORITY.clamp(fortificationPriority),
                BannerModGovernorPolicy.TAX_PRESSURE.clamp(taxPressure),
                autoManage, maxContractReward, incidentTokens, recommendationTokens
        );
    }

    public BannerModGovernorSnapshot withAutoManage(boolean autoManage) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunkX, anchorChunkZ, settlementFactionId,
                governorRecruitUuid, governorOwnerUuid,
                lastHeartbeatTick, lastCollectionTick, citizenCount, taxesDue, taxesCollected,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                garrisonPriority, fortificationPriority, taxPressure,
                autoManage, maxContractReward, incidentTokens, recommendationTokens
        );
    }

    public BannerModGovernorSnapshot withMaxContractReward(int maxContractReward) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunkX, anchorChunkZ, settlementFactionId,
                governorRecruitUuid, governorOwnerUuid,
                lastHeartbeatTick, lastCollectionTick, citizenCount, taxesDue, taxesCollected,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                garrisonPriority, fortificationPriority, taxPressure,
                autoManage, Math.max(0, maxContractReward), incidentTokens, recommendationTokens
        );
    }

    public BannerModGovernorSnapshot withHeartbeatReport(long heartbeatTick, long collectionTick, int citizenCount,
                                                         int taxesDue, int taxesCollected,
                                                         List<String> incidentTokens,
                                                         List<String> recommendationTokens) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunkX, anchorChunkZ, settlementFactionId,
                governorRecruitUuid, governorOwnerUuid,
                heartbeatTick, collectionTick, citizenCount, taxesDue, taxesCollected,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                garrisonPriority, fortificationPriority, taxPressure,
                autoManage, maxContractReward, incidentTokens, recommendationTokens
        );
    }

    public BannerModGovernorSnapshot withFiscalRollup(@Nullable BannerModTreasuryLedgerSnapshot.FiscalRollup fiscalRollup) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunkX, anchorChunkZ, settlementFactionId,
                governorRecruitUuid, governorOwnerUuid,
                lastHeartbeatTick, lastCollectionTick, citizenCount, taxesDue, taxesCollected,
                fiscalRollup == null ? 0 : fiscalRollup.treasuryBalance(),
                fiscalRollup == null ? 0 : fiscalRollup.lastNetChange(),
                fiscalRollup == null ? 0 : fiscalRollup.projectedNextBalance(),
                garrisonPriority, fortificationPriority, taxPressure,
                autoManage, maxContractReward, incidentTokens, recommendationTokens
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ClaimUuid", claimUuid);
        tag.putInt("AnchorChunkX", anchorChunkX);
        tag.putInt("AnchorChunkZ", anchorChunkZ);
        if (settlementFactionId != null && !settlementFactionId.isBlank()) {
            tag.putString("SettlementFactionId", settlementFactionId);
        }
        if (governorRecruitUuid != null) tag.putUUID("GovernorRecruitUuid", governorRecruitUuid);
        if (governorOwnerUuid != null) tag.putUUID("GovernorOwnerUuid", governorOwnerUuid);
        tag.putLong("LastHeartbeatTick", lastHeartbeatTick);
        tag.putLong("LastCollectionTick", lastCollectionTick);
        tag.putInt("CitizenCount", citizenCount);
        tag.putInt("TaxesDue", taxesDue);
        tag.putInt("TaxesCollected", taxesCollected);
        tag.putInt("TreasuryBalance", treasuryBalance);
        tag.putInt("LastTreasuryNet", lastTreasuryNet);
        tag.putInt("ProjectedTreasuryBalance", projectedTreasuryBalance);
        tag.putInt("GarrisonPriority", garrisonPriority);
        tag.putInt("FortificationPriority", fortificationPriority);
        tag.putInt("TaxPressure", taxPressure);
        tag.putBoolean("AutoManage", autoManage);
        tag.putInt("MaxContractReward", maxContractReward);
        tag.put("IncidentTokens", writeTokens(incidentTokens));
        tag.put("RecommendationTokens", writeTokens(recommendationTokens));
        return tag;
    }

    public static BannerModGovernorSnapshot create(UUID claimUuid, ChunkPos anchorChunk, @Nullable String settlementFactionId) {
        return new BannerModGovernorSnapshot(
                claimUuid, anchorChunk.x, anchorChunk.z, settlementFactionId,
                null, null, 0L, 0L, 0, 0, 0, 0, 0, 0,
                BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE,
                false, 0, List.of(), List.of()
        );
    }

    public static BannerModGovernorSnapshot fromTag(CompoundTag tag) {
        UUID claimUuid = tag.getUUID("ClaimUuid");
        String settlementFactionId = tag.contains("SettlementFactionId", Tag.TAG_STRING) ? tag.getString("SettlementFactionId") : null;
        UUID governorRecruitUuid = tag.hasUUID("GovernorRecruitUuid") ? tag.getUUID("GovernorRecruitUuid") : null;
        UUID governorOwnerUuid = tag.hasUUID("GovernorOwnerUuid") ? tag.getUUID("GovernorOwnerUuid") : null;
        return new BannerModGovernorSnapshot(
                claimUuid,
                tag.getInt("AnchorChunkX"),
                tag.getInt("AnchorChunkZ"),
                settlementFactionId,
                governorRecruitUuid,
                governorOwnerUuid,
                tag.getLong("LastHeartbeatTick"),
                tag.getLong("LastCollectionTick"),
                tag.getInt("CitizenCount"),
                tag.getInt("TaxesDue"),
                tag.getInt("TaxesCollected"),
                tag.contains("TreasuryBalance", Tag.TAG_INT) ? tag.getInt("TreasuryBalance") : 0,
                tag.contains("LastTreasuryNet", Tag.TAG_INT) ? tag.getInt("LastTreasuryNet") : 0,
                tag.contains("ProjectedTreasuryBalance", Tag.TAG_INT) ? tag.getInt("ProjectedTreasuryBalance") : 0,
                tag.contains("GarrisonPriority", Tag.TAG_INT) ? tag.getInt("GarrisonPriority") : BannerModGovernorPolicy.DEFAULT_VALUE,
                tag.contains("FortificationPriority", Tag.TAG_INT) ? tag.getInt("FortificationPriority") : BannerModGovernorPolicy.DEFAULT_VALUE,
                tag.contains("TaxPressure", Tag.TAG_INT) ? tag.getInt("TaxPressure") : BannerModGovernorPolicy.DEFAULT_VALUE,
                tag.contains("AutoManage", Tag.TAG_BYTE) && tag.getBoolean("AutoManage"),
                tag.contains("MaxContractReward", Tag.TAG_INT) ? tag.getInt("MaxContractReward") : 0,
                readTokens(tag.getList("IncidentTokens", Tag.TAG_STRING)),
                readTokens(tag.getList("RecommendationTokens", Tag.TAG_STRING))
        );
    }

    private static ListTag writeTokens(List<String> tokens) {
        ListTag list = new ListTag();
        for (String token : copyTokens(tokens)) list.add(StringTag.valueOf(token));
        return list;
    }

    private static List<String> readTokens(ListTag list) {
        List<String> tokens = new ArrayList<>();
        for (Tag tag : list) tokens.add(tag.getAsString());
        return Collections.unmodifiableList(tokens);
    }

    private static List<String> copyTokens(List<String> tokens) {
        List<String> copied = new ArrayList<>();
        if (tokens != null) {
            for (String token : tokens) {
                if (token != null && !token.isBlank()) copied.add(token);
            }
        }
        return Collections.unmodifiableList(copied);
    }
}
