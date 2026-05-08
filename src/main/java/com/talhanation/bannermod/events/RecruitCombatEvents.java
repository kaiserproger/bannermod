package com.talhanation.bannermod.events;

import com.talhanation.bannermod.combat.runtime.RecruitCombatRuntime;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class RecruitCombatEvents {
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        RecruitCombatRuntime.onProjectileImpact(event);
    }

    @SubscribeEvent
    public void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        RecruitCombatRuntime.onEntityLeaveWorld(event);
    }

    @SubscribeEvent
    public void onPlayerInteractWithCaravan(PlayerInteractEvent.EntityInteract entityInteract) {
        RecruitCombatRuntime.onPlayerInteractWithCaravan(entityInteract);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingIncomingDamageEvent event) {
        RecruitCombatRuntime.onLivingHurt(event);
    }

    @SubscribeEvent
    public void onLivingAttack(LivingIncomingDamageEvent event) {
        RecruitCombatRuntime.onLivingAttack(event);
    }

    @SubscribeEvent
    public void onRecruitDeath(LivingDeathEvent event) {
        RecruitCombatRuntime.onRecruitDeath(event);
    }

    @SubscribeEvent
    public void onWorldTickArrowCleaner(LevelTickEvent.Post event) {
        RecruitCombatRuntime.onWorldTickArrowCleaner(event);
    }
}
