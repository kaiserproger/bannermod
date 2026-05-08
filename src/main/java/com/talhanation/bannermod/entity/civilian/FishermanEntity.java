package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.ai.pathfinding.AsyncGroundPathNavigation;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.entity.civilian.workarea.FishingArea;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

public class FishermanEntity extends AbstractWorkerEntity{
    public FishermanEntity(EntityType<? extends AbstractWorkerEntity> entityType, Level world) {
        super(entityType, world);
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
        this.setCustomName(Component.literal("Fisherman"));
        //this.setCost(WorkersServerConfig.Fisherman.get());
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

    @Override
    public boolean wantsToKeep(ItemStack itemStack) {
        if (itemStack.getItem() instanceof FishingRodItem) {
            int rods = countMatchingItems(stack -> stack.getItem() instanceof FishingRodItem);
            return rods <= 1;
        }

        return super.wantsToKeep(itemStack);
    }

    public boolean wantsToPickUp(ItemStack itemStack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if(id == null) return false;

        if(WorkersServerConfig.FISHERMAN_PICKUP.contains(id.toString())) return true;


        return super.wantsToPickUp(itemStack);
    }

    @Nullable
    public FishingArea getCurrentFishingArea() {
        return getCurrentWorkArea() instanceof FishingArea fa ? fa : null;
    }

    public FishingBobberEntity throwFishingHook(Vec3 target){
        FishingBobberEntity fishingBobber = new FishingBobberEntity(this.getCommandSenderWorld(), this);
        fishingBobber.setPos(this.getEyePosition());

        double d0 = target.x() - this.getX();
        double d1 = target.y() - this.getY();
        double d2 = target.z() - this.getZ();
        double d3 = Mth.sqrt((float) (d0 * d0 + d2 * d2));

        float angle = 0.25F;
        float force = 0.75F;
        float accuracy = 40;

        fishingBobber.shoot(d0, d1 + d3 * angle, d2, force, accuracy);

        this.getCommandSenderWorld().addFreshEntity(fishingBobber);

        return fishingBobber;
    }

    public void spawnFishingLoot(FishingBobberEntity fishingBobber) {
        if(fishingBobber == null ) return;

        ServerLevel serverLevel = (ServerLevel)this.getCommandSenderWorld();
        double luckFromTool = EnchantmentHelper.getFishingLuckBonus(serverLevel, this.getItemInHand(InteractionHand.MAIN_HAND), this);
        double luckFromDepth = Math.min(25, fishingBobber.getWaterDepth())/10F;
        double luck = 0.1D + luckFromTool + luckFromDepth;

        LootParams lootparams = (new LootParams.Builder(serverLevel))
                .withParameter(LootContextParams.ORIGIN, this.position())
                .withParameter(LootContextParams.TOOL, getMainHandItem())
                .withParameter(LootContextParams.ATTACKING_ENTITY, this)
                .withLuck((float)(luck + luckFromTool))
                .create(LootContextParamSets.FISHING);
        LootTable loottable = this.getCommandSenderWorld().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> list = loottable.getRandomItems(lootparams);

        MinecraftServer server = getServer();
        if (server == null) return;

        for (ItemStack itemstack : list) {
            getInventory().addItem(itemstack);
        }
    }
}
