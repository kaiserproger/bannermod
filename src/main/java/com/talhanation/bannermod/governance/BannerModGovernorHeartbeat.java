package com.talhanation.bannermod.governance;

import com.talhanation.bannermod.shared.logistics.BannerModSupplyStatus;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.WorkerIndex;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.war.runtime.WarSiegeQueries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

public final class BannerModGovernorHeartbeat {
    private static final int TAX_PER_CITIZEN = 2;

    private BannerModGovernorHeartbeat() {
    }

    public static HeartbeatReport evaluate(HeartbeatInput input) {
        EnumSet<BannerModGovernorIncident> incidents = EnumSet.noneOf(BannerModGovernorIncident.class);
        EnumSet<BannerModGovernorRecommendation> recommendations = EnumSet.noneOf(BannerModGovernorRecommendation.class);

        int citizens = Math.max(0, input.villagerCount()) + Math.max(0, input.workerCount());
        int taxesDue = 0;
        int taxesCollected = 0;

        switch (input.settlementStatus()) {
            case HOSTILE_CLAIM -> incidents.add(BannerModGovernorIncident.HOSTILE_CLAIM);
            case DEGRADED_MISMATCH -> incidents.add(BannerModGovernorIncident.DEGRADED_SETTLEMENT);
            case UNCLAIMED -> incidents.add(BannerModGovernorIncident.UNCLAIMED_SETTLEMENT);
            case FRIENDLY_CLAIM -> {
                taxesDue = citizens * TAX_PER_CITIZEN;
                taxesCollected = taxesDue;
            }
        }

        if (input.underSiege()) {
            incidents.add(BannerModGovernorIncident.UNDER_SIEGE);
            taxesCollected = 0;
        }

        if (input.settlementStatus() != BannerModSettlementBinding.Status.FRIENDLY_CLAIM) {
            taxesCollected = 0;
        }

        if (input.workerCount() <= 0) {
            incidents.add(BannerModGovernorIncident.WORKER_SHORTAGE);
        }

        if (input.recruitCount() < Math.max(1, citizens / 2)) {
            recommendations.add(BannerModGovernorRecommendation.INCREASE_GARRISON);
            recommendations.add(BannerModGovernorRecommendation.STRENGTHEN_FORTIFICATIONS);
        }

        if (input.workerSupplyStatus() != null && input.workerSupplyStatus().blocked()) {
            incidents.add(BannerModGovernorIncident.SUPPLY_BLOCKED);
            recommendations.add(BannerModGovernorRecommendation.RELIEVE_SUPPLY_PRESSURE);
        }

        if (input.recruitSupplyStatus() != null && input.recruitSupplyStatus().blocked()) {
            incidents.add(BannerModGovernorIncident.RECRUIT_UPKEEP_BLOCKED);
            recommendations.add(BannerModGovernorRecommendation.RELIEVE_SUPPLY_PRESSURE);
        }

        if (recommendations.isEmpty()) {
            recommendations.add(BannerModGovernorRecommendation.HOLD_COURSE);
        }

        return new HeartbeatReport(
                citizens,
                taxesDue,
                taxesCollected,
                List.copyOf(incidents),
                List.copyOf(recommendations),
                input.gameTime(),
                taxesCollected > 0 ? input.gameTime() : input.snapshot().lastCollectionTick()
        );
    }

    public static List<String> incidentTokens(List<BannerModGovernorIncident> incidents) {
        List<String> tokens = new ArrayList<>();
        for (BannerModGovernorIncident incident : incidents) {
            tokens.add(incident.token());
        }
        return tokens;
    }

    public static List<String> recommendationTokens(List<BannerModGovernorRecommendation> recommendations) {
        List<String> tokens = new ArrayList<>();
        for (BannerModGovernorRecommendation recommendation : recommendations) {
            tokens.add(recommendation.token());
        }
        return tokens;
    }

    public static void runGovernedClaimHeartbeat(ServerLevel level, RecruitsClaimManager claimManager, BannerModGovernorManager governorManager) {
        if (level == null) {
            return;
        }
        runGovernedClaimHeartbeat(level, claimManager, governorManager, BannerModTreasuryManager.get(level));
    }

