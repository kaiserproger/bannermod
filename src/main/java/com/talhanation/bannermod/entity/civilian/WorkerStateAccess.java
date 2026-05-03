package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.society.NpcSocietyAccess;
import com.talhanation.bannermod.society.NpcSocietyIntentRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

final class WorkerStateAccess {
    private static final int WORKER_STORAGE_START_SLOT = 6;

    private final AbstractWorkerEntity worker;

    WorkerStateAccess(AbstractWorkerEntity worker) {
        this.worker = worker;
    }

    boolean isWorking() {
        return this.worker.getFollowState() == 6;
    }

    boolean needsToSleep() {
        if (this.worker.getCurrentWorkArea() == null) {
            return !this.worker.getCommandSenderWorld().isDay();
        }
        if (currentIntentIsRestLike()) {
            return true;
        }
        return !this.worker.getCommandSenderWorld().isDay();
    }

    double getDistanceToOwner() {
        return this.worker.getOwner() != null ? this.worker.distanceToSqr(this.worker.getOwner()) : 1D;
    }

    void switchMainHandItem(Predicate<ItemStack> predicate) {
        if (!this.worker.isAlive() || predicate == null) {
            return;
        }

        SimpleContainer inventory = this.worker.getInventory();
        ItemStack mainHand = this.worker.getMainHandItem();
        if (predicate.test(mainHand)) {
            return;
        }

        for (int i = WORKER_STORAGE_START_SLOT; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (predicate.test(stack)) {
                inventory.setItem(i, mainHand);
                this.worker.setItemInHand(InteractionHand.MAIN_HAND, stack);
                return;
            }
        }
    }

    double getHorizontalDistanceTo(Vec3 target) {
        Vec3 position = new Vec3(this.worker.position().x, 0, this.worker.position().z);
        Vec3 toTarget = new Vec3(target.x, 0, target.z);

        return position.distanceToSqr(toTarget);
    }

    boolean shouldWork() {
        return this.worker.isOwned()
                && (this.worker.getFollowState() == 0 || this.worker.getFollowState() == 6)
                && currentIntentAllowsWork();
    }

    private boolean currentIntentAllowsWork() {
        if (this.worker.getCurrentWorkArea() == null) {
            return true;
        }
        if (!(this.worker.level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        return NpcSocietyAccess.profileFor(serverLevel, this.worker.getUUID())
                .map(profile -> NpcSocietyIntentRules.isWorkerLaborIntent(profile.currentIntent()))
                .orElse(true);
    }

    private boolean currentIntentIsRestLike() {
        if (!(this.worker.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return NpcSocietyAccess.profileFor(serverLevel, this.worker.getUUID())
                .map(profile -> NpcSocietyIntentRules.isRestLikeIntent(profile.currentIntent()))
                .orElse(false);
    }
}
