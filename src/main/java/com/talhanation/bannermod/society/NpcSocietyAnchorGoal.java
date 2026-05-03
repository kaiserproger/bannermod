package com.talhanation.bannermod.society;

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

    private final PathfinderMob mob;
    private Vec3 targetPos;

    public NpcSocietyAnchorGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.targetPos = resolveTarget();
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        Vec3 nextTarget = resolveTarget();
        if (nextTarget == null) {
            return false;
        }
        this.targetPos = nextTarget;
        return true;
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        NpcSocietyProfile profile = profile();
        this.mob.getLookControl().setLookAt(this.targetPos.x, this.targetPos.y, this.targetPos.z);
        if (this.mob.position().distanceToSqr(this.targetPos) > ARRIVAL_DISTANCE_SQR) {
            this.mob.getNavigation().moveTo(this.targetPos.x, this.targetPos.y, this.targetPos.z, speed());
            return;
        }
        this.mob.getNavigation().stop();
        if (profile != null && profile.currentIntent() == NpcIntent.SOCIALISE) {
            LivingEntity partner = nearestSocialPartner();
            if (partner != null) {
                this.mob.getLookControl().setLookAt(partner, 30.0F, 30.0F);
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
        return switch (profile.currentIntent()) {
            case GO_HOME -> buildingCenter(snapshot, profile.homeBuildingUuid());
            case REST -> profile.homeBuildingUuid() != null
                    ? buildingCenter(snapshot, profile.homeBuildingUuid())
                    : streetNear(firstBuildingCenter(snapshot));
            case LEAVE_HOME -> streetNear(buildingCenter(snapshot, profile.homeBuildingUuid()));
            case EAT -> profile.homeBuildingUuid() != null
                    ? buildingCenter(snapshot, profile.homeBuildingUuid())
                    : marketOrStreet(snapshot);
            case SEEK_SUPPLIES -> marketStockpileOrStreet(snapshot);
            case SOCIALISE -> marketOrStreet(snapshot);
            case HIDE -> profile.homeBuildingUuid() != null
                    ? buildingCenter(snapshot, profile.homeBuildingUuid())
                    : streetNear(marketOrStreet(snapshot));
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
        return streetNear(firstBuildingCenter(snapshot));
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
        return streetNear(firstBuildingCenter(snapshot));
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
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(this.mob)))
                .orElse(null);
    }
}
