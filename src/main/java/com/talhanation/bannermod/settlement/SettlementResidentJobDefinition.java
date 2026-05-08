package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public record SettlementResidentJobDefinition(
        SettlementJobHandlerSeed handlerSeed,
        @Nullable UUID targetBuildingUuid,
        @Nullable String targetBuildingTypeId,
        @Nullable SettlementBuildingCategory targetBuildingCategory,
        @Nullable SettlementBuildingProfileSeed targetBuildingProfileSeed
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

    public static SettlementResidentJobDefinition fromTag(CompoundTag tag) {
        SettlementJobHandlerSeed handlerSeed = tag.contains("HandlerSeed", Tag.TAG_STRING)
                ? SettlementJobHandlerSeed.fromTagName(tag.getString("HandlerSeed"))
                : SettlementJobHandlerSeed.NONE;
        UUID targetBuildingUuid = tag.hasUUID("TargetBuildingUuid") ? tag.getUUID("TargetBuildingUuid") : null;
        String targetBuildingTypeId = tag.contains("TargetBuildingTypeId", Tag.TAG_STRING)
                ? tag.getString("TargetBuildingTypeId")
                : null;
        SettlementBuildingCategory targetBuildingCategory = tag.contains("TargetBuildingCategory", Tag.TAG_STRING)
                ? SettlementBuildingCategory.fromTagName(tag.getString("TargetBuildingCategory"))
                : null;
        SettlementBuildingProfileSeed targetBuildingProfileSeed = tag.contains("TargetBuildingProfileSeed", Tag.TAG_STRING)
                ? SettlementBuildingProfileSeed.fromTagName(tag.getString("TargetBuildingProfileSeed"))
                : null;
        return new SettlementResidentJobDefinition(handlerSeed, targetBuildingUuid, targetBuildingTypeId, targetBuildingCategory, targetBuildingProfileSeed);
    }

    public static SettlementResidentJobDefinition defaultFor(SettlementResidentRole role,
                                                                      SettlementResidentRuntimeRoleState runtimeRoleState,
                                                                      SettlementResidentServiceContract serviceContract,
                                                                      @Nullable SettlementBuildingRecord building) {
        return switch (runtimeRoleState) {
            case VILLAGE_LIFE -> new SettlementResidentJobDefinition(SettlementJobHandlerSeed.VILLAGE_LIFE, null, null, null, null);
            case GOVERNANCE -> new SettlementResidentJobDefinition(SettlementJobHandlerSeed.GOVERNANCE, null, null, null, null);
            case LOCAL_LABOR -> new SettlementResidentJobDefinition(
                    resolveLocalLaborHandler(role, serviceContract),
                    serviceContract.serviceBuildingUuid(),
                    resolveBuildingTypeId(serviceContract, building),
                    building == null ? null : building.buildingCategory(),
                    building == null ? null : building.buildingProfileSeed()
            );
            case FLOATING_LABOR -> new SettlementResidentJobDefinition(SettlementJobHandlerSeed.FLOATING_LABOR_POOL, null, null, null, null);
            case ORPHANED_LABOR_ASSIGNMENT -> new SettlementResidentJobDefinition(
                    SettlementJobHandlerSeed.ORPHANED_LABOR_RECOVERY,
                    serviceContract.serviceBuildingUuid(),
                    serviceContract.serviceBuildingTypeId(),
                    null,
                    null
            );
        };
    }

    private static SettlementJobHandlerSeed resolveLocalLaborHandler(SettlementResidentRole role,
                                                                              SettlementResidentServiceContract serviceContract) {
        if (role == SettlementResidentRole.CONTROLLED_WORKER
                && serviceContract.actorState() == SettlementServiceActorState.LOCAL_BUILDING_SERVICE) {
            return SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR;
        }
        return SettlementJobHandlerSeed.NONE;
    }

    private static String resolveBuildingTypeId(SettlementResidentServiceContract serviceContract,
                                                @Nullable SettlementBuildingRecord building) {
        if (building != null) {
            return building.buildingTypeId();
        }
        return serviceContract.serviceBuildingTypeId();
    }

    public static SettlementResidentJobDefinition none() {
        return new SettlementResidentJobDefinition(SettlementJobHandlerSeed.NONE, null, null, null, null);
    }
}
