package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.army.command.CommandRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRemoveAssignedGroupFromCompanionAuthorityTest {
    private static final Path HANDLER = Path.of(
            "src/main/java/com/talhanation/bannermod/network/messages/military/MessageRemoveAssignedGroupFromCompanion.java");

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000916");
    private static final UUID FOREIGN_SENDER = UUID.fromString("00000000-0000-0000-0000-000000000917");

    @Test
    void forgedForeignCompanionGroupRemovalCannotReachMutations() throws IOException {
        assertEquals(CommandRole.NONE,
                CommandHierarchy.roleFor(FOREIGN_SENDER, null, false, OWNER, null, true),
                "Foreign non-op sender must not directly control another player's companion leader");

        String src = Files.readString(HANDLER);
        String authorityGate = "RecruitCommandAuthority.canDirectlyControl(serverPlayer, companionEntity)";
        String leaderBindingMutation = "group.leaderUUID = null";
        String armyListenMutation = "RecruitCommanderUtil.setRecruitsListen";
        String armyResetMutation = "companionEntity.army = null";
        String screenBroadcast = "new MessageToClientUpdateLeaderScreen";

        int gateIndex = src.indexOf(authorityGate);
        int leaderBindingIndex = src.indexOf(leaderBindingMutation);
        int armyListenIndex = src.indexOf(armyListenMutation);
        int armyResetIndex = src.indexOf(armyResetMutation);
        int screenBroadcastIndex = src.indexOf(screenBroadcast);

        assertTrue(gateIndex >= 0, "Group-removal handler must use the canonical recruit authority gate");
        assertTrue(leaderBindingIndex >= 0, "Authorized removal must still clear the group leader binding");
        assertTrue(armyListenIndex >= 0, "Authorized removal must still reset army listen state");
        assertTrue(armyResetIndex >= 0, "Authorized removal must still detach the companion army");
        assertTrue(screenBroadcastIndex >= 0, "Authorized removal must still broadcast the leader screen update");
        assertTrue(gateIndex < leaderBindingIndex,
                "Forged packet for a foreign nearby companion must fail authority before group binding changes");
        assertTrue(gateIndex < armyListenIndex,
                "Forged packet for a foreign nearby companion must fail authority before army state changes");
        assertTrue(gateIndex < armyResetIndex,
                "Forged packet for a foreign nearby companion must fail authority before army detaches");
        assertTrue(gateIndex < screenBroadcastIndex,
                "Forged packet for a foreign nearby companion must fail authority before screen updates broadcast");
        assertEquals(leaderBindingIndex, src.lastIndexOf(leaderBindingMutation),
                "The guarded handler path must be the only leader-binding mutation entry point");
        assertEquals(armyResetIndex, src.lastIndexOf(armyResetMutation),
                "The guarded handler path must be the only army-detach mutation entry point");
    }
}
