package com.talhanation.bannermod.client.military.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalEntityColorPaletteVerificationTest {
    private static final Path ROOT = Path.of("");

    @Test
    void politicalEntityColorUsesPaletteScreenAndLocalizedKeys() throws IOException {
        String politicalEntityListScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/war/PoliticalEntityListScreen.java");
        String politicalEntityColorPaletteScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/war/PoliticalEntityColorPaletteScreen.java");
        String enLang = read("src/main/resources/assets/bannermod/lang/en_us.json");
        String ruLang = read("src/main/resources/assets/bannermod/lang/ru_ru.json");

        assertTrue(politicalEntityListScreen.contains("new PoliticalEntityColorPaletteScreen("));
        assertTrue(politicalEntityColorPaletteScreen.contains("PoliticalColorParser.parseArgb"));
        assertTrue(politicalEntityColorPaletteScreen.contains("gui.bannermod.states.palette.current"));
        assertTrue(politicalEntityColorPaletteScreen.contains("this::applyCustomColor"));
        assertTrue(enLang.contains("gui.bannermod.states.palette.current"));
        assertTrue(ruLang.contains("gui.bannermod.states.palette.current"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
