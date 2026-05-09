package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.ai.military.BraceAgainstChargePolicy;
import com.talhanation.bannermod.ai.military.CombatStance;
import com.talhanation.bannermod.ai.military.FacingHitZone;
import com.talhanation.bannermod.ai.military.FlankDamage;
import com.talhanation.bannermod.ai.military.FormationCohesion;
import com.talhanation.bannermod.ai.military.FormationSlotRegistry;
import com.talhanation.bannermod.ai.military.FormationTargetSelectionController;
import com.talhanation.bannermod.ai.military.ShieldBlockGeometry;
import com.talhanation.bannermod.ai.military.ShieldMitigation;
import com.talhanation.bannermod.combat.FormationPlanner;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RecruitCombatOverrideService {

    /** Ticks of shield stagger applied after a successful directional block. */
    private static final int BLOCK_STAGGER_COOLDOWN_TICKS = 100;
    /** Extra mitigation multiplier for shieldman-class recruits (stacked onto stance). */
    private static final float SHIELDMAN_BONUS_REMAINING = 0.9f;
    /** Knockback strength applied to attackers whose melee blow is blocked at the front. */
    private static final float BLOCK_KNOCKBACK_STRENGTH = 0.5f;
    /** Stage 4.B: cohesion-check cache TTL, in ticks. */
    private static final int COHESION_CACHE_TTL_TICKS = 10;
    /** Minimum ticks between protect-target fanout scans for the same damaged recruit. */
    private static final int PROTECT_TARGET_PROPAGATION_INTERVAL_TICKS = 5;

    private RecruitCombatOverrideService() {
    }

    static float prepareIncomingDamage(AbstractRecruitEntity recruit, DamageSource damageSource, float damage) {
        Entity attacker = damageSource.getEntity();
        if (attacker != null && !(attacker instanceof Player) && !(attacker instanceof AbstractArrow)) {
            damage = (damage + 1.0F) / 2.0F;
        }

        if (recruit.getMorale() > 0) recruit.setMoral(recruit.getMorale() - 0.25F);

        damage = applyShieldMitigation(recruit, damageSource, damage);

        // Stage 4.A: flank / back vulnerability. Only a direct LivingEntity hit counts —
        // fall damage, magic, etc. should not benefit (or suffer) from facing.
        damage = applyFlankMultiplier(recruit, damageSource, damage);

        // Stage 4.C: bracing recruits take ×0.7 against cavalry riders (after flank).
        damage = applyBraceAgainstCavalryMitigation(recruit, damageSource, damage);

        // Stage 4.B: phalanx cohesion bonus — final multiplier.
        damage = applyFormationCohesionMitigation(recruit, damage);

        if (recruit.isBlocking()) recruit.hurtCurrentlyUsedShield(damage);

        if (attacker instanceof LivingEntity living && RecruitEvents.canAttack(recruit, living)) {
            propagateProtectTarget(recruit, living);
            recruit.assignReactiveCombatTarget(living);

            if (recruit.getShouldProtect() && recruit.getProtectingMob() instanceof AbstractRecruitEntity patrolLeader) {
                patrolLeader.assignReactiveCombatTarget(living);
            }
        }

        return damage;
    }

    /** Stage 4.A: apply a damage multiplier for side / back hits by a living attacker. */
    private static float applyFlankMultiplier(AbstractRecruitEntity recruit, DamageSource damageSource, float damage) {
        if (damage <= 0f) {
            return damage;
        }
        Entity direct = damageSource.getDirectEntity();
        Entity attacker = damageSource.getEntity();
        if (!(attacker instanceof LivingEntity) && !(direct instanceof LivingEntity)) {
            return damage;
        }
        double[] origin = attackerOrigin(recruit, damageSource);
        if (origin == null) {
            return damage;
        }
        FacingHitZone zone = FacingHitZone.classify(
                recruit.yBodyRot,
                origin[0], origin[1],
                recruit.getX(), recruit.getZ()
        );
        float multiplier = FlankDamage.multiplierFor(zone);
        if (multiplier == 1.0f) {
            return damage;
        }
        return damage * multiplier;
    }

    /** Stage 4.C: extra damage reduction vs a charging cavalry rider while bracing. */
    private static float applyBraceAgainstCavalryMitigation(AbstractRecruitEntity recruit, DamageSource damageSource, float damage) {
        if (damage <= 0f || !recruit.isBracing) {
            return damage;
        }
        Entity attacker = damageSource.getEntity();
        if (attacker == null) {
            return damage;
        }
        boolean attackerMounted = attacker.isPassenger() && attacker.getVehicle() instanceof LivingEntity;
        if (!attackerMounted) {
            return damage;
        }
        return damage * BraceAgainstChargePolicy.BRACE_CAVALRY_REMAINING;
    }

    /** Stage 4.B: apply phalanx cohesion reduction with a short cache. */
    private static float applyFormationCohesionMitigation(AbstractRecruitEntity recruit, float damage) {
        if (damage <= 0f) {
            return damage;
        }
        CombatStance stance = recruit.getCombatStance();
        if (stance != CombatStance.LINE_HOLD && stance != CombatStance.SHIELD_WALL) {
            return damage;
        }
        if (isIsolatedFromFormationSlot(recruit)) {
            return damage;
        }
        boolean cohesive;
        int now = recruit.tickCount;
        if (recruit.cachedCohesionTick != Integer.MIN_VALUE
                && (now - recruit.cachedCohesionTick) < COHESION_CACHE_TTL_TICKS) {
            cohesive = recruit.cachedCohesion;
        } else {
            cohesive = computeCohesion(recruit, stance);
            recruit.cachedCohesion = cohesive;
            recruit.cachedCohesionTick = now;
        }
        if (!cohesive) {
            return damage;
        }
        return damage * FormationCohesion.COHESION_REMAINING;
    }

    private static boolean computeCohesion(AbstractRecruitEntity recruit, CombatStance stance) {
        UUID ownerId = recruit.getOwnerUUID();
        UUID groupId = recruit.getGroup();
        if (ownerId == null || groupId == null) {
            return false;
        }
        FormationTargetSelectionController.CohortKey cohort =
                new FormationTargetSelectionController.CohortKey(ownerId, groupId);
        Map<Integer, FormationSlotRegistry.SlotEntry> slots = FormationSlotRegistry.slotsOf(cohort);
        if (slots.isEmpty()) {
            return false;
        }
        Level level = recruit.level();
        List<FormationCohesion.AllyObservation> allies = new ArrayList<>(slots.size());
        for (FormationSlotRegistry.SlotEntry entry : slots.values()) {
            if (entry == null) continue;
            UUID ownerUuid = entry.ownerId();
            if (ownerUuid == null || ownerUuid.equals(recruit.getUUID())) {
                continue;
            }
            Entity e = resolveEntityByUuid(level, ownerUuid);
            if (!(e instanceof AbstractRecruitEntity ally) || !ally.isAlive()) {
                continue;
            }
            allies.add(new FormationCohesion.AllyObservation(
                    ally.getX() - recruit.getX(),
                    ally.getZ() - recruit.getZ(),
                    ally.getCombatStance()
            ));
        }
        return FormationCohesion.isCohesive(allies, stance, FormationCohesion.DEFAULT_MAX_DIST_SQR);
    }

    private static Entity resolveEntityByUuid(Level level, UUID uuid) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(uuid);
        }
        // Client-side or test: return null; cohesion is a server-only gate anyway.
        return null;
    }

    /**
     * Step 2.A/B: directional shield mitigation with stance-driven reduction and
     * BannerMod stagger cooldown on a successful block.
     */
    private static float applyShieldMitigation(AbstractRecruitEntity recruit, DamageSource damageSource, float damage) {
        if (damage <= 0f) {
            return damage;
        }
        if (!recruit.isBlocking() && !recruit.getShouldBlock()) {
            return damage;
        }
        if (!isBlockableDamageSource(damageSource)) {
            return damage;
        }
        double[] origin = attackerOrigin(recruit, damageSource);
        if (origin == null) {
            return damage;
        }
        boolean inCone = ShieldBlockGeometry.isInFrontCone(
                recruit.yBodyRot,
                recruit.getX(), recruit.getZ(),
                origin[0], origin[1],
                ShieldBlockGeometry.FRONT_CONE_HALF_DEG
        );
        if (!inCone) {
            return damage;
        }

        CombatStance stance = recruit.getCombatStance();
        if ((stance == CombatStance.LINE_HOLD || stance == CombatStance.SHIELD_WALL) && isIsolatedFromFormationSlot(recruit)) {
            stance = CombatStance.LOOSE;
        }
        boolean staggered = recruit.blockCoolDown > 0;
        float mitigated = ShieldMitigation.damageAfterBlock(stance, damage, true, true, staggered);
        if (recruit instanceof RecruitShieldmanEntity) {
            mitigated *= SHIELDMAN_BONUS_REMAINING;
        }

        // Step 2.B: bump stagger cooldown on successful block. Take max so an existing
        // higher cooldown (e.g. from a disabled shield) is not reduced.
        recruit.blockCoolDown = Math.max(recruit.blockCoolDown, BLOCK_STAGGER_COOLDOWN_TICKS);

        // Step 2.E: light knockback on melee attackers whose blow was blocked.
        Entity direct = damageSource.getDirectEntity();
        if (direct instanceof LivingEntity livingDirect && !(direct instanceof AbstractArrow)) {
            float yawRad = recruit.yBodyRot * ((float) Math.PI / 180F);
            livingDirect.knockback(BLOCK_KNOCKBACK_STRENGTH, Mth.sin(yawRad), -Mth.cos(yawRad));
        }

        return mitigated;
    }

    private static boolean isIsolatedFromFormationSlot(AbstractRecruitEntity recruit) {
        UUID ownerId = recruit.getOwnerUUID();
        UUID groupId = recruit.getGroup();
        if (ownerId == null || groupId == null) {
            return false;
        }
        Map<Integer, FormationSlotRegistry.SlotEntry> slots = FormationSlotRegistry.slotsOf(
                new FormationTargetSelectionController.CohortKey(ownerId, groupId)
        );
        for (FormationSlotRegistry.SlotEntry entry : slots.values()) {
            if (entry != null && recruit.getUUID().equals(entry.ownerId()) && entry.holdPos() != null) {
                return FormationPlanner.isIsolated(recruit.position().distanceTo(entry.holdPos()));
            }
        }
        return false;
    }

    private static boolean isBlockableDamageSource(DamageSource damageSource) {
        if (damageSource == null) {
            return false;
        }
        if (damageSource.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        if (damageSource.is(DamageTypeTags.IS_FALL)
                || damageSource.is(DamageTypeTags.IS_DROWNING)
                || damageSource.is(DamageTypeTags.IS_FIRE)
                || damageSource.is(DamageTypeTags.IS_FREEZING)
                || damageSource.is(DamageTypes.IN_WALL)
                || damageSource.is(DamageTypes.STARVE)
                || damageSource.is(DamageTypes.MAGIC)
                || damageSource.is(DamageTypes.WITHER)
                || damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return false;
        }
        if (damageSource.is(DamageTypeTags.IS_PROJECTILE)) {
            return true;
        }
        Entity direct = damageSource.getDirectEntity();
        Entity attacker = damageSource.getEntity();
        return direct != null && attacker instanceof LivingEntity;
    }

    private static double[] attackerOrigin(AbstractRecruitEntity recruit, DamageSource damageSource) {
        Entity direct = damageSource.getDirectEntity();
        if (direct != null && direct != recruit) {
            return new double[]{direct.getX(), direct.getZ()};
        }
        Entity attacker = damageSource.getEntity();
        if (attacker != null && attacker != recruit) {
            return new double[]{attacker.getX(), attacker.getZ()};
        }
        return null;
    }

    static boolean handleKillRewards(AbstractRecruitEntity recruit, LivingEntity victim) {
        recruit.addXp(5);
        recruit.setKills(recruit.getKills() + 1);
        if (recruit.getMorale() < 100) recruit.setMoral(recruit.getMorale() + 1);

        if (victim instanceof Player) {
            recruit.addXp(45);
            if (recruit.getMorale() < 100) recruit.setMoral(recruit.getMorale() + 9);
        }

        if (victim instanceof Raider) {
            recruit.addXp(5);
            if (recruit.getMorale() < 100) recruit.setMoral(recruit.getMorale() + 2);
        }

        if (victim instanceof Villager villager) {
            if (villager.isBaby()) {
                if (recruit.getMorale() > 0) recruit.setMoral(recruit.getMorale() - 10);
            }
            else if (recruit.getMorale() > 0) {
                recruit.setMoral(recruit.getMorale() - 2);
            }
        }

        if (victim instanceof WitherBoss) {
            recruit.addXp(99);
            if (recruit.getMorale() < 100) recruit.setMoral(recruit.getMorale() + 9);
        }

        if (victim instanceof IronGolem) {
            recruit.addXp(49);
            if (recruit.getMorale() > 0) recruit.setMoral(recruit.getMorale() - 1);
        }

        if (victim instanceof EnderDragon) {
            recruit.addXp(999);
            if (recruit.getMorale() < 100) recruit.setMoral(recruit.getMorale() + 49);
        }

        recruit.checkLevel();
        return true;
    }

    private static void propagateProtectTarget(AbstractRecruitEntity recruit, LivingEntity attacker) {
        if (recruit.getFollowState() != 5) {
            return;
        }
        int tick = recruit.tickCount;
        if (recruit.lastProtectTargetPropagationTick != Integer.MIN_VALUE
                && tick - recruit.lastProtectTargetPropagationTick < PROTECT_TARGET_PROPAGATION_INTERVAL_TICKS) {
            return;
        }
        recruit.lastProtectTargetPropagationTick = tick;

        java.util.List<AbstractRecruitEntity> nearby = RecruitIndex.instance().allInBox(
                recruit.getCommandSenderWorld(),
                recruit.getBoundingBox().inflate(32D),
                true);
        if (nearby == null) {
            nearby = recruit.getCommandSenderWorld().getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    recruit.getBoundingBox().inflate(32D));
        }
        for (AbstractRecruitEntity nearbyRecruit : nearby) {
            if (nearbyRecruit.getUUID().equals(nearbyRecruit.getProtectUUID()) && nearbyRecruit.isAlive() && !nearbyRecruit.equals(attacker)) {
                nearbyRecruit.assignReactiveCombatTarget(attacker);
            }
        }
    }
}
