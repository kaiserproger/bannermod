package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public record SettlementResidentJobTargetSelectionState(
        SettlementJobTargetSelectionMode selectionMode,
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

    public static SettlementResidentJobTargetSelectionState fromTag(CompoundTag tag) {
        SettlementJobTargetSelectionMode selectionMode = tag.contains("SelectionMode", Tag.TAG_STRING)
                ? SettlementJobTargetSelectionMode.fromTagName(tag.getString("SelectionMode"))
                : SettlementJobTargetSelectionMode.NONE;
        UUID targetMarketUuid = tag.hasUUID("TargetMarketUuid") ? tag.getUUID("TargetMarketUuid") : null;
        String targetMarketName = tag.contains("TargetMarketName", Tag.TAG_STRING)
                ? tag.getString("TargetMarketName")
                : null;
        return new SettlementResidentJobTargetSelectionState(selectionMode, targetMarketUuid, targetMarketName);
    }

    public static SettlementResidentJobTargetSelectionState defaultFor(UUID residentUuid,
                                                                               SettlementResidentJobDefinition jobDefinition,
                                                                               SettlementResidentServiceContract serviceContract,
                                                                               SettlementMarketState marketState) {
        SettlementSellerDispatchRecord sellerDispatch = findSellerDispatch(residentUuid, marketState);
        if (sellerDispatch != null) {
            return new SettlementResidentJobTargetSelectionState(
                    sellerDispatch.dispatchState() == SettlementSellerDispatchState.READY
                            ? SettlementJobTargetSelectionMode.SELLER_MARKET_DISPATCH
                            : SettlementJobTargetSelectionMode.SELLER_MARKET_CLOSED,
                    sellerDispatch.marketUuid(),
                    sellerDispatch.marketName()
            );
        }

        return switch (jobDefinition.handlerSeed()) {
            case LOCAL_BUILDING_LABOR -> serviceContract.actorState() == SettlementServiceActorState.LOCAL_BUILDING_SERVICE
                    ? new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null)
                    : none();
            case FLOATING_LABOR_POOL -> new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.FLOATING_LABOR_POOL, null, null);
            case ORPHANED_LABOR_RECOVERY -> new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.ORPHANED_SERVICE_BUILDING, null, null);
            default -> none();
        };
    }

    public static SettlementResidentJobTargetSelectionState none() {
        return new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.NONE, null, null);
    }

    @Nullable
    private static SettlementSellerDispatchRecord findSellerDispatch(UUID residentUuid,
                                                                              SettlementMarketState marketState) {
        for (SettlementSellerDispatchRecord sellerDispatch : marketState.sellerDispatches()) {
            if (sellerDispatch.residentUuid().equals(residentUuid)) {
                return sellerDispatch;
            }
        }
        return null;
    }
}
