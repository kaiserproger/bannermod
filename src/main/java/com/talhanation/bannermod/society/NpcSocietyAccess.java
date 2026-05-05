package com.talhanation.bannermod.society;

import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
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
                                                           long gameTime) {
        return NpcSocietySavedData.get(level).runtime().reconcilePhaseOneState(
                residentUuid,
                householdId,
                homeBuildingUuid,
                workBuildingUuid,
                dailyPhase,
                currentIntent,
                currentAnchor,
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
                                                       int socialNeed,
                                                       int safetyNeed,
                                                       long gameTime) {
        return NpcSocietySavedData.get(level).runtime().reconcileNeedState(
                residentUuid,
                hungerNeed,
                fatigueNeed,
                socialNeed,
                safetyNeed,
                gameTime
        );
    }

    public static NpcSocietyProfile reconcileSocialState(ServerLevel level,
                                                         UUID residentUuid,
                                                         int trustScore,
                                                         int fearScore,
                                                         int angerScore,
                                                         int gratitudeScore,
                                                         int loyaltyScore,
                                                         long gameTime) {
        return NpcSocietySavedData.get(level).runtime().reconcileSocialState(
                residentUuid,
                trustScore,
                fearScore,
                angerScore,
                gratitudeScore,
                loyaltyScore,
                gameTime
        );
    }

    public static NpcSocietyProfile moveResidentProfile(ServerLevel level,
                                                          UUID fromResidentUuid,
                                                          UUID toResidentUuid,
                                                          long gameTime) {
        NpcHouseholdAccess.moveResident(level, fromResidentUuid, toResidentUuid, gameTime);
        NpcFamilyAccess.moveResident(level, fromResidentUuid, toResidentUuid, gameTime);
        NpcMemorySavedData.get(level).runtime().moveResident(fromResidentUuid, toResidentUuid, gameTime);
        return NpcSocietySavedData.get(level).runtime().moveResident(fromResidentUuid, toResidentUuid, gameTime);
    }

    public static NpcPhaseOneSnapshot phaseOneSnapshot(ServerLevel level,
                                                       UUID residentUuid,
                                                       @Nullable UUID fallbackWorkBuildingUuid) {
        NpcSocietyProfile profile = NpcMemoryAccess.tickResidentState(
                level,
                ensureResident(level, residentUuid, level.getGameTime()),
                level.getGameTime()
        );
        UUID workBuildingUuid = profile.workBuildingUuid() != null ? profile.workBuildingUuid() : fallbackWorkBuildingUuid;
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
                household == null ? 0 : household.memberResidentUuids().size(),
                household == null ? NpcHouseholdHousingState.HOMELESS.name() : household.housingState().name(),
                profile.hungerNeed(),
                profile.fatigueNeed(),
                profile.socialNeed(),
                profile.safetyNeed(),
                profile.trustScore(),
                profile.fearScore(),
                profile.angerScore(),
                profile.gratitudeScore(),
                profile.loyaltyScore(),
                NpcHousingRequestAccess.statusFor(level, residentUuid).name(),
                housingUrgencyTag,
                housingReasonTag,
                housingWaitingDays,
                NpcMemoryAccess.summarySnapshots(level, residentUuid, level.getGameTime())
        );
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
