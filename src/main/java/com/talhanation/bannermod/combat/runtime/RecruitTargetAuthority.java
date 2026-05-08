package com.talhanation.bannermod.combat.runtime;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitPoliticalContext;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalRelations;
import com.talhanation.bannermod.war.runtime.OccupationRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

public final class RecruitTargetAuthority {
    private RecruitTargetAuthority() {
    }

    static boolean canTargetUnderClaimAuthority(LivingEntity attacker, LivingEntity target) {
        if (!(attacker instanceof AbstractRecruitEntity) || !(attacker.level() instanceof ServerLevel level)) {
            return true;
        }
        RecruitsClaim claim = claimAt(level, target);
        if (claim == null || !isClaimProtectedTarget(target)) {
            return true;
        }
        UUID attackerPoliticalEntityId = RecruitPoliticalContext.politicalEntityIdOf(attacker, WarRuntimeContext.registry(level));
        UUID claimOwnerPoliticalEntityId = claim.getOwnerPoliticalEntityId();
        boolean occupiesClaim = occupiesClaim(level, attackerPoliticalEntityId, claimOwnerPoliticalEntityId, new ChunkPos(target.blockPosition()));
        boolean atWarWithClaimOwner = PoliticalRelations.atWar(
                WarRuntimeContext.declarations(level),
                attackerPoliticalEntityId,
                claimOwnerPoliticalEntityId
        );
        return claimAuthorityAllowsTarget(attackerPoliticalEntityId, claimOwnerPoliticalEntityId, occupiesClaim, atWarWithClaimOwner);
    }

    public static boolean claimAuthorityAllowsTarget(@Nullable UUID attackerPoliticalEntityId,
                                                     @Nullable UUID claimOwnerPoliticalEntityId,
                                                     boolean occupiesClaim,
                                                     boolean atWarWithClaimOwner) {
        if (claimOwnerPoliticalEntityId == null) {
            return true;
        }
        if (attackerPoliticalEntityId == null) {
            return false;
        }
        if (attackerPoliticalEntityId.equals(claimOwnerPoliticalEntityId)) {
            return true;
        }
        return occupiesClaim || atWarWithClaimOwner;
    }

    private static boolean isClaimProtectedTarget(LivingEntity target) {
        return !(target instanceof Enemy);
    }

    @Nullable
    private static RecruitsClaim claimAt(ServerLevel level, LivingEntity target) {
        if (ClaimEvents.claimManager() == null || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        ChunkAccess access = level.getChunk(target.blockPosition());
        return ClaimEvents.claimManager().getClaim(access.getPos());
    }

    private static boolean occupiesClaim(ServerLevel level,
                                         @Nullable UUID attackerPoliticalEntityId,
                                         @Nullable UUID claimOwnerPoliticalEntityId,
                                         ChunkPos claimChunk) {
        if (attackerPoliticalEntityId == null || claimOwnerPoliticalEntityId == null) {
            return false;
        }
        Collection<OccupationRecord> records = WarRuntimeContext.occupations(level)
                .forOccupiedClaimChunk(claimOwnerPoliticalEntityId, claimChunk);
        for (OccupationRecord record : records) {
            if (attackerPoliticalEntityId.equals(record.occupierEntityId())) {
                return true;
            }
        }
        return false;
    }
}
