package com.talhanation.bannermod.entity.military.perks;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.compat.CrossbowWeapon;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.BowmanEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import com.talhanation.bannermod.entity.military.HorsemanEntity;
import com.talhanation.bannermod.entity.military.RecruitShieldmanEntity;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModRecruitSkillTreeCombatGameTests {
    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000002d01");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void trackedRecruitPerksModifyCombatStats(GameTestHelper helper) {
        AbstractRecruitEntity swordsman = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper, ModEntityTypes.RECRUIT.get(), new BlockPos(1, 2, 1), "perk-swordsman", OWNER_UUID);
        double swordsmanDamage = value(swordsman, Attributes.ATTACK_DAMAGE);
        double swordsmanHealth = value(swordsman, Attributes.MAX_HEALTH);
        unlock(swordsman, "swordsman/iron_grip_i");
        unlock(swordsman, "universal/toughness_i");
        PerkEffectService.applyRecruitAttributeBonuses(swordsman);
        helper.assertTrue(value(swordsman, Attributes.ATTACK_DAMAGE) > swordsmanDamage,
                "Expected swordsman attack-damage perk to raise melee damage");
        helper.assertTrue(value(swordsman, Attributes.MAX_HEALTH) > swordsmanHealth,
                "Expected universal toughness perk to raise max health");

        RecruitShieldmanEntity pikeman = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper, ModEntityTypes.RECRUIT_SHIELDMAN.get(), new BlockPos(2, 2, 1), "perk-pikeman", OWNER_UUID);
        unlock(pikeman, "pikeman/braced_stance_i");
        assertAttributeIncreases(helper, pikeman, Attributes.KNOCKBACK_RESISTANCE,
                "Expected pikeman perk to raise knockback resistance");

        HorsemanEntity cavalry = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper, ModEntityTypes.HORSEMAN.get(), new BlockPos(3, 2, 1), "perk-cavalry", OWNER_UUID);
        unlock(cavalry, "cavalry/swift_charge_i");
        assertAttributeIncreases(helper, cavalry, Attributes.MOVEMENT_SPEED,
                "Expected cavalry perk to raise movement speed");

        BowmanEntity bowman = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper, ModEntityTypes.BOWMAN.get(), new BlockPos(4, 2, 1), "perk-bowman", OWNER_UUID);
        float bowBaseline = PerkEffectService.rangedInaccuracyFor(bowman, 4.0F);
        unlock(bowman, "bowman/steady_aim_i");
        float bowUnlocked = PerkEffectService.rangedInaccuracyFor(bowman, 4.0F);
        helper.assertTrue(bowUnlocked < bowBaseline,
                "Expected bowman accuracy perk to reduce ranged inaccuracy");

        CrossBowmanEntity crossbowman = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper, ModEntityTypes.CROSSBOWMAN.get(), new BlockPos(5, 2, 1), "perk-crossbowman", OWNER_UUID);
        CrossbowWeapon crossbow = new CrossbowWeapon();
        AbstractArrow baselineBolt = crossbow.getProjectileArrow(crossbowman);
        crossbow.shootArrow(crossbowman, baselineBolt, crossbowman.getX() + 8.0D, crossbowman.getY() + 1.5D, crossbowman.getZ());
        unlock(crossbowman, "crossbowman/heavy_bolts_i");
        AbstractArrow perkBolt = crossbow.getProjectileArrow(crossbowman);
        crossbow.shootArrow(crossbowman, perkBolt, crossbowman.getX() + 8.0D, crossbowman.getY() + 1.5D, crossbowman.getZ());
        helper.assertTrue(perkBolt.getDeltaMovement().lengthSqr() > baselineBolt.getDeltaMovement().lengthSqr(),
                "Expected crossbowman velocity perk to produce a faster captured projectile");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void freshRecruitKeepsBaselineStatsAndNoUnlockedPerks(GameTestHelper helper) {
        AbstractRecruitEntity recruit = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper, ModEntityTypes.RECRUIT.get(), new BlockPos(1, 2, 2), "perk-baseline", OWNER_UUID);

        double health = value(recruit, Attributes.MAX_HEALTH);
        double damage = value(recruit, Attributes.ATTACK_DAMAGE);
        double movement = value(recruit, Attributes.MOVEMENT_SPEED);
        PerkEffectService.applyRecruitAttributeBonuses(recruit);

        helper.assertTrue(recruit.getPerkProgress().getOwnedPerks().isEmpty(),
                "Expected freshly spawned recruit to have zero unlocked perks");
        helper.assertTrue(value(recruit, Attributes.MAX_HEALTH) == health,
                "Expected no-perk max health to keep the baseline value");
        helper.assertTrue(value(recruit, Attributes.ATTACK_DAMAGE) == damage,
                "Expected no-perk attack damage to keep the baseline value");
        helper.assertTrue(value(recruit, Attributes.MOVEMENT_SPEED) == movement,
                "Expected no-perk movement speed to keep the baseline value");
        helper.succeed();
    }

    private static void unlock(AbstractRecruitEntity recruit, String perkId) {
        recruit.getPerkProgress().grantPoints(1);
        PerkNode node = PerkRegistry.get(perkId).orElseThrow();
        if (recruit.getPerkProgress().unlock(node) != PerkProgress.UnlockResult.OK) {
            throw new IllegalStateException("Could not unlock test perk: " + perkId);
        }
    }

    private static void assertAttributeIncreases(GameTestHelper helper, AbstractRecruitEntity recruit,
                                                 net.minecraft.core.Holder<Attribute> attribute, String message) {
        double baseline = value(recruit, attribute);
        PerkEffectService.applyRecruitAttributeBonuses(recruit);
        helper.assertTrue(value(recruit, attribute) > baseline, message);
    }

    private static double value(AbstractRecruitEntity recruit, net.minecraft.core.Holder<Attribute> attribute) {
        return recruit.getAttributeValue(attribute);
    }
}
