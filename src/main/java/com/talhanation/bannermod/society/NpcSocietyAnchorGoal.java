package com.talhanation.bannermod.society;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;

public final class NpcSocietyAnchorGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQR = 5.0D;
    private static final double HOME_INTENT_ARRIVAL_DISTANCE_SQR = 8.0D;
    private static final double HOME_SOCIAL_ARRIVAL_DISTANCE_SQR = 10.0D;
    private static final double TARGET_SNAP_DISTANCE_SQR = 4.0D;
    private static final double HOME_INTENT_TARGET_SNAP_DISTANCE_SQR = 20.25D;
    private static final double HOME_SOCIAL_TARGET_SNAP_DISTANCE_SQR = 25.0D;
    private static final double HOUSEHOLD_COMPANION_RANGE = 7.0D;
    private static final double HOUSEHOLD_COMPANION_DEADBAND_SQR = 12.25D;
    private static final double SOCIAL_PARTNER_DEADBAND_SQR = 6.25D;
    private static final int REPATH_INTERVAL_TICKS = 15;
    private static final int HOME_INTENT_REPATH_INTERVAL_TICKS = 32;
    private static final int HOME_SOCIAL_REPATH_INTERVAL_TICKS = 40;

    private final PathfinderMob mob;
    private Vec3 targetPos;
    private int repathCooldown;

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
        if (this.targetPos == null) {
            return;
        }
        NpcSocietyProfile profile = profile();
        if (this.mob.position().distanceToSqr(this.targetPos) > arrivalDistanceSqr(profile)) {
            this.mob.getNavigation().moveTo(this.targetPos.x, this.targetPos.y, this.targetPos.z, speed());
            this.repathCooldown = repathIntervalTicks(profile);
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.repathCooldown = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        NpcSocietyProfile profile = profile();
        this.mob.getLookControl().setLookAt(this.targetPos.x, this.targetPos.y, this.targetPos.z);
        if (this.mob.position().distanceToSqr(this.targetPos) > arrivalDistanceSqr(profile)) {
            if (this.repathCooldown <= 0 || this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().moveTo(this.targetPos.x, this.targetPos.y, this.targetPos.z, speed());
                this.repathCooldown = repathIntervalTicks(profile);
            } else {
                this.repathCooldown--;
            }
            return;
        }
        this.mob.getNavigation().stop();
        this.repathCooldown = 0;
        if (profile != null && profile.currentIntent() == NpcIntent.SOCIALISE) {
            LivingEntity partner = preferredSocialPartner(profile);
            if (partner != null) {
                this.mob.getLookControl().setLookAt(partner, 30.0F, 30.0F);
            }
            return;
        }
        if (profile != null
                && profile.currentAnchor() == NpcAnchorType.HOME
                && (profile.currentIntent() == NpcIntent.GO_HOME
                || profile.currentIntent() == NpcIntent.REST
                || profile.currentIntent() == NpcIntent.EAT
                || profile.currentIntent() == NpcIntent.HIDE)) {
            LivingEntity companion = nearestHouseholdCompanion();
            if (companion != null) {
                this.mob.getLookControl().setLookAt(companion, 22.0F, 22.0F);
            }
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
            case EAT, SEEK_SUPPLIES, SOCIALISE, LEAVE_HOME -> 0.9D;
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
        BannerModSettlementSnapshot snapshot = resolveSnapshot(serverLevel, profile);
        Vec3 anchorBase = resolveAnchorBase(snapshot, profile);
        if (anchorBase == null) {
            anchorBase = resolveIntentBase(snapshot, profile);
        }
        Vec3 target = approachTarget(anchorBase, profile.currentIntent(), profile.currentAnchor());
        if (profile.currentAnchor() == NpcAnchorType.HOME && isHouseholdHomeIntent(profile.currentIntent())) {
            target = householdGatherTarget(target, profile);
        }
        if (profile.currentIntent() == NpcIntent.SOCIALISE) {
            return socialGatherTarget(target, profile);
        }
        return target;
    }

    private boolean isHouseholdHomeIntent(@Nullable NpcIntent intent) {
        return intent == NpcIntent.GO_HOME
                || intent == NpcIntent.REST
                || intent == NpcIntent.EAT
                || intent == NpcIntent.HIDE;
    }

    private @Nullable Vec3 resolveAnchorBase(@Nullable BannerModSettlementSnapshot snapshot, NpcSocietyProfile profile) {
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
        if (profile.currentIntent() == NpcIntent.SOCIALISE && profile.currentAnchor() == NpcAnchorType.HOME) {
            return HOME_SOCIAL_TARGET_SNAP_DISTANCE_SQR;
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
        if (profile.currentIntent() == NpcIntent.SOCIALISE && profile.currentAnchor() == NpcAnchorType.HOME) {
            return HOME_SOCIAL_ARRIVAL_DISTANCE_SQR;
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
        if (profile.currentIntent() == NpcIntent.SOCIALISE && profile.currentAnchor() == NpcAnchorType.HOME) {
            return HOME_SOCIAL_REPATH_INTERVAL_TICKS;
        }
        if (profile.currentAnchor() == NpcAnchorType.HOME && isHouseholdHomeIntent(profile.currentIntent())) {
            return HOME_INTENT_REPATH_INTERVAL_TICKS;
        }
        return REPATH_INTERVAL_TICKS;
    }

    private @Nullable Vec3 resolveIntentBase(@Nullable BannerModSettlementSnapshot snapshot, NpcSocietyProfile profile) {
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
            case SOCIALISE -> socialSpot(snapshot, profile.homeBuildingUuid(), profile.currentAnchor() == NpcAnchorType.HOME);
            case HIDE -> profile.homeBuildingUuid() != null
                    ? buildingCenter(snapshot, profile.homeBuildingUuid())
                    : streetNear(socialSpot(snapshot, profile.homeBuildingUuid(), false));
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

    private @Nullable BannerModSettlementSnapshot resolveSnapshot(ServerLevel level, NpcSocietyProfile profile) {
        for (BannerModSettlementSnapshot snapshot : BannerModSettlementManager.get(level).getAllSnapshots()) {
            if (snapshot == null) {
                continue;
            }
            if (containsBuilding(snapshot, profile.homeBuildingUuid()) || containsBuilding(snapshot, profile.workBuildingUuid())) {
                return snapshot;
            }
            for (var resident : snapshot.residents()) {
                if (resident != null && this.mob.getUUID().equals(resident.residentUuid())) {
                    return snapshot;
                }
            }
        }
        return null;
    }

    private boolean containsBuilding(BannerModSettlementSnapshot snapshot, @Nullable UUID buildingUuid) {
        if (snapshot == null || buildingUuid == null) {
            return false;
        }
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && buildingUuid.equals(building.buildingUuid())) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Vec3 buildingCenter(@Nullable BannerModSettlementSnapshot snapshot, @Nullable UUID buildingUuid) {
        if (snapshot == null || buildingUuid == null) {
            return null;
        }
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && buildingUuid.equals(building.buildingUuid())) {
                return Vec3.atCenterOf(building.originPos());
            }
        }
        return null;
    }

    private @Nullable Vec3 marketOrStreet(@Nullable BannerModSettlementSnapshot snapshot) {
        if (snapshot != null) {
            for (BannerModSettlementMarketRecord market : snapshot.marketState().markets()) {
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

    private @Nullable Vec3 marketStockpileOrStreet(@Nullable BannerModSettlementSnapshot snapshot) {
        if (snapshot != null) {
            for (BannerModSettlementMarketRecord market : snapshot.marketState().markets()) {
                if (market != null && market.open()) {
                    Vec3 marketPos = buildingCenter(snapshot, market.buildingUuid());
                    if (marketPos != null) {
                        return marketPos;
                    }
                }
            }
            for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
                if (building != null && building.stockpileBuilding()) {
                    return Vec3.atCenterOf(building.originPos());
                }
            }
        }
        return streetNear(settlementCenter(snapshot));
    }

    private @Nullable Vec3 barracksOrWork(@Nullable BannerModSettlementSnapshot snapshot, @Nullable UUID workBuildingUuid) {
        if (snapshot != null) {
            for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
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

    private @Nullable Vec3 firstBuildingCenter(@Nullable BannerModSettlementSnapshot snapshot) {
        if (snapshot == null || snapshot.buildings().isEmpty()) {
            return this.mob.position();
        }
        return snapshot.buildings().stream()
                .filter(building -> building != null && building.originPos() != null)
                .map(building -> Vec3.atCenterOf(building.originPos()))
                .min(Comparator.comparingDouble(pos -> pos.distanceToSqr(this.mob.position())))
                .orElse(this.mob.position());
    }

    private Vec3 settlementCenter(@Nullable BannerModSettlementSnapshot snapshot) {
        if (snapshot == null || snapshot.buildings().isEmpty()) {
            return this.mob.position();
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
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

    private Vec3 socialSpot(@Nullable BannerModSettlementSnapshot snapshot,
                            @Nullable UUID homeBuildingUuid,
                            boolean preferHome) {
        Vec3 selected = NpcSocietySocialSpotSelector.select(snapshot, homeBuildingUuid, preferHome).anchorPos();
        return selected == null ? settlementCenter(snapshot) : selected;
    }

    private Vec3 streetBase(@Nullable BannerModSettlementSnapshot snapshot, NpcSocietyProfile profile) {
        if (profile.currentIntent() == NpcIntent.LEAVE_HOME) {
            return streetNear(buildingCenter(snapshot, profile.homeBuildingUuid()));
        }
        if (profile.currentIntent() == NpcIntent.SOCIALISE) {
            return socialSpot(snapshot, profile.homeBuildingUuid(), false);
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
            case SOCIALISE -> anchor == NpcAnchorType.HOME ? 1.0D : anchor == NpcAnchorType.MARKET ? 0.8D : 2.4D;
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

    private @Nullable LivingEntity nearestSocialPartner() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return this.mob.level().getEntitiesOfClass(LivingEntity.class, this.mob.getBoundingBox().inflate(4.0D), entity -> {
                    if (entity == null || entity == this.mob || !entity.isAlive()) {
                        return false;
                    }
                    return NpcSocietyAccess.profileFor(serverLevel, entity.getUUID()).isPresent();
                }).stream()
                .sorted(Comparator
                        .comparingInt((LivingEntity entity) -> socialPartnerWeight(serverLevel, entity)).reversed()
                        .thenComparingDouble(entity -> entity.distanceToSqr(this.mob)))
                .findFirst()
                .orElse(null);
    }

    private @Nullable LivingEntity preferredSocialPartner(NpcSocietyProfile profile) {
        LivingEntity householdCompanion = profile.currentAnchor() == NpcAnchorType.HOME ? nearestHouseholdCompanion() : null;
        return householdCompanion != null ? householdCompanion : nearestSocialPartner();
    }

    private Vec3 socialGatherTarget(@Nullable Vec3 base, NpcSocietyProfile profile) {
        if (base == null) {
            return this.mob.position();
        }
        LivingEntity partner = preferredSocialPartner(profile);
        if (partner == null || partner.position().distanceToSqr(base) > 144.0D) {
            return base;
        }
        if (partner.position().distanceToSqr(base) <= SOCIAL_PARTNER_DEADBAND_SQR) {
            return base;
        }
        double blend = profile.currentAnchor() == NpcAnchorType.HOME ? 0.66D : 0.45D;
        return new Vec3(
                base.x + (partner.getX() - base.x) * blend,
                base.y,
                base.z + (partner.getZ() - base.z) * blend
        );
    }

    private Vec3 householdGatherTarget(@Nullable Vec3 base, NpcSocietyProfile profile) {
        if (base == null) {
            return this.mob.position();
        }
        LivingEntity companion = nearestHouseholdCompanion();
        if (companion == null || companion.position().distanceToSqr(base) > 100.0D) {
            return base;
        }
        if (companion.position().distanceToSqr(base) <= HOUSEHOLD_COMPANION_DEADBAND_SQR) {
            return base;
        }
        double blend = switch (profile.currentIntent()) {
            case HIDE -> 0.62D;
            case REST, EAT -> 0.55D;
            case GO_HOME -> 0.35D;
            default -> 0.0D;
        };
        if (blend <= 0.0D) {
            return base;
        }
        return new Vec3(
                base.x + (companion.getX() - base.x) * blend,
                base.y,
                base.z + (companion.getZ() - base.z) * blend
        );
    }

    private @Nullable LivingEntity nearestHouseholdCompanion() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return this.mob.level().getEntitiesOfClass(LivingEntity.class, this.mob.getBoundingBox().inflate(HOUSEHOLD_COMPANION_RANGE), entity -> {
                    if (entity == null || entity == this.mob || !entity.isAlive()) {
                        return false;
                    }
                    return NpcSocietyAccess.profileFor(serverLevel, entity.getUUID()).isPresent();
                }).stream()
                .sorted(Comparator
                        .comparingInt((LivingEntity entity) -> householdCompanionWeight(serverLevel, entity)).reversed()
                        .thenComparingDouble(entity -> entity.distanceToSqr(this.mob)))
                .filter(entity -> householdCompanionWeight(serverLevel, entity) > 0)
                .findFirst()
                .orElse(null);
    }

    private int socialPartnerWeight(ServerLevel level, LivingEntity candidate) {
        NpcSocietyProfile self = NpcSocietyAccess.profileFor(level, this.mob.getUUID()).orElse(null);
        NpcSocietyProfile other = NpcSocietyAccess.profileFor(level, candidate.getUUID()).orElse(null);
        if (self == null || other == null) {
            return 0;
        }
        int weight = other.currentIntent() == NpcIntent.SOCIALISE ? 2 : 0;
        if (self.householdId() != null && self.householdId().equals(other.householdId())) {
            weight += 3;
        }
        com.talhanation.bannermod.society.NpcFamilyRecord family = NpcFamilySavedData.get(level).runtime().familyFor(this.mob.getUUID()).orElse(null);
        if (family == null) {
            return weight;
        }
        if (candidate.getUUID().equals(family.spouseUuid())
                || candidate.getUUID().equals(family.motherUuid())
                || candidate.getUUID().equals(family.fatherUuid())
                || family.childUuids().contains(candidate.getUUID())) {
            weight += 2;
        }
        return weight;
    }

    private int householdCompanionWeight(ServerLevel level, LivingEntity candidate) {
        NpcSocietyProfile self = NpcSocietyAccess.profileFor(level, this.mob.getUUID()).orElse(null);
        NpcSocietyProfile other = NpcSocietyAccess.profileFor(level, candidate.getUUID()).orElse(null);
        if (self == null || other == null) {
            return 0;
        }
        int weight = 0;
        if (self.householdId() != null && self.householdId().equals(other.householdId())) {
            weight += 5;
        }
        com.talhanation.bannermod.society.NpcFamilyRecord family = NpcFamilySavedData.get(level).runtime().familyFor(this.mob.getUUID()).orElse(null);
        if (family == null) {
            return weight;
        }
        if (candidate.getUUID().equals(family.spouseUuid())
                || candidate.getUUID().equals(family.motherUuid())
                || candidate.getUUID().equals(family.fatherUuid())
                || family.childUuids().contains(candidate.getUUID())) {
            weight += 5;
        }
        if (other.currentAnchor() == NpcAnchorType.HOME
                || other.currentIntent() == NpcIntent.REST
                || other.currentIntent() == NpcIntent.EAT
                || other.currentIntent() == NpcIntent.SOCIALISE) {
            weight += 2;
        }
        return weight;
    }
}
