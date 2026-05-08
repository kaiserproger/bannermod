package com.talhanation.bannermod.network.messages;

import com.mojang.authlib.GameProfile;
import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.BannerModGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.gametest.support.PacketGameTestSupport;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.network.messages.civilian.MessageUpdateOwner;
import com.talhanation.bannermod.network.messages.military.MessageAssignGroupToPlayer;
import com.talhanation.bannermod.network.messages.military.MessageAssignRecruitToPlayer;
import com.talhanation.bannermod.network.messages.military.MessageTeleportPlayer;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class PacketAuthorityGameTests {
    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000851");
    private static final UUID OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000852");
    private static final UUID RECIPIENT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000853");
    private static final UUID ADMIN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000854");
    private static final UUID GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000855");
    private static final UUID OFFLINE_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000856");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void recruitTransferPacketPathRejectsOutsiderAndAllowsOwnerAndAdmin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = createPlayer(helper, level, OWNER_UUID, "packet-recruit-owner");
        ServerPlayer outsider = createPlayer(helper, level, OUTSIDER_UUID, "packet-recruit-outsider");
        createPlayer(helper, level, RECIPIENT_UUID, "packet-recruit-recipient");
        ServerPlayer admin = createAdminPlayer(helper, level, ADMIN_UUID, "packet-recruit-admin");

        AbstractRecruitEntity denied = spawnOwnedRecruit(helper, OWNER_UUID, "Denied Packet Recruit");
        PacketGameTestSupport.dispatchServerbound(outsider,
                new MessageAssignRecruitToPlayer(denied.getUUID(), RECIPIENT_UUID),
                MessageAssignRecruitToPlayer::new);
        helper.assertTrue(OWNER_UUID.equals(denied.getOwnerUUID()),
                "Expected spoofed recruit transfer packet to leave ownership unchanged");

        AbstractRecruitEntity accepted = spawnOwnedRecruit(helper, OWNER_UUID, "Accepted Packet Recruit");
        PacketGameTestSupport.dispatchServerbound(owner,
                new MessageAssignRecruitToPlayer(accepted.getUUID(), RECIPIENT_UUID),
                MessageAssignRecruitToPlayer::new);
        helper.assertTrue(RECIPIENT_UUID.equals(accepted.getOwnerUUID()),
                "Expected owner recruit transfer packet to update ownership");

        AbstractRecruitEntity adminTarget = spawnOwnedRecruit(helper, OFFLINE_OWNER_UUID, "Admin Packet Recruit");
        PacketGameTestSupport.dispatchServerbound(admin,
                new MessageAssignRecruitToPlayer(adminTarget.getUUID(), RECIPIENT_UUID),
                MessageAssignRecruitToPlayer::new);
        helper.assertTrue(RECIPIENT_UUID.equals(adminTarget.getOwnerUUID()),
                "Expected admin recruit transfer packet to update ownership");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void groupTransferPacketPathRejectsOutsiderAndAllowsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = createPlayer(helper, level, OWNER_UUID, "packet-group-owner");
        ServerPlayer outsider = createPlayer(helper, level, OUTSIDER_UUID, "packet-group-outsider");
        createPlayer(helper, level, RECIPIENT_UUID, "packet-group-recipient");
        AbstractRecruitEntity recruit = spawnOwnedRecruit(helper, OWNER_UUID, "Packet Group Recruit");
        RecruitsBattleGameTestSupport.assignFormationCohort(List.of(recruit), GROUP_UUID);
        RecruitsGroup group = RecruitEvents.groupsManager().getGroup(GROUP_UUID);

        PacketGameTestSupport.dispatchServerbound(outsider,
                new MessageAssignGroupToPlayer(OWNER_UUID, new RecruitsPlayerInfo(RECIPIENT_UUID, "spoofed-recipient"), GROUP_UUID),
                MessageAssignGroupToPlayer::new);
        helper.assertTrue(OWNER_UUID.equals(group.getPlayerUUID()),
                "Expected spoofed group transfer packet to leave group owner unchanged");
        helper.assertTrue(OWNER_UUID.equals(recruit.getOwnerUUID()),
                "Expected spoofed group transfer packet to leave member owner unchanged");

        PacketGameTestSupport.dispatchServerbound(owner,
                new MessageAssignGroupToPlayer(OWNER_UUID, new RecruitsPlayerInfo(RECIPIENT_UUID, "spoofed-recipient"), GROUP_UUID),
                MessageAssignGroupToPlayer::new);
        helper.assertTrue(RECIPIENT_UUID.equals(group.getPlayerUUID()),
                "Expected owner group transfer packet to update group owner");
        helper.assertTrue(RECIPIENT_UUID.equals(recruit.getOwnerUUID()),
                "Expected owner group transfer packet to update member owner");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void worldMapTeleportPacketPathRejectsUnauthorizedAndAllowsCreativeOp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer outsider = createPlayer(helper, level, OUTSIDER_UUID, "packet-teleport-outsider");
        RecordingAdminPlayer admin = createAdminPlayer(helper, level, ADMIN_UUID, "packet-teleport-admin");
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos outsiderStart = outsider.blockPosition();

        PacketGameTestSupport.dispatchServerbound(outsider,
                new MessageTeleportPlayer(target),
                MessageTeleportPlayer::new);
        helper.assertTrue(outsiderStart.equals(outsider.blockPosition()),
                "Expected unauthorized teleport packet to leave the sender in place");

        PacketGameTestSupport.dispatchServerbound(admin,
                new MessageTeleportPlayer(target),
                MessageTeleportPlayer::new);
        helper.assertTrue(admin.teleportRequested && admin.teleportX == target.getX() && admin.teleportZ == target.getZ(),
                "Expected creative operator teleport packet to request a teleport to the requested X/Z");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void workAreaOwnerPacketPathRejectsOutsiderAndAllowsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player ownerPlayer = createPlayer(helper, level, OWNER_UUID, "packet-area-owner");
        ServerPlayer outsider = createPlayer(helper, level, OUTSIDER_UUID, "packet-area-outsider");
        createPlayer(helper, level, RECIPIENT_UUID, "packet-area-recipient");
        StorageArea area = BannerModGameTestSupport.spawnOwnedStorageArea(helper, ownerPlayer, new BlockPos(4, 2, 4));

        PacketGameTestSupport.dispatchServerbound(outsider,
                new MessageUpdateOwner(area.getUUID(), new RecruitsPlayerInfo(RECIPIENT_UUID, "spoofed-area-recipient")),
                MessageUpdateOwner::new);
        helper.assertTrue(OWNER_UUID.equals(area.getPlayerUUID()),
                "Expected unauthorized work-area owner packet to leave ownership unchanged");

        PacketGameTestSupport.dispatchServerbound((ServerPlayer) ownerPlayer,
                new MessageUpdateOwner(area.getUUID(), new RecruitsPlayerInfo(RECIPIENT_UUID, "spoofed-area-recipient")),
                MessageUpdateOwner::new);
        helper.succeedWhen(() -> {
            helper.assertTrue(RECIPIENT_UUID.equals(area.getPlayerUUID()),
                    "Expected owner work-area owner packet to update ownership");
            helper.assertTrue(!"spoofed-area-recipient".equals(area.getPlayerName()),
                    "Expected work-area owner packet to ignore the spoofed client name");
            helper.assertTrue(area.getPlayerName() != null && !area.getPlayerName().isBlank(),
                    "Expected work-area owner packet to resolve a non-blank server-side owner name");
        });
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, ServerLevel level, UUID playerId, String name) {
        ServerPlayer player = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, playerId, name);
        BlockPos pos = helper.absolutePos(RecruitsBattleGameTestSupport.SquadAnchor.WEST.anchor());
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, -90.0F, 0.0F);
        return player;
    }

    private static RecordingAdminPlayer createAdminPlayer(GameTestHelper helper, ServerLevel level, UUID playerId, String name) {
        RecordingAdminPlayer player = new RecordingAdminPlayer(level, new GameProfile(playerId, name));
        BlockPos pos = helper.absolutePos(RecruitsBattleGameTestSupport.SquadAnchor.WEST.anchor());
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, -90.0F, 0.0F);
        player.getAbilities().instabuild = true;
        level.addFreshEntity(player);
        return player;
    }

    private static AbstractRecruitEntity spawnOwnedRecruit(GameTestHelper helper, UUID ownerId, String name) {
        AbstractRecruitEntity recruit = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.SquadAnchor.WEST.recoveryLeftPos(),
                name,
                ownerId
        );
        recruit.setListen(true);
        return recruit;
    }

    private static class RecordingAdminPlayer extends FakePlayer {
        private boolean teleportRequested;
        private double teleportX;
        private double teleportZ;

        private RecordingAdminPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean hasPermissions(int permissionLevel) {
            return true;
        }

        @Override
        public boolean isCreative() {
            return true;
        }

        @Override
        public void teleportTo(double x, double y, double z) {
            this.teleportRequested = true;
            this.teleportX = x;
            this.teleportZ = z;
            super.teleportTo(x, y, z);
        }
    }
}
