package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.citizen.CitizenProfession;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.military.RecruitPoliticalContext;
import com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes;
import com.talhanation.bannermod.registry.civilian.ModEntityTypes;
import com.talhanation.bannermod.settlement.prefab.staffing.PrefabAutoStaffingRuntime;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementRefreshSupport;
import com.talhanation.bannermod.society.NpcSocietyAccess;
import com.talhanation.bannermod.war.WarRuntimeContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public final class WorkerCitizenConversionService {
    public static final int MANUAL_ASSIGNMENT_PAUSE_TICKS = 20 * 60;

    private WorkerCitizenConversionService() {
    }

    @Nullable
    public static String convertDeniedReasonKey(@Nullable ServerPlayer player, @Nullable AbstractWorkerEntity worker) {
        if (player == null || worker == null || !(worker.level() instanceof ServerLevel serverLevel)) {
            return "gui.bannermod.worker_screen.convert.denied.missing";
        }
        if (!worker.isAlive() || worker.isRemoved()) {
            return "gui.bannermod.worker_screen.convert.denied.dead";
        }
        if (player.distanceToSqr(worker) > 16.0D * 16.0D) {
            return "gui.bannermod.worker_screen.convert.denied.too_far";
        }
        if (player.isCreative() && player.hasPermissions(2)) {
            return null;
        }
        UUID playerPoliticalEntityId = RecruitPoliticalContext.politicalEntityIdOf(player, WarRuntimeContext.registry(serverLevel));
        UUID workerPoliticalEntityId = RecruitPoliticalContext.politicalEntityIdOf(worker, WarRuntimeContext.registry(serverLevel));
        if (player.getUUID().equals(worker.getOwnerUUID())
                || (playerPoliticalEntityId != null && playerPoliticalEntityId.equals(workerPoliticalEntityId))) {
            return null;
        }
        return "gui.bannermod.worker_screen.convert.denied.not_controller";
    }

    public static boolean canConvert(@Nullable ServerPlayer player, @Nullable AbstractWorkerEntity worker) {
        return convertDeniedReasonKey(player, worker) == null;
    }

    public static boolean convert(@Nullable ServerPlayer player, @Nullable AbstractWorkerEntity worker) {
        if (convertDeniedReasonKey(player, worker) != null || worker == null || !(worker.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        CitizenEntity citizen = ModCitizenEntityTypes.CITIZEN.get().create(serverLevel);
        if (citizen == null) {
            return false;
        }

        citizen.moveTo(worker.getX(), worker.getY(), worker.getZ(), worker.getYRot(), worker.getXRot());
        citizen.apply(worker.getCitizenCore().snapshot());
        citizen.setFollowState(0);
        citizen.setBoundWorkAreaUUID(null);
        citizen.clearHoldPos();
        citizen.clearMovePos();
        // Owner ("сеньор") preservation on worker → citizen conversion. The snapshot/apply
        // path can drop the worker's owner UUID for citizens originally settlement-spawned
        // without an explicit owner. Carry the worker's owner across, and fall back to the
        // owner of the claim under the conversion target so the resulting citizen never
        // ends up unowned in friendly territory.
        UUID resolvedOwner = worker.getOwnerUUID();
        if (resolvedOwner == null && com.talhanation.bannermod.events.ClaimEvents.claimManager() != null) {
            com.talhanation.bannermod.persistence.military.RecruitsClaim claim = com.talhanation.bannermod.events.ClaimEvents.claimManager()
                    .getClaim(new net.minecraft.world.level.ChunkPos(citizen.blockPosition()));
            if (claim != null && claim.getPlayerInfo() != null) {
                resolvedOwner = claim.getPlayerInfo().getUUID();
            }
        }
        if (resolvedOwner != null) {
            citizen.setOwnerUUID(java.util.Optional.of(resolvedOwner));
        }
        citizen.getPersistentData().putLong(
                PrefabAutoStaffingRuntime.TAG_ASSIGNMENT_PAUSE_UNTIL,
                serverLevel.getGameTime() + MANUAL_ASSIGNMENT_PAUSE_TICKS
        );
        if (worker.hasCustomName()) {
            citizen.setCustomName(worker.getCustomName());
        }

        serverLevel.addFreshEntity(citizen);
        NpcSocietyAccess.moveResidentProfile(serverLevel, worker.getUUID(), citizen.getUUID(), serverLevel.getGameTime());
        if (worker.getTeam() instanceof PlayerTeam team) {
            serverLevel.getScoreboard().addPlayerToTeam(citizen.getScoreboardName(), team);
        }
        if (worker.getCurrentWorkArea() != null) {
            worker.getCurrentWorkArea().setBeingWorkedOn(false);
        }
        worker.discard();
        BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, citizen.blockPosition());
        return true;
    }

    /**
     * Map a worker entity to its {@link CitizenProfession} tag (e.g. "FARMER").
     * Returns the empty string for unknown subclasses so the UI can fall back
     * to offering the full reassign list instead of filtering nothing out.
     */
    public static String workerProfessionTag(@Nullable AbstractWorkerEntity worker) {
        if (worker == null) {
            return "";
        }
        if (worker instanceof FarmerEntity) {
            return CitizenProfession.FARMER.name();
        }
        if (worker instanceof LumberjackEntity) {
            return CitizenProfession.LUMBERJACK.name();
        }
        if (worker instanceof MinerEntity) {
            return CitizenProfession.MINER.name();
        }
        if (worker instanceof AnimalFarmerEntity) {
            return CitizenProfession.ANIMAL_FARMER.name();
        }
        if (worker instanceof BuilderEntity) {
            return CitizenProfession.BUILDER.name();
        }
        if (worker instanceof MerchantEntity) {
            return CitizenProfession.MERCHANT.name();
        }
        if (worker instanceof FishermanEntity) {
            return CitizenProfession.FISHERMAN.name();
        }
        return "";
    }

    @Nullable
    public static EntityType<? extends AbstractWorkerEntity> workerTypeFor(CitizenProfession profession) {
        if (profession == null) {
            return null;
        }
        return switch (profession) {
            case FARMER -> ModEntityTypes.FARMER.get();
            case LUMBERJACK -> ModEntityTypes.LUMBERJACK.get();
            case MINER -> ModEntityTypes.MINER.get();
            case BUILDER -> ModEntityTypes.BUILDER.get();
            case MERCHANT -> ModEntityTypes.MERCHANT.get();
            case FISHERMAN -> ModEntityTypes.FISHERMAN.get();
            case ANIMAL_FARMER -> ModEntityTypes.ANIMAL_FARMER.get();
            default -> null;
        };
    }

    /**
     * Atomic profession reassignment: convert worker -> citizen, switch
     * profession, spawn the new worker entity at the same anchor with the
     * same ownership and bound work area. Returns null on success or a
     * translatable denial key on failure (no entity is mutated when the
     * denial key is non-null).
     *
     * <p>Server-authoritative — auth reuses {@link #convertDeniedReasonKey}.
     */
    @Nullable
    public static String reassignProfession(@Nullable ServerPlayer player,
                                             @Nullable AbstractWorkerEntity worker,
                                             @Nullable CitizenProfession target) {
        if (target == null || target == CitizenProfession.NONE
                || target.coarseRole() != com.talhanation.bannermod.citizen.CitizenRole.CONTROLLED_WORKER) {
            return "chat.bannermod.workerui.reassign.denied.invalid_profession";
        }
        EntityType<? extends AbstractWorkerEntity> targetType = workerTypeFor(target);
        if (targetType == null) {
            return "chat.bannermod.workerui.reassign.denied.invalid_profession";
        }
        if (worker == null) {
            return "chat.bannermod.workerui.reassign.denied.missing";
        }
        String authDenial = convertDeniedReasonKey(player, worker);
        if (authDenial != null) {
            return authDenial;
        }
        if (target.name().equals(workerProfessionTag(worker))) {
            return "chat.bannermod.workerui.reassign.denied.same_profession";
        }
        if (!(worker.level() instanceof ServerLevel serverLevel)) {
            return "chat.bannermod.workerui.reassign.denied.missing";
        }

        // Snapshot anchor before mutation.
        double x = worker.getX();
        double y = worker.getY();
        double z = worker.getZ();
        float yaw = worker.getYRot();
        float pitch = worker.getXRot();
        UUID ownerUuid = worker.getOwnerUUID();
        boolean isOwned = worker.isOwned();
        UUID boundWorkAreaUuid = worker.getBoundWorkAreaUUID();
        PlayerTeam team = worker.getTeam() instanceof PlayerTeam pt ? pt : null;

        AbstractWorkerEntity replacement = targetType.create(serverLevel);
        if (replacement == null) {
            return "chat.bannermod.workerui.reassign.denied.convert_failed";
        }

        // Tear down the old worker; mirror the convert() cleanup but skip the
        // citizen-spawn step since we are spawning a new worker directly.
        if (worker.getCurrentWorkArea() != null) {
            worker.getCurrentWorkArea().setBeingWorkedOn(false);
        }
        worker.discard();

        replacement.moveTo(x, y, z, yaw, pitch);
        if (ownerUuid != null) {
            replacement.setOwnerUUID(Optional.of(ownerUuid));
            replacement.setIsOwned(isOwned);
        }
        if (boundWorkAreaUuid != null) {
            replacement.getCitizenCore().setBoundWorkAreaUUID(boundWorkAreaUuid);
        }
        replacement.getPersistentData().putLong(
                PrefabAutoStaffingRuntime.TAG_ASSIGNMENT_PAUSE_UNTIL,
                serverLevel.getGameTime() + MANUAL_ASSIGNMENT_PAUSE_TICKS
        );

        if (!serverLevel.addFreshEntity(replacement)) {
            return "chat.bannermod.workerui.reassign.denied.convert_failed";
        }
        NpcSocietyAccess.moveResidentProfile(serverLevel, worker.getUUID(), replacement.getUUID(), serverLevel.getGameTime());
        if (team != null) {
            serverLevel.getScoreboard().addPlayerToTeam(replacement.getScoreboardName(), team);
        }
        // Workers carry a CitizenCore but no per-instance profession switcher;
        // the new worker subclass itself encodes the profession identity, so
        // no switchProfession call is needed here.
        BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, replacement.blockPosition());
        return null;
    }
}
