package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.shared.settlement.BannerModSettlementRefreshSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

public final class WorkerDismissService {
    private WorkerDismissService() {
    }

    @Nullable
    public static String dismissDeniedReasonKey(@Nullable ServerPlayer player, @Nullable AbstractWorkerEntity worker) {
        if (player == null || worker == null || !(worker.level() instanceof ServerLevel)) {
            return "chat.bannermod.workerui.dismiss.denied.missing";
        }
        if (!worker.isAlive() || worker.isRemoved()) {
            return "chat.bannermod.workerui.dismiss.denied.dead";
        }
        if (player.distanceToSqr(worker) > 16.0D * 16.0D) {
            return "chat.bannermod.workerui.dismiss.denied.too_far";
        }
        if (player.getUUID().equals(worker.getOwnerUUID()) || player.hasPermissions(2)) {
            return null;
        }
        return "chat.bannermod.workerui.dismiss.denied.not_owner";
    }

    public static boolean dismiss(@Nullable ServerPlayer player, @Nullable AbstractWorkerEntity worker) {
        if (dismissDeniedReasonKey(player, worker) != null || worker == null || !(worker.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (worker.getCurrentWorkArea() != null) {
            worker.getCurrentWorkArea().setBeingWorkedOn(false);
        }
        worker.discard();
        BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, worker.blockPosition());
        return true;
    }
}
