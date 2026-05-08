package com.talhanation.bannermod.entity.civilian;

import com.google.common.collect.ImmutableSet;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.ai.pathfinding.AsyncGroundPathNavigation;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class FarmerEntity extends AbstractWorkerEntity{
    public static final Set<Block> TILLABLES = ImmutableSet.of(
            Blocks.DIRT,
            Blocks.ROOTED_DIRT,
            Blocks.COARSE_DIRT,
            Blocks.GRASS_BLOCK);

    public FarmerEntity(EntityType<? extends AbstractWorkerEntity> entityType, Level world) {
        super(entityType, world);

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getCommandSenderWorld().isClientSide()
                && this.shouldWork()
                && this.getCurrentCropArea() == null) {
            this.reportIdleReason("farmer_no_area", Component.literal(this.getName().getString() + ": Waiting for a crop area."));
        }
    }

    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(NeoForgeMod.SWIM_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 0D)
                .add(Attributes.ATTACK_SPEED);

    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficultyInstance, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag nbt) {
        RandomSource randomsource = world.getRandom();
        SpawnGroupData ilivingentitydata = super.finalizeSpawn(world, difficultyInstance, reason, data, nbt);
        ((AsyncGroundPathNavigation)this.getNavigation()).setCanOpenDoors(true);
        this.populateDefaultEquipmentEnchantments(world, randomsource, difficultyInstance);

        this.initSpawn();

        return ilivingentitydata;
    }

    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override//not used
    public Predicate<ItemEntity> getAllowedItems() {
        return null;
    }

    @Override
    public void initSpawn() {
        this.setCustomName(Component.literal("Farmer"));
        //this.setCost(WorkersServerConfig.FarmerCost.get());
        this.setCost(10);

        this.setEquipment();
        this.setDropEquipment();
        this.setRandomSpawnBonus();
        this.setPersistenceRequired();

        AbstractRecruitEntity.applySpawnValues(this);
    }

    @Override
    public List<Item> inventoryInputHelp() {
        return null;
    }
    public boolean isBucketWithWater(ItemStack itemStack) {
        return itemStack.is(Items.WATER_BUCKET);
    }
    public boolean wantsToKeep(ItemStack itemStack) {
        if (itemStack.getItem() instanceof HoeItem) {
            int items = countMatchingItems(stack -> stack.getItem() instanceof HoeItem);
            return items <= 1;
        }

        CropArea cropArea = getCurrentCropArea();
        if(cropArea != null) {
            ItemStack crop = cropArea.getSeedStack();
            if(ItemStack.isSameItem(crop, itemStack)){
                int items = countMatchingStacks(stack -> crop.is(stack.getItem()));
                return items <= 1;
            }
        }

        return super.wantsToKeep(itemStack);
    }
    public boolean wantsToPickUp(ItemStack itemStack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if(id == null) return false;

        if(WorkersServerConfig.FARMER_PICKUP.contains(id.toString())) return true;
        if(itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock) return true;

        return super.wantsToPickUp(itemStack);
    }

    @Nullable
    public CropArea getCurrentCropArea() {
        return getCurrentWorkArea() instanceof CropArea ca ? ca : null;
    }
}
