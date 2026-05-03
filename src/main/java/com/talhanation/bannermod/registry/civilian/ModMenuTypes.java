package com.talhanation.bannermod.registry.civilian;

import java.util.Arrays;
import java.util.UUID;
import javax.annotation.Nullable;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.civilian.gui.MerchantAddEditTradeScreen;
import com.talhanation.bannermod.client.civilian.gui.MerchantTradeScreen;
import com.talhanation.bannermod.client.civilian.gui.CitizenProfileScreen;
import com.talhanation.bannermod.entity.civilian.MerchantEntity;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.inventory.civilian.CitizenProfileMenu;
import com.talhanation.bannermod.inventory.civilian.MerchantAddEditTradeContainer;
import com.talhanation.bannermod.inventory.civilian.MerchantTradeContainer;
import com.talhanation.bannermod.persistence.civilian.WorkersMerchantTrade;
import com.talhanation.bannermod.society.NpcFamilyTreeSnapshot;
import com.talhanation.bannermod.society.NpcPhaseOneSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    private static final Logger logger = LogManager.getLogger(BannerModMain.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BannerModMain.MOD_ID);

    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MERCHANT_ADD_EDIT_TRADE_CONTAINER_TYPE.get(), MerchantAddEditTradeScreen::new);
        event.register(MERCHANT_TRADE_CONTAINER_TYPE.get(), MerchantTradeScreen::new);
        event.register(CITIZEN_PROFILE_CONTAINER_TYPE.get(), CitizenProfileScreen::new);
        logger.info("Civilian MenuScreens registered");
    }

    public static final DeferredHolder<MenuType<?>, MenuType<MerchantAddEditTradeContainer>> MERCHANT_ADD_EDIT_TRADE_CONTAINER_TYPE =
            MENU_TYPES.register("merchant_add_edit_trade_container", () -> IMenuTypeExtension.create((windowId, inv, data) -> {
                MerchantEntity merchant = (MerchantEntity) getRecruitByUUID(inv.player, data.readUUID());
                CompoundTag nbt = data.readNbt();
                if (merchant == null || nbt == null) {
                    return null;
                }
                WorkersMerchantTrade trade = WorkersMerchantTrade.fromNbt(nbt);
                return new MerchantAddEditTradeContainer(windowId, merchant, inv, trade);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<MerchantTradeContainer>> MERCHANT_TRADE_CONTAINER_TYPE =
            MENU_TYPES.register("merchant_trade_container", () -> IMenuTypeExtension.create((windowId, inv, data) -> {
                MerchantEntity merchant = (MerchantEntity) getRecruitByUUID(inv.player, data.readUUID());
                if (merchant == null) {
                    return null;
                }
                return new MerchantTradeContainer(windowId, merchant, inv);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<CitizenProfileMenu>> CITIZEN_PROFILE_CONTAINER_TYPE =
            MENU_TYPES.register("citizen_profile_container", () -> IMenuTypeExtension.create((windowId, inv, data) -> {
                CitizenEntity citizen = getCitizenByUUID(inv.player, data.readUUID());
                if (citizen == null) {
                    return null;
                }
                return new CitizenProfileMenu(
                        windowId,
                        citizen,
                        inv,
                        NpcPhaseOneSnapshot.fromBytes(data),
                        NpcFamilyTreeSnapshot.fromBytes(data)
                );
            }));

    @Nullable
    private static AbstractWorkerEntity getRecruitByUUID(Player player, UUID uuid) {
        double distance = 10D;
        AABB lookupBounds = new AABB(player.getX() - distance, player.getY() - distance, player.getZ() - distance,
                player.getX() + distance, player.getY() + distance, player.getZ() + distance);
        if (player.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
            if (serverLevel.getEntity(uuid) instanceof AbstractWorkerEntity worker
                    && lookupBounds.intersects(worker.getBoundingBox())) {
                return worker;
            }
            return null;
        }
        return player.getCommandSenderWorld().getEntitiesOfClass(AbstractWorkerEntity.class, lookupBounds,
                entity -> entity.getUUID().equals(uuid)).stream().findAny().orElse(null);
    }

    @Nullable
    private static CitizenEntity getCitizenByUUID(Player player, UUID uuid) {
        double distance = 12D;
        AABB lookupBounds = new AABB(player.getX() - distance, player.getY() - distance, player.getZ() - distance,
                player.getX() + distance, player.getY() + distance, player.getZ() + distance);
        if (player.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
            if (serverLevel.getEntity(uuid) instanceof CitizenEntity citizen && lookupBounds.intersects(citizen.getBoundingBox())) {
                return citizen;
            }
            return null;
        }
        return player.getCommandSenderWorld().getEntitiesOfClass(CitizenEntity.class, lookupBounds,
                entity -> entity.getUUID().equals(uuid)).stream().findAny().orElse(null);
    }
}
