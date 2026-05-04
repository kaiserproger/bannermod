package com.talhanation.bannermod.society;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectBlocker;
import com.talhanation.bannermod.settlement.growth.ProjectKind;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NpcHousingProjectPlanner {
    private static final int HOUSE_REQUEST_PRIORITY = 880;
    private static final int HOUSE_REQUEST_TICK_COST = 500;

    private NpcHousingProjectPlanner() {
    }

    public static List<PendingProject> collectApprovedHouseProjects(ServerLevel level,
                                                                    BannerModSettlementSnapshot snapshot,
                                                                    BannerModHomeAssignmentRuntime homeRuntime,
                                                                    long gameTime) {
        if (level == null || snapshot == null || homeRuntime == null) {
            return List.of();
        }
        UUID lordUuid = resolveLordUuid(level, snapshot.claimUuid());
        List<PendingProject> projects = new ArrayList<>();
        Set<UUID> visitedHouseholds = new LinkedHashSet<>();
        for (BannerModSettlementResidentRecord resident : snapshot.residents()) {
            if (resident == null || resident.residentUuid() == null) {
                continue;
            }
            UUID residentUuid = resident.residentUuid();
            NpcHouseholdRecord household = NpcHouseholdAccess.householdForResident(level, residentUuid).orElse(null);
            if (household == null || !visitedHouseholds.add(household.householdId())) {
                continue;
            }
            if (household.housingState() == NpcHouseholdHousingState.NORMAL) {
                NpcHousingRequestAccess.markFulfilled(level, residentUuid, gameTime);
                continue;
            }
            UUID requesterResidentUuid = pickRequesterResident(level, household, gameTime);
            if (requesterResidentUuid == null) {
                continue;
            }
            NpcHousingRequestRecord request = NpcHousingRequestAccess.requestHouse(
                    level,
                    household.householdId(),
                    requesterResidentUuid,
                    snapshot.claimUuid(),
                    lordUuid,
                    gameTime
            );
            request = NpcHousingPlotPlanner.ensureReservedPlot(level, snapshot, request, gameTime);
            if (request.status() == NpcHousingRequestStatus.REQUESTED) {
                if (request.requestedAtGameTime() == gameTime) {
                    notifyLord(level, request, household);
                }
            }
            if (request.status() == NpcHousingRequestStatus.APPROVED) {
                projects.add(new PendingProject(
                        request.projectId(),
                        ProjectKind.NEW_BUILDING,
                        null,
                        null,
                        BannerModSettlementBuildingProfileSeed.GENERAL.category(),
                        BannerModSettlementBuildingProfileSeed.GENERAL,
                        HOUSE_REQUEST_PRIORITY,
                        gameTime,
                        HOUSE_REQUEST_TICK_COST,
                        ProjectBlocker.NONE
                ));
            }
        }
        return projects;
    }

    public static Set<UUID> approvedRequesterIdsForClaim(ServerLevel level, UUID claimUuid) {
        if (level == null || claimUuid == null) {
            return Set.of();
        }
        Set<UUID> ordered = new LinkedHashSet<>();
        for (NpcHousingRequestRecord request : NpcHousingRequestSavedData.get(level).runtime().requestsForClaim(claimUuid)) {
            if (request != null && request.status() == NpcHousingRequestStatus.APPROVED) {
                ordered.add(request.residentUuid());
            }
        }
        return ordered;
    }

    @Nullable
    private static UUID pickRequesterResident(ServerLevel level,
                                              NpcHouseholdRecord household,
                                              long gameTime) {
        UUID fallback = null;
        for (UUID memberResidentUuid : household.memberResidentUuids()) {
            if (memberResidentUuid == null) {
                continue;
            }
            if (fallback == null) {
                fallback = memberResidentUuid;
            }
            NpcSocietyProfile profile = NpcSocietyAccess.ensureResident(level, memberResidentUuid, gameTime);
            if (profile.lifeStage() == NpcLifeStage.ADULT || profile.lifeStage() == NpcLifeStage.ELDER) {
                return memberResidentUuid;
            }
        }
        return fallback;
    }

    @Nullable
    private static UUID resolveLordUuid(ServerLevel level, @Nullable UUID claimUuid) {
        if (level == null || claimUuid == null || ClaimEvents.claimManager() == null) {
            return null;
        }
        RecruitsClaim claim = null;
        for (RecruitsClaim candidate : ClaimEvents.claimManager().getAllClaims()) {
            if (candidate != null && claimUuid.equals(candidate.getUUID())) {
                claim = candidate;
                break;
            }
        }
        if (claim == null || claim.getOwnerPoliticalEntityId() == null) {
            return null;
        }
        PoliticalEntityRecord owner = WarRuntimeContext.registry(level).byId(claim.getOwnerPoliticalEntityId()).orElse(null);
        return owner == null ? null : owner.leaderUuid();
    }

    private static void notifyLord(ServerLevel level,
                                   NpcHousingRequestRecord request,
                                   NpcHouseholdRecord household) {
        if (level == null || request == null || request.lordPlayerUuid() == null || household == null) {
            return;
        }
        ServerPlayer lord = level.getServer().getPlayerList().getPlayer(request.lordPlayerUuid());
        if (lord == null) {
            return;
        }
        String householdId = household.householdId().toString();
        MutableComponent approve = actionButton(
                "gui.bannermod.society.housing_request.action.approve",
                "/bannermod society housing approve " + householdId,
                ChatFormatting.GREEN,
                "gui.bannermod.society.housing_request.action.approve.tooltip"
        );
        MutableComponent deny = actionButton(
                "gui.bannermod.society.housing_request.action.deny",
                "/bannermod society housing deny " + householdId,
                ChatFormatting.RED,
                "gui.bannermod.society.housing_request.action.deny.tooltip"
        );
        MutableComponent list = actionButton(
                "gui.bannermod.society.housing_request.action.list",
                "/bannermod society housing list",
                ChatFormatting.GOLD,
                "gui.bannermod.society.housing_request.action.list.tooltip"
        );
        Component housingState = Component.translatable(
                "gui.bannermod.society.household_housing."
                        + household.housingState().name().toLowerCase(java.util.Locale.ROOT)
        );
        Component plotPos = request.reservedPlotPos() == null
                ? Component.literal("-")
                : Component.literal(request.reservedPlotPos().getX() + " "
                + request.reservedPlotPos().getY() + " "
                + request.reservedPlotPos().getZ());
        lord.sendSystemMessage(Component.translatable(
                "gui.bannermod.society.housing_request.notice",
                request.residentUuid().toString().substring(0, 8),
                housingState,
                household.memberResidentUuids().size(),
                plotPos
        ).append(Component.literal(" "))
                .append(approve)
                .append(Component.literal(" "))
                .append(deny)
                .append(Component.literal(" "))
                .append(list));
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
