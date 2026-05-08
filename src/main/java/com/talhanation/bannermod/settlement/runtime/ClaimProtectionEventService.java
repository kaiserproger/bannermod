package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.events.SiegeExplosionTuning;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.validation.BuildingInvalidationReason;
import com.talhanation.bannermod.settlement.validation.BuildingInvalidationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class ClaimProtectionEventService {
    public void onBlockBreakEvent(BlockEvent.BreakEvent event) {
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyBlockBreak(event.getLevel(), event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
            ClaimProtectionFeedback.sendDenied(event.getPlayer(), event.getLevel(), event.getPos(), ClaimEvents.claimManager());
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            BuildingInvalidationRuntime.enqueueByBlockChange(level, event.getPos(), BuildingInvalidationReason.BLOCK_BROKEN);
        }
    }

    public void onBlockPlaceEvent(BlockEvent.EntityPlaceEvent event) {
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyBlockPlacement(event.getLevel(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                ClaimProtectionFeedback.sendDenied(player, event.getLevel(), event.getPos(), ClaimEvents.claimManager());
            }
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            BuildingInvalidationRuntime.enqueueByBlockChange(level, event.getPos(), BuildingInvalidationReason.BLOCK_PLACED);
        }
    }

    public void onFluidPlaceBlockEvent(BlockEvent.FluidPlaceBlockEvent event) {
        LevelAccessor level = event.getLevel();
        if(level.isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyFluidPlacement(level, event.getPos(), event.getLiquidPos())) {
            event.setCanceled(true);
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            BuildingInvalidationRuntime.enqueueByBlockChange(serverLevel, event.getPos(), BuildingInvalidationReason.FLUID_CHANGED);
        }
    }

    public void onExplosion(ExplosionEvent.Start event) {
        if(event.getLevel().isClientSide()) return;
        Vec3 vec = event.getExplosion().center();
        BlockPos pos = new BlockPos((int) vec.x, (int) vec.y, (int) vec.z);
        ChunkAccess access = ClaimEvents.server().overworld().getChunk(pos);
        RecruitsClaim claim = ClaimEvents.claimManager().getClaim(access.getPos());

        Entity entity = event.getExplosion().getDirectSourceEntity();
        if(entity instanceof Player player && player.isCreative() && player.hasPermissions(2)){
            return;
        }

        if(claim != null && RecruitsServerConfig.ExplosionProtectionInClaims.get()){
            // Allow the explosion through ONLY if an active siege is happening here:
            // a war in battle-window-eligible state, this position inside that war's
            // SiegeStandard zone, and the BattleWindowSchedule currently open. That
            // means TNT / Medieval Siege Machines blasts can land during a real
            // assault but are nullified during peacetime / outside battle hours.
            if (event.getLevel() instanceof ServerLevel serverLevel
                    && com.talhanation.bannermod.war.runtime.SiegeExplosionPolicy
                    .isExplosionAllowedDuringActiveSiege(serverLevel, pos)) {
                BuildingInvalidationRuntime.enqueueByBlockChange(serverLevel, pos, BuildingInvalidationReason.EXPLOSION);
                return;
            }
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            BuildingInvalidationRuntime.enqueueByBlockChange(level, pos, BuildingInvalidationReason.EXPLOSION);
        }
    }

    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (!SiegeExplosionTuning.shouldLimitTerrainDamage(event.getExplosion().getDirectSourceEntity())) {
            return;
        }
        SiegeExplosionTuning.limitAffectedBlocks(event.getExplosion().center(), event.getAffectedBlocks());
    }

    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if(event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if(claimProtectionPolicy().shouldDenyBlockInteraction(event.getLevel(), event.getPos(), player, event.getHand())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            ClaimProtectionFeedback.sendDenied(player, event.getLevel(), event.getPos(), ClaimEvents.claimManager());
        }
    }

    public void onItemInteract(PlayerInteractEvent.RightClickItem event) {
        if(event.getLevel().isClientSide()) return;
        BlockPos targetPos = ClaimInteractionTargetResolver.resolveItemInteractionTarget(event.getEntity(), event.getHand());
        if(targetPos == null) return;
        if(claimProtectionPolicy().shouldDenyBlockInteraction(event.getLevel(), targetPos, event.getEntity(), event.getHand())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            ClaimProtectionFeedback.sendDenied(event.getEntity(), event.getLevel(), targetPos, ClaimEvents.claimManager());
        }
    }

    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyEntityInteraction(event.getEntity(), event.getTarget())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            ClaimProtectionFeedback.sendDenied(event.getEntity(), event.getLevel(), event.getTarget().blockPosition(), ClaimEvents.claimManager());
        }
    }

    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyEntityInteraction(event.getEntity(), event.getTarget())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            ClaimProtectionFeedback.sendDenied(event.getEntity(), event.getLevel(), event.getTarget().blockPosition(), ClaimEvents.claimManager());
        }
    }

    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if(player.level().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyEntityAttack(player, event.getTarget())){
            event.setCanceled(true);
            ClaimProtectionFeedback.sendDenied(player, player.level(), event.getTarget().blockPosition(), ClaimEvents.claimManager());
        }
    }

    private ClaimProtectionPolicy claimProtectionPolicy() {
        return new ClaimProtectionPolicy(ClaimEvents.claimManager());
    }
}
