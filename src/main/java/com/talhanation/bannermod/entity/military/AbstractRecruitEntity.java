package com.talhanation.bannermod.entity.military;
import com.talhanation.bannermod.bootstrap.BannerModMain;
//ezgi&talha kantar

import com.talhanation.bannermod.citizen.CitizenCore;
import com.talhanation.bannermod.citizen.CitizenPersistenceBridge;
import com.talhanation.bannermod.citizen.CitizenRole;
import com.talhanation.bannermod.entity.citizen.AbstractCitizenEntity;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.events.*;
import com.talhanation.bannermod.events.RecruitEvent;
import com.talhanation.bannermod.compat.IWeapon;
import com.talhanation.bannermod.config.RecruitsClientConfig;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.ai.home.PathfindHomeGoal;
import com.talhanation.bannermod.ai.military.*;
import com.talhanation.bannermod.ai.military.async.AsyncManager;
import com.talhanation.bannermod.ai.military.async.AsyncTaskWithCallback;
import com.talhanation.bannermod.ai.military.controller.RecruitCommandStateTransitions;
import com.talhanation.bannermod.ai.military.compat.BlockWithWeapon;
import com.talhanation.bannermod.ai.military.navigation.RecruitPathNavigation;
import com.talhanation.bannermod.ai.military.navigation.RecruitsOpenDoorGoal;
import com.talhanation.bannermod.registry.military.ModItems;
import com.talhanation.bannermod.inventory.military.DebugInvMenu;
import com.talhanation.bannermod.inventory.military.RecruitHireMenu;
import com.talhanation.bannermod.inventory.military.RecruitInventoryMenu;
import com.talhanation.bannermod.network.messages.military.*;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalRelations;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import com.talhanation.bannermod.network.compat.BannerModNetworkHooks;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbstractRecruitEntity extends AbstractCitizenEntity implements RecruitCommandAccess, RecruitOwnershipAccess, RecruitUpkeepAccess, RecruitEquipmentAccess, RecruitProgressionAccess {
    static {
        RecruitStateAccess.ensureAccessorsRegistered();
    }

    public int blockCoolDown;
    public boolean needsTeamUpdate = true;
    public boolean needsGroupUpdate = true;
    public boolean forcedUpkeep;
    public int dismount = 0;
    public int upkeepTimer = 0;
    public int mountTimer = 0;
    public int despawnTimer = -1;
    public boolean reachedMovePos;
    public int attackCooldown = 0;
    public int paymentTimer;
    public boolean rotate;
    public float ownerRot;
    public int rotateTicks;
    public int formationPos = -1;
    private int maxFallDistance;
    private final int tickOffset = (int)(System.nanoTime() % 20);
    public Vec3 holdPosVec;
    public boolean isInFormation;
    public boolean needsColorUpdate = true;
    public float moveSpeed = 1;
    public TargetingConditions targetingConditions;
    /** Last recruit tickCount at which a live combat target was cleared (dead/removed). */
    public int lastTargetLossTick = Integer.MIN_VALUE;
    /** Last recruit tickCount at which this recruit migrated slots via gap-fill. */
    public int lastFormationGapFillTick = Integer.MIN_VALUE;
    /** Stage 4.B: last tick at which {@link #cachedCohesion} was recomputed (cache TTL). */
    public int cachedCohesionTick = Integer.MIN_VALUE;
    /** Stage 4.B: cached FormationCohesion decision for this recruit. */
    public boolean cachedCohesion;
    /** Stage 4.C: true while the recruit is bracing against a cavalry charge. */
    public boolean isBracing;
    /** Last tick this recruit fanned out a protect-target reaction after being hit. */
    public int lastProtectTargetPropagationTick = Integer.MIN_VALUE;
    private final CitizenCore citizenCore = RecruitCitizenBridge.createCore(this);
    private final com.talhanation.bannermod.entity.military.perks.PerkProgress perkProgress
            = new com.talhanation.bannermod.entity.military.perks.PerkProgress();

    public AbstractRecruitEntity(EntityType<? extends AbstractInventoryEntity> entityType, Level world) {
        super(entityType, world);
        this.xpReward = 6;
        this.navigation = this.createNavigation(world);
        this.targetingConditions = TargetingConditions.forCombat().ignoreInvisibilityTesting().selector(this::shouldAttack);
        this.setMaxFallDistance(1);
    }

    ///////////////////////////////////NAVIGATION/////////////////////////////////////////
    @NotNull
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new RecruitPathNavigation(this, level);
    }

    public @NotNull PathNavigation getNavigation() {
        return super.getNavigation();
    }

    public CitizenCore getCitizenCore() {
        return this.citizenCore;
    }

    /**
     * Server-authoritative perk store (SKILLTREE-002 phase 1). Serialized via
     * {@link RecruitPersistenceBridge}; combat hooks land in SKILLTREE-003.
     */
    public com.talhanation.bannermod.entity.military.perks.PerkProgress getPerkProgress() {
        return this.perkProgress;
    }

    @Override
    public CitizenRole getCitizenRole() {
        return CitizenRole.CONTROLLED_RECRUIT;
    }

    public void rideTick() {
        super.rideTick();
    }

    public double getMyRidingOffset() {
        return -0.35D;
    }

    public int getMaxFallDistance() {
        return maxFallDistance;
    }

    public void setMaxFallDistance(int x){
        this.maxFallDistance = x;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    ///////////////////////////////////TICK/////////////////////////////////////////

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        if(this.rotateTicks > 0 && this.getNavigation().isDone()) {
            this.yBodyRot = this.ownerRot;
            this.yHeadRot = this.ownerRot;
            return 0;
        }
        // Step 1.D: slow body-yaw while in formation under LINE_HOLD / SHIELD_WALL.
        // Head yaw is left alone so recruits can still look at threats.
        float beforeBody = this.yBodyRot;
        float delta = super.tickHeadTurn(yRot, animStep);
        if (this.isInFormation) {
            float limit = com.talhanation.bannermod.ai.military.FormationYawPolicy.perTickBodyYawLimitDegrees(this.getCombatStance());
            if (!Float.isNaN(limit)) {
                this.yBodyRot = com.talhanation.bannermod.ai.military.FormationYawPolicy.clampBodyYaw(beforeBody, this.yBodyRot, limit);
            }
        }
        if (shouldLockFormationFacing()) {
            float limit = com.talhanation.bannermod.ai.military.FormationYawPolicy.perTickBodyYawLimitDegrees(this.getCombatStance());
            if (Float.isNaN(limit)) {
                limit = com.talhanation.bannermod.ai.military.FormationYawPolicy.LINE_HOLD_YAW_DELTA_LIMIT_DEG;
            }
            float lockedBody = com.talhanation.bannermod.ai.military.FormationYawPolicy.clampBodyYaw(this.yBodyRot, this.ownerRot, limit);
            this.yBodyRot = lockedBody;
            this.yHeadRot = lockedBody;
            this.setYRot(lockedBody);
            // Keep head pitch neutral while holding formation without a combat target.
            this.setXRot(0.0F);
            this.xRotO = 0.0F;
        }
        return delta;
    }

    private boolean shouldLockFormationFacing() {
        if (!this.isInFormation || !this.getShouldHoldPos()) {
            return false;
        }
        if (this.getCombatStance() == com.talhanation.bannermod.ai.military.CombatStance.LOOSE) {
            return false;
        }
        LivingEntity target = this.getTarget();
        return target == null || !target.isAlive() || target.isRemoved();
    }

    private boolean canObserveWhileInFormation(@Nullable Entity observed) {
        if (observed == null) {
            return false;
        }
        if (shouldSuppressIdleLook()) {
            return false;
        }
        if (!shouldLockFormationFacing()) {
            return true;
        }
        if (Math.abs(observed.getEyeY() - this.getEyeY()) > 2.0D) {
            return false;
        }
        return com.talhanation.bannermod.ai.military.ShieldBlockGeometry.isInFrontCone(
                this.ownerRot,
                this.getX(), this.getZ(),
                observed.getX(), observed.getZ(),
                50.0F
        );
    }

    private boolean shouldSuppressIdleLook() {
        int followState = this.getFollowState();
        // Hold-position modes (2/3) should not run ambient look-at-player behavior.
        // This keeps the unit from breaking visual discipline by tracking nearby players.
        return this.getShouldHoldPos() || this.isInFormation || followState == 2 || followState == 3;
    }

    /** Step 1.A: current combat stance. Defaults to LOOSE. */
    public com.talhanation.bannermod.ai.military.CombatStance getCombatStance() {
        return RecruitStateAccess.getCombatStance(this);
    }

    /** Step 1.A: set current combat stance. Null is coerced to LOOSE. */
    public void setCombatStance(com.talhanation.bannermod.ai.military.CombatStance stance) {
        RecruitStateAccess.setCombatStance(this, stance);
    }

    public int getBetterCombatAttackTicks() {
        return RecruitStateAccess.getBetterCombatAttackTicks(this);
    }

    public int getBetterCombatAttackDuration() {
        return RecruitStateAccess.getBetterCombatAttackDuration(this);
    }

    public int getBetterCombatAttackUpswing() {
        return RecruitStateAccess.getBetterCombatAttackUpswing(this);
    }

    public int getBetterCombatAttackShape() {
        return RecruitStateAccess.getBetterCombatAttackShape(this);
    }

    public void setBetterCombatAttackPresentation(int ticks, int duration, int upswing, int shape) {
        RecruitStateAccess.setBetterCombatAttackPresentation(this, ticks, duration, upswing, shape);
    }

    // @Override
    public void aiStep(){
        super.aiStep();
        updateSwingTime();
        updateShield();

        if (this.getCommandSenderWorld().isClientSide()) return;

        RecruitRuntimeLoop.aiStep(this);
        this.getCitizenRoleController().onServerAiStep(this);

    }
    public void tick() {
        super.tick();
        if(this.level().isClientSide()) return;

        RecruitRuntimeLoop.tick(this);
        this.getCitizenRoleController().onServerTick(this);

    }

    public void searchForTargets() {
        RecruitRuntimeLoop.searchForTargets(this);
    }

    protected NearbyCombatCandidates scanNearbyCombatCandidates(ServerLevel serverLevel, double radius) {
        return RecruitCombatTargeting.scanNearbyCombatCandidates(this, serverLevel, radius);
    }

    protected List<LivingEntity> filterCombatCandidates(List<LivingEntity> candidates, Predicate<LivingEntity> filter, boolean sortByDistance) {
        return RecruitCombatTargeting.filterCombatCandidates(this, candidates, filter, sortByDistance);
    }

    public boolean canAssignCombatTarget(@Nullable LivingEntity target) {
        return RecruitCombatTargeting.canAssignCombatTarget(this, target);
    }

    public boolean assignOrderedCombatTarget(@Nullable LivingEntity target) {
        return RecruitCombatTargeting.assignOrderedCombatTarget(this, target);
    }

    public boolean assignReactiveCombatTarget(@Nullable LivingEntity target) {
        return RecruitCombatTargeting.assignReactiveCombatTarget(this, target);
    }

    public static void resetTargetSearchProfiling() {
        RecruitRuntimeLoop.resetTargetSearchProfiling();
    }

    protected static record NearbyCombatCandidates(int observedCount, List<LivingEntity> candidates) {
    }

    public static TargetSearchProfilingSnapshot targetSearchProfilingSnapshot() {
        return RecruitRuntimeLoop.targetSearchProfilingSnapshot();
    }

    public record TargetSearchProfilingSnapshot(
            long searchOpportunities,
            long totalSearches,
            long asyncSearches,
            long syncSearches,
            long candidateEntitiesObserved,
            long targetsAssigned,
            long lodSkippedSearches,
            long lodFullTierTicks,
            long lodReducedTierTicks,
            long lodShedTierTicks
    ) {
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance diff, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag nbt) {
        return RecruitSpawnService.prepareBaseRecruitSpawn(this, world, spawnData);
    }

    protected final SpawnGroupData finishRecruitLeafSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, SpawnGroupData spawnData, boolean canOpenDoors, boolean enchantEquipment) {
        return RecruitSpawnService.finishLeafRecruitSpawn(this, world, difficulty, spawnData, canOpenDoors, enchantEquipment);
    }

    void rebuildSpawnNavigation(ServerLevelAccessor world) {
        this.createNavigation(world.getLevel());
    }

    void enableRecruitSpawnDoors() {
        if (this.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }
    }

    void applyRecruitSpawnEnchantments(ServerLevelAccessor world, DifficultyInstance difficulty) {
        this.populateDefaultEquipmentEnchantments(world, world.getRandom(), difficulty);
    }

    protected final void initStandardRecruitSpawn(String defaultName, int cost) {
        RecruitSpawnService.initStandardRecruitSpawn(this, defaultName, cost);
    }

    protected final void initPersistentNamedSpawn(String defaultName) {
        RecruitSpawnService.initPersistentNamedSpawn(this, defaultName);
    }

    public void setRandomSpawnBonus(){
        RecruitSpawnService.setRandomSpawnBonus(this);
    }

    ////////////////////////////////////REGISTER////////////////////////////////////

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new com.talhanation.bannermod.ai.military.RecruitMoraleRoutGoal(this));
        this.goalSelector.addGoal(6, new com.talhanation.bannermod.ai.military.RecruitSiegeObjectiveAttackGoal(this));
        this.goalSelector.addGoal(7, new com.talhanation.bannermod.ai.military.RecruitSiegeEscortGoal(this));
        this.goalSelector.addGoal(4, new BlockWithWeapon(this));
        this.goalSelector.addGoal(0, new RecruitFloatGoal(this));
        this.goalSelector.addGoal(1, new RecruitQuaffGoal(this));
        this.goalSelector.addGoal(1, new FleeTNT(this));
        this.goalSelector.addGoal(1, new FleeFire(this));
        this.goalSelector.addGoal(6, new RecruitsOpenDoorGoal(this, true) {});
        this.goalSelector.addGoal(1, new RecruitProtectEntityGoal(this));
        this.goalSelector.addGoal(0, new RecruitEatGoal(this));
        this.goalSelector.addGoal(5, new RecruitUpkeepPosGoal(this));
        this.goalSelector.addGoal(6, new RecruitUpkeepEntityGoal(this));
        this.goalSelector.addGoal(3, new RecruitMountEntity(this));
        this.goalSelector.addGoal(3, new RecruitDismountEntity(this));
        this.goalSelector.addGoal(3, new RecruitMoveToPosGoal(this, 1.05D));
        this.goalSelector.addGoal(2, new RecruitFollowOwnerGoal(this, 1.05D, 300, 100));
        this.goalSelector.addGoal(2, new RecruitMeleeAttackGoal(this, 1.05D, this.getMeleeStartRange()));
        this.goalSelector.addGoal(3, new RecruitHoldPosGoal(this, 32.0F));
        //this.goalSelector.addGoal(7, new RecruitDodgeGoal(this));
        this.goalSelector.addGoal(4, new RestGoal(this));
        // HOMEASSIGN-003: when the player has assigned a home for this recruit
        // (or worker, since AbstractWorkerEntity inherits this registerGoals),
        // prefer pathing to that home instead of the nearest free bed. Same
        // priority slot as RestGoal so combat/work goals at priority <=3 still
        // win during the day.
        this.goalSelector.addGoal(4, new PathfindHomeGoal(
                this,
                this::getHomePos,
                () -> this.getShouldRest() || this.getMorale() < 45.0F || this.getHealth() < this.getMaxHealth(),
                1.0D));
        this.goalSelector.addGoal(10, new RecruitWanderGoal(this));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return !shouldSuppressIdleLook() && super.canUse() && canObserveWhileInFormation(this.lookAt);
            }

            @Override
            public boolean canContinueToUse() {
                return !shouldSuppressIdleLook() && super.canContinueToUse() && canObserveWhileInFormation(this.lookAt);
            }
        });
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !shouldSuppressIdleLook() && !shouldLockFormationFacing() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !shouldSuppressIdleLook() && !shouldLockFormationFacing() && super.canContinueToUse();
            }
        });
        //this.goalSelector.addGoal(13, new RecruitPickupWantedItemGoal(this));

        this.targetSelector.addGoal(1, new RecruitProtectHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new RecruitOwnerHurtByTargetGoal(this));

        this.targetSelector.addGoal(3, (new RecruitHurtByTargetGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(4, new RecruitOwnerHurtTargetGoal(this));

        this.targetSelector.addGoal(7, new RecruitDefendVillageFromPlayerGoal(this));
    }

    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        RecruitStateAccess.defineSynchedData(builder);
        //STATE
        // 0 = NEUTRAL
        // 1 = AGGRESSIVE
        // 2 = RAID
        // 3 = PASSIVE

        //FOLLOW
        //0 = wander
        //1 = follow
        //2 = hold position
        //3 = back to position
        //4 = hold my position
        //5 = Protect
        //6 = Work

    }
    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        RecruitPersistenceBridge.writeRecruitData(this, nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        RecruitPersistenceBridge.readRecruitData(this, nbt);
    }

    ////////////////////////////////////GET////////////////////////////////////

    public int getVariant() {
        return RecruitStateAccess.getVariant(this);
    }
    public float getAttackDamage(){
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    public float getMovementSpeed(){
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    public boolean getFleeing() {
        return RecruitStateAccess.getFleeing(this);
    }

    //STATE
    // 0 = NEUTRAL
    // 1 = AGGRESSIVE
    // 2 = RAID
    // 3 = PASSIVE
    //FOLLOW
    //0 = wander
    //1 = follow
    //2 = hold your position
    //3 = back to position
    //4 = hold my position
    //5 = Protect
    //6 = Work
    /**
     * Safe accessor for {@code RecruitsClientConfig.RecruitsLookLikeVillagers} that
     * returns the config default ({@code true}) when Forge config has not loaded yet
     * (e.g. gametest harness early-tick).
     */
    static boolean recruitsLookLikeVillagers() {
        try {
            return RecruitsClientConfig.RecruitsLookLikeVillagers.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public SoundEvent getHurtSound(@NotNull DamageSource ds) {
        if (this.isBlocking())
            return SoundEvents.SHIELD_BLOCK;
        return recruitsLookLikeVillagers() ? SoundEvents.VILLAGER_HURT : SoundEvents.GENERIC_HURT;
    }

    protected SoundEvent getDeathSound() {
        return recruitsLookLikeVillagers() ? SoundEvents.VILLAGER_DEATH : SoundEvents.GENERIC_DEATH;
    }

    protected float getSoundVolume() {
        return 0.4F;
    }

    protected float getStandingEyeHeight(@NotNull Pose pos, EntityDimensions size) {
        return size.height() * 0.98F;
    }

    public int getMaxHeadXRot() {
        return super.getMaxHeadXRot();
    }

    public int getMaxSpawnClusterSize() {
        return 8;
    }

    @Nullable
    public LivingEntity getProtectingMob(){
        UUID protectUuid = this.getProtectUUID();
        if (protectUuid == null || !(this.getCommandSenderWorld() instanceof ServerLevel level)) return null;

        Entity entity = level.getEntity(protectUuid);
        if (entity instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(this) <= 64D * 64D) {
            return living;
        }
        return null;
    }

    public int getColor() {
        return RecruitStateAccess.getColor(this);
    }

    public int getBiome() {
        return RecruitStateAccess.getBiome(this);
    }
    public DyeColor getDyeColor() {
        return DyeColor.byId(getColor());
    }

    ////////////////////////////////////SET////////////////////////////////////

    public void setVariant(int variant){
        RecruitStateAccess.setVariant(this, variant);
    }
    public void setColor(byte color){
        RecruitStateAccess.setColor(this, color);
    }
    public void setBiome(byte biome){
        RecruitStateAccess.setBiome(this, biome);
    }
    public void setFleeing(boolean bool){
        RecruitStateAccess.setFleeing(this, bool);
    }
    public void setMountTimer(int x){
        this.mountTimer = x;
    }

    public void disband(@Nullable Player player, boolean keepTeam, boolean increaseCost){
        RecruitLifecycleService.disband(this, player, keepTeam, increaseCost, player == null ? null : TEXT_DISBAND(this.getName().getString()));
    }

    //STATE
    // 0 = NEUTRAL
    // 1 = AGGRESSIVE
    // 2 = RAID
    // 3 = PASSIVE

    //FOLLOW
    //0 = wander
    //1 = follow
    //2 = hold position
    //3 = back to position
    //4 = hold my position
    //5 = Protect
    //6 = Work
    public static void hydrateCitizenStateFromLegacy(CitizenCore citizenCore, CompoundTag nbt) {
        citizenCore.apply(CitizenPersistenceBridge.fromRecruitLegacy(nbt));
    }

    public static CompoundTag persistCitizenStateToLegacy(CitizenCore citizenCore, CompoundTag nbt) {
        return CitizenPersistenceBridge.writeRecruitLegacy(citizenCore.snapshot(), nbt);
    }

    @Override
    public void setTarget(@Nullable LivingEntity p_21544_) {
        super.setTarget(p_21544_);

        this.setUpkeepTimer(500);
    }

    public List<List<String>> getEquipment() {
        return null;
    }

    public double getMeleeStartRange() {
        return 32D;
    }

    public abstract void initSpawn();

    public static void applySpawnValues(AbstractRecruitEntity recruit){
        RecruitSpawnService.applySpawnValues(recruit);
    }

    public static void applyBiomeAndVariant(AbstractRecruitEntity recruit){
        RecruitSpawnService.applyBiomeAndVariant(recruit);
    }

    ////////////////////////////////////is FUNCTIONS////////////////////////////////////

    public boolean isEffectedByCommand(UUID player_uuid) {
        return isEffectedByCommand(player_uuid, null);
    }

    public boolean isEffectedByCommand(UUID player_uuid, UUID group) {
        if (!this.isOwned() || !this.isAlive() || !this.getListen()) return false;

        if (!this.getOwnerUUID().equals(player_uuid)) return false;

        if (group == null) {
            return true;
        }

        return this.getGroup() != null && this.getGroup().equals(group);
    }
    ////////////////////////////////////ON FUNCTIONS////////////////////////////////////

    public InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return RecruitInteractionService.mobInteract(this, player, hand);
    }

    public boolean hire(Player player, RecruitsGroup group, boolean message) {
        String name = this.getName().getString() + ": ";
        return RecruitLifecycleService.hire(this, player, group, message, INFO_RECRUITING_MAX(name), List.of(TEXT_RECRUITED1(name), TEXT_RECRUITED2(name), TEXT_RECRUITED3(name)));
    }

    public void dialogue(String name, Player player) {
        int i = this.random.nextInt(4);
        switch (i) {
            case 1 -> {
                player.sendSystemMessage(TEXT_HELLO_1(name));
            }
            case 2 -> {
                player.sendSystemMessage(TEXT_HELLO_2(name));
            }
            case 3 -> {
                player.sendSystemMessage(TEXT_HELLO_3(name));
            }
        }
    }

    ////////////////////////////////////ATTACK FUNCTIONS////////////////////////////////////

    public boolean hurt(@NotNull DamageSource dmg, float amt) {
        if (this.isInvulnerableTo(dmg)) {
            return false;
        } else {
            amt = RecruitCombatOverrideService.prepareIncomingDamage(this, dmg, amt);
            boolean applied = super.hurt(dmg, amt);
            // COMBAT-001: feed the suppression accumulator only on landed damage. Blocked /
            // shielded hits and zero-damage events do not count toward SUSTAINED_FIRE — the
            // recruit only feels the pressure that actually reaches it.
            if (applied && amt > 0.0F && this.level() instanceof ServerLevel sl) {
                com.talhanation.bannermod.combat.RecruitMoraleService.recordDamageTaken(
                        this, sl.getGameTime());
            }
            return applied;
        }
    }

    public boolean doHurtTarget(@NotNull Entity entity) {
        return RecruitCombatDecisions.doHurtTarget(this, entity);
    }

    public boolean doHurtTarget(@NotNull Entity entity, double damageMultiplier) {
        return RecruitCombatDecisions.doHurtTarget(this, entity, damageMultiplier);
    }

    /*
           .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    */
    /**
        Important for mod compat: See smallships or siege weapons mod
    **/
    public boolean isAlliedTo(@NotNull Team team) {
        if(!this.getCommandSenderWorld().isClientSide() && this.getTeam() != null && this.level() instanceof ServerLevel level){
            UUID ownEntityId = RecruitPoliticalContext.politicalEntityIdOf(this, WarRuntimeContext.registry(level));
            UUID otherEntityId = RecruitPoliticalContext.politicalEntityIdForToken(team.getName(), WarRuntimeContext.registry(level));
            return PoliticalRelations.ally(WarRuntimeContext.registry(level), ownEntityId, otherEntityId);
        }
        return super.isAlliedTo(team);
    }

    public void die(DamageSource dmg) {
        RecruitLifecycleService.onDeath(this, dmg, this.getCombatTracker().getDeathMessage());
    }

    ////////////////////////////////////OTHER FUNCTIONS////////////////////////////////////

    public boolean needsToPotion(){
        LivingEntity target = this.getTarget();
        if(target != null){
            return getHealth() <= (getMaxHealth() * 0.60) || target.getHealth() > this.getHealth();
        }
        return false;
    }

    public void makeHireSound() {
        if(recruitsLookLikeVillagers())
            this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, 0.8F + 0.4F * this.random.nextFloat());
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    protected void hurtArmor(@NotNull DamageSource damageSource, float damage) {
        RecruitEquipmentService.hurtArmor(this, damageSource, damage);
    }

    @Override
    protected void hurtCurrentlyUsedShield(float damage) {
        RecruitEquipmentService.hurtCurrentlyUsedShield(this, damage);
    }

    @Override
    public boolean killedEntity(@NotNull ServerLevel level, @NotNull LivingEntity living) {
        super.killedEntity(level, living);
        return RecruitCombatOverrideService.handleKillRewards(this, living);
    }

    @Override
    protected void blockUsingShield(@NotNull LivingEntity living) {
        super.blockUsingShield(living);
        if (living.getMainHandItem().canDisableShield(this.useItem, this, living))
            this.disableShield();
    }

    public int getMountTimer() {
        return this.mountTimer;
    }

    @Override
    public void openGUI(Player player) {
        RecruitInteractionService.openGUI(this, player);
    }

    public void openDebugScreen(Player player) {
        RecruitInteractionService.openDebugScreen(this, player);
    }

    public static void openTakeOverGUI(Player player) {

    }
    public boolean canBeHired(){
        return true;
    }
    @Override
    public boolean canAttack(@Nonnull LivingEntity target) {
        if(target instanceof MessengerEntity messenger && messenger.isAtMission()) return false;
        if(RecruitsServerConfig.TargetBlackList.get().contains(target.getEncodeId())) return false;
        return RecruitEvents.canAttack(this, target);
    }
    // 0 = NEUTRAL
    // 1 = AGGRESSIVE
    // 2 = RAID
    // 3 = PASSIVE
    public boolean shouldAttack(LivingEntity target) {
        return RecruitCombatDecisions.shouldAttack(this, target);
    }

    public boolean isAlliedTo(Entity target) {
        if (target instanceof LivingEntity livingTarget) {
            return !RecruitEvents.canHarmTeam(this, livingTarget);
        } else {
            return super.isAlliedTo(target);
        }
    }

    //
    /*********************************************************
     * Update the current team of the recruit in following conditions:
     * - If recruit team is not the same team as the owner
     * - If recruit team is null but owner team != null
     * - If recruit team is != null but owner team is null
     *********************************************************/
    public void updateTeam(){
        RecruitLifecycleService.updateTeam(this);
    }

    public void updateGroup() {
        RecruitLifecycleService.updateGroup(this);
    }


    public void openHireGUI(Player player) {
        RecruitInteractionService.openHireGUI(this, player);
    }

    public void assignToPlayer(UUID newOwner, UUID newGroupUUID){
        RecruitLifecycleService.assignToPlayer(this, newOwner, newGroupUUID);
    }

    public static enum ArmPose {
        ATTACKING,
        BLOCKING,
        BOW_AND_ARROW,
        CROSSBOW_HOLD,
        CROSSBOW_CHARGE,
        CELEBRATING,
        NEUTRAL;
    }

    public AbstractRecruitEntity.ArmPose getArmPose() {
        return AbstractRecruitEntity.ArmPose.NEUTRAL;
    }

    private MutableComponent TEXT_RECRUITED1(String name) {
        return Component.translatable("chat.bannermod.text.recruited1", name);
    }

    private MutableComponent TEXT_RECRUITED2(String name) {
        return Component.translatable("chat.bannermod.text.recruited2", name);
    }

    private MutableComponent TEXT_RECRUITED3(String name) {
        return Component.translatable("chat.bannermod.text.recruited3", name);
    }

    private Component INFO_RECRUITING_MAX(String name) {
        return Component.translatable("chat.bannermod.info.reached_max", name);
    }

    private MutableComponent TEXT_DISBAND(String name) {
        return Component.translatable("chat.bannermod.text.disband", name);
    }

    private MutableComponent TEXT_WANDER(String name) {
        return Component.translatable("chat.bannermod.text.wander", name);
    }

    private MutableComponent TEXT_HOLD_YOUR_POS(String name) {
        return Component.translatable("chat.bannermod.text.holdPos", name);
    }

    private MutableComponent TEXT_FOLLOW(String name) {
        return Component.translatable("chat.bannermod.text.follow", name);
    }

    private MutableComponent TEXT_HELLO_1(String name) {
        return Component.translatable("chat.bannermod.text.hello_1", name);
    }

    private MutableComponent TEXT_HELLO_2(String name) {
        return Component.translatable("chat.bannermod.text.hello_2", name);
    }

    private MutableComponent TEXT_HELLO_3(String name) {
        return Component.translatable("chat.bannermod.text.hello_3", name);
    }

    private MutableComponent TEXT_NO_PAYMENT(String name) {
        return Component.translatable("chat.bannermod.text.noPaymentInUpkeep", name);
    }

    InteractionResult superMobInteract(Player player, InteractionHand hand) {
        return super.mobInteract(player, hand);
    }

    MutableComponent textWander(String name) {
        return TEXT_WANDER(name);
    }

    MutableComponent textHoldYourPos(String name) {
        return TEXT_HOLD_YOUR_POS(name);
    }

    MutableComponent textFollow(String name) {
        return TEXT_FOLLOW(name);
    }

    Component textNoPayment(String name) {
        return TEXT_NO_PAYMENT(name);
    }

    void pickUpArrows() {
        this.getCommandSenderWorld().getEntitiesOfClass(
                AbstractArrow.class,
                this.getBoundingBox().inflate(7D),
                (arrow) -> arrow.inGround &&
                        arrow.pickup == AbstractArrow.Pickup.ALLOWED &&
                        this.getInventory().canAddItem(Items.ARROW.getDefaultInstance())
        ).forEach((arrow) -> {
            this.getInventory().addItem(Items.ARROW.getDefaultInstance());
            arrow.moveTo(this.position());
            arrow.discard();
        });
    }

    @Override
    public boolean startRiding(Entity entity) {
        this.setMountUUID(Optional.of(entity.getUUID()));
        return super.startRiding(entity);
    }

    @Override
    public boolean removeWhenFarAway(double p_21542_) {
        return false;
    }


    private boolean hasFoodInContainer(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (this.canEatItemStack(container.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    void writeAggroState(int state) {
        RecruitStateAccess.writeAggroState(this, state);
    }

    void writeFollowState(int state) {
        RecruitStateAccess.writeFollowState(this, state);
    }

    void writeHoldPos(Optional<BlockPos> holdPos) {
        RecruitStateAccess.writeHoldPos(this, holdPos);
    }

    void writeMovePos(Optional<BlockPos> movePos) {
        RecruitStateAccess.writeMovePos(this, movePos);
    }

    int targetSearchTickOffset() {
        return this.tickOffset;
    }

    void stopNavigation() {
        this.navigation.stop();
    }

    void superDie(DamageSource dmg) {
        super.die(dmg);
    }

    public enum NoPaymentAction{
        MORALE_LOSS,
        DISBAND,
        DISBAND_KEEP_TEAM,
        DESPAWN;

        public static NoPaymentAction fromString(String name) {
            if (name == null || name.isBlank()) {
                return MORALE_LOSS;
            }
            try {
                return NoPaymentAction.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return MORALE_LOSS;
            }
        }
    }

    <T> T getStateData(EntityDataAccessor<T> accessor) {
        return this.entityData.get(accessor);
    }

    <T> void setStateData(EntityDataAccessor<T> accessor, T value) {
        this.entityData.set(accessor, value);
    }

    // ------------------------------------------------------------------
    // HomeAssign aliasing — recruits reuse upkeepPos / upkeepUUID as home.
    // (HOMEASSIGN-002.) Citizens and workers carry an independent homePos
    // synched accessor on AbstractCitizenEntity; for recruits we forward
    // to the upkeep fields so existing upkeep AI still sees one source of
    // truth.
    // ------------------------------------------------------------------

    @Override
    @Nullable
    public BlockPos getHomePos() {
        return this.getUpkeepPos();
    }

    @Override
    public void setHomePos(@Nullable BlockPos pos) {
        if (pos == null) {
            this.clearUpkeepPos();
        } else {
            this.setUpkeepPos(pos);
        }
    }

    @Override
    public void clearHomePos() {
        this.clearUpkeepPos();
        this.clearUpkeepEntity();
    }

    @Override
    @Nullable
    public UUID getHomeBuildAreaUUID() {
        return this.getUpkeepUUID();
    }

    @Override
    public void setHomeBuildAreaUUID(@Nullable UUID uuid) {
        this.setUpkeepUUID(uuid == null ? Optional.empty() : Optional.of(uuid));
    }

    @Override
    public boolean hasHomeAssigned() {
        return this.getUpkeepPos() != null;
    }
}
