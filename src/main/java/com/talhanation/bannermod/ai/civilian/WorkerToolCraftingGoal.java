package com.talhanation.bannermod.ai.civilian;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.AnimalFarmerEntity;
import com.talhanation.bannermod.entity.civilian.BuilderEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.LumberjackEntity;
import com.talhanation.bannermod.entity.civilian.MinerEntity;
import com.talhanation.bannermod.persistence.civilian.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class WorkerToolCraftingGoal extends Goal {
    private static final int SEARCH_RADIUS = 24;
    private static final int REQUEST_COOLDOWN_TICKS = 20 * 10;
    private static final double REACH_SQR = 2.5D * 2.5D;

    private final AbstractWorkerEntity worker;
    @Nullable
    private ToolRecipe recipe;
    @Nullable
    private BlockPos workbenchPos;
    private int lastRequestTick = -REQUEST_COOLDOWN_TICKS;

    public WorkerToolCraftingGoal(AbstractWorkerEntity worker) {
        this.worker = worker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.worker.level().isClientSide() || this.worker.hasActiveCourierTask()) {
            return false;
        }
        if (!this.worker.isOwned() || !this.worker.isWorking() && !this.worker.getCommandSenderWorld().isDay()) {
            return false;
        }
        if (!this.worker.shouldWork() || this.worker.needsToSleep()) {
            return false;
        }
        this.recipe = nextMissingRecipe();
        if (this.recipe == null) {
            return false;
        }
        this.workbenchPos = findNearbyWorkbench();
        if (this.workbenchPos == null) {
            return false;
        }
        if (canCraftNow(this.recipe)) {
            return true;
        }
        maybeRequestMaterials(this.recipe);
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.recipe != null
                && this.workbenchPos != null
                && this.worker.shouldWork()
                && !this.worker.needsToSleep()
                && canCraftNow(this.recipe);
    }

    @Override
    public void stop() {
        super.stop();
        this.recipe = null;
        this.workbenchPos = null;
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.recipe == null || this.workbenchPos == null) {
            return;
        }
        this.worker.getLookControl().setLookAt(this.workbenchPos.getCenter());
        if (this.worker.distanceToSqr(this.workbenchPos.getX() + 0.5D, this.workbenchPos.getY() + 0.5D, this.workbenchPos.getZ() + 0.5D) > REACH_SQR) {
            this.worker.getNavigation().moveTo(this.workbenchPos.getX() + 0.5D, this.workbenchPos.getY(), this.workbenchPos.getZ() + 0.5D, 0.9D);
            return;
        }
        this.worker.getNavigation().stop();
        if (!canCraftNow(this.recipe)) {
            maybeRequestMaterials(this.recipe);
            this.stop();
            return;
        }
        consumeInputs(this.recipe);
        this.worker.addItem(this.recipe.output().copy());
        this.worker.onWorkerItemAdded(this.recipe.output());
        this.worker.switchMainHandItem(stack -> stack.is(this.recipe.output().getItem()));
        this.worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        this.worker.clearWorkStatus();
        this.stop();
    }

    @Nullable
    private ToolRecipe nextMissingRecipe() {
        if (this.worker instanceof FarmerEntity && this.worker.getMatchingItem(stack -> stack.getItem() instanceof HoeItem) == null) {
            return new ToolRecipe(new ItemStack(Items.STONE_HOE), 2);
        }
        if (this.worker instanceof LumberjackEntity && this.worker.getMatchingItem(stack -> stack.getItem() instanceof AxeItem) == null) {
            return new ToolRecipe(new ItemStack(Items.STONE_AXE), 3);
        }
        if (this.worker instanceof AnimalFarmerEntity && this.worker.getMatchingItem(stack -> stack.getItem() instanceof AxeItem) == null) {
            return new ToolRecipe(new ItemStack(Items.STONE_AXE), 3);
        }
        if (this.worker instanceof MinerEntity) {
            if (this.worker.getMatchingItem(stack -> stack.getItem() instanceof PickaxeItem) == null) {
                return new ToolRecipe(new ItemStack(Items.STONE_PICKAXE), 3);
            }
            if (this.worker.getMatchingItem(stack -> stack.getItem() instanceof ShovelItem) == null) {
                return new ToolRecipe(new ItemStack(Items.STONE_SHOVEL), 1);
            }
        }
        if (this.worker instanceof BuilderEntity) {
            if (this.worker.getMatchingItem(stack -> stack.getItem() instanceof AxeItem) == null) {
                return new ToolRecipe(new ItemStack(Items.STONE_AXE), 3);
            }
            if (this.worker.getMatchingItem(stack -> stack.getItem() instanceof PickaxeItem) == null) {
                return new ToolRecipe(new ItemStack(Items.STONE_PICKAXE), 3);
            }
            if (this.worker.getMatchingItem(stack -> stack.getItem() instanceof ShovelItem) == null) {
                return new ToolRecipe(new ItemStack(Items.STONE_SHOVEL), 1);
            }
        }
        return null;
    }

    private boolean canCraftNow(ToolRecipe recipe) {
        return countItem(Items.COBBLESTONE) >= recipe.cobblestoneCost() && availableStickCount() >= 2;
    }

    private void maybeRequestMaterials(ToolRecipe recipe) {
        if (this.worker.tickCount - this.lastRequestTick < REQUEST_COOLDOWN_TICKS) {
            return;
        }
        this.lastRequestTick = this.worker.tickCount;
        int missingCobblestone = Math.max(0, recipe.cobblestoneCost() - countItem(Items.COBBLESTONE));
        if (missingCobblestone > 0) {
            this.worker.requestRequiredItem(new NeededItem(stack -> stack.is(Items.COBBLESTONE), missingCobblestone, true),
                    "worker_crafting_missing_cobblestone",
                    Component.literal(this.worker.getName().getString() + ": I need cobblestone to craft a tool."));
        }
        if (availableStickCount() >= 2) {
            return;
        }
        if (countTagged(ItemTags.PLANKS) >= 2 || countTagged(ItemTags.LOGS) >= 1) {
            return;
        }
        this.worker.requestRequiredItem(new NeededItem(stack -> stack.is(ItemTags.PLANKS) || stack.is(Items.STICK), 2, true),
                "worker_crafting_missing_wood",
                Component.literal(this.worker.getName().getString() + ": I need wood to craft a tool."));
    }

    private void consumeInputs(ToolRecipe recipe) {
        removeItems(Items.COBBLESTONE, recipe.cobblestoneCost());
        ensureTwoSticks();
        removeItems(Items.STICK, 2);
    }

    private void ensureTwoSticks() {
        if (countItem(Items.STICK) >= 2) {
            return;
        }
        if (countTagged(ItemTags.PLANKS) < 2) {
            convertOneLogToPlanks();
        }
        if (countItem(Items.STICK) < 2 && countTagged(ItemTags.PLANKS) >= 2) {
            removeTaggedItems(ItemTags.PLANKS, 2);
            this.worker.addItem(new ItemStack(Items.STICK, 4));
            this.worker.onWorkerItemAdded(new ItemStack(Items.STICK, 4));
        }
    }

    private void convertOneLogToPlanks() {
        net.minecraft.world.SimpleContainer inventory = this.worker.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ItemTags.LOGS)) {
                continue;
            }
            Item plankItem = plankOutputForLog(stack);
            stack.shrink(1);
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
            ItemStack planks = new ItemStack(plankItem, 4);
            this.worker.addItem(planks.copy());
            this.worker.onWorkerItemAdded(planks);
            return;
        }
    }

    private Item plankOutputForLog(ItemStack logStack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(logStack.getItem());
        if (id == null) {
            return Items.OAK_PLANKS;
        }
        String path = id.getPath();
        String plankPath = path
                .replace("_log", "_planks")
                .replace("_wood", "_planks")
                .replace("stem", "planks")
                .replace("hyphae", "planks");
        Item resolved = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), plankPath));
        return resolved == Items.AIR ? Items.OAK_PLANKS : resolved;
    }

    @Nullable
    private BlockPos findNearbyWorkbench() {
        BlockPos center = this.worker.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS), center.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
            if (!this.worker.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                continue;
            }
            double dist = pos.distSqr(center);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    private int availableStickCount() {
        int sticks = countItem(Items.STICK);
        int planks = countTagged(ItemTags.PLANKS);
        int logs = countTagged(ItemTags.LOGS);
        return sticks + (planks / 2) * 4 + logs * 8;
    }

    private int countItem(Item item) {
        return this.worker.countMatchingItems(stack -> stack.is(item));
    }

    private int countTagged(net.minecraft.tags.TagKey<Item> tag) {
        return this.worker.countMatchingItems(stack -> stack.is(tag));
    }

    private void removeItems(Item item, int count) {
        if (count <= 0) {
            return;
        }
        net.minecraft.world.SimpleContainer inventory = this.worker.getInventory();
        int remaining = count;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private void removeTaggedItems(net.minecraft.tags.TagKey<Item> tag, int count) {
        if (count <= 0) {
            return;
        }
        net.minecraft.world.SimpleContainer inventory = this.worker.getInventory();
        int remaining = count;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(tag)) {
                continue;
            }
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private record ToolRecipe(ItemStack output, int cobblestoneCost) {
    }
}
