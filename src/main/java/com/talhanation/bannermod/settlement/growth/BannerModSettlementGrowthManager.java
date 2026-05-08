package com.talhanation.bannermod.settlement.growth;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingCategory;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementSupplySignal;
import com.talhanation.bannermod.settlement.BannerModSettlementTradeRouteHandoffSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stateless scorer that decides which buildings a settlement should pursue
 * next. Dormant by default; slice 25-next-C will wire it into execution.
 * Scoring is deterministic. Contract is patterned on Millenaire's
 * {@code VillageGrowthManager}; implementation is hand-rolled.
 */
public final class BannerModSettlementGrowthManager {

    private static final int SEED_CANDIDATE_BASE_SCORE = 300;
    private static final int SEED_CANDIDATE_PRIORITY_BONUS_PER_UNIT = 25;
    private static final int HOUSING_SHORTAGE_BASE_SCORE = 600;
    private static final int HOUSING_SHORTAGE_PER_UNASSIGNED_BONUS = 40;
    private static final int MARKET_MISSING_BASE_SCORE = 500;
    private static final int DESIRED_GOOD_BASE_SCORE = 400;
    private static final int DESIRED_GOOD_PER_DRIVER_BONUS = 20;
    private static final int SUPPLY_SHORTAGE_BASE_SCORE = 560;
    private static final int SUPPLY_SHORTAGE_PER_UNIT_BONUS = 35;
    private static final int SUPPLY_RESERVATION_BASE_SCORE = 480;
    private static final int SUPPLY_RESERVATION_PER_UNIT_BONUS = 25;
    private static final int SIEGE_DEFENSE_BONUS = 300;
    private static final int SIEGE_NON_DEFENSE_PENALTY = 250;
    private static final int GOVERNOR_POLICY_WEIGHT = 5;
    private static final int DEFAULT_ESTIMATED_TICK_COST = 20 * 60;

    private BannerModSettlementGrowthManager() {}

    /** Ordered list of projects, highest priority first; empty when nothing to do. */
    public static List<PendingProject> evaluateGrowthQueue(
            BannerModSettlementGrowthContext ctx,
            int maxQueueSize
    ) {
        if (ctx == null || maxQueueSize <= 0) {
            return List.of();
        }
        Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile =
                new EnumMap<>(BannerModSettlementBuildingProfileSeed.class);
        scoreSeedCandidate(ctx, byProfile);
        scoreHousingPressure(ctx, byProfile);
        scoreMissingMarket(ctx, byProfile);
        scoreDesiredGoods(ctx, byProfile);
        scoreSpecificSupplySignals(ctx, byProfile);
        applyGovernorAdjustments(ctx, byProfile);
        applySiegeAdjustments(ctx, byProfile);
        if (byProfile.isEmpty()) {
            return List.of();
        }
        List<ScoredCandidate> scored = new ArrayList<>(byProfile.values());
        scored.sort(CANDIDATE_COMPARATOR);
        List<PendingProject> out = new ArrayList<>(Math.min(scored.size(), maxQueueSize));
        for (ScoredCandidate candidate : scored) {
            if (out.size() >= maxQueueSize) {
                break;
            }
            out.add(candidate.toPendingProject(ctx));
        }
        return List.copyOf(out);
    }

    /** Convenience wrapper returning only the top-scored project. */
    public static Optional<PendingProject> pickNextProject(BannerModSettlementGrowthContext ctx) {
        List<PendingProject> queue = evaluateGrowthQueue(ctx, 1);
        return queue.isEmpty() ? Optional.empty() : Optional.of(queue.get(0));
    }

    private static void scoreSeedCandidate(
            BannerModSettlementGrowthContext ctx,
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile
    ) {
        BannerModSettlementProjectCandidateSnapshot seed = ctx.projectCandidateSnapshot();
        if (seed == null || seed.targetBuildingProfileSeed() == null) {
            return;
        }
        BannerModSettlementBuildingProfileSeed profile = seed.targetBuildingProfileSeed();
        int score = SEED_CANDIDATE_BASE_SCORE + SEED_CANDIDATE_PRIORITY_BONUS_PER_UNIT * seed.priority();
        mergeOrInsert(byProfile, profile, score);
    }

    private static void scoreHousingPressure(
            BannerModSettlementGrowthContext ctx,
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile
    ) {
        int capacity = ctx.residentCapacity();
        int assigned = ctx.assignedResidentCount();
        int unassignedWorkers = ctx.unassignedWorkerCount();
        boolean saturated = capacity > 0 && assigned >= capacity;
        boolean noCapacity = capacity == 0 && (assigned > 0 || unassignedWorkers > 0);
        if (!saturated && !noCapacity) {
            return;
        }
        int bonusUnits = Math.max(unassignedWorkers, Math.max(0, assigned - capacity));
        int score = HOUSING_SHORTAGE_BASE_SCORE + HOUSING_SHORTAGE_PER_UNASSIGNED_BONUS * bonusUnits;
        // Housing falls under GENERAL; no dedicated HOUSING category yet.
        mergeOrInsert(byProfile, BannerModSettlementBuildingProfileSeed.GENERAL, score);
    }

