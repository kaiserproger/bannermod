package com.talhanation.bannermod.settlement.civilian.runtime;

import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.civilian.WorkerSettlementSpawnRules;
import com.talhanation.bannermod.settlement.civilian.WorkerSettlementSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WorkerSettlementSpawnRuntime {
    private static final Map<UUID, Long> settlementSpawnCooldowns = new HashMap<>();
    private static final Set<UUID> knownAdultVillagers = new HashSet<>();

    private WorkerSettlementSpawnRuntime() {
    }

    static void reset() {
        settlementSpawnCooldowns.clear();
        knownAdultVillagers.clear();
    }

    static void recordVillagerJoin(Villager villager) {
        if (!villager.isBaby()) {
            knownAdultVillagers.add(villager.getUUID());
        }
    }

    static void handleVillagerAdultTick(ServerLevel level, Villager villager) {
        if (knownAdultVillagers.add(villager.getUUID())) {
            attemptBirthWorkerSpawn(level, villager);
        }
    }

    static AbstractWorkerEntity attemptBirthWorkerSpawn(ServerLevel level, Villager villager) {
        RecruitsClaim claim = WorkerSettlementClaimPolicy.resolveClaim(villager.blockPosition());
        if (claim == null) {
            return null;
        }

        int villagerCount = Math.max(1, WorkerSettlementClaimPolicy.countEntitiesInClaim(level, claim, Villager.class));
        WorkerSettlementSpawnRules.Decision decision = WorkerSettlementSpawnRules.evaluateBirth(
                WorkerSettlementClaimPolicy.resolveSettlementBinding(villager, claim),
                villagerCount,
                WorkerSettlementClaimPolicy.countEntitiesInClaim(level, claim, AbstractWorkerEntity.class),
                false,
                WorkersServerConfig.workerBirthRuleConfig(),
                WorkerSettlementClaimPolicy.countWorkersByProfession(level, claim),
                WorkerSettlementClaimPolicy.housingSlackForClaim(level, claim)
        );
        AbstractWorkerEntity worker = WorkerSettlementSpawner.spawnWorkerFromVillager(level, villager, decision, claim);
        if (worker != null) {
            WorkerSettlementClaimPolicy.assignHomeIfAvailable(level, claim, worker.getUUID(), level.getGameTime());
        }
        return worker;
    }

    static AbstractWorkerEntity attemptSettlementWorkerSpawn(ServerLevel level, Villager villager) {
        RecruitsClaim claim = WorkerSettlementClaimPolicy.resolveClaim(villager.blockPosition());
        if (claim == null) {
            return null;
        }

        long now = level.getGameTime();
        // The candidate villager is, by construction, a member of the claim's villager pool
        // about to be promoted; mirror the birth path's Math.max(1, ...) so a not-yet-indexed
        // freshly-spawned candidate cannot wedge the rule into INSUFFICIENT_VILLAGERS.
        int villagerCount = Math.max(1,
                WorkerSettlementClaimPolicy.countEntitiesInClaim(level, claim, Villager.class));
        WorkerSettlementSpawnRules.Decision decision = WorkerSettlementSpawnRules.evaluateSettlementSpawn(
                WorkerSettlementClaimPolicy.resolveSettlementBinding(villager, claim),
                villagerCount,
                WorkerSettlementClaimPolicy.countEntitiesInClaim(level, claim, AbstractWorkerEntity.class),
                isSettlementSpawnOnCooldown(claim, now),
                WorkersServerConfig.workerSettlementSpawnRuleConfig(),
                WorkerSettlementClaimPolicy.countWorkersByProfession(level, claim),
                WorkerSettlementClaimPolicy.housingSlackForClaim(level, claim)
        );

        AbstractWorkerEntity worker = WorkerSettlementSpawner.spawnWorkerFromVillager(level, villager, decision, claim);
        if (worker != null) {
            long cooldownTicks = WorkersServerConfig.settlementSpawnCooldownTicks();
            if (cooldownTicks > 0L) {
                settlementSpawnCooldowns.put(claim.getUUID(), now + cooldownTicks);
            }
            WorkerSettlementClaimPolicy.assignHomeIfAvailable(level, claim, worker.getUUID(), now);
        }
        return worker;
    }

    private static boolean isSettlementSpawnOnCooldown(RecruitsClaim claim, long gameTime) {
        Long cooldownUntil = settlementSpawnCooldowns.get(claim.getUUID());
        return cooldownUntil != null && cooldownUntil > gameTime;
    }
}
