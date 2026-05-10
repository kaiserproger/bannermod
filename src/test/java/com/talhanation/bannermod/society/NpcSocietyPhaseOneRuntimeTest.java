package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.dispatch.SellerResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.DeliverResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.FetchResidentGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcSocietyPhaseOneRuntimeTest {
    @Test
    void taskSpecificWorkGoalsKeepDistinctPublishedIntents() {
        assertEquals(NpcIntent.SELL, NpcSocietyPhaseOneRuntime.intentForGoal(SellerResidentGoal.ID));
        assertEquals(NpcIntent.FETCH, NpcSocietyPhaseOneRuntime.intentForGoal(FetchResidentGoal.ID));
        assertEquals(NpcIntent.DELIVER, NpcSocietyPhaseOneRuntime.intentForGoal(DeliverResidentGoal.ID));
    }
}
