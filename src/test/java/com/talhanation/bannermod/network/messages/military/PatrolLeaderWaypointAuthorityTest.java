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

class PatrolLeaderWaypointAuthorityTest {
    private static final Path ROOT = Path.of("");
    private static final Path ADD_WAYPOINT_MESSAGE = ROOT.resolve(
            "src/main/java/com/talhanation/bannermod/network/messages/military/MessagePatrolLeaderAddWayPoint.java");
    private static final Path REMOVE_MESSAGE = ROOT.resolve(
            "src/main/java/com/talhanation/bannermod/network/messages/military/MessagePatrolLeaderRemoveWayPoint.java");
    private static final Path SET_CYCLE_MESSAGE = ROOT.resolve(
            "src/main/java/com/talhanation/bannermod/network/messages/military/MessagePatrolLeaderSetCycle.java");

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static final UUID FOREIGN_SENDER = UUID.fromString("00000000-0000-0000-0000-000000000912");

    @Test
    void forgedForeignLeaderWaypointPacketCannotReachMutation() throws IOException {
        assertEquals(CommandRole.NONE,
                CommandHierarchy.roleFor(FOREIGN_SENDER, null, false, OWNER, null, true),
                "Foreign non-op sender must not directly control another player's leader");

        String src = Files.readString(ADD_WAYPOINT_MESSAGE);
        String authorityGate = "RecruitCommandAuthority.canDirectlyControl(player, leader)";
        String handlerMutation = "this.addWayPoint(new BlockPos(x, y, z), player, leader)";

        int gateIndex = src.indexOf(authorityGate);
        int mutationIndex = src.indexOf(handlerMutation);

        assertTrue(gateIndex >= 0, "Waypoint add handler must use the canonical recruit authority gate");
        assertTrue(mutationIndex >= 0, "Waypoint add handler must still route mutations through addWayPoint");
        assertTrue(gateIndex < mutationIndex,
                "Forged packet for a foreign leader must fail authority before waypoint state can change");
        assertEquals(mutationIndex, src.lastIndexOf(handlerMutation),
                "The guarded handler path must be the only waypoint-add mutation entry point");
    }

    @Test
    void forgedForeignLeaderRemoveWaypointPacketCannotReachMutation() throws IOException {
        assertEquals(CommandRole.NONE,
                CommandHierarchy.roleFor(FOREIGN_SENDER, null, false, OWNER, null, true),
                "Foreign non-op sender must not directly control another player's leader");

        String src = Files.readString(REMOVE_MESSAGE);
        String authorityGate = "RecruitCommandAuthority.canDirectlyControl(player, leader)";
        String handlerMutation = "this.removeLastWayPoint(player, leader)";

        int gateIndex = src.indexOf(authorityGate);
        int mutationIndex = src.indexOf(handlerMutation);

        assertTrue(gateIndex >= 0, "Waypoint remove handler must use the canonical recruit authority gate");
        assertTrue(mutationIndex >= 0, "Waypoint remove handler must still route mutations through removeLastWayPoint");
        assertTrue(gateIndex < mutationIndex,
                "Forged packet for a foreign leader must fail authority before waypoint state can change");
        assertEquals(mutationIndex, src.lastIndexOf(handlerMutation),
                "The guarded handler path must be the only waypoint-remove mutation entry point");
    }

    @Test
    void forgedForeignLeaderCyclePacketCannotReachMutation() throws IOException {
        assertEquals(CommandRole.NONE,
                CommandHierarchy.roleFor(FOREIGN_SENDER, null, false, OWNER, null, true),
                "Foreign non-op sender must not directly control another player's leader");

        String src = Files.readString(SET_CYCLE_MESSAGE);
        String authorityGate = "RecruitCommandAuthority.canDirectlyControl(player, leader)";
        String handlerMutation = "leader.setCycle(this.cycle)";

        int gateIndex = src.indexOf(authorityGate);
        int mutationIndex = src.indexOf(handlerMutation);

        assertTrue(gateIndex >= 0, "Cycle handler must use the canonical recruit authority gate");
        assertTrue(mutationIndex >= 0, "Cycle handler must still mutate cycle for authorized leaders");
        assertTrue(gateIndex < mutationIndex,
                "Forged packet for a foreign leader must fail authority before cycle state can change");
        assertEquals(mutationIndex, src.lastIndexOf(handlerMutation),
                "The guarded handler path must be the only cycle mutation entry point");
    }
}
