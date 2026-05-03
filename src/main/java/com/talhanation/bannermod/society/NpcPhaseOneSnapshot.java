package com.talhanation.bannermod.society;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public record NpcPhaseOneSnapshot(
        String lifeStageTag,
        String sexTag,
        @Nullable UUID householdId,
        @Nullable UUID homeBuildingUuid,
        @Nullable UUID workBuildingUuid,
        @Nullable String cultureId,
        @Nullable String faithId,
        String dailyPhaseTag,
        String currentIntentTag,
        String currentAnchorTag,
        int householdSize,
        String householdHousingStateTag,
        int hungerNeed,
        int fatigueNeed,
        int socialNeed,
        int safetyNeed,
        String housingRequestStatusTag
) {
    public static NpcPhaseOneSnapshot empty() {
        return new NpcPhaseOneSnapshot(
                NpcLifeStage.UNSPECIFIED.name(),
                NpcSex.UNSPECIFIED.name(),
                null,
                null,
                null,
                null,
                null,
                NpcDailyPhase.UNSPECIFIED.name(),
                NpcIntent.UNSPECIFIED.name(),
                NpcAnchorType.NONE.name(),
                0,
                NpcHouseholdHousingState.HOMELESS.name(),
                0,
                0,
                0,
                0,
                NpcHousingRequestStatus.NONE.name()
        );
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(safeTag(this.lifeStageTag));
        buf.writeUtf(safeTag(this.sexTag));
        writeNullableUuid(buf, this.householdId);
        writeNullableUuid(buf, this.homeBuildingUuid);
        writeNullableUuid(buf, this.workBuildingUuid);
        writeNullableString(buf, this.cultureId);
        writeNullableString(buf, this.faithId);
        buf.writeUtf(safeTag(this.dailyPhaseTag));
        buf.writeUtf(safeTag(this.currentIntentTag));
        buf.writeUtf(safeTag(this.currentAnchorTag));
        buf.writeVarInt(Math.max(0, this.householdSize));
        buf.writeUtf(safeTag(this.householdHousingStateTag));
        buf.writeVarInt(Math.max(0, this.hungerNeed));
        buf.writeVarInt(Math.max(0, this.fatigueNeed));
        buf.writeVarInt(Math.max(0, this.socialNeed));
        buf.writeVarInt(Math.max(0, this.safetyNeed));
        buf.writeUtf(safeTag(this.housingRequestStatusTag));
    }

    public static NpcPhaseOneSnapshot fromBytes(FriendlyByteBuf buf) {
        return new NpcPhaseOneSnapshot(
                buf.readUtf(),
                buf.readUtf(),
                readNullableUuid(buf),
                readNullableUuid(buf),
                readNullableUuid(buf),
                readNullableString(buf),
                readNullableString(buf),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf()
        );
    }

    public String lifeStageTranslationKey() {
        return "gui.bannermod.society.life_stage." + safeTag(this.lifeStageTag).toLowerCase(Locale.ROOT);
    }

    public String sexTranslationKey() {
        return "gui.bannermod.society.sex." + safeTag(this.sexTag).toLowerCase(Locale.ROOT);
    }

    public String dailyPhaseTranslationKey() {
        return "gui.bannermod.society.daily_phase." + safeTag(this.dailyPhaseTag).toLowerCase(Locale.ROOT);
    }

    public String currentIntentTranslationKey() {
        return "gui.bannermod.society.intent." + safeTag(this.currentIntentTag).toLowerCase(Locale.ROOT);
    }

    public String currentAnchorTranslationKey() {
        return "gui.bannermod.society.anchor." + safeTag(this.currentAnchorTag).toLowerCase(Locale.ROOT);
    }

    public String householdHousingStateTranslationKey() {
        return "gui.bannermod.society.household_housing." + safeTag(this.householdHousingStateTag).toLowerCase(Locale.ROOT);
    }

    public String housingRequestTranslationKey() {
        return "gui.bannermod.society.housing_request." + safeTag(this.housingRequestStatusTag).toLowerCase(Locale.ROOT);
    }

    public String cultureLabel() {
        return this.cultureId == null || this.cultureId.isBlank() ? "-" : this.cultureId;
    }

    public String faithLabel() {
        return this.faithId == null || this.faithId.isBlank() ? "-" : this.faithId;
    }

    public static String shortId(@Nullable UUID uuid) {
        return uuid == null ? "-" : uuid.toString().substring(0, 8);
    }

    private static void writeNullableUuid(FriendlyByteBuf buf, @Nullable UUID value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeUUID(value);
        }
    }

    private static @Nullable UUID readNullableUuid(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    private static void writeNullableString(FriendlyByteBuf buf, @Nullable String value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeUtf(value);
        }
    }

    private static @Nullable String readNullableString(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }

    private static String safeTag(@Nullable String value) {
        return value == null || value.isBlank() ? "UNSPECIFIED" : value;
    }
}
