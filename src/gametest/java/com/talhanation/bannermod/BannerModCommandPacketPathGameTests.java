package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.gametest.support.RecruitsCommandGameTestSupport;
import com.talhanation.bannermod.network.messages.military.MessageFaceCommand;
import com.talhanation.bannermod.network.messages.military.MessageRangedFire;
import com.talhanation.bannermod.network.messages.military.MessageUpkeepPos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModCommandPacketPathGameTests {

    private static final UUID FACE_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID FACE_OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID RANGED_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000803");
    private static final UUID RANGED_OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000804");
    private static final UUID UPKEEP_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000805");
    private static final UUID UPKEEP_OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000806");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void facePacketPathAcceptsOwnerAndRejectsSpoofedOutsider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = commandSender(level, FACE_OWNER_UUID, "face-owner", helper, -45.0F);
        ServerPlayer outsider = commandSender(level, FACE_OUTSIDER_UUID, "face-outsider", helper, 90.0F);
        AbstractRecruitEntity recruit = commandRecruit(helper, FACE_OWNER_UUID, "Face Packet Recruit");

        MessageFaceCommand.dispatchToServer(owner, owner.getUUID(), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID, 0, false);

        helper.assertTrue(recruit.getFollowState() == 3,
                "Expected the owner face packet path to place the recruit into face/hold state");
        helper.assertTrue(recruit.rotateTicks == 40 && recruit.ownerRot == owner.getYRot(),
                "Expected the owner face packet path to apply sender rotation to the recruit");

        recruit.setFollowState(0);
        recruit.rotateTicks = 0;
        recruit.ownerRot = 0.0F;

        MessageFaceCommand.dispatchToServer(outsider, owner.getUUID(), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID, 0, false);

        helper.assertTrue(recruit.getFollowState() == 0 && recruit.rotateTicks == 0 && recruit.ownerRot == 0.0F,
                "Expected a spoofed outsider face packet to leave the owner recruit unchanged");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void rangedFirePacketPathAcceptsOwnerAndRejectsSpoofedOutsider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = commandSender(level, RANGED_OWNER_UUID, "ranged-owner", helper, -45.0F);
        ServerPlayer outsider = commandSender(level, RANGED_OUTSIDER_UUID, "ranged-outsider", helper, -45.0F);
        AbstractRecruitEntity recruit = commandRecruit(helper, RANGED_OWNER_UUID, "Ranged Packet Recruit");

        MessageRangedFire.dispatchToServer(owner, owner.getUUID(), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID, true);

        helper.assertTrue(recruit.getShouldRanged(),
                "Expected the owner ranged-fire packet path to enable ranged fire on the recruit");

        recruit.setShouldRanged(false);

        MessageRangedFire.dispatchToServer(outsider, owner.getUUID(), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID, true);

        helper.assertFalse(recruit.getShouldRanged(),
                "Expected a spoofed outsider ranged-fire packet to leave ranged fire disabled");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void upkeepPacketPathAcceptsOwnerAndRejectsSpoofedOutsider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = commandSender(level, UPKEEP_OWNER_UUID, "upkeep-owner", helper, -45.0F);
        ServerPlayer outsider = commandSender(level, UPKEEP_OUTSIDER_UUID, "upkeep-outsider", helper, -45.0F);
        AbstractRecruitEntity recruit = commandRecruit(helper, UPKEEP_OWNER_UUID, "Upkeep Packet Recruit");
        BlockPos upkeepPos = helper.absolutePos(RecruitsBattleGameTestSupport.WEST_FLANK_POS);

        MessageUpkeepPos.dispatchToServer(owner, owner.getUUID(), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID, upkeepPos);

        helper.assertTrue(upkeepPos.equals(recruit.getUpkeepPos()),
                "Expected the owner upkeep packet path to assign the recruit upkeep position");

        recruit.clearUpkeepPos();

        MessageUpkeepPos.dispatchToServer(outsider, owner.getUUID(), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID, upkeepPos);

        helper.assertTrue(recruit.getUpkeepPos() == null,
                "Expected a spoofed outsider upkeep packet to leave recruit upkeep unchanged");
        helper.succeed();
    }

    private static ServerPlayer commandSender(ServerLevel level, UUID playerId, String name, GameTestHelper helper, float yRot) {
        ServerPlayer player = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(
                level,
                playerId,
                name
        );
        BlockPos pos = helper.absolutePos(RecruitsBattleGameTestSupport.SquadAnchor.WEST.anchor());
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yRot, 0.0F);
        player.setYRot(yRot);
        return player;
    }

    private static AbstractRecruitEntity commandRecruit(GameTestHelper helper, UUID ownerId, String name) {
        AbstractRecruitEntity recruit = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                com.talhanation.bannermod.registry.military.ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_FRONTLINE_POS,
                name,
                ownerId
        );
        RecruitsCommandGameTestSupport.prepareForCommand(recruit, RecruitsCommandGameTestSupport.TARGET_GROUP_UUID);
        return recruit;
    }
}
