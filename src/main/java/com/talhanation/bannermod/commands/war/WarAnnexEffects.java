package com.talhanation.bannermod.commands.war;

import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.UUID;

/**
 * Post-annex re-binding side effects: walks the annexed claim's chunks and re-teams any
 * non-player entities (workers, recruits, work areas) whose current team matches the
 * defender's political-entity UUID, moving them onto the attacker's team. Best-effort;
 * entities outside the claim's loaded chunks at the time of annex are not touched.
 */
public final class WarAnnexEffects {
    private WarAnnexEffects() {
    }

    /**
     * @return the number of entities re-teamed.
     */
    public static int rebindEntitiesToNewOwner(ServerLevel level,
                                               RecruitsClaim claim,
                                               UUID defenderEntityId,
                                               UUID attackerEntityId) {
        if (level == null || claim == null || defenderEntityId == null || attackerEntityId == null) {
            return 0;
        }
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam attackerTeam = scoreboard.getPlayerTeam(attackerEntityId.toString());
        String defenderTeamName = defenderEntityId.toString();
        int rebound = 0;
        for (ChunkPos chunk : claim.getClaimedChunks()) {
            AABB chunkBounds = new AABB(
                    chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
            for (Entity entity : level.getEntities((Entity) null, chunkBounds, e -> e != null)) {
                if (entity.getTeam() == null) continue;
                if (!defenderTeamName.equals(entity.getTeam().getName())) continue;
                if (attackerTeam != null) {
                    scoreboard.addPlayerToTeam(entity.getScoreboardName(), attackerTeam);
                } else {
                    scoreboard.removePlayerFromTeam(entity.getScoreboardName());
                }
                if (entity instanceof AbstractWorkAreaEntity workArea) {
                    workArea.setTeamStringID(attackerTeam == null ? "" : attackerEntityId.toString());
                }
                rebound++;
            }
        }
        return rebound;
    }
}