    public static void runGovernedClaimHeartbeat(ServerLevel level,
                                                 RecruitsClaimManager claimManager,
                                                 BannerModGovernorManager governorManager,
                                                 @Nullable BannerModTreasuryManager treasuryManager) {
        runGovernedClaimHeartbeatBatch(level, claimManager, governorManager, treasuryManager, null, 0, Integer.MAX_VALUE);
    }

    public static BatchResult runGovernedClaimHeartbeatBatch(ServerLevel level,
                                                             RecruitsClaimManager claimManager,
                                                             BannerModGovernorManager governorManager,
                                                             @Nullable BannerModTreasuryManager treasuryManager,
                                                             @Nullable BannerModContractManager contractManager,
                                                             int startIndex,
                                                             int maxSnapshots) {
        if (level == null || claimManager == null || governorManager == null) {
            return BatchResult.completedResult();
        }
        long startNanos = System.nanoTime();

        Set<UUID> activeClaimUuids = new HashSet<>();
        for (RecruitsClaim claim : claimManager.getAllClaims()) {
            if (claim != null) {
                activeClaimUuids.add(claim.getUUID());
            }
        }

        List<UUID> staleSnapshots = new ArrayList<>();
        for (BannerModGovernorSnapshot snapshot : governorManager.getAllSnapshots()) {
            if (snapshot != null && !activeClaimUuids.contains(snapshot.claimUuid())) {
                staleSnapshots.add(snapshot.claimUuid());
            }
        }
        for (UUID claimUuid : staleSnapshots) {
            governorManager.removeSnapshot(claimUuid);
            if (treasuryManager != null) {
                treasuryManager.removeLedger(claimUuid);
            }
        }

        List<BannerModGovernorSnapshot> snapshots = new ArrayList<>(governorManager.getAllSnapshots());
        snapshots.removeIf(snapshot -> snapshot == null || !snapshot.hasGovernor());
        snapshots.sort(Comparator.comparing(BannerModGovernorSnapshot::claimUuid));
        int total = snapshots.size();
        if (total == 0 || maxSnapshots <= 0) {
            return recordBatchResult("settlement.heartbeat.governor_batch", new BatchResult(0, total == 0 ? 0 : Math.max(0, Math.min(startIndex, total)), total, total == 0), startNanos);
        }

        int clampedStart = Math.max(0, Math.min(startIndex, total));
        int endIndex = Math.min(total, clampedStart + maxSnapshots);
        for (int i = clampedStart; i < endIndex; i++) {
            BannerModGovernorSnapshot snapshot = snapshots.get(i);
            if (snapshot == null || !snapshot.hasGovernor()) {
                continue;
            }

            RecruitsClaim claim = resolveClaim(claimManager, snapshot);
            List<AbstractWorkerEntity> workers = claim == null ? List.of() : workersInClaim(level, claim);
            List<AbstractRecruitEntity> recruits = claim == null ? List.of() : recruitsInClaim(level, claim);
            BannerModSupplyStatus.WorkerSupplyStatus workerSupplyStatus = summarizeWorkerSupplyStatus(workers);
            BannerModSupplyStatus.RecruitSupplyStatus recruitSupplyStatus = summarizeRecruitSupplyStatus(level, recruits);
            BannerModSettlementBinding.Binding binding = claim == null
                    ? BannerModSettlementBinding.resolveSettlementStatus((RecruitsClaim) null, snapshot.anchorChunk(), snapshot.settlementFactionId())
                    : BannerModSettlementBinding.resolveSettlementStatus(claim, resolveAnchorChunk(claim, snapshot), snapshot.settlementFactionId());

            HeartbeatReport report = evaluate(new HeartbeatInput(
                    binding.status(),
                    claim != null && WarSiegeQueries.isClaimUnderSiege(level, claim),
                    claim == null ? 0 : countEntitiesInClaim(level, claim, Villager.class),
                    workers.size(),
                    recruits.size(),
                    workerSupplyStatus,
                    recruitSupplyStatus,
                    level.getGameTime(),
                    snapshot.lastHeartbeatTick(),
                    snapshot
            ));

            BannerModTreasuryLedgerSnapshot.FiscalRollup fiscalRollup = recordHeartbeatAccounting(
                    treasuryManager,
                    snapshot,
                    binding,
                    report,
                    recruitSupplyStatus
            );

            BannerModGovernorSnapshot updated = snapshot.withHeartbeatReport(
                    report.heartbeatTick(),
                    report.collectionTick(),
                    report.citizenCount(),
                    report.taxesDue(),
                    report.taxesCollected(),
                    incidentTokens(report.incidents()),
                    recommendationTokens(report.recommendations())
            ).withFiscalRollup(fiscalRollup);
            governorManager.putSnapshot(updated);

            if (contractManager != null) {
                contractManager.expireOldContracts(level.getGameTime());
                BannerModContractPolicy.autoPost(updated, report, contractManager, level.getGameTime());
            }
        }

        return recordBatchResult("settlement.heartbeat.governor_batch", new BatchResult(clampedStart, endIndex, total, endIndex >= total), startNanos);
    }

