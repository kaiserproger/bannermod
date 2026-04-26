package com.talhanation.bannermod.war;

import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import com.talhanation.bannermod.war.cooldown.WarCooldownRuntime;
import com.talhanation.bannermod.war.cooldown.WarCooldownSavedData;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.war.registry.WarPoliticalRegistrySavedData;
import com.talhanation.bannermod.war.runtime.DemilitarizationRuntime;
import com.talhanation.bannermod.war.runtime.DemilitarizationSavedData;
import com.talhanation.bannermod.war.runtime.OccupationRuntime;
import com.talhanation.bannermod.war.runtime.OccupationSavedData;
import com.talhanation.bannermod.war.runtime.RevoltRuntime;
import com.talhanation.bannermod.war.runtime.RevoltSavedData;
import com.talhanation.bannermod.war.runtime.SiegeStandardRuntime;
import com.talhanation.bannermod.war.runtime.SiegeStandardSavedData;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRuntime;
import com.talhanation.bannermod.war.runtime.WarAllyInviteSavedData;
import com.talhanation.bannermod.war.runtime.WarDeclarationRuntime;
import com.talhanation.bannermod.war.runtime.WarDeclarationSavedData;
import com.talhanation.bannermod.war.runtime.WarOutcomeApplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

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

    public static OccupationRuntime occupations(ServerLevel level) {
        return OccupationSavedData.get(level).runtime();
    }

    public static DemilitarizationRuntime demilitarizations(ServerLevel level) {
        return DemilitarizationSavedData.get(level).runtime();
    }

    public static RevoltRuntime revolts(ServerLevel level) {
        return RevoltSavedData.get(level).runtime();
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

    public static WarOutcomeApplier applierFor(ServerLevel level) {
        return new WarOutcomeApplier(
                declarations(level),
                sieges(level),
                audit(level),
                occupations(level),
                demilitarizations(level),
                registry(level),
                cooldowns(level),
                com.talhanation.bannermod.war.config.WarServerConfig.lostTerritoryImmunityTicks(),
                com.talhanation.bannermod.governance.BannerModTreasuryManager.get(level),
                com.talhanation.bannermod.events.ClaimEvents.recruitsClaimManager
        );
    }
}
