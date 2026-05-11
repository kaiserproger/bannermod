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
        saveClaims();
    }

    public void onWorldSave(LevelEvent.Save event) {
        // LevelEvent.Save fires once per dimension; only persist via the overworld save so the
        // SavedData write happens once and never on a half-installed runtime (server() or
        // claimManager() can be null during early init / shutdown races).
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        saveClaims();
    }

    private void saveClaims() {
        var server = ClaimEvents.server();
        var manager = ClaimEvents.claimManager();
        if (server == null || manager == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        try {
            manager.save(overworld);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger(ClaimRuntimeService.class)
                    .error("Failed to persist RecruitsClaimManager during world save", t);
        }
    }

    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide()) return;

        if(event.getEntity() instanceof ServerPlayer player){
            ClaimEvents.claimManager().sendClaimsToPlayer(player);
        }
    }
}
