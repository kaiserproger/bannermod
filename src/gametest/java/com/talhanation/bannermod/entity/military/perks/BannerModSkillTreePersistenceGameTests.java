package com.talhanation.bannermod.entity.military.perks;

import com.mojang.authlib.GameProfile;
import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModSkillTreePersistenceGameTests {
    private static final UUID RECRUIT_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000002c01");
    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000002c02");
    private static final String RECRUIT_PERK_ID = "universal/toughness_i";
    private static final String PLAYER_PERK_ID = "player/weapon_training_i";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void recruitPerksAndSkillPointsSurviveSaveLoad(GameTestHelper helper) {
        AbstractRecruitEntity recruit = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                new BlockPos(1, 2, 1),
                "skilltree-recruit",
                RECRUIT_OWNER_UUID
        );
        PerkNode node = PerkRegistry.get(RECRUIT_PERK_ID).orElseThrow();
        recruit.getPerkProgress().grantPoints(3);
        helper.assertTrue(recruit.getPerkProgress().unlock(node) == PerkProgress.UnlockResult.OK,
                "Expected recruit test perk to unlock before save");

        CompoundTag saved = BannerModDedicatedServerGameTestSupport.saveEntity(recruit);
        recruit.discard();
        AbstractRecruitEntity reloaded = BannerModDedicatedServerGameTestSupport.loadEntity(
                helper,
                ModEntityTypes.RECRUIT.get(),
                new BlockPos(2, 2, 1),
                saved
        );

        helper.assertTrue(reloaded.getPerkProgress().isOwned(RECRUIT_PERK_ID),
                "Expected recruit unlocked perk to survive save/load");
        helper.assertTrue(reloaded.getPerkProgress().getAvailablePoints() == 2,
                "Expected recruit skill points to survive save/load");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void playerAttachmentPerksAndSkillPointsSurviveSaveLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                level,
                PLAYER_UUID,
                "skilltree-player",
                helper.absolutePos(new BlockPos(1, 2, 2))
        );
        PlayerPerkProgressService.grantLevelPoints(player, 3);
        helper.assertTrue(PlayerPerkProgressService.unlock(player, PLAYER_PERK_ID) == PerkProgress.UnlockResult.OK,
                "Expected player test perk to unlock before save");

        ServerLevel destination = level.getServer().getLevel(Level.NETHER);
        if (destination == null) {
            destination = level;
        }
        if (destination != level) {
            player.setServerLevel(destination);
            player.moveTo(player.getX(), player.getY() + 1.0D, player.getZ(), player.getYRot(), player.getXRot());
        }
        helper.assertTrue(PlayerPerkProgressService.progress(player).isOwned(PLAYER_PERK_ID),
                "Expected player perk to survive dimension teleport before reload");

        CompoundTag saved = BannerModDedicatedServerGameTestSupport.saveEntity(player);
        ServerLevel currentLevel = player.serverLevel();
        ServerPlayer reloaded = new FakePlayer(currentLevel, new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000002c03"), "skilltree-reloaded"));
        reloaded.load(saved);

        PerkProgress restored = PlayerPerkProgressService.progress(reloaded);
        helper.assertTrue(restored.isOwned(PLAYER_PERK_ID),
                "Expected player attachment unlocked perk to survive save/load");
        helper.assertTrue(restored.getAvailablePoints() == 2,
                "Expected player attachment skill points to survive save/load");

        ServerPlayer dimensionReloaded = new FakePlayer(destination, new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000002c04"), "skilltree-dimension"));
        dimensionReloaded.load(saved);
        PerkProgress dimensionProgress = PlayerPerkProgressService.progress(dimensionReloaded);
        helper.assertTrue(dimensionProgress.isOwned(PLAYER_PERK_ID),
                "Expected player perk to survive reload after dimension transfer");
        helper.assertTrue(dimensionProgress.getAvailablePoints() == 2,
                "Expected player skill points to survive reload after dimension transfer");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void respecRefundsPointsWithoutChangingXpOrLevel(GameTestHelper helper) {
        AbstractRecruitEntity recruit = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                ModEntityTypes.RECRUIT.get(),
                new BlockPos(1, 2, 3),
                "skilltree-respec-recruit",
                RECRUIT_OWNER_UUID
        );
        recruit.setXpLevel(7);
        recruit.setXp(42);
        PerkNode node = PerkRegistry.get(RECRUIT_PERK_ID).orElseThrow();
        recruit.getPerkProgress().grantPoints(2);
        helper.assertTrue(recruit.getPerkProgress().unlock(node) == PerkProgress.UnlockResult.OK,
                "Expected recruit test perk to unlock before respec");

        int refund = recruit.getPerkProgress().respec();

        helper.assertTrue(refund == node.pointCost(),
                "Expected respec to refund the unlocked perk cost");
        helper.assertTrue(recruit.getPerkProgress().getAvailablePoints() == 2,
                "Expected respec to restore spent skill points");
        helper.assertTrue(recruit.getPerkProgress().getOwnedPerks().isEmpty(),
                "Expected respec to clear unlocked perks");
        helper.assertTrue(recruit.getXpLevel() == 7,
                "Expected respec to preserve recruit XP level");
        helper.assertTrue(recruit.getXp() == 42,
                "Expected respec to preserve recruit XP progress");
        helper.succeed();
    }
}
