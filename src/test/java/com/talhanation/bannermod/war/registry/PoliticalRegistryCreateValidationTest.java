package com.talhanation.bannermod.war.registry;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalRegistryCreateValidationTest {
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void validateCreateRejectsSecondEntityForSameLeader() {
        PoliticalRegistryRuntime runtime = new PoliticalRegistryRuntime();
        runtime.create("Acadia", LEADER, BlockPos.ZERO, "", "", "", "", 0L).orElseThrow();

        PoliticalRegistryValidation.Result validation = runtime.canCreate("Brittany", LEADER);

        assertFalse(validation.valid());
        assertEquals("leader_already_has_entity", validation.reason());
    }

    @Test
    void createAllowsDifferentLeaders() {
        PoliticalRegistryRuntime runtime = new PoliticalRegistryRuntime();
        runtime.create("Acadia", LEADER, BlockPos.ZERO, "", "", "", "", 0L).orElseThrow();

        assertTrue(runtime.create("Brittany", UUID.fromString("00000000-0000-0000-0000-0000000000bb"), BlockPos.ZERO,
                "", "", "", "", 0L).isPresent());
    }
}
