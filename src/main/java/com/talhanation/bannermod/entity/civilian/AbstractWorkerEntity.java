package com.talhanation.bannermod.entity.civilian;

import com.google.common.collect.ImmutableSet;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.citizen.CitizenCore;
import com.talhanation.bannermod.citizen.CitizenRole;
import com.talhanation.bannermod.entity.military.RecruitPoliticalContext;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRuntime;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import com.talhanation.bannermod.society.NpcPhaseOneSnapshot;
import com.talhanation.bannermod.society.NpcSocietyAccess;
import com.talhanation.bannermod.config.RecruitsClientConfig;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.entity.military.AbstractChunkLoaderEntity;
import com.talhanation.bannermod.ai.civilian.DepositItemsToStorage;
import com.talhanation.bannermod.ai.civilian.GetNeededItemsFromStorage;
import com.talhanation.bannermod.ai.civilian.SettlementOrderWorkGoal;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;
import com.talhanation.bannermod.network.messages.civilian.MessageToClientOpenWorkerScreen;
import com.talhanation.bannermod.persistence.civilian.NeededItem;
import com.talhanation.bannermod.util.BannerModNpcNamePool;
import com.talhanation.bannermod.society.NpcFamilyTreeSnapshot;
import com.talhanation.bannermod.war.WarRuntimeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;


public abstract class AbstractWorkerEntity extends AbstractChunkLoaderEntity implements WorkerLogisticsAccess {

    public static final Set<Block> UNBREAKABLES = ImmutableSet.of(
            Blocks.BEDROCK,
            Blocks.BARRIER);

