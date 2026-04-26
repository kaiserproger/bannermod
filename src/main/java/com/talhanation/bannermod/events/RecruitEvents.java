package com.talhanation.bannermod.events;
import com.talhanation.bannermod.bootstrap.BannerModMain;

import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.compat.IWeapon;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.util.DelayedExecutor;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.ICompanion;
import com.talhanation.bannermod.entity.military.MessengerEntity;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import com.talhanation.bannermod.inventory.military.PromoteContainer;
import com.talhanation.bannermod.network.messages.military.MessageOpenPromoteScreen;
import com.talhanation.bannermod.persistence.military.*;
import com.talhanation.bannermod.events.RecruitEvent;
import com.talhanation.bannermod.events.runtime.RecruitCombatRuntime;
import com.talhanation.bannermod.events.runtime.RecruitWorldLifecycleService;
import com.talhanation.bannermod.ai.pathfinding.async.TrueAsyncPathfindingRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RecruitEvents {
    private static final Map<ServerLevel, RecruitsPatrolSpawn> RECRUIT_PATROL = new HashMap<>();
    private static final Map<ServerLevel, PillagerPatrolSpawn> PILLAGER_PATROL = new HashMap<>();
    public static RecruitsPlayerUnitManager recruitsPlayerUnitManager;
    public static RecruitsGroupsManager recruitsGroupsManager;

    public static MinecraftServer server;
    public static HashMap<Integer, EntityType<? extends AbstractRecruitEntity>> entitiesByProfession = new HashMap<>() {
        {
            put(0, ModEntityTypes.MESSENGER.get());
            put(1, ModEntityTypes.SCOUT.get());
            put(2, ModEntityTypes.PATROL_LEADER.get());
            put(3, ModEntityTypes.CAPTAIN.get());
        }
    };

    public static void promoteRecruit(AbstractRecruitEntity recruit, int profession, String name, ServerPlayer player) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) {
            return;
        }

        // RecruitEvent.Promoted feuern – cancelable
        RecruitEvent.Promoted promoteEvent = new RecruitEvent.Promoted(recruit, profession, name, player);
        MinecraftForge.EVENT_BUS.post(promoteEvent);
        if (promoteEvent.isCanceled()) return;

        if (profession == 6) {
            RecruitGovernorWorkflow.tryPromoteRecruit(recruit, name, player);
            return;
        }

        EntityType<? extends AbstractRecruitEntity> companionType = entitiesByProfession.get(profession);
        if (companionType == null) {
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
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
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

    public static void updateGovernorAutoManage(ServerPlayer player, AbstractRecruitEntity recruit, boolean autoManage) {
        RecruitGovernorWorkflow.updateGovernorAutoManage(player, recruit, autoManage);
    }

    public static void openContractBoard(ServerPlayer player, AbstractRecruitEntity recruit) {
        RecruitGovernorWorkflow.openContractBoard(player, recruit);
    }

    public static void acceptContract(ServerPlayer player, AbstractRecruitEntity recruit, java.util.UUID contractId) {
        RecruitGovernorWorkflow.acceptContract(player, recruit, contractId);
    }

    public static void cancelContract(ServerPlayer player, AbstractRecruitEntity recruit, java.util.UUID contractId) {
        RecruitGovernorWorkflow.cancelContract(player, recruit, contractId);
    }

    public static void pinContract(ServerPlayer player, AbstractRecruitEntity recruit, java.util.UUID contractId, boolean pinned) {
        RecruitGovernorWorkflow.pinContract(player, recruit, contractId, pinned);
    }

    public static void setContractMaxReward(ServerPlayer player, AbstractRecruitEntity recruit, int maxReward) {
        RecruitGovernorWorkflow.setContractMaxReward(player, recruit, maxReward);
    }

    public static void handleGroupBackwardCompatibility(AbstractRecruitEntity recruit, int oldGroupNumber) {
        RecruitWorldLifecycleService.handleLegacyGroup(recruit, oldGroupNumber, server, recruitsGroupsManager);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        RecruitWorldLifecycleService.RecruitManagers managers = RecruitWorldLifecycleService.initializeManagers(server);
        recruitsPlayerUnitManager = managers.playerUnitManager();
        recruitsGroupsManager = managers.groupsManager();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // start() hier und nicht in ServerStartingEvent:
        // ServerStartingEvent feuert bevor die Levels initialisiert sind — server.overworld()
        // kann dort eine NPE werfen und würde start() nie erreichen lassen.
        // ServerStartedEvent garantiert dass alle Levels geladen sind und der Executor
        // vor dem ersten Entity-Tick bereit ist.
        com.talhanation.bannermod.ai.pathfinding.AsyncPathProcessor.start();
    }


    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RecruitWorldLifecycleService.saveManagers(server, recruitsPlayerUnitManager, recruitsGroupsManager);

        // Fix: Async-Executor sauber herunterfahren damit der Server nicht hängt
        com.talhanation.bannermod.ai.pathfinding.AsyncPathProcessor.shutdown();
        TrueAsyncPathfindingRuntime.instance().shutdown();
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event){
        RecruitWorldLifecycleService.saveManagers(server, recruitsPlayerUnitManager, recruitsGroupsManager);
    }

    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()) return;

        if(event.getEntity() instanceof Player player){
            if (player instanceof ServerPlayer serverPlayer) {
                RecruitWorldLifecycleService.syncPlayerJoin(serverPlayer, recruitsPlayerUnitManager, recruitsGroupsManager);
            }
        }
    }

    @SubscribeEvent
    public void onTeleportEvent(EntityTeleportEvent event) {
        RecruitWorldLifecycleService.teleportFollowingRecruits(event);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        RecruitWorldLifecycleService.tickLevel(event, RECRUIT_PATROL, PILLAGER_PATROL);
    }

    public static void serverSideRecruitGroup(ServerLevel level){
        RecruitWorldLifecycleService.markRecruitsForGroupRefresh(level, recruitsGroupsManager);
    }

    private static final Set<Projectile> canceledProjectiles = new HashSet<>();

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        RecruitCombatRuntime.onProjectileImpact(event);
    }

    @SubscribeEvent
    public void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        RecruitCombatRuntime.onEntityLeaveWorld(event);
    }

    @SubscribeEvent
    public void onPlayerInteractWithCaravan(PlayerInteractEvent.EntityInteract entityInteract) {
        RecruitCombatRuntime.onPlayerInteractWithCaravan(entityInteract);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        RecruitCombatRuntime.onLivingHurt(event);
    }

    private static final double DAMAGE_THRESHOLD_PERCENTAGE = 0.75;

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        RecruitCombatRuntime.onLivingAttack(event);
    }

    private void handleSignificantDamage(LivingEntity attacker, LivingEntity target, double damage, ServerLevel level) {
        Team attackerTeam = attacker.getTeam();
        Team targetTeam = target.getTeam();

        if (attackerTeam == null || targetTeam == null) return;


        double newHealth = target.getHealth() - damage;
        double damageThreshold = target.getMaxHealth() * DAMAGE_THRESHOLD_PERCENTAGE;


        if (newHealth < damageThreshold) {
            setTeamsAsEnemies(attackerTeam, targetTeam, level);
        }
    }

    private void setTeamsAsEnemies(Team attackerTeam, Team targetTeam, ServerLevel level) {
    }

    @SubscribeEvent
    public void onHorseJoinWorld(EntityJoinLevelEvent event) {
        RecruitWorldLifecycleService.ensureHorseGoal(event.getEntity());
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

    public static boolean isNeutral(Team team1, Team team2) {
        return RecruitCombatRuntime.isNeutral(team1, team2);
    }

    public static boolean canHarmTeam(LivingEntity attacker, LivingEntity target) {
        return RecruitCombatRuntime.canHarmTeam(attacker, target);
    }

    public static boolean canHarmTeamNoFriendlyFire(LivingEntity attacker, LivingEntity target) {
        return RecruitCombatRuntime.canHarmTeamNoFriendlyFire(attacker, target);
    }

    @SubscribeEvent
    public void onRecruitDeath(LivingDeathEvent event) {
        RecruitCombatRuntime.onRecruitDeath(event);
    }

    private final List<AbstractArrow> trackedArrows = new ArrayList<>();
    private int tickCounter = 0;

    @SubscribeEvent
    public void onWorldTickArrowCleaner(TickEvent.LevelTickEvent event) {//for 1.18 and 1.19 use TickEvent.WorldTickEvent
        RecruitCombatRuntime.onWorldTickArrowCleaner(event);
    }
}
