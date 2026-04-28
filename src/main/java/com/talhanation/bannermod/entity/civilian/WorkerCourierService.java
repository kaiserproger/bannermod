package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.ai.civilian.CourierTaskFlow;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.persistence.civilian.NeededItem;
import com.talhanation.bannermod.shared.logistics.BannerModCourierTask;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsBlockedReason;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRuntime;
import com.talhanation.bannermod.shared.logistics.BannerModSupplyStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

final class WorkerCourierService {
    private final AbstractWorkerEntity worker;
    private final WorkerStorageRequestState storageRequestState = new WorkerStorageRequestState();
    @Nullable
    private BannerModCourierTask activeCourierTask;
    @Nullable
    private NeededItem activeCourierNeededItem;

    WorkerCourierService(AbstractWorkerEntity worker) {
        this.worker = worker;
    }

    void clearStorageRequestState() {
        this.storageRequestState.clear();
    }

    void requestRequiredItem(NeededItem neededItem, String reasonToken, Component message) {
        if (neededItem == null) {
            return;
        }

        if (!neededItem.required) {
            this.worker.addNeededItem(neededItem);
            return;
        }

        if (this.worker.countMatchingItems(neededItem::matches) >= neededItem.count) {
            this.clearPendingStorageComplaint();
            return;
        }

        this.worker.addNeededItem(neededItem);
        if (message != null) {
            this.storageRequestState.recordPendingComplaint(reasonToken, message.getString());
        }
    }

    void clearPendingStorageComplaint() {
        this.storageRequestState.clear();
    }

    @Nullable
    WorkerStorageRequestState.PendingComplaint releasePendingStorageComplaint() {
        return this.storageRequestState.releasePendingComplaint();
    }

    @Nullable
    BannerModCourierTask getActiveCourierTask() {
        return this.activeCourierTask;
    }

    boolean hasActiveCourierTask() {
        return this.activeCourierTask != null;
    }

    void setActiveCourierTask(@Nullable BannerModCourierTask activeCourierTask) {
        this.activeCourierTask = activeCourierTask;
        if (activeCourierTask == null) {
            this.worker.transportService().releaseTransport();
        }
        this.clearCourierBlockedState();
        this.syncActiveCourierTaskNeeds();
    }

    void clearActiveCourierTask() {
        if (this.activeCourierTask != null) {
            BannerModLogisticsRuntime.service().releaseReservation(this.activeCourierTask.reservation().reservationId());
        }
        this.activeCourierTask = null;
        this.worker.transportService().releaseTransport();
        if (this.activeCourierNeededItem != null) {
            this.worker.neededItems.remove(this.activeCourierNeededItem);
            this.activeCourierNeededItem = null;
        }
    }

    int getActiveCourierCarriedCount() {
        if (this.activeCourierTask == null) {
            return 0;
        }
        return this.worker.countMatchingItems(this.activeCourierTask.reservation().filter()::matches);
    }

    int getActiveCourierPickupMissingCount() {
        if (this.activeCourierTask == null) {
            return 0;
        }
        return CourierTaskFlow.missingPickupCount(this.activeCourierTask, this.getActiveCourierCarriedCount());
    }

    boolean hasActiveCourierPickupPending() {
        if (this.activeCourierTask == null) {
            return false;
        }
        return CourierTaskFlow.pickupPending(this.activeCourierTask, this.getActiveCourierCarriedCount());
    }

    @Nullable
    UUID getActiveCourierTargetStorageAreaId() {
        if (this.activeCourierTask == null) {
            return null;
        }
        return CourierTaskFlow.targetStorageAreaId(this.activeCourierTask, this.getActiveCourierCarriedCount());
    }

    void markActiveCourierPickupComplete() {
        this.syncActiveCourierTaskNeeds();
        this.clearPendingStorageComplaint();
    }

