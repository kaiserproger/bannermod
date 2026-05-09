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
        applyAttributeBonus(recruit, archetype, Attributes.MAX_HEALTH, PerkStat.MAX_HEALTH, MAX_HEALTH_ID);
        applyAttributeBonus(recruit, archetype, Attributes.KNOCKBACK_RESISTANCE, PerkStat.KNOCKBACK_RESIST, KNOCKBACK_RESIST_ID);
        applyAttributeBonus(recruit, archetype, Attributes.ATTACK_DAMAGE, PerkStat.ATTACK_DAMAGE, ATTACK_DAMAGE_ID);
        applyAttributeBonus(recruit, archetype, Attributes.ATTACK_SPEED, PerkStat.ATTACK_SPEED, ATTACK_SPEED_ID);
        applyAttributeBonus(recruit, archetype, Attributes.MOVEMENT_SPEED, PerkStat.MOVEMENT_SPEED, MOVEMENT_SPEED_ID);
    }

    public static float rangedInaccuracyFor(AbstractRecruitEntity recruit, float baseInaccuracy) {
        double bonus = recruitBonus(recruit, PerkStat.RANGED_ACCURACY);
        if (bonus <= 0.0D || baseInaccuracy <= 0.0F) {
            return baseInaccuracy;
        }
        return (float) Math.max(0.0D, baseInaccuracy * Math.max(0.0D, 1.0D - bonus));
    }

    public static float rangedVelocityFor(AbstractRecruitEntity recruit, float baseVelocity) {
        double bonus = recruitBonus(recruit, PerkStat.RANGED_VELOCITY);
        if (bonus <= 0.0D || baseVelocity <= 0.0F) {
            return baseVelocity;
        }
        return (float) (baseVelocity * (1.0D + bonus));
    }

    public static double recruitBonus(AbstractRecruitEntity recruit, PerkStat stat) {
        return bonus(recruit.getPerkProgress(), stat, recruitArchetype(recruit));
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

    private static void applyAttributeBonus(AbstractRecruitEntity recruit, PerkArchetype archetype,
                                            Holder<Attribute> attribute, PerkStat stat, ResourceLocation modifierId) {
        AttributeInstance instance = recruit.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        double amount = bonus(recruit.getPerkProgress(), stat, archetype);
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
}
