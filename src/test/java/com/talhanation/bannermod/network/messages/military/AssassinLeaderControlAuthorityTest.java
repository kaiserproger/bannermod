package com.talhanation.bannermod.network.messages.military;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

        int controlCheck = source.indexOf("leader.isControlledBy(player)");
        int countMutation = source.indexOf("leader.setCount(this.count)");
        int ownerAssignment = source.indexOf("assignControlOwnerIfAbsent");

        assertTrue(controlCheck >= 0, "count updates must require the server-side control owner");
        assertTrue(countMutation >= 0, "authorized count updates must still set the leader count");
        assertTrue(controlCheck < countMutation,
                "forged count packets must hit the control-owner check before count mutation");
        assertTrue(ownerAssignment < 0, "count packets must not assign or claim control ownership");
    }
}
