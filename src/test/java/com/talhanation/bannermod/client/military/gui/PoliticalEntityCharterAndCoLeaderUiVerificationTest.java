package com.talhanation.bannermod.client.military.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalEntityCharterAndCoLeaderUiVerificationTest {
    private static final Path ROOT = Path.of("");

    @Test
    void stateScreenUsesDedicatedCharterEditorAndPlayerPicker() throws IOException {
        String listScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/war/PoliticalEntityListScreen.java");
        String charterScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/war/PoliticalEntityCharterScreen.java");
        String coLeaderPickerScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/war/PoliticalEntityCoLeaderPickerScreen.java");
        String enLang = read("src/main/resources/assets/bannermod/lang/en_us.json");
        String ruLang = read("src/main/resources/assets/bannermod/lang/ru_ru.json");

        assertTrue(listScreen.contains("new PoliticalEntityCharterScreen("));
        assertTrue(listScreen.contains("new PoliticalEntityCoLeaderPickerScreen("));
        assertTrue(listScreen.contains("gui.bannermod.states.detail.charter"));
        assertTrue(charterScreen.contains("RecruitsMultiLineEditBox"));
        assertTrue(charterScreen.contains("gui.bannermod.states.dialog.charter.subtitle"));
        assertTrue(coLeaderPickerScreen.contains("MessageUpdateCoLeader"));
        assertTrue(coLeaderPickerScreen.contains("ClientManager.onlinePlayersVersion"));
        assertTrue(coLeaderPickerScreen.contains("this.init();"));
        assertTrue(coLeaderPickerScreen.contains("drawWrapped(graphics, Component.translatable(\"gui.bannermod.states.dialog.co_leader.subtitle\")"));
        assertTrue(coLeaderPickerScreen.contains("gui.bannermod.states.dialog.co_leader.manual"));
        assertTrue(enLang.contains("gui.bannermod.states.dialog.co_leader.select.tooltip"));
        assertTrue(enLang.contains("gui.bannermod.states.dialog.co_leader.manual"));
        assertTrue(ruLang.contains("gui.bannermod.states.dialog.co_leader.select.tooltip"));
        assertTrue(ruLang.contains("gui.bannermod.states.dialog.co_leader.manual"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
