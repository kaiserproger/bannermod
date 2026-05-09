package com.talhanation.bannermod.entity.military.perks;

import com.talhanation.bannermod.ai.military.WeaponReach;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.BowmanEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import com.talhanation.bannermod.entity.military.HorsemanEntity;
import com.talhanation.bannermod.entity.military.RecruitShieldmanEntity;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class PerkEffectService {
    private static final ResourceLocation MAX_HEALTH_ID = modifierId("perk_max_health");
    private static final ResourceLocation KNOCKBACK_RESIST_ID = modifierId("perk_knockback_resist");
    private static final ResourceLocation ATTACK_DAMAGE_ID = modifierId("perk_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_ID = modifierId("perk_attack_speed");
    private static final ResourceLocation MOVEMENT_SPEED_ID = modifierId("perk_movement_speed");

    private PerkEffectService() {
    }

    public static void applyRecruitAttributeBonuses(AbstractRecruitEntity recruit) {
        PerkArchetype archetype = recruitArchetype(recruit);
        applyAttributeBonus(recruit, Attributes.MAX_HEALTH, bonus(recruit.getPerkProgress(), PerkStat.MAX_HEALTH, archetype), MAX_HEALTH_ID);
        applyAttributeBonus(recruit, Attributes.KNOCKBACK_RESISTANCE, bonus(recruit.getPerkProgress(), PerkStat.KNOCKBACK_RESIST, archetype), KNOCKBACK_RESIST_ID);
        applyAttributeBonus(recruit, Attributes.ATTACK_DAMAGE, bonus(recruit.getPerkProgress(), PerkStat.ATTACK_DAMAGE, archetype), ATTACK_DAMAGE_ID);
        applyAttributeBonus(recruit, Attributes.ATTACK_SPEED, bonus(recruit.getPerkProgress(), PerkStat.ATTACK_SPEED, archetype), ATTACK_SPEED_ID);
        applyAttributeBonus(recruit, Attributes.MOVEMENT_SPEED, bonus(recruit.getPerkProgress(), PerkStat.MOVEMENT_SPEED, archetype), MOVEMENT_SPEED_ID);
    }

    public static void applyPlayerAttributeBonuses(ServerPlayer player) {
        applyAttributeBonus(player, Attributes.MAX_HEALTH, playerBonus(player, PerkStat.MAX_HEALTH), MAX_HEALTH_ID);
        applyAttributeBonus(player, Attributes.KNOCKBACK_RESISTANCE, playerBonus(player, PerkStat.KNOCKBACK_RESIST), KNOCKBACK_RESIST_ID);
        applyAttributeBonus(player, Attributes.ATTACK_DAMAGE, playerBonus(player, PerkStat.ATTACK_DAMAGE), ATTACK_DAMAGE_ID);
        applyAttributeBonus(player, Attributes.ATTACK_SPEED, playerBonus(player, PerkStat.ATTACK_SPEED), ATTACK_SPEED_ID);
        applyAttributeBonus(player, Attributes.MOVEMENT_SPEED, playerBonus(player, PerkStat.MOVEMENT_SPEED), MOVEMENT_SPEED_ID);
    }

    public static float rangedInaccuracyFor(AbstractRecruitEntity recruit, float baseInaccuracy) {
        return rangedInaccuracy(baseInaccuracy, recruitBonus(recruit, PerkStat.RANGED_ACCURACY));
    }

    public static float rangedVelocityFor(AbstractRecruitEntity recruit, float baseVelocity) {
        return rangedVelocity(baseVelocity, recruitBonus(recruit, PerkStat.RANGED_VELOCITY));
    }

    public static float playerRangedInaccuracyFor(ServerPlayer player, float baseInaccuracy) {
        return rangedInaccuracy(baseInaccuracy, playerBonus(player, PerkStat.RANGED_ACCURACY));
    }

    public static float playerRangedVelocityFor(ServerPlayer player, float baseVelocity) {
        return rangedVelocity(baseVelocity, playerBonus(player, PerkStat.RANGED_VELOCITY));
    }

    public static double recruitBonus(AbstractRecruitEntity recruit, PerkStat stat) {
        return bonus(recruit.getPerkProgress(), stat, recruitArchetype(recruit));
    }

    public static double playerBonus(ServerPlayer player, PerkStat stat) {
        double total = 0.0D;
        for (String id : PlayerPerkProgressService.unlockedPerkIds(player)) {
            PerkNode node = PerkRegistry.get(id).orElse(null);
            if (!PlayerPerkProgressService.isPlayerPerk(node)) {
                continue;
            }
            for (PerkBonus bonus : node.bonuses()) {
                if (bonus.stat() == stat) {
                    total += bonus.amount();
                }
            }
        }
        return total;
    }

    public static double bonus(PerkProgress progress, PerkStat stat, PerkArchetype archetype) {
        double total = 0.0D;
        for (String id : progress.getOwnedPerks()) {
            PerkNode node = PerkRegistry.get(id).orElse(null);
            if (node == null || (node.archetype() != PerkArchetype.UNIVERSAL && node.archetype() != archetype)) {
                continue;
            }
            for (PerkBonus bonus : node.bonuses()) {
                if (bonus.stat() == stat) {
                    total += bonus.amount();
                }
            }
        }
        return total;
    }

    public static PerkArchetype recruitArchetype(AbstractRecruitEntity recruit) {
        if (recruit instanceof BowmanEntity) {
            return PerkArchetype.BOWMAN;
        }
        if (recruit instanceof CrossBowmanEntity) {
            return PerkArchetype.CROSSBOWMAN;
        }
        if (recruit instanceof HorsemanEntity) {
            return PerkArchetype.CAVALRY;
        }
        if (recruit instanceof RecruitShieldmanEntity
                || WeaponReach.effectiveReachFor(recruit.getMainHandItem()) >= WeaponReach.PIKE_EXTRA_REACH) {
            return PerkArchetype.PIKEMAN;
        }
        return PerkArchetype.SWORDSMAN;
    }

    private static void applyAttributeBonus(LivingEntity entity, Holder<Attribute> attribute,
                                            double amount, ResourceLocation modifierId) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(modifierId);
        if (existing != null) {
            if (amount != 0.0D && Double.compare(existing.amount(), amount) == 0) {
                return;
            }
            instance.removeModifier(modifierId);
        }

        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(
                    modifierId,
                    amount,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, path);
    }

    private static float rangedInaccuracy(float baseInaccuracy, double bonus) {
        if (bonus <= 0.0D || baseInaccuracy <= 0.0F) {
            return baseInaccuracy;
        }
        return (float) Math.max(0.0D, baseInaccuracy * Math.max(0.0D, 1.0D - bonus));
    }

    private static float rangedVelocity(float baseVelocity, double bonus) {
        if (bonus <= 0.0D || baseVelocity <= 0.0F) {
            return baseVelocity;
        }
        return (float) (baseVelocity * (1.0D + bonus));
    }
}
