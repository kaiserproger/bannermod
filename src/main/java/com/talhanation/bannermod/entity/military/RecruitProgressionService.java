package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.api.event.RecruitEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.common.NeoForge;

final class RecruitProgressionService {
    private RecruitProgressionService() {
    }

    /** Skill-points granted to a recruit each time it gains a level. SKILLTREE-002 phase 1. */
    static final int PERK_POINTS_PER_LEVEL = 1;

    static void addXpLevel(AbstractRecruitEntity recruit, int level) {
        int currentLevel = recruit.getXpLevel();
        int newLevel = currentLevel + level;
        if (newLevel > RecruitsServerConfig.RecruitsMaxXpLevel.get()) {
            newLevel = RecruitsServerConfig.RecruitsMaxXpLevel.get();
        } else {
            recruit.makeLevelUpSound();
            applyLevelBuffs(recruit);
        }
        int gained = Math.max(0, newLevel - currentLevel);
        if (gained > 0) {
            recruit.getPerkProgress().grantPoints(gained * PERK_POINTS_PER_LEVEL);
        }
        recruit.setXpLevel(newLevel);
    }

    static void applyLevelBuffs(AbstractRecruitEntity recruit) {
        int level = recruit.getXpLevel();
        if (level <= 10) {
            recruit.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "heath_bonus_level"), 2D, AttributeModifier.Operation.ADD_VALUE));
            recruit.getAttribute(Attributes.ATTACK_DAMAGE).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "attack_bonus_level"), 0.03D, AttributeModifier.Operation.ADD_VALUE));
            recruit.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "knockback_bonus_level"), 0.0012D, AttributeModifier.Operation.ADD_VALUE));
            recruit.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "speed_bonus_level"), 0.0025D, AttributeModifier.Operation.ADD_VALUE));
        }
        if (level > 10) {
            recruit.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "heath_bonus_level"), 2D, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    static void applyLevelBuffsForLevel(AbstractRecruitEntity recruit, int level) {
        for (int i = 0; i < level; i++) {
            if (level <= 10) {
                recruit.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "heath_bonus_level"), 2D, AttributeModifier.Operation.ADD_VALUE));
                recruit.getAttribute(Attributes.ATTACK_DAMAGE).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "attack_bonus_level"), 0.03D, AttributeModifier.Operation.ADD_VALUE));
                recruit.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "knockback_bonus_level"), 0.0012D, AttributeModifier.Operation.ADD_VALUE));
                recruit.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "speed_bonus_level"), 0.0025D, AttributeModifier.Operation.ADD_VALUE));
            }
            if (level > 10) {
                recruit.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "heath_bonus_level"), 2D, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    static void checkLevel(AbstractRecruitEntity recruit) {
        int currentXp = recruit.getXp();
        if (currentXp >= RecruitsServerConfig.RecruitsMaxXpForLevelUp.get()) {
            addXpLevel(recruit, 1);
            recruit.setXp(0);
            recruit.heal(10F);
            recalculateCost(recruit);
            if (recruit.getMorale() < 100) recruit.setMoral(recruit.getMorale() + 5F);
            if (!recruit.getCommandSenderWorld().isClientSide()) {
                NeoForge.EVENT_BUS.post(new RecruitEvent.LevelUp(recruit, recruit.getXpLevel()));
            }
        }
    }

    static void recalculateCost(AbstractRecruitEntity recruit) {
        int currCost = recruit.getCost();
        int armorBonus = recruit.getArmorValue() * 2;
        int weaponBonus = 4;
        int speedBonus = (int) (recruit.getSpeed() * 2);
        int shieldBonus = recruit.getOffhandItem().getItem() instanceof ShieldItem ? 10 : 0;
        int newCost = Math.abs(shieldBonus + speedBonus + weaponBonus + armorBonus + currCost + recruit.getXpLevel() * 2);
        recruit.setCost(newCost);
    }
}
