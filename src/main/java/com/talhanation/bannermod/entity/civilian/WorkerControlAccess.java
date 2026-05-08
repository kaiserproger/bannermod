package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

final class WorkerControlAccess {
    private final AbstractWorkerEntity worker;
    private final WorkerStatusRuntime statusRuntime;
    private final WorkerRecoveryService recoveryService;
    private UUID boundWorkArea;

    WorkerControlAccess(AbstractWorkerEntity worker) {
        this.worker = worker;
        this.statusRuntime = new WorkerStatusRuntime(worker);
        this.recoveryService = new WorkerRecoveryService(worker, this);
    }

    @Nullable
    UUID getBoundWorkAreaUUID() {
        return this.boundWorkArea;
    }

    void setBoundWorkAreaBinding(@Nullable UUID boundWorkAreaUuid) {
        UUID previous = this.boundWorkArea;
        this.boundWorkArea = boundWorkAreaUuid;
        if ((previous == null && boundWorkAreaUuid == null) || (previous != null && previous.equals(boundWorkAreaUuid))) {
            return;
        }
        refreshSettlementSnapshot();
    }

    void reportBlockedReason(String reasonToken, @Nullable Component message) {
        this.statusRuntime.reportReason(WorkerControlStatus.Kind.BLOCKED, reasonToken, message);
    }

    void reportIdleReason(String reasonToken, @Nullable Component message) {
        this.statusRuntime.reportReason(WorkerControlStatus.Kind.IDLE, reasonToken, message);
    }

    void clearWorkStatus() {
        this.statusRuntime.clearWorkStatus();
    }

    WorkerControlStatus workStatus() {
        return this.statusRuntime.workStatus();
    }

    void resetRecoveredControlState() {
        this.statusRuntime.resetRecoveredControlState();
    }

    boolean recoverControl(Player requester) {
        return this.recoveryService.recoverControl(requester);
    }

    private void refreshSettlementSnapshot() {
        if (!(this.worker.level() instanceof ServerLevel serverLevel) || ClaimEvents.claimManager() == null) {
            return;
        }
        SettlementService.refreshClaimAt(
                serverLevel,
                ClaimEvents.claimManager(),
                SettlementManager.get(serverLevel),
                BannerModGovernorManager.get(serverLevel),
                this.worker.blockPosition()
        );
    }
}
