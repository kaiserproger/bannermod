package com.talhanation.bannermod.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class MedievalBoomsticksCompat {
    private static final String MOD_ID = "musketmod";
    private static final ResourceLocation CARTRIDGE = new ResourceLocation(MOD_ID, "cartridge");
    private static final Map<String, FirearmContract> SUPPORTED_FIREARMS = Map.of(
            "musket", new FirearmContract(CARTRIDGE, MusketWeapon::new),
            "musket_with_bayonet", new FirearmContract(CARTRIDGE, MusketBayonetWeapon::new),
            "musket_with_scope", new FirearmContract(CARTRIDGE, MusketScopeWeapon::new),
            "blunderbuss", new FirearmContract(CARTRIDGE, BlunderbussWeapon::new),
            "pistol", new FirearmContract(CARTRIDGE, PistolWeapon::new)
    );

    private MedievalBoomsticksCompat() {
    }

    public static boolean isSupportedRecruitFirearm(ItemStack stack) {
        return contract(stack).isPresent();
    }

    public static boolean isSupportedRecruitItem(ItemStack stack) {
        return isSupportedRecruitFirearm(stack) || isAmmo(stack, CARTRIDGE);
    }

    public static boolean isMedievalBoomsticksItem(ItemStack stack) {
        ResourceLocation id = itemId(stack);
        return id != null && MOD_ID.equals(id.getNamespace());
    }

    public static Optional<IWeapon> createRecruitWeapon(ItemStack stack) {
        return contract(stack).map(FirearmContract::createWeapon);
    }

    public static Optional<ResourceLocation> ammoContract(ItemStack stack) {
        return contract(stack).map(FirearmContract::ammoId);
    }

    public static boolean isAmmo(ItemStack stack, ResourceLocation ammoId) {
        ResourceLocation id = itemId(stack);
        return ammoId.equals(id);
    }

    public static ItemStack createAmmoStack(ResourceLocation ammoId, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(ammoId);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = item.getDefaultInstance();
        stack.setCount(count);
        return stack;
    }

    private static Optional<FirearmContract> contract(ItemStack stack) {
        ResourceLocation id = itemId(stack);
        if (id == null || !MOD_ID.equals(id.getNamespace())) {
            return Optional.empty();
        }

        return Optional.ofNullable(SUPPORTED_FIREARMS.get(id.getPath()));
    }

    private static ResourceLocation itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        return ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    private record FirearmContract(ResourceLocation ammoId, Supplier<IWeapon> weaponFactory) {
        IWeapon createWeapon() {
            return weaponFactory.get();
        }
    }
}
