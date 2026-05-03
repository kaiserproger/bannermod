package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record NpcSocietyProfile(
        UUID residentUuid,
        NpcLifeStage lifeStage,
        NpcSex sex,
        @Nullable UUID householdId,
        @Nullable UUID homeBuildingUuid,
        @Nullable UUID workBuildingUuid,
        @Nullable String cultureId,
        @Nullable String faithId,
        NpcDailyPhase dailyPhase,
        NpcIntent currentIntent,
        NpcAnchorType currentAnchor,
        int hungerNeed,
        int fatigueNeed,
        int socialNeed,
        long version,
        long lastUpdatedGameTime
) {
    public static NpcSocietyProfile createDefault(UUID residentUuid, long gameTime) {
        if (residentUuid == null) {
            throw new IllegalArgumentException("residentUuid must not be null");
        }
        return new NpcSocietyProfile(
                residentUuid,
                NpcLifeStage.ADULT,
                defaultSexFor(residentUuid),
                null,
                null,
                null,
                null,
                null,
                NpcDailyPhase.UNSPECIFIED,
                NpcIntent.UNSPECIFIED,
                NpcAnchorType.NONE,
                10,
                10,
                10,
                1L,
                gameTime
        );
    }

    public static NpcSocietyProfile createSeeded(UUID residentUuid,
                                                 NpcLifeStage lifeStage,
                                                 NpcSex sex,
                                                 long gameTime) {
        NpcSocietyProfile profile = createDefault(residentUuid, gameTime);
        return new NpcSocietyProfile(
                residentUuid,
                lifeStage == null ? NpcLifeStage.ADULT : lifeStage,
                sex == null ? defaultSexFor(residentUuid) : sex,
                profile.householdId,
                profile.homeBuildingUuid,
                profile.workBuildingUuid,
                profile.cultureId,
                profile.faithId,
                profile.dailyPhase,
                profile.currentIntent,
                profile.currentAnchor,
                profile.hungerNeed,
                profile.fatigueNeed,
                profile.socialNeed,
                profile.version,
                gameTime
        );
    }

    public NpcSocietyProfile withPhaseOneState(@Nullable UUID householdId,
                                              @Nullable UUID homeBuildingUuid,
                                              @Nullable UUID workBuildingUuid,
                                              NpcDailyPhase dailyPhase,
                                              NpcIntent currentIntent,
                                              NpcAnchorType currentAnchor,
                                              long gameTime) {
        if (sameNullableUuid(this.householdId, householdId)
                && sameNullableUuid(this.homeBuildingUuid, homeBuildingUuid)
                && sameNullableUuid(this.workBuildingUuid, workBuildingUuid)
                && sameEnum(this.dailyPhase, dailyPhase)
                && sameEnum(this.currentIntent, currentIntent)
                && sameEnum(this.currentAnchor, currentAnchor)) {
            return this;
        }
        return new NpcSocietyProfile(
                this.residentUuid,
                this.lifeStage,
                this.sex,
                householdId,
                homeBuildingUuid,
                workBuildingUuid,
                this.cultureId,
                this.faithId,
                dailyPhase == null ? NpcDailyPhase.UNSPECIFIED : dailyPhase,
                currentIntent == null ? NpcIntent.UNSPECIFIED : currentIntent,
                currentAnchor == null ? NpcAnchorType.NONE : currentAnchor,
                this.hungerNeed,
                this.fatigueNeed,
                this.socialNeed,
                this.version + 1L,
                gameTime
        );
    }

    public NpcSocietyProfile withNeedState(int hungerNeed,
                                           int fatigueNeed,
                                           int socialNeed,
                                           long gameTime) {
        int clampedHunger = clampNeed(hungerNeed);
        int clampedFatigue = clampNeed(fatigueNeed);
        int clampedSocial = clampNeed(socialNeed);
        if (this.hungerNeed == clampedHunger && this.fatigueNeed == clampedFatigue && this.socialNeed == clampedSocial) {
            return this;
        }
        return new NpcSocietyProfile(
                this.residentUuid,
                this.lifeStage,
                this.sex,
                this.householdId,
                this.homeBuildingUuid,
                this.workBuildingUuid,
                this.cultureId,
                this.faithId,
                this.dailyPhase,
                this.currentIntent,
                this.currentAnchor,
                clampedHunger,
                clampedFatigue,
                clampedSocial,
                this.version + 1L,
                gameTime
        );
    }

    public NpcSocietyProfile moveToResident(UUID residentUuid, long gameTime) {
        if (residentUuid == null) {
            throw new IllegalArgumentException("residentUuid must not be null");
        }
        if (residentUuid.equals(this.residentUuid)) {
            return this;
        }
        return new NpcSocietyProfile(
                residentUuid,
                this.lifeStage,
                this.sex,
                this.householdId,
                this.homeBuildingUuid,
                this.workBuildingUuid,
                this.cultureId,
                this.faithId,
                this.dailyPhase,
                this.currentIntent,
                this.currentAnchor,
                this.hungerNeed,
                this.fatigueNeed,
                this.socialNeed,
                this.version + 1L,
                gameTime
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ResidentUuid", this.residentUuid);
        tag.putString("LifeStage", (this.lifeStage == null ? NpcLifeStage.UNSPECIFIED : this.lifeStage).name());
        tag.putString("Sex", (this.sex == null ? NpcSex.UNSPECIFIED : this.sex).name());
        if (this.householdId != null) {
            tag.putUUID("HouseholdId", this.householdId);
        }
        if (this.homeBuildingUuid != null) {
            tag.putUUID("HomeBuildingUuid", this.homeBuildingUuid);
        }
        if (this.workBuildingUuid != null) {
            tag.putUUID("WorkBuildingUuid", this.workBuildingUuid);
        }
        if (this.cultureId != null && !this.cultureId.isBlank()) {
            tag.putString("CultureId", this.cultureId);
        }
        if (this.faithId != null && !this.faithId.isBlank()) {
            tag.putString("FaithId", this.faithId);
        }
        tag.putString("DailyPhase", (this.dailyPhase == null ? NpcDailyPhase.UNSPECIFIED : this.dailyPhase).name());
        tag.putString("CurrentIntent", (this.currentIntent == null ? NpcIntent.UNSPECIFIED : this.currentIntent).name());
        tag.putString("CurrentAnchor", (this.currentAnchor == null ? NpcAnchorType.NONE : this.currentAnchor).name());
        tag.putInt("HungerNeed", this.hungerNeed);
        tag.putInt("FatigueNeed", this.fatigueNeed);
        tag.putInt("SocialNeed", this.socialNeed);
        tag.putLong("Version", this.version);
        tag.putLong("LastUpdatedGameTime", this.lastUpdatedGameTime);
        return tag;
    }

    public static NpcSocietyProfile fromTag(CompoundTag tag) {
        UUID residentUuid = tag.getUUID("ResidentUuid");
        return new NpcSocietyProfile(
                residentUuid,
                NpcLifeStage.fromName(tag.getString("LifeStage")),
                NpcSex.fromName(tag.getString("Sex")),
                tag.contains("HouseholdId") ? tag.getUUID("HouseholdId") : null,
                tag.contains("HomeBuildingUuid") ? tag.getUUID("HomeBuildingUuid") : null,
                tag.contains("WorkBuildingUuid") ? tag.getUUID("WorkBuildingUuid") : null,
                tag.contains("CultureId") ? tag.getString("CultureId") : null,
                tag.contains("FaithId") ? tag.getString("FaithId") : null,
                NpcDailyPhase.fromName(tag.getString("DailyPhase")),
                NpcIntent.fromName(tag.getString("CurrentIntent")),
                NpcAnchorType.fromName(tag.getString("CurrentAnchor")),
                clampNeed(tag.getInt("HungerNeed")),
                clampNeed(tag.getInt("FatigueNeed")),
                clampNeed(tag.getInt("SocialNeed")),
                Math.max(1L, tag.getLong("Version")),
                tag.getLong("LastUpdatedGameTime")
        );
    }

    private static NpcSex defaultSexFor(UUID residentUuid) {
        long bits = residentUuid.getLeastSignificantBits() ^ residentUuid.getMostSignificantBits();
        return (bits & 1L) == 0L ? NpcSex.MALE : NpcSex.FEMALE;
    }

    private static boolean sameNullableUuid(@Nullable UUID left, @Nullable UUID right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean sameEnum(@Nullable Enum<?> left, @Nullable Enum<?> right) {
        return left == null ? right == null : left.equals(right);
    }

    private static int clampNeed(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
