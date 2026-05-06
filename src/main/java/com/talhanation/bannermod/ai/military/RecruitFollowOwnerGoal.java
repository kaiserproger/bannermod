package com.talhanation.bannermod.ai.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;

import com.talhanation.bannermod.entity.military.CaptainEntity;
import com.talhanation.bannermod.util.FormationDimensionGuard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.vehicle.Boat;


public class RecruitFollowOwnerGoal extends Goal {
    private final AbstractRecruitEntity recruit;
    private LivingEntity owner;
    private final double speedModifier;
    private int timeToRecalcPath;
    private final float stopDistance;
    private final float startDistance;
    private long lastCanUseCheck;

    public RecruitFollowOwnerGoal(AbstractRecruitEntity recruit, double speedModifier, float startDistance, float stopDistance) {
        this.recruit = recruit;
        this.speedModifier = speedModifier;
        this.startDistance = 9;
        this.stopDistance = 7;
    }

    public boolean canUse() {
        long i = this.recruit.getCommandSenderWorld().getGameTime();
        if (i - this.lastCanUseCheck >= 10L) {
            this.lastCanUseCheck = i;
            LivingEntity livingentity = this.recruit.getOwner();
            LivingEntity target = this.recruit.getTarget();
            double start = target == null ? startDistance : startDistance + 100;
            if (livingentity == null) {
                return false;
            }
            // FORMATIONDIM-001: refuse to follow into another dimension; hold position instead.
            if (FormationDimensionGuard.shouldHoldDueToDimensionMismatch(this.recruit, livingentity)) {
                return false;
            }
            else if (livingentity.isSpectator()) {
                return false;
            }
            else if (this.recruit.distanceToSqr(livingentity) < start) {
                return false;
            }
            else {
                this.owner = livingentity;
                return this.recruit.getShouldFollow() && !recruit.getFleeing() && recruit.getFollowState() == 1 && !recruit.needsToGetFood() && !recruit.getShouldMount() && !recruit.getShouldMovePos();
            }
        }
        return false;

    }

    public boolean canContinueToUse() {
        if (this.recruit.getNavigation().isDone()) {
            return false;
        }
        LivingEntity liveOwner = this.recruit.getOwner();
        if (liveOwner == null || !liveOwner.isAlive() || liveOwner.isRemoved()) {
            return false;
        }
        this.owner = liveOwner;
        // FORMATIONDIM-001: stop the follow as soon as the leader crosses dimensions.
        if (FormationDimensionGuard.shouldHoldDueToDimensionMismatch(this.recruit, this.owner)) {
            return false;
        }
        return !(this.recruit.distanceToSqr(this.owner) <= this.stopDistance) && this.recruit.getShouldFollow() && !recruit.getFleeing() && recruit.getFollowState() == 1 && !recruit.needsToGetFood() && !recruit.getShouldMount() && !recruit.getShouldMovePos();
    }

    public void start() {
        this.timeToRecalcPath = 0;
        this.recruit.setIsFollowing(true);
    }

    public void stop() {
        super.stop();
        this.owner = null;
        this.recruit.setIsFollowing(false);
        this.recruit.getNavigation().stop();
    }

    public void tick() {
        LivingEntity liveOwner = this.recruit.getOwner();
        if (liveOwner == null || !liveOwner.isAlive() || liveOwner.isRemoved()) {
            this.recruit.getNavigation().stop();
            return;
        }
        this.owner = liveOwner;
        // FORMATIONDIM-001: belt-and-braces — bail out if leader crossed dimensions mid-tick.
        if (FormationDimensionGuard.shouldHoldDueToDimensionMismatch(this.recruit, this.owner)) {
            this.recruit.getNavigation().stop();
            return;
        }
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.recruit.getVehicle() != null ? this.adjustedTickDelay(5) : this.adjustedTickDelay(10);
            this.recruit.getNavigation().moveTo(this.owner, this.speedModifier);

            if(this.recruit instanceof CaptainEntity captain && captain.getVehicle() instanceof Boat){
                captain.setSailPos(this.owner.getOnPos());
            }
        }
    }
}
