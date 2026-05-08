package com.talhanation.bannermod.events;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.settlement.runtime.ClaimProtectionEventService;
import com.talhanation.bannermod.settlement.runtime.ClaimQueueTickService;
import com.talhanation.bannermod.settlement.runtime.ClaimRuntimeService;
import com.talhanation.bannermod.settlement.runtime.SettlementHeartbeatService;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.persistence.military.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.List;

public class ClaimEvents {

    private static MinecraftServer server;
    private static RecruitsClaimManager recruitsClaimManager;

    public static MinecraftServer server() {
        return server;
    }

    public static RecruitsClaimManager claimManager() {
        return recruitsClaimManager;
    }

    public static void installRuntime(MinecraftServer currentServer, RecruitsClaimManager currentClaimManager) {
        server = currentServer;
        recruitsClaimManager = currentClaimManager;
    }

    public static void installClaimManagerForTests(RecruitsClaimManager currentClaimManager) {
        recruitsClaimManager = currentClaimManager;
    }

    private final ClaimRuntimeService runtimeService = new ClaimRuntimeService();
    private final ClaimQueueTickService queueTickService = new ClaimQueueTickService();
    private final SettlementHeartbeatService settlementHeartbeatService = new SettlementHeartbeatService();
    private final ClaimProtectionEventService protectionEventService = new ClaimProtectionEventService();

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        runtimeService.onServerStarting(event);
        settlementHeartbeatService.reset();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        runtimeService.onServerStopping(event);
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event){
        runtimeService.onWorldSave(event);
    }

    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event){
        runtimeService.onPlayerJoin(event);
    }

    @SubscribeEvent
    public void onServerTickStart(ServerTickEvent.Pre event){
        queueTickService.onServerTickStart();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event){
        queueTickService.recordServerTickDuration();
        if (server() == null || claimManager() == null) return;

        ServerLevel level = server().overworld();
        if(level == null || level.isClientSide()) return;

        queueTickService.tickCommandQueue(server(), level);
        settlementHeartbeatService.tick(level);
        queueTickService.tickBuildingInvalidationQueue(level);
    }


    public static List<AbstractRecruitEntity> getRecruitsOfTeamInRange(Level level, Player attackingPlayer, double radius, String teamId) {
        List<AbstractRecruitEntity> recruits = RecruitIndex.instance().allInBox(level, attackingPlayer.getBoundingBox().inflate(radius), true);
        if (recruits == null) {
            RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
            recruits = level.getEntitiesOfClass(AbstractRecruitEntity.class, attackingPlayer.getBoundingBox().inflate(radius));
        }
        return recruits.stream()
                .filter(recruit -> recruit.isAlive() && recruit.getTeam() != null && teamId.equals(recruit.getTeam().getName()))
                .toList();
    }
    @SubscribeEvent
    public void onBlockBreakEvent(BlockEvent.BreakEvent event) {
        protectionEventService.onBlockBreakEvent(event);
    }

    @SubscribeEvent
    public void onBlockPlaceEvent(BlockEvent.EntityPlaceEvent event) {
        protectionEventService.onBlockPlaceEvent(event);
    }

    @SubscribeEvent
    public void onFluidPlaceBlockEvent(BlockEvent.FluidPlaceBlockEvent event) {
        protectionEventService.onFluidPlaceBlockEvent(event);
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Start event) {
        protectionEventService.onExplosion(event);
    }
    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        protectionEventService.onExplosionDetonate(event);
    }
    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        protectionEventService.onBlockInteract(event);
    }

    @SubscribeEvent
    public void onItemInteract(PlayerInteractEvent.RightClickItem event) {
        protectionEventService.onItemInteract(event);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        protectionEventService.onEntityInteract(event);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        protectionEventService.onEntityInteractSpecific(event);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        protectionEventService.onAttackEntity(event);
    }

}
