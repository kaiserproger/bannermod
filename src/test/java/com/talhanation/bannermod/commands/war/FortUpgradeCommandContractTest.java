package com.talhanation.bannermod.commands.war;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FortUpgradeCommandContractTest {
    private static final Path ROOT = Path.of("");
    private static final String COMMAND = "src/main/java/com/talhanation/bannermod/commands/war/EconomyStatusCommands.java";

    @Test
    void upgradeFortRejectsNonOverworldBeforeClaimOrEconomyLookup() throws IOException {
        String src = Files.readString(ROOT.resolve(COMMAND));

        int dimensionGuard = src.indexOf("level.dimension() != Level.OVERWORLD");
        int claimLookup = src.indexOf("ClaimEvents.claimManager()");
        int treasuryLookup = src.indexOf("BannerModTreasuryManager.get(level)");
        int accountingLookup = src.indexOf("StrategicResourceAccountingManager.get(level)");

        assertTrue(dimensionGuard >= 0, "upgradeFort must reject non-Overworld execution");
        assertTrue(src.contains("chat.bannermod.fort.upgrade.denied.overworld_only"),
                "upgradeFort must return a localization-ready Overworld-only denial");
        assertTrue(dimensionGuard < claimLookup, "dimension guard must run before claim lookup");
        assertTrue(dimensionGuard < treasuryLookup, "dimension guard must run before treasury lookup");
        assertTrue(dimensionGuard < accountingLookup, "dimension guard must run before resource-accounting lookup");
    }
}
