package com.talhanation.bannermod.army.command.runtime;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.BowmanEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.util.BannerModCurrencyHelper;
import com.talhanation.bannermod.util.RegistryLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

import java.util.Optional;
import java.util.UUID;

public final class RecruitCommandActionService {

    private RecruitCommandActionService() {
    }

    public static boolean handleRecruiting(Player player, RecruitsGroup group, AbstractRecruitEntity recruit, boolean message) {
        String name = recruit.getName().getString() + ": ";
        int sollPrice = recruit.getCost();
        boolean playerCanPay = BannerModCurrencyHelper.canAfford(player, sollPrice);
        Item currency = BannerModCurrencyHelper.currencyItem();

        if (playerCanPay || player.isCreative()) {
            if (recruit.hire(player, group, message)) {
                BannerModCurrencyHelper.removeCurrency(player, sollPrice);

                return true;
            }
        } else {
            player.sendSystemMessage(textHireCosts(name, sollPrice, currency));
        }

        return false;
    }

    public static void onMountButton(UUID playerUuid, AbstractRecruitEntity recruit, UUID mountUuid, UUID group) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            if (mountUuid != null) {
                recruit.shouldMount(true, mountUuid);
            } else if (recruit.getMountUUID() != null) {
                recruit.shouldMount(true, recruit.getMountUUID());
            }
            recruit.dismount = 0;
        }
    }

    public static void onDismountButton(UUID playerUuid, AbstractRecruitEntity recruit, UUID group) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            recruit.shouldMount(false, null);
            if (recruit.isPassenger()) {
                recruit.stopRiding();
                recruit.dismount = 180;
            }
        }
    }

    public static void onProtectButton(UUID playerUuid, AbstractRecruitEntity recruit, UUID protectUuid, UUID group) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            recruit.shouldProtect(true, protectUuid);
        }
    }

    public static void onClearTargetButton(UUID playerUuid, AbstractRecruitEntity recruit, UUID group) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            recruit.setTarget(null);
            recruit.setLastHurtByPlayer(null);
            recruit.setLastHurtMob(null);
            recruit.setLastHurtByMob(null);
        }
    }

    public static void onClearUpkeepButton(UUID playerUuid, AbstractRecruitEntity recruit, UUID group) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            recruit.clearUpkeepEntity();
            recruit.clearUpkeepPos();
        }
    }

    public static void onUpkeepCommand(UUID playerUuid, AbstractRecruitEntity recruit, UUID group, boolean isEntity, UUID entityUuid, BlockPos blockPos) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            if (isEntity) {
                recruit.setUpkeepUUID(Optional.of(entityUuid));
                recruit.clearUpkeepPos();
            } else {
                recruit.setUpkeepPos(blockPos);
                recruit.clearUpkeepEntity();
            }
            recruit.forcedUpkeep = true;
            recruit.setUpkeepTimer(0);
            onClearTargetButton(playerUuid, recruit, group);
        }
    }

    public static void onShieldsCommand(Player player, UUID playerUuid, AbstractRecruitEntity recruit, UUID group, boolean shields) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            recruit.setShouldBlock(shields);
        }
    }

    public static void onRangedFireCommand(ServerPlayer serverPlayer, UUID playerUuid, AbstractRecruitEntity recruit, UUID group, boolean should) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            recruit.setShouldRanged(should);

            if (should) {
                if (recruit instanceof CrossBowmanEntity) {
                    recruit.switchMainHandItem(itemStack -> itemStack.getItem() instanceof CrossbowItem);
                }
                if (recruit instanceof BowmanEntity) {
                    recruit.switchMainHandItem(itemStack -> itemStack.getItem() instanceof BowItem);
                }
            } else {
                recruit.switchMainHandItem(itemStack -> itemStack.getItem() instanceof SwordItem);
            }
        }
    }

    public static void onRestCommand(ServerPlayer serverPlayer, UUID playerUuid, AbstractRecruitEntity recruit, UUID group, boolean should) {
        if (recruit.isEffectedByCommand(playerUuid, group)) {
            onClearTargetButton(playerUuid, recruit, group);
            recruit.setShouldRest(should);
        }
    }

    private static MutableComponent textHireCosts(String name, int sollPrice, Item item) {
        return Component.translatable("chat.recruits.text.hire_costs", name, String.valueOf(sollPrice), item.getDescription().getString());
    }
}
