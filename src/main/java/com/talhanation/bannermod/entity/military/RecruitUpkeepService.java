package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.compat.IWeapon;
import com.talhanation.bannermod.compat.MedievalBoomsticksCompat;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.shared.logistics.BannerModSupplyStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

final class RecruitUpkeepService {
    private RecruitUpkeepService() {
    }

    static void updateMorale(AbstractRecruitEntity recruit) {
        if (recruit instanceof VillagerNobleEntity) return;
        float currentMorale = recruit.getMorale();
        float newMorale = currentMorale;
        if (isStarving(recruit) && recruit.isOwned() && currentMorale > 0) newMorale -= 2F;
        if (recruit.isOwned() && !isSaturated(recruit) && currentMorale > 35) newMorale -= 1F;
        if ((isSaturated(recruit) || recruit.getHealth() >= recruit.getMaxHealth() * 0.85) && currentMorale < 65) newMorale += 2F;
        if (newMorale < 0) newMorale = 0;
        recruit.setMoral(newMorale);
    }

    static void applyMoralEffects(AbstractRecruitEntity recruit) {
        boolean confused = 0 <= recruit.getMorale() && recruit.getMorale() < 20;
        boolean lowMoral = 20 <= recruit.getMorale() && recruit.getMorale() < 40;
        boolean highMoral = 90 <= recruit.getMorale() && recruit.getMorale() <= 100;
        if (confused) {
            addEffectIfMissing(recruit, MobEffects.WEAKNESS, 200, 3);
            addEffectIfMissing(recruit, MobEffects.MOVEMENT_SLOWDOWN, 200, 2);
            addEffectIfMissing(recruit, MobEffects.CONFUSION, 200, 1);
        }
        if (lowMoral) {
            addEffectIfMissing(recruit, MobEffects.WEAKNESS, 200, 1);
            addEffectIfMissing(recruit, MobEffects.MOVEMENT_SLOWDOWN, 200, 1);
        }
        if (highMoral) {
            addEffectIfMissing(recruit, MobEffects.DAMAGE_BOOST, 200, 0);
            addEffectIfMissing(recruit, MobEffects.DAMAGE_RESISTANCE, 200, 0);
        }
    }

    static void updateHunger(AbstractRecruitEntity recruit) {
        if (recruit instanceof VillagerNobleEntity) return;
        float hunger = recruit.getHunger();
        hunger -= recruit.getFollowState() == 2 ? 1 / 60F : 2 / 60F;
        if (hunger < 0) hunger = 0;
        recruit.setHunger(hunger);
        if (RecruitsServerConfig.RecruitsStarving.get() && hunger == 0) {
            recruit.hurt(recruit.damageSources().starve(), 0.25F);
        }
    }

    static boolean needsToGetFood(AbstractRecruitEntity recruit) {
        int timer = recruit.getUpkeepTimer();
        boolean needsToEat = needsToEat(recruit);
        boolean hasFood = hasFoodInInv(recruit);
        boolean isChest = recruit.getUpkeepPos() != null;
        boolean isEntity = recruit.getUpkeepUUID() != null;
        return (recruit.forcedUpkeep || (!hasFood && timer == 0 && needsToEat) && (isChest || isEntity)) && !recruit.getShouldProtect();
    }

    static boolean hasFoodInInv(AbstractRecruitEntity recruit) {
        return recruit.getInventory().items.stream().anyMatch(ItemStack::isEdible);
    }

    static boolean needsToEat(AbstractRecruitEntity recruit) {
        if (recruit.getHunger() <= 50F) return true;
        if (recruit.getHunger() <= 70F && recruit.getHealth() != recruit.getMaxHealth() && recruit.getTarget() == null && recruit.getIsOwned()) return true;
        return recruit.getHealth() <= (recruit.getMaxHealth() * 0.30) && recruit.getTarget() == null;
    }

    static boolean isStarving(AbstractRecruitEntity recruit) {
        return recruit.getHunger() <= 1F;
    }

    static boolean isSaturated(AbstractRecruitEntity recruit) {
        return recruit.getHunger() >= 90F;
    }

