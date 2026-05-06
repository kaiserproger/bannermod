package com.talhanation.bannermod.events;

import com.talhanation.bannermod.citizen.runtime.CitizenBirthService;
import com.talhanation.bannermod.citizen.CitizenProfession;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.civilian.WorkerSettlementSpawnRules;
import com.talhanation.bannermod.war.runtime.WarSiegeQueries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.citizen.CitizenIndex;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WorkerSettlementEventService {
    private static final Map<UUID, Long> CLAIM_WORKER_GROWTH_SPAWN_TIMES = new HashMap<>();

    private WorkerSettlementEventService() {
    }

    static void resetRuntimeState() {
        CLAIM_WORKER_GROWTH_SPAWN_TIMES.clear();
        WorkerSettlementSpawnRuntime.reset();
        CitizenBirthService.resetRuntimeState();
    }

    static void runCitizenBirthPass(ServerLevel level) {
        CitizenBirthService.runCitizenBirthPass(level);
    }

    static void recordVillagerJoin(Villager villager) {
        WorkerSettlementSpawnRuntime.recordVillagerJoin(villager);
    }

    static void handleVillagerAdultTick(ServerLevel level, Villager villager) {
        WorkerSettlementSpawnRuntime.handleVillagerAdultTick(level, villager);
    }

    static AbstractWorkerEntity attemptBirthWorkerSpawn(ServerLevel level, Villager villager) {
        return WorkerSettlementSpawnRuntime.attemptBirthWorkerSpawn(level, villager);
    }

    static AbstractWorkerEntity attemptSettlementWorkerSpawn(ServerLevel level, Villager villager) {
        return WorkerSettlementSpawnRuntime.attemptSettlementWorkerSpawn(level, villager);
    }

    static void runClaimWorkerGrowthPass(ServerLevel level) {
        if (level == null || ClaimEvents.claimManager() == null) {
            return;
        }

        for (RecruitsClaim claim : ClaimEvents.claimManager().getAllClaims()) {
            if (claim == null || claim.getOwnerPoliticalEntityId() == null) {
                continue;
            }
            mobilizeCitizensIfClaimUnderSiege(level, claim);
            attemptClaimWorkerGrowth(level, claim, claim.getOwnerPoliticalEntityId().toString(), level.getGameTime());
        }
    }

    private static void mobilizeCitizensIfClaimUnderSiege(ServerLevel level, RecruitsClaim claim) {
        if (!WarSiegeQueries.isClaimUnderSiege(level, claim)) {
            return;
        }
        float chance = WorkersServerConfig.citizenMilitiaMobilizationChance();
        if (chance <= 0.0F) {
            return;
        }
        UUID politicalEntityId = claim.getOwnerPoliticalEntityId();
        if (politicalEntityId == null) {
            return;
        }
        PoliticalEntityRecord owner = WarRuntimeContext.registry(level).byId(politicalEntityId).orElse(null);
        if (owner == null) {
            return;
        }
        List<CitizenEntity> nearbyCitizens = CitizenIndex.instance().queryInClaim(level, claim).orElse(List.of());
        for (CitizenEntity citizen : nearbyCitizens) {
            if (!citizen.isAlive()) {
                continue;
            }
            if (citizen.activeProfession() != CitizenProfession.NONE) {
                continue;
            }
            UUID citizenOwner = citizen.getOwnerUUID();
            if (citizenOwner == null || !isPoliticalMember(owner, citizenOwner)) {
                continue;
            }
            if (citizen.getRandom().nextFloat() >= chance) {
                continue;
            }
            citizen.switchProfession(CitizenProfession.RECRUIT_SPEAR);
        }
    }

    private static boolean isPoliticalMember(PoliticalEntityRecord entity, UUID playerUuid) {
        if (entity == null || playerUuid == null) {
            return false;
        }
        if (playerUuid.equals(entity.leaderUuid())) {
            return true;
        }
        return entity.coLeaderUuids().contains(playerUuid);
    }

    static AbstractWorkerEntity attemptClaimWorkerGrowth(ServerLevel level,
                                                         RecruitsClaim claim,
                                                         BannerModSettlementBinding.Binding binding,
                                                         long gameTime,
                                                         WorkerSettlementSpawnRules.ClaimGrowthConfig config) {
        return WorkerSettlementClaimPolicy.attemptClaimWorkerGrowth(level, claim, binding, gameTime, config, CLAIM_WORKER_GROWTH_SPAWN_TIMES);
    }

    static AbstractWorkerEntity attemptClaimWorkerGrowth(ServerLevel level,
                                                         RecruitsClaim claim,
                                                         String settlementFactionId,
                                                         long gameTime) {
        return attemptClaimWorkerGrowth(level, claim, settlementFactionId, gameTime, WorkersServerConfig.claimWorkerGrowthConfig());
    }

    static AbstractWorkerEntity attemptClaimWorkerGrowth(ServerLevel level,
                                                         RecruitsClaim claim,
                                                         String settlementFactionId,
                                                         long gameTime,
                                                         WorkerSettlementSpawnRules.ClaimGrowthConfig config) {
        if (level != null && settlementFactionId != null && !settlementFactionId.isBlank()) {
            settlementFactionId = WarRuntimeContext.registry(level)
                    .byName(settlementFactionId)
                    .map(PoliticalEntityRecord::id)
                    .map(UUID::toString)
                    .orElse(settlementFactionId);
        }
        return attemptClaimWorkerGrowth(level, claim, WorkerSettlementClaimPolicy.resolveClaimGrowthBinding(claim, settlementFactionId), gameTime, config);
    }
}
