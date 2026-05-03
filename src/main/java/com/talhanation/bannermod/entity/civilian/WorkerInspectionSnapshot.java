package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.society.NpcPhaseOneSnapshot;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.UUID;

public record WorkerInspectionSnapshot(
        int entityId,
        UUID workerUuid,
        String workerName,
        String professionKey,
        String ownerLabel,
        String politicalLabel,
        String claimRelationKey,
        String assignmentLabel,
        String problemLabel,
        String transportLabel,
        NpcPhaseOneSnapshot phaseOne,
        boolean canConvert,
        @Nullable String convertBlockedReasonKey,
        String currentProfessionTag
) {
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(workerUuid);
        buf.writeUtf(workerName);
        buf.writeUtf(professionKey);
        buf.writeUtf(ownerLabel);
        buf.writeUtf(politicalLabel);
        buf.writeUtf(claimRelationKey);
        buf.writeUtf(assignmentLabel);
        buf.writeUtf(problemLabel);
        buf.writeUtf(transportLabel);
        (phaseOne == null ? NpcPhaseOneSnapshot.empty() : phaseOne).toBytes(buf);
        buf.writeBoolean(canConvert);
        buf.writeBoolean(convertBlockedReasonKey != null);
        if (convertBlockedReasonKey != null) {
            buf.writeUtf(convertBlockedReasonKey);
        }
        buf.writeUtf(currentProfessionTag == null ? "" : currentProfessionTag);
    }

    public static WorkerInspectionSnapshot fromBytes(FriendlyByteBuf buf) {
        return new WorkerInspectionSnapshot(
                buf.readVarInt(),
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                NpcPhaseOneSnapshot.fromBytes(buf),
                buf.readBoolean(),
                buf.readBoolean() ? buf.readUtf() : null,
                buf.readUtf()
        );
    }
}
