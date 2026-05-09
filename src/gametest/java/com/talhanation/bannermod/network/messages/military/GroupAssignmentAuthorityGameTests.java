package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.gametest.support.RecruitsCommandGameTestSupport;
import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class GroupAssignmentAuthorityGameTests {
    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000821");
    private static final UUID OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000822");
    private static final UUID NEW_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000823");
    private static final UUID TRANSFER_GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000824");
    private static final UUID SPOOFED_TRANSFER_GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000825");
    private static final UUID TRUSTED_NEW_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000826");
    private static final UUID UPDATE_GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000827");
    private static final UUID UPDATE_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000828");
    private static final UUID UPDATE_OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000829");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void leaderGroupAssignmentRejectsOutsiderAndAllowsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = createPlayer(helper, level, OWNER_UUID, "group-owner");
        ServerPlayer outsider = createPlayer(helper, level, OUTSIDER_UUID, "group-outsider");
        AbstractLeaderEntity leader = spawnOwnedLeader(helper, OWNER_UUID);

        RecruitsBattleGameTestSupport.assignFormationCohort(
                List.of(leader), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID);
        leader.setGroupUUID(null);

        helper.assertFalse(MessageSetLeaderGroup.canApplyLeaderGroup(
                        outsider, leader, RecruitsCommandGameTestSupport.TARGET_GROUP_UUID),
                "Expected outsider to be denied leader group assignment");
        helper.assertTrue(MessageSetLeaderGroup.canApplyLeaderGroup(
                        owner, leader, RecruitsCommandGameTestSupport.TARGET_GROUP_UUID),
                "Expected owner to be allowed leader group assignment");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void companionGroupAssignmentRejectsOutsiderAndAllowsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = createPlayer(helper, level, OWNER_UUID, "companion-owner");
        ServerPlayer outsider = createPlayer(helper, level, OUTSIDER_UUID, "companion-outsider");
        AbstractLeaderEntity leader = spawnOwnedLeader(helper, OWNER_UUID);

        RecruitsBattleGameTestSupport.assignFormationCohort(
                List.of(leader), RecruitsCommandGameTestSupport.TARGET_GROUP_UUID);

        helper.assertFalse(MessageAssignGroupToCompanion.canAssignCompanionGroup(outsider, leader),
                "Expected outsider to be denied companion group assignment");
        helper.assertTrue(MessageAssignGroupToCompanion.canAssignCompanionGroup(owner, leader),
                "Expected owner to be allowed companion group assignment");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void groupTransferRejectsSpoofedOutsider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        createPlayer(helper, level, OWNER_UUID, "transfer-owner");
        ServerPlayer outsider = createPlayer(helper, level, OUTSIDER_UUID, "transfer-outsider");
        createPlayer(helper, level, NEW_OWNER_UUID, "transfer-new-owner");
        AbstractRecruitEntity recruit = spawnOwnedRecruit(helper, OWNER_UUID, "Spoofed Transfer Recruit");
        RecruitsBattleGameTestSupport.assignFormationCohort(List.of(recruit), SPOOFED_TRANSFER_GROUP_UUID);

        RecruitsGroup group = RecruitEvents.groupsManager().getGroup(SPOOFED_TRANSFER_GROUP_UUID);
        boolean transferred = MessageAssignGroupToPlayer.transferGroupToPlayer(
                outsider,
                SPOOFED_TRANSFER_GROUP_UUID,
                new RecruitsPlayerInfo(NEW_OWNER_UUID, "spoofed-new-owner")
        );

        helper.assertFalse(transferred, "Expected spoofed outsider group transfer to be denied");
        helper.assertTrue(OWNER_UUID.equals(group.getPlayerUUID()), "Expected denied transfer to keep group owner");
        helper.assertTrue(OWNER_UUID.equals(recruit.getOwnerUUID()), "Expected denied transfer to keep member owner");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void groupTransferUpdatesGroupAndMembersFromTrustedPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = createPlayer(helper, level, OWNER_UUID, "valid-transfer-owner");
        createPlayer(helper, level, TRUSTED_NEW_OWNER_UUID, "trusted-new-owner");
        AbstractRecruitEntity recruit = spawnOwnedRecruit(helper, OWNER_UUID, "Valid Transfer Recruit");
        RecruitsBattleGameTestSupport.assignFormationCohort(List.of(recruit), TRANSFER_GROUP_UUID);

        RecruitsGroup group = RecruitEvents.groupsManager().getGroup(TRANSFER_GROUP_UUID);
        boolean transferred = MessageAssignGroupToPlayer.transferGroupToPlayer(
                owner,
                TRANSFER_GROUP_UUID,
                new RecruitsPlayerInfo(TRUSTED_NEW_OWNER_UUID, "client-spoofed-name")
        );

        helper.assertTrue(transferred, "Expected owner group transfer to be allowed");
        helper.assertTrue(TRUSTED_NEW_OWNER_UUID.equals(group.getPlayerUUID()), "Expected valid transfer to update group owner");
        helper.assertTrue("trusted-new-owner".equals(group.getPlayerName()), "Expected group owner name from server player state");
        helper.assertTrue(TRUSTED_NEW_OWNER_UUID.equals(recruit.getOwnerUUID()), "Expected valid transfer to update member owner");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void groupUpdateRejectsNonOwnerAndKeepsExistingGroup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = createPlayer(helper, level, UPDATE_OWNER_UUID, "update-owner");
        ServerPlayer outsider = createPlayer(helper, level, UPDATE_OUTSIDER_UUID, "update-outsider");

        RecruitsGroup group = new RecruitsGroup("Owner Group", owner, 1);
        group.setUUID(UPDATE_GROUP_UUID);
        RecruitEvents.groupsManager().addOrUpdateGroup(level, owner, group);

        RecruitsGroup spoofedUpdate = new RecruitsGroup("Spoofed Group", outsider, 9);
        spoofedUpdate.setUUID(UPDATE_GROUP_UUID);
        spoofedUpdate.removed = true;
        RecruitEvents.groupsManager().addOrUpdateGroup(level, outsider, spoofedUpdate);

        RecruitsGroup saved = RecruitEvents.groupsManager().getGroup(UPDATE_GROUP_UUID);
        helper.assertTrue(saved != null, "Expected denied update to keep existing group");
        helper.assertTrue(UPDATE_OWNER_UUID.equals(saved.getPlayerUUID()), "Expected denied update to keep owner UUID");
        helper.assertTrue("update-owner".equals(saved.getPlayerName()), "Expected denied update to keep owner name");
        helper.assertTrue("Owner Group".equals(saved.getName()), "Expected denied update to keep group name");
        helper.assertFalse(saved.removed, "Expected denied update not to remove existing group");
        helper.succeed();
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, ServerLevel level, UUID playerId, String name) {
        Player player = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, playerId, name);
        BlockPos pos = helper.absolutePos(RecruitsBattleGameTestSupport.SquadAnchor.WEST.anchor());
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, -90.0F, 0.0F);
        return (ServerPlayer) player;
    }

    private static AbstractLeaderEntity spawnOwnedLeader(GameTestHelper helper, UUID ownerId) {
        return RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.PATROL_LEADER.get(),
                RecruitsBattleGameTestSupport.SquadAnchor.WEST.recoveryLeftPos(),
                "Authority Leader",
                ownerId
        );
    }

    private static AbstractRecruitEntity spawnOwnedRecruit(GameTestHelper helper, UUID ownerId, String name) {
        return RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.SquadAnchor.WEST.recoveryRightPos(),
                name,
                ownerId
        );
    }
}
