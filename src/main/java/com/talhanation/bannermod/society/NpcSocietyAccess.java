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
                                                       long gameTime) {
        return NpcSocietySavedData.get(level).runtime().reconcileNeedState(
                residentUuid,
                hungerNeed,
                fatigueNeed,
                socialNeed,
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
        UUID workBuildingUuid = profile.workBuildingUuid() != null ? profile.workBuildingUuid() : fallbackWorkBuildingUuid;
        NpcHouseholdRecord household = NpcHouseholdAccess.householdForResident(level, residentUuid).orElse(null);
        UUID householdId = household == null ? profile.householdId() : household.householdId();
        return new NpcPhaseOneSnapshot(
                profile.lifeStage().name(),
                profile.sex().name(),
                householdId,
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
                NpcHousingRequestAccess.statusFor(level, residentUuid).name()
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