    static BannerModSupplyStatus.RecruitSupplyStatus getSupplyStatus(AbstractRecruitEntity recruit, @Nullable Container upkeepContainer) {
        boolean upkeepHasFood = upkeepContainer != null && hasFoodInContainer(recruit, upkeepContainer);
        boolean upkeepHasPayment = upkeepContainer != null && recruit.isPaymentInContainer(upkeepContainer);
        return BannerModSupplyStatus.recruitSupplyStatus(
                recruit.hasUpkeep(),
                needsToGetFood(recruit),
                recruit.paymentTimer == 0,
                upkeepHasFood,
                upkeepHasPayment,
                recruit.isPaymentInContainer(recruit.getInventory()),
                recruit.getHunger()
        );
    }

    static void upkeepReequip(AbstractRecruitEntity recruit, @NotNull Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack itemstack = container.getItem(i);
            ItemStack equipment;
            if (!recruit.canEatItemStack(itemstack) && recruit.wantsToPickUp(itemstack)) {
                if (recruit.canEquipItem(itemstack)) {
                    equipment = itemstack.copy();
                    equipment.setCount(1);
                    recruit.equipItem(equipment);
                    itemstack.shrink(1);
                }
                if (recruit instanceof CrossBowmanEntity crossBowmanEntity && BannerModMain.isMusketModLoaded && IWeapon.isMusketModWeapon(crossBowmanEntity.getMainHandItem()) && MedievalBoomsticksCompat.ammoContract(crossBowmanEntity.getMainHandItem()).map(ammoId -> MedievalBoomsticksCompat.isAmmo(itemstack, ammoId)).orElse(false)) {
                    if (recruit.canTakeCartridge()) {
                        equipment = itemstack.copy();
                        recruit.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                } else if (recruit instanceof IRangedRecruit && itemstack.is(ItemTags.ARROWS)) {
                    if (recruit.canTakeArrows()) {
                        equipment = itemstack.copy();
                        recruit.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                }
            }
            if (recruit instanceof CaptainEntity && BannerModMain.isSmallShipsLoaded) {
                if (itemstack.getDescriptionId().contains("cannon_ball")) {
                    if (recruit.canTakeCannonBalls()) {
                        equipment = itemstack.copy();
                        recruit.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                } else if (itemstack.is(ItemTags.PLANKS)) {
                    if (recruit.canTakePlanks()) {
                        equipment = itemstack.copy();
                        recruit.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                } else if (itemstack.is(Items.IRON_NUGGET)) {
                    if (recruit.canTakeIronNuggets()) {
                        equipment = itemstack.copy();
                        recruit.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                }
            }
        }
    }

    static void checkPayment(AbstractRecruitEntity recruit, Container container, @Nullable Component noPaymentMessage) {
        if (RecruitsServerConfig.RecruitsPayment.get() && recruit.isOwned()) {
            if (recruit.isPaymentInContainer(container)) {
                recruit.doPayment(container);
            } else if (recruit.isPaymentInContainer(recruit.getInventory())) {
                recruit.doPayment(recruit.getInventory());
            } else {
                doNoPaymentAction(recruit);
                if (recruit.getOwner() != null && noPaymentMessage != null) recruit.getOwner().sendSystemMessage(noPaymentMessage);
            }
            resetPaymentTimer(recruit);
        }
    }

    static void doNoPaymentAction(AbstractRecruitEntity recruit) {
        AbstractRecruitEntity.NoPaymentAction action = RecruitsServerConfig.RecruitsNoPaymentAction.get();
        switch (action) {
            case MORALE_LOSS -> recruit.setMoral((float) Math.max(0, recruit.getMorale() * 0.7));
            case DISBAND_KEEP_TEAM -> recruit.disband(recruit.getOwner(), true, true);
            case DISBAND -> recruit.disband(recruit.getOwner(), false, true);
            case DESPAWN -> recruit.discard();
        }
    }

    static void resetPaymentTimer(AbstractRecruitEntity recruit) {
        int interval = RecruitsServerConfig.RecruitsPaymentInterval.get();
        recruit.paymentTimer = 20 * 60 * interval;
    }

    private static boolean hasFoodInContainer(AbstractRecruitEntity recruit, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (recruit.canEatItemStack(container.getItem(i))) return true;
        }
        return false;
    }

    private static void addEffectIfMissing(AbstractRecruitEntity recruit, MobEffect effect, int duration, int amplifier) {
        if (!recruit.hasEffect(effect)) recruit.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
    }
}
