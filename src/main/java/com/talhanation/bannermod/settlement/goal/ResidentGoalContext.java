package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.society.NpcHouseholdHousingState;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyDecisionSnapshot;
import com.talhanation.bannermod.society.NpcSocietyPhaseOneRuntime;
import com.talhanation.bannermod.society.NpcSocietyProfile;
import com.talhanation.bannermod.society.NpcLifeStage;
import com.talhanation.bannermod.settlement.SettlementResidentRecord;
import com.talhanation.bannermod.settlement.SettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.SettlementResidentSchedulePolicy;
import com.talhanation.bannermod.settlement.SettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.SettlementResidentRole;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public record ResidentGoalContext(
        SettlementResidentRecord resident,
        @Nullable SettlementSnapshot settlement,
        long gameTime,
        long worldDayTime,
        @Nullable NpcSocietyProfile societyProfile,
        @Nullable Vec3 currentPosition
) {

    private static final double HOME_SETTLE_DISTANCE_SQR = 36.0D;
    private static final double LEAVE_HOME_FAN_OUT_DISTANCE_SQR = 16.0D;

    public ResidentGoalContext(SettlementResidentRecord resident,
                               @Nullable SettlementSnapshot settlement,
                               long gameTime) {
        this(resident, settlement, gameTime, gameTime, null, null);
    }

    public ResidentGoalContext(SettlementResidentRecord resident,
                               @Nullable SettlementSnapshot settlement,
                               long gameTime,
                               @Nullable NpcSocietyProfile societyProfile) {
        this(resident, settlement, gameTime, gameTime, societyProfile, null);
    }

    public ResidentGoalContext(SettlementResidentRecord resident,
                               @Nullable SettlementSnapshot settlement,
                               long gameTime,
                               long worldDayTime,
                               @Nullable NpcSocietyProfile societyProfile) {
        this(resident, settlement, gameTime, worldDayTime, societyProfile, null);
    }

    public ResidentGoalContext(SettlementResidentRecord resident,
                               @Nullable SettlementSnapshot settlement,
                               long gameTime,
                               long worldDayTime,
                               @Nullable NpcSocietyProfile societyProfile,
                               int householdSize,
                               NpcHouseholdHousingState householdHousingState,
                               boolean hasSpouse,
                               int childCount) {
        this(resident, settlement, gameTime, worldDayTime, societyProfile, null);
    }

    public ResidentGoalContext(SettlementResidentRecord resident,
                               @Nullable SettlementSnapshot settlement,
                               long gameTime,
                               long worldDayTime,
                               @Nullable NpcSocietyProfile societyProfile,
                               int householdSize,
                               NpcHouseholdHousingState householdHousingState,
                               boolean hasSpouse,
                               int childCount,
                               @Nullable Vec3 currentPosition) {
        this(resident, settlement, gameTime, worldDayTime, societyProfile, currentPosition);
    }

    public UUID residentId() {
        return this.resident.residentUuid();
    }

    public SettlementResidentSchedulePolicy policy() {
        return this.resident.schedulePolicy();
    }

    public SettlementResidentScheduleWindowSeed window() {
        return this.resident.scheduleWindowSeed();
    }

    /** Minecraft day-of-time (0..23999) derived from absolute game time. */
    public int dayTime() {
        long time = this.worldDayTime % 24000L;
        if (time < 0L) {
            time += 24000L;
        }
        return (int) time;
    }

    /** True when within the policy-defined active window of the current day. */
    public boolean isActivePhase() {
        int t = this.dayTime();
        SettlementResidentScheduleWindowSeed w = this.window();
        return t >= w.activeStartTick() && t < w.activeEndTick();
    }

    /** True when within the policy-defined rest window of the current day. */
    public boolean isRestPhase() {
        int t = this.dayTime();
        SettlementResidentScheduleWindowSeed w = this.window();
        return t >= w.restStartTick() || t < w.activeStartTick();
    }

    /** True during the gap between labor/civic work and the rest window. */
    public boolean isLeisurePhase() {
        int t = this.dayTime();
        SettlementResidentScheduleWindowSeed w = this.window();
        return t >= w.activeEndTick() && t < w.restStartTick();
    }

    public boolean isDayRoutinePhase() {
        return this.isActivePhase() || this.isLeisurePhase();
    }

    public int ticksSinceActiveStart() {
        int t = this.dayTime();
        int activeStart = this.window().activeStartTick();
        return t < activeStart ? -1 : t - activeStart;
    }

    public int ticksUntilRestStart() {
        if (this.isRestPhase()) {
            return 0;
        }
        int t = this.dayTime();
        int restStart = this.window().restStartTick();
        return t >= restStart ? -1 : restStart - t;
    }

    public boolean isEarlyActiveWindow(int windowTicks) {
        int sinceStart = this.ticksSinceActiveStart();
        return this.isActivePhase() && windowTicks > 0 && sinceStart >= 0 && sinceStart < windowTicks;
    }

    public boolean isLateDayWindow(int windowTicks) {
        int untilRest = this.ticksUntilRestStart();
        return windowTicks > 0 && untilRest > 0 && untilRest <= windowTicks;
    }

    public NpcIntent currentPublishedIntent() {
        return this.societyProfile == null || this.societyProfile.currentIntent() == null
                ? NpcIntent.UNSPECIFIED
                : this.societyProfile.currentIntent();
    }

    public NpcIntent lastPublishedIntent() {
        if (this.societyProfile == null || this.societyProfile.decisionSnapshot() == null) {
            return NpcIntent.UNSPECIFIED;
        }
        return NpcIntent.fromName(this.societyProfile.decisionSnapshot().lastIntentTag());
    }

    public long currentIntentAgeTicks() {
        if (this.societyProfile == null || this.societyProfile.decisionSnapshot() == null) {
            return 0L;
        }
        long started = this.societyProfile.decisionSnapshot().currentIntentStartedGameTime();
        if (started <= 0L) {
            return 0L;
        }
        return Math.max(0L, this.gameTime - started);
    }

    public @Nullable String previousBlockedGoalId() {
        NpcSocietyDecisionSnapshot snapshot = this.societyProfile == null ? null : this.societyProfile.decisionSnapshot();
        if (snapshot == null || snapshot.blockedGoalId() == null || snapshot.blockedGoalId().isBlank()) {
            return null;
        }
        return snapshot.blockedGoalId();
    }

    public String previousBlockedReasonTag() {
        NpcSocietyDecisionSnapshot snapshot = this.societyProfile == null ? null : this.societyProfile.decisionSnapshot();
        return snapshot == null ? "NONE" : snapshot.blockedReasonTag();
    }

    public NpcIntent previousBlockedIntent() {
        String goalId = this.previousBlockedGoalId();
        if (goalId == null) {
            return NpcIntent.UNSPECIFIED;
        }
        return NpcSocietyPhaseOneRuntime.intentForGoal(ResourceLocation.tryParse(goalId));
    }

    public boolean isReadyToSettleAtHome() {
        if (!this.isRestPhase()
                || this.currentPublishedIntent() != NpcIntent.GO_HOME
                || this.currentIntentAgeTicks() < 80L) {
            return false;
        }
        Vec3 homeCenter = this.homeBuildingCenter();
        if (this.currentPosition == null || homeCenter == null) {
            return true;
        }
        return this.currentPosition.distanceToSqr(homeCenter) <= HOME_SETTLE_DISTANCE_SQR;
    }

    public boolean isReadyToFanOutFromLeaveHome() {
        if (!this.isActivePhase()
                || this.currentPublishedIntent() != NpcIntent.LEAVE_HOME
                || this.currentIntentAgeTicks() < 50L) {
            return false;
        }
        Vec3 homeCenter = this.homeBuildingCenter();
        if (this.currentPosition == null || homeCenter == null) {
            return true;
        }
        return this.currentPosition.distanceToSqr(homeCenter) >= LEAVE_HOME_FAN_OUT_DISTANCE_SQR;
    }

    public boolean recentlyCameFromHome() {
        return this.currentPublishedIntent() == NpcIntent.LEAVE_HOME
                || this.lastPublishedIntent() == NpcIntent.LEAVE_HOME;
    }

    public boolean hasHome() {
        return this.societyProfile != null && this.societyProfile.homeBuildingUuid() != null;
    }

    private @Nullable Vec3 homeBuildingCenter() {
        if (this.settlement == null || this.societyProfile == null || this.societyProfile.homeBuildingUuid() == null) {
            return null;
        }
        for (SettlementBuildingRecord building : this.settlement.buildings()) {
            if (building != null && this.societyProfile.homeBuildingUuid().equals(building.buildingUuid()) && building.originPos() != null) {
                return Vec3.atCenterOf(building.originPos());
            }
        }
        return null;
    }

    public boolean hasWorkAssignment() {
        SettlementResidentAssignmentState state = this.resident.assignmentState();
        return this.resident.effectiveWorkBuildingUuid() != null
                && (state == SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                || state == SettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING);
    }

    public int hungerNeed() {
        return this.societyProfile == null ? 0 : this.societyProfile.hungerNeed();
    }

    public int fatigueNeed() {
        return this.societyProfile == null ? 0 : this.societyProfile.fatigueNeed();
    }

    public int safetyNeed() {
        return this.societyProfile == null ? 0 : this.societyProfile.safetyNeed();
    }

    public boolean canDefend() {
        return this.resident.role() == SettlementResidentRole.GOVERNOR_RECRUIT;
    }

    public boolean isAdolescent() {
        return this.societyProfile != null && this.societyProfile.lifeStage() == NpcLifeStage.ADOLESCENT;
    }

    public boolean hasMarketFoodAccess() {
        return this.settlement != null && this.settlement.marketState().openMarketCount() > 0;
    }

    public boolean hasSupplyAccess() {
        if (this.hasMarketFoodAccess()) {
            return true;
        }
        if (this.settlement == null) {
            return false;
        }
        if (this.settlement.stockpileSummary().storageBuildingCount() > 0) {
            return true;
        }
        for (SettlementBuildingRecord building : this.settlement.buildings()) {
            if (building != null && building.stockpileBuilding()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOnlyStockpileFoodAccess() {
        return this.hasSupplyAccess() && !this.hasMarketFoodAccess();
    }

    public boolean shouldEscalateMealRecoveryToSupplies() {
        if (!this.hasSupplyAccess()) {
            return false;
        }
        if (this.hungerNeed() < 70) {
            return false;
        }
        return !this.hasHome() && !this.hasMarketFoodAccess();
    }

}
