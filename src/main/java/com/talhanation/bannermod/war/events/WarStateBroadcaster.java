package com.talhanation.bannermod.war.events;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.network.messages.war.MessageToClientUpdateWarState;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.config.WarServerConfig;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.runtime.BattleWindowSchedule;
import com.talhanation.bannermod.war.runtime.SiegeStandardRecord;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

/**
 * Server-authoritative broadcaster for the warfare-RP runtime snapshot.
 *
 * <p>Polls the three war runtimes once per second; if their content hash differs from the
 * last broadcast, pushes a fresh {@link MessageToClientUpdateWarState} to every online player.
 * On player login, sends the current snapshot immediately so the new client has a populated
 * {@link WarClientState} before any HUD render runs.</p>
 *
 * <p>Polling instead of dirty-listener chaining keeps the existing {@code SavedData::setDirty}
 * wiring intact (each runtime's listener is owned by its SavedData).</p>
 */
public class WarStateBroadcaster {
    private static final int TICK_INTERVAL = 20;

    private int counter = 0;
    private int lastHash = 0;
    private boolean primed = false;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        counter = 0;
        lastHash = 0;
        primed = false;
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        counter = 0;
        lastHash = 0;
        primed = false;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel().getServer().overworld();
        if (level == null) return;
        sendSnapshotTo(player, level);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;
        if (++counter < TICK_INTERVAL) return;
        counter = 0;

        Collection<PoliticalEntityRecord> entities = WarRuntimeContext.registry(level).all();
        Collection<WarDeclarationRecord> wars = WarRuntimeContext.declarations(level).all();
        Collection<SiegeStandardRecord> sieges = WarRuntimeContext.sieges(level).all();
        BattleWindowSchedule schedule = resolveSchedule();
        Collection<WarAllyInviteRecord> invites = WarRuntimeContext.allyInvites(level).all();
        int hash = stateHash(entities, wars, sieges, schedule, invites);
        if (primed && hash == lastHash) return;
        lastHash = hash;
        primed = true;

        CompoundTag payload = WarClientState.encode(entities, wars, sieges, schedule, invites);
        BannerModMain.SIMPLE_CHANNEL.send(PacketDistributor.ALL.noArg(),
                new MessageToClientUpdateWarState(payload));
    }

    private static void sendSnapshotTo(ServerPlayer player, ServerLevel level) {
        CompoundTag payload = WarClientState.encode(
                WarRuntimeContext.registry(level).all(),
                WarRuntimeContext.declarations(level).all(),
                WarRuntimeContext.sieges(level).all(),
                resolveSchedule(),
                WarRuntimeContext.allyInvites(level).all()
        );
        BannerModMain.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MessageToClientUpdateWarState(payload));
    }

    private static BattleWindowSchedule resolveSchedule() {
        try {
            return WarServerConfig.resolveSchedule();
        } catch (IllegalStateException ex) {
            return BattleWindowSchedule.defaultSchedule();
        }
    }

    private static int stateHash(Collection<PoliticalEntityRecord> entities,
                                 Collection<WarDeclarationRecord> wars,
                                 Collection<SiegeStandardRecord> sieges,
                                 BattleWindowSchedule schedule,
                                 Collection<WarAllyInviteRecord> invites) {
        int result = 1;
        for (PoliticalEntityRecord entity : entities) {
            result = 31 * result + entity.toTag().hashCode();
        }
        for (WarDeclarationRecord war : wars) {
            result = 31 * result + war.toTag().hashCode();
        }
        for (SiegeStandardRecord siege : sieges) {
            result = 31 * result + siege.toTag().hashCode();
        }
        if (schedule != null) {
            result = 31 * result + schedule.toListTag().hashCode();
        }
        for (WarAllyInviteRecord invite : invites) {
            result = 31 * result + invite.toTag().hashCode();
        }
        return result;
    }
}
