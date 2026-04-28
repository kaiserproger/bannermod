package com.talhanation.bannermod.compat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallShipsSeaTradeCompatTest {

    private static final ResourceLocation COG = new ResourceLocation("smallships", "cog");
    private static final ResourceLocation GALLEY = new ResourceLocation("smallships", "galley");
    private static final ResourceLocation MINECRAFT_BOAT = new ResourceLocation("minecraft", "boat");

    @Test
    void absentSmallShipsExposesNoCarrierCandidates() {
        List<SmallShipsSeaTradeCompat.CarrierCandidate> candidates = SmallShipsSeaTradeCompat.candidateCarrierTypes(
                false,
                List.of(COG, GALLEY)
        );

        assertTrue(candidates.isEmpty());
    }

    @Test
    void loadedSmallShipsExposesOnlyRegisteredSupportedCarrierCandidates() {
        List<SmallShipsSeaTradeCompat.CarrierCandidate> candidates = SmallShipsSeaTradeCompat.candidateCarrierTypes(
                true,
                List.of(COG, MINECRAFT_BOAT)
        );

        assertEquals(List.of(new SmallShipsSeaTradeCompat.CarrierCandidate(COG)), candidates);
    }

    @Test
    void loadedSmallShipsWithRegisteredSupportedTypeReportsBindableCandidate() {
        boolean bindable = SmallShipsSeaTradeCompat.hasBindableCarrierCandidate(
                true,
                List.of(COG, MINECRAFT_BOAT),
                className -> {
                    if ("com.talhanation.smallships.world.entity.ship.Ship".equals(className)) {
                        return FakeSmallShip.class;
                    }
                    throw new ClassNotFoundException(className);
                }
        );

        assertTrue(bindable);
    }

    @Test
    void bindsSupportedSmallShipsCarrierReflectively() {
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        var bound = SmallShipsSeaTradeCompat.bindCarrier(
                new FakeSmallShip(),
                COG,
                carrierId,
                ownerId,
                true,
                className -> {
                    if ("com.talhanation.smallships.world.entity.ship.Ship".equals(className)) {
                        return FakeSmallShip.class;
                    }
                    throw new ClassNotFoundException(className);
                }
        );

        assertTrue(bound.isPresent());
        assertEquals(new SmallShipsSeaTradeCompat.BoundCarrier(COG, carrierId, ownerId), bound.get());
    }

    @Test
    void rejectsUnsupportedOrAbsentCarrierBindings() {
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        assertTrue(SmallShipsSeaTradeCompat.bindCarrier(new FakeSmallShip(), COG, carrierId, ownerId, false,
                className -> FakeSmallShip.class).isEmpty());
        assertTrue(SmallShipsSeaTradeCompat.bindCarrier(new FakeSmallShip(), MINECRAFT_BOAT, carrierId, ownerId, true,
                className -> FakeSmallShip.class).isEmpty());
        assertTrue(SmallShipsSeaTradeCompat.bindCarrier(new Object(), COG, carrierId, ownerId, true,
                className -> FakeSmallShip.class).isEmpty());
    }

    private static final class FakeSmallShip {
    }
}
