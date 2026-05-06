package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class ClaimRuntimeService {
    public void onServerStarting(ServerStartingEvent event) {
        ServerLevel level = event.getServer().overworld();

        RecruitsClaimManager claimManager = new RecruitsClaimManager();
        claimManager.load(level);
        ClaimEvents.installRuntime(event.getServer(), claimManager);
    }

    public void onServerStopping(ServerStoppingEvent event) {
        ClaimEvents.claimManager().save(ClaimEvents.server().overworld());
    }

    public void onWorldSave(LevelEvent.Save event) {
        ClaimEvents.claimManager().save(ClaimEvents.server().overworld());
    }

    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide()) return;

        if(event.getEntity() instanceof ServerPlayer player){
            ClaimEvents.claimManager().sendClaimsToPlayer(player);
        }
    }
}
