package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.economy.FortLevelDefinition;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;


public class MessageUpdateClaim implements BannerModMessage<MessageUpdateClaim> {

    private CompoundTag claimNBT;

    public MessageUpdateClaim(){

    }

    public MessageUpdateClaim(RecruitsClaim claim) {
        this.claimNBT = claim.toNBT();
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            RecruitsClaim updatedClaim = RecruitsClaim.fromNBT(this.claimNBT);
            if(!RecruitsServerConfig.AllowClaiming.get()) return;
            if(sender.level().dimension() != Level.OVERWORLD) return;
            if (ClaimEvents.claimManager() == null) return;

            ServerLevel level = (ServerLevel) sender.getCommandSenderWorld();
            RecruitsClaim existingClaim = getExistingClaim(updatedClaim);
            boolean isAdmin = isAdmin(sender);
            if (existingClaim == null && !isAdmin) return;
            if (existingClaim != null && !ClaimPacketAuthority.canEditClaim(
                    sender.getUUID(),
                    isAdmin,
                    existingClaim,
                    resolvePoliticalOwner(sender, existingClaim))) return;
            if (!isAdmin && overlapsOtherClaim(updatedClaim, existingClaim)) return;

            preserveServerOwnedFields(updatedClaim, existingClaim);
            if (existingClaim != null && !isAdmin) {
                updatedClaim.setOwnerPoliticalEntityId(existingClaim.getOwnerPoliticalEntityId());
                updatedClaim.setPlayer(existingClaim.getPlayerInfo());
                updatedClaim.setAdminClaim(existingClaim.isAdmin);
            }
            if (updatedClaim.getPlayerInfo() != null) {
                updatedClaim.removeTrustedPlayer(updatedClaim.getPlayerInfo().getUUID());
            }
            if (ClaimEvents.claimManager().isTownTooCloseToSameNationTown(
                    updatedClaim,
                    existingClaim,
                    RecruitsServerConfig.TownMinCenterDistance.get())) return;

            ClaimEvents.claimManager().addOrUpdateClaim(level, updatedClaim);
        });
    }

    static RecruitsClaim getExistingClaim(RecruitsClaim updatedClaim) {
        if (updatedClaim == null || ClaimEvents.claimManager() == null) return null;
        return getExistingClaim(updatedClaim.getUUID());
    }

    static RecruitsClaim getExistingClaim(UUID claimUuid) {
        if (claimUuid == null || ClaimEvents.claimManager() == null) return null;
        for (RecruitsClaim claim : ClaimEvents.claimManager().getAllClaims()) {
            if (claim.getUUID().equals(claimUuid)) {
                return claim;
            }
        }
        return null;
    }

    static boolean isAdmin(ServerPlayer sender) {
        return sender != null && sender.isCreative() && sender.hasPermissions(2);
    }

    static PoliticalEntityRecord resolvePoliticalOwner(ServerPlayer sender, RecruitsClaim existingClaim) {
        if (sender == null || existingClaim == null) return null;
        UUID politicalEntityId = existingClaim.getOwnerPoliticalEntityId();
        if (politicalEntityId == null) return null;
        PoliticalRegistryRuntime registry = WarRuntimeContext.registry((ServerLevel) sender.getCommandSenderWorld());
        return registry.byId(politicalEntityId).orElse(null);
    }

    private static boolean overlapsOtherClaim(RecruitsClaim updatedClaim, RecruitsClaim existingClaim) {
        if (updatedClaim == null || updatedClaim.isRemoved || ClaimEvents.claimManager() == null) return false;
        for (ChunkPos pos : updatedClaim.getClaimedChunks()) {
            RecruitsClaim occupied = ClaimEvents.claimManager().getClaim(pos);
            if (occupied != null && (existingClaim == null || !occupied.getUUID().equals(existingClaim.getUUID()))) {
                return true;
            }
        }
        return false;
    }

    static void preserveServerOwnedFields(RecruitsClaim updatedClaim, RecruitsClaim existingClaim) {
        if (updatedClaim == null) return;
        updatedClaim.setFortLevel(existingClaim != null ? existingClaim.getFortLevel() : FortLevelDefinition.MIN_LEVEL);
    }

    public MessageUpdateClaim fromBytes(FriendlyByteBuf buf) {
        this.claimNBT = buf.readNbt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(claimNBT);
    }
}
