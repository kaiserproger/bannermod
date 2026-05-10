package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.util.BannerModNpcNamePool;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.Tags;

final class RecruitSpawnService {
    private RecruitSpawnService() {
    }

    static SpawnGroupData prepareBaseRecruitSpawn(AbstractRecruitEntity recruit, ServerLevelAccessor world, SpawnGroupData spawnData) {
        setRandomSpawnBonus(recruit);
        recruit.rebuildSpawnNavigation(world);
        return spawnData;
    }

    static SpawnGroupData finishLeafRecruitSpawn(AbstractRecruitEntity recruit, ServerLevelAccessor world, DifficultyInstance difficulty, SpawnGroupData spawnData, boolean canOpenDoors, boolean enchantEquipment) {
        if (canOpenDoors) {
            recruit.enableRecruitSpawnDoors();
        }
        if (enchantEquipment) {
            recruit.applyRecruitSpawnEnchantments(world, difficulty);
        }
        recruit.initSpawn();
        return spawnData;
    }

    static void initStandardRecruitSpawn(AbstractRecruitEntity recruit, String defaultName, int cost) {
        recruit.setCustomName(Component.literal(defaultName));
        recruit.setCost(cost);
        recruit.setEquipment();
        setRandomSpawnBonus(recruit);
        recruit.setPersistenceRequired();
        applySpawnValues(recruit);
    }

    static void initPersistentNamedSpawn(AbstractRecruitEntity recruit, String defaultName) {
        if (recruit.getCustomName() == null || recruit.getCustomName().getString().isEmpty()) {
            recruit.setCustomName(Component.literal(defaultName));
        }
        recruit.setPersistenceRequired();
        applySpawnValues(recruit);
    }

    static void setRandomSpawnBonus(AbstractRecruitEntity recruit) {
        applyPermanentModifier(recruit.getAttribute(Attributes.MAX_HEALTH), new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "heath_bonus"), recruit.getRandom().nextDouble() * 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        applyPermanentModifier(recruit.getAttribute(Attributes.ATTACK_DAMAGE), new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "attack_bonus"), recruit.getRandom().nextDouble() * 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        applyPermanentModifier(recruit.getAttribute(Attributes.KNOCKBACK_RESISTANCE), new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "knockback_bonus"), recruit.getRandom().nextDouble() * 0.1D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        applyPermanentModifier(recruit.getAttribute(Attributes.MOVEMENT_SPEED), new AttributeModifier(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "speed_bonus"), recruit.getRandom().nextDouble() * 0.1D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void applyPermanentModifier(AttributeInstance attribute, AttributeModifier modifier) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(modifier.id());
        attribute.addPermanentModifier(modifier);
    }

    static void applySpawnValues(AbstractRecruitEntity recruit) {
        BannerModNpcNamePool.ensureNamed(recruit);
        recruit.setHunger(50);
        recruit.setMoral(50);
        recruit.setListen(true);
        recruit.setXpLevel(1);
        applyBiomeAndVariant(recruit);
    }

    static void applyBiomeAndVariant(AbstractRecruitEntity recruit) {
        Holder<Biome> biome = recruit.getCommandSenderWorld().getBiome(recruit.getOnPos());
        byte biomeByte = 2;
        int variant = recruit.getRandom().nextInt(0, 14);
        if (biome.is(Biomes.ERODED_BADLANDS) || biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_SANDY) && !biome.is(Tags.Biomes.IS_WET_OVERWORLD)) {
            biomeByte = 0;
            variant = recruit.getRandom().nextInt(15, 19);
        }
        else if (biome.is(Tags.Biomes.IS_TAIGA) && biome.is(Tags.Biomes.IS_COLD_OVERWORLD) && !(biome.is(Tags.Biomes.IS_SNOWY))) {
            biomeByte = 6;
            variant = recruit.getRandom().nextInt(5, 14);
        }
        else if (biome.is(Tags.Biomes.IS_WET_OVERWORLD) && !biome.is(Tags.Biomes.IS_SANDY) && !biome.is(Tags.Biomes.IS_SWAMP)) {
            biomeByte = 1;
            variant = recruit.getRandom().nextInt(15, 19);
        }
        else if (biome.is(Tags.Biomes.IS_HOT_OVERWORLD) && biome.is(Tags.Biomes.IS_SPARSE_VEGETATION_OVERWORLD)) {
            biomeByte = 3;
            variant = recruit.getRandom().nextInt(15, 19);
        }
        else if (biome.is(Tags.Biomes.IS_SNOWY)) {
            biomeByte = 4;
            variant = recruit.getRandom().nextInt(5, 10);
        }
        else if (biome.is(Tags.Biomes.IS_SWAMP)) {
            biomeByte = 5;
            variant = recruit.getRandom().nextInt(5, 14);
        }

        recruit.setBiome(biomeByte);
        recruit.setVariant(variant);
    }
}
