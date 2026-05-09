package com.talhanation.bannermod.events;

import com.talhanation.bannermod.entity.military.perks.PerkEffectService;
import com.talhanation.bannermod.entity.military.perks.PerkStat;
import com.talhanation.bannermod.entity.military.perks.PlayerPerkProgressService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class PlayerPerkEvents {
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PerkEffectService.applyPlayerAttributeBonuses(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLevelChange(PlayerXpEvent.LevelChange event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player && event.getLevels() > 0) {
            PlayerPerkProgressService.grantLevelPoints(player, event.getLevels());
        }
    }

    @SubscribeEvent
    public void onProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            applyPlayerProjectileBonuses(player, projectile);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer instanceof ServerPlayer player && killer != event.getEntity()) {
            PlayerPerkProgressService.grantKillCredit(player);
        }
    }

    public static void applyPlayerProjectileBonuses(ServerPlayer player, Projectile projectile) {
        Vec3 movement = projectile.getDeltaMovement();
        double speed = movement.length();
        if (speed <= 0.0D) {
            return;
        }
        double accuracyBonus = PerkEffectService.playerBonus(player, PerkStat.RANGED_ACCURACY);
        if (accuracyBonus > 0.0D) {
            Vec3 ideal = player.getLookAngle().normalize().scale(speed);
            movement = ideal.add(movement.subtract(ideal).scale(Math.max(0.0D, 1.0D - accuracyBonus)));
            projectile.setDeltaMovement(movement);
        }
        float adjusted = PerkEffectService.playerRangedVelocityFor(player, (float) speed);
        if (adjusted > speed) {
            projectile.setDeltaMovement(movement.normalize().scale(adjusted));
        }
    }
}
