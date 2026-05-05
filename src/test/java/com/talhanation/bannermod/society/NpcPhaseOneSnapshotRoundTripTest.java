package com.talhanation.bannermod.society;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcPhaseOneSnapshotRoundTripTest {

    @Test
    void roundTripsHouseholdHeadAndHousingContext() {
        NpcPhaseOneSnapshot snapshot = new NpcPhaseOneSnapshot(
                NpcLifeStage.ADULT.name(),
                NpcSex.FEMALE.name(),
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                UUID.fromString("00000000-0000-0000-0000-000000000904"),
                "culture.test",
                "faith.test",
                NpcDailyPhase.ACTIVE.name(),
                NpcIntent.GO_HOME.name(),
                NpcAnchorType.HOME.name(),
                5,
                NpcHouseholdHousingState.OVERCROWDED.name(),
                12,
                22,
                32,
                42,
                55,
                15,
                18,
                7,
                61,
                NpcHousingRequestStatus.REQUESTED.name(),
                "HIGH",
                "OVERCROWDED",
                9,
                List.of(new NpcMemorySummarySnapshot("HOUSING_PRESSURE", "HOUSEHOLD", "household:00000000", 88, true))
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        snapshot.toBytes(buf);
        NpcPhaseOneSnapshot decoded = NpcPhaseOneSnapshot.fromBytes(buf);

        assertEquals(snapshot.householdHeadResidentUuid(), decoded.householdHeadResidentUuid());
        assertEquals(snapshot.housingUrgencyTag(), decoded.housingUrgencyTag());
        assertEquals(snapshot.housingReasonTag(), decoded.housingReasonTag());
        assertEquals(snapshot.housingWaitingDays(), decoded.housingWaitingDays());
        assertEquals(snapshot.safeRecentMemories(), decoded.safeRecentMemories());
    }
}
