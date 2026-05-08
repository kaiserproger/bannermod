package com.talhanation.bannermod.events;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.ai.civilian.animals.WorkerAnimalGoalInjector;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.settlement.civilian.WorkerSettlementSpawnRules;
import com.talhanation.bannermod.settlement.civilian.runtime.WorkerMarketAreaAccess;
import com.talhanation.bannermod.settlement.civilian.runtime.WorkerSettlementEventService;
import com.talhanation.bannermod.settlement.civilian.runtime.WorkerTradeBootstrap;
import com.talhanation.bannermod.network.messages.civilian.MessageToClientUpdateConfig;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.bootstrap.WorkersRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;

public class WorkersVillagerEvents {
    @SubscribeEvent
    public void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide()) return;

        if(event.getEntity() instanceof ServerPlayer player){
                WorkersRuntime.channel().send(BannerModPacketDistributor.PLAYER.with(() -> player),
                        new MessageToClientUpdateConfig(WorkersServerConfig.shouldWorkAreaOnlyBeInFactionClaim()));
        }
    }
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        WorkerSettlementEventService.resetRuntimeState();
        WorkerTradeBootstrap.registerTrades();
    }

    @SubscribeEvent
    public void onVillagerJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)) {
            return;
        }

        WorkerSettlementEventService.recordVillagerJoin(villager);
    }

    @SubscribeEvent
    public void onVillagerLivingUpdate(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager villager) || villager.level().isClientSide() || !(villager.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (villager.isBaby()) {
            return;
        }

        WorkerSettlementEventService.handleVillagerAdultTick(serverLevel, villager);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() == null
                ? null
                : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
        if (level == null || level.getGameTime() % 200L != 0L) {
            return;
        }

        WorkerSettlementEventService.runClaimWorkerGrowthPass(level);
        WorkerSettlementEventService.runCitizenBirthPass(level);
    }

    @SubscribeEvent
    public void onAnimalJoinWorld(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        WorkerAnimalGoalInjector.injectTemptGoal(entity);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handleBlockInteraction(event);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        handleBlockInteraction(event);
    }

    private void handleBlockInteraction(PlayerInteractEvent event) {
        if (event.getLevel() == null || event.getEntity() == null) return;

        if(disableInteractionInMarketArea(event.getEntity(), event.getLevel(), event.getPos())){
            if (event instanceof ICancellableEvent cancellableEvent) {
                cancellableEvent.setCanceled(true);
            }
        }
    }

    public static boolean disableInteractionInMarketArea(Player player, Level level, BlockPos pos) {
        return WorkerMarketAreaAccess.shouldBlockInteraction(player, level, pos);
    }

    public static AbstractWorkerEntity attemptBirthWorkerSpawn(ServerLevel level, Villager villager) {
        return WorkerSettlementEventService.attemptBirthWorkerSpawn(level, villager);
    }

    public static AbstractWorkerEntity attemptSettlementWorkerSpawn(ServerLevel level, Villager villager) {
        return WorkerSettlementEventService.attemptSettlementWorkerSpawn(level, villager);
    }

    public static void runClaimWorkerGrowthPass(ServerLevel level) {
        WorkerSettlementEventService.runClaimWorkerGrowthPass(level);
    }

    public static AbstractWorkerEntity attemptClaimWorkerGrowth(ServerLevel level,
                                                                RecruitsClaim claim,
                                                                com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding.Binding binding,
                                                                long gameTime) {
        return WorkerSettlementEventService.attemptClaimWorkerGrowth(level, claim, binding, gameTime, WorkersServerConfig.claimWorkerGrowthConfig());
    }

    public static AbstractWorkerEntity attemptClaimWorkerGrowth(ServerLevel level,
                                                                RecruitsClaim claim,
                                                                com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding.Binding binding,
                                                                long gameTime,
                                                                WorkerSettlementSpawnRules.ClaimGrowthConfig config) {
        return WorkerSettlementEventService.attemptClaimWorkerGrowth(level, claim, binding, gameTime, config);
    }

    public static AbstractWorkerEntity attemptClaimWorkerGrowth(ServerLevel level,
                                                                RecruitsClaim claim,
                                                                String settlementFactionId,
                                                                long gameTime) {
        return WorkerSettlementEventService.attemptClaimWorkerGrowth(level, claim, settlementFactionId, gameTime);
    }

    public static AbstractWorkerEntity attemptClaimWorkerGrowth(ServerLevel level,
                                                                RecruitsClaim claim,
                                                                String settlementFactionId,
                                                                long gameTime,
                                                                WorkerSettlementSpawnRules.ClaimGrowthConfig config) {
        return WorkerSettlementEventService.attemptClaimWorkerGrowth(level, claim, settlementFactionId, gameTime, config);
    }
}