    private static BatchResult recordBatchResult(String keyPrefix, BatchResult result, long startNanos) {
        RuntimeProfilingCounters.recordBatch(keyPrefix, Math.max(0, result.nextIndex() - result.startIndex()), result.totalItems(), System.nanoTime() - startNanos, result.completed());
        return result;
    }

    private static BannerModSupplyStatus.WorkerSupplyStatus summarizeWorkerSupplyStatus(List<AbstractWorkerEntity> workers) {
        for (AbstractWorkerEntity worker : workers) {
            BannerModSupplyStatus.WorkerSupplyStatus supplyStatus = worker.getSupplyStatus();
            if (supplyStatus != null && supplyStatus.blocked()) {
                return supplyStatus;
            }
        }
        return new BannerModSupplyStatus.WorkerSupplyStatus(false, null, null);
    }

    private static BannerModSupplyStatus.RecruitSupplyStatus summarizeRecruitSupplyStatus(ServerLevel level,
                                                                                           List<AbstractRecruitEntity> recruits) {
        boolean blocked = false;
        boolean needsFood = false;
        boolean needsPayment = false;
        String reasonToken = null;
        int unpaidLevel = 0;
        int starvingLevel = 0;
        boolean unpaid = false;
        boolean starving = false;
        float lowestHunger = 100.0F;

        for (AbstractRecruitEntity recruit : recruits) {
            BannerModSupplyStatus.RecruitSupplyStatus supplyStatus = recruit.getSupplyStatus(resolveRecruitUpkeepContainer(level, recruit));
            if (supplyStatus == null) {
                continue;
            }
            blocked |= supplyStatus.blocked();
            needsFood |= supplyStatus.needsFood();
            needsPayment |= supplyStatus.needsPayment();
            if (reasonToken == null && supplyStatus.reasonToken() != null) {
                reasonToken = supplyStatus.reasonToken();
            }
            if (supplyStatus.accounting() != null) {
                unpaid |= supplyStatus.accounting().unpaid();
                starving |= supplyStatus.accounting().starving();
                unpaidLevel += Math.max(0, supplyStatus.accounting().unpaidLevel());
                starvingLevel = Math.max(starvingLevel, Math.max(0, supplyStatus.accounting().starvingLevel()));
            }
            lowestHunger = Math.min(lowestHunger, recruit.getHunger());
        }

        BannerModSupplyStatus.RecruitSupplyState state = BannerModSupplyStatus.RecruitSupplyState.READY;
        if (needsFood && needsPayment) {
            state = BannerModSupplyStatus.RecruitSupplyState.NEEDS_FOOD_AND_PAYMENT;
        } else if (needsFood) {
            state = BannerModSupplyStatus.RecruitSupplyState.NEEDS_FOOD;
        } else if (needsPayment) {
            state = BannerModSupplyStatus.RecruitSupplyState.NEEDS_PAYMENT;
        }

        BannerModSupplyStatus.ArmyUpkeepState accountingState = BannerModSupplyStatus.armyUpkeepStatus(unpaid, starving, lowestHunger).state();
        BannerModSupplyStatus.ArmyUpkeepStatus accounting = new BannerModSupplyStatus.ArmyUpkeepStatus(
                accountingState,
                unpaid,
                starving,
                unpaidLevel,
                starvingLevel,
                BannerModSupplyStatus.armyUpkeepStatus(unpaid, starving, lowestHunger).reasonToken()
        );
        return new BannerModSupplyStatus.RecruitSupplyStatus(state, blocked, needsFood, needsPayment, reasonToken, accounting);
    }

