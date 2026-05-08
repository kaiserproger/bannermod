package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitPoliticalContext;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.war.WarRuntimeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.UUID;

final class ClaimAccessQueries {

    private ClaimAccessQueries() {
    }

    static RecruitsClaim getClaim(RecruitsClaimManager claimManager, LevelAccessor level, BlockPos pos) {
        if (claimManager == null) return null;
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) return null;
        ChunkAccess access = level.getChunk(pos);
        return claimManager.getClaim(access.getPos());
    }

    static boolean hasAdminBypass(Entity entity) {
        return entity instanceof Player player && hasAdminBypass(player);
    }

    static boolean hasAdminBypass(Player player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    static boolean isFriendlyToClaim(Entity entity, RecruitsClaim claim) {
        return entity instanceof LivingEntity livingEntity && isFriendlyToClaim(livingEntity, claim);
    }

    static boolean isFriendlyToClaim(LivingEntity livingEntity, RecruitsClaim claim) {
        UUID actorUuid = actorUuid(livingEntity);
        if (claim.isTrustedPlayer(actorUuid)) {
            return true;
        }
        if (claim.getOwnerPoliticalEntityId() == null) {
            return false;
        }
        if (livingEntity.level() instanceof ServerLevel serverLevel) {
            if (claim.getOwnerPoliticalEntityId().equals(
                    RecruitPoliticalContext.politicalEntityIdOf(livingEntity, WarRuntimeContext.registry(serverLevel)))) {
                return true;
            }
        }
        return livingEntity.getTeam() != null
                && livingEntity.getTeam().getName().equals(claim.getOwnerPoliticalEntityId().toString());
    }

    private static UUID actorUuid(LivingEntity livingEntity) {
        if (livingEntity instanceof Player player) {
            return player.getUUID();
        }
        if (livingEntity instanceof AbstractRecruitEntity recruit) {
            return recruit.getOwnerUUID();
        }
        return null;
    }
}
