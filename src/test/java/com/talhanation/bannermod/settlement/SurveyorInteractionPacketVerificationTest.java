package com.talhanation.bannermod.settlement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyorInteractionPacketVerificationTest {
    private static final Path ROOT = Path.of("");

    @Test
    void surveyorClicksRouteThroughDedicatedPacket() throws IOException {
        String surveyorTool = read("src/main/java/com/talhanation/bannermod/items/civilian/SettlementSurveyorToolItem.java");
        String useBlockMessage = read("src/main/java/com/talhanation/bannermod/network/messages/civilian/MessageUseSurveyorBlock.java");
        String packetCatalog = read("src/main/java/com/talhanation/bannermod/network/catalog/CivilianPacketCatalog.java");

        assertTrue(surveyorTool.contains("new MessageUseSurveyorBlock(context.getHand(), clicked)"));
        assertTrue(surveyorTool.contains("handleBlockClick(ServerPlayer player, ItemStack stack, BlockPos clicked)"));
        assertTrue(useBlockMessage.contains("player.getEyePosition().distanceToSqr(Vec3.atCenterOf(this.clickedPos)) > MAX_CLICK_DISTANCE_SQR"));
        assertTrue(useBlockMessage.contains("SettlementSurveyorToolItem.handleBlockClick(player, stack, this.clickedPos);"));
        assertTrue(packetCatalog.contains("MessageUseSurveyorBlock.class"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
