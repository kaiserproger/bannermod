package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortLevelDefinitionTest {
    @Test
    void starterFortDefaultsToLevelOneAndBetaCaps() {
        FortLevelDefinition definition = FortLevelDefinition.forLevel(1);

        assertEquals(1, definition.level());
        assertEquals(4, definition.workerCap());
        assertEquals(4, definition.soldierCap());
        assertEquals(1, definition.mineCap());
        assertEquals(0, definition.outpostCap());
        assertNotNull(definition.nextLevelRequirement());
    }

    @Test
    void levelCapsMatchBetaTable() {
        assertCaps(1, 4, 4, 1, 0);
        assertCaps(2, 8, 10, 1, 1);
        assertCaps(3, 16, 25, 2, 2);
        assertCaps(4, 32, 50, 3, 4);
    }

    @Test
    void claimFortLevelSurvivesNbtRoundTrip() {
        RecruitsClaim claim = new RecruitsClaim("Fort", UUID.randomUUID());
        claim.addChunk(new ChunkPos(3, 4));
        claim.setCenter(new ChunkPos(3, 4));
        claim.setFortLevel(3);

        CompoundTag tag = claim.toNBT();
        RecruitsClaim loaded = RecruitsClaim.fromNBT(tag);

        assertEquals(3, loaded.getFortLevel());
    }

    @Test
    void summaryDebugLinesShowCapsAndNextRequirements() {
        UUID claimUuid = UUID.randomUUID();
        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                SettlementSnapshot.create(claimUuid, new ChunkPos(0, 0), null),
                List.of(),
                null,
                List.of(),
                1
        );

        List<String> lines = summary.debugLines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("Fort level 1")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("caps workers=4 soldiers=4 mines=1 outposts=0")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Next fort level requirements: food=")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("iron=") && line.contains("wood=")
                && line.contains("stone=") && line.contains("coins=")));
    }

    private static void assertCaps(int level, int workers, int soldiers, int mines, int outposts) {
        FortLevelDefinition definition = FortLevelDefinition.forLevel(level);

        assertEquals(workers, definition.workerCap());
        assertEquals(soldiers, definition.soldierCap());
        assertEquals(mines, definition.mineCap());
        assertEquals(outposts, definition.outpostCap());
    }
}
