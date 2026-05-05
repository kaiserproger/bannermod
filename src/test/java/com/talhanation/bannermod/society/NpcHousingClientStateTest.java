package com.talhanation.bannermod.society;

import com.talhanation.bannermod.society.client.NpcHousingClientState;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcHousingClientStateTest {

    @Test
    void appliesSnapshotContractIntoClientMirror() {
        UUID claimId = UUID.fromString("00000000-0000-0000-0000-000000000801");
        UUID householdId = UUID.fromString("00000000-0000-0000-0000-000000000802");
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000000803");

        NpcHousingLedgerEntry entry = new NpcHousingLedgerEntry(
                householdId,
                residentId,
                claimId,
                UUID.fromString("00000000-0000-0000-0000-000000000804"),
                UUID.fromString("00000000-0000-0000-0000-000000000805"),
                null,
                null,
                NpcHousingRequestStatus.REQUESTED.name(),
                NpcHouseholdHousingState.OVERCROWDED.name(),
                "HIGH",
                "OVERCROWDED",
                4,
                9,
                268,
                1,
                100L,
                120L
        );

        CompoundTag snapshot = NpcHousingSnapshotContract.encode(
                claimId,
                true,
                "",
                List.of(entry)
        );

        NpcHousingClientState.clear();
        NpcHousingClientState.beginSync();
        NpcHousingClientState.applyFromNbt(snapshot);

        assertTrue(NpcHousingClientState.hasSnapshot());
        assertTrue(NpcHousingClientState.hasClaim());
        assertTrue(NpcHousingClientState.canManage());
        assertFalse(NpcHousingClientState.syncPending());
        assertEquals(claimId, NpcHousingClientState.claimUuid());
        assertEquals(1, NpcHousingClientState.requests().size());
        NpcHousingLedgerEntry mirrored = NpcHousingClientState.requestByHousehold(householdId);
        assertNotNull(mirrored);
        assertEquals(1, mirrored.queueRank());
        assertEquals("OVERCROWDED", mirrored.reasonTag());
        assertEquals(4, mirrored.householdSize());
    }
}
