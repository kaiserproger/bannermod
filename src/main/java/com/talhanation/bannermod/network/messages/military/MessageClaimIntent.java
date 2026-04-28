package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public class MessageClaimIntent implements Message<MessageClaimIntent> {
    private byte action;
    private UUID claimUuid;
    private long chunkLong;

    public MessageClaimIntent() {
    }

    public MessageClaimIntent(Action action, UUID claimUuid, ChunkPos chunk) {
        this.action = (byte) action.ordinal();
        this.claimUuid = claimUuid;
        this.chunkLong = chunk == null ? 0L : chunk.toLong();
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        applyServerSide(player, decode(action), claimUuid, new ChunkPos(chunkLong));
    }

    public static boolean applyServerSide(ServerPlayer player, Action decoded, UUID claimUuid, ChunkPos chunk) {
        if (player == null || claimUuid == null) return false;
        if (!RecruitsServerConfig.AllowClaiming.get()) {
            deny(player, "Claim editing is disabled on this server.");
            return false;
        }
        if (player.level().dimension() != Level.OVERWORLD) {
            deny(player, "Claims can only be edited in the Overworld.");
            return false;
        }
        if (ClaimEvents.recruitsClaimManager == null) return false;
        RecruitsClaim claim = MessageUpdateClaim.getExistingClaim(claimUuid);
        if (claim == null) {
            deny(player, "Claim not found on server.");
            return false;
        }
        boolean admin = MessageUpdateClaim.isAdmin(player);
        if (!ClaimPacketAuthority.canEditClaim(player.getUUID(), admin, claim, MessageUpdateClaim.resolvePoliticalOwner(player, claim))) {
            deny(player, "You do not have authority to edit this claim.");
            return false;
        }
        if (decoded != Action.DELETE && tooFar(player, chunk)) {
            deny(player, "Claim edit target is too far away.");
            return false;
        }
        if (decoded == Action.ADD_CHUNK) {
            if (ClaimEvents.recruitsClaimManager.getClaim(chunk) != null) {
                deny(player, "Chunk is already claimed.");
                return false;
            }
            if (claim.getClaimedChunks().size() >= RecruitsClaim.MAX_SIZE) {
                deny(player, "Claim is already at max size.");
                return false;
            }
            int cost = RecruitsServerConfig.ChunkCost.get();
            if (!canPay(player, cost)) {
                deny(player, "Not enough currency for claim chunk.");
                return false;
            }
            charge(player, cost);
            claim.addChunk(chunk);
            recalculateCenter(claim);
            ClaimEvents.recruitsClaimManager.addOrUpdateClaim((ServerLevel) player.getCommandSenderWorld(), claim);
        } else if (decoded == Action.REMOVE_CHUNK) {
            if (!claim.containsChunk(chunk)) {
                deny(player, "Chunk is not part of this claim.");
                return false;
            }
            claim.removeChunk(chunk);
            recalculateCenter(claim);
            ClaimEvents.recruitsClaimManager.addOrUpdateClaim((ServerLevel) player.getCommandSenderWorld(), claim);
        } else if (decoded == Action.DELETE) {
            ClaimEvents.recruitsClaimManager.removeClaim(claim);
        }
        ClaimEvents.recruitsClaimManager.broadcastClaimsToAll((ServerLevel) player.getCommandSenderWorld());
        player.sendSystemMessage(Component.literal("Claim edit accepted: " + decoded.name().toLowerCase(java.util.Locale.ROOT)));
        return true;
    }

    private static boolean tooFar(ServerPlayer player, ChunkPos chunk) {
        int dx = Math.abs(player.chunkPosition().x - chunk.x);
        int dz = Math.abs(player.chunkPosition().z - chunk.z);
        return dx > 4 || dz > 4;
    }

    private static boolean canPay(ServerPlayer player, int amount) {
        return player.isCreative() && player.hasPermissions(2) || player.getInventory().countItem(currency()) >= amount;
    }

    private static void charge(ServerPlayer player, int amount) {
        if (player.isCreative() && player.hasPermissions(2)) return;
        Item currency = currency();
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(currency)) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    private static Item currency() {
        return ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(RecruitsServerConfig.RecruitCurrency.get()))
                .map(Holder::value)
                .orElse(Items.EMERALD);
    }

    private static void recalculateCenter(RecruitsClaim claim) {
        if (claim.getClaimedChunks().isEmpty()) return;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos pos : claim.getClaimedChunks()) {
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        claim.setCenter(new ChunkPos((minX + maxX) / 2, (minZ + maxZ) / 2));
    }

    private static Action decode(byte ordinal) {
        Action[] values = Action.values();
        return ordinal < 0 || ordinal >= values.length ? Action.ADD_CHUNK : values[ordinal];
    }

    private static void deny(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal("Claim edit denied: " + reason));
    }

    @Override
    public MessageClaimIntent fromBytes(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.claimUuid = buf.readUUID();
        this.chunkLong = buf.readLong();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeUUID(claimUuid);
        buf.writeLong(chunkLong);
    }

    public enum Action {
        ADD_CHUNK,
        REMOVE_CHUNK,
        DELETE
    }
}
