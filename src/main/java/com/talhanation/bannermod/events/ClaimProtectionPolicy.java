package com.talhanation.bannermod.events;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.war.config.WarServerConfig;
import com.talhanation.bannermod.war.runtime.WarSiegeQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;

final class ClaimProtectionPolicy {

    private final RecruitsClaimManager claimManager;

    ClaimProtectionPolicy(RecruitsClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    boolean shouldDenyBlockBreak(LevelAccessor level, BlockPos pos, Player player) {
        if (ClaimAccessQueries.hasAdminBypass(player)) return false;
        RecruitsClaim claim = ClaimAccessQueries.getClaim(claimManager, level, pos);
        if (claim == null) {
            return RecruitsServerConfig.BlockPlacingBreakingOnlyWhenClaimed.get();
        }
        if (siegeBlocksManualAction(level, claim, player)) return true;
        return !claim.isBlockBreakingAllowed() && !ClaimAccessQueries.isFriendlyToClaim(player, claim);
    }

    boolean shouldDenyBlockPlacement(LevelAccessor level, BlockPos pos, Entity entity) {
        if (ClaimAccessQueries.hasAdminBypass(entity)) return false;
        RecruitsClaim claim = ClaimAccessQueries.getClaim(claimManager, level, pos);
        if (claim == null) {
            return RecruitsServerConfig.BlockPlacingBreakingOnlyWhenClaimed.get();
        }
        if (entity instanceof Player p && siegeBlocksManualAction(level, claim, p)) return true;
        return !claim.isBlockPlacementAllowed() && !ClaimAccessQueries.isFriendlyToClaim(entity, claim);
    }

    boolean shouldDenyFluidPlacement(LevelAccessor level, BlockPos targetPos, BlockPos sourcePos) {
        RecruitsClaim targetClaim = ClaimAccessQueries.getClaim(claimManager, level, targetPos);
        if (targetClaim == null) {
            return RecruitsServerConfig.BlockPlacingBreakingOnlyWhenClaimed.get();
        }
        if (targetClaim.isBlockPlacementAllowed()) {
            return false;
        }

        RecruitsClaim sourceClaim = ClaimAccessQueries.getClaim(claimManager, level, sourcePos);
        if (sourceClaim == null) {
            return true;
        }

        return !sourceClaim.getUUID().equals(targetClaim.getUUID());
    }

    boolean shouldDenyBlockInteraction(LevelAccessor level, BlockPos pos, Player player, InteractionHand hand) {
        if (ClaimAccessQueries.hasAdminBypass(player)) return false;
        RecruitsClaim claim = ClaimAccessQueries.getClaim(claimManager, level, pos);
        if (claim == null || ClaimAccessQueries.isFriendlyToClaim(player, claim)) return false;
        if (siegeBlocksManualAction(level, claim, player)) return true;

        if (!claim.isBlockInteractionAllowed()) {
            return true;
        }

        return !claim.isBlockPlacementAllowed() && ClaimInteractionTargetResolver.handTriggersPlacement(player, hand);
    }

    boolean shouldDenyEntityInteraction(Player player, Entity target) {
        if (ClaimAccessQueries.hasAdminBypass(player)) return false;
        RecruitsClaim claim = ClaimAccessQueries.getClaim(claimManager, player.level(), target.blockPosition());
        if (claim == null) return false;
        if (siegeBlocksManualAction(player.level(), claim, player)) return true;
        return !claim.isBlockInteractionAllowed() && !ClaimAccessQueries.isFriendlyToClaim(player, claim);
    }

    boolean shouldDenyEntityAttack(Player player, Entity target) {
        if (ClaimAccessQueries.hasAdminBypass(player)) return false;
        RecruitsClaim claim = ClaimAccessQueries.getClaim(claimManager, player.level(), target.blockPosition());
        return claim != null && !ClaimAccessQueries.isFriendlyToClaim(player, claim);
    }

    /**
     * Returns true when the claim is currently under siege and the config flag restricts
     * non-friendly manual actions to explosives only. Defenders and friends keep their
     * normal rights; explosions and siege machines do not pass through these player-event
     * paths and so naturally bypass this gate.
     */
    private boolean siegeBlocksManualAction(LevelAccessor level, RecruitsClaim claim, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (!WarServerConfig.SiegeProtectionAttackersExplosivesOnly.get()) return false;
        if (ClaimAccessQueries.isFriendlyToClaim(player, claim)) return false;
        return WarSiegeQueries.isClaimUnderSiege(serverLevel, claim);
    }
}
