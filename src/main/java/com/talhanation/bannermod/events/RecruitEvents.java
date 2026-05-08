package com.talhanation.bannermod.events;
import com.talhanation.bannermod.bootstrap.BannerModMain;

import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.governance.runtime.RecruitGovernorWorkflow;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.ICompanion;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import com.talhanation.bannermod.inventory.military.PromoteContainer;
import com.talhanation.bannermod.network.messages.military.MessageOpenPromoteScreen;
import com.talhanation.bannermod.persistence.military.*;
import com.talhanation.bannermod.events.RecruitEvent;
import com.talhanation.bannermod.combat.runtime.RecruitCombatRuntime;
import com.talhanation.bannermod.entity.military.runtime.RecruitWorldLifecycleService;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.scores.Team;
import com.talhanation.bannermod.network.compat.BannerModNetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RecruitEvents {
    static final Map<ServerLevel, RecruitsPatrolSpawn> RECRUIT_PATROL = new HashMap<>();
    static final Map<ServerLevel, PillagerPatrolSpawn> PILLAGER_PATROL = new HashMap<>();
    private static RecruitsPlayerUnitManager recruitsPlayerUnitManager;
    private static RecruitsGroupsManager recruitsGroupsManager;

    private static MinecraftServer server;
    public static HashMap<Integer, EntityType<? extends AbstractRecruitEntity>> entitiesByProfession = new HashMap<>() {
        {
            put(0, ModEntityTypes.MESSENGER.get());
            put(1, ModEntityTypes.SCOUT.get());
            put(2, ModEntityTypes.PATROL_LEADER.get());
            put(3, ModEntityTypes.CAPTAIN.get());
        }
    };

    public static RecruitsPlayerUnitManager playerUnitManager() {
        return recruitsPlayerUnitManager;
    }

    public static RecruitsGroupsManager groupsManager() {
        return recruitsGroupsManager;
    }

    public static MinecraftServer server() {
        return server;
    }

    static void installRuntime(MinecraftServer currentServer,
                               RecruitsPlayerUnitManager currentPlayerUnitManager,
                               RecruitsGroupsManager currentGroupsManager) {
        server = currentServer;
        recruitsPlayerUnitManager = currentPlayerUnitManager;
        recruitsGroupsManager = currentGroupsManager;
    }

    public static void promoteRecruit(AbstractRecruitEntity recruit, int profession, String name, ServerPlayer player) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) {
            return;
        }

        // RecruitEvent.Promoted feuern – cancelable
        RecruitEvent.Promoted promoteEvent = new RecruitEvent.Promoted(recruit, profession, name, player);
        NeoForge.EVENT_BUS.post(promoteEvent);
        if (promoteEvent.isCanceled()) return;

        if (profession == 6) {
            RecruitGovernorWorkflow.tryPromoteRecruit(recruit, name, player);
            return;
        }

        if (!entitiesByProfession.containsKey(profession)) {
            player.sendSystemMessage(Component.translatable("chat.bannermod.promote.unsupported_profession"));
            return;
        }

        EntityType<? extends AbstractRecruitEntity> companionType = entitiesByProfession.get(profession);
        if (companionType == null) {
            player.sendSystemMessage(Component.translatable("chat.bannermod.promote.unsupported_profession"));
            return;
        }
        AbstractRecruitEntity abstractRecruit = companionType.create(recruit.getCommandSenderWorld());
        if (abstractRecruit instanceof ICompanion companion) {
            abstractRecruit.setCustomName(Component.literal(name));
            abstractRecruit.copyPosition(recruit);
            companion.applyRecruitValues(recruit);
            companion.setOwnerName(player.getName().getString());

            recruit.discard();
            abstractRecruit.getCommandSenderWorld().addFreshEntity(abstractRecruit);
        }
    }

    public static void openPromoteScreen(Player player, AbstractRecruitEntity recruit) {
        if (player instanceof ServerPlayer) {
            BannerModNetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return recruit.getName();
                }

                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new PromoteContainer(i, playerEntity, recruit);
                }
            }, packetBuffer -> {
                packetBuffer.writeUUID(recruit.getUUID());
            });
        } else {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageOpenPromoteScreen(player, recruit.getUUID()));
        }
    }

    public static void openGovernorScreen(Player player, AbstractRecruitEntity recruit) {
        RecruitGovernorWorkflow.openGovernorScreen(player, recruit);
    }

    public static void syncGovernorScreen(ServerPlayer player, AbstractRecruitEntity recruit) {
        RecruitGovernorWorkflow.syncGovernorScreen(player, recruit);
    }

    public static void updateGovernorPolicy(ServerPlayer player, AbstractRecruitEntity recruit, BannerModGovernorPolicy policy, int value) {
        RecruitGovernorWorkflow.updateGovernorPolicy(player, recruit, policy, value);
    }

    public static void handleGroupBackwardCompatibility(AbstractRecruitEntity recruit, int oldGroupNumber) {
        RecruitWorldLifecycleService.handleLegacyGroup(recruit, oldGroupNumber, server, recruitsGroupsManager);
    }

    public static void syncGovernorMutationRefresh(ServerLevel level, RecruitsClaim claim) {
        RecruitGovernorWorkflow.syncGovernorMutationRefresh(level, claim);
    }

    public static void serverSideRecruitGroup(ServerLevel level){
        RecruitWorldLifecycleService.markRecruitsForGroupRefresh(level, recruitsGroupsManager);
    }

    public static boolean canAttack(LivingEntity attacker, LivingEntity target) {
        return RecruitCombatRuntime.canAttack(attacker, target);
    }

    public static boolean canAttackAnimal(LivingEntity attacker, Animal animal) {
        return RecruitCombatRuntime.canAttackAnimal(attacker, animal);
    }

    public static boolean canAttackPlayer(LivingEntity attacker, Player player) {
        return RecruitCombatRuntime.canAttackPlayer(attacker, player);
    }

    public static boolean canAttackRecruit(LivingEntity attacker, AbstractRecruitEntity targetRecruit) {
        return RecruitCombatRuntime.canAttackRecruit(attacker, targetRecruit);
    }

    public static boolean isAlly(Team team1, Team team2) {
        return RecruitCombatRuntime.isAlly(team1, team2);
    }

    public static boolean isEnemy(Team team1, Team team2) {
        return RecruitCombatRuntime.isEnemy(team1, team2);
    }

    public static boolean isEnemy(LivingEntity attacker, LivingEntity target) {
        return RecruitCombatRuntime.isEnemy(attacker, target);
    }

    public static boolean isNeutral(Team team1, Team team2) {
        return RecruitCombatRuntime.isNeutral(team1, team2);
    }

    public static boolean canHarmTeam(LivingEntity attacker, LivingEntity target) {
        return RecruitCombatRuntime.canHarmTeam(attacker, target);
    }

    public static boolean canHarmTeamNoFriendlyFire(LivingEntity attacker, LivingEntity target) {
        return RecruitCombatRuntime.canHarmTeamNoFriendlyFire(attacker, target);
    }

}