    private static void scoreMissingMarket(
            BannerModSettlementGrowthContext ctx,
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile
    ) {
        if (ctx.marketState() == null) {
            return;
        }
        if (ctx.marketState().marketCount() > 0) {
            return;
        }
        // Only suggest a market when the settlement is populated enough to
        // need one; a completely empty snapshot should not propose anything.
        boolean hasActivity = !ctx.residents().isEmpty()
                || !ctx.buildings().isEmpty()
                || ctx.assignedResidentCount() > 0
                || ctx.unassignedWorkerCount() > 0
                || ctx.residentCapacity() > 0;
        if (!hasActivity) {
            return;
        }
        mergeOrInsert(byProfile, BannerModSettlementBuildingProfileSeed.MARKET, MARKET_MISSING_BASE_SCORE);
    }

    private static void scoreDesiredGoods(
            BannerModSettlementGrowthContext ctx,
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile
    ) {
        Map<String, Integer> demandByGood = hintedDemandByGood(ctx);
        if (demandByGood.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : demandByGood.entrySet()) {
            BannerModSettlementBuildingProfileSeed profile = profileForDesiredGood(entry.getKey());
            if (profile == null) {
                continue;
            }
            int score = DESIRED_GOOD_BASE_SCORE + DESIRED_GOOD_PER_DRIVER_BONUS * entry.getValue()
                    + tradeRouteDemandBonus(profile, ctx.tradeRouteHandoffSnapshot());
            mergeOrInsert(byProfile, profile, score);
        }
    }

    private static Map<String, Integer> hintedDemandByGood(BannerModSettlementGrowthContext ctx) {
        Map<String, Integer> demandByGood = new LinkedHashMap<>();
        for (BannerModSettlementDesiredGoodSnapshot good : ctx.desiredGoodsSnapshot().desiredGoods()) {
            mergeDemand(demandByGood, good.desiredGoodId(), good.driverCount());
        }
        for (BannerModSettlementDesiredGoodSnapshot good : ctx.tradeRouteHandoffSnapshot().desiredGoods()) {
            mergeDemand(demandByGood, good.desiredGoodId(), good.driverCount());
        }
        for (BannerModSettlementSupplySignal signal : ctx.supplySignalState().signals()) {
            mergeDemand(demandByGood, signal.goodId(), signal.desiredUnits());
        }
        return demandByGood;
    }

    private static void scoreSpecificSupplySignals(
            BannerModSettlementGrowthContext ctx,
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile
    ) {
        for (BannerModSettlementSupplySignal signal : ctx.supplySignalState().signals()) {
            int shortageUnits = signal.shortageUnits();
            int reservationUnits = signal.reservationHintUnits();
            if (shortageUnits <= 0 && reservationUnits <= 0) {
                continue;
            }
            BannerModSettlementBuildingProfileSeed profile = profileForDesiredGood(signal.goodId());
            if (profile == null) {
                continue;
            }
            int score = 0;
            if (shortageUnits > 0) {
                score = Math.max(score, SUPPLY_SHORTAGE_BASE_SCORE + SUPPLY_SHORTAGE_PER_UNIT_BONUS * shortageUnits);
            }
            if (reservationUnits > 0) {
                score = Math.max(score, SUPPLY_RESERVATION_BASE_SCORE + SUPPLY_RESERVATION_PER_UNIT_BONUS * reservationUnits);
            }
            mergeOrInsert(byProfile, profile, score + tradeRouteDemandBonus(profile, ctx.tradeRouteHandoffSnapshot()));
        }
    }

    private static void mergeDemand(Map<String, Integer> demandByGood, String goodId, int units) {
        if (goodId == null || goodId.isBlank() || units <= 0) {
            return;
        }
        demandByGood.merge(goodId, units, Math::max);
    }

    private static int tradeRouteDemandBonus(BannerModSettlementBuildingProfileSeed profile,
                                             BannerModSettlementTradeRouteHandoffSnapshot handoffSnapshot) {
        if (handoffSnapshot == null) {
            return 0;
        }
        return switch (profile) {
            case STORAGE -> DESIRED_GOOD_PER_DRIVER_BONUS
                    * Math.max(handoffSnapshot.activeReservationCount(), handoffSnapshot.routedStorageCount());
            case MARKET -> DESIRED_GOOD_PER_DRIVER_BONUS
                    * Math.max(handoffSnapshot.activeReservationCount(),
                    handoffSnapshot.readySellerDispatchCount() + handoffSnapshot.portEntrypointCount());
            default -> 0;
        };
    }

