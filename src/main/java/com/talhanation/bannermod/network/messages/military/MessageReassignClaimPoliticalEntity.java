package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * WORLDMAPCLAIMPE-001 — server-bound packet that reassigns the owning political
 * entity of an existing claim. Server-authoritative: the server re-validates
 * authority on BOTH the source PE (via {@link ClaimPacketAuthority#canEditClaim})
 * AND the target PE (via {@link PoliticalEntityAuthority#canAct}). Admins (op +
 * creative) bypass the political checks.
 *
 * <p>A null target id detaches the claim from any political entity; this is only
 * permitted for admins (handled implicitly by canEditClaim's admin override).
 */
public class MessageReassignClaimPoliticalEntity implements BannerModMessage<MessageReassignClaimPoliticalEntity> {

    private UUID claimUuid;
    @Nullable
    private UUID targetPoliticalEntityId;

    public MessageReassignClaimPoliticalEntity() {
    }

    public MessageReassignClaimPoliticalEntity(UUID claimUuid, @Nullable UUID targetPoliticalEntityId) {
        this.claimUuid = claimUuid;
        this.targetPoliticalEntityId = targetPoliticalEntityId;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            if (!RecruitsServerConfig.AllowClaiming.get()) return;
            if (sender.level().dimension() != Level.OVERWORLD) return;
            if (ClaimEvents.claimManager() == null) return;
            if (claimUuid == null) {
                sendDenial(sender, "chat.bannermod.claim.transfer.denied.missing");
                return;
            }

            ServerLevel level = (ServerLevel) sender.getCommandSenderWorld();
            RecruitsClaim existingClaim = MessageUpdateClaim.getExistingClaim(claimUuid);
            if (existingClaim == null) {
                sendDenial(sender, "chat.bannermod.claim.transfer.denied.missing");
                return;
            }

            boolean isAdmin = MessageUpdateClaim.isAdmin(sender);
            PoliticalEntityRecord sourcePeRecord = MessageUpdateClaim.resolvePoliticalOwner(sender, existingClaim);

            PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
            PoliticalEntityRecord targetPeRecord = null;
            if (targetPoliticalEntityId != null) {
                targetPeRecord = registry.byId(targetPoliticalEntityId).orElse(null);
                if (targetPeRecord == null) {
                    sendDenial(sender, "chat.bannermod.claim.transfer.denied.target_missing");
                    return;
                }
            }

            TransferResult transferResult = reassignClaimPoliticalEntity(
                    sender.getUUID(),
                    isAdmin,
                    existingClaim,
                    sourcePeRecord,
                    targetPeRecord,
                    targetPoliticalEntityId);
            if (!transferResult.transferred()) {
                if (transferResult.denialReasonKey() != null) {
                    sender.sendSystemMessage(Component.translatable(transferResult.denialKey())
                            .append(Component.literal(" "))
                            .append(Component.translatable(transferResult.denialReasonKey())));
                } else {
                    sendDenial(sender, transferResult.denialKey());
                }
                return;
            }
            ClaimEvents.claimManager().addOrUpdateClaim(level, existingClaim);

            String targetName = targetPeRecord != null
                    ? targetPeRecord.name()
                    : Component.translatable("chat.bannermod.claim.transfer.detached").getString();
            sender.sendSystemMessage(Component.translatable(
                    "chat.bannermod.claim.transfer.success",
                    existingClaim.getName(),
                    targetName));
        });
    }

    private static void sendDenial(ServerPlayer sender, String key) {
        sender.sendSystemMessage(Component.translatable(key));
    }

    static TransferResult reassignClaimPoliticalEntity(UUID actorUuid,
                                                       boolean admin,
                                                       RecruitsClaim existingClaim,
                                                       @Nullable PoliticalEntityRecord sourcePeRecord,
                                                       @Nullable PoliticalEntityRecord targetPeRecord,
                                                       @Nullable UUID targetPoliticalEntityId) {
        if (existingClaim == null) {
            return TransferResult.denied("chat.bannermod.claim.transfer.denied.missing");
        }
        if (targetPoliticalEntityId != null && targetPeRecord == null) {
            return TransferResult.denied("chat.bannermod.claim.transfer.denied.target_missing");
        }
        UUID currentOwnerId = existingClaim.getOwnerPoliticalEntityId();
        if (java.util.Objects.equals(currentOwnerId, targetPoliticalEntityId)) {
            return TransferResult.denied("chat.bannermod.claim.transfer.denied.same");
        }

        // Source authority — must be able to edit the existing claim.
        if (!ClaimPacketAuthority.canEditClaim(actorUuid, admin, existingClaim, sourcePeRecord)) {
            return TransferResult.denied("chat.bannermod.claim.transfer.denied.no_source_authority");
        }

        // Target authority — only enforced when not admin and a target PE exists.
        // Detaching to no-state requires admin; non-admin callers must always pick
        // a target PE in which they hold authority.
        if (!admin) {
            if (targetPeRecord == null) {
                return TransferResult.denied("chat.bannermod.claim.transfer.denied.no_target_authority");
            }
            if (!PoliticalEntityAuthority.canAct(actorUuid, false, targetPeRecord)) {
                return TransferResult.denied(
                        "chat.bannermod.claim.transfer.denied.no_target_authority",
                        PoliticalEntityAuthority.denialReasonKey(actorUuid, false, targetPeRecord));
            }
        }

        existingClaim.setOwnerPoliticalEntityId(targetPoliticalEntityId);
        return TransferResult.success();
    }

    record TransferResult(boolean transferred, @Nullable String denialKey, @Nullable String denialReasonKey) {
        static TransferResult success() {
            return new TransferResult(true, null, null);
        }

        static TransferResult denied(String denialKey) {
            return denied(denialKey, null);
        }

        static TransferResult denied(String denialKey, @Nullable String denialReasonKey) {
            return new TransferResult(false, denialKey, denialReasonKey);
        }
    }

    public MessageReassignClaimPoliticalEntity fromBytes(FriendlyByteBuf buf) {
        this.claimUuid = buf.readUUID();
        if (buf.readBoolean()) {
            this.targetPoliticalEntityId = buf.readUUID();
        } else {
            this.targetPoliticalEntityId = null;
        }
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(claimUuid);
        if (targetPoliticalEntityId != null) {
            buf.writeBoolean(true);
            buf.writeUUID(targetPoliticalEntityId);
        } else {
            buf.writeBoolean(false);
        }
    }
}
