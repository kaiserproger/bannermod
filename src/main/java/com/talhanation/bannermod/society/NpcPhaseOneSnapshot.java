package com.talhanation.bannermod.society;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public record NpcPhaseOneSnapshot(
        String lifeStageTag,
        String sexTag,
        @Nullable UUID householdId,
        @Nullable UUID householdHeadResidentUuid,
        @Nullable UUID homeBuildingUuid,
        @Nullable UUID workBuildingUuid,
        @Nullable String cultureId,
        @Nullable String faithId,
        String dailyPhaseTag,
        String currentIntentTag,
        String currentAnchorTag,
        String aiStateTag,
        @Nullable String aiCurrentGoalId,
        String aiChoiceReasonTag,
        String aiRouteReasonTag,
        @Nullable String aiBlockedGoalId,
        String aiBlockedReasonTag,
        int householdSize,
        String householdHousingStateTag,
        int hungerNeed,
        int fatigueNeed,
        int safetyNeed,
        String housingRequestStatusTag,
        String housingUrgencyTag,
        String housingReasonTag,
        int housingWaitingDays
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
                null,
                NpcDailyPhase.UNSPECIFIED.name(),
                NpcIntent.UNSPECIFIED.name(),
                NpcAnchorType.NONE.name(),
                "IDLE",
                null,
                "NO_STARTABLE_GOAL",
                "NO_CLEAR_ROUTE",
                null,
                "NONE",
                0,
                NpcHouseholdHousingState.HOMELESS.name(),
                0,
                0,
                0,
                NpcHousingRequestStatus.NONE.name(),
                "LOW",
                "STABLE",
                0
        );
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(safeTag(this.lifeStageTag));
        buf.writeUtf(safeTag(this.sexTag));
        writeNullableUuid(buf, this.householdId);
        writeNullableUuid(buf, this.householdHeadResidentUuid);
        writeNullableUuid(buf, this.homeBuildingUuid);
        writeNullableUuid(buf, this.workBuildingUuid);
        writeNullableString(buf, this.cultureId);
        writeNullableString(buf, this.faithId);
        buf.writeUtf(safeTag(this.dailyPhaseTag));
        buf.writeUtf(safeTag(this.currentIntentTag));
        buf.writeUtf(safeTag(this.currentAnchorTag));
        buf.writeUtf(safeTag(this.aiStateTag));
        writeNullableString(buf, this.aiCurrentGoalId);
        buf.writeUtf(safeTag(this.aiChoiceReasonTag));
        buf.writeUtf(safeTag(this.aiRouteReasonTag));
        writeNullableString(buf, this.aiBlockedGoalId);
        buf.writeUtf(safeTag(this.aiBlockedReasonTag));
        buf.writeVarInt(Math.max(0, this.householdSize));
        buf.writeUtf(safeTag(this.householdHousingStateTag));
        buf.writeVarInt(Math.max(0, this.hungerNeed));
        buf.writeVarInt(Math.max(0, this.fatigueNeed));
        buf.writeVarInt(Math.max(0, this.safetyNeed));
        buf.writeUtf(safeTag(this.housingRequestStatusTag));
        buf.writeUtf(safeTag(this.housingUrgencyTag));
        buf.writeUtf(safeTag(this.housingReasonTag));
        buf.writeVarInt(Math.max(0, this.housingWaitingDays));
    }

    public static NpcPhaseOneSnapshot fromBytes(FriendlyByteBuf buf) {
        String lifeStageTag = buf.readUtf();
        String sexTag = buf.readUtf();
        UUID householdId = readNullableUuid(buf);
        UUID householdHeadResidentUuid = readNullableUuid(buf);
        UUID homeBuildingUuid = readNullableUuid(buf);
        UUID workBuildingUuid = readNullableUuid(buf);
        String cultureId = readNullableString(buf);
        String faithId = readNullableString(buf);
        String dailyPhaseTag = buf.readUtf();
        String currentIntentTag = buf.readUtf();
        String currentAnchorTag = buf.readUtf();
        String aiStateTag = buf.readUtf();
        String aiCurrentGoalId = readNullableString(buf);
        String aiChoiceReasonTag = buf.readUtf();
        String aiRouteReasonTag = buf.readUtf();
        String aiBlockedGoalId = readNullableString(buf);
        String aiBlockedReasonTag = buf.readUtf();
        int householdSize = buf.readVarInt();
        String householdHousingStateTag = buf.readUtf();
        int hungerNeed = buf.readVarInt();
        int fatigueNeed = buf.readVarInt();
        int safetyNeed = buf.readVarInt();
        String housingRequestStatusTag = buf.readUtf();
        String housingUrgencyTag = buf.readUtf();
        String housingReasonTag = buf.readUtf();
        int housingWaitingDays = buf.readVarInt();
        return new NpcPhaseOneSnapshot(
                lifeStageTag,
                sexTag,
                householdId,
                householdHeadResidentUuid,
                homeBuildingUuid,
                workBuildingUuid,
                cultureId,
                faithId,
                dailyPhaseTag,
                currentIntentTag,
                currentAnchorTag,
                aiStateTag,
                aiCurrentGoalId,
                aiChoiceReasonTag,
                aiRouteReasonTag,
                aiBlockedGoalId,
                aiBlockedReasonTag,
                householdSize,
                householdHousingStateTag,
                hungerNeed,
                fatigueNeed,
                safetyNeed,
                housingRequestStatusTag,
                housingUrgencyTag,
                housingReasonTag,
                housingWaitingDays
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

    public String aiStateTranslationKey() {
        return "gui.bannermod.society.ai.state." + safeTag(this.aiStateTag).toLowerCase(Locale.ROOT);
    }

    public String aiChoiceReasonTranslationKey() {
        return "gui.bannermod.society.ai.reason." + safeTag(this.aiChoiceReasonTag).toLowerCase(Locale.ROOT);
    }

    public String aiBlockedReasonTranslationKey() {
        return "gui.bannermod.society.ai.reason." + safeTag(this.aiBlockedReasonTag).toLowerCase(Locale.ROOT);
    }

    public String aiRouteReasonTranslationKey() {
        return "gui.bannermod.society.ai.route." + safeTag(this.aiRouteReasonTag).toLowerCase(Locale.ROOT);
    }

    public boolean isRecoveringState() {
        return "RECOVERING".equalsIgnoreCase(this.aiStateTag);
    }

    public boolean isBlockedState() {
        return "BLOCKED".equalsIgnoreCase(this.aiStateTag) || this.isRecoveringState();
    }

    public boolean hasAiCurrentGoal() {
        return this.aiCurrentGoalId != null && !this.aiCurrentGoalId.isBlank();
    }

    public boolean hasAiBlockedGoal() {
        return this.aiBlockedGoalId != null && !this.aiBlockedGoalId.isBlank();
    }

    public String householdHousingStateTranslationKey() {
        return "gui.bannermod.society.household_housing." + safeTag(this.householdHousingStateTag).toLowerCase(Locale.ROOT);
    }

    public String housingRequestTranslationKey() {
        return "gui.bannermod.society.housing_request." + safeTag(this.housingRequestStatusTag).toLowerCase(Locale.ROOT);
    }

    public String housingUrgencyTranslationKey() {
        return "gui.bannermod.housing_ledger.urgency." + safeTag(this.housingUrgencyTag).toLowerCase(Locale.ROOT);
    }

    public String housingReasonTranslationKey() {
        return "gui.bannermod.housing_ledger.reason." + safeTag(this.housingReasonTag).toLowerCase(Locale.ROOT);
    }

    public String householdRoleTranslationKey(@Nullable UUID residentUuid) {
        if (residentUuid == null || this.householdId == null) {
            return "gui.bannermod.society.household_role.unknown";
        }
        if (this.householdHeadResidentUuid != null && this.householdHeadResidentUuid.equals(residentUuid)) {
            return "gui.bannermod.society.household_role.head";
        }
        return "gui.bannermod.society.household_role.member";
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

    public String aiCurrentGoalLabel() {
        return NpcSocietyDecisionSnapshot.goalLabelOrDash(this.aiCurrentGoalId);
    }

    public String aiBlockedGoalLabel() {
        return NpcSocietyDecisionSnapshot.goalLabelOrDash(this.aiBlockedGoalId);
    }

    public Component aiCurrentGoalComponent() {
        return goalComponent(this.aiCurrentGoalId);
    }

    public Component aiBlockedGoalComponent() {
        return goalComponent(this.aiBlockedGoalId);
    }

    public Component aiRouteSecondaryComponent() {
        if ((this.isBlockedState() || !this.hasAiCurrentGoal()) && this.hasAiBlockedGoal()) {
            return Component.translatable(
                    "gui.bannermod.society.ai.route.blocked_detail",
                    this.aiBlockedGoalComponent(),
                    Component.translatable(this.aiBlockedReasonTranslationKey())
            );
        }
        return Component.translatable(
                "gui.bannermod.society.ai.route.target",
                Component.translatable(this.currentAnchorTranslationKey())
        );
    }

    public Component aiReadableRoutineReasonComponent() {
        Component route = Component.translatable(this.aiRouteReasonTranslationKey());
        if ((this.isBlockedState() || !this.hasAiCurrentGoal()) && this.hasAiBlockedGoal()) {
            return Component.translatable(
                    "gui.bannermod.society.ai.route.blocked_summary",
                    this.aiBlockedGoalComponent(),
                    Component.translatable(this.aiBlockedReasonTranslationKey())
            );
        }
        return route;
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

    private static Component goalComponent(@Nullable String goalId) {
        String label = NpcSocietyDecisionSnapshot.goalLabelOrDash(goalId);
        return switch (label) {
            case "-" -> Component.translatable("gui.bannermod.common.none");
            case "go_home" -> Component.translatable("gui.bannermod.society.ai.goal.go_home");
            case "leave_home" -> Component.translatable("gui.bannermod.society.ai.goal.leave_home");
            case "rest" -> Component.translatable("gui.bannermod.society.ai.goal.rest");
            case "eat" -> Component.translatable("gui.bannermod.society.ai.goal.eat");
            case "seek_supplies" -> Component.translatable("gui.bannermod.society.ai.goal.seek_supplies");
            case "hide" -> Component.translatable("gui.bannermod.society.ai.goal.hide");
            case "defend" -> Component.translatable("gui.bannermod.society.ai.goal.defend");
            case "work" -> Component.translatable("gui.bannermod.society.ai.goal.work");
            case "sell", "seller" -> Component.translatable("gui.bannermod.society.ai.goal.sell");
            case "fetch" -> Component.translatable("gui.bannermod.society.ai.goal.fetch");
            case "deliver" -> Component.translatable("gui.bannermod.society.ai.goal.deliver");
            case "idle" -> Component.translatable("gui.bannermod.society.ai.goal.idle");
            default -> Component.literal(label.replace('_', ' '));
        };
    }
}
