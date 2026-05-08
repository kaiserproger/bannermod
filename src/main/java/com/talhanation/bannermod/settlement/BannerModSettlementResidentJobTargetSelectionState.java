package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public record BannerModSettlementResidentJobTargetSelectionState(
        BannerModSettlementJobTargetSelectionMode selectionMode,
        @Nullable UUID targetMarketUuid,
        @Nullable String targetMarketName
) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("SelectionMode", this.selectionMode.name());
        if (this.targetMarketUuid != null) {
            tag.putUUID("TargetMarketUuid", this.targetMarketUuid);
        }
        if (this.targetMarketName != null && !this.targetMarketName.isBlank()) {
            tag.putString("TargetMarketName", this.targetMarketName);
        }
        return tag;
    }

    public static BannerModSettlementResidentJobTargetSelectionState fromTag(CompoundTag tag) {
        BannerModSettlementJobTargetSelectionMode selectionMode = tag.contains("SelectionMode", Tag.TAG_STRING)
                ? BannerModSettlementJobTargetSelectionMode.fromTagName(tag.getString("SelectionMode"))
                : BannerModSettlementJobTargetSelectionMode.NONE;
        UUID targetMarketUuid = tag.hasUUID("TargetMarketUuid") ? tag.getUUID("TargetMarketUuid") : null;
        String targetMarketName = tag.contains("TargetMarketName", Tag.TAG_STRING)
                ? tag.getString("TargetMarketName")
                : null;
        return new BannerModSettlementResidentJobTargetSelectionState(selectionMode, targetMarketUuid, targetMarketName);
    }

    public static BannerModSettlementResidentJobTargetSelectionState defaultFor(UUID residentUuid,
                                                                               BannerModSettlementResidentJobDefinition jobDefinition,
                                                                               BannerModSettlementResidentServiceContract serviceContract,
                                                                               BannerModSettlementMarketState marketState) {
        BannerModSettlementSellerDispatchRecord sellerDispatch = findSellerDispatch(residentUuid, marketState);
        if (sellerDispatch != null) {
            return new BannerModSettlementResidentJobTargetSelectionState(
                    sellerDispatch.dispatchState() == BannerModSettlementSellerDispatchState.READY
                            ? BannerModSettlementJobTargetSelectionMode.SELLER_MARKET_DISPATCH
                            : BannerModSettlementJobTargetSelectionMode.SELLER_MARKET_CLOSED,
                    sellerDispatch.marketUuid(),
                    sellerDispatch.marketName()
            );
        }

        return switch (jobDefinition.handlerSeed()) {
            case LOCAL_BUILDING_LABOR -> serviceContract.actorState() == BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE
                    ? new BannerModSettlementResidentJobTargetSelectionState(BannerModSettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null)
                    : none();
            case FLOATING_LABOR_POOL -> new BannerModSettlementResidentJobTargetSelectionState(BannerModSettlementJobTargetSelectionMode.FLOATING_LABOR_POOL, null, null);
            case ORPHANED_LABOR_RECOVERY -> new BannerModSettlementResidentJobTargetSelectionState(BannerModSettlementJobTargetSelectionMode.ORPHANED_SERVICE_BUILDING, null, null);
            default -> none();
        };
    }

    public static BannerModSettlementResidentJobTargetSelectionState none() {
        return new BannerModSettlementResidentJobTargetSelectionState(BannerModSettlementJobTargetSelectionMode.NONE, null, null);
    }

    @Nullable
    private static BannerModSettlementSellerDispatchRecord findSellerDispatch(UUID residentUuid,
                                                                              BannerModSettlementMarketState marketState) {
        for (BannerModSettlementSellerDispatchRecord sellerDispatch : marketState.sellerDispatches()) {
            if (sellerDispatch.residentUuid().equals(residentUuid)) {
                return sellerDispatch;
            }
        }
        return null;
    }
}
