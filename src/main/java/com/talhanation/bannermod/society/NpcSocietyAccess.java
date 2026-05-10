package com.talhanation.bannermod.society;

import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.SettlementService;
import com.talhanation.bannermod.settlement.bootstrap.SettlementRecord;
import com.talhanation.bannermod.settlement.bootstrap.SettlementRegistryData;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NpcSocietyAccess {
    private NpcSocietyAccess() {
    }

    public static NpcSocietyProfile ensureResident(ServerLevel level, UUID residentUuid, long gameTime) {
        return NpcSocietySavedData.get(level).runtime().ensureResident(residentUuid, gameTime);
    }

    public static NpcSocietyProfile seedResident(ServerLevel level,
                                                 UUID residentUuid,
                                                 NpcLifeStage lifeStage,
                                                 NpcSex sex,
                                                 long gameTime) {
        return NpcSocietySavedData.get(level).runtime().seedResident(
                NpcSocietyProfile.createSeeded(residentUuid, lifeStage, sex, gameTime)
        );
    }

    public static NpcSocietyProfile ensureResidentForEntity(ServerLevel level, Entity entity) {
        if (level == null || entity == null) {
            throw new IllegalArgumentException("level and entity must not be null");
        }
        NpcSocietyRuntime runtime = NpcSocietySavedData.get(level).runtime();
        return runtime.profileFor(entity.getUUID()).orElseGet(() -> runtime.seedResident(seedProfileFor(entity, level.getGameTime())));
    }

    public static NpcSocietyProfile reconcilePhaseOneState(ServerLevel level,
                                                           UUID residentUuid,
                                                           @Nullable UUID householdId,
                                                           @Nullable UUID homeBuildingUuid,
                                                           @Nullable UUID workBuildingUuid,
                                                           NpcDailyPhase dailyPhase,
                                                           NpcIntent currentIntent,
                                                           NpcAnchorType currentAnchor,
                                                           @Nullable NpcSocietyDecisionSnapshot decisionSnapshot,
                                                           long gameTime) {
        return NpcSocietySavedData.get(level).runtime().reconcilePhaseOneState(
                residentUuid,
                householdId,
                homeBuildingUuid,
                workBuildingUuid,
                dailyPhase,
                currentIntent,
                currentAnchor,
                decisionSnapshot,
                gameTime
        );
    }

    public static Optional<NpcSocietyProfile> profileFor(ServerLevel level, UUID residentUuid) {
        return NpcSocietySavedData.get(level).runtime().profileFor(residentUuid);
    }

    public static NpcSocietyProfile reconcileNeedState(ServerLevel level,
                                                       UUID residentUuid,
                                                       int hungerNeed,
                                                       int fatigueNeed,
                                                       int safetyNeed,
                                                       long gameTime) {
        return NpcSocietySavedData.get(level).runtime().reconcileNeedState(
                residentUuid,
                hungerNeed,
                fatigueNeed,
                safetyNeed,
                gameTime
        );
    }

    public static NpcSocietyProfile moveResidentProfile(ServerLevel level,
                                                            UUID fromResidentUuid,
                                                            UUID toResidentUuid,
                                                           long gameTime) {
        NpcHouseholdAccess.moveResident(level, fromResidentUuid, toResidentUuid, gameTime);
        NpcFamilyAccess.moveResident(level, fromResidentUuid, toResidentUuid, gameTime);
        return NpcSocietySavedData.get(level).runtime().moveResident(fromResidentUuid, toResidentUuid, gameTime);
    }

    public static NpcPhaseOneSnapshot phaseOneSnapshot(ServerLevel level,
                                                       UUID residentUuid,
                                                       @Nullable UUID fallbackWorkBuildingUuid) {
        NpcSocietyProfile profile = ensureResident(level, residentUuid, level.getGameTime());
        UUID workBuildingUuid = authoritativeFallbackWorkBuildingUuid(
                level,
                residentUuid,
                profile.workBuildingUuid() != null ? profile.workBuildingUuid() : fallbackWorkBuildingUuid
        );
        NpcHouseholdRecord household = NpcHouseholdAccess.householdForResident(level, residentUuid).orElse(null);
        UUID householdId = household == null ? profile.householdId() : household.householdId();
        NpcHousingRequestRecord housingRequest = householdId == null ? null : NpcHousingRequestAccess.requestForHousehold(level, householdId);
        String housingUrgencyTag = "LOW";
        String housingReasonTag = "STABLE";
        int housingWaitingDays = 0;
        if (housingRequest != null
                && housingRequest.status() != NpcHousingRequestStatus.NONE
                && housingRequest.status() != NpcHousingRequestStatus.FULFILLED) {
            NpcHousingLedgerEntry housingEntry = NpcHousingPriorityService.describe(housingRequest, household, level.getGameTime());
            housingUrgencyTag = housingEntry.urgencyTag();
            housingReasonTag = housingEntry.reasonTag();
            housingWaitingDays = housingEntry.waitingDays();
        } else if (household == null || household.housingState() == NpcHouseholdHousingState.HOMELESS) {
            housingUrgencyTag = "CRITICAL";
            housingReasonTag = "HOMELESS";
        } else if (household.housingState() == NpcHouseholdHousingState.OVERCROWDED) {
            housingUrgencyTag = "HIGH";
            housingReasonTag = "OVERCROWDED";
        }
        NpcSocietyDecisionSnapshot decisionSnapshot = profile.decisionSnapshot() == null
                ? NpcSocietyDecisionSnapshot.empty()
                : profile.decisionSnapshot();
        return new NpcPhaseOneSnapshot(
                profile.lifeStage().name(),
                profile.sex().name(),
                householdId,
                household == null ? null : household.headResidentUuid(),
                profile.homeBuildingUuid(),
                workBuildingUuid,
                profile.cultureId(),
                profile.faithId(),
                profile.dailyPhase().name(),
                profile.currentIntent().name(),
                profile.currentAnchor().name(),
                decisionSnapshot.stateTag(),
                decisionSnapshot.currentGoalId(),
                decisionSnapshot.choiceReasonTag(),
                decisionSnapshot.routeReasonTag(),
                decisionSnapshot.blockedGoalId(),
                decisionSnapshot.blockedReasonTag(),
                household == null ? 0 : household.memberResidentUuids().size(),
                household == null ? NpcHouseholdHousingState.HOMELESS.name() : household.housingState().name(),
                profile.hungerNeed(),
                profile.fatigueNeed(),
                profile.safetyNeed(),
                NpcHousingRequestAccess.statusFor(level, residentUuid).name(),
                housingUrgencyTag,
                housingReasonTag,
                housingWaitingDays
        );
    }

    private static @Nullable UUID authoritativeFallbackWorkBuildingUuid(ServerLevel level,
                                                                        UUID residentUuid,
                                                                        @Nullable UUID fallbackWorkBuildingUuid) {
        if (level == null || residentUuid == null || fallbackWorkBuildingUuid == null || ClaimEvents.claimManager() == null) {
            return fallbackWorkBuildingUuid;
        }
        Entity fallbackWorkBuilding = level.getEntity(fallbackWorkBuildingUuid);
        RecruitsClaim claim = fallbackWorkBuilding == null
                ? null
                : ClaimEvents.claimManager().getClaim(new ChunkPos(fallbackWorkBuilding.blockPosition()));
        if (claim == null) {
            Entity residentEntity = level.getEntity(residentUuid);
            if (residentEntity == null) {
                return fallbackWorkBuildingUuid;
            }
            claim = ClaimEvents.claimManager().getClaim(new ChunkPos(residentEntity.blockPosition()));
        }
        if (claim == null) {
            return fallbackWorkBuildingUuid;
        }
        RecruitsClaim authoritativeClaim = claim;
        SettlementRecord settlementRecord = SettlementRegistryData.get(level).getSettlementByClaimId(claim.getUUID());
        if (settlementRecord == null) {
            return fallbackWorkBuildingUuid;
        }
        List<AbstractWorkAreaEntity> workAreas = level.getEntitiesOfClass(
                AbstractWorkAreaEntity.class,
                SettlementService.claimBounds(level, authoritativeClaim),
                area -> area != null && area.isAlive() && authoritativeClaim.containsChunk(area.chunkPosition())
        );
        List<ValidatedBuildingRecord> validatedBuildings = new ArrayList<>();
        for (ValidatedBuildingRecord record : ValidatedBuildingRegistryData.get(level).allRecords()) {
            if (record != null
                    && record.state() == BuildingValidationState.VALID
                    && level.dimension().equals(record.dimension())
                    && settlementRecord.settlementId().equals(record.settlementId())) {
                validatedBuildings.add(record);
            }
        }
        Map<UUID, UUID> authoritativeBindings = SettlementService.buildAuthoritativeWorkBuildingBindings(validatedBuildings, workAreas);
        return authoritativeBindings.getOrDefault(fallbackWorkBuildingUuid, fallbackWorkBuildingUuid);
    }

    public static NpcFamilyTreeSnapshot familyTreeSnapshot(ServerLevel level, UUID residentUuid) {
        return NpcFamilyAccess.familyTreeSnapshot(level, residentUuid, level.getGameTime());
    }

    private static NpcSocietyProfile seedProfileFor(Entity entity, long gameTime) {
        UUID residentUuid = entity.getUUID();
        NpcSex sex = ((residentUuid.getLeastSignificantBits() ^ residentUuid.getMostSignificantBits()) & 1L) == 0L
                ? NpcSex.MALE
                : NpcSex.FEMALE;
        NpcLifeStage stage = NpcLifeStage.ADULT;
        if (entity instanceof CitizenEntity && !(entity instanceof AbstractWorkerEntity) && !(entity instanceof AbstractRecruitEntity)) {
            stage = seededCivilianStage(residentUuid);
        }
        return NpcSocietyProfile.createSeeded(residentUuid, stage, sex, gameTime);
    }

    private static NpcLifeStage seededCivilianStage(UUID residentUuid) {
        int roll = Math.floorMod(residentUuid.hashCode(), 10);
        return roll <= 1 ? NpcLifeStage.ADOLESCENT : NpcLifeStage.ADULT;
    }
}
