package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.entity.military.RecruitEntity;
import com.talhanation.bannermod.entity.military.VillagerNobleEntity;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;

import java.util.List;
import java.util.Random;

public final class VillageGuardSpawnService {
    private VillageGuardSpawnService() {
    }

    public static void handleIronGolemOverride(IronGolem ironGolemEntity, Random random) {
        if (!ironGolemEntity.isPlayerCreated() && RecruitsServerConfig.OverrideIronGolemSpawn.get()) {
            List<AbstractRecruitEntity> nearby = RecruitIndex.instance().allInBox(ironGolemEntity.getCommandSenderWorld(), ironGolemEntity.getBoundingBox().inflate(32), false);
            if (nearby == null) {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                nearby = ironGolemEntity.getCommandSenderWorld().getEntitiesOfClass(AbstractRecruitEntity.class, ironGolemEntity.getBoundingBox().inflate(32));
            }
            nearby.removeIf(recruit -> recruit instanceof VillagerNobleEntity);
            if (nearby.size() > 1) {
                ironGolemEntity.remove(Entity.RemovalReason.KILLED);
                return;
            }
            int i = random.nextInt(6);
            if (i == 1) IronGolemRecruitReplacementFactory.createBowman(ironGolemEntity);
            else if (i == 2) IronGolemRecruitReplacementFactory.createCrossbowman(ironGolemEntity);
            else if (i == 0) IronGolemRecruitReplacementFactory.createShieldman(ironGolemEntity);
            else IronGolemRecruitReplacementFactory.createRecruit(ironGolemEntity);
        }
    }

    public static void spawnVillageGuards(ServerLevel level, BlockPos villagePos) {
        Random random = new Random();
        BlockPos spawnPos = com.talhanation.bannermod.persistence.military.RecruitsPatrolSpawn.func_221244_a(villagePos, 20, random, level);
        if (spawnPos != null && com.talhanation.bannermod.persistence.military.RecruitsPatrolSpawn.func_226559_a_(spawnPos, level) && spawnPos.distSqr(villagePos) > 10) {
            BlockPos upPos = new BlockPos(spawnPos.getX(), spawnPos.getY() + 2, spawnPos.getZ());
            int i = random.nextInt(4);
            switch (i) {
                default -> VillageGuardSquadFactory.spawnSmallGuardRecruits(upPos, level, random);
                case 1 -> VillageGuardSquadFactory.spawnMediumGuardRecruits(upPos, level, random);
                case 2 -> VillageGuardSquadFactory.spawnLargeGuardRecruits(upPos, level, random);
            }
        }
    }

    static RecruitEntity createGuardLeader(BlockPos upPos, String name, ServerLevel world, Random random) {
        return VillageGuardRecruitFactory.createGuardLeader(upPos, name, world, random);
    }

    static void setGuardLeaderEquipment(RecruitEntity recruit) {
        VillageGuardRecruitFactory.setGuardLeaderEquipment(recruit);
    }
}
