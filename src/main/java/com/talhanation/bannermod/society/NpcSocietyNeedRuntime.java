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
                                              boolean underThreat,
                                              boolean canDefend,
                                              long gameTime) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        int hungerNeed = profile.hungerNeed();
        int fatigueNeed = profile.fatigueNeed();
        int socialNeed = profile.socialNeed();
        int safetyNeed = profile.safetyNeed();

        if (restPhase) {
            hungerNeed += 1;
            fatigueNeed -= homeBuildingUuid == null ? 1 : 4;
            socialNeed += homeBuildingUuid == null ? 1 : 0;
            safetyNeed -= homeBuildingUuid == null ? 0 : 3;
        } else if (activePhase) {
            hungerNeed += 2;
            fatigueNeed += 2;
            socialNeed += 1;
            safetyNeed -= 1;
        } else {
            hungerNeed += 1;
            fatigueNeed += 1;
            safetyNeed -= 1;
        }

        NpcIntent activeIntent = activeTask == null ? NpcIntent.UNSPECIFIED : NpcSocietyPhaseOneRuntime.intentForGoal(activeTask.goalId());
        if (NpcSocietyIntentRules.isRestLikeIntent(activeIntent)) {
            fatigueNeed -= 3;
            safetyNeed -= homeBuildingUuid == null ? 1 : 5;
        }
        if (activeIntent == NpcIntent.WORK || activeIntent == NpcIntent.FETCH || activeIntent == NpcIntent.DELIVER || activeIntent == NpcIntent.SELL) {
            fatigueNeed += 2;
            hungerNeed += 1;
        }
        if (activeIntent == NpcIntent.SOCIALISE) {
            socialNeed -= 5;
            safetyNeed -= 2;
        }
        if (activeIntent == NpcIntent.EAT) {
            hungerNeed -= 8;
            safetyNeed -= 1;
        }
        if (activeIntent == NpcIntent.SEEK_SUPPLIES) {
            hungerNeed -= 3;
        }
        if (activeIntent == NpcIntent.DEFEND) {
            safetyNeed -= canDefend ? 6 : 1;
            fatigueNeed += 1;
        }
        if (homeBuildingUuid == null) {
            fatigueNeed += 1;
            socialNeed += 1;
            safetyNeed += 1;
        }

        if (profile.lifeStage() == NpcLifeStage.ADOLESCENT) {
            fatigueNeed += activePhase ? 1 : 0;
        }

        if (underThreat) {
            safetyNeed += canDefend ? 18 : 26;
            socialNeed += canDefend ? 0 : 2;
        }

        return profile.withNeedState(
                clampNeed(hungerNeed),
                clampNeed(fatigueNeed),
                clampNeed(socialNeed),
                clampNeed(safetyNeed),
                gameTime
        );
    }

    private static int clampNeed(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
