package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.entity.military.RecruitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

final class VillageGuardSquadFactory {
    private VillageGuardSquadFactory() {
    }

    static void spawnSmallGuardRecruits(BlockPos spawnPos, ServerLevel world, Random random) {
        RecruitEntity guardLeader = VillageGuardRecruitFactory.createGuardLeader(spawnPos, "Village Guard Leader", world, random);
        VillageGuardRecruitFactory.createGuardRecruit(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardRecruit(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardShieldman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardBowman(spawnPos, guardLeader, world, random);
    }

    static void spawnMediumGuardRecruits(BlockPos spawnPos, ServerLevel world, Random random) {
        RecruitEntity guardLeader = VillageGuardRecruitFactory.createGuardLeader(spawnPos, "Village Guard Leader", world, random);
        VillageGuardRecruitFactory.createGuardShieldman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardShieldman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardBowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createGuardBowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createPatrolCrossbowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createPatrolCrossbowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createGuardHorseman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardNomad(spawnPos, guardLeader, "Village Guard", world, random);
    }

    static void spawnLargeGuardRecruits(BlockPos spawnPos, ServerLevel world, Random random) {
        RecruitEntity guardLeader = VillageGuardRecruitFactory.createGuardLeader(spawnPos, "Village Guard Leader", world, random);
        VillageGuardRecruitFactory.createGuardRecruit(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardRecruit(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardShieldman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardShieldman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardShieldman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardBowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createGuardBowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createGuardBowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createPatrolCrossbowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createPatrolCrossbowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createPatrolCrossbowman(spawnPos, guardLeader, world, random);
        VillageGuardRecruitFactory.createGuardHorseman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardNomad(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardHorseman(spawnPos, guardLeader, "Village Guard", world, random);
        VillageGuardRecruitFactory.createGuardNomad(spawnPos, guardLeader, "Village Guard", world, random);
    }
}
