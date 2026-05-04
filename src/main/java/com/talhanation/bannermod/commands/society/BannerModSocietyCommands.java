package com.talhanation.bannermod.commands.society;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.society.NpcHouseholdAccess;
import com.talhanation.bannermod.society.NpcHouseholdHousingState;
import com.talhanation.bannermod.society.NpcHouseholdRecord;
import com.talhanation.bannermod.society.NpcHousingRequestAccess;
import com.talhanation.bannermod.society.NpcHousingRequestRecord;
import com.talhanation.bannermod.society.NpcHousingRequestSavedData;
import com.talhanation.bannermod.society.NpcHousingRequestStatus;
import com.talhanation.bannermod.society.NpcLivelihoodRequestAccess;
import com.talhanation.bannermod.society.NpcLivelihoodRequestRecord;
import com.talhanation.bannermod.society.NpcLivelihoodRequestSavedData;
import com.talhanation.bannermod.society.NpcLivelihoodRequestStatus;
import com.talhanation.bannermod.society.NpcLivelihoodRequestType;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BannerModSocietyCommands {
    private BannerModSocietyCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("society")
                .then(Commands.literal("housing")
                        .then(Commands.literal("list")
                                .executes(BannerModSocietyCommands::listCurrentClaimRequests))
                        .then(Commands.literal("approve")
                                .then(Commands.argument("householdId", StringArgumentType.word())
                                        .executes(ctx -> updateRequestStatus(ctx, true))))
                        .then(Commands.literal("deny")
                                .then(Commands.argument("householdId", StringArgumentType.word())
                                        .executes(ctx -> updateRequestStatus(ctx, false)))))
                .then(Commands.literal("livelihood")
                        .then(Commands.literal("list")
                                .executes(BannerModSocietyCommands::listCurrentClaimLivelihoodRequests))
                        .then(Commands.literal("approve")
                                .then(Commands.argument("claimId", StringArgumentType.word())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(ctx -> updateLivelihoodRequestStatus(ctx, true)))))
                        .then(Commands.literal("deny")
                                .then(Commands.argument("claimId", StringArgumentType.word())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(ctx -> updateLivelihoodRequestStatus(ctx, false))))));
    }

    private static int listCurrentClaimRequests(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        RecruitsClaim claim = currentClaim(player);
        if (claim == null) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.housing_request.command.no_claim"));
            return 0;
        }
        PoliticalEntityRecord owner = ownerRecord(level, claim);
        if (!PoliticalEntityAuthority.canAct(player, owner)) {
            ctx.getSource().sendFailure(PoliticalEntityAuthority.denialReason(player.getUUID(), player.hasPermissions(2), owner));
            return 0;
        }

        List<NpcHousingRequestRecord> requests = new ArrayList<>(NpcHousingRequestSavedData.get(level).runtime().requestsForClaim(claim.getUUID()));
        requests.removeIf(request -> request == null
                || request.status() == NpcHousingRequestStatus.NONE
                || request.status() == NpcHousingRequestStatus.FULFILLED);
        requests.sort(Comparator
                .comparingInt((NpcHousingRequestRecord request) -> severity(level, request.householdId()))
                .thenComparingLong(NpcHousingRequestRecord::requestedAtGameTime));

        if (requests.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("gui.bannermod.society.housing_request.command.empty"), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("gui.bannermod.society.housing_request.command.header", requests.size()), false);
        for (NpcHousingRequestRecord request : requests) {
            NpcHouseholdRecord household = NpcHouseholdAccess.householdFor(level, request.householdId()).orElse(null);
            int members = household == null ? 0 : household.memberResidentUuids().size();
            Component state = household == null
                    ? Component.literal("unknown")
                    : Component.translatable("gui.bannermod.society.household_housing."
                    + household.housingState().name().toLowerCase(Locale.ROOT));
            Component status = Component.translatable("gui.bannermod.society.housing_request."
                    + request.status().name().toLowerCase(Locale.ROOT));
            Component plot = request.reservedPlotPos() == null
                    ? Component.literal("-")
                    : Component.literal(request.reservedPlotPos().getX() + " "
                    + request.reservedPlotPos().getY() + " "
                    + request.reservedPlotPos().getZ());
            MutableComponent line = Component.translatable(
                    "gui.bannermod.society.housing_request.command.entry",
                    shortId(request.residentUuid()),
                    state,
                    members,
                    status,
                    plot
            );
            if (request.status() == NpcHousingRequestStatus.REQUESTED || request.status() == NpcHousingRequestStatus.DENIED) {
                line.append(Component.literal(" "))
                        .append(actionButton(
                                "gui.bannermod.society.housing_request.action.approve",
                                "/bannermod society housing approve " + request.householdId(),
                                ChatFormatting.GREEN,
                                "gui.bannermod.society.housing_request.action.approve.tooltip"
                        ));
            }
            if (request.status() == NpcHousingRequestStatus.REQUESTED) {
                line.append(Component.literal(" "))
                        .append(actionButton(
                                "gui.bannermod.society.housing_request.action.deny",
                                "/bannermod society housing deny " + request.householdId(),
                                ChatFormatting.RED,
                                "gui.bannermod.society.housing_request.action.deny.tooltip"
                        ));
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int updateRequestStatus(CommandContext<CommandSourceStack> ctx, boolean approve) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        UUID householdId = parseUuid(ctx.getSource(), StringArgumentType.getString(ctx, "householdId"));
        if (householdId == null) {
            return 0;
        }
        NpcHousingRequestRecord request = NpcHousingRequestAccess.requestForHousehold(level, householdId);
        if (request == null) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.housing_request.command.not_found"));
            return 0;
        }
        RecruitsClaim claim = claimForRequest(request);
        if (claim == null) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.housing_request.command.no_claim"));
            return 0;
        }
        PoliticalEntityRecord owner = ownerRecord(level, claim);
        if (!PoliticalEntityAuthority.canAct(player, owner)) {
            ctx.getSource().sendFailure(PoliticalEntityAuthority.denialReason(player.getUUID(), player.hasPermissions(2), owner));
            return 0;
        }
        if (!approve && request.status() == NpcHousingRequestStatus.APPROVED) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.housing_request.command.approved_locked"));
            return 0;
        }
        if (request.status() == NpcHousingRequestStatus.FULFILLED) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.housing_request.command.fulfilled_locked"));
            return 0;
        }

        NpcHousingRequestRecord updated = approve
                ? NpcHousingRequestAccess.approveHousehold(level, householdId, level.getGameTime())
                : NpcHousingRequestAccess.denyHousehold(level, householdId, level.getGameTime());
        Component plot = updated.reservedPlotPos() == null
                ? Component.literal("-")
                : Component.literal(updated.reservedPlotPos().getX() + " "
                + updated.reservedPlotPos().getY() + " "
                + updated.reservedPlotPos().getZ());
        Component result = approve
                ? Component.translatable("gui.bannermod.society.housing_request.command.approved", shortId(updated.residentUuid()), plot)
                : Component.translatable("gui.bannermod.society.housing_request.command.denied", shortId(updated.residentUuid()), plot);
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int listCurrentClaimLivelihoodRequests(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        RecruitsClaim claim = currentClaim(player);
        if (claim == null) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.livelihood_request.command.no_claim"));
            return 0;
        }
        PoliticalEntityRecord owner = ownerRecord(level, claim);
        if (!PoliticalEntityAuthority.canAct(player, owner)) {
            ctx.getSource().sendFailure(PoliticalEntityAuthority.denialReason(player.getUUID(), player.hasPermissions(2), owner));
            return 0;
        }
        List<NpcLivelihoodRequestRecord> requests = new ArrayList<>(NpcLivelihoodRequestSavedData.get(level).runtime().requestsForClaim(claim.getUUID()));
        requests.removeIf(request -> request == null
                || request.status() == NpcLivelihoodRequestStatus.NONE
                || request.status() == NpcLivelihoodRequestStatus.FULFILLED);
        requests.sort(Comparator.comparingInt((NpcLivelihoodRequestRecord request) -> livelihoodSeverity(request.type()))
                .thenComparingLong(NpcLivelihoodRequestRecord::requestedAtGameTime));
        if (requests.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("gui.bannermod.society.livelihood_request.command.empty"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("gui.bannermod.society.livelihood_request.command.header", requests.size()), false);
        for (NpcLivelihoodRequestRecord request : requests) {
            Component type = Component.translatable("gui.bannermod.society.livelihood_request.type." + request.type().translationSuffix());
            Component status = Component.translatable("gui.bannermod.society.livelihood_request.status." + request.status().name().toLowerCase(Locale.ROOT));
            MutableComponent line = Component.translatable(
                    "gui.bannermod.society.livelihood_request.command.entry",
                    type,
                    shortId(request.representativeResidentUuid()),
                    status
            );
            if (request.status() == NpcLivelihoodRequestStatus.REQUESTED || request.status() == NpcLivelihoodRequestStatus.DENIED) {
                line.append(Component.literal(" "))
                        .append(actionButton(
                                "gui.bannermod.society.livelihood_request.action.approve",
                                "/bannermod society livelihood approve " + request.claimUuid() + " " + request.type().name(),
                                ChatFormatting.GREEN,
                                "gui.bannermod.society.livelihood_request.action.approve.tooltip"
                        ));
            }
            if (request.status() == NpcLivelihoodRequestStatus.REQUESTED) {
                line.append(Component.literal(" "))
                        .append(actionButton(
                                "gui.bannermod.society.livelihood_request.action.deny",
                                "/bannermod society livelihood deny " + request.claimUuid() + " " + request.type().name(),
                                ChatFormatting.RED,
                                "gui.bannermod.society.livelihood_request.action.deny.tooltip"
                        ));
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int updateLivelihoodRequestStatus(CommandContext<CommandSourceStack> ctx, boolean approve) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        UUID claimId = parseUuid(ctx.getSource(), StringArgumentType.getString(ctx, "claimId"), "gui.bannermod.society.livelihood_request.command.invalid_id");
        if (claimId == null) {
            return 0;
        }
        NpcLivelihoodRequestType type = NpcLivelihoodRequestType.fromName(StringArgumentType.getString(ctx, "type"));
        if (type == null) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.livelihood_request.command.invalid_type"));
            return 0;
        }
        NpcLivelihoodRequestRecord request = NpcLivelihoodRequestAccess.requestFor(level, claimId, type);
        if (request == null) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.livelihood_request.command.not_found"));
            return 0;
        }
        RecruitsClaim claim = claimForRequest(request);
        if (claim == null) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.livelihood_request.command.no_claim"));
            return 0;
        }
        PoliticalEntityRecord owner = ownerRecord(level, claim);
        if (!PoliticalEntityAuthority.canAct(player, owner)) {
            ctx.getSource().sendFailure(PoliticalEntityAuthority.denialReason(player.getUUID(), player.hasPermissions(2), owner));
            return 0;
        }
        if (!approve && request.status() == NpcLivelihoodRequestStatus.APPROVED) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.livelihood_request.command.approved_locked"));
            return 0;
        }
        if (request.status() == NpcLivelihoodRequestStatus.FULFILLED) {
            ctx.getSource().sendFailure(Component.translatable("gui.bannermod.society.livelihood_request.command.fulfilled_locked"));
            return 0;
        }
        NpcLivelihoodRequestRecord updated = approve
                ? NpcLivelihoodRequestAccess.approve(level, claimId, type, level.getGameTime())
                : NpcLivelihoodRequestAccess.deny(level, claimId, type, level.getGameTime());
        Component result = approve
                ? Component.translatable("gui.bannermod.society.livelihood_request.command.approved",
                Component.translatable("gui.bannermod.society.livelihood_request.type." + updated.type().translationSuffix()))
                : Component.translatable("gui.bannermod.society.livelihood_request.command.denied",
                Component.translatable("gui.bannermod.society.livelihood_request.type." + updated.type().translationSuffix()));
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    @Nullable
    private static UUID parseUuid(CommandSourceStack source, String raw) {
        return parseUuid(source, raw, "gui.bannermod.society.housing_request.command.invalid_id");
    }

    @Nullable
    private static UUID parseUuid(CommandSourceStack source, String raw, String invalidKey) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            source.sendFailure(Component.translatable(invalidKey));
            return null;
        }
    }

    @Nullable
    private static RecruitsClaim currentClaim(ServerPlayer player) {
        if (player == null || ClaimEvents.claimManager() == null) {
            return null;
        }
        return ClaimEvents.claimManager().getClaim(new ChunkPos(player.blockPosition()));
    }

    @Nullable
    private static RecruitsClaim claimForRequest(NpcHousingRequestRecord request) {
        if (request == null || request.claimUuid() == null || ClaimEvents.claimManager() == null) {
            return null;
        }
        return claimById(request.claimUuid());
    }

    @Nullable
    private static RecruitsClaim claimForRequest(NpcLivelihoodRequestRecord request) {
        if (request == null || request.claimUuid() == null || ClaimEvents.claimManager() == null) {
            return null;
        }
        return claimById(request.claimUuid());
    }

    @Nullable
    private static RecruitsClaim claimById(UUID claimUuid) {
        if (claimUuid == null || ClaimEvents.claimManager() == null) {
            return null;
        }
        for (RecruitsClaim claim : ClaimEvents.claimManager().getAllClaims()) {
            if (claim != null && claimUuid.equals(claim.getUUID())) {
                return claim;
            }
        }
        return null;
    }

    @Nullable
    private static PoliticalEntityRecord ownerRecord(ServerLevel level, RecruitsClaim claim) {
        if (level == null || claim == null || claim.getOwnerPoliticalEntityId() == null) {
            return null;
        }
        return WarRuntimeContext.registry(level).byId(claim.getOwnerPoliticalEntityId()).orElse(null);
    }

    private static int severity(ServerLevel level, UUID householdId) {
        NpcHouseholdHousingState state = NpcHouseholdAccess.householdFor(level, householdId)
                .map(NpcHouseholdRecord::housingState)
                .orElse(NpcHouseholdHousingState.NORMAL);
        return switch (state) {
            case HOMELESS -> 0;
            case OVERCROWDED -> 1;
            case NORMAL -> 2;
        };
    }

    private static int livelihoodSeverity(NpcLivelihoodRequestType type) {
        if (type == null) {
            return 99;
        }
        return switch (type) {
            case LUMBER_CAMP -> 0;
            case MINE -> 1;
            case ANIMAL_PEN -> 2;
        };
    }

    private static String shortId(@Nullable UUID uuid) {
        if (uuid == null) {
            return "?";
        }
        String raw = uuid.toString();
        return raw.length() > 8 ? raw.substring(0, 8) : raw;
    }

    private static MutableComponent actionButton(String labelKey,
                                                 String command,
                                                 ChatFormatting color,
                                                 String tooltipKey) {
        return Component.translatable(labelKey)
                .withStyle(style -> style.withColor(color)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(tooltipKey))));
    }
}
