package com.talhanation.bannermod.society;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectBlocker;
import com.talhanation.bannermod.settlement.growth.ProjectKind;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NpcLivelihoodProjectPlanner {
    private static final int LUMBER_PRIORITY = 860;
    private static final int MINE_PRIORITY = 840;
    private static final int ANIMAL_PEN_PRIORITY = 820;
    private static final int PROJECT_TICK_COST = 20 * 60;

    private NpcLivelihoodProjectPlanner() {
    }

    public static List<PendingProject> collectApprovedProjects(ServerLevel level,
                                                               BannerModSettlementSnapshot snapshot,
                                                               long gameTime) {
        if (level == null || snapshot == null || snapshot.claimUuid() == null) {
            return List.of();
        }
        UUID lordUuid = resolveLordUuid(level, snapshot.claimUuid());
        List<PendingProject> projects = new ArrayList<>();
        for (NpcLivelihoodRequestType type : NpcLivelihoodRequestType.values()) {
            if (!shouldRequest(snapshot, type)) {
                continue;
            }
            if (hasLivelihoodBuilding(snapshot, type)) {
                NpcLivelihoodRequestAccess.fulfill(level, snapshot.claimUuid(), type, gameTime);
                continue;
            }
            UUID requester = pickRepresentative(snapshot, type);
            if (requester == null) {
                continue;
            }
            NpcLivelihoodRequestRecord request = NpcLivelihoodRequestAccess.request(
                    level,
                    snapshot.claimUuid(),
                    requester,
                    type,
                    lordUuid,
                    gameTime
            );
            if (request.status() == NpcLivelihoodRequestStatus.REQUESTED && request.requestedAtGameTime() == gameTime) {
                notifyLord(level, request);
            }
            if (request.status() == NpcLivelihoodRequestStatus.APPROVED) {
                projects.add(new PendingProject(
                        request.projectId(),
                        ProjectKind.NEW_BUILDING,
                        null,
                        request.type().prefabId(),
                        request.type().profileSeed().category(),
                        request.type().profileSeed(),
                        priorityFor(type),
                        gameTime,
                        PROJECT_TICK_COST,
                        ProjectBlocker.NONE
                ));
            }
        }
        return projects;
    }

    private static boolean shouldRequest(BannerModSettlementSnapshot snapshot, NpcLivelihoodRequestType type) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.unassignedWorkerCount() <= 0 && snapshot.missingWorkAreaAssignmentCount() <= 0) {
            return false;
        }
        return switch (type) {
            case LUMBER_CAMP, MINE -> true;
            case ANIMAL_PEN -> snapshot.residents().size() >= 4;
        };
    }

    private static boolean hasLivelihoodBuilding(BannerModSettlementSnapshot snapshot, NpcLivelihoodRequestType type) {
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
            if (matchesType(building, type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesType(@Nullable BannerModSettlementBuildingRecord building,
                                       NpcLivelihoodRequestType type) {
        if (building == null || building.buildingTypeId() == null) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(building.buildingTypeId());
        String path = id == null ? building.buildingTypeId().toLowerCase(Locale.ROOT) : id.getPath().toLowerCase(Locale.ROOT);
        return switch (type) {
            case LUMBER_CAMP -> path.contains("lumber_camp") || path.contains("lumber_area");
            case MINE -> path.equals("mine") || path.contains("validated_mine") || path.contains("mining_area");
            case ANIMAL_PEN -> path.contains("animal_pen");
        };
    }

    @Nullable
    private static UUID pickRepresentative(BannerModSettlementSnapshot snapshot, NpcLivelihoodRequestType type) {
        UUID fallback = null;
        for (BannerModSettlementResidentRecord resident : snapshot.residents()) {
            if (resident == null || resident.residentUuid() == null) {
                continue;
            }
            if (fallback == null) {
                fallback = resident.residentUuid();
            }
            if (resident.role() != BannerModSettlementResidentRole.CONTROLLED_WORKER) {
                continue;
            }
            if (resident.assignmentState() != BannerModSettlementResidentAssignmentState.UNASSIGNED
                    && resident.assignmentState() != BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING) {
                continue;
            }
            return resident.residentUuid();
        }
        return fallback;
    }

    @Nullable
    private static UUID resolveLordUuid(ServerLevel level, @Nullable UUID claimUuid) {
        if (level == null || claimUuid == null || ClaimEvents.claimManager() == null) {
            return null;
        }
        for (RecruitsClaim claim : ClaimEvents.claimManager().getAllClaims()) {
            if (claim != null && claimUuid.equals(claim.getUUID())) {
                if (claim.getOwnerPoliticalEntityId() == null) {
                    return null;
                }
                PoliticalEntityRecord owner = WarRuntimeContext.registry(level).byId(claim.getOwnerPoliticalEntityId()).orElse(null);
                return owner == null ? null : owner.leaderUuid();
            }
        }
        return null;
    }

    private static void notifyLord(ServerLevel level, NpcLivelihoodRequestRecord request) {
        if (level == null || request == null || request.lordPlayerUuid() == null) {
            return;
        }
        ServerPlayer lord = level.getServer().getPlayerList().getPlayer(request.lordPlayerUuid());
        if (lord == null) {
            return;
        }
        String claimId = request.claimUuid().toString();
        String type = request.type().name();
        lord.sendSystemMessage(Component.translatable(
                "gui.bannermod.society.livelihood_request.notice",
                Component.translatable("gui.bannermod.society.livelihood_request.type." + request.type().translationSuffix()),
                request.representativeResidentUuid().toString().substring(0, 8)
        ).append(Component.literal(" "))
                .append(actionButton(
                        "gui.bannermod.society.livelihood_request.action.approve",
                        "/bannermod society livelihood approve " + claimId + " " + type,
                        ChatFormatting.GREEN,
                        "gui.bannermod.society.livelihood_request.action.approve.tooltip"
                ))
                .append(Component.literal(" "))
                .append(actionButton(
                        "gui.bannermod.society.livelihood_request.action.deny",
                        "/bannermod society livelihood deny " + claimId + " " + type,
                        ChatFormatting.RED,
                        "gui.bannermod.society.livelihood_request.action.deny.tooltip"
                ))
                .append(Component.literal(" "))
                .append(actionButton(
                        "gui.bannermod.society.livelihood_request.action.list",
                        "/bannermod society livelihood list",
                        ChatFormatting.GOLD,
                        "gui.bannermod.society.livelihood_request.action.list.tooltip"
                )));
    }

    private static int priorityFor(NpcLivelihoodRequestType type) {
        return switch (type) {
            case LUMBER_CAMP -> LUMBER_PRIORITY;
            case MINE -> MINE_PRIORITY;
            case ANIMAL_PEN -> ANIMAL_PEN_PRIORITY;
        };
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
