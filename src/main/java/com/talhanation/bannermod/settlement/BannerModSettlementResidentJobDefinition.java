package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public record BannerModSettlementResidentJobDefinition(
        BannerModSettlementJobHandlerSeed handlerSeed,
        @Nullable UUID targetBuildingUuid,
        @Nullable String targetBuildingTypeId,
        @Nullable BannerModSettlementBuildingCategory targetBuildingCategory,
        @Nullable BannerModSettlementBuildingProfileSeed targetBuildingProfileSeed
) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("HandlerSeed", this.handlerSeed.name());
        if (this.targetBuildingUuid != null) {
            tag.putUUID("TargetBuildingUuid", this.targetBuildingUuid);
        }
        if (this.targetBuildingTypeId != null && !this.targetBuildingTypeId.isBlank()) {
            tag.putString("TargetBuildingTypeId", this.targetBuildingTypeId);
        }
        if (this.targetBuildingCategory != null) {
            tag.putString("TargetBuildingCategory", this.targetBuildingCategory.name());
        }
        if (this.targetBuildingProfileSeed != null) {
            tag.putString("TargetBuildingProfileSeed", this.targetBuildingProfileSeed.name());
        }
        return tag;
    }

    public static BannerModSettlementResidentJobDefinition fromTag(CompoundTag tag) {
        BannerModSettlementJobHandlerSeed handlerSeed = tag.contains("HandlerSeed", Tag.TAG_STRING)
                ? BannerModSettlementJobHandlerSeed.fromTagName(tag.getString("HandlerSeed"))
                : BannerModSettlementJobHandlerSeed.NONE;
        UUID targetBuildingUuid = tag.hasUUID("TargetBuildingUuid") ? tag.getUUID("TargetBuildingUuid") : null;
        String targetBuildingTypeId = tag.contains("TargetBuildingTypeId", Tag.TAG_STRING)
                ? tag.getString("TargetBuildingTypeId")
                : null;
        BannerModSettlementBuildingCategory targetBuildingCategory = tag.contains("TargetBuildingCategory", Tag.TAG_STRING)
                ? BannerModSettlementBuildingCategory.fromTagName(tag.getString("TargetBuildingCategory"))
                : null;
        BannerModSettlementBuildingProfileSeed targetBuildingProfileSeed = tag.contains("TargetBuildingProfileSeed", Tag.TAG_STRING)
                ? BannerModSettlementBuildingProfileSeed.fromTagName(tag.getString("TargetBuildingProfileSeed"))
                : null;
        return new BannerModSettlementResidentJobDefinition(handlerSeed, targetBuildingUuid, targetBuildingTypeId, targetBuildingCategory, targetBuildingProfileSeed);
    }

    public static BannerModSettlementResidentJobDefinition defaultFor(BannerModSettlementResidentRole role,
                                                                      BannerModSettlementResidentRuntimeRoleState runtimeRoleState,
                                                                      BannerModSettlementResidentServiceContract serviceContract,
                                                                      @Nullable BannerModSettlementBuildingRecord building) {
        return switch (runtimeRoleState) {
            case VILLAGE_LIFE -> new BannerModSettlementResidentJobDefinition(BannerModSettlementJobHandlerSeed.VILLAGE_LIFE, null, null, null, null);
            case GOVERNANCE -> new BannerModSettlementResidentJobDefinition(BannerModSettlementJobHandlerSeed.GOVERNANCE, null, null, null, null);
            case LOCAL_LABOR -> new BannerModSettlementResidentJobDefinition(
                    resolveLocalLaborHandler(role, serviceContract),
                    serviceContract.serviceBuildingUuid(),
                    resolveBuildingTypeId(serviceContract, building),
                    building == null ? null : building.buildingCategory(),
                    building == null ? null : building.buildingProfileSeed()
            );
            case FLOATING_LABOR -> new BannerModSettlementResidentJobDefinition(BannerModSettlementJobHandlerSeed.FLOATING_LABOR_POOL, null, null, null, null);
            case ORPHANED_LABOR_ASSIGNMENT -> new BannerModSettlementResidentJobDefinition(
                    BannerModSettlementJobHandlerSeed.ORPHANED_LABOR_RECOVERY,
                    serviceContract.serviceBuildingUuid(),
                    serviceContract.serviceBuildingTypeId(),
                    null,
                    null
            );
        };
    }

    private static BannerModSettlementJobHandlerSeed resolveLocalLaborHandler(BannerModSettlementResidentRole role,
                                                                              BannerModSettlementResidentServiceContract serviceContract) {
        if (role == BannerModSettlementResidentRole.CONTROLLED_WORKER
                && serviceContract.actorState() == BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE) {
            return BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR;
        }
        return BannerModSettlementJobHandlerSeed.NONE;
    }

    private static String resolveBuildingTypeId(BannerModSettlementResidentServiceContract serviceContract,
                                                @Nullable BannerModSettlementBuildingRecord building) {
        if (building != null) {
            return building.buildingTypeId();
        }
        return serviceContract.serviceBuildingTypeId();
    }

    public static BannerModSettlementResidentJobDefinition none() {
        return new BannerModSettlementResidentJobDefinition(BannerModSettlementJobHandlerSeed.NONE, null, null, null, null);
    }
}
