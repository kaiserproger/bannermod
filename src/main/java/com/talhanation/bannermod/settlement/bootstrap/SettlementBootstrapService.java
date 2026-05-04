package com.talhanation.bannermod.settlement.bootstrap;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes;
import com.talhanation.bannermod.society.NpcFamilyAccess;
import com.talhanation.bannermod.society.NpcHouseholdAccess;
import com.talhanation.bannermod.society.NpcLifeStage;
import com.talhanation.bannermod.society.NpcSex;
import com.talhanation.bannermod.society.NpcSocietyAccess;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalMembership;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.civilian.WorkerSettlementSpawnRules;
import com.talhanation.bannermod.settlement.civilian.WorkerSettlementSpawner;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.util.BannerModNpcNamePool;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class SettlementBootstrapService {
    private static final int STARTER_FREE_CITIZEN_COUNT = 4;
    private static final List<WorkerSettlementSpawnRules.WorkerProfession> STARTER_PROFESSIONS = List.of(
            WorkerSettlementSpawnRules.WorkerProfession.FARMER,
            WorkerSettlementSpawnRules.WorkerProfession.MINER,
            WorkerSettlementSpawnRules.WorkerProfession.LUMBERJACK,
            WorkerSettlementSpawnRules.WorkerProfession.BUILDER
    );

    private SettlementBootstrapService() {
    }

    /**
     * Manual founding path for {@link SettlementBootstrapLifecycle}: validate STARTER_FORT,
     * establish/reuse the claim, write the formal settlement record, then seed starter population.
     */
    public static BootstrapResult bootstrapSettlement(ServerLevel level,
                                                      ServerPlayer player,
                                                      BuildingValidationResult fortValidationResult) {
        if (level == null || player == null || fortValidationResult == null) {
            return BootstrapResult.failure("Bootstrap request is missing required arguments.");
        }
        if (!fortValidationResult.valid() || fortValidationResult.type() != BuildingType.STARTER_FORT) {
            return BootstrapResult.failure("Settlement bootstrap requires a successful STARTER_FORT validation.");
        }
        if (fortValidationResult.snapshot() == null) {
            return BootstrapResult.failure("Fort validation snapshot is missing.");
        }

        BlockPos authorityPos = fortValidationResult.snapshot().anchorPos();
        RecruitsClaim claim = claimAt(authorityPos);
        if (claim == null) {
            claim = createStarterClaim(level, player, authorityPos);
            if (claim == null) {
                return BootstrapResult.failure("Starter fort bootstrap could not create a claim here. Create or join a state first, keep distance from same-side towns, or claim the land manually.");
            }
        }
        if (!playerOwnsClaim(player, claim)) {
            return BootstrapResult.failure("Settlement bootstrap requires a fort inside your faction claim.");
        }

        SettlementRegistryData registry = SettlementRegistryData.get(level);
        SettlementRecord existing = registry.getSettlementAt(new ChunkPos(authorityPos));
        if (existing != null) {
            return BootstrapResult.success("Settlement already exists at this authority position.", existing);
        }
        return createSettlement(level, player.getUUID(), authorityPos, claim);
    }

    static String starterWorkerReadinessMessage(int spawnedWorkers) {
        return starterWorkerReadinessMessage(spawnedWorkers, STARTER_FREE_CITIZEN_COUNT);
    }

    static String starterWorkerReadinessMessage(int spawnedWorkers, int spawnedFreeCitizens) {
        return "Settlement bootstrapped. Starter workers spawned: " + spawnedWorkers
                + ". Starter households seeded: " + Math.max(0, spawnedFreeCitizens)
                + " residents. Adult free citizens can fill vacancies; adolescents and children stay in their families. Ready: farmer has a starter crop area. Waiting: miner needs a mine, lumberjack needs a lumber camp, builder needs an architect workshop/build area. If vacancies remain empty, no free adult citizen is close enough or available yet.";
    }

    public static BootstrapResult bootstrapSettlement(ServerLevel level,
                                                      Player player,
                                                      BuildingValidationResult fortValidationResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            return bootstrapSettlement(level, serverPlayer, fortValidationResult);
        }
        if (level == null || player == null || fortValidationResult == null) {
            return BootstrapResult.failure("Bootstrap request is missing required arguments.");
        }
        if (!fortValidationResult.valid() || fortValidationResult.type() != BuildingType.STARTER_FORT) {
            return BootstrapResult.failure("Settlement bootstrap requires a successful STARTER_FORT validation.");
        }
        if (fortValidationResult.snapshot() == null) {
            return BootstrapResult.failure("Fort validation snapshot is missing.");
        }
        BlockPos authorityPos = fortValidationResult.snapshot().anchorPos();
        RecruitsClaim claim = claimAt(authorityPos);
        if (claim == null) {
            return BootstrapResult.failure("No claim found at fort authority position.");
        }
        if (!playerOwnsClaim(player, claim)) {
            return BootstrapResult.failure("Settlement bootstrap requires a fort inside your faction claim.");
        }
        SettlementRegistryData registry = SettlementRegistryData.get(level);
        SettlementRecord existing = registry.getSettlementAt(new ChunkPos(authorityPos));
        if (existing != null) {
            return BootstrapResult.success("Settlement already exists at this authority position.", existing);
        }
        return createSettlement(level, player.getUUID(), authorityPos, claim);
    }

    /**
     * Automatic claim bootstrap path: starter structure placement happens before this call, then the
     * persisted settlement record and starter population are created through the same model as manual founding.
     */
    public static BootstrapResult bootstrapClaimSettlement(ServerLevel level, RecruitsClaim claim, BlockPos authorityPos) {
        if (level == null || claim == null || authorityPos == null) {
            return BootstrapResult.failure("Bootstrap request is missing required arguments.");
        }
        SettlementRegistryData registry = SettlementRegistryData.get(level);
        SettlementRecord existing = registry.getSettlementByClaimId(claim.getUUID());
        if (existing == null) {
            existing = registry.getSettlementAt(new ChunkPos(authorityPos));
        }
        if (existing != null) {
            return BootstrapResult.success("Settlement already exists for this claim.", existing);
        }

        UUID ownerPlayerId = new UUID(0L, 0L);
        PoliticalEntityRecord owner = claim.getOwnerPoliticalEntityId() == null
                ? null
                : WarRuntimeContext.registry(level).byId(claim.getOwnerPoliticalEntityId()).orElse(null);
        if (owner != null && owner.leaderUuid() != null) {
            ownerPlayerId = owner.leaderUuid();
        } else if (claim.getPlayerInfo() != null) {
            ownerPlayerId = claim.getPlayerInfo().getUUID();
        }
        return createSettlement(level, ownerPlayerId, authorityPos, claim);
    }

    private static BootstrapResult createSettlement(ServerLevel level, UUID ownerPlayerId, BlockPos authorityPos, RecruitsClaim claim) {
        SettlementRegistryData registry = SettlementRegistryData.get(level);
        UUID settlementId = UUID.randomUUID();
        SettlementRecord settlement = new SettlementRecord(
                settlementId,
                ownerPlayerId,
                claim.getOwnerPoliticalEntityId() == null ? null : claim.getOwnerPoliticalEntityId().toString(),
                claim.getUUID(),
                level.dimension(),
                authorityPos,
                authorityPos,
                UUID.randomUUID(),
                SettlementStatus.ACTIVE,
                level.getGameTime()
        );
        registry.put(settlement);
        int spawnedWorkers = spawnStarterCitizens(level, authorityPos, claim);
        int spawnedFreeCitizens = spawnStarterFamilies(level, authorityPos, claim);
        return BootstrapResult.success(starterWorkerReadinessMessage(spawnedWorkers, spawnedFreeCitizens), settlement);
    }

    @Nullable
    private static RecruitsClaim claimAt(BlockPos anchorPos) {
        if (ClaimEvents.claimManager() == null) {
            return null;
        }
        return ClaimEvents.claimManager().getClaim(new ChunkPos(anchorPos));
    }

    private static boolean playerOwnsClaim(Player player, RecruitsClaim claim) {
        if (player == null || claim == null) return false;
        if (claim.getPlayerInfo() != null && player.getUUID().equals(claim.getPlayerInfo().getUUID())) {
            return true;
        }
        UUID politicalEntityId = claim.getOwnerPoliticalEntityId();
        if (politicalEntityId == null || !(player.level() instanceof ServerLevel level)) return false;
        PoliticalEntityRecord owner = WarRuntimeContext.registry(level).byId(politicalEntityId).orElse(null);
        if (owner == null) return false;
        UUID playerUuid = player.getUUID();
        return playerUuid.equals(owner.leaderUuid()) || owner.coLeaderUuids().contains(playerUuid);
    }

    private static boolean isStarterTownTooCloseToSameNationTown(ServerLevel level, ServerPlayer player, BlockPos authorityPos) {
        if (ClaimEvents.claimManager() == null) {
            return false;
        }
        UUID politicalEntityId = PoliticalMembership.entityIdFor(WarRuntimeContext.registry(level), player.getUUID());
        if (politicalEntityId == null) {
            return false;
        }
        RecruitsClaim candidate = new RecruitsClaim(player.getName().getString(), politicalEntityId);
        ChunkPos center = new ChunkPos(authorityPos);
        candidate.addChunk(center);
        candidate.setCenter(center);
        return ClaimEvents.claimManager().isTownTooCloseToSameNationTown(
                candidate,
                null,
                RecruitsServerConfig.TownMinCenterDistance.get());
    }

    @Nullable
    private static RecruitsClaim createStarterClaim(ServerLevel level, ServerPlayer player, BlockPos authorityPos) {
        if (ClaimEvents.claimManager() == null) {
            return null;
        }
        UUID politicalEntityId = PoliticalMembership.entityIdFor(WarRuntimeContext.registry(level), player.getUUID());
        if (politicalEntityId == null) {
            return null;
        }

        ChunkPos anchorChunk = new ChunkPos(authorityPos);
        RecruitsClaim existing = ClaimEvents.claimManager().getClaim(anchorChunk);
        if (existing != null) {
            return existing;
        }

        RecruitsClaim claim = new RecruitsClaim(player.getName().getString(), politicalEntityId);
        claim.addChunk(anchorChunk);
        claim.setCenter(anchorChunk);
        claim.setPlayer(new RecruitsPlayerInfo(player.getUUID(), player.getName().getString()));
        if (ClaimEvents.claimManager().isTownTooCloseToSameNationTown(
                claim,
                null,
                RecruitsServerConfig.TownMinCenterDistance.get())) {
            return null;
        }
        ClaimEvents.claimManager().addOrUpdateClaim(level, claim);
        return claim;
    }

    private static int spawnStarterCitizens(ServerLevel level, BlockPos authorityPos, RecruitsClaim claim) {
        int spawned = 0;
        for (WorkerSettlementSpawnRules.WorkerProfession profession : STARTER_PROFESSIONS) {
            WorkerSettlementSpawnRules.Decision decision =
                    new WorkerSettlementSpawnRules.Decision(true, profession, null, 0L);
            if (WorkerSettlementSpawner.spawnClaimWorker(level, authorityPos, decision, claim) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private static int spawnStarterFamilies(ServerLevel level, BlockPos authorityPos, RecruitsClaim claim) {
        Random random = new Random(authorityPos.asLong() ^ claim.getUUID().getMostSignificantBits() ^ claim.getUUID().getLeastSignificantBits());
        List<StarterHouseholdSeed> households = planStarterHouseholds(random);
        int spawned = 0;
        long gameTime = level.getGameTime();
        for (int householdIndex = 0; householdIndex < households.size(); householdIndex++) {
            StarterHouseholdSeed household = households.get(householdIndex);
            BlockPos householdOrigin = authorityPos.offset(2 + (householdIndex % 3) * 4, 0, 2 + (householdIndex / 3) * 4);
            List<UUID> residentIds = new ArrayList<>();
            UUID headResidentUuid = null;
            for (int memberIndex = 0; memberIndex < household.members().size(); memberIndex++) {
                StarterResidentSeed member = household.members().get(memberIndex);
                BlockPos spawnPos = householdOrigin.offset(memberIndex % 2, 0, memberIndex / 2);
                CitizenEntity citizen = spawnFreeCitizen(level, spawnPos, claim, member.lifeStage(), member.sex());
                if (citizen == null) {
                    continue;
                }
                spawned++;
                residentIds.add(citizen.getUUID());
                if (headResidentUuid == null && member.isHouseholdHeadCandidate()) {
                    headResidentUuid = citizen.getUUID();
                }
            }
            if (residentIds.isEmpty()) {
                continue;
            }
            UUID householdId = UUID.randomUUID();
            NpcHouseholdAccess.seedHousehold(
                    level,
                    householdId,
                    headResidentUuid == null ? residentIds.getFirst() : headResidentUuid,
                    residentIds,
                    gameTime
            );
            NpcFamilyAccess.reconcileHousehold(level, householdId, gameTime);
        }
        return spawned;
    }

    private static @Nullable CitizenEntity spawnFreeCitizen(ServerLevel level,
                                                            BlockPos spawnPos,
                                                            RecruitsClaim claim,
                                                            NpcLifeStage lifeStage,
                                                            NpcSex sex) {
        CitizenEntity citizen = ModCitizenEntityTypes.CITIZEN.get().create(level);
        if (citizen == null) {
            return null;
        }
        BlockPos safeSpawnPos = resolveSafeSpawnPos(level, spawnPos);
        citizen.moveTo(safeSpawnPos.getX() + 0.5D, safeSpawnPos.getY(), safeSpawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        citizen.setOwned(true);
        citizen.setFemale(sex == NpcSex.FEMALE);
        PoliticalEntityRecord owner = claim.getOwnerPoliticalEntityId() == null
                ? null
                : WarRuntimeContext.registry(level).byId(claim.getOwnerPoliticalEntityId()).orElse(null);
        if (owner != null && owner.leaderUuid() != null) {
            citizen.setOwnerUUID(java.util.Optional.of(owner.leaderUuid()));
        } else if (claim.getPlayerInfo() != null) {
            citizen.setOwnerUUID(java.util.Optional.of(claim.getPlayerInfo().getUUID()));
        }
        BannerModNpcNamePool.ensureNamed(citizen);
        level.addFreshEntity(citizen);
        NpcSocietyAccess.seedResident(level, citizen.getUUID(), lifeStage, sex, level.getGameTime());
        if (owner != null) {
            PlayerTeam team = level.getScoreboard().getPlayerTeam(owner.name());
            if (team != null) {
                level.getScoreboard().addPlayerToTeam(citizen.getScoreboardName(), team);
            }
        }
        return citizen;
    }

    private static List<StarterHouseholdSeed> planStarterHouseholds(Random random) {
        int householdCount = 2 + random.nextInt(3);
        List<StarterHouseholdSeed> households = new ArrayList<>();
        households.add(rootHousehold(random));
        while (households.size() < householdCount) {
            int roll = random.nextInt(4);
            if (roll == 0) {
                households.add(newlyweds());
            } else if (roll == 1) {
                households.add(youngFamily(random, true));
            } else {
                households.add(youngFamily(random, false));
            }
        }
        return households;
    }

    private static StarterHouseholdSeed rootHousehold(Random random) {
        List<StarterResidentSeed> members = new ArrayList<>();
        members.add(new StarterResidentSeed(NpcLifeStage.ADULT, NpcSex.MALE));
        members.add(new StarterResidentSeed(NpcLifeStage.ADULT, NpcSex.FEMALE));
        members.add(new StarterResidentSeed(randomMinorStage(random), random.nextBoolean() ? NpcSex.MALE : NpcSex.FEMALE));
        if (random.nextBoolean()) {
            members.add(new StarterResidentSeed(randomMinorStage(random), random.nextBoolean() ? NpcSex.MALE : NpcSex.FEMALE));
        }
        return new StarterHouseholdSeed(List.copyOf(members));
    }

    private static StarterHouseholdSeed youngFamily(Random random, boolean withTwoChildren) {
        List<StarterResidentSeed> members = new ArrayList<>();
        members.add(new StarterResidentSeed(NpcLifeStage.ADULT, NpcSex.MALE));
        members.add(new StarterResidentSeed(NpcLifeStage.ADULT, NpcSex.FEMALE));
        members.add(new StarterResidentSeed(randomMinorStage(random), random.nextBoolean() ? NpcSex.MALE : NpcSex.FEMALE));
        if (withTwoChildren || random.nextBoolean()) {
            members.add(new StarterResidentSeed(randomMinorStage(random), random.nextBoolean() ? NpcSex.MALE : NpcSex.FEMALE));
        }
        return new StarterHouseholdSeed(List.copyOf(members));
    }

    private static StarterHouseholdSeed newlyweds() {
        return new StarterHouseholdSeed(List.of(
                new StarterResidentSeed(NpcLifeStage.ADULT, NpcSex.MALE),
                new StarterResidentSeed(NpcLifeStage.ADULT, NpcSex.FEMALE)
        ));
    }

    private static NpcLifeStage randomMinorStage(Random random) {
        return random.nextBoolean() ? NpcLifeStage.ADOLESCENT : NpcLifeStage.CHILD;
    }

    private static BlockPos resolveSafeSpawnPos(ServerLevel level, BlockPos preferredPos) {
        if (isSpawnSpaceClear(level, preferredPos)) {
            return preferredPos;
        }
        BlockPos abovePreferred = preferredPos.above();
        if (isSpawnSpaceClear(level, abovePreferred)) {
            return abovePreferred;
        }
        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, preferredPos);
        if (isSpawnSpaceClear(level, surfacePos)) {
            return surfacePos;
        }
        return surfacePos.above();
    }

    private static boolean isSpawnSpaceClear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }

    private record StarterHouseholdSeed(List<StarterResidentSeed> members) {
    }

    private record StarterResidentSeed(NpcLifeStage lifeStage, NpcSex sex) {
        private boolean isHouseholdHeadCandidate() {
            return this.lifeStage == NpcLifeStage.ADULT && this.sex == NpcSex.MALE;
        }
    }
}
