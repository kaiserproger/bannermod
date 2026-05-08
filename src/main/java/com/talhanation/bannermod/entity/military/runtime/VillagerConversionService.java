package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.ICompanion;
import com.talhanation.bannermod.entity.military.VillagerNobleEntity;
import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class VillagerConversionService {
    private VillagerConversionService() {
    }

    public static void createRecruit(Villager villager, EntityType<? extends AbstractRecruitEntity> recruitType) {
        AbstractRecruitEntity abstractRecruit = recruitType.create(villager.getCommandSenderWorld());
        if (abstractRecruit == null) return;
        abstractRecruit.copyPosition(villager);
        abstractRecruit.initSpawn();
        copyVillagerInventory(villager, abstractRecruit);
        levelCompanionIfNeeded(abstractRecruit);
        villager.getCommandSenderWorld().addFreshEntity(abstractRecruit);
        Component name = villager.getCustomName();
        if (name != null) abstractRecruit.setCustomName(name);
        releaseVillagerPoi(villager);
        villager.discard();
    }

    public static void createNobleVillager(Villager villager) {
        ServerLevel level = (ServerLevel) villager.getCommandSenderWorld();
        VillagerNobleEntity nobleEntity = ModEntityTypes.VILLAGER_NOBLE.get().create(level);
        if (nobleEntity == null || level.isClientSide()) return;
        nobleEntity.copyPosition(villager);
        nobleEntity.initSpawn();
        nobleEntity.setFollowState(0);
        copyVillagerInventory(villager, nobleEntity);
        Component name = villager.getCustomName();
        if (name != null) nobleEntity.setCustomName(name);
        Optional<GlobalPos> homeMemory = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (homeMemory.isPresent()) {
            nobleEntity.setSleepingPos(homeMemory.get().pos());
            nobleEntity.setShouldRest(true);
        }
        villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).ifPresent(globalPos -> nobleEntity.setHoldPos(globalPos.pos().getCenter()));
        level.addFreshEntity(nobleEntity);
        releaseVillagerPoi(villager);
        villager.discard();
    }

    public static void createHiredRecruitFromVillager(ServerLevel serverLevel, Villager villager, EntityType<? extends AbstractRecruitEntity> recruitType, Player player, RecruitsGroup group) {
        AbstractRecruitEntity abstractRecruit = recruitType.create(villager.getCommandSenderWorld());
        if (abstractRecruit == null) return;
        abstractRecruit.initSpawn();
        Component name = villager.getCustomName();
        if (name != null && !name.getString().isEmpty()) abstractRecruit.setCustomName(name);
        if (CommandEvents.handleRecruiting(player, group, abstractRecruit, false)) {
            abstractRecruit.copyPosition(villager);
            abstractRecruit.setFollowState(1);
            copyVillagerInventory(villager, abstractRecruit);
            abstractRecruit.setGroupUUID(group.getUUID());
            villager.getCommandSenderWorld().addFreshEntity(abstractRecruit);
            if (abstractRecruit instanceof ICompanion companion) {
                levelCompanionIfNeeded(abstractRecruit);
                companion.applyRecruitValues(abstractRecruit);
            }
            releaseVillagerPoi(villager);
            villager.discard();
        }
    }

    public static void spawnHiredRecruit(ServerLevel serverLevel, EntityType<? extends AbstractRecruitEntity> recruitType, Player player, RecruitsGroup group) {
        AbstractRecruitEntity abstractRecruit = recruitType.create(player.getCommandSenderWorld());
        if (abstractRecruit == null) return;
        abstractRecruit.initSpawn();
        if (CommandEvents.handleRecruiting(player, group, abstractRecruit, false)) {
            abstractRecruit.copyPosition(player);
            abstractRecruit.setFollowState(1);
            abstractRecruit.setGroupUUID(group.getUUID());
            player.getCommandSenderWorld().addFreshEntity(abstractRecruit);
            if (abstractRecruit instanceof ICompanion companion) {
                levelCompanionIfNeeded(abstractRecruit);
                companion.applyRecruitValues(abstractRecruit);
            }
        }
    }

    private static void copyVillagerInventory(Villager villager, AbstractRecruitEntity recruit) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack itemStack = villager.getInventory().getItem(i);
            recruit.getInventory().addItem(itemStack);
        }
    }

    private static void levelCompanionIfNeeded(AbstractRecruitEntity recruit) {
        if (recruit instanceof ICompanion) {
            for (int i = 0; i < 4; i++) {
                recruit.addXp(RecruitsServerConfig.RecruitsMaxXpForLevelUp.get());
                recruit.checkLevel();
            }
        }
    }

    private static void releaseVillagerPoi(Villager villager) {
        if (RecruitsServerConfig.RecruitTablesPOIReleasing.get()) villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.HOME);
        villager.releasePoi(MemoryModuleType.MEETING_POINT);
    }
}
