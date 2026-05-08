package com.talhanation.bannermod.combat.runtime;

import com.talhanation.bannermod.entity.military.RecruitPoliticalContext;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalRelations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Team;

import java.util.UUID;

final class RecruitDiplomacyPolicy {
    private static final double DAMAGE_THRESHOLD_PERCENTAGE = 0.75;

    private RecruitDiplomacyPolicy() {
    }

    static boolean isAlly(Team team1, Team team2) {
        return team1 != null && team1.equals(team2);
    }

    static boolean isEnemy(Team team1, Team team2) {
        return false;
    }

    static boolean isEnemy(LivingEntity attacker, LivingEntity target) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return false;
        }
        UUID attackerEntityId = RecruitPoliticalContext.politicalEntityIdOf(attacker, WarRuntimeContext.registry(level));
        UUID targetEntityId = RecruitPoliticalContext.politicalEntityIdOf(target, WarRuntimeContext.registry(level));
        return attackerEntityId != null
                && targetEntityId != null
                && !attackerEntityId.equals(targetEntityId)
                && PoliticalRelations.atWar(WarRuntimeContext.declarations(level), attackerEntityId, targetEntityId);
    }

    static boolean isNeutral(Team team1, Team team2) {
        return team1 == null || team2 == null || !team1.equals(team2);
    }

    static boolean canHarmTeam(LivingEntity attacker, LivingEntity target) {
        Team attackerTeam = attacker.getTeam();
        Team targetTeam = target.getTeam();
        if (attackerTeam == null || targetTeam == null) return true;
        if (attackerTeam.equals(targetTeam) && !attackerTeam.isAllowFriendlyFire()) return false;
        if (!(attacker.level() instanceof ServerLevel level)) return true;
        UUID attackerEntityId = RecruitPoliticalContext.politicalEntityIdOf(attacker, WarRuntimeContext.registry(level));
        UUID targetEntityId = RecruitPoliticalContext.politicalEntityIdOf(target, WarRuntimeContext.registry(level));
        if (attackerEntityId == null || targetEntityId == null) return true;
        if (attackerEntityId.equals(targetEntityId)) return false;
        return PoliticalRelations.atWar(WarRuntimeContext.declarations(level), attackerEntityId, targetEntityId);
    }

    static boolean canHarmTeamNoFriendlyFire(LivingEntity attacker, LivingEntity target) {
        Team team = attacker.getTeam();
        Team team1 = target.getTeam();
        if (team == null || team1 == null) return true;
        if (team == team1) return false;
        return canHarmTeam(attacker, target);
    }

    static void handleSignificantDamage(LivingEntity attacker, LivingEntity target, double damage, ServerLevel level) {
        Team attackerTeam = attacker.getTeam();
        Team targetTeam = target.getTeam();
        if (attackerTeam == null || targetTeam == null) return;
        if (target.getHealth() - damage < target.getMaxHealth() * DAMAGE_THRESHOLD_PERCENTAGE) {
            setTeamsAsEnemies(attackerTeam, targetTeam, level);
        }
    }

    static void setTeamsAsEnemies(Team attackerTeam, Team targetTeam, ServerLevel level) {
    }
}
