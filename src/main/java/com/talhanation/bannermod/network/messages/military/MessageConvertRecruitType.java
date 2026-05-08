package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Player-initiated request to convert a base recruit (swordsman/bowman/pikeman/crossbowman/cavalry)
 * into a different base type. Server validates ownership via the same range-resolver as the rest
 * of the recruit packets, then delegates to {@link RecruitTypeConverter}.
 */
public class MessageConvertRecruitType implements BannerModMessage<MessageConvertRecruitType> {

    private UUID recruit;
    private byte targetKind;

    public MessageConvertRecruitType() {
    }

    public MessageConvertRecruitType(UUID recruit, RecruitTypeConverter.Kind kind) {
        this.recruit = recruit;
        this.targetKind = (byte) kind.ordinal();
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            AbstractRecruitEntity target = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(sender, this.recruit, 16.0D);
            if (target == null) return;
            // Ownership gate: only the recruit's owner (or an op in creative) can mutate type.
            if (!isOwnedBySender(target, sender)) return;
            if (!RecruitTypeConverter.isConvertibleBaseType(target)) return;
            RecruitTypeConverter.convert(target, RecruitTypeConverter.Kind.ofOrdinal(this.targetKind), sender);
        });
    }

    private static boolean isOwnedBySender(AbstractRecruitEntity recruit, ServerPlayer sender) {
        if (sender.isCreative() && sender.hasPermissions(2)) return true;
        UUID ownerUuid = recruit.getOwnerUUID();
        return ownerUuid != null && ownerUuid.equals(sender.getUUID());
    }

    @Override
    public MessageConvertRecruitType fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.targetKind = buf.readByte();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
        buf.writeByte(targetKind);
    }
}
