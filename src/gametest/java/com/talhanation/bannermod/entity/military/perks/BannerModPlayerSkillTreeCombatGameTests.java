package com.talhanation.bannermod.entity.military.perks;

import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.events.PlayerPerkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModPlayerSkillTreeCombatGameTests {
    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000002d02");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void playerPerksModifyCombatStatsAndProjectiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                level, PLAYER_UUID, "perk-player", helper.absolutePos(new BlockPos(1, 2, 1)));

        double health = value(player, Attributes.MAX_HEALTH);
        double knockback = value(player, Attributes.KNOCKBACK_RESISTANCE);
        double damage = value(player, Attributes.ATTACK_DAMAGE);
        double attackSpeed = value(player, Attributes.ATTACK_SPEED);
        double movement = value(player, Attributes.MOVEMENT_SPEED);
        float inaccuracy = PerkEffectService.playerRangedInaccuracyFor(player, 4.0F);
        PlayerPerkEvents events = new PlayerPerkEvents();
        AbstractArrow baselineArrow = arrow(level, player);
        baselineArrow.shoot(1.0D, 0.0D, 0.0D, 2.0F, 0.0F);
        events.onProjectileJoin(new EntityJoinLevelEvent(baselineArrow, level));

        unlock(player, "player/toughness_i");
        unlock(player, "player/iron_skin_i");
        unlock(player, "player/weapon_training_i");
        unlock(player, "player/quick_hands_i");
        unlock(player, "player/marching_drill_i");
        unlock(player, "player/steady_aim_i");
        unlock(player, "player/strong_draw_i");
        PerkEffectService.applyPlayerAttributeBonuses(player);
        AbstractArrow perkArrow = arrow(level, player);
        perkArrow.shoot(1.0D, 0.0D, 0.0D, 2.0F, 0.0F);
        events.onProjectileJoin(new EntityJoinLevelEvent(perkArrow, level));

        helper.assertTrue(value(player, Attributes.MAX_HEALTH) > health,
                "Expected player max-health perk to raise max health");
        helper.assertTrue(value(player, Attributes.KNOCKBACK_RESISTANCE) > knockback,
                "Expected player knockback perk to raise knockback resistance");
        helper.assertTrue(value(player, Attributes.ATTACK_DAMAGE) > damage,
                "Expected player damage perk to raise attack damage");
        helper.assertTrue(value(player, Attributes.ATTACK_SPEED) > attackSpeed,
                "Expected player attack-speed perk to raise attack speed");
        helper.assertTrue(value(player, Attributes.MOVEMENT_SPEED) > movement,
                "Expected player movement perk to raise movement speed");
        helper.assertTrue(PerkEffectService.playerRangedInaccuracyFor(player, 4.0F) < inaccuracy,
                "Expected player accuracy perk to reduce ranged inaccuracy");
        helper.assertTrue(perkArrow.getDeltaMovement().lengthSqr() > baselineArrow.getDeltaMovement().lengthSqr(),
                "Expected player velocity perk to produce a faster captured projectile");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void playerLevelEarningKillCreditAndRespecMatchRecruitRate(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                helper.getLevel(), UUID.fromString("00000000-0000-0000-0000-000000002d03"), "perk-earner", helper.absolutePos(new BlockPos(1, 2, 2)));
        PlayerPerkEvents events = new PlayerPerkEvents();

        events.onPlayerLevelChange(new PlayerXpEvent.LevelChange(player, 3));
        helper.assertTrue(PlayerPerkProgressService.availablePoints(player) == 3,
                "Expected player level earning to grant one perk point per gained level");
        PlayerPerkProgressService.grantKillCredit(player);
        helper.assertTrue(PlayerPerkProgressService.availablePoints(player) == 4,
                "Expected player kill credit to grant the same single-point increment");

        if (PlayerPerkProgressService.unlock(player, "player/weapon_training_i") != PerkProgress.UnlockResult.OK) {
            throw new IllegalStateException("Could not unlock player earning test perk");
        }
        helper.assertTrue(PlayerPerkProgressService.availablePoints(player) == 3,
                "Expected unlocking one cost-1 player perk to spend one point");
        int refund = PlayerPerkProgressService.respec(player);
        helper.assertTrue(refund == 1,
                "Expected player respec to refund the unlocked perk cost");
        helper.assertTrue(PlayerPerkProgressService.availablePoints(player) == 4,
                "Expected player respec to restore spent points");
        helper.assertTrue(PlayerPerkProgressService.unlockedPerkIds(player).isEmpty(),
                "Expected player respec to clear unlocked perks");
        helper.assertTrue(PlayerPerkProgressService.perkPointsPerLevel() == 1,
                "Expected player point rate to match recruit point rate numerically");
        helper.succeed();
    }

    private static void unlock(ServerPlayer player, String perkId) {
        PlayerPerkProgressService.progress(player).grantPoints(1);
        if (PlayerPerkProgressService.unlock(player, perkId) != PerkProgress.UnlockResult.OK) {
            throw new IllegalStateException("Could not unlock player test perk: " + perkId);
        }
    }

    private static AbstractArrow arrow(ServerLevel level, ServerPlayer player) {
        return new Arrow(level, player, Items.ARROW.getDefaultInstance(), Items.BOW.getDefaultInstance());
    }

    private static double value(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute) {
        return player.getAttributeValue(attribute);
    }
}
