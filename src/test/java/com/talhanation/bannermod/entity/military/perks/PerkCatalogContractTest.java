package com.talhanation.bannermod.entity.military.perks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkCatalogContractTest {

    @Test
    void defaultCatalogCoversRecruitArchetypesAndUniversalStats() {
        Set<PerkStat> universalStats = EnumSet.noneOf(PerkStat.class);
        for (PerkNode node : PerkRegistry.byArchetype(PerkArchetype.UNIVERSAL)) {
            for (PerkBonus bonus : node.bonuses()) {
                universalStats.add(bonus.stat());
            }
        }

        assertTrue(universalStats.containsAll(EnumSet.allOf(PerkStat.class)),
                "Expected universal recruit catalog to expose every general stat");
        for (PerkArchetype archetype : PerkArchetype.values()) {
            if (archetype != PerkArchetype.UNIVERSAL) {
                assertFalse(PerkRegistry.byArchetype(archetype).isEmpty(),
                        "Expected at least one tracked perk for " + archetype);
            }
        }
    }

    @Test
    void defaultCatalogHasEnglishAndRussianLocalizationKeys() throws IOException {
        String enUs = Files.readString(Path.of("src/main/resources/assets/bannermod/lang/en_us.json"));
        String ruRu = Files.readString(Path.of("src/main/resources/assets/bannermod/lang/ru_ru.json"));

        for (PerkNode node : allDefaultNodes()) {
            assertContains(enUs, node.localizationKey());
            assertContains(enUs, node.localizationKey() + ".desc");
            assertContains(ruRu, node.localizationKey());
            assertContains(ruRu, node.localizationKey() + ".desc");
        }
    }

    private static List<PerkNode> allDefaultNodes() {
        List<PerkNode> nodes = new ArrayList<>();
        for (PerkArchetype archetype : PerkArchetype.values()) {
            nodes.addAll(PerkRegistry.byArchetype(archetype));
        }
        return nodes;
    }

    private static void assertContains(String content, String key) {
        assertTrue(content.contains("\"" + key + "\""), "Missing localization key: " + key);
    }
}
