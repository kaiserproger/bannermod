package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.settlement.SettlementResidentRecord;
import com.talhanation.bannermod.settlement.SettlementResidentSchedulePolicy;
import com.talhanation.bannermod.settlement.SettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.SettlementSnapshot;

import javax.annotation.Nullable;
import java.util.UUID;

public record ResidentGoalContext(
        SettlementResidentRecord resident,
        @Nullable SettlementSnapshot settlement,
        long gameTime
) {

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
        long time = this.gameTime % 24000L;
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
}
