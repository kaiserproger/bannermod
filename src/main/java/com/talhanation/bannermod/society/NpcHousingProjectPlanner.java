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
import net.minecraft.network.chat.Component;
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
        for (BannerModSettlementResidentRecord resident : snapshot.residents()) {
            if (resident == null || resident.residentUuid() == null) {
                continue;
            }
            UUID residentUuid = resident.residentUuid();
            if (homeRuntime.homeFor(residentUuid).isPresent()) {
                NpcHousingRequestAccess.markFulfilled(level, residentUuid, gameTime);
                continue;
            }
            NpcSocietyProfile profile = NpcSocietyAccess.ensureResident(level, residentUuid, gameTime);
            if (profile.lifeStage() != NpcLifeStage.ADULT && profile.lifeStage() != NpcLifeStage.ELDER) {
                continue;
            }
            NpcHousingRequestRecord request = NpcHousingRequestAccess.requestHouse(level, residentUuid, snapshot.claimUuid(), lordUuid, gameTime);
            if (request.status() == NpcHousingRequestStatus.REQUESTED) {
                notifyLord(level, lordUuid, residentUuid);
                request = NpcHousingRequestAccess.approve(level, residentUuid, gameTime);
            }
            if (request.status() == NpcHousingRequestStatus.APPROVED) {
                projects.add(new PendingProject(
                        request.projectId(),
                        ProjectKind.NEW_BUILDING,
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

    private static void notifyLord(ServerLevel level, @Nullable UUID lordUuid, UUID residentUuid) {
        if (level == null || lordUuid == null) {
            return;
        }
        ServerPlayer lord = level.getServer().getPlayerList().getPlayer(lordUuid);
        if (lord == null) {
            return;
        }
        lord.sendSystemMessage(Component.translatable(
                "gui.bannermod.society.housing_request.notice",
                residentUuid.toString().substring(0, 8)
        ));
    }
}
