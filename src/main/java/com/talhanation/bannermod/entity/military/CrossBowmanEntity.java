package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.compat.IWeapon;
import com.talhanation.bannermod.compat.MedievalBoomsticksCompat;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.ai.military.RecruitMoveTowardsTargetGoal;
import com.talhanation.bannermod.ai.military.RecruitRangedCrossbowAttackGoal;
import com.talhanation.bannermod.ai.military.compat.RecruitRangedMusketAttackGoal;
import com.talhanation.bannermod.persistence.military.RecruitsPatrolSpawn;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

import static com.talhanation.bannermod.bootstrap.BannerModMain.isMusketModLoaded;


public class CrossBowmanEntity extends AbstractStrategicFireRecruitEntity implements CrossbowAttackMob, IRangedRecruit {

    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(CrossBowmanEntity.class, EntityDataSerializers.BOOLEAN);


    public CrossBowmanEntity(EntityType<? extends AbstractRecruitEntity> entityType, Level world) {
        super(entityType, world);

    }

    private final Predicate<ItemEntity> ALLOWED_ITEMS = (item) ->
            (!item.hasPickUpDelay() && item.isAlive() && getInventory().canAddItem(item.getItem()) && this.wantsToPickUp(item.getItem()));

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_IS_CHARGING_CROSSBOW, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);

        nbt.putBoolean("isChargingCrossbow", this.getChargingCrossbow());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        this.setChargingCrossbow(nbt.getBoolean("isChargingCrossbow"));
    }
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new com.talhanation.bannermod.ai.military.RecruitRangedSpacingGoal(this));
        if(isMusketModLoaded){
            this.goalSelector.addGoal(0, new RecruitRangedMusketAttackGoal(this, this.getMeleeStartRange()));
        }
        this.goalSelector.addGoal(0, new RecruitRangedCrossbowAttackGoal(this, this.getMeleeStartRange()));
        this.goalSelector.addGoal(8, new RecruitMoveTowardsTargetGoal(this, 1.15D, (float) this.getMeleeStartRange()));
    }


    //ATTRIBUTES
    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(ForgeMod.SWIM_SPEED.get(), 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.05D)
                .add(Attributes.ATTACK_DAMAGE, 1.5D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 0D)
                .add(Attributes.ATTACK_SPEED);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficultyInstance, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag nbt) {
        return finishRecruitLeafSpawn(world, difficultyInstance, super.finalizeSpawn(world, difficultyInstance, reason, data, nbt), true, true);
    }

    @Override
    public void initSpawn() {
        initStandardRecruitSpawn("Crossbowman", RecruitsServerConfig.CrossbowmanCost.get());

        if(RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get()){
            if(isMusketModLoaded && IWeapon.isMusketModWeapon(this.getMainHandItem())){
                int i = this.getRandom().nextInt(32);
                MedievalBoomsticksCompat.ammoContract(this.getMainHandItem())
                        .map(ammoId -> MedievalBoomsticksCompat.createAmmoStack(ammoId, 14 + i))
                        .filter(stack -> !stack.isEmpty())
                        .ifPresent(arrows -> this.inventory.setItem(6, arrows));
            }
            else RecruitsPatrolSpawn.setRangedArrows(this);
        }

    }

    @Override
    public boolean canHoldItem(ItemStack itemStack){
        return !(itemStack.getItem() instanceof SwordItem || itemStack.getItem() instanceof ShieldItem) || itemStack.getItem() instanceof CrossbowItem;
    }
    public void performRangedAttack(@NotNull LivingEntity target, float v) {

    }
    @Override
    public boolean wantsToPickUp(@NotNull ItemStack itemStack) {
        if(isMusketModLoaded && MedievalBoomsticksCompat.isMedievalBoomsticksItem(itemStack)) return MedievalBoomsticksCompat.isSupportedRecruitItem(itemStack);
        else if ((itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof ProjectileWeaponItem || itemStack.getItem() instanceof SwordItem) && this.getMainHandItem().isEmpty()){
            return !hasSameTypeOfItem(itemStack);
        }
        else if(itemStack.is(ItemTags.ARROWS) && RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get())
            return true;
        else
            return super.wantsToPickUp(itemStack);

    }

    @Override
    public Predicate<ItemEntity> getAllowedItems() {
        return ALLOWED_ITEMS;
    }
    @Override
    public double getMeleeStartRange() {
        return 5D;
    }

    //Pillager
    @Override
    public void shootCrossbowProjectile(@NotNull LivingEntity target, @NotNull ItemStack stack, @NotNull Projectile projectile, float f) {
        this.shootCrossbowProjectile(this, target, projectile, f, 1.6F);
    }

    private boolean getChargingCrossbow() {
        return this.entityData.get(DATA_IS_CHARGING_CROSSBOW);
    }

    public void setChargingCrossbow(boolean is) {
        this.entityData.set(DATA_IS_CHARGING_CROSSBOW, is);
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem weaponItem) {
        return weaponItem.equals(Items.CROSSBOW);
    }
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    public List<List<String>> getEquipment(){
        return RecruitsServerConfig.CrossbowmanStartEquipments.get();
    }


    @Override
    public Predicate<ItemStack> getWeaponType() {
        return itemStack -> itemStack.getItem() instanceof CrossbowItem;
    }
}
