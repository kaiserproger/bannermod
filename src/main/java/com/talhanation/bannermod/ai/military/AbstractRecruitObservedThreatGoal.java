package com.talhanation.bannermod.ai.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import javax.annotation.Nullable;

abstract class AbstractRecruitObservedThreatGoal extends TargetGoal {
    protected final AbstractRecruitEntity recruit;
    @Nullable
    private LivingEntity observedTarget;
    private int timestamp;

    protected AbstractRecruitObservedThreatGoal(AbstractRecruitEntity recruit) {
        super(recruit, false);
        this.recruit = recruit;
    }

    @Override
    public boolean canUse() {
        LivingEntity observer = getObservedObserver();
        if (observer == null) {
            return false;
        }

        LivingEntity target = getObservedTarget(observer);
        int observedTimestamp = getObservedTimestamp(observer);
        if (target == null || observedTimestamp == this.timestamp) {
            return false;
        }

        if (requiresActiveCombatState() && this.recruit.getState() == 3) {
            return false;
        }

        if (!this.canAttack(target, TargetingConditions.DEFAULT) || !RecruitEvents.canAttack(observer, target)) {
            return false;
        }

        this.observedTarget = target;
        return true;
    }

    @Override
    public void start() {
        if (this.observedTarget != null) {
            this.recruit.assignReactiveCombatTarget(this.observedTarget);
            afterTargetAssigned(this.observedTarget);
        }

        LivingEntity observer = getObservedObserver();
        if (observer != null) {
            this.timestamp = getObservedTimestamp(observer);
        }

        super.start();
    }

    protected boolean requiresActiveCombatState() {
        return true;
    }

    protected void afterTargetAssigned(LivingEntity target) {
    }

    protected abstract @Nullable LivingEntity getObservedObserver();

    protected abstract @Nullable LivingEntity getObservedTarget(LivingEntity observer);

    protected abstract int getObservedTimestamp(LivingEntity observer);
}
