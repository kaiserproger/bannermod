package com.talhanation.bannermod.network.messages.war;

import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.war.registry.PoliticalStatePromotionPolicy;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.Optional;
import java.util.UUID;

public class MessageSetPoliticalEntityStatus implements BannerModMessage<MessageSetPoliticalEntityStatus> {
    private UUID entityId;
    private byte statusOrdinal;

    public MessageSetPoliticalEntityStatus() {
    }

    public MessageSetPoliticalEntityStatus(UUID entityId, PoliticalEntityStatus status) {
        this.entityId = entityId;
        this.statusOrdinal = status == null ? (byte) PoliticalEntityStatus.SETTLEMENT.ordinal() : (byte) status.ordinal();
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || this.entityId == null) {
                return;
            }
            ServerLevel level = player.serverLevel().getServer().overworld();
            PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
            Optional<PoliticalEntityRecord> recordOpt = registry.byId(this.entityId);
            if (recordOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("Cannot update status: state not found."));
                return;
            }
            PoliticalEntityRecord record = recordOpt.get();
            if (!PoliticalEntityAuthority.canAct(player.getUUID(), player.hasPermissions(2), record)) {
                player.sendSystemMessage(Component.literal(PoliticalEntityAuthority.DENIAL_NOT_AUTHORIZED));
                return;
            }
            PoliticalEntityStatus status = decodeStatus(this.statusOrdinal);
            if (status == PoliticalEntityStatus.STATE && record.status() != PoliticalEntityStatus.STATE) {
                PoliticalStatePromotionPolicy.Result promotion = PoliticalStatePromotionPolicy.evaluate(
                        SettlementManager.get(level).getAllSnapshots().stream()
                                .filter(snapshot -> record.id().toString().equals(snapshot.settlementFactionId()))
                                .findFirst()
                                .orElse(null));
                if (!promotion.allowed()) {
                    player.sendSystemMessage(Component.literal(promotion.denialReason()));
                    return;
                }
            }
            if (!registry.updateStatus(this.entityId, status)) {
                player.sendSystemMessage(Component.literal("Failed to update status."));
                return;
            }
            player.sendSystemMessage(Component.literal("Status of " + record.name() + " set to " + status.name() + "."));
        });
    }

    private static PoliticalEntityStatus decodeStatus(byte ordinal) {
        PoliticalEntityStatus[] values = PoliticalEntityStatus.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return PoliticalEntityStatus.SETTLEMENT;
        }
        return values[ordinal];
    }

    @Override
    public MessageSetPoliticalEntityStatus fromBytes(FriendlyByteBuf buf) {
        this.entityId = buf.readUUID();
        this.statusOrdinal = buf.readByte();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.entityId);
        buf.writeByte(this.statusOrdinal);
    }
}
