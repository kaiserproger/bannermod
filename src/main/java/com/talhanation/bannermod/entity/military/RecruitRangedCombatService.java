package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.perks.PerkEffectService;
import com.talhanation.bannermod.util.AttackUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.function.BooleanSupplier;

public final class RecruitRangedCombatService {
    private RecruitRangedCombatService() {
    }

    public static boolean canUseRangedTarget(AbstractRecruitEntity recruit, LivingEntity target, double stopRange, BooleanSupplier canAttackMovePos) {
        return target != null
                && target.isAlive()
                && recruit.getShouldRanged()
                && target.distanceTo(recruit) >= stopRange
                && canAttackMovePos.getAsBoolean()
                && !recruit.needsToGetFood()
                && recruit.canAttack(target)
                && recruit.getState() != 3
                && !recruit.getShouldMount();
    }

    public static boolean hasArrowAmmo(AbstractRecruitEntity recruit, boolean consumeArrows) {
        return !consumeArrows || recruit.getInventory().hasAnyMatching(item -> item.is(ItemTags.ARROWS));
    }

    public static LivingEntity resolveMountedTarget(AbstractRecruitEntity shooter, LivingEntity target) {
        if (AttackUtil.canPerformHorseAttack(shooter, target) && target.getVehicle() instanceof LivingEntity vehicle) {
            return vehicle;
        }
        return target;
    }

    public static void fireBowAtTarget(BowmanEntity shooter, LivingEntity target, float drawPower) {
        LivingEntity resolvedTarget = resolveMountedTarget(shooter, target);
        ItemStack projectileStack = shooter.getProjectile(shooter.getItemInHand(InteractionHand.MAIN_HAND));
        if (projectileStack.isEmpty()) {
            projectileStack = Items.ARROW.getDefaultInstance();
        }
        AbstractArrow arrow = ProjectileUtil.getMobArrow(shooter, projectileStack, drawPower, shooter.getMainHandItem());
        applyBowEnchantments(shooter, projectileStack, arrow, true);

        double distance = shooter.distanceToSqr(resolvedTarget.getX(), resolvedTarget.getY(), resolvedTarget.getZ());
        double heightDiff = resolvedTarget.getY() - shooter.getY();
        double d0 = resolvedTarget.getX() - shooter.getX();
        double d1 = resolvedTarget.getY() - arrow.getY() + resolvedTarget.getEyeHeight();
        double d2 = resolvedTarget.getZ() - shooter.getZ();
        double d3 = Mth.sqrt((float) (d0 * d0 + d2 * d2));

        double angle = IRangedRecruit.getAngleDistanceModifier(distance, 47, 4) + IRangedRecruit.getAngleHeightModifier(distance, heightDiff, 1.00D) / 100;
        float force = 1.90F + IRangedRecruit.getForceDistanceModifier(distance, 1.90F);
        double morale = shooter.getMorale();
        force = PerkEffectService.rangedVelocityFor(shooter, force);
        float accuracy = PerkEffectService.rangedInaccuracyFor(shooter, Math.max(6 - (float) (0.1F * morale), 0));
        arrow.shoot(d0, d1 + d3 * angle, d2, force, accuracy);

        finishBowShot(shooter, arrow, true);
    }

    public static void fireBowAtPosition(BowmanEntity shooter, double x, double y, double z, float drawPower, float angle, float force) {
        ItemStack projectileStack = shooter.getProjectile(shooter.getItemInHand(InteractionHand.MAIN_HAND));
        if (projectileStack.isEmpty()) {
            projectileStack = Items.ARROW.getDefaultInstance();
        }
        AbstractArrow arrow = ProjectileUtil.getMobArrow(shooter, projectileStack, drawPower, shooter.getMainHandItem());
        applyBowEnchantments(shooter, projectileStack, arrow, false);

        double d0 = x - shooter.getX();
        double d1 = y - shooter.getY();
        double d2 = z - shooter.getZ();
        double d3 = Mth.sqrt((float) (d0 * d0 + d2 * d2));
        double morale = shooter.getMorale();
        float adjustedForce = PerkEffectService.rangedVelocityFor(shooter, force + 1.95F);
        float accuracy = PerkEffectService.rangedInaccuracyFor(shooter, 3F + Math.max(6 - (float) (0.1F * morale), 0));
        arrow.shoot(d0, d1 + d3 + angle, d2, adjustedForce, accuracy);

        finishBowShot(shooter, arrow, false);
    }

    private static void applyBowEnchantments(BowmanEntity shooter, ItemStack projectileStack, AbstractArrow arrow, boolean alwaysApplyBaseBonus) {
        int powerLevel = getEnchantmentLevel(shooter, Enchantments.POWER, projectileStack);
        if (alwaysApplyBaseBonus || powerLevel > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + (double) powerLevel * 0.5D + 0.5D + shooter.arrowDamageModifier());
        }

        int fireLevel = getEnchantmentLevel(shooter, Enchantments.FLAME, projectileStack);
        if (fireLevel > 0) {
            arrow.igniteForSeconds(5.0F);
        }
    }

    private static void finishBowShot(BowmanEntity shooter, AbstractArrow arrow, boolean honorInfinity) {
        if (RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get()) {
            int infinityLevel = getEnchantmentLevel(shooter, Enchantments.INFINITY, shooter.getMainHandItem());
            if (!honorInfinity || infinityLevel == 0) {
                shooter.consumeArrow();
                arrow.pickup = AbstractArrow.Pickup.ALLOWED;
            }
        }

        shooter.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (shooter.getRandom().nextFloat() * 0.4F + 0.8F));
        shooter.getCommandSenderWorld().addFreshEntity(arrow);
        shooter.damageMainHandItem();
    }

    private static int getEnchantmentLevel(LivingEntity shooter, ResourceKey<Enchantment> enchantment, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                shooter.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(enchantment),
                stack
        );
    }
}