    @Nullable
    private static Container resolveRecruitUpkeepContainer(ServerLevel level, AbstractRecruitEntity recruit) {
        if (recruit.getUpkeepPos() != null) {
            var blockEntity = level.getBlockEntity(recruit.getUpkeepPos());
            if (blockEntity instanceof Container container) {
                return container;
            }
        }
        if (recruit.getUpkeepUUID() != null) {
            Entity entity = level.getEntity(recruit.getUpkeepUUID());
            if (entity instanceof Container container) {
                return container;
            }
            if (entity instanceof AbstractRecruitEntity upkeepRecruit) {
                return upkeepRecruit.getInventory();
            }
        }
        return null;
    }

    private static <T extends Entity> List<T> entitiesInClaim(ServerLevel level, RecruitsClaim claim, Class<T> entityClass) {
        return level.getEntitiesOfClass(entityClass, claimBounds(level, claim), entity -> entity.isAlive() && claim.containsChunk(entity.chunkPosition()));
    }

    private static List<AbstractWorkerEntity> workersInClaim(ServerLevel level, RecruitsClaim claim) {
        return WorkerIndex.instance()
                .queryInClaim(level, claim)
                .orElseGet(() -> {
                    RuntimeProfilingCounters.increment("worker.index.fallback_scans");
                    return entitiesInClaim(level, claim, AbstractWorkerEntity.class);
                });
    }

    private static List<AbstractRecruitEntity> recruitsInClaim(ServerLevel level, RecruitsClaim claim) {
        return RecruitIndex.instance()
                .queryInClaim(level, claim)
                .orElseGet(() -> {
                    RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                    return entitiesInClaim(level, claim, AbstractRecruitEntity.class);
                });
    }

    static void depositTaxes(@Nullable BannerModTreasuryManager treasuryManager,
                             BannerModGovernorSnapshot snapshot,
                             BannerModSettlementBinding.Binding binding,
                             HeartbeatReport report) {
        if (treasuryManager == null || snapshot == null || binding == null || report == null || report.taxesCollected() <= 0) {
            return;
        }
        treasuryManager.depositTaxes(
                snapshot.claimUuid(),
                snapshot.anchorChunk(),
                binding.claimFactionId(),
                report.taxesCollected(),
                report.collectionTick()
        );
    }

    static void recordArmyUpkeepDebit(@Nullable BannerModTreasuryManager treasuryManager,
                                      BannerModGovernorSnapshot snapshot,
                                      BannerModSettlementBinding.Binding binding,
                                      HeartbeatReport report,
                                      @Nullable BannerModSupplyStatus.RecruitSupplyStatus recruitSupplyStatus) {
        if (treasuryManager == null || snapshot == null || binding == null || report == null || recruitSupplyStatus == null) {
            return;
        }
        BannerModSupplyStatus.ArmyUpkeepStatus accounting = recruitSupplyStatus.accounting();
        if (accounting == null || !accounting.unpaid()) {
            return;
        }
        treasuryManager.recordArmyUpkeepDebit(
                snapshot.claimUuid(),
                snapshot.anchorChunk(),
                binding.claimFactionId(),
                accounting.unpaidLevel(),
                report.heartbeatTick()
        );
    }

