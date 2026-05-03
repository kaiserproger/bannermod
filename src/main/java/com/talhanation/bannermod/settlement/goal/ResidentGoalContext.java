package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.society.NpcLifeStage;
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
        @Nullable NpcSocietyProfile societyProfile
) {

    public ResidentGoalContext(BannerModSettlementResidentRecord resident,
                               @Nullable BannerModSettlementSnapshot settlement,
                               long gameTime) {
        this(resident, settlement, gameTime, gameTime, null);
    }

    public ResidentGoalContext(BannerModSettlementResidentRecord resident,
                               @Nullable BannerModSettlementSnapshot settlement,
                               long gameTime,
                               @Nullable NpcSocietyProfile societyProfile) {
        this(resident, settlement, gameTime, gameTime, societyProfile);
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
}