    public AbstractWorkerEntity(EntityType<? extends AbstractWorkerEntity> entityType, Level world) {
        super(entityType, world);
        // Allow workers to treat water as traversable instead of an impassable wall. Without
        // this they drown / get stuck in ponds and dock waters when trying to return to land.
        if (this.getNavigation() != null) {
            this.getNavigation().setCanFloat(true);
        }
        // Workers are paid, named, claim-bound NPCs — they must not despawn just because
        // their work-area routine took them outside the player's 32-block no-despawn radius.
        // Recruits already opt out of vanilla despawn via removeWhenFarAway; mirror that
        // here so a wandering farmer/lumberjack/miner doesn't disappear while the player
        // is at the other end of the claim.
        this.setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double sqDistanceToClosestPlayer) {
        return false;
    }
    public List<NeededItem> neededItems = new ArrayList<>();
    public int farmedItems;
    public boolean forcedDeposit;
    public UUID lastStorage;
    private final WorkerCourierService courierService = new WorkerCourierService(this);
    private final WorkerTransportService transportService = new WorkerTransportService(this);
    private final WorkerInventoryService inventoryService = new WorkerInventoryService(this);
    private final WorkerControlAccess controlAccess = new WorkerControlAccess(this);
    private final WorkerSupplyRuntime supplyRuntime = new WorkerSupplyRuntime(this);
    private final WorkerBlockBreakService blockBreakService = new WorkerBlockBreakService(this);
    private final WorkerStateAccess stateAccess = new WorkerStateAccess(this);
    private final CitizenCore citizenCore = WorkerCitizenBridge.createCore(this);
    @Nullable
    private AbstractWorkAreaEntity currentWorkAreaCache;
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new SettlementOrderWorkGoal(this));
        this.goalSelector.addGoal(0, new DepositItemsToStorage(this));
        this.goalSelector.addGoal(0, new GetNeededItemsFromStorage(this));

        this.goalSelector.removeGoal(new MoveTowardsTargetGoal(this, 0.9D, 32.0F));
    }

    @Nullable
    public final AbstractWorkAreaEntity getCurrentWorkArea() {
        if (this.currentWorkAreaCache != null && !this.currentWorkAreaCache.isRemoved()) {
            return this.currentWorkAreaCache;
        }
        this.currentWorkAreaCache = null;
        UUID bound = this.controlAccess.getBoundWorkAreaUUID();
        if (bound == null) return null;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;
        net.minecraft.world.entity.Entity resolved = serverLevel.getEntity(bound);
        if (resolved instanceof AbstractWorkAreaEntity area && !area.isRemoved()) {
            this.currentWorkAreaCache = area;
            return area;
        }
        return null;
    }

    public final void setCurrentWorkArea(@Nullable AbstractWorkAreaEntity area) {
        this.currentWorkAreaCache = area;
        this.controlAccess.setBoundWorkAreaBinding(area == null ? null : area.getUUID());
    }

    @Override
    public CitizenCore getCitizenCore() {
        return this.citizenCore;
    }

    @Override
    public CitizenRole getCitizenRole() {
        return CitizenRole.CONTROLLED_WORKER;
    }

    protected void clearCurrentWorkAreaForRecovery() {
        setCurrentWorkArea(null);
    }

    @Nullable
    public UUID getBoundWorkAreaUUID() {
        return this.controlAccess.getBoundWorkAreaUUID();
    }

    WorkerControlAccess controlAccess() {
        return this.controlAccess;
    }

    public void reportBlockedReason(String reasonToken, Component message) {
        this.controlAccess.reportBlockedReason(reasonToken, message);
    }

    public void reportIdleReason(String reasonToken, Component message) {
        this.controlAccess.reportIdleReason(reasonToken, message);
    }

    public void clearWorkStatus() {
        this.controlAccess.clearWorkStatus();
    }

    @Override
    public InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (!this.level().isClientSide() && hand == InteractionHand.MAIN_HAND) {
            BannerModNpcNamePool.ensureNamed(this);
            openDepositsGUI(player);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    private WorkerInspectionSnapshot inspectionSnapshot(@Nullable Player viewer) {
        ServerPlayer serverPlayer = viewer instanceof ServerPlayer sp ? sp : null;
        String convertBlockedReasonKey = WorkerCitizenConversionService.convertDeniedReasonKey(serverPlayer, this);
        NpcPhaseOneSnapshot phaseOneSnapshot = this.level() instanceof ServerLevel serverLevel
                ? NpcSocietyAccess.phaseOneSnapshot(serverLevel, this.getUUID(), this.getBoundWorkAreaUUID())
                : NpcPhaseOneSnapshot.empty();
        NpcFamilyTreeSnapshot familyTreeSnapshot = this.level() instanceof ServerLevel serverLevel
                ? NpcSocietyAccess.familyTreeSnapshot(serverLevel, this.getUUID())
                : NpcFamilyTreeSnapshot.empty();
        return new WorkerInspectionSnapshot(
                this.getId(),
                this.getUUID(),
                this.getName().getString(),
                this.getType().getDescriptionId(),
                workerOwnerLabel(),
                workerPoliticalLabel(),
                workerClaimRelationKey(),
                workerAssignmentLabel(),
                workerProblemLabel(),
                this.transportService.inspectionMessage().getString(),
                phaseOneSnapshot,
                familyTreeSnapshot,
                convertBlockedReasonKey == null,
                convertBlockedReasonKey,
                WorkerCitizenConversionService.workerProfessionTag(this)
        );
    }

    private String workerOwnerLabel() {
        Player owner = this.getOwner();
        if (owner != null) {
            return owner.getName().getString();
        }
        UUID ownerUuid = this.getOwnerUUID();
        return ownerUuid == null ? "none" : ownerUuid.toString();
    }

    private String workerPoliticalLabel() {
        return this.getTeam() == null ? "none" : this.getTeam().getName();
    }

    private String workerAssignmentLabel() {
        AbstractWorkAreaEntity workArea = this.getCurrentWorkArea();
        if (workArea != null) {
            return workArea.getType().getDescription().getString();
        }
        return this.getBoundWorkAreaUUID() == null ? "unassigned" : "missing work area";
    }

    private String workerClaimRelationKey() {
        if (!(this.level() instanceof ServerLevel serverLevel) || ClaimEvents.claimManager() == null) {
            return "gui.bannermod.worker_screen.relation.unknown";
        }
        String token = this.getTeam() == null ? null : this.getTeam().getName();
        UUID politicalEntityId = RecruitPoliticalContext.politicalEntityIdOf(this, WarRuntimeContext.registry(serverLevel));
        if (politicalEntityId != null) {
            token = politicalEntityId.toString();
        }
        BannerModSettlementBinding.Binding binding = BannerModSettlementBinding.resolveSettlementStatus(
                ClaimEvents.claimManager(),
                this.blockPosition(),
                token
        );
        return switch (binding.status()) {
            case FRIENDLY_CLAIM -> "gui.bannermod.worker_screen.relation.friendly_claim";
            case HOSTILE_CLAIM -> "gui.bannermod.worker_screen.relation.hostile_claim";
            case DEGRADED_MISMATCH -> "gui.bannermod.worker_screen.relation.degraded_mismatch";
            case UNCLAIMED -> "gui.bannermod.worker_screen.relation.unclaimed";
        };
    }

    private String workerProblemLabel() {
        WorkerControlStatus status = this.controlAccess.workStatus();
        if (status.kind() == null || status.reasonToken() == null || status.reasonToken().isBlank()) {
            return "none reported";
        }
        if (status.reasonMessage() != null && !status.reasonMessage().isBlank()) {
            return status.reasonMessage();
        }
        return status.kind().name().toLowerCase(Locale.ROOT) + ": " + status.reasonToken();
    }

    WorkerCourierService courierService() {
        return this.courierService;
    }

    WorkerTransportService transportService() {
        return this.transportService;
    }

    WorkerInventoryService inventoryService() {
        return this.inventoryService;
    }

    WorkerSupplyRuntime supplyRuntime() {
        return this.supplyRuntime;
    }

    public boolean recoverControl(Player requester) {
        return this.controlAccess.recoverControl(requester);
    }

    /////////////////////////////////// TICK/////////////////////////////////////////

    public boolean isWorking(){
        return this.stateAccess.isWorking();
    }
    @Override
    public void aiStep() {
        super.aiStep();
        if(this.getCommandSenderWorld().isClientSide()) return;

        this.getCommandSenderWorld().getProfiler().push("looting");
        WorkerRuntimeLoop.aiStep(this);
        this.transportService.tick();
        this.getCitizenRoleController().onServerAiStep(this);
        this.getCommandSenderWorld().getProfiler().pop();
    }

    boolean isAliveForLooting() {
        return this.isAlive() && !this.dead;
    }

    @Override
    public boolean wantsToPickUp(ItemStack itemStack) {
        if (this.wantsToPickUpWorkerItem(itemStack)) return true;
        return super.wantsToPickUp(itemStack);
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        this.pickUpWorkerItem(itemEntity);
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor world, @NotNull DifficultyInstance diff, @NotNull MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag nbt) {
        return spawnData;
    }
    public void setDropEquipment()  {
        this.dropEquipment();
    }


    //////////////////////////////////// REGISTER////////////////////////////////////

    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    public void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        WorkerPersistenceBridge.writeWorkerData(this, nbt);
    }

    public void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        WorkerPersistenceBridge.readWorkerData(this, nbt);
    }

    private static boolean recruitsLookLikeVillagers() {
        try {
            return RecruitsClientConfig.RecruitsLookLikeVillagers.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public SoundEvent getHurtSound(DamageSource ds) {
        if(recruitsLookLikeVillagers()){
            return SoundEvents.VILLAGER_HURT;
        }
        else
            return SoundEvents.PLAYER_HURT;
    }

    @OnlyIn(Dist.CLIENT)
    protected SoundEvent getDeathSound() {
        if(recruitsLookLikeVillagers()){
            return SoundEvents.VILLAGER_DEATH;
        }
        else
            return SoundEvents.PLAYER_DEATH;
    }

    protected float getSoundVolume() {
        return 0.4F;
    }

    //////////////////////////////////// SET////////////////////////////////////

    public void setEquipment() {
    }

    public boolean needsToSleep() {
        return this.stateAccess.needsToSleep();
    }

    public abstract Predicate<ItemEntity> getAllowedItems();

    public void initSpawn(){
        this.setEquipment();
        this.setDropEquipment();
        this.setPersistenceRequired();
        this.setCanPickUpLoot(true);
    }

    public double getDistanceToOwner(){
        return this.stateAccess.getDistanceToOwner();
    }

    public void tick() {
        super.tick();
        if(this.getCommandSenderWorld().isClientSide()) return;

        WorkerIndex.instance().onWorkerTick(this);
        this.getCitizenRoleController().onServerTick(this);
    }

    public abstract List<Item> inventoryInputHelp();

    public void mineBlock(BlockPos pos) {
        this.blockBreakService.mineBlock(pos);
    }


    public void switchMainHandItem(Predicate<ItemStack> predicate) {
        this.stateAccess.switchMainHandItem(predicate);
    }

    public double getHorizontalDistanceTo(Vec3 target){
        return this.stateAccess.getHorizontalDistanceTo(target);
    }

    @Override
    public void die(DamageSource dmg) {
        super.die(dmg);
        if(this.getCurrentWorkArea() != null) getCurrentWorkArea().setBeingWorkedOn(false);
    }

    public static boolean isPosBroken(BlockPos pos, Level level, boolean allowWater) {
        return WorkerBlockBreakService.isPosBroken(pos, level, allowWater);
    }

    public void openDepositsGUI(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BannerModMain.SIMPLE_CHANNEL.send(
                BannerModPacketDistributor.PLAYER.with(() -> serverPlayer),
                new MessageToClientOpenWorkerScreen(this.inspectionSnapshot(serverPlayer))
        );
    }

    public boolean shouldWork() {
        return this.stateAccess.shouldWork();
    }
}
