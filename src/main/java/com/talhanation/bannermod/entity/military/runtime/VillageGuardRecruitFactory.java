package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.BowmanEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import com.talhanation.bannermod.entity.military.HorsemanEntity;
import com.talhanation.bannermod.entity.military.NomadEntity;
import com.talhanation.bannermod.entity.military.RecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitShieldmanEntity;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.Random;

final class VillageGuardRecruitFactory {
    private VillageGuardRecruitFactory() {
    }

    static RecruitEntity createGuardLeader(BlockPos spawnPos, String name, ServerLevel world, Random random) {
        RecruitEntity patrolLeader = ModEntityTypes.RECRUIT.get().create(world);
        patrolLeader.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.5D, spawnPos.getZ() + 0.5D, random.nextFloat() * 360 - 180F, 0);
        patrolLeader.finalizeSpawn(world, world.getCurrentDifficultyAt(spawnPos), MobSpawnType.PATROL, null, null);
        setGuardLeaderEquipment(patrolLeader);
        patrolLeader.setPersistenceRequired();
        patrolLeader.setXpLevel(1 + random.nextInt(2));
        patrolLeader.addLevelBuffsForLevel(patrolLeader.getXpLevel());
        patrolLeader.setHunger(100);
        patrolLeader.setMoral(100);
        patrolLeader.setCost(45);
        patrolLeader.setXp(random.nextInt(200));
        patrolLeader.setCustomName(Component.literal(name));
        patrolLeader.setProtectUUID(Optional.of(patrolLeader.getUUID()));
        world.addFreshEntity(patrolLeader);
        return patrolLeader;
    }

    static void setGuardLeaderEquipment(RecruitEntity recruit) {
        Random random = new Random();
        recruit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        recruit.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        recruit.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        ItemStack emeralds = new ItemStack(Items.EMERALD);
        emeralds.setCount(8 + random.nextInt(10));
        recruit.inventory.setItem(8, emeralds);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        int k = random.nextInt(8);
        ItemStack food = switch (k) {
            default -> new ItemStack(Items.BREAD);
            case 1 -> new ItemStack(Items.COOKED_BEEF);
            case 2 -> new ItemStack(Items.COOKED_CHICKEN);
            case 3 -> new ItemStack(Items.COOKED_MUTTON);
        };
        food.setCount(32 + k);
        recruit.inventory.setItem(7, food);
    }

    static void createGuardRecruit(BlockPos spawnPos, RecruitEntity patrolLeader, String name, ServerLevel world, Random random) {
        RecruitEntity recruit = ModEntityTypes.RECRUIT.get().create(world);
        spawnGuardMember(recruit, spawnPos, patrolLeader, name, world, random, 2, 65, 10, 80);
    }

    static void createGuardBowman(BlockPos spawnPos, RecruitEntity patrolLeader, ServerLevel world, Random random) {
        BowmanEntity bowman = ModEntityTypes.BOWMAN.get().create(world);
        spawnGuardMember(bowman, spawnPos, patrolLeader, "Village Guard", world, random, 2, 65, 16, 120);
    }

    static void createGuardShieldman(BlockPos spawnPos, RecruitEntity patrolLeader, String name, ServerLevel world, Random random) {
        RecruitShieldmanEntity shieldman = ModEntityTypes.RECRUIT_SHIELDMAN.get().create(world);
        spawnGuardMember(shieldman, spawnPos, patrolLeader, name, world, random, 2, 65, 24, 120);
    }

    static void createGuardHorseman(BlockPos spawnPos, RecruitEntity patrolLeader, String name, ServerLevel world, Random random) {
        HorsemanEntity horseman = ModEntityTypes.HORSEMAN.get().create(world);
        spawnGuardMember(horseman, spawnPos, patrolLeader, name, world, random, 2, 75, 20, 120);
    }

    static void createGuardNomad(BlockPos spawnPos, RecruitEntity patrolLeader, String name, ServerLevel world, Random random) {
        NomadEntity nomad = ModEntityTypes.NOMAD.get().create(world);
        spawnGuardMember(nomad, spawnPos, patrolLeader, name, world, random, 2, 75, 25, 120);
    }

    static void createPatrolCrossbowman(BlockPos spawnPos, RecruitEntity patrolLeader, ServerLevel world, Random random) {
        CrossBowmanEntity crossBowman = ModEntityTypes.CROSSBOWMAN.get().create(world);
        spawnGuardMember(crossBowman, spawnPos, patrolLeader, "Village Guard", world, random, 3, 65, 32, 120);
    }

    private static void spawnGuardMember(AbstractRecruitEntity recruit, BlockPos spawnPos, RecruitEntity patrolLeader, String name, ServerLevel world, Random random, int maxLevelRoll, float morale, int cost, int xpBound) {
        recruit.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.5D, spawnPos.getZ() + 0.5D, random.nextFloat() * 360 - 180F, 0);
        recruit.finalizeSpawn(world, world.getCurrentDifficultyAt(spawnPos), MobSpawnType.PATROL, null, null);
        recruit.setEquipment();
        configureGuard(recruit, patrolLeader, name, random, maxLevelRoll, morale, cost, xpBound);
        world.addFreshEntity(recruit);
    }

    private static void configureGuard(AbstractRecruitEntity recruit, RecruitEntity patrolLeader, String name, Random random, int maxLevelRoll, float morale, int cost, int xpBound) {
        recruit.setPersistenceRequired();
        recruit.setXpLevel(1 + random.nextInt(maxLevelRoll));
        recruit.addLevelBuffsForLevel(recruit.getXpLevel());
        recruit.setHunger(80);
        recruit.setMoral(morale);
        recruit.setCost(cost);
        recruit.setProtectUUID(Optional.of(patrolLeader.getUUID()));
        recruit.setShouldProtect(true);
        recruit.setXp(random.nextInt(xpBound));
        recruit.setCustomName(Component.literal(name));
    }
}