    void completeActiveCourierDelivery() {
        this.clearCourierBlockedState();
        this.clearActiveCourierTask();
        this.clearPendingStorageComplaint();
        this.worker.forcedDeposit = false;
    }

    void abandonActiveCourierTask(BannerModLogisticsBlockedReason reason, String message) {
        if (this.activeCourierTask == null || reason == null) {
            return;
        }

        this.updateCourierBlockedState(reason, message);
        this.clearActiveCourierTask();
        this.worker.forcedDeposit = false;
        this.worker.reportBlockedReason(reason.reasonToken(), Component.literal(this.worker.getName().getString() + ": " + message));
    }

    BannerModSupplyStatus.WorkerSupplyStatus getSupplyStatus() {
        return BannerModSupplyStatus.workerSupplyStatus(this.storageRequestState.peekPendingComplaint());
    }

    boolean hasPendingStorageComplaint() {
        return this.storageRequestState.hasPendingComplaint();
    }

    private void syncActiveCourierTaskNeeds() {
        if (this.activeCourierNeededItem != null) {
            this.worker.neededItems.remove(this.activeCourierNeededItem);
            this.activeCourierNeededItem = null;
        }

        if (this.activeCourierTask == null) {
            return;
        }

        int missingCount = this.getActiveCourierPickupMissingCount();
        if (missingCount <= 0) {
            return;
        }

        this.activeCourierNeededItem = new NeededItem(this.activeCourierTask.reservation().filter()::matches, missingCount, true);
        this.worker.neededItems.add(this.activeCourierNeededItem);
    }

    private void clearCourierBlockedState() {
        this.updateStorageAreaBlockedState(this.resolveActiveCourierSourceStorage(), null, null);
        this.updateStorageAreaBlockedState(this.resolveActiveCourierDestinationStorage(), null, null);
    }

    private void updateCourierBlockedState(BannerModLogisticsBlockedReason reason, String message) {
        StorageArea sourceStorage = this.resolveActiveCourierSourceStorage();
        StorageArea destinationStorage = this.resolveActiveCourierDestinationStorage();
        switch (reason) {
            case SOURCE_SHORTAGE, SOURCE_CONTAINER_MISSING -> this.updateStorageAreaBlockedState(sourceStorage, reason, message);
            case DESTINATION_CONTAINER_MISSING, DESTINATION_FULL -> this.updateStorageAreaBlockedState(destinationStorage, reason, message);
            case RESERVATION_TIMEOUT -> {
                this.updateStorageAreaBlockedState(sourceStorage, reason, message);
                this.updateStorageAreaBlockedState(destinationStorage, reason, message);
            }
        }
    }

    private void updateStorageAreaBlockedState(@Nullable StorageArea storageArea,
                                               @Nullable BannerModLogisticsBlockedReason reason,
                                               @Nullable String message) {
        if (storageArea == null) {
            return;
        }
        if (reason == null) {
            storageArea.clearRouteBlockedState();
            return;
        }
        storageArea.setRouteBlockedState(reason, message);
    }

    @Nullable
    private StorageArea resolveActiveCourierSourceStorage() {
        if (this.activeCourierTask == null || this.worker.level().isClientSide()) {
            return null;
        }
        return this.worker.level() instanceof ServerLevel serverLevel ? this.findStorageArea(serverLevel, this.activeCourierTask.route().source().storageAreaId()) : null;
    }

    @Nullable
    private StorageArea resolveActiveCourierDestinationStorage() {
        if (this.activeCourierTask == null || this.worker.level().isClientSide()) {
            return null;
        }
        return this.worker.level() instanceof ServerLevel serverLevel ? this.findStorageArea(serverLevel, this.activeCourierTask.route().destination().storageAreaId()) : null;
    }

    @Nullable
    private StorageArea findStorageArea(ServerLevel level, UUID storageAreaId) {
        Entity entity = level.getEntity(storageAreaId);
        return entity instanceof StorageArea storageArea ? storageArea : null;
    }
}
