package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Items;

final class IronGolemRecruitReplacementFactory {
    private IronGolemRecruitReplacementFactory() {
    }

    static void createRecruit(IronGolem ironGolem) {
        spawnReplacement(ironGolem, ModEntityTypes.RECRUIT.get());
    }

    static void createShieldman(IronGolem ironGolem) {
        spawnReplacement(ironGolem, ModEntityTypes.RECRUIT_SHIELDMAN.get());
    }

    static void createBowman(IronGolem ironGolem) {
        spawnReplacement(ironGolem, ModEntityTypes.BOWMAN.get());
    }

    static void createCrossbowman(IronGolem ironGolem) {
        spawnReplacement(ironGolem, ModEntityTypes.CROSSBOWMAN.get());
    }

    private static void spawnReplacement(IronGolem ironGolem, EntityType<? extends AbstractRecruitEntity> recruitType) {
        AbstractRecruitEntity recruit = recruitType.create(ironGolem.getCommandSenderWorld());
        recruit.copyPosition(ironGolem);
        recruit.initSpawn();
        recruit.getInventory().setItem(8, Items.BREAD.getDefaultInstance());
        ironGolem.remove(Entity.RemovalReason.DISCARDED);
        ironGolem.getCommandSenderWorld().addFreshEntity(recruit);
    }
}
