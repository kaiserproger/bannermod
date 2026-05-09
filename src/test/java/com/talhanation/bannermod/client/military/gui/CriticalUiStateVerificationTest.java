package com.talhanation.bannermod.client.military.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalUiStateVerificationTest {
    private static final Path ROOT = Path.of("");

    @Test
    void keybindingsKeepCurrentWorkerMapAndWarRoomGating() throws IOException {
        String keyEvents = read("src/main/java/com/talhanation/bannermod/client/military/events/KeyEvents.java");

        assertTrue(keyEvents.contains("com.talhanation.bannermod.registry.civilian.ModShortcuts.COMMAND_SCREEN_KEY.consumeClick()"));
        assertTrue(keyEvents.contains("selectWorkerCommandCategory(clientPlayerEntity);"));
        assertTrue(keyEvents.contains("CommandEvents.openCommandScreen(clientPlayerEntity);"));
        assertTrue(keyEvents.contains("nbt.putInt(\"RecruitsCategory\", workerCategory);"));

        assertTrue(keyEvents.contains("minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD"));
        assertTrue(keyEvents.contains("new MessageRequestFormationMapSnapshot()"));
        assertTrue(keyEvents.contains("minecraft.setScreen(new WorldMapScreen());"));

        assertTrue(keyEvents.contains("ModShortcuts.WAR_ROOM_KEY.consumeClick()"));
        assertTrue(keyEvents.contains("minecraft.setScreen(new WarListScreen(null));"));
    }

    @Test
    void worldMapActionsKeepVisibleGatingAndSyncStateReasons() throws IOException {
        String generalActions = read("src/main/java/com/talhanation/bannermod/client/military/gui/worldmap/WorldMapGeneralMenuActions.java");
        String claimActions = read("src/main/java/com/talhanation/bannermod/client/military/gui/worldmap/WorldMapClaimMenuActions.java");
        String clientPlayerEvents = read("src/main/java/com/talhanation/bannermod/client/military/events/ClientPlayerEvents.java");
        String worldMapScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/worldmap/WorldMapScreen.java");

        assertTrue(generalActions.contains("if (screen.getSelectedRoute() == null)"));
        assertTrue(generalActions.contains("menu.addDisabledEntry(TEXT_ADD_WAYPOINT, TEXT_WAYPOINT_NEEDS_ROUTE, \"route_waypoint_disabled\")"));
        assertTrue(generalActions.contains("!screen.canPlaceWaypointAt(screen.snapshotWorldX, screen.snapshotWorldZ)"));
        assertTrue(generalActions.contains("menu.addDisabledEntry(TEXT_ADD_WAYPOINT, TEXT_WAYPOINT_NEEDS_EXPLORED, \"route_waypoint_disabled\")"));
        assertTrue(generalActions.contains("menu.addEntry(TEXT_ADD_WAYPOINT, () -> true, WorldMapScreen::addWaypointAtClicked, \"route_waypoint_add\")"));

        assertTrue(claimActions.contains("boolean claimsReady = ClientManager.hasClaimsSnapshot && !ClientManager.claimsSnapshotStale;"));
        assertTrue(claimActions.contains("Component claimChunkDisabledReason = !ClientManager.hasClaimsSnapshot ? TEXT_DISABLED_SYNC"));
        assertTrue(claimActions.contains(": ClientManager.claimsSnapshotStale ? TEXT_DISABLED_STALE"));
        assertTrue(claimActions.contains(": !canClaimChunk ? TEXT_DISABLED_UNCLAIMABLE"));
        assertTrue(claimActions.contains(": !isNeighborLeader ? TEXT_DISABLED_NOT_LEADER"));
        assertTrue(claimActions.contains(": TEXT_DISABLED_NO_CURRENCY;"));

        assertTrue(clientPlayerEvents.contains("updateMapTiles(!screen.isNavigatingMap());"));

        assertTrue(worldMapScreen.contains("if (!ClientManager.hasClaimsSnapshot) {"));
        assertTrue(worldMapScreen.contains("\"gui.bannermod.map.claim_state.waiting_sync\""));
        assertTrue(worldMapScreen.contains("} else if (ClientManager.claimsSnapshotStale) {"));
        assertTrue(worldMapScreen.contains("\"gui.bannermod.map.claim_state.stale\""));
        assertTrue(worldMapScreen.contains("} else if (ClientManager.recruitsClaims.isEmpty()) {"));
        assertTrue(worldMapScreen.contains("\"gui.bannermod.map.claim_state.empty\""));
    }

    @Test
    void warScreensKeepWaitingForSyncStateSelection() throws IOException {
        String warListScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/war/WarListScreen.java");
        String politicalEntityListScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/war/PoliticalEntityListScreen.java");

        assertTrue(warListScreen.contains("boolean hasSnapshot = WarClientState.hasSnapshot();"));
        assertTrue(warListScreen.contains("? \"gui.bannermod.war_list.empty\""));
        assertTrue(warListScreen.contains(": \"gui.bannermod.war_list.waiting_sync\"") );

        assertTrue(politicalEntityListScreen.contains("String empty = text(WarClientState.hasSnapshot()"));
        assertTrue(politicalEntityListScreen.contains("? \"gui.bannermod.states.empty\""));
        assertTrue(politicalEntityListScreen.contains(": \"gui.bannermod.states.waiting_sync\"") );
    }

    @Test
    void perkTreeKeepsServerAuthoritativeUiAndLocalizedWiring() throws IOException {
        String recruitInventoryScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/RecruitInventoryScreen.java");
        String perkTreeScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/PerkTreeScreen.java");
        String keyEvents = read("src/main/java/com/talhanation/bannermod/client/military/events/KeyEvents.java");
        String modShortcuts = read("src/main/java/com/talhanation/bannermod/registry/military/ModShortcuts.java");
        String packetCatalog = read("src/main/java/com/talhanation/bannermod/network/catalog/MilitaryPacketCatalog.java");
        String updatePacket = read("src/main/java/com/talhanation/bannermod/network/messages/military/MessageUpdatePerkTree.java");
        String enLang = read("src/main/resources/assets/bannermod/lang/en_us.json");
        String ruLang = read("src/main/resources/assets/bannermod/lang/ru_ru.json");

        assertTrue(recruitInventoryScreen.contains("PerkTreeScreen.recruitTree(this.recruit)"));
        assertTrue(modShortcuts.contains("PLAYER_SKILL_TREE_KEY"));
        assertTrue(keyEvents.contains("PerkTreeScreen.playerTree()"));

        assertTrue(perkTreeScreen.contains("private boolean snapshotReady"));
        assertTrue(perkTreeScreen.contains("if (!snapshotReady) return null"));
        assertTrue(perkTreeScreen.contains("respecButton.active = progress != null"));
        assertTrue(perkTreeScreen.contains("setScreen(null)"));
        assertTrue(perkTreeScreen.contains("PerkState.LOCKED"));
        assertTrue(perkTreeScreen.contains("PerkState.AVAILABLE"));
        assertTrue(perkTreeScreen.contains("PerkState.OWNED"));
        assertTrue(perkTreeScreen.contains("new MessageUpdatePerkTree(playerTree, recruitUuid(), action, perkId)"));
        assertTrue(perkTreeScreen.contains("gui.bannermod.perk_tree.respec.confirm"));

        assertTrue(updatePacket.contains("PlayerPerkProgressService.unlock(sender, perkId)"));
        assertTrue(updatePacket.contains("recruit.getPerkProgress().unlock(node)"));
        assertTrue(updatePacket.contains("MessageRequestPerkTreeSnapshot.isAuthorized(sender, recruit)"));
        assertTrue(updatePacket.contains("PerkEffectService.applyRecruitAttributeBonuses(recruit)"));
        assertTrue(packetCatalog.contains("MessageRequestPerkTreeSnapshot.class"));
        assertTrue(packetCatalog.contains("MessageUpdatePerkTree.class"));
        assertTrue(packetCatalog.contains("MessageToClientUpdatePerkTreeSnapshot.class"));

        assertTrue(enLang.contains("gui.bannermod.perk_tree.state.available"));
        assertTrue(enLang.contains("key.bannermod.player_skill_tree_key"));
        assertTrue(ruLang.contains("gui.bannermod.perk_tree.state.available"));
        assertTrue(ruLang.contains("key.bannermod.player_skill_tree_key"));
    }

    @Test
    void recruitAndAssassinScreensKeepVisibleStatusReasons() throws IOException {
        String recruitInventoryScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/RecruitInventoryScreen.java");
        String assassinLeaderScreen = read("src/main/java/com/talhanation/bannermod/client/military/gui/AssassinLeaderScreen.java");
        String claimAuthorityStatusTest = read("src/test/java/com/talhanation/bannermod/client/military/gui/overlay/ClaimAuthorityStatusTest.java");
        String enLang = read("src/main/resources/assets/bannermod/lang/en_us.json");
        String ruLang = read("src/main/resources/assets/bannermod/lang/ru_ru.json");

        assertTrue(recruitInventoryScreen.contains("MilitaryGuiStyle.drawBadge(guiGraphics, font, orderStatusLine()"));
        assertTrue(recruitInventoryScreen.contains("gui.recruits.inv.status.group_unset"));
        assertTrue(recruitInventoryScreen.contains("gui.recruits.inv.status.ready"));
        assertTrue(recruitInventoryScreen.contains("TEXT_EXP"));
        assertTrue(recruitInventoryScreen.contains("TEXT_MORALE"));
        assertTrue(recruitInventoryScreen.contains("TEXT_HUNGER"));

        assertTrue(assassinLeaderScreen.contains("gui.recruits.assassin.action.dispatch.disabled"));
        assertTrue(assassinLeaderScreen.contains("gui.recruits.assassin.status.count_updated"));
        assertTrue(assassinLeaderScreen.contains("MilitaryGuiStyle.parchmentPanel"));

        assertTrue(claimAuthorityStatusTest.contains("void leaderOfOwningPoliticalEntityIsFriendly()"));

        assertTrue(enLang.contains("gui.recruits.inv.status.group_unset"));
        assertTrue(enLang.contains("gui.recruits.assassin.action.dispatch.disabled"));
        assertTrue(ruLang.contains("gui.recruits.inv.status.group_unset"));
        assertTrue(ruLang.contains("gui.recruits.assassin.action.dispatch.disabled"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
