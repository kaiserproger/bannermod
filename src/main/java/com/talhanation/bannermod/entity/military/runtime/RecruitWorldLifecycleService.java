package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.ai.military.horse.HorseRiddenByRecruitGoal;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.citizen.runtime.CitizenWorldLifecycleService;
import com.talhanation.bannermod.persistence.military.PillagerPatrolSpawn;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.persistence.military.RecruitsGroupsManager;
import com.talhanation.bannermod.persistence.military.RecruitsPatrolSpawn;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerUnitManager;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecruitWorldLifecycleService {
    private RecruitWorldLifecycleService() {
    }

    public static RecruitManagers initializeManagers(MinecraftServer server) {
        RecruitsPlayerUnitManager playerUnitManager = new RecruitsPlayerUnitManager();
        playerUnitManager.load(server.overworld());

        RecruitsGroupsManager groupsManager = new RecruitsGroupsManager();
        groupsManager.load(server.overworld());
        return new RecruitManagers(playerUnitManager, groupsManager);
    }

    public static void saveManagers(MinecraftServer server,
                                    RecruitsPlayerUnitManager playerUnitManager,
                                    RecruitsGroupsManager groupsManager) {
        playerUnitManager.save(server.overworld());
        groupsManager.save(server.overworld());
    }

    public static void syncPlayerJoin(ServerPlayer player,
                                      RecruitsPlayerUnitManager playerUnitManager,
                                      RecruitsGroupsManager groupsManager) {
        playerUnitManager.broadCastUnitInfoToPlayer(player);
        groupsManager.broadCastGroupsToPlayer(player);
    }

    public static void teleportFollowingRecruits(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event instanceof EntityTeleportEvent.EnderPearl
                || event instanceof EntityTeleportEvent.ChorusFruit
                || event instanceof EntityTeleportEvent.EnderEntity) {
            return;
        }

        double targetX = event.getTargetX();
        double targetY = event.getTargetY();
        double targetZ = event.getTargetZ();
        UUID playerUuid = player.getUUID();

        List<AbstractRecruitEntity> recruits = player.getCommandSenderWorld().getEntitiesOfClass(
                AbstractRecruitEntity.class,
                player.getBoundingBox().inflate(64, 32, 64),
                recruit -> recruit.isAlive() && recruit.getFollowState() == 1 && recruit.getOwnerUUID().equals(playerUuid)
        );

        recruits.forEach(recruit -> recruit.teleportTo(targetX, targetY, targetZ));
    }

    public static void tickLevel(LevelTickEvent.Post event,
                                 Map<ServerLevel, RecruitsPatrolSpawn> recruitPatrols,
                                 Map<ServerLevel, PillagerPatrolSpawn> pillagerPatrols) {
        if (event.getLevel().isClientSide || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (RecruitsServerConfig.ShouldRecruitPatrolsSpawn.get()) {
            recruitPatrols.computeIfAbsent(serverLevel, ignored -> new RecruitsPatrolSpawn(serverLevel)).tick();
        }

        if (RecruitsServerConfig.ShouldPillagerPatrolsSpawn.get()) {
            pillagerPatrols.computeIfAbsent(serverLevel, ignored -> new PillagerPatrolSpawn(serverLevel)).tick();
        }

        CitizenWorldLifecycleService.tickAsyncPathfinding(serverLevel);
    }

    public static void markRecruitsForGroupRefresh(ServerLevel level, RecruitsGroupsManager groupsManager) {
        List<AbstractRecruitEntity> recruitList = RecruitIndex.instance().all(level, false);
        if (recruitList == null) {
            RuntimeProfilingCounters.increment("recruit.index.unavailable");
            return;
        }
        for (AbstractRecruitEntity recruit : recruitList) {
            recruit.needsGroupUpdate = true;
        }

        groupsManager.save(level);
    }

    public static void handleLegacyGroup(AbstractRecruitEntity recruit,
                                         int oldGroupNumber,
                                         MinecraftServer server,
                                         RecruitsGroupsManager groupsManager) {
        if (recruit.getCommandSenderWorld().isClientSide()) {
            return;
        }
        if (recruit.getOwner() instanceof ServerPlayer serverPlayer) {
            String name = "Group " + oldGroupNumber;
            RecruitsGroup group = groupsManager.getPlayersGroupByName(serverPlayer, name);
            if (group == null) {
                group = new RecruitsGroup(name, serverPlayer, 0);
            }
            recruit.setGroupUUID(group.getUUID());
            group.addMember(recruit.getUUID());
            groupsManager.addOrUpdateGroup(server.overworld(), serverPlayer, group);
            groupsManager.broadCastGroupsToPlayer(serverPlayer);
        }
    }

    public static void ensureHorseGoal(Entity entity) {
        if (entity instanceof AbstractHorse horse) {
            horse.goalSelector.addGoal(0, new HorseRiddenByRecruitGoal(horse));
        }
    }

    public record RecruitManagers(RecruitsPlayerUnitManager playerUnitManager,
                                  RecruitsGroupsManager groupsManager) {
    }
}
