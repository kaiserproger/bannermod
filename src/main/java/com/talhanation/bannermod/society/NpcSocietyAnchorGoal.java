package com.talhanation.bannermod.society;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementMarketRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcSocietyAnchorGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQR = 5.0D;
    private static final double HOME_INTENT_ARRIVAL_DISTANCE_SQR = 8.0D;
    private static final double TARGET_SNAP_DISTANCE_SQR = 4.0D;
    private static final double HOME_INTENT_TARGET_SNAP_DISTANCE_SQR = 20.25D;
    private static final int REPATH_INTERVAL_TICKS = 30;
    private static final int HOME_INTENT_REPATH_INTERVAL_TICKS = 60;
    private static final int SNAPSHOT_LOOKUP_INTERVAL_TICKS = 40;
    private static final int ROUTE_INVALID_STALL_LIMIT = 3;
    private static final double ROUTE_PROGRESS_EPSILON_SQR = 1.0D;
    private static final double ROUTE_TARGET_RESET_DISTANCE_SQR = 9.0D;

    private static final Map<UUID, RouteInvalidationSignal> ROUTE_INVALIDATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, SnapshotLookupCache> SNAPSHOT_LOOKUPS = new ConcurrentHashMap<>();

    private final PathfinderMob mob;
    private Vec3 targetPos;
    private int repathCooldown;
    private long nextSnapshotLookupGameTime;
    private @Nullable SettlementSnapshot cachedSnapshot;
    private @Nullable SnapshotLookupCache cachedSnapshotLookup;
    private @Nullable Vec3 lastMoveRequestTargetPos;
    private double lastMoveRequestDistanceSqr = Double.MAX_VALUE;
    private int stalledRouteCount;

    public NpcSocietyAnchorGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob instanceof AbstractWorkerEntity worker && worker.hasActiveCourierTask()) {
            return false;
        }
        this.targetPos = resolveTarget();
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob instanceof AbstractWorkerEntity worker && worker.hasActiveCourierTask()) {
            return false;
        }
        Vec3 nextTarget = resolveTarget();
        if (nextTarget == null) {
            return false;
        }
        if (this.targetPos == null || this.targetPos.distanceToSqr(nextTarget) > targetSnapDistanceSqr()) {
            this.targetPos = nextTarget;
        }
        return true;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.nextSnapshotLookupGameTime = 0L;
        this.cachedSnapshot = null;
        this.cachedSnapshotLookup = null;
        resetRouteFailureTracking();
        clearRouteInvalidation(this.mob.getUUID());
        if (this.targetPos == null) {
            return;
        }
        NpcSocietyProfile profile = profile();
        double distanceToTargetSqr = this.mob.position().distanceToSqr(this.targetPos);
        if (distanceToTargetSqr > arrivalDistanceSqr(profile)) {
            this.mob.getNavigation().moveTo(this.targetPos.x, this.targetPos.y, this.targetPos.z, speed());
            rememberMoveRequest(distanceToTargetSqr);
            this.repathCooldown = repathIntervalTicks(profile);
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.repathCooldown = 0;
        this.nextSnapshotLookupGameTime = 0L;
        this.cachedSnapshot = null;
        this.cachedSnapshotLookup = null;
        resetRouteFailureTracking();
        clearRouteInvalidation(this.mob.getUUID());
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        NpcSocietyProfile profile = profile();
        this.mob.getLookControl().setLookAt(this.targetPos.x, this.targetPos.y, this.targetPos.z);
        double distanceToTargetSqr = this.mob.position().distanceToSqr(this.targetPos);
        if (distanceToTargetSqr > arrivalDistanceSqr(profile)) {
            boolean navigationDone = this.mob.getNavigation().isDone();
            if (targetChangedSinceLastMoveRequest()) {
                resetRouteFailureTracking();
            }
            if (this.repathCooldown <= 0 || navigationDone) {
                if (navigationDone) {
                    if (madeMeaningfulRouteProgress(this.lastMoveRequestDistanceSqr, distanceToTargetSqr)) {
                        this.stalledRouteCount = 0;
                    } else {
                        this.stalledRouteCount++;
                    }
                    if (shouldInvalidateStalledRoute(this.stalledRouteCount, distanceToTargetSqr, arrivalDistanceSqr(profile))) {
                        signalRouteInvalidation(this.mob.getUUID(), profile == null ? NpcIntent.UNSPECIFIED : profile.currentIntent(), this.mob.level().getGameTime());
                        this.mob.getNavigation().stop();
                        this.repathCooldown = 0;
                        resetRouteFailureTracking();
                        return;
                    }
                }
                this.mob.getNavigation().moveTo(this.targetPos.x, this.targetPos.y, this.targetPos.z, speed());
                rememberMoveRequest(distanceToTargetSqr);
                this.repathCooldown = repathIntervalTicks(profile);
            } else {
                this.repathCooldown--;
            }
            return;
        }
        this.mob.getNavigation().stop();
        this.repathCooldown = 0;
        resetRouteFailureTracking();
    }

    static boolean madeMeaningfulRouteProgress(double previousDistanceSqr, double currentDistanceSqr) {
        return previousDistanceSqr - currentDistanceSqr >= ROUTE_PROGRESS_EPSILON_SQR;
    }

    static boolean shouldInvalidateStalledRoute(int stalledRouteCount, double distanceToTargetSqr, double arrivalDistanceSqr) {
        return stalledRouteCount >= ROUTE_INVALID_STALL_LIMIT && distanceToTargetSqr > arrivalDistanceSqr + 4.0D;
    }

    public static void signalRouteInvalidation(UUID residentUuid, @Nullable NpcIntent intent, long gameTime) {
        if (residentUuid == null || intent == null || intent == NpcIntent.UNSPECIFIED) {
            return;
        }
        ROUTE_INVALIDATIONS.put(residentUuid, new RouteInvalidationSignal(intent, gameTime));
    }

    public static boolean consumeRouteInvalidation(UUID residentUuid, @Nullable NpcIntent expectedIntent, long gameTime) {
        if (residentUuid == null) {
            return false;
        }
        RouteInvalidationSignal signal = ROUTE_INVALIDATIONS.get(residentUuid);
        if (signal == null) {
            return false;
        }
        if (gameTime - signal.gameTime() > 1L) {
            ROUTE_INVALIDATIONS.remove(residentUuid, signal);
            return false;
        }
        if (expectedIntent == null || signal.intent() != expectedIntent) {
            return false;
        }
        return ROUTE_INVALIDATIONS.remove(residentUuid, signal);
    }

    private static void clearRouteInvalidation(UUID residentUuid) {
        if (residentUuid != null) {
            ROUTE_INVALIDATIONS.remove(residentUuid);
        }
    }

    private double speed() {
        NpcSocietyProfile profile = profile();
        if (profile == null) {
            return 0.8D;
        }
        return switch (profile.currentIntent()) {
            case DEFEND -> 1.15D;
            case HIDE, GO_HOME -> 1.0D;
            case EAT, SEEK_SUPPLIES, LEAVE_HOME -> 0.9D;
            default -> 0.75D;
        };
    }

    private @Nullable Vec3 resolveTarget() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        NpcSocietyProfile profile = profile();
        if (profile == null || !NpcSocietyIntentRules.isAnchoredRoutineIntent(profile.currentIntent())) {
            return null;
        }
        SettlementSnapshot snapshot = resolveSnapshot(serverLevel, profile);
        Vec3 anchorBase = resolveAnchorBase(snapshot, profile);
        if (anchorBase == null) {
            anchorBase = resolveIntentBase(snapshot, profile);
        }
        return approachTarget(anchorBase, profile.currentIntent(), profile.currentAnchor());
    }

    private boolean isHouseholdHomeIntent(@Nullable NpcIntent intent) {
        return intent == NpcIntent.GO_HOME
                || intent == NpcIntent.REST
                || intent == NpcIntent.EAT
                || intent == NpcIntent.HIDE;
    }

    private @Nullable Vec3 resolveAnchorBase(@Nullable SettlementSnapshot snapshot, NpcSocietyProfile profile) {
        return switch (profile.currentAnchor()) {
            case HOME -> buildingCenter(snapshot, profile.homeBuildingUuid());
            case WORKPLACE -> {
                if (profile.currentIntent() == NpcIntent.SEEK_SUPPLIES) {
                    yield marketStockpileOrStreet(snapshot);
                }
                Vec3 workPos = buildingCenter(snapshot, profile.workBuildingUuid());
                yield workPos != null ? workPos : streetNear(settlementCenter(snapshot));
            }
            case MARKET -> marketOrStreet(snapshot);
            case BARRACKS -> barracksOrWork(snapshot, profile.workBuildingUuid());
            case STREET -> streetBase(snapshot, profile);
            default -> null;
        };
    }

    private double targetSnapDistanceSqr() {
        NpcSocietyProfile profile = profile();
        if (profile == null) {
            return TARGET_SNAP_DISTANCE_SQR;
        }
        if (profile.currentAnchor() == NpcAnchorType.HOME && isHouseholdHomeIntent(profile.currentIntent())) {
            return HOME_INTENT_TARGET_SNAP_DISTANCE_SQR;
        }
        return TARGET_SNAP_DISTANCE_SQR;
    }

    private double arrivalDistanceSqr(@Nullable NpcSocietyProfile profile) {
        if (profile == null) {
            return ARRIVAL_DISTANCE_SQR;
        }
        if (profile.currentAnchor() == NpcAnchorType.HOME && isHouseholdHomeIntent(profile.currentIntent())) {
            return HOME_INTENT_ARRIVAL_DISTANCE_SQR;
        }
        return ARRIVAL_DISTANCE_SQR;
    }

    private int repathIntervalTicks(@Nullable NpcSocietyProfile profile) {
        if (profile == null) {
            return REPATH_INTERVAL_TICKS;
        }
        if (profile.currentAnchor() == NpcAnchorType.HOME && isHouseholdHomeIntent(profile.currentIntent())) {
            return HOME_INTENT_REPATH_INTERVAL_TICKS;
        }
        return REPATH_INTERVAL_TICKS;
    }

    private @Nullable Vec3 resolveIntentBase(@Nullable SettlementSnapshot snapshot, NpcSocietyProfile profile) {
        return switch (profile.currentIntent()) {
            case GO_HOME -> buildingCenter(snapshot, profile.homeBuildingUuid());
            case REST -> profile.homeBuildingUuid() != null
                    ? buildingCenter(snapshot, profile.homeBuildingUuid())
                    : streetNear(settlementCenter(snapshot));
            case LEAVE_HOME -> streetNear(buildingCenter(snapshot, profile.homeBuildingUuid()));
            case EAT -> profile.homeBuildingUuid() != null
                    ? buildingCenter(snapshot, profile.homeBuildingUuid())
                    : marketOrStreet(snapshot);
            case SEEK_SUPPLIES -> marketStockpileOrStreet(snapshot);
            case HIDE -> profile.homeBuildingUuid() != null
                    ? buildingCenter(snapshot, profile.homeBuildingUuid())
                    : streetNear(settlementCenter(snapshot));
            case DEFEND -> barracksOrWork(snapshot, profile.workBuildingUuid());
            default -> null;
        };
    }

    private @Nullable NpcSocietyProfile profile() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return NpcSocietyAccess.profileFor(serverLevel, this.mob.getUUID()).orElse(null);
    }

    private @Nullable SettlementSnapshot resolveSnapshot(ServerLevel level, NpcSocietyProfile profile) {
        UUID residentUuid = this.mob.getUUID();
        long gameTime = level.getGameTime();
        if (this.cachedSnapshotLookup != null
                && this.cachedSnapshotLookup.matches(profile)
                && gameTime < this.nextSnapshotLookupGameTime) {
            return this.cachedSnapshot;
        }
        SettlementManager manager = SettlementManager.get(level);
        SnapshotLookupCache cached = SNAPSHOT_LOOKUPS.get(residentUuid);
        if (cached != null && cached.matches(profile)) {
            SettlementSnapshot snapshot = manager.getSnapshot(cached.claimUuid());
            if (snapshotMatchesProfile(snapshot, profile, residentUuid)) {
                this.cachedSnapshot = snapshot;
                this.cachedSnapshotLookup = cached;
                this.nextSnapshotLookupGameTime = gameTime + SNAPSHOT_LOOKUP_INTERVAL_TICKS;
                return snapshot;
            }
        }
        for (SettlementSnapshot snapshot : manager.getAllSnapshots()) {
            if (snapshot == null) {
                continue;
            }
            if (snapshotMatchesProfile(snapshot, profile, residentUuid)) {
                SnapshotLookupCache resolved = new SnapshotLookupCache(snapshot.claimUuid(), profile.homeBuildingUuid(), profile.workBuildingUuid());
                SNAPSHOT_LOOKUPS.put(residentUuid, resolved);
                this.cachedSnapshot = snapshot;
                this.cachedSnapshotLookup = resolved;
                this.nextSnapshotLookupGameTime = gameTime + SNAPSHOT_LOOKUP_INTERVAL_TICKS;
                return snapshot;
            }
        }
        SNAPSHOT_LOOKUPS.remove(residentUuid);
        this.cachedSnapshot = null;
        this.cachedSnapshotLookup = new SnapshotLookupCache(null, profile.homeBuildingUuid(), profile.workBuildingUuid());
        this.nextSnapshotLookupGameTime = gameTime + SNAPSHOT_LOOKUP_INTERVAL_TICKS;
        return null;
    }

    private boolean snapshotMatchesProfile(@Nullable SettlementSnapshot snapshot,
                                           NpcSocietyProfile profile,
                                           UUID residentUuid) {
        if (snapshot == null) {
            return false;
        }
        if (containsBuilding(snapshot, profile.homeBuildingUuid()) || containsBuilding(snapshot, profile.workBuildingUuid())) {
            return true;
        }
        for (var resident : snapshot.residents()) {
            if (resident != null && residentUuid.equals(resident.residentUuid())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsBuilding(SettlementSnapshot snapshot, @Nullable UUID buildingUuid) {
        if (snapshot == null || buildingUuid == null) {
            return false;
        }
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && buildingUuid.equals(building.buildingUuid())) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Vec3 buildingCenter(@Nullable SettlementSnapshot snapshot, @Nullable UUID buildingUuid) {
        if (snapshot == null || buildingUuid == null) {
            return null;
        }
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && buildingUuid.equals(building.buildingUuid())) {
                return Vec3.atCenterOf(building.originPos());
            }
        }
        return null;
    }

    private @Nullable Vec3 marketOrStreet(@Nullable SettlementSnapshot snapshot) {
        if (snapshot != null) {
            for (SettlementMarketRecord market : snapshot.marketState().markets()) {
                if (market != null && market.open()) {
                    Vec3 marketPos = buildingCenter(snapshot, market.buildingUuid());
                    if (marketPos != null) {
                        return marketPos;
                    }
                }
            }
        }
        return streetNear(settlementCenter(snapshot));
    }

    private @Nullable Vec3 marketStockpileOrStreet(@Nullable SettlementSnapshot snapshot) {
        if (snapshot != null) {
            for (SettlementMarketRecord market : snapshot.marketState().markets()) {
                if (market != null && market.open()) {
                    Vec3 marketPos = buildingCenter(snapshot, market.buildingUuid());
                    if (marketPos != null) {
                        return marketPos;
                    }
                }
            }
            for (SettlementBuildingRecord building : snapshot.buildings()) {
                if (building != null && building.stockpileBuilding()) {
                    return Vec3.atCenterOf(building.originPos());
                }
            }
        }
        return streetNear(settlementCenter(snapshot));
    }

    private @Nullable Vec3 barracksOrWork(@Nullable SettlementSnapshot snapshot, @Nullable UUID workBuildingUuid) {
        if (snapshot != null) {
            for (SettlementBuildingRecord building : snapshot.buildings()) {
                if (building == null || building.buildingTypeId() == null) {
                    continue;
                }
                if (building.buildingTypeId().contains("barracks")) {
                    return Vec3.atCenterOf(building.originPos());
                }
            }
        }
        Vec3 workPos = buildingCenter(snapshot, workBuildingUuid);
        return workPos != null ? workPos : streetNear(firstBuildingCenter(snapshot));
    }

    private @Nullable Vec3 firstBuildingCenter(@Nullable SettlementSnapshot snapshot) {
        if (snapshot == null || snapshot.buildings().isEmpty()) {
            return this.mob.position();
        }
        Vec3 nearest = this.mob.position();
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building == null || building.originPos() == null) {
                continue;
            }
            Vec3 candidate = Vec3.atCenterOf(building.originPos());
            double candidateDistanceSqr = candidate.distanceToSqr(this.mob.position());
            if (candidateDistanceSqr < nearestDistanceSqr) {
                nearest = candidate;
                nearestDistanceSqr = candidateDistanceSqr;
            }
        }
        return nearest;
    }

    private Vec3 settlementCenter(@Nullable SettlementSnapshot snapshot) {
        if (snapshot == null || snapshot.buildings().isEmpty()) {
            return this.mob.position();
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building == null || building.originPos() == null) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(building.originPos());
            x += center.x;
            y += center.y;
            z += center.z;
            count++;
        }
        if (count <= 0) {
            return firstBuildingCenter(snapshot);
        }
        return new Vec3(x / count, y / count, z / count);
    }
    private Vec3 streetBase(@Nullable SettlementSnapshot snapshot, NpcSocietyProfile profile) {
        if (profile.currentIntent() == NpcIntent.LEAVE_HOME) {
            return streetNear(buildingCenter(snapshot, profile.homeBuildingUuid()));
        }
        if (profile.currentIntent() == NpcIntent.HIDE && profile.homeBuildingUuid() != null) {
            return streetNear(buildingCenter(snapshot, profile.homeBuildingUuid()));
        }
        return streetNear(settlementCenter(snapshot));
    }

    private Vec3 streetNear(@Nullable Vec3 base) {
        Vec3 center = base == null ? this.mob.position() : base;
        double angle = (Math.floorMod(this.mob.getUUID().hashCode(), 360) / 180.0D) * Math.PI;
        double radius = 4.0D + Math.floorMod(this.mob.getUUID().hashCode(), 3);
        return new Vec3(
                center.x + Math.cos(angle) * radius,
                center.y,
                center.z + Math.sin(angle) * radius
        );
    }

    private @Nullable Vec3 approachTarget(@Nullable Vec3 base, @Nullable NpcIntent intent, @Nullable NpcAnchorType anchor) {
        if (base == null) {
            return null;
        }
        double radius = switch (intent == null ? NpcIntent.UNSPECIFIED : intent) {
            case GO_HOME -> 0.9D;
            case REST, HIDE, EAT -> 1.4D;
            case SEEK_SUPPLIES, LEAVE_HOME, DEFEND -> 1.8D;
            default -> 0.0D;
        };
        if (radius <= 0.0D) {
            return base;
        }
        int seed = this.mob.getUUID().hashCode() * 31 + (intent == null ? 0 : intent.ordinal() * 17);
        double angle = (Math.floorMod(seed, 360) / 180.0D) * Math.PI;
        return new Vec3(
                base.x + Math.cos(angle) * radius,
                base.y,
                base.z + Math.sin(angle) * radius
        );
    }

    private boolean targetChangedSinceLastMoveRequest() {
        return this.lastMoveRequestTargetPos != null
                && this.targetPos != null
                && this.lastMoveRequestTargetPos.distanceToSqr(this.targetPos) > ROUTE_TARGET_RESET_DISTANCE_SQR;
    }

    private void rememberMoveRequest(double distanceToTargetSqr) {
        this.lastMoveRequestTargetPos = this.targetPos;
        this.lastMoveRequestDistanceSqr = distanceToTargetSqr;
    }

    private void resetRouteFailureTracking() {
        this.lastMoveRequestTargetPos = null;
        this.lastMoveRequestDistanceSqr = Double.MAX_VALUE;
        this.stalledRouteCount = 0;
    }

    private record RouteInvalidationSignal(NpcIntent intent, long gameTime) {
    }

    private record SnapshotLookupCache(UUID claimUuid,
                                       @Nullable UUID homeBuildingUuid,
                                       @Nullable UUID workBuildingUuid) {
        private boolean matches(NpcSocietyProfile profile) {
            return sameUuid(this.homeBuildingUuid, profile.homeBuildingUuid())
                    && sameUuid(this.workBuildingUuid, profile.workBuildingUuid());
        }

        private static boolean sameUuid(@Nullable UUID left, @Nullable UUID right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
