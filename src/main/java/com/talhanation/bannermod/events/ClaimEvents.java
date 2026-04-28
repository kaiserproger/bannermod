package com.talhanation.bannermod.events;

import com.talhanation.bannermod.army.command.CommandIntentQueueRuntime;
import com.talhanation.bannermod.governance.BannerModGovernorHeartbeat;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import com.talhanation.bannermod.settlement.BannerModSettlementOrchestrator;
import com.talhanation.bannermod.settlement.BannerModSettlementService;
import com.talhanation.bannermod.settlement.validation.BuildingInvalidationReason;
import com.talhanation.bannermod.settlement.validation.BuildingInvalidationRuntime;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.AdaptiveRuntimeBudgets;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.persistence.military.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.List;

public class ClaimEvents {

    public static MinecraftServer server;
    public static RecruitsClaimManager recruitsClaimManager;

    private static final int GOVERNOR_TICK_INTERVAL = 200;

    private static final int GOVERNOR_HEARTBEAT_BATCH_SIZE = 16;

    private static final int SETTLEMENT_REFRESH_BATCH_SIZE = 16;

    private static final int SETTLEMENT_ORCHESTRATOR_BATCH_SIZE = 16;

    private static final int GOVERNOR_STAGE_IDLE = 0;

    private static final int GOVERNOR_STAGE_HEARTBEAT = 1;

    private static final int GOVERNOR_STAGE_REFRESH = 2;

    private static final int GOVERNOR_STAGE_ORCHESTRATOR = 3;

    public static int governorCounter;

    private static int governorMaintenanceStage;

    private static int governorMaintenanceCursor;

    private static long serverTickStartedAtNanos;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        ServerLevel level = server.overworld();

