package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.ai.military.UnitTypeMatchup;
import com.talhanation.bannermod.ai.military.WeaponReach;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;

final class RecruitCombatDecisions {
    private RecruitCombatDecisions() {
    }

    static boolean shouldAttack(AbstractRecruitEntity recruit, LivingEntity target) {
        return switch (recruit.getState()) {
            case 3 -> false;
            case 0 -> shouldAttackOnNeutral(recruit, target) && recruit.canAttack(target);
            case 1 -> (shouldAttackOnNeutral(recruit, target) || shouldAttackOnAggressive(recruit, target)) && recruit.canAttack(target);
            case 2 -> !RecruitEvents.isAlly(recruit.getTeam(), target.getTeam()) && recruit.canAttack(target);
            default -> recruit.canAttack(target);
        };
    }

    static boolean shouldAttackOnNeutral(AbstractRecruitEntity recruit, LivingEntity target) {
        if (isMonster(target) || isAttackingOwnerOrSelf(recruit, target)) return true;
        if (target instanceof Villager) return false;
        return RecruitEvents.isEnemy(recruit, target);
    }

    static boolean shouldAttackOnAggressive(AbstractRecruitEntity recruit, LivingEntity target) {
        if (target instanceof Villager) return false;
        return (target instanceof AbstractRecruitEntity || target instanceof Player)
                && (RecruitEvents.isNeutral(recruit.getTeam(), target.getTeam()) || RecruitEvents.isEnemy(recruit, target));
    }

    static boolean doHurtTarget(AbstractRecruitEntity recruit, @NotNull Entity entity) {
        return doHurtTarget(recruit, entity, 1.0D);
    }

    static boolean doHurtTarget(AbstractRecruitEntity recruit, @NotNull Entity entity, double damageMultiplier) {
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved() || !shouldAttack(recruit, target)) {
            return false;
        }
        float damage = (float) recruit.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        net.minecraft.world.damagesource.DamageSource source = recruit.damageSources().mobAttack(recruit);
        if (recruit.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, recruit.getMainHandItem(), entity, source, damage);
        }
        if (damageMultiplier > 0.0D && damageMultiplier != 1.0D) {
            damage = (float) (damage * damageMultiplier);
        }
        // Stage 4.D: BannerMod unit-type matchup counters — only against other recruits
        // so PvE / PvP balance against players and monsters is untouched.
        if (entity instanceof AbstractRecruitEntity defender) {
            UnitTypeMatchup.UnitClass attackerClass = classifyUnit(recruit);
            UnitTypeMatchup.UnitClass defenderClass = classifyUnit(defender);
            double mult = UnitTypeMatchup.damageMultiplier(attackerClass, defenderClass);
            if (mult != 1.0D) {
                damage = (float) (damage * mult);
            }
        }
        // COMBAT-004: cavalry-charge first-hit bonus / pike-brace penalty. Applies on the
        // outgoing damage so the multiplier reaches non-recruit targets too (the inbound
        // RecruitCombatOverrideService.applyBraceAgainstCavalryMitigation only fires when the
        // target is a recruit, which would silently drop the bonus against vanilla mobs).
        double chargeMultiplier = com.talhanation.bannermod.combat.CavalryChargeService
                .computeChargeMultiplierFor(recruit, entity);
        if (chargeMultiplier != 1.0D) {
            damage = (float) (damage * chargeMultiplier);
        }
        // COMBAT-001: shaken hit-rate dampening. A SHAKEN squad still swings but at reduced
        // effectiveness — the rout goal handles full disengagement separately, so a routed
        // recruit is not in this code path. Multiplier resolves to 1.0 for STEADY/ROUTED.
        double moraleMultiplier = com.talhanation.bannermod.combat.RecruitMoraleService
                .attackMultiplierFor(recruit);
        if (moraleMultiplier != 1.0D) {
            damage = (float) (damage * moraleMultiplier);
        }
        boolean flag = entity.hurt(source, damage);
        if (flag) {
            if (recruit.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, entity, source);
            }
            recruit.setLastHurtMob(entity);
            // COMBAT-004: a successful melee hit during a CHARGING window transitions the
            // attacker to EXHAUSTED via CavalryChargePolicy.advance — that's the gate that
            // stops the rider from spamming +100% bonus every swing.
            com.talhanation.bannermod.combat.CavalryChargeService.onChargeHit(recruit);
            recruit.addXp(1);
            if (recruit.getHunger() > 0) recruit.setHunger(recruit.getHunger() - 0.1F);
            recruit.checkLevel();
            if (recruit.getMorale() < 100) recruit.setMoral(recruit.getMorale() + 0.25F);
            recruit.damageMainHandItem();
        }
        return flag;
    }

    static boolean isMonster(LivingEntity target) {
        return target instanceof Enemy;
    }

    static boolean isAttackingOwnerOrSelf(AbstractRecruitEntity recruit, LivingEntity target) {
        return target.getLastHurtByMob() != null && (target.getLastHurtByMob().equals(recruit) || target.getLastHurtByMob().equals(recruit.getOwner()));
    }

    /** Stage 4.D: resolve a recruit's effective unit class from its held gear / mount state. */
    static UnitTypeMatchup.UnitClass classifyUnit(AbstractRecruitEntity recruit) {
        boolean mounted = recruit.getVehicle() instanceof LivingEntity;
        ItemStack mainHand = recruit.getMainHandItem();
        double extraReach = mainHand.isEmpty() ? 0.0D : WeaponReach.effectiveReachFor(mainHand.getItem());
        boolean rangedWeapon = !mainHand.isEmpty()
                && (mainHand.getItem() instanceof BowItem
                    || mainHand.getItem() instanceof CrossbowItem
                    || mainHand.getItem() instanceof ProjectileWeaponItem);
        boolean shieldman = recruit instanceof RecruitShieldmanEntity;
        int chestDefense = 0;
        ItemStack chest = recruit.getItemBySlot(EquipmentSlot.CHEST);
        if (chest != null && chest.getItem() instanceof ArmorItem armor) {
            chestDefense = armor.getDefense();
        }
        return UnitTypeMatchup.classify(mounted, extraReach, rangedWeapon, shieldman, chestDefense);
    }

}
