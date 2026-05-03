package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.goal.ResidentTask;

import javax.annotation.Nullable;
import java.util.UUID;

public final class NpcSocietyNeedRuntime {
    private NpcSocietyNeedRuntime() {
    }

    public static NpcSocietyProfile tickNeeds(NpcSocietyProfile profile,
                                              @Nullable UUID homeBuildingUuid,
                                              boolean activePhase,
                                              boolean restPhase,
                                              @Nullable ResidentTask activeTask,
                                              long gameTime) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        int hungerNeed = profile.hungerNeed();
        int fatigueNeed = profile.fatigueNeed();
        int socialNeed = profile.socialNeed();

        if (restPhase) {
            hungerNeed += 1;
            fatigueNeed -= homeBuildingUuid == null ? 1 : 4;
            socialNeed += homeBuildingUuid == null ? 1 : 0;
        } else if (activePhase) {
            hungerNeed += 2;
            fatigueNeed += 2;
            socialNeed += 1;
        } else {
            hungerNeed += 1;
            fatigueNeed += 1;
        }

        NpcIntent activeIntent = activeTask == null ? NpcIntent.UNSPECIFIED : NpcSocietyPhaseOneRuntime.intentForGoal(activeTask.goalId());
        if (activeIntent == NpcIntent.REST || activeIntent == NpcIntent.GO_HOME) {
            fatigueNeed -= 3;
            socialNeed += 0;
        }
        if (activeIntent == NpcIntent.WORK || activeIntent == NpcIntent.FETCH || activeIntent == NpcIntent.DELIVER || activeIntent == NpcIntent.SELL) {
            fatigueNeed += 2;
            hungerNeed += 1;
        }
        if (activeIntent == NpcIntent.SOCIALISE) {
            socialNeed -= 5;
        }
        if (homeBuildingUuid == null) {
            fatigueNeed += 1;
            socialNeed += 1;
        }

        if (profile.lifeStage() == NpcLifeStage.ADOLESCENT) {
            fatigueNeed += activePhase ? 1 : 0;
        }

        return profile.withNeedState(
                clampNeed(hungerNeed),
                clampNeed(fatigueNeed),
                clampNeed(socialNeed),
                gameTime
        );
    }

    private static int clampNeed(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
