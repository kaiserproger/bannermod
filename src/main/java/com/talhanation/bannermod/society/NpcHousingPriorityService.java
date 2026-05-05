package com.talhanation.bannermod.society;

import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NpcHousingPriorityService {
    private static final long TICKS_PER_DAY = 24000L;
    private static final Comparator<NpcHousingLedgerEntry> ENTRY_ORDER =
            Comparator.comparingInt((NpcHousingLedgerEntry entry) -> statusOrder(entry.statusTag()))
                    .thenComparing(Comparator.comparingInt(NpcHousingLedgerEntry::priorityScore).reversed())
                    .thenComparingLong(NpcHousingLedgerEntry::requestedAtGameTime)
                    .thenComparing(entry -> entry.householdId().toString());

    private NpcHousingPriorityService() {
    }

    public static List<NpcHousingLedgerEntry> activeEntriesForClaim(ServerLevel level, @Nullable UUID claimUuid, long currentGameTime) {
        if (level == null || claimUuid == null) {
            return List.of();
        }
        List<NpcHousingLedgerEntry> entries = new ArrayList<>();
        for (NpcHousingRequestRecord request : NpcHousingRequestSavedData.get(level).runtime().requestsForClaim(claimUuid)) {
            if (!isVisibleStatus(request.status())) {
                continue;
            }
            NpcHouseholdRecord household = NpcHouseholdAccess.householdFor(level, request.householdId()).orElse(null);
            entries.add(describe(request, household, currentGameTime));
        }
        return rankEntries(entries);
    }

    static List<NpcHousingLedgerEntry> rankEntries(Iterable<NpcHousingLedgerEntry> entries) {
        List<NpcHousingLedgerEntry> rankedEntries = new ArrayList<>();
        if (entries != null) {
            for (NpcHousingLedgerEntry entry : entries) {
                if (entry != null) {
                    rankedEntries.add(entry);
                }
            }
        }
        rankedEntries.sort(ENTRY_ORDER);
        List<NpcHousingLedgerEntry> ranked = new ArrayList<>(rankedEntries.size());
        for (int i = 0; i < rankedEntries.size(); i++) {
            ranked.add(rankedEntries.get(i).withQueueRank(i + 1));
        }
        return List.copyOf(ranked);
    }

    public static NpcHousingLedgerEntry describe(NpcHousingRequestRecord request,
                                                 @Nullable NpcHouseholdRecord household,
                                                 long currentGameTime) {
        NpcHousingRequestStatus status = request == null ? NpcHousingRequestStatus.NONE : request.status();
        NpcHouseholdHousingState housingState = household == null ? NpcHouseholdHousingState.NORMAL : household.housingState();
        int householdSize = household == null ? 0 : household.memberResidentUuids().size();
        int waitingDays = waitingDays(request == null ? 0L : request.requestedAtGameTime(), currentGameTime);
        int priorityScore = score(status, housingState, householdSize, waitingDays);
        return new NpcHousingLedgerEntry(
                request.householdId(),
                request.residentUuid(),
                request.claimUuid(),
                household == null ? null : household.headResidentUuid(),
                household == null ? null : household.homeBuildingUuid(),
                request.buildAreaUuid(),
                request.reservedPlotPos(),
                status.name(),
                housingState.name(),
                urgencyTag(priorityScore, housingState),
                reasonTag(status, housingState, householdSize, waitingDays),
                householdSize,
                waitingDays,
                priorityScore,
                0,
                request.requestedAtGameTime(),
                request.updatedAtGameTime()
        );
    }

    public static boolean canApprove(@Nullable NpcHousingLedgerEntry entry) {
        NpcHousingRequestStatus status = entry == null ? NpcHousingRequestStatus.NONE : NpcHousingRequestStatus.fromName(entry.statusTag());
        return status == NpcHousingRequestStatus.REQUESTED || status == NpcHousingRequestStatus.DENIED;
    }

    public static boolean canDeny(@Nullable NpcHousingLedgerEntry entry) {
        NpcHousingRequestStatus status = entry == null ? NpcHousingRequestStatus.NONE : NpcHousingRequestStatus.fromName(entry.statusTag());
        return status == NpcHousingRequestStatus.REQUESTED;
    }

    private static boolean isVisibleStatus(NpcHousingRequestStatus status) {
        return status != null && status != NpcHousingRequestStatus.NONE && status != NpcHousingRequestStatus.FULFILLED;
    }

    private static int waitingDays(long requestedAtGameTime, long currentGameTime) {
        if (requestedAtGameTime <= 0L || currentGameTime <= requestedAtGameTime) {
            return 0;
        }
        return (int) Math.max(0L, (currentGameTime - requestedAtGameTime) / TICKS_PER_DAY);
    }

    private static int score(NpcHousingRequestStatus status,
                             NpcHouseholdHousingState housingState,
                             int householdSize,
                             int waitingDays) {
        int score = switch (status == null ? NpcHousingRequestStatus.NONE : status) {
            case REQUESTED -> 40;
            case DENIED -> 24;
            case APPROVED -> 8;
            case NONE, FULFILLED -> 0;
        };
        score += switch (housingState == null ? NpcHouseholdHousingState.NORMAL : housingState) {
            case HOMELESS -> 300;
            case OVERCROWDED -> 180;
            case NORMAL -> 60;
        };
        score += Math.min(6, Math.max(0, householdSize)) * 12;
        score += Math.min(30, Math.max(0, waitingDays)) * 4;
        return score;
    }

    private static String urgencyTag(int priorityScore, NpcHouseholdHousingState housingState) {
        if (housingState == NpcHouseholdHousingState.HOMELESS || priorityScore >= 300) {
            return "CRITICAL";
        }
        if (housingState == NpcHouseholdHousingState.OVERCROWDED || priorityScore >= 180) {
            return "HIGH";
        }
        if (priorityScore >= 110) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static String reasonTag(NpcHousingRequestStatus status,
                                    NpcHouseholdHousingState housingState,
                                    int householdSize,
                                    int waitingDays) {
        if (housingState == NpcHouseholdHousingState.HOMELESS) {
            return "HOMELESS";
        }
        if (housingState == NpcHouseholdHousingState.OVERCROWDED) {
            return "OVERCROWDED";
        }
        if (waitingDays >= 7) {
            return "LONG_WAIT";
        }
        if (householdSize >= 4) {
            return "LARGE_HOUSEHOLD";
        }
        if (status == NpcHousingRequestStatus.DENIED) {
            return "DENIED_REVIEW";
        }
        if (status == NpcHousingRequestStatus.APPROVED) {
            return "APPROVED_PIPELINE";
        }
        return "STANDARD";
    }

    private static int statusOrder(@Nullable String statusTag) {
        return switch (NpcHousingRequestStatus.fromName(statusTag == null ? "" : statusTag.toUpperCase(Locale.ROOT))) {
            case REQUESTED -> 0;
            case DENIED -> 1;
            case APPROVED -> 2;
            case NONE, FULFILLED -> 99;
        };
    }
}
