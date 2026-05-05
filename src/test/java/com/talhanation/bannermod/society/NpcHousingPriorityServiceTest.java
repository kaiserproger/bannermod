package com.talhanation.bannermod.society;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcHousingPriorityServiceTest {

    @Test
    void ranksHomelessAndOlderRequestsAheadOfWeakerEntries() {
        UUID claimId = UUID.fromString("00000000-0000-0000-0000-000000000701");

        NpcHousingLedgerEntry homeless = NpcHousingPriorityService.describe(
                request("00000000-0000-0000-0000-000000000711", "00000000-0000-0000-0000-000000000721", claimId, NpcHousingRequestStatus.REQUESTED, 0L),
                household("00000000-0000-0000-0000-000000000711", NpcHouseholdHousingState.HOMELESS, 2),
                20L * 24000L
        );
        NpcHousingLedgerEntry oldDenied = NpcHousingPriorityService.describe(
                request("00000000-0000-0000-0000-000000000712", "00000000-0000-0000-0000-000000000722", claimId, NpcHousingRequestStatus.DENIED, 2L * 24000L),
                household("00000000-0000-0000-0000-000000000712", NpcHouseholdHousingState.OVERCROWDED, 5),
                20L * 24000L
        );
        NpcHousingLedgerEntry approved = NpcHousingPriorityService.describe(
                request("00000000-0000-0000-0000-000000000713", "00000000-0000-0000-0000-000000000723", claimId, NpcHousingRequestStatus.APPROVED, 19L * 24000L),
                household("00000000-0000-0000-0000-000000000713", NpcHouseholdHousingState.NORMAL, 3),
                20L * 24000L
        );

        List<NpcHousingLedgerEntry> ranked = NpcHousingPriorityService.rankEntries(List.of(approved, oldDenied, homeless));

        assertEquals(homeless.householdId(), ranked.get(0).householdId());
        assertEquals(1, ranked.get(0).queueRank());
        assertEquals("CRITICAL", ranked.get(0).urgencyTag());
        assertEquals(oldDenied.householdId(), ranked.get(1).householdId());
        assertEquals("OVERCROWDED", ranked.get(1).reasonTag());
        assertEquals(approved.householdId(), ranked.get(2).householdId());
        assertEquals("APPROVED_PIPELINE", ranked.get(2).reasonTag());
    }

    @Test
    void olderRequestWinsWhenSeverityMatches() {
        UUID claimId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        NpcHousingLedgerEntry older = NpcHousingPriorityService.describe(
                request("00000000-0000-0000-0000-000000000731", "00000000-0000-0000-0000-000000000741", claimId, NpcHousingRequestStatus.REQUESTED, 1L * 24000L),
                household("00000000-0000-0000-0000-000000000731", NpcHouseholdHousingState.OVERCROWDED, 3),
                12L * 24000L
        );
        NpcHousingLedgerEntry newer = NpcHousingPriorityService.describe(
                request("00000000-0000-0000-0000-000000000732", "00000000-0000-0000-0000-000000000742", claimId, NpcHousingRequestStatus.REQUESTED, 10L * 24000L),
                household("00000000-0000-0000-0000-000000000732", NpcHouseholdHousingState.OVERCROWDED, 3),
                12L * 24000L
        );

        List<NpcHousingLedgerEntry> ranked = NpcHousingPriorityService.rankEntries(List.of(newer, older));

        assertEquals(older.householdId(), ranked.get(0).householdId());
        assertEquals(newer.householdId(), ranked.get(1).householdId());
        assertEquals(11, ranked.get(0).waitingDays());
        assertEquals(2, ranked.get(1).waitingDays());
    }

    private static NpcHousingRequestRecord request(String householdId,
                                                   String residentId,
                                                   UUID claimId,
                                                   NpcHousingRequestStatus status,
                                                   long requestedAtGameTime) {
        return new NpcHousingRequestRecord(
                UUID.fromString(householdId),
                UUID.fromString(residentId),
                claimId,
                UUID.randomUUID(),
                null,
                null,
                null,
                status,
                requestedAtGameTime,
                requestedAtGameTime
        );
    }

    private static NpcHouseholdRecord household(String householdId,
                                                NpcHouseholdHousingState state,
                                                int members) {
        java.util.List<UUID> residentIds = new java.util.ArrayList<>();
        for (int i = 0; i < members; i++) {
            residentIds.add(new UUID(0L, 800L + i));
        }
        return NpcHouseholdRecord.create(
                UUID.fromString(householdId),
                null,
                residentIds.isEmpty() ? null : residentIds.getFirst(),
                residentIds,
                2,
                state,
                0L
        );
    }
}
