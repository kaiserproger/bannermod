package com.talhanation.bannermod.ai.civilian;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.FishermanEntity;
import com.talhanation.bannermod.entity.civilian.FishingBobberEntity;
import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.persistence.civilian.BuildBlockParse;
import com.talhanation.bannermod.persistence.civilian.NeededItem;
import com.talhanation.bannermod.settlement.BannerModSettlementOrchestrator;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsItemFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.SpecialPlantable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Worker goal that consumes {@link SettlementWorkOrder} claims from the per-level
 * {@link SettlementWorkOrderRuntime} and executes them in-world.
 *
 * <p>Handles in-world block actions ({@code HARVEST_CROP}, {@code BUILD_BLOCK}, ...) and the
 * transport family ({@code FETCH_INPUT}, {@code HAUL_RESOURCE}). Transport orders move items
 * between source and destination storage anchors using {@link TransportContainerExchange};
 * non-transport orders resolve their target state from the owning {@link BuildArea} or
 * vanilla block state and never guess.</p>
 *
 * <p>Priority: this goal is registered alongside the legacy {@code *WorkGoal} at priority 0.
 * Its {@link #canUse()} is only true while the runtime holds a claim for the worker, so it
 * preempts the legacy goal only when settlement demand is actively driving this worker.</p>
 */
public final class SettlementOrderWorkGoal extends Goal {

    private static final double REACH_THRESHOLD = 2.6D;
    private static final double STORAGE_LOOKUP_RADIUS = 6.0D;
    private static final int MAX_PATH_TICKS = 20 * 30;
    private static final int MAX_ACTION_TICKS = 20 * 15;

    private final AbstractWorkerEntity worker;
    private SettlementWorkOrder activeOrder;
    private int pathTicks;
    private int actionTicks;
    private boolean attemptedExecution;
    @Nullable
    private TransportPhase transportPhase;
    private int transportCarried;

    private enum TransportPhase {
        MOVE_TO_SOURCE,
        WITHDRAW,
        MOVE_TO_DESTINATION,
        DEPOSIT
    }

    public SettlementOrderWorkGoal(AbstractWorkerEntity worker) {
        this.worker = worker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.getCommandSenderWorld().isClientSide()) {
            return false;
        }
        if (!(worker.getCommandSenderWorld() instanceof ServerLevel level)) {
            return false;
        }
        SettlementWorkOrderRuntime runtime = BannerModSettlementOrchestrator.workOrderRuntime(level);
        if (runtime == null) {
            return false;
        }
        Optional<SettlementWorkOrder> claim = runtime.currentClaim(worker.getUUID());
        return claim.isPresent() && isExecutableOrder(claim.get());
    }

    @Override
    public boolean canContinueToUse() {
        if (activeOrder == null) {
            return false;
        }
        if (!(worker.getCommandSenderWorld() instanceof ServerLevel level)) {
            return false;
        }
        SettlementWorkOrderRuntime runtime = BannerModSettlementOrchestrator.workOrderRuntime(level);
        if (runtime == null) {
            return false;
        }
        return runtime.find(activeOrder.orderUuid()).isPresent();
    }

    @Override
    public void start() {
        super.start();
        if (!(worker.getCommandSenderWorld() instanceof ServerLevel level)) {
            return;
        }
        SettlementWorkOrderRuntime runtime = BannerModSettlementOrchestrator.workOrderRuntime(level);
        if (runtime == null) {
            return;
        }
        this.activeOrder = runtime.currentClaim(worker.getUUID()).orElse(null);
        this.pathTicks = 0;
        this.actionTicks = 0;
        this.attemptedExecution = false;
        this.transportCarried = 0;
        this.transportPhase = isTransportType(activeOrder)
                ? (activeOrder.sourcePos() != null ? TransportPhase.MOVE_TO_SOURCE : TransportPhase.MOVE_TO_DESTINATION)
                : null;
    }

    @Override
    public void stop() {
        super.stop();
        this.activeOrder = null;
        this.pathTicks = 0;
        this.actionTicks = 0;
        this.attemptedExecution = false;
        this.transportPhase = null;
        this.transportCarried = 0;
        worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        if (activeOrder == null) {
            return;
        }
        if (!(worker.getCommandSenderWorld() instanceof ServerLevel level)) {
            return;
        }
        SettlementWorkOrderRuntime runtime = BannerModSettlementOrchestrator.workOrderRuntime(level);
        if (runtime == null) {
            return;
        }

        if (transportPhase != null) {
            tickTransport(level, runtime);
            return;
        }

        BlockPos target = activeOrder.targetPos();
        if (target == null) {
            completeActiveOrder(runtime, level);
            this.activeOrder = null;
            return;
        }

        double distance = worker.getHorizontalDistanceTo(target.getCenter());
        if (distance > REACH_THRESHOLD) {
            pathTicks++;
            if (pathTicks > MAX_PATH_TICKS) {
                runtime.release(activeOrder.orderUuid());
                this.activeOrder = null;
                return;
            }
            worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9D);
            worker.getLookControl().setLookAt(target.getCenter());
            return;
        }

        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(target.getCenter());
        actionTicks++;
        if (actionTicks > MAX_ACTION_TICKS) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }

        executeAt(level, target, runtime);
    }

    private void tickTransport(ServerLevel level, SettlementWorkOrderRuntime runtime) {
        switch (transportPhase) {
            case MOVE_TO_SOURCE -> {
                BlockPos src = activeOrder.sourcePos();
                if (src == null) {
                    transportPhase = TransportPhase.MOVE_TO_DESTINATION;
                    pathTicks = 0;
                    return;
                }
                if (!stepToward(src)) {
                    runtime.release(activeOrder.orderUuid());
                    this.activeOrder = null;
                    return;
                }
                if (worker.getHorizontalDistanceTo(src.getCenter()) <= REACH_THRESHOLD) {
                    worker.getNavigation().stop();
                    transportPhase = TransportPhase.WITHDRAW;
                    pathTicks = 0;
                    actionTicks = 0;
                }
            }
            case WITHDRAW -> {
                int withdrawn = withdrawAtSource(level);
                transportCarried += withdrawn;
                transportPhase = TransportPhase.MOVE_TO_DESTINATION;
                pathTicks = 0;
                actionTicks = 0;
            }
            case MOVE_TO_DESTINATION -> {
                BlockPos dst = activeOrder.destinationPos();
                if (dst == null) {
                    completeActiveOrder(runtime, level);
                    this.activeOrder = null;
                    return;
                }
                if (!stepToward(dst)) {
                    runtime.release(activeOrder.orderUuid());
                    this.activeOrder = null;
                    return;
                }
                if (worker.getHorizontalDistanceTo(dst.getCenter()) <= REACH_THRESHOLD) {
                    worker.getNavigation().stop();
                    transportPhase = TransportPhase.DEPOSIT;
                    pathTicks = 0;
                    actionTicks = 0;
                }
            }
            case DEPOSIT -> {
                depositAtDestination(level);
                completeActiveOrder(runtime, level);
                this.activeOrder = null;
            }
        }
    }

    private boolean stepToward(BlockPos target) {
        if (worker.getHorizontalDistanceTo(target.getCenter()) <= REACH_THRESHOLD) {
            return true;
        }
        pathTicks++;
        if (pathTicks > MAX_PATH_TICKS) {
            return false;
        }
        worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9D);
        worker.getLookControl().setLookAt(target.getCenter());
        return true;
    }

    private int withdrawAtSource(ServerLevel level) {
        BlockPos src = activeOrder.sourcePos();
        if (src == null) {
            return 0;
        }
        int requestedTotal = Math.max(activeOrder.itemCount(), 1);
        int budget = Math.max(0, requestedTotal - transportCarried);
        if (budget == 0) {
            return 0;
        }
        BannerModLogisticsItemFilter filter = TransportContainerExchange
                .filterFromResourceHint(activeOrder.resourceHintId());
        int withdrawn = 0;
        for (Container container : containersAt(level, src)) {
            if (withdrawn >= budget) {
                break;
            }
            withdrawn += TransportContainerExchange.withdrawInto(
                    container, worker.getInventory(), filter, budget - withdrawn);
        }
        return withdrawn;
    }

    private void depositAtDestination(ServerLevel level) {
        BlockPos dst = activeOrder.destinationPos();
        if (dst == null) {
            return;
        }
        BannerModLogisticsItemFilter filter = TransportContainerExchange
                .filterFromResourceHint(activeOrder.resourceHintId());
        for (Container container : containersAt(level, dst)) {
            int moved = TransportContainerExchange.depositInto(container, worker.getInventory(), filter);
            if (moved == 0) {
                continue;
            }
            // Continue across containers in case the first slot ran out of space mid-stack.
        }
    }

    private List<Container> containersAt(ServerLevel level, BlockPos pos) {
        List<Container> out = new ArrayList<>();
        StorageArea area = nearestStorageArea(level, pos);
        if (area != null) {
            area.scanStorageBlocks();
            out.addAll(area.storageMap.values());
            if (!out.isEmpty()) {
                return out;
            }
        }
        Container direct = directContainer(level, pos);
        if (direct != null) {
            out.add(direct);
        }
        return out;
    }

    @Nullable
    private StorageArea nearestStorageArea(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        List<StorageArea> nearby = WorkAreaIndex.instance().queryInRange(level, center, STORAGE_LOOKUP_RADIUS, StorageArea.class);
        return nearby.stream()
                .min(Comparator.comparingDouble(area -> area.distanceToSqr(center)))
                .orElse(null);
    }

    @Nullable
    private Container directContainer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, level, pos, false);
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container container ? container : null;
    }

    private void executeAt(ServerLevel level, BlockPos target, SettlementWorkOrderRuntime runtime) {
        SettlementWorkOrderType type = activeOrder.type();
        switch (type) {
            case HARVEST_CROP,
                  BREAK_BLOCK,
                  MINE_BLOCK,
                  FELL_TREE -> {
                BlockState state = level.getBlockState(target);
                if (state.isAir() || AbstractWorkerEntity.isPosBroken(target, level, true)) {
                    completeActiveOrder(runtime, level);
                    this.activeOrder = null;
                    return;
                }
                if (!attemptedExecution) {
                    attemptedExecution = true;
                }
                worker.mineBlock(target);
                worker.swing(InteractionHand.MAIN_HAND);
            }
            case TILL_SOIL -> executeTillSoil(level, target, runtime);
            case PLANT_CROP -> executePlantCrop(level, target, runtime);
            case REPLANT_TREE -> executeReplantTree(level, target, runtime);
            case FISH -> executeFish(target, runtime, level);
            case BUILD_BLOCK -> executeBuildBlock(level, target, runtime);
            default -> {
                // Placement-style or specialist types are left to legacy profession goals.
                runtime.release(activeOrder.orderUuid());
                this.activeOrder = null;
            }
        }
    }

    private void executeFish(BlockPos target, SettlementWorkOrderRuntime runtime, ServerLevel level) {
        if (!(worker instanceof FishermanEntity fisherman)) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }
        if(!fisherman.hasFreeInvSlot()){
            fisherman.reportBlockedReason("fisherman_inventory_full", Component.literal(fisherman.getName().getString() + ": My inventory is full."));
            fisherman.forcedDeposit = true;
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }

        boolean hasFishingRod = fisherman.getInventory().hasAnyMatching(itemStack -> itemStack.getItem() instanceof FishingRodItem);
        if(!hasFishingRod){
            fisherman.requestRequiredItem(new NeededItem(stack -> stack.getItem() instanceof FishingRodItem, 1, true),
                    "fisherman_missing_rod",
                    Component.literal(fisherman.getName().getString() + ": I need a fishing rod to continue."));
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }

        fisherman.clearWorkStatus();
        fisherman.switchMainHandItem(itemStack -> itemStack.getItem() instanceof FishingRodItem);
        fisherman.swing(InteractionHand.MAIN_HAND);
        fisherman.playSound(SoundEvents.FISHING_BOBBER_THROW, 1, 1);
        FishingBobberEntity fishingBobber = fisherman.throwFishingHook(target.getCenter());
        fisherman.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 1, 1);
        fisherman.swing(InteractionHand.MAIN_HAND);
        fisherman.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1, 1);
        fisherman.spawnFishingLoot(fishingBobber);
        fishingBobber.discard();
        fisherman.farmedItems++;
        if(fisherman.tickCount % 2 == 0) fisherman.damageMainHandItem();
        completeActiveOrder(runtime, level);
        this.activeOrder = null;
    }

    private static boolean isExecutableOrder(SettlementWorkOrder order) {
        if (order == null) {
            return false;
        }
        if (isTransportType(order)) {
            return order.sourcePos() != null || order.destinationPos() != null;
        }
        if (order.targetPos() == null) {
            return false;
        }
        return switch (order.type()) {
            case HARVEST_CROP, BREAK_BLOCK, MINE_BLOCK, FELL_TREE, TILL_SOIL, PLANT_CROP, REPLANT_TREE, BUILD_BLOCK -> true;
            default -> false;
        };
    }

    private static boolean isTransportType(@Nullable SettlementWorkOrder order) {
        if (order == null) {
            return false;
        }
        return order.type() == SettlementWorkOrderType.FETCH_INPUT
                || order.type() == SettlementWorkOrderType.HAUL_RESOURCE;
    }

    private void executeBuildBlock(ServerLevel level, BlockPos target, SettlementWorkOrderRuntime runtime) {
        Entity buildingEntity = level.getEntity(activeOrder.buildingUuid());
        if (!(buildingEntity instanceof BuildArea buildArea) || !buildArea.isAlive()) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }

        BlockState buildingState = buildArea.getStateFromPos(target);
        if (buildingState == null) {
            completeActiveOrder(runtime, level);
            this.activeOrder = null;
            return;
        }

        BlockState levelState = level.getBlockState(target);
        if (buildArea.statesMatch(levelState, buildingState)) {
            buildArea.removeBuildBlockToPlace(target);
            buildArea.removeMultiBlockToPlace(target);
            completeActiveOrder(runtime, level);
            this.activeOrder = null;
            return;
        }
        if (!levelState.isAir() && !BuildArea.canDirectlyReplace(levelState, buildingState)) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }

        BuildBlockParse blockParse = BuildBlockParse.parseBlock(buildingState.getBlock());
        ItemStack buildingItem = worker.getMatchingItem(itemStack -> itemStack.is(blockParse.getItem()));
        if (buildingItem == null) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }
        if (!worker.getMainHandItem().is(buildingItem.getItem())) {
            worker.switchMainHandItem(itemStack -> itemStack.is(buildingItem.getItem()));
        }
        if (blockParse.wasParsed() && buildingItem.getItem() instanceof BlockItem blockItem) {
            buildingState = blockItem.getBlock().defaultBlockState();
        }

        BlockState secondaryState = buildArea.findPairedMultiBlockState(target);
        BlockPos secondaryPos = buildArea.findPairedMultiBlockPos(target);
        if (secondaryState != null && secondaryPos != null) {
            level.setBlock(target, buildingState, Block.UPDATE_CLIENTS);
            level.setBlock(secondaryPos, secondaryState, Block.UPDATE_ALL);
            level.blockUpdated(target, buildingState.getBlock());
            buildArea.removeMultiBlockToPlace(secondaryPos);
        } else {
            level.setBlockAndUpdate(target, buildingState);
            buildArea.removeMultiBlockToPlace(target);
        }

        level.playSound(null, target.getX(), target.getY(), target.getZ(), buildingState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        worker.swing(InteractionHand.MAIN_HAND);
        buildingItem.shrink(1);
        buildArea.removeBuildBlockToPlace(target);
        completeActiveOrder(runtime, level);
        this.activeOrder = null;
    }

    private void executeTillSoil(ServerLevel level, BlockPos target, SettlementWorkOrderRuntime runtime) {
        BlockState state = level.getBlockState(target);
        if (state.getBlock() instanceof FarmBlock) {
            completeActiveOrder(runtime, level);
            this.activeOrder = null;
            return;
        }
        if (!(worker.getMainHandItem().getItem() instanceof HoeItem)) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }
        level.setBlock(target, Blocks.FARMLAND.defaultBlockState(), 3);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        worker.damageMainHandItem();
        worker.swing(InteractionHand.MAIN_HAND);
        completeActiveOrder(runtime, level);
        this.activeOrder = null;
    }

    private void executePlantCrop(ServerLevel level, BlockPos target, SettlementWorkOrderRuntime runtime) {
        BlockState state = level.getBlockState(target);
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) {
            completeActiveOrder(runtime, level);
            this.activeOrder = null;
            return;
        }
        ItemStack seedStack = worker.getMatchingItem(this::isCropSeed);
        if (seedStack == null) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }
        if (seedStack.getItem() instanceof BlockItem blockItem) {
            level.setBlockAndUpdate(target, blockItem.getBlock().defaultBlockState());
        } else if (seedStack.getItem() instanceof SpecialPlantable plantable && plantable.canPlacePlantAtPosition(seedStack, level, target, Direction.UP)) {
            plantable.spawnPlantAtPosition(seedStack, level, target, Direction.UP);
        } else {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        seedStack.shrink(1);
        worker.swing(InteractionHand.MAIN_HAND);
        completeActiveOrder(runtime, level);
        this.activeOrder = null;
    }

    private void executeReplantTree(ServerLevel level, BlockPos target, SettlementWorkOrderRuntime runtime) {
        if (!level.getBlockState(target).isAir()) {
            completeActiveOrder(runtime, level);
            this.activeOrder = null;
            return;
        }
        ItemStack saplingStack = worker.getMatchingItem(itemStack -> itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock);
        if (saplingStack == null || !(saplingStack.getItem() instanceof BlockItem blockItem)) {
            runtime.release(activeOrder.orderUuid());
            this.activeOrder = null;
            return;
        }
        level.setBlockAndUpdate(target, blockItem.getBlock().defaultBlockState());
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        saplingStack.shrink(1);
        worker.swing(InteractionHand.MAIN_HAND);
        completeActiveOrder(runtime, level);
        this.activeOrder = null;
    }

    private void completeActiveOrder(SettlementWorkOrderRuntime runtime, ServerLevel level) {
        runtime.complete(activeOrder.orderUuid(), level.getGameTime());
    }

    private boolean isCropSeed(ItemStack itemStack) {
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().getBlock() instanceof CropBlock) {
            return true;
        }
        return itemStack.getItem() instanceof SpecialPlantable;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
