package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.society.NpcLifeStage;
import com.talhanation.bannermod.society.NpcHouseholdHousingState;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyProfile;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentSchedulePolicy;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;

import javax.annotation.Nullable;
import java.util.UUID;

public record ResidentGoalContext(
        BannerModSettlementResidentRecord resident,
        @Nullable BannerModSettlementSnapshot settlement,
        long gameTime,
        long worldDayTime,
        @Nullable NpcSocietyProfile societyProfile,
        int householdSize,
        NpcHouseholdHousingState householdHousingState,
        boolean hasSpouse,
        int childCount
) {

    public ResidentGoalContext(BannerModSettlementResidentRecord resident,
                               @Nullable BannerModSettlementSnapshot settlement,
                               long gameTime) {
        this(resident, settlement, gameTime, gameTime, null, 0, NpcHouseholdHousingState.NORMAL, false, 0);
    }

    public ResidentGoalContext(BannerModSettlementResidentRecord resident,
                               @Nullable BannerModSettlementSnapshot settlement,
                               long gameTime,
                               @Nullable NpcSocietyProfile societyProfile) {
        this(resident, settlement, gameTime, gameTime, societyProfile, 0, NpcHouseholdHousingState.NORMAL, false, 0);
    }

    public ResidentGoalContext(BannerModSettlementResidentRecord resident,
                               @Nullable BannerModSettlementSnapshot settlement,
                               long gameTime,
                               long worldDayTime,
                               @Nullable NpcSocietyProfile societyProfile) {
        this(resident, settlement, gameTime, worldDayTime, societyProfile, 0, NpcHouseholdHousingState.NORMAL, false, 0);
    }

    public UUID residentId() {
        return this.resident.residentUuid();
    }

    public BannerModSettlementResidentSchedulePolicy policy() {
        return this.resident.schedulePolicy();
    }

    public BannerModSettlementResidentScheduleWindowSeed window() {
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
        BannerModSettlementResidentScheduleWindowSeed w = this.window();
        return t >= w.activeStartTick() && t < w.activeEndTick();
    }

    /** True when within the policy-defined rest window of the current day. */
    public boolean isRestPhase() {
        int t = this.dayTime();
        BannerModSettlementResidentScheduleWindowSeed w = this.window();
        return t >= w.restStartTick() || t < w.activeStartTick();
    }

    /** True during the gap between labor/civic work and the rest window. */
    public boolean isLeisurePhase() {
        int t = this.dayTime();
        BannerModSettlementResidentScheduleWindowSeed w = this.window();
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

    public boolean isReadyToSettleAtHome() {
        return this.isRestPhase()
                && this.currentPublishedIntent() == NpcIntent.GO_HOME
                && this.currentIntentAgeTicks() >= 80L;
    }

    public boolean isReadyToFanOutFromLeaveHome() {
        return this.isActivePhase()
                && this.currentPublishedIntent() == NpcIntent.LEAVE_HOME
                && this.currentIntentAgeTicks() >= 50L;
    }

    public boolean recentlyCameFromHome() {
        return this.currentPublishedIntent() == NpcIntent.LEAVE_HOME
                || this.lastPublishedIntent() == NpcIntent.LEAVE_HOME;
    }

    public boolean hasHome() {
        return this.societyProfile != null && this.societyProfile.homeBuildingUuid() != null;
    }

    public int hungerNeed() {
        return this.societyProfile == null ? 0 : this.societyProfile.hungerNeed();
    }

    public int fatigueNeed() {
        return this.societyProfile == null ? 0 : this.societyProfile.fatigueNeed();
    }

    public int socialNeed() {
        return this.societyProfile == null ? 0 : this.societyProfile.socialNeed();
    }

    public int safetyNeed() {
        return this.societyProfile == null ? 0 : this.societyProfile.safetyNeed();
    }

    public int trustScore() {
        return this.societyProfile == null ? 50 : this.societyProfile.trustScore();
    }

    public int fearScore() {
        return this.societyProfile == null ? 0 : this.societyProfile.fearScore();
    }

    public int angerScore() {
        return this.societyProfile == null ? 0 : this.societyProfile.angerScore();
    }

    public int gratitudeScore() {
        return this.societyProfile == null ? 0 : this.societyProfile.gratitudeScore();
    }

    public int loyaltyScore() {
        return this.societyProfile == null ? 50 : this.societyProfile.loyaltyScore();
    }

    public boolean canDefend() {
        return this.resident.role() == com.talhanation.bannermod.settlement.BannerModSettlementResidentRole.GOVERNOR_RECRUIT;
    }

    public boolean isAdolescent() {
        return this.societyProfile != null && this.societyProfile.lifeStage() == NpcLifeStage.ADOLESCENT;
    }

    public boolean hasFamilyTies() {
        return this.hasSpouse || this.childCount > 0 || this.householdSize > 1;
    }

    public boolean hasDependents() {
        return this.childCount > 0;
    }

    public boolean isHouseholdPressured() {
        return this.householdHousingState == NpcHouseholdHousingState.HOMELESS
                || this.householdHousingState == NpcHouseholdHousingState.OVERCROWDED;
    }

    public boolean isHomelessHousehold() {
        return this.householdHousingState == NpcHouseholdHousingState.HOMELESS;
    }

    public boolean isOvercrowdedHousehold() {
        return this.householdHousingState == NpcHouseholdHousingState.OVERCROWDED;
    }
}
