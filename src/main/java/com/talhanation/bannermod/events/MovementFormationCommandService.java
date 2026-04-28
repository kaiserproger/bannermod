package com.talhanation.bannermod.events;

import com.talhanation.bannermod.ai.military.controller.RecruitCommandStateTransitions;
import com.talhanation.bannermod.army.command.MovementCommandState;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractLeaderEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.CaptainEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.util.FormationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class MovementFormationCommandService {

    private MovementFormationCommandService() {
    }

    static void onMovementCommand(Player player, List<AbstractRecruitEntity> recruits, int movementState, int formation) {
        onMovementCommand(player, recruits, movementState, formation, false);
    }

    static void onMovementCommand(Player player, List<AbstractRecruitEntity> recruits, int movementState, int formation, boolean tight) {
        onMovementCommand(player, recruits, movementState, formation, tight, null);
    }

    static void onMovementCommand(Player player, List<AbstractRecruitEntity> recruits, int movementState, int formation, boolean tight, @Nullable Vec3 explicitTargetPos) {
        if (formation != 0 && MovementCommandState.usesFormationTarget(movementState)) {
            Vec3 targetPos = null;

            switch (movementState) {
                case MovementCommandState.HOLD_POSITION -> targetPos = FormationUtils.getGeometricMedian(recruits, (ServerLevel) player.getCommandSenderWorld());
                case MovementCommandState.HOLD_OWNER_POSITION -> targetPos = player.position();
                case MovementCommandState.MOVE_TO_POSITION -> {
                    if (explicitTargetPos != null) {
                        targetPos = explicitTargetPos;
                    } else {
                        HitResult hitResult = player.pick(200, 1F, true);
                        targetPos = hitResult.getLocation();
                    }
                }
                case MovementCommandState.FORWARD -> targetPos = getFormationRelativeTarget(player, recruits, 1D);
                case MovementCommandState.BACKWARD -> targetPos = getFormationRelativeTarget(player, recruits, -1D);
            }

            applyFormation(formation, recruits, player, targetPos, tight);
        } else {
            for (AbstractRecruitEntity recruit : recruits) {
                int state = recruit.getFollowState();

                switch (movementState) {
                    case MovementCommandState.WANDER -> {
                        if (state != MovementCommandState.WANDER) {
                            recruit.setFollowState(MovementCommandState.WANDER);
                        }
                    }
                    case MovementCommandState.FOLLOW -> {
                        if (state != MovementCommandState.FOLLOW) {
                            recruit.setFollowState(MovementCommandState.FOLLOW);
                        }
                    }
                    case MovementCommandState.HOLD_POSITION -> {
                        if (state != MovementCommandState.HOLD_POSITION) {
                            recruit.setFollowState(MovementCommandState.HOLD_POSITION);
                        }
                    }
                    case MovementCommandState.BACK_TO_POSITION -> {
                        if (state != MovementCommandState.BACK_TO_POSITION) {
                            recruit.setFollowState(MovementCommandState.BACK_TO_POSITION);
                        }
                    }
                    case MovementCommandState.HOLD_OWNER_POSITION -> {
                        if (state != MovementCommandState.HOLD_OWNER_POSITION) {
                            recruit.setFollowState(MovementCommandState.HOLD_OWNER_POSITION);
                        }
                    }
                    case MovementCommandState.PROTECT -> {
                        if (state != MovementCommandState.PROTECT) {
                            recruit.setFollowState(MovementCommandState.PROTECT);
                        }
                    }
                    case MovementCommandState.MOVE_TO_POSITION -> {
                        BlockPos blockPos = null;
                        if (explicitTargetPos != null) {
                            blockPos = BlockPos.containing(explicitTargetPos);
                        } else {
                            HitResult hitResult = player.pick(100, 1F, true);
                            if (hitResult.getType() == HitResult.Type.BLOCK) {
                                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                                blockPos = blockHitResult.getBlockPos();
                            }
                        }

                        if (blockPos != null) {
                            recruit.setMovePos(blockPos);
                            recruit.setFollowState(MovementCommandState.WANDER);
                            recruit.setShouldMovePos(true);
                        }
                    }
                    case MovementCommandState.FORWARD -> applySingleRecruitForwardBack(player, recruit, 1D);
                    case MovementCommandState.BACKWARD -> applySingleRecruitForwardBack(player, recruit, -1D);
                }

                recruit.isInFormation = false;
            }
        }

        for (AbstractRecruitEntity recruit : recruits) {
            recruit.setUpkeepTimer(recruit.getUpkeepCooldown());
            if (recruit.getShouldMount()) {
                recruit.setShouldMount(false);
            }

            checkPatrolLeaderState(recruit);
            recruit.forcedUpkeep = false;
        }
    }

    static void applyFormation(int formation, List<AbstractRecruitEntity> recruits, Player player, Vec3 targetPos) {
        applyFormation(formation, recruits, player, targetPos, false);
    }

    static void applyFormation(int formation, List<AbstractRecruitEntity> recruits, Player player, Vec3 targetPos, boolean tight) {
        saveFormationCenter(player, targetPos);
        double spacingMultiplier = tight ? 0.5 : 1.0;

        switch (formation) {
            case 1 -> FormationUtils.lineUpFormation(player, recruits, targetPos, spacingMultiplier);
            case 2 -> FormationUtils.squareFormation(player, recruits, targetPos, spacingMultiplier);
            case 3 -> FormationUtils.triangleFormation(player, recruits, targetPos, spacingMultiplier);
            case 4 -> FormationUtils.hollowCircleFormation(player, recruits, targetPos, spacingMultiplier);
            case 5 -> FormationUtils.hollowSquareFormation(player, recruits, targetPos, spacingMultiplier);
            case 6 -> FormationUtils.vFormation(player, recruits, targetPos, spacingMultiplier);
            case 7 -> FormationUtils.circleFormation(player, recruits, targetPos, spacingMultiplier);
            case 8 -> FormationUtils.movementFormation(player, recruits, targetPos, spacingMultiplier);
            case 9 -> FormationUtils.testudoFormation(player, recruits, targetPos, spacingMultiplier);
        }
    }

    static void onFaceCommand(Player player, List<AbstractRecruitEntity> recruits, int formation, boolean tight) {
        if (recruits.isEmpty()) {
            return;
        }

        if (formation != 0) {
            Vec3 targetPos = getSavedFormationCenter(player);
            if (targetPos == null) {
                targetPos = FormationUtils.getGeometricMedian(recruits, (ServerLevel) player.getCommandSenderWorld());
            }
            applyFormation(formation, recruits, player, targetPos, tight);
        } else {
            for (AbstractRecruitEntity recruit : recruits) {
                recruit.setHoldPos(recruit.position());
                recruit.setFollowState(3);
            }
        }

        for (AbstractRecruitEntity recruit : recruits) {
            recruit.ownerRot = player.getYRot();
            recruit.rotateTicks = 40;

            if (recruit instanceof CaptainEntity captain && captain.smallShipsController.ship != null && captain.smallShipsController.ship.isCaptainDriver()) {
                captain.smallShipsController.startFaceRotation(player.getYRot());
            }
        }
    }

    static void onMovementCommandGUI(AbstractRecruitEntity recruit, int movementState) {
        int state = recruit.getFollowState();

        switch (movementState) {
            case 0 -> {
                if (state != 0) {
                    recruit.setFollowState(0);
                }
            }
            case 1 -> {
                if (state != 1) {
                    recruit.setFollowState(1);
                }
            }
            case 2 -> {
                if (state != 2) {
                    recruit.setFollowState(2);
                }
            }
            case 3 -> {
                if (state != 3) {
                    recruit.setFollowState(3);
                }
            }
            case 4 -> {
                if (state != 4) {
                    recruit.setFollowState(4);
                }
            }
            case 5 -> {
                if (state != 5) {
                    recruit.setFollowState(5);
                }
            }
        }

        recruit.setUpkeepTimer(recruit.getUpkeepCooldown());
        if (recruit.getShouldMount()) {
            recruit.setShouldMount(false);
        }
        checkPatrolLeaderState(recruit);
        recruit.forcedUpkeep = false;
    }

    static void checkPatrolLeaderState(AbstractRecruitEntity recruit) {
        if (recruit instanceof AbstractLeaderEntity leader) {
            AbstractLeaderEntity.State patrolState = AbstractLeaderEntity.State.fromIndex(leader.getPatrollingState());
            AbstractLeaderEntity.State nextState = RecruitCommandStateTransitions.afterManualMovement(patrolState);
            if (nextState == AbstractLeaderEntity.State.IDLE && patrolState != nextState) {
                leader.resetPatrolling();
                leader.setPatrolState(nextState);
            } else if (nextState != patrolState) {
                leader.setPatrolState(nextState);
            }
        }
    }

    static void onServerPlayerTick(ServerPlayer serverPlayer) {
        int formation = getSavedFormation(serverPlayer);
        if (formation <= 0) {
            return;
        }

        int[] savedPos = getSavedFormationPos(serverPlayer);
        if (savedPos.length == 0) {
            savedPos = new int[]{(int) serverPlayer.getX(), (int) serverPlayer.getZ()};
            saveFormationPos(serverPlayer, savedPos);
        }

        Vec3 oldPos = new Vec3(savedPos[0], serverPlayer.getY(), savedPos[1]);
        Vec3 targetPosition = serverPlayer.position();
        if (targetPosition.distanceToSqr(oldPos) <= 50) {
            return;
        }

        AABB commandBox = serverPlayer.getBoundingBox().inflate(200);
        List<AbstractRecruitEntity> recruits = RecruitIndex.instance().allInBox(serverPlayer.getCommandSenderWorld(), commandBox, false);
        if (recruits == null) {
            RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
            recruits = serverPlayer.getCommandSenderWorld().getEntitiesOfClass(AbstractRecruitEntity.class, commandBox);
        }

        List<RecruitsGroup> groups = RecruitEvents.recruitsGroupsManager.getPlayerGroups(serverPlayer);
        if (groups == null) {
            return;
        }

        List<UUID> activeGroups = getSavedUUIDList(serverPlayer, "ActiveGroups");
        recruits.removeIf(recruit -> !activeGroups.contains(recruit.getGroup()));
        groups.removeIf(group -> !activeGroups.contains(group.getUUID()));

        applyFormation(formation, recruits, serverPlayer, targetPosition);
        saveFormationPos(serverPlayer, new int[]{(int) targetPosition.x, (int) targetPosition.z});
    }

    static void initializePlayerCommandState(Player player) {
        CompoundTag playerData = player.getPersistentData();
        CompoundTag data = playerData.getCompound(Player.PERSISTED_NBT_TAG);

        if (!data.contains("MaxRecruits")) {
            data.putInt("MaxRecruits", RecruitsServerConfig.MaxRecruitsForPlayer.get());
        }
        if (!data.contains("CommandingGroup")) {
            data.putInt("CommandingGroup", 0);
        }
        if (!data.contains("TotalRecruits")) {
            data.putInt("TotalRecruits", 0);
        }
        if (!data.contains("ActiveGroups")) {
            data.put("ActiveGroups", new ListTag());
        }
        if (!data.contains("Formation")) {
            data.putInt("Formation", 0);
        }
        if (!data.contains("FormationPos")) {
            data.putIntArray("FormationPos", new int[]{(int) player.getX(), (int) player.getZ()});
        }

        playerData.put(Player.PERSISTED_NBT_TAG, data);
    }

    static int getSavedFormation(Player player) {
        return getPersistedData(player).getInt("Formation");
    }

    static void saveFormation(Player player, int formation) {
        CompoundTag persisted = getPersistedData(player);
        persisted.putInt("Formation", formation);
        savePersistedData(player, persisted);
    }

    static void saveUUIDList(Player player, String key, Collection<UUID> uuids) {
        CompoundTag persisted = getPersistedData(player);
        ListTag list = new ListTag();

        for (UUID uuid : uuids) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", uuid);
            list.add(tag);
        }

        persisted.put(key, list);
        savePersistedData(player, persisted);
    }

    static List<UUID> getSavedUUIDList(Player player, String key) {
        CompoundTag persisted = getPersistedData(player);
        List<UUID> result = new ArrayList<>();
        if (!persisted.contains(key, Tag.TAG_LIST)) {
            return result;
        }

        ListTag list = persisted.getList(key, Tag.TAG_COMPOUND);
        for (Tag tag : list) {
            CompoundTag uuidTag = (CompoundTag) tag;
            if (uuidTag.hasUUID("UUID")) {
                result.add(uuidTag.getUUID("UUID"));
            }
        }

        return result;
    }

    static int[] getSavedFormationPos(Player player) {
        return getPersistedData(player).getIntArray("FormationPos");
    }

    static void saveFormationPos(Player player, int[] pos) {
        CompoundTag persisted = getPersistedData(player);
        persisted.putIntArray("FormationPos", pos);
        savePersistedData(player, persisted);
    }

    static void saveFormationCenter(Player player, Vec3 center) {
        CompoundTag persisted = getPersistedData(player);
        persisted.putDouble("FormationCenterX", center.x);
        persisted.putDouble("FormationCenterY", center.y);
        persisted.putDouble("FormationCenterZ", center.z);
        savePersistedData(player, persisted);
    }

    @Nullable
    static Vec3 getSavedFormationCenter(Player player) {
        CompoundTag persisted = getPersistedData(player);
        if (!persisted.contains("FormationCenterX")) {
            return null;
        }

        return new Vec3(
                persisted.getDouble("FormationCenterX"),
                persisted.getDouble("FormationCenterY"),
                persisted.getDouble("FormationCenterZ")
        );
    }

    private static Vec3 getFormationRelativeTarget(Player player, List<AbstractRecruitEntity> recruits, double direction) {
        Vec3 center = getSavedFormationCenter(player);
        if (center == null) {
            center = FormationUtils.getGeometricMedian(recruits, (ServerLevel) player.getCommandSenderWorld());
        }

        Vec3 forward = player.getForward();
        Vec3 pos = center.add(forward.scale(direction * getForwardScale(recruits)));
        BlockPos blockPos = FormationUtils.getPositionOrSurface(
                player.getCommandSenderWorld(),
                new BlockPos((int) pos.x, (int) pos.y, (int) pos.z)
        );
        return new Vec3(pos.x, blockPos.getY(), pos.z);
    }

    private static void applySingleRecruitForwardBack(Player player, AbstractRecruitEntity recruit, double direction) {
        Vec3 forward = player.getForward();
        Vec3 pos = recruit.position().add(forward.scale(direction * getForwardScale(recruit)));
        BlockPos blockPos = FormationUtils.getPositionOrSurface(
                player.getCommandSenderWorld(),
                new BlockPos((int) pos.x, (int) pos.y, (int) pos.z)
        );
        Vec3 targetPos = new Vec3(pos.x, blockPos.getY(), pos.z);

        recruit.setHoldPos(targetPos);
        recruit.ownerRot = player.getYRot();
        recruit.setFollowState(3);
    }

    private static double getForwardScale(List<AbstractRecruitEntity> recruits) {
        for (AbstractRecruitEntity recruit : recruits) {
            if (recruit instanceof CaptainEntity) {
                return getForwardScale(recruit);
            }
        }
        return 10;
    }

    private static double getForwardScale(AbstractRecruitEntity recruit) {
        return recruit instanceof CaptainEntity captain
                && captain.smallShipsController.ship != null
                && captain.smallShipsController.ship.isCaptainDriver() ? 25 : 10;
    }

    private static CompoundTag getPersistedData(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void savePersistedData(Player player, CompoundTag persisted) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