        recruitsClaimManager = new RecruitsClaimManager();
        recruitsClaimManager.load(level);
        governorCounter = 0;
        governorMaintenanceStage = GOVERNOR_STAGE_IDLE;
        governorMaintenanceCursor = 0;
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        recruitsClaimManager.save(server.overworld());
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event){
        recruitsClaimManager.save(server.overworld());
    }

    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()) return;

        if(event.getEntity() instanceof ServerPlayer player){
            recruitsClaimManager.sendClaimsToPlayer(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event){
        if (event.phase == TickEvent.Phase.START) {
            serverTickStartedAtNanos = System.nanoTime();
            return;
        }
        if (event.phase != TickEvent.Phase.END) return;
        if (serverTickStartedAtNanos > 0L) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(System.nanoTime() - serverTickStartedAtNanos);
        }
        if (server == null || recruitsClaimManager == null) return;

        ServerLevel level = server.overworld();
        if(level == null || level.isClientSide()) return;

        governorCounter++;

        // Command-intent queue advancement runs every tick — the runtime already no-ops
        // cheaply when no recruit has a queued head.
        CommandIntentQueueRuntime.instance().tick(server, level.getGameTime());

        if(governorMaintenanceStage == GOVERNOR_STAGE_IDLE && governorCounter >= GOVERNOR_TICK_INTERVAL){
            governorCounter = 0;
            governorMaintenanceStage = GOVERNOR_STAGE_HEARTBEAT;
            governorMaintenanceCursor = 0;
        }

        if(governorMaintenanceStage != GOVERNOR_STAGE_IDLE){
            tickGovernorMaintenance(level);
        }

        int revalidationBudget = AdaptiveRuntimeBudgets.intBudget(
                "settlement.revalidation.batch",
                WorkersServerConfig.settlementRevalidationBatchSizePerTick(),
                1
        );
        BuildingInvalidationRuntime.tickBatch(level, revalidationBudget);
    }

    private void tickGovernorMaintenance(ServerLevel level) {
        BannerModGovernorManager governorManager = BannerModGovernorManager.get(level);
        BannerModSettlementManager settlementManager = BannerModSettlementManager.get(level);

        if (governorMaintenanceStage == GOVERNOR_STAGE_HEARTBEAT) {
            long startNanos = System.nanoTime();
            BannerModGovernorHeartbeat.BatchResult result = BannerModGovernorHeartbeat.runGovernedClaimHeartbeatBatch(
                    level,
                    recruitsClaimManager,
                    governorManager,
                    BannerModTreasuryManager.get(level),
                    governorMaintenanceCursor,
                    GOVERNOR_HEARTBEAT_BATCH_SIZE
            );
            recordGovernorMaintenanceBatch("claim_events.settlement_heartbeat.governor_batch", result, startNanos);
            advanceGovernorMaintenance(result.nextIndex(), result.completed(), GOVERNOR_STAGE_REFRESH);
            return;
        }

        if (governorMaintenanceStage == GOVERNOR_STAGE_REFRESH) {
            long startNanos = System.nanoTime();
            BannerModSettlementService.BatchResult result = BannerModSettlementService.refreshClaimsBatch(
                    level,
                    recruitsClaimManager,
                    settlementManager,
                    governorManager,
                    governorMaintenanceCursor,
                    SETTLEMENT_REFRESH_BATCH_SIZE
            );
            recordGovernorMaintenanceBatch("claim_events.settlement_heartbeat.refresh_batch", result.startIndex(), result.nextIndex(), result.totalItems(), result.completed(), startNanos);
            advanceGovernorMaintenance(result.nextIndex(), result.completed(), GOVERNOR_STAGE_ORCHESTRATOR);
            return;
        }

        if (governorMaintenanceStage == GOVERNOR_STAGE_ORCHESTRATOR) {
            long startNanos = System.nanoTime();
            BannerModSettlementOrchestrator.BatchResult result = BannerModSettlementOrchestrator.tickBatch(
                    level,
                    settlementManager,
                    governorManager,
                    governorMaintenanceCursor,
                    AdaptiveRuntimeBudgets.intBudget(
                            "settlement.orchestrator.batch",
                            SETTLEMENT_ORCHESTRATOR_BATCH_SIZE,
                            1
                    )
            );
            recordGovernorMaintenanceBatch("claim_events.settlement_heartbeat.orchestrator_batch", result.startIndex(), result.nextIndex(), result.totalItems(), result.completed(), startNanos);
            advanceGovernorMaintenance(result.nextIndex(), result.completed(), GOVERNOR_STAGE_IDLE);
        }
    }

    private static void recordGovernorMaintenanceBatch(String keyPrefix, BannerModGovernorHeartbeat.BatchResult result, long startNanos) {
        recordGovernorMaintenanceBatch(keyPrefix, result.startIndex(), result.nextIndex(), result.totalItems(), result.completed(), startNanos);
    }

    private static void recordGovernorMaintenanceBatch(String keyPrefix, int startIndex, int nextIndex, int totalItems, boolean completed, long startNanos) {
        RuntimeProfilingCounters.recordBatch(keyPrefix, Math.max(0, nextIndex - startIndex), totalItems, System.nanoTime() - startNanos, completed);
    }

    private static void advanceGovernorMaintenance(int nextIndex, boolean complete, int nextStage) {
        if (complete) {
            governorMaintenanceStage = nextStage;
            governorMaintenanceCursor = 0;
            return;
        }
        governorMaintenanceCursor = nextIndex;
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
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyBlockBreak(event.getLevel(), event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            BuildingInvalidationRuntime.enqueueByBlockChange(level, event.getPos(), BuildingInvalidationReason.BLOCK_BROKEN);
        }
    }

    @SubscribeEvent
    public void onBlockPlaceEvent(BlockEvent.EntityPlaceEvent event) {
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyBlockPlacement(event.getLevel(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            BuildingInvalidationRuntime.enqueueByBlockChange(level, event.getPos(), BuildingInvalidationReason.BLOCK_PLACED);
        }
    }

    @SubscribeEvent
    public void onFluidPlaceBlockEvent(BlockEvent.FluidPlaceBlockEvent event) {
        LevelAccessor level = event.getLevel();
        if(level.isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyFluidPlacement(level, event.getPos(), event.getLiquidPos())) {
            event.setCanceled(true);
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            BuildingInvalidationRuntime.enqueueByBlockChange(serverLevel, event.getPos(), BuildingInvalidationReason.FLUID_CHANGED);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent event) {
        if(event.getLevel().isClientSide()) return;
        Vec3 vec = event.getExplosion().getPosition();
        BlockPos pos = new BlockPos((int) vec.x, (int) vec.y, (int) vec.z);
        ChunkAccess access = server.overworld().getChunk(pos);
        RecruitsClaim claim = recruitsClaimManager.getClaim(access.getPos());

        Entity entity = event.getExplosion().getDirectSourceEntity();
        if(entity instanceof Player player && player.isCreative() && player.hasPermissions(2)){
            return;
        }

        if(claim != null && RecruitsServerConfig.ExplosionProtectionInClaims.get()){
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            BuildingInvalidationRuntime.enqueueByBlockChange(level, pos, BuildingInvalidationReason.EXPLOSION);
        }
    }
    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if(event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if(claimProtectionPolicy().shouldDenyBlockInteraction(event.getLevel(), event.getPos(), player, event.getHand())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onItemInteract(PlayerInteractEvent.RightClickItem event) {
        if(event.getLevel().isClientSide()) return;
        BlockPos targetPos = ClaimInteractionTargetResolver.resolveItemInteractionTarget(event.getEntity(), event.getHand());
        if(targetPos == null) return;
        if(claimProtectionPolicy().shouldDenyBlockInteraction(event.getLevel(), targetPos, event.getEntity(), event.getHand())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyEntityInteraction(event.getEntity(), event.getTarget())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if(event.getLevel().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyEntityInteraction(event.getEntity(), event.getTarget())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if(player.level().isClientSide()) return;
        if(claimProtectionPolicy().shouldDenyEntityAttack(player, event.getTarget())){
            event.setCanceled(true);
        }
    }

    private ClaimProtectionPolicy claimProtectionPolicy() {
        return new ClaimProtectionPolicy(recruitsClaimManager);
    }

}