    @Nullable
    static BannerModTreasuryLedgerSnapshot.FiscalRollup recordHeartbeatAccounting(@Nullable BannerModTreasuryManager treasuryManager,
                                                                                  BannerModGovernorSnapshot snapshot,
                                                                                  BannerModSettlementBinding.Binding binding,
                                                                                  HeartbeatReport report,
                                                                                  @Nullable BannerModSupplyStatus.RecruitSupplyStatus recruitSupplyStatus) {
        if (treasuryManager == null || snapshot == null || binding == null || report == null) {
            return null;
        }
        int requestedArmyUpkeepDebit = resolveRequestedArmyUpkeepDebit(recruitSupplyStatus);
        BannerModTreasuryLedgerSnapshot updated = treasuryManager.applyHeartbeatAccounting(
                snapshot.claimUuid(),
                snapshot.anchorChunk(),
                binding.claimFactionId(),
                report.taxesCollected(),
                requestedArmyUpkeepDebit,
                report.heartbeatTick()
        );
        return updated.projectFiscalRollup(report.taxesCollected(), requestedArmyUpkeepDebit, report.heartbeatTick());
    }

    private static int resolveRequestedArmyUpkeepDebit(@Nullable BannerModSupplyStatus.RecruitSupplyStatus recruitSupplyStatus) {
        if (recruitSupplyStatus == null || recruitSupplyStatus.accounting() == null || !recruitSupplyStatus.accounting().unpaid()) {
            return 0;
        }
        return recruitSupplyStatus.accounting().unpaidLevel();
    }

    public record HeartbeatInput(BannerModSettlementBinding.Status settlementStatus,
                                 boolean underSiege,
                                 int villagerCount,
                                 int workerCount,
                                 int recruitCount,
                                 BannerModSupplyStatus.WorkerSupplyStatus workerSupplyStatus,
                                 BannerModSupplyStatus.RecruitSupplyStatus recruitSupplyStatus,
                                 long gameTime,
                                 long previousHeartbeatTick,
                                 BannerModGovernorSnapshot snapshot) {
    }

    public record HeartbeatReport(int citizenCount,
                                  int taxesDue,
                                  int taxesCollected,
                                  List<BannerModGovernorIncident> incidents,
                                  List<BannerModGovernorRecommendation> recommendations,
                                  long heartbeatTick,
                                   long collectionTick) {
    }

    public record BatchResult(int startIndex,
                              int nextIndex,
                              int totalItems,
                              boolean completed) {
        private static BatchResult completedResult() {
            return new BatchResult(0, 0, 0, true);
        }
    }

    private static RecruitsClaim resolveClaim(RecruitsClaimManager claimManager, BannerModGovernorSnapshot snapshot) {
        RecruitsClaim claim = claimManager.getClaim(snapshot.anchorChunk());
        if (claim != null && claim.getUUID().equals(snapshot.claimUuid())) {
            return claim;
        }
        for (RecruitsClaim candidate : claimManager.getAllClaims()) {
            if (candidate != null && candidate.getUUID().equals(snapshot.claimUuid())) {
                return candidate;
            }
        }
        return claim;
    }

    private static ChunkPos resolveAnchorChunk(RecruitsClaim claim, BannerModGovernorSnapshot snapshot) {
        if (claim.getCenter() != null) {
            return claim.getCenter();
        }
        if (!claim.getClaimedChunks().isEmpty()) {
            return claim.getClaimedChunks().get(0);
        }
        return snapshot.anchorChunk();
    }

    private static <T extends Entity> int countEntitiesInClaim(ServerLevel level, RecruitsClaim claim, Class<T> entityClass) {
        return level.getEntitiesOfClass(entityClass, claimBounds(level, claim), entity -> entity.isAlive() && claim.containsChunk(entity.chunkPosition())).size();
    }

    private static AABB claimBounds(ServerLevel level, RecruitsClaim claim) {
        ChunkPos anchor = claim.getCenter() != null ? claim.getCenter() : new ChunkPos(0, 0);
        int minChunkX = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.x).min().orElse(anchor.x);
        int maxChunkX = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.x).max().orElse(anchor.x);
        int minChunkZ = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.z).min().orElse(anchor.z);
        int maxChunkZ = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.z).max().orElse(anchor.z);
        return new AABB(
                minChunkX * 16.0D,
                level.getMinBuildHeight(),
                minChunkZ * 16.0D,
                (maxChunkX + 1) * 16.0D,
                level.getMaxBuildHeight(),
                (maxChunkZ + 1) * 16.0D
        );
    }
}
