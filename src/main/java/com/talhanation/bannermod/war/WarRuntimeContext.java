package com.talhanation.bannermod.war;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.settlement.economy.StrategicResourceAccountingManager;
import com.talhanation.bannermod.war.config.WarServerConfig;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import com.talhanation.bannermod.war.cooldown.WarCooldownRuntime;
import com.talhanation.bannermod.war.cooldown.WarCooldownSavedData;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.war.registry.WarPoliticalRegistrySavedData;
import com.talhanation.bannermod.war.runtime.DemilitarizationRuntime;
import com.talhanation.bannermod.war.runtime.DemilitarizationSavedData;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveRuntime;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveSavedData;
import com.talhanation.bannermod.war.runtime.OccupationRuntime;
import com.talhanation.bannermod.war.runtime.OccupationSavedData;
import com.talhanation.bannermod.war.runtime.OccupationTaxRuntime;
import com.talhanation.bannermod.war.runtime.RevoltRuntime;
import com.talhanation.bannermod.war.runtime.RevoltSavedData;
import com.talhanation.bannermod.war.runtime.SiegeStandardRuntime;
import com.talhanation.bannermod.war.runtime.SiegeStandardSavedData;
import com.talhanation.bannermod.war.runtime.TreatyPaymentRuntime;
import com.talhanation.bannermod.war.runtime.TreatyRuntime;
import com.talhanation.bannermod.war.runtime.TreatySavedData;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRuntime;
import com.talhanation.bannermod.war.runtime.WarAllyInviteSavedData;
import com.talhanation.bannermod.war.runtime.WarDeclarationRuntime;
import com.talhanation.bannermod.war.runtime.WarDeclarationSavedData;
import com.talhanation.bannermod.war.runtime.WarOutcomeApplier;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

public final class WarRuntimeContext {
    private WarRuntimeContext() {
    }

    public static ServerLevel overworld(MinecraftServer server) {
        return server == null ? null : server.overworld();
    }

    public static PoliticalRegistryRuntime registry(ServerLevel level) {
        return WarPoliticalRegistrySavedData.get(level).runtime();
    }

    public static WarDeclarationRuntime declarations(ServerLevel level) {
        return WarDeclarationSavedData.get(level).runtime();
    }

    public static SiegeStandardRuntime sieges(ServerLevel level) {
        return SiegeStandardSavedData.get(level).runtime();
    }

    /**
     * Resolve a player's political-entity UUID via leadership in the registry. Returns
     * {@code null} for unaffiliated players (no entity has them as leader / co-leader). Used
     * by COMBAT-006 to feed {@code SiegeObjectivePolicy.canAttackStandard}.
     */
    @Nullable
    public static UUID factionOf(@Nullable ServerLevel level, @Nullable Player player) {
        if (level == null || player == null) return null;
        UUID actorUuid = player.getUUID();
        for (PoliticalEntityRecord record : registry(level).all()) {
            if (actorUuid.equals(record.leaderUuid())) return record.id();
            if (record.coLeaderUuids() != null && record.coLeaderUuids().contains(actorUuid)) {
                return record.id();
            }
        }
        return null;
    }

    public static OccupationRuntime occupations(ServerLevel level) {
        return OccupationSavedData.get(level).runtime();
    }

    public static DemilitarizationRuntime demilitarizations(ServerLevel level) {
        return DemilitarizationSavedData.get(level).runtime();
    }

    public static RevoltRuntime revolts(ServerLevel level) {
        return RevoltSavedData.get(level).runtime();
    }

    public static EconomicObjectiveRuntime economicObjectives(ServerLevel level) {
        return EconomicObjectiveSavedData.get(level).runtime();
    }

    public static WarCooldownRuntime cooldowns(ServerLevel level) {
        return WarCooldownSavedData.get(level).runtime();
    }

    public static WarAuditLogSavedData audit(ServerLevel level) {
        return WarAuditLogSavedData.get(level);
    }

    public static WarAllyInviteRuntime allyInvites(ServerLevel level) {
        return WarAllyInviteSavedData.get(level).runtime();
    }

    public static TreatyRuntime treaties(ServerLevel level) {
        return TreatySavedData.get(level).runtime();
    }

    public static TreatyPaymentRuntime treatyPayments(ServerLevel level) {
        return new TreatyPaymentRuntime(
                treaties(level),
                BannerModTreasuryManager.get(level),
                StrategicResourceAccountingManager.get(level),
                ClaimEvents.claimManager(),
                audit(level)
        );
    }

    public static OccupationTaxRuntime taxRuntime(ServerLevel level) {
        return new OccupationTaxRuntime(
                occupations(level),
                BannerModTreasuryManager.get(level),
                ClaimEvents.claimManager(),
                audit(level)
        );
    }

    public static WarOutcomeApplier applierFor(ServerLevel level) {
        return new WarOutcomeApplier(
                declarations(level),
                sieges(level),
                audit(level),
                occupations(level),
                demilitarizations(level),
                registry(level),
                cooldowns(level),
                WarServerConfig.lostTerritoryImmunityTicks(),
                level,
                BannerModTreasuryManager.get(level),
                ClaimEvents.claimManager(),
                treaties(level),
                WarServerConfig.tributeTreatyIntervalTicks()
        );
    }
}
