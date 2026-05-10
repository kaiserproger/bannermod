package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.api.event.RecruitEvent;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RecruitLifecycleService {
    private RecruitLifecycleService() {
    }

    static void disband(AbstractRecruitEntity recruit, @Nullable Player player, boolean keepTeam, boolean increaseCost, @Nullable Component dismissMessage) {
        if (!recruit.getCommandSenderWorld().isClientSide()) {
            RecruitEvent.Dismissed dismissEvent = new RecruitEvent.Dismissed(recruit, player, keepTeam);
            NeoForge.EVENT_BUS.post(dismissEvent);
            if (dismissEvent.isCanceled()) return;
        }
        if (player != null && dismissMessage != null) player.sendSystemMessage(dismissMessage);
        recruit.setTarget(null);
        recruit.setIsOwned(false);
        if (increaseCost) recruit.recalculateCost();
        if (recruit.getCommandSenderWorld().isClientSide()) return;
        RecruitEvents.playerUnitManager().removeRecruits(recruit.getOwnerUUID(), 1);
        recruit.setOwnerUUID(Optional.empty());
        if (recruit.getGroup() != null) {
            RecruitEvents.groupsManager().removeMember(recruit.getGroup(), recruit.getUUID(), (ServerLevel) recruit.getCommandSenderWorld());
            recruit.setGroupUUID(null);
        }
    }

    static boolean hire(AbstractRecruitEntity recruit, Player player, @Nullable RecruitsGroup group, boolean message, Component recruitingMax, List<Component> recruitedMessages) {
        if (!recruit.getCommandSenderWorld().isClientSide()) {
            RecruitEvent.Hired hireEvent = new RecruitEvent.Hired(recruit, player);
            NeoForge.EVENT_BUS.post(hireEvent);
            if (hireEvent.isCanceled()) return false;
        }
        Team ownerTeam = player.getTeam();
        String stringId = ownerTeam != null ? ownerTeam.getName() : "";
        if (!RecruitEvents.playerUnitManager().canPlayerRecruit(stringId, player.getUUID())) {
            player.sendSystemMessage(recruitingMax);
            return false;
        }
        recruit.makeHireSound();
        RecruitUpkeepService.resetPaymentTimer(recruit);
        recruit.setOwnerUUID(Optional.of(player.getUUID()));
        recruit.setIsOwned(true);
        recruit.stopNavigation();
        recruit.setTarget(null);
        recruit.setFollowState(2);
        recruit.setAggroState(0);
        if (group != null) recruit.setGroupUUID(group.getUUID());
        recruit.despawnTimer = -1;
        if (!recruit.getCommandSenderWorld().isClientSide()) {
            RecruitEvents.playerUnitManager().addRecruits(player.getUUID(), 1);
            if (group != null) {
                RecruitEvents.groupsManager().addMember(group.getUUID(), recruit.getUUID(), (ServerLevel) recruit.getCommandSenderWorld());
                RecruitEvents.groupsManager().broadCastGroupsToPlayer(player);
            }
        }
        if (message && !recruitedMessages.isEmpty()) {
            player.sendSystemMessage(recruitedMessages.get(recruit.getRandom().nextInt(recruitedMessages.size())));
        }
        return true;
    }

    static void assignSpawnedToPlayer(AbstractRecruitEntity recruit, Player player, @Nullable RecruitsGroup group) {
        RecruitUpkeepService.resetPaymentTimer(recruit);
        recruit.setOwnerUUID(Optional.of(player.getUUID()));
        recruit.setIsOwned(true);
        recruit.stopNavigation();
        recruit.setTarget(null);
        recruit.setFollowState(2);
        recruit.setAggroState(0);
        if (group != null) {
            recruit.setGroupUUID(group.getUUID());
        }
        recruit.despawnTimer = -1;
        if (!recruit.getCommandSenderWorld().isClientSide()) {
            RecruitEvents.playerUnitManager().addRecruits(player.getUUID(), 1);
            if (group != null) {
                RecruitEvents.groupsManager().addMember(group.getUUID(), recruit.getUUID(), (ServerLevel) recruit.getCommandSenderWorld());
                RecruitEvents.groupsManager().broadCastGroupsToPlayer(player);
            }
        }
    }

    static void onDeath(AbstractRecruitEntity recruit, DamageSource dmg, Component deathMessage) {
        recruit.superDie(dmg);
        if (!recruit.isDeadOrDying() || recruit.getCommandSenderWorld().isClientSide()) return;
        if (recruit.getCommandSenderWorld().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SHOWDEATHMESSAGES) && recruit.getOwner() instanceof ServerPlayer) {
            recruit.getOwner().sendSystemMessage(deathMessage);
        }
        if (recruit.getGroup() != null) {
            RecruitEvents.groupsManager().removeMember(recruit.getGroup(), recruit.getUUID(), (ServerLevel) recruit.getCommandSenderWorld());
        }
        if (recruit.isOwned()) {
            RecruitEvents.playerUnitManager().removeRecruits(recruit.getOwnerUUID(), 1);
        }
    }

    static void updateTeam(AbstractRecruitEntity recruit) {
        if (recruit.isOwned() && !recruit.getCommandSenderWorld().isClientSide()) {
            Player owner = recruit.getOwner();
            if (owner != null) {
                Team recruitTeam = recruit.getTeam();
                Team ownerTeam = owner.getTeam();
                recruit.needsTeamUpdate = false;
            }
        }
    }

    static void updateGroup(AbstractRecruitEntity recruit) {
        if (recruit.getCommandSenderWorld().isClientSide()) return;
        recruit.needsGroupUpdate = false;
        if (recruit.getGroup() == null) return;
        UUID raw = recruit.getGroup();
        UUID resolved = RecruitEvents.groupsManager().resolveGroup(raw);
        UUID finalGroup = RecruitEvents.groupsManager().resolveRecruit(recruit.getUUID(), resolved);
        if (!finalGroup.equals(raw)) recruit.setGroupUUID(finalGroup);
        RecruitsGroup group = RecruitEvents.groupsManager().getGroup(finalGroup);
        if (group == null || !group.members.contains(recruit.getUUID())) {
            recruit.setGroupUUID(null);
            return;
        }
        if (group.disbandContext != null && group.disbandContext.disband) {
            recruit.disband(null, group.disbandContext.keepTeam, group.disbandContext.increaseCost);
            recruit.needsTeamUpdate = true;
            return;
        }
        if (recruit.isOwned() && !recruit.getOwnerUUID().equals(group.getPlayerUUID())) {
            assignToPlayer(recruit, group.getPlayerUUID(), group.getUUID());
        }
        recruit.needsTeamUpdate = true;
    }

    static void assignToPlayer(AbstractRecruitEntity recruit, UUID newOwner, UUID newGroupUUID) {
        RecruitsGroup currentGroup = RecruitEvents.groupsManager().getGroup(recruit.getGroup());
        if (currentGroup != null) currentGroup.removeMember(recruit.getUUID());
        recruit.setGroupUUID(newGroupUUID);
        RecruitsGroup newGroup = RecruitEvents.groupsManager().getGroup(newGroupUUID);
        recruit.disband(null, false, false);
        recruit.setOwnerUUID(Optional.of(newOwner));
        if (recruit.getOwner() != null) {
            recruit.hire(recruit.getOwner(), newGroup, true);
            recruit.setFollowState(1);
        }
    }
}
