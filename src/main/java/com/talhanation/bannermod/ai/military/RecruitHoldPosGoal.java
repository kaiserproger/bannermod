package com.talhanation.bannermod.ai.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.util.FormationDimensionGuard;
import com.talhanation.bannermod.util.FormationFallbackPlanner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RecruitHoldPosGoal extends Goal {
    /** Step 1.C: minimum gap between gap-fill migrations by the same recruit. */
    public static final int GAP_FILL_PER_RECRUIT_COOLDOWN_TICKS = 60;
    /** Step 1.C: tick period between gap-fill scans (staggered per recruit). */
    public static final int GAP_FILL_SCAN_PERIOD_TICKS = 20;

    private final AbstractRecruitEntity recruit;

    private int timeToRecalcPath;
    private int formationFallbackCooldown;

    public RecruitHoldPosGoal(AbstractRecruitEntity recruit, double within) {
      this.recruit = recruit;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public void start() {
        super.start();
        timeToRecalcPath = 0;
        formationFallbackCooldown = 0;
    }

    public boolean canUse() {
        if (this.recruit.getHoldPos() == null) {
            return false;
        }
        else
            return this.recruit.getShouldHoldPos() && !recruit.getFleeing() && !recruit.needsToGetFood() && !recruit.getShouldMount();
    }

    public boolean canContinueToUse() {
        return canUse();
    }

    public void tick() {
        LivingEntity leader = this.recruit.getOwner();
        if (FormationDimensionGuard.shouldHoldDueToDimensionMismatch(this.recruit, leader)) {
            this.recruit.getNavigation().stop();
            return;
        }

        if (this.formationFallbackCooldown > 0) {
            this.formationFallbackCooldown--;
        }

        // Step 1.C: staggered gap-fill scan when stance is LINE_HOLD / SHIELD_WALL.
        tryGapFillScan();

        Vec3 pos = this.recruit.getHoldPos();
        if (pos != null) {
            double distance = recruit.distanceToSqr(pos);
            if(distance >= 0.3) {
                if (--this.timeToRecalcPath <= 0) {
                    this.timeToRecalcPath = this.recruit.getVehicle() != null ? this.adjustedTickDelay(5) : this.adjustedTickDelay(10);
                    this.recruit.getNavigation().moveTo(pos.x(), pos.y(), pos.z(), formationMoveSpeed());
                }

                if (recruit.horizontalCollision || recruit.minorHorizontalCollision) {
                    this.recruit.getJumpControl().jump();
                }

                if (this.formationFallbackCooldown <= 0
                        && this.recruit.isInFormation
                        && this.recruit.getFollowState() == 3
                        && (this.recruit.getNavigation().isStuck() || this.recruit.horizontalCollision || this.recruit.minorHorizontalCollision)
                        && FormationFallbackPlanner.tryFallbackToNearestFreeSlot(this.recruit)) {
                    this.formationFallbackCooldown = this.adjustedTickDelay(20);
                    Vec3 fallbackPos = this.recruit.getHoldPos();
                    if (fallbackPos != null) {
                        this.recruit.getNavigation().moveTo(fallbackPos.x(), fallbackPos.y(), fallbackPos.z(), formationMoveSpeed());
                    }
                }
            } else{
            }
        }
    }

    private void tryGapFillScan() {
        if (!this.recruit.isInFormation || this.recruit.getFollowState() != 3) {
            return;
        }
        // FORMATIONDIM-001: do not migrate formation slots while leader is in another dimension.
        CombatStance stance = this.recruit.getCombatStance();
        if (!FormationGapFillPolicy.stanceAllowsGapFill(stance)) {
            return;
        }
        int offset = Math.floorMod(this.recruit.getUUID().hashCode(), GAP_FILL_SCAN_PERIOD_TICKS);
        if ((this.recruit.tickCount + offset) % GAP_FILL_SCAN_PERIOD_TICKS != 0) {
            return;
        }
        if (this.recruit.tickCount - this.recruit.lastFormationGapFillTick < GAP_FILL_PER_RECRUIT_COOLDOWN_TICKS) {
            return;
        }
        if (FormationFallbackPlanner.tryFillForwardGap(this.recruit)) {
            this.recruit.lastFormationGapFillTick = this.recruit.tickCount;
            Vec3 newHold = this.recruit.getHoldPos();
            if (newHold != null) {
                this.recruit.getNavigation().moveTo(newHold.x(), newHold.y(), newHold.z(), formationMoveSpeed());
            }
        }
    }

    private double formationMoveSpeed() {
        if (this.recruit.isInFormation && this.recruit.getCombatStance() == CombatStance.SHIELD_WALL) {
            return this.recruit.moveSpeed * 0.55D;
        }
        return this.recruit.moveSpeed;
    }
}