    private static void applyGovernorAdjustments(
            BannerModSettlementGrowthContext ctx,
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile
    ) {
        if (ctx.governorSnapshot() == null) {
            return;
        }
        // Fortification priority nudges CONSTRUCTION; garrison priority also
        // leans on CONSTRUCTION since there is no dedicated defence profile.
        int fortificationBoost = ctx.governorSnapshot().fortificationPriority() * GOVERNOR_POLICY_WEIGHT;
        int garrisonBoost = ctx.governorSnapshot().garrisonPriority() * GOVERNOR_POLICY_WEIGHT;
        if (fortificationBoost != 0 || garrisonBoost != 0) {
            ScoredCandidate existing = byProfile.get(BannerModSettlementBuildingProfileSeed.CONSTRUCTION);
            int boost = fortificationBoost + garrisonBoost;
            if (existing != null) {
                existing.score += boost;
            } else if (boost > 0) {
                mergeOrInsert(byProfile, BannerModSettlementBuildingProfileSeed.CONSTRUCTION, boost);
            }
        }
    }

    private static void applySiegeAdjustments(
            BannerModSettlementGrowthContext ctx,
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile
    ) {
        if (!ctx.isUnderSiege()) {
            return;
        }
        for (Map.Entry<BannerModSettlementBuildingProfileSeed, ScoredCandidate> entry : byProfile.entrySet()) {
            if (entry.getKey() == BannerModSettlementBuildingProfileSeed.CONSTRUCTION) {
                entry.getValue().score += SIEGE_DEFENSE_BONUS;
                entry.getValue().blocker = ProjectBlocker.NONE;
            } else {
                entry.getValue().score -= SIEGE_NON_DEFENSE_PENALTY;
                entry.getValue().blocker = ProjectBlocker.UNDER_SIEGE;
            }
        }
        // Ensure a defensive option always exists while under siege.
        byProfile.computeIfAbsent(BannerModSettlementBuildingProfileSeed.CONSTRUCTION,
                profile -> new ScoredCandidate(profile, SIEGE_DEFENSE_BONUS, ProjectBlocker.NONE));
    }

    private static BannerModSettlementBuildingProfileSeed profileForDesiredGood(String desiredGoodId) {
        if (desiredGoodId == null || desiredGoodId.isBlank()) {
            return null;
        }
        if (desiredGoodId.startsWith("storage_type:")) {
            return BannerModSettlementBuildingProfileSeed.STORAGE;
        }
        return switch (desiredGoodId) {
            case "food" -> BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION;
            case "materials" -> BannerModSettlementBuildingProfileSeed.MATERIAL_PRODUCTION;
            case "construction_materials" -> BannerModSettlementBuildingProfileSeed.CONSTRUCTION;
            case "market_goods", "trade_stock" -> BannerModSettlementBuildingProfileSeed.MARKET;
            default -> null;
        };
    }

    private static void mergeOrInsert(
            Map<BannerModSettlementBuildingProfileSeed, ScoredCandidate> byProfile,
            BannerModSettlementBuildingProfileSeed profile,
            int score
    ) {
        ScoredCandidate existing = byProfile.get(profile);
        if (existing == null) {
            byProfile.put(profile, new ScoredCandidate(profile, score, ProjectBlocker.NONE));
        } else if (score > existing.score) {
            existing.score = score;
        }
    }

    private static final Comparator<ScoredCandidate> CANDIDATE_COMPARATOR = (a, b) -> {
        int scoreCompare = Integer.compare(b.clampedScore(), a.clampedScore());
        if (scoreCompare != 0) return scoreCompare;
        int catCompare = Integer.compare(a.profile.category().ordinal(), b.profile.category().ordinal());
        if (catCompare != 0) return catCompare;
        return Integer.compare(Integer.reverse(a.profile.hashCode()), Integer.reverse(b.profile.hashCode()));
    };

    private static final class ScoredCandidate {
        final BannerModSettlementBuildingProfileSeed profile;
        int score;
        ProjectBlocker blocker;

        ScoredCandidate(BannerModSettlementBuildingProfileSeed profile, int score, ProjectBlocker blocker) {
            this.profile = profile;
            this.score = score;
            this.blocker = blocker;
        }

        int clampedScore() {
            return Math.max(0, Math.min(1000, this.score));
        }

        PendingProject toPendingProject(BannerModSettlementGrowthContext ctx) {
            BannerModSettlementBuildingCategory category = this.profile.category();
            UUID projectId = deterministicProjectId(ctx, this.profile);
            ProjectBlocker effectiveBlocker = this.blocker;
            if (effectiveBlocker == ProjectBlocker.NONE && ctx.isUnderSiege()
                    && this.profile != BannerModSettlementBuildingProfileSeed.CONSTRUCTION) {
                effectiveBlocker = ProjectBlocker.UNDER_SIEGE;
            }
            return new PendingProject(
                    projectId,
                    ProjectKind.NEW_BUILDING,
                    null,
                    category,
                    this.profile,
                    this.clampedScore(),
                    ctx.gameTime(),
                    DEFAULT_ESTIMATED_TICK_COST,
                    effectiveBlocker
            );
        }
    }

    private static UUID deterministicProjectId(
            BannerModSettlementGrowthContext ctx,
            BannerModSettlementBuildingProfileSeed profile
    ) {
        long hi = mix64(profile.ordinal() + 1L, profile.category().ordinal() + 1L);
        long lo = mix64(profile.hashCode(), ctx.buildings().size() + 1L);
        return new UUID(hi, lo);
    }

    private static long mix64(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L + b;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
