package com.talhanation.bannermod.events;
import com.talhanation.bannermod.bootstrap.BannerModMain;

import com.talhanation.bannermod.ai.military.CombatStance;
import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.entity.military.*;
import com.talhanation.bannermod.inventory.military.CommandMenu;
import com.talhanation.bannermod.network.messages.military.*;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CommandEvents {

    //0 = wander
    //1 = follow
    //2 = hold your position
    //3 = back to position
    //4 = hold my position
    //5 = Protect
    //6 = move
    //7 = forward
    //8 = backward
    public static void onMovementCommand(Player player, List<AbstractRecruitEntity> recruits, int movementState, int formation) {
        MovementFormationCommandService.onMovementCommand(player, recruits, movementState, formation);
    }

    public static void onMovementCommand(Player player, List<AbstractRecruitEntity> recruits, int movementState, int formation, boolean tight) {
        MovementFormationCommandService.onMovementCommand(player, recruits, movementState, formation, tight);
    }

    public static void onMovementCommand(Player player, List<AbstractRecruitEntity> recruits, int movementState, int formation, boolean tight, Vec3 targetPos) {
        MovementFormationCommandService.onMovementCommand(player, recruits, movementState, formation, tight, targetPos);
    }

    public static void applyFormation(int formation, List<AbstractRecruitEntity> recruits, Player player, Vec3 targetPos) {
        MovementFormationCommandService.applyFormation(formation, recruits, player, targetPos);
    }

    public static void applyFormation(int formation, List<AbstractRecruitEntity> recruits, Player player, Vec3 targetPos, boolean tight) {
        MovementFormationCommandService.applyFormation(formation, recruits, player, targetPos, tight);
    }

    public static void onFaceCommand(Player player, List<AbstractRecruitEntity> recruits, int formation, boolean tight) {
        MovementFormationCommandService.onFaceCommand(player, recruits, formation, tight);
    }

    public static void onMovementCommandGUI(AbstractRecruitEntity recruit, int movementState) {
        MovementFormationCommandService.onMovementCommandGUI(recruit, movementState);
    }

    public static void checkPatrolLeaderState(AbstractRecruitEntity recruit) {
        MovementFormationCommandService.checkPatrolLeaderState(recruit);
    }

    public static void onAggroCommand(UUID player_uuid, AbstractRecruitEntity recruit, int x_state, UUID group, boolean fromGui) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            int state = recruit.getState();
            switch (x_state) {

                case 0:
                    if (state != 0)
                        recruit.setAggroState(0);
                    break;

                case 1:
                    if (state != 1)
                        recruit.setAggroState(1);
                    break;

                case 2:
                    if (state != 2)
                        recruit.setAggroState(2);
                    break;

                case 3:
                    if (state != 3)
                        recruit.setAggroState(3);
                    break;
            }
        }
    }

    public static void onCombatStanceCommand(UUID playerUuid,
                                             AbstractRecruitEntity recruit,
                                             CombatStance stance,
                                             UUID group) {
        if (stance == null) {
            return;
        }
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            if (recruit.getCombatStance() != stance) {
                recruit.setCombatStance(stance);
            }
            // SHIELD_WALL is a visible command: keep shields raised by default so
            // the stance does not look like it was ignored.
            if (stance == CombatStance.SHIELD_WALL) {
                recruit.setShouldBlock(true);
            }
        }
    }

    public static void onAttackCommand(Player player, UUID player_uuid, List<AbstractRecruitEntity> list, UUID group) {
        HitResult hitResult = player.pick(100, 1F, false);
        BlockPos blockpos = null;
        List<LivingEntity> targets = new ArrayList<>();
        if (hitResult.getType() == HitResult.Type.ENTITY){
            EntityHitResult entityHitResult = (EntityHitResult) hitResult;

            blockpos = entityHitResult.getEntity().getOnPos();
            if(entityHitResult.getEntity() instanceof LivingEntity living) targets.add(living);
        }
        else if (hitResult.getType() == HitResult.Type.BLOCK){
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            blockpos = blockHitResult.getBlockPos();
        }
        else return;

        applyAttackAt(player, player_uuid, list, group, blockpos, targets, 10);
    }

    /**
     * Map-driven attack: assign combat targets near {@code targetPos} to {@code list} of recruits.
     * Used by world-map "free charge" / formation engagement actions where the target comes
     * over the network (UUID + BlockPos) instead of from {@code player.pick(...)}.
     *
     * @param searchRadius half-extent of the AABB around {@code targetPos} in blocks
     */
    public static void onAttackCommandAt(Player player, UUID player_uuid, List<AbstractRecruitEntity> list, UUID group,
                                         BlockPos targetPos, double searchRadius) {
        applyAttackAt(player, player_uuid, list, group, targetPos, new ArrayList<>(), searchRadius);
    }

    private static void applyAttackAt(Player player, UUID player_uuid, List<AbstractRecruitEntity> list, UUID group,
                                      BlockPos blockpos, List<LivingEntity> seedTargets, double searchRadius) {
        if (blockpos == null) return;
        AABB aabb = new AABB(blockpos).inflate(searchRadius);

        list.removeIf(recruit -> !recruit.isEffectedByCommand(player_uuid, group));

        List<LivingEntity> validTargets = seedTargets.isEmpty()
                ? player.getCommandSenderWorld().getEntitiesOfClass(LivingEntity.class, aabb)
                : new ArrayList<>(seedTargets);

        if (list.isEmpty() || validTargets.isEmpty()) return;

        validTargets.removeIf(target -> list.stream().noneMatch(recruit -> recruit.canAssignCombatTarget(target)));

        if (validTargets.isEmpty()) return;

        for (int i = 0; i < list.size(); i++) {
            AbstractRecruitEntity recruit = list.get(i);
            LivingEntity target = validTargets.get(i % validTargets.size());
            if (recruit.assignOrderedCombatTarget(target)) {
                recruit.setHoldPos(target.position());
                recruit.setFollowState(3);
            }
        }
    }

    public static void onStrategicFireCommand(Player player, UUID player_uuid, AbstractRecruitEntity recruit, UUID group, boolean should) {
        if (recruit.isEffectedByCommand(player_uuid, group)){

            if (recruit instanceof IStrategicFire bowman){
                HitResult hitResult = player.pick(100, 1F, false);
                bowman.setShouldStrategicFire(should);
                if (hitResult != null) {
                    if (hitResult.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                        BlockPos blockpos = blockHitResult.getBlockPos();
                        bowman.setStrategicFirePos(blockpos);
                    }
                }
            }
        }
    }

    /**
     * Map-driven strategic fire: enable strategic fire and set the target position
     * directly for every {@link IStrategicFire} recruit in {@code list}. Used by world-map
     * "open fire" actions where the target comes over the network.
     */
    public static void onStrategicFireCommandAt(UUID player_uuid, List<AbstractRecruitEntity> list, UUID group,
                                                BlockPos targetPos, boolean should) {
        if (targetPos == null) return;
        for (AbstractRecruitEntity recruit : list) {
            if (!recruit.isEffectedByCommand(player_uuid, group)) continue;
            if (recruit instanceof IStrategicFire bowman) {
                bowman.setShouldStrategicFire(should);
                bowman.setStrategicFirePos(targetPos);
            }
        }
    }

    public static void openCommandScreen(Player player) {
        if (player instanceof ServerPlayer) {
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {

                @Override
                public @NotNull Component getDisplayName() {
                    return Component.literal("command_screen");
                }

                @Override
                public @NotNull AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new CommandMenu(i, playerEntity);
                }
            }, packetBuffer -> {packetBuffer.writeUUID(player.getUUID());});
        } else {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageCommandScreen(player));
        }
    }
    @SubscribeEvent
    public void onServerPlayerTick(TickEvent.PlayerTickEvent event){
        if(event.player instanceof ServerPlayer serverPlayer && serverPlayer.tickCount % 20 == 0){
            MovementFormationCommandService.onServerPlayerTick(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MovementFormationCommandService.initializePlayerCommandState(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        MovementFormationCommandService.copyPersistentCommandPreferences(event.getOriginal(), event.getEntity());
    }

    public static int getSavedFormation(Player player) {
        return MovementFormationCommandService.getSavedFormation(player);
    }

    public static void saveFormation(Player player, int formation) {
        MovementFormationCommandService.saveFormation(player, formation);
    }


    public static void saveUUIDList(Player player, String key, Collection<UUID> uuids) {
        MovementFormationCommandService.saveUUIDList(player, key, uuids);
    }

    public static List<UUID> getSavedUUIDList(Player player, String key) {
        return MovementFormationCommandService.getSavedUUIDList(player, key);
    }

    public static int[] getSavedFormationPos(Player player) {
        return MovementFormationCommandService.getSavedFormationPos(player);
    }

    public static void saveFormationPos(Player player, int[] pos) {
        MovementFormationCommandService.saveFormationPos(player, pos);
    }

    public static void saveFormationCenter(Player player, Vec3 center) {
        MovementFormationCommandService.saveFormationCenter(player, center);
    }

    public static Vec3 getSavedFormationCenter(Player player) {
        return MovementFormationCommandService.getSavedFormationCenter(player);
    }

    public static boolean handleRecruiting(Player player, RecruitsGroup group, AbstractRecruitEntity recruit, boolean message){
        return RecruitCommandActionService.handleRecruiting(player, group, recruit, message);
    }

    public static void onMountButton(UUID player_uuid, AbstractRecruitEntity recruit, UUID mount_uuid, UUID group) {
        RecruitCommandActionService.onMountButton(player_uuid, recruit, mount_uuid, group);
    }

    public static void onDismountButton(UUID player_uuid, AbstractRecruitEntity recruit, UUID group) {
        RecruitCommandActionService.onDismountButton(player_uuid, recruit, group);
    }

    public static void onProtectButton(UUID player_uuid, AbstractRecruitEntity recruit, UUID protect_uuid, UUID group) {
        RecruitCommandActionService.onProtectButton(player_uuid, recruit, protect_uuid, group);
    }

    public static void onClearTargetButton(UUID player_uuid, AbstractRecruitEntity recruit, UUID group) {
        RecruitCommandActionService.onClearTargetButton(player_uuid, recruit, group);
    }

    public static void onClearUpkeepButton(UUID player_uuid, AbstractRecruitEntity recruit, UUID group) {
        RecruitCommandActionService.onClearUpkeepButton(player_uuid, recruit, group);
    }
    public static void onUpkeepCommand(UUID player_uuid, AbstractRecruitEntity recruit, UUID group, boolean isEntity, UUID entity_uuid, BlockPos blockPos) {
        RecruitCommandActionService.onUpkeepCommand(player_uuid, recruit, group, isEntity, entity_uuid, blockPos);
    }

    public static void onShieldsCommand(Player player, UUID player_uuid, AbstractRecruitEntity recruit, UUID group, boolean shields) {
        RecruitCommandActionService.onShieldsCommand(player, player_uuid, recruit, group, shields);
    }

    public static void onShieldsCommand(ServerPlayer serverPlayer, UUID player_uuid, AbstractRecruitEntity recruit, UUID group, boolean shields) {
        onShieldsCommand((Player) serverPlayer, player_uuid, recruit, group, shields);
    }

    public static void onRangedFireCommand(ServerPlayer serverPlayer, UUID player_uuid, AbstractRecruitEntity recruit, UUID group, boolean should) {
        RecruitCommandActionService.onRangedFireCommand(serverPlayer, player_uuid, recruit, group, should);
    }

    public static void onRestCommand(ServerPlayer serverPlayer, UUID player_uuid, AbstractRecruitEntity recruit, UUID group, boolean should) {
        RecruitCommandActionService.onRestCommand(serverPlayer, player_uuid, recruit, group, should);
    }
}
