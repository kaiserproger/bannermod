package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.AssassinLeaderCountAuthority;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssassinLeaderControlAuthorityTest {
    @Test
    void serverGuiOpenAssignsAndPersistsControlOwner() throws IOException {
        Path entityPath = Paths.get("src/main/java/com/talhanation/bannermod/entity/military/AssassinLeaderEntity.java");
        String source = Files.readString(entityPath);

        int serverGuiBranch = source.indexOf("if (player instanceof ServerPlayer)");
        int assignment = source.indexOf("assignControlOwnerIfAbsent(player)");
        int openScreen = source.indexOf("BannerModNetworkHooks.openScreen");
        int saveOwner = source.indexOf("nbt.putUUID(CONTROL_OWNER_TAG, this.controlOwnerUUID)");
        int loadOwner = source.indexOf("nbt.hasUUID(CONTROL_OWNER_TAG) ? nbt.getUUID(CONTROL_OWNER_TAG) : null");

        assertTrue(serverGuiBranch >= 0, "assassin leader GUI must still have a server-side open path");
        assertTrue(assignment > serverGuiBranch && assignment < openScreen,
                "server-side GUI open must assign the control owner before the menu can send count changes");
        assertTrue(saveOwner >= 0, "control owner must be persisted with the entity");
        assertTrue(loadOwner >= 0, "control owner must be restored when the entity loads");
    }

    @Test
    void countPacketChecksControlOwnerWithoutAssigningIt() throws IOException {
        Path handlerPath = Paths.get("src/main/java/com/talhanation/bannermod/network/messages/military/MessageAssassinCount.java");
        String source = Files.readString(handlerPath);

        int controlCheck = source.indexOf("leader.trySetCountFrom(player.getUUID(), player.hasPermissions(2), this.count)");
        int ownerAssignment = source.indexOf("assignControlOwnerIfAbsent");

        assertTrue(controlCheck >= 0, "count updates must require the server-side control owner or op permission");
        assertTrue(ownerAssignment < 0, "count packets must not assign or claim control ownership");
    }

    @Test
    void countPacketRejectsForeignNonOpAndKeepsRangeGate() throws IOException {
        Path handlerPath = Paths.get("src/main/java/com/talhanation/bannermod/network/messages/military/MessageAssassinCount.java");
        String source = Files.readString(handlerPath);

        int authorityCheck = source.indexOf("leader.trySetCountFrom(player.getUUID(), player.hasPermissions(2), this.count)");
        int rangeCheck = source.indexOf("player.getBoundingBox().inflate(16.0D).intersects(leader.getBoundingBox())");
        int entityMutation = source.indexOf("leader.trySetCountFrom(player.getUUID(), player.hasPermissions(2), this.count)");

        assertTrue(authorityCheck >= 0, "foreign non-op senders must not satisfy the owner-or-op count gate");
        assertTrue(rangeCheck >= 0, "count updates must preserve the existing nearby-leader range gate");
        assertTrue(entityMutation >= 0, "owner or op senders must still be able to update count");
        assertTrue(rangeCheck < entityMutation, "range validation must still run before count mutation");
        assertTrue(source.contains("player.hasPermissions(2)"),
                "op senders must pass into the authority gate before count mutation");
        assertTrue(source.contains("player.getUUID()"),
                "owner identity must pass into the authority gate before count mutation");
        assertTrue(entityMutation == source.lastIndexOf("leader.trySetCountFrom(player.getUUID(), player.hasPermissions(2), this.count)"),
                "the guarded handler path must be the only count mutation entry point");
    }

    @Test
    void foreignNonOpSenderLeavesAssassinLeaderCountUnchanged() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000008001");
        UUID foreignSender = UUID.fromString("00000000-0000-0000-0000-000000008002");
        ForeignAssassinLeader leader = new ForeignAssassinLeader(owner, 3);

        boolean changed = AssassinLeaderCountAuthority.trySetCount(leader.controlOwnerUUID, foreignSender, false,
                7, leader::setCount);

        assertFalse(changed, "foreign non-op senders must fail the count authority gate");
        assertEquals(3, leader.count, "foreign non-op sender must leave the assassin leader count unchanged");
    }

    private static final class ForeignAssassinLeader {
        private final UUID controlOwnerUUID;
        private int count;

        private ForeignAssassinLeader(UUID controlOwnerUUID, int count) {
            this.controlOwnerUUID = controlOwnerUUID;
            this.count = count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
