package com.talhanation.bannermod.compat;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SmallShipsSeaTradeCompat {

    private static final List<ResourceLocation> SUPPORTED_VESSEL_TYPES = List.of(
            new ResourceLocation("smallships", "cog"),
            new ResourceLocation("smallships", "galley"),
            new ResourceLocation("smallships", "drakkar"),
            new ResourceLocation("smallships", "rowboat"),
            new ResourceLocation("smallships", "brigg"),
            new ResourceLocation("smallships", "dhow")
    );

    private SmallShipsSeaTradeCompat() {
    }

    public static List<CarrierCandidate> candidateCarrierTypes() {
        return candidateCarrierTypes(BannerModMain.isSmallShipsLoaded, ForgeRegistries.ENTITY_TYPES.getKeys());
    }

    public static boolean hasBindableCarrierCandidate() {
        return hasBindableCarrierCandidate(BannerModMain.isSmallShipsLoaded, ForgeRegistries.ENTITY_TYPES.getKeys(), Class::forName);
    }

    static boolean hasBindableCarrierCandidate(boolean smallShipsLoaded,
                                               Collection<ResourceLocation> registeredEntityTypes,
                                               SmallShips.ReflectiveClassResolver classResolver) {
        return !candidateCarrierTypes(smallShipsLoaded, registeredEntityTypes).isEmpty()
                && SmallShips.hasSmallShipEntityClass(classResolver);
    }

    static List<CarrierCandidate> candidateCarrierTypes(boolean smallShipsLoaded, Collection<ResourceLocation> registeredEntityTypes) {
        if (!smallShipsLoaded || registeredEntityTypes == null || registeredEntityTypes.isEmpty()) {
            return List.of();
        }

        return SUPPORTED_VESSEL_TYPES.stream()
                .filter(registeredEntityTypes::contains)
                .map(CarrierCandidate::new)
                .toList();
    }

    public static Optional<BoundCarrier> bindCarrier(@Nullable Entity entity, UUID routeOwnerId) {
        if (entity == null) return Optional.empty();

        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return bindCarrier(entity, typeId, entity.getUUID(), routeOwnerId, BannerModMain.isSmallShipsLoaded, Class::forName);
    }

    static Optional<BoundCarrier> bindCarrier(@Nullable Object entity,
                                             @Nullable ResourceLocation typeId,
                                             UUID carrierId,
                                             UUID routeOwnerId,
                                             boolean smallShipsLoaded,
                                             SmallShips.ReflectiveClassResolver classResolver) {
        if (!smallShipsLoaded || entity == null || typeId == null || carrierId == null || routeOwnerId == null) {
            return Optional.empty();
        }
        if (!SUPPORTED_VESSEL_TYPES.contains(typeId)) {
            return Optional.empty();
        }
        if (!SmallShips.isSmallShipEntity(entity, classResolver)) {
            return Optional.empty();
        }
        return Optional.of(new BoundCarrier(typeId, carrierId, routeOwnerId));
    }

    public record CarrierCandidate(ResourceLocation entityTypeId) {
    }

    public record BoundCarrier(ResourceLocation entityTypeId, UUID carrierId, UUID routeOwnerId) {
    }
}
