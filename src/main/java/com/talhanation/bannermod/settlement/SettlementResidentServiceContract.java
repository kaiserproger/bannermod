package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public record SettlementResidentServiceContract(
        SettlementServiceActorState actorState,
        @Nullable UUID serviceBuildingUuid,
        @Nullable String serviceBuildingTypeId
) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ActorState", this.actorState.name());
        if (this.serviceBuildingUuid != null) {
            tag.putUUID("ServiceBuildingUuid", this.serviceBuildingUuid);
        }
        if (this.serviceBuildingTypeId != null && !this.serviceBuildingTypeId.isBlank()) {
            tag.putString("ServiceBuildingTypeId", this.serviceBuildingTypeId);
        }
        return tag;
    }

    public static SettlementResidentServiceContract fromTag(CompoundTag tag) {
        SettlementServiceActorState actorState = tag.contains("ActorState", Tag.TAG_STRING)
                ? SettlementServiceActorState.fromTagName(tag.getString("ActorState"))
                : SettlementServiceActorState.NOT_SERVICE_ACTOR;
        UUID serviceBuildingUuid = tag.hasUUID("ServiceBuildingUuid") ? tag.getUUID("ServiceBuildingUuid") : null;
        String serviceBuildingTypeId = tag.contains("ServiceBuildingTypeId", Tag.TAG_STRING)
                ? tag.getString("ServiceBuildingTypeId")
                : null;
        return new SettlementResidentServiceContract(actorState, serviceBuildingUuid, serviceBuildingTypeId);
    }

    public static SettlementResidentServiceContract defaultFor(SettlementResidentRole role,
                                                                        SettlementResidentMode residentMode,
                                                                        SettlementResidentAssignmentState assignmentState,
                                                                        @Nullable UUID boundWorkAreaUuid,
                                                                        @Nullable String serviceBuildingTypeId) {
        if (role != SettlementResidentRole.CONTROLLED_WORKER
                || residentMode != SettlementResidentMode.PROJECTED_CONTROLLED_WORKER) {
            return notServiceActor();
        }
        return switch (assignmentState) {
            case ASSIGNED_LOCAL_BUILDING -> new SettlementResidentServiceContract(
                    SettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                    boundWorkAreaUuid,
                    serviceBuildingTypeId
            );
            case ASSIGNED_MISSING_BUILDING -> new SettlementResidentServiceContract(
                    SettlementServiceActorState.ORPHANED_SERVICE,
                    boundWorkAreaUuid,
                    null
            );
            case UNASSIGNED -> new SettlementResidentServiceContract(
                    SettlementServiceActorState.FLOATING_SERVICE,
                    null,
                    null
            );
            default -> notServiceActor();
        };
    }

    public static SettlementResidentServiceContract notServiceActor() {
        return new SettlementResidentServiceContract(SettlementServiceActorState.NOT_SERVICE_ACTOR, null, null);
    }
}
