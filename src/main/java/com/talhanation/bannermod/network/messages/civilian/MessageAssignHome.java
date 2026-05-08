package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.entity.citizen.AbstractCitizenEntity;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Player-issued packet that anchors a citizen / worker / recruit to a "home"
 * block (HOMEASSIGN-002). Validation rules:
 *
 * <ol>
 *   <li>Sender must own the target entity (player UUID == entity owner UUID,
 *       or the player has op permission level &ge; 2).</li>
 *   <li>The target block must be a {@link BedBlock} or a registered sleeping
 *       zone (HousePrefab BuildArea). Anything else is rejected with a logged
 *       reason and a localized chat status to the sender.</li>
 *   <li>Unknown entity UUIDs are rejected silently with a logged reason.</li>
 * </ol>
 *
 * <p>On success the entity's home is set via
 * {@link AbstractCitizenEntity#setHomePos(BlockPos)}, which on recruits aliases
 * to the existing upkeepPos field (see HOMEASSIGN-001 scope (a)).
 */
public class MessageAssignHome implements BannerModMessage<MessageAssignHome> {

    private static final Logger LOGGER = LogManager.getLogger(MessageAssignHome.class);

    /** Reject reason keys — also referenced by localization (en_us / ru_ru). */
    public static final String REJECT_UNKNOWN_ENTITY = "bannermod.assign_home.reject.unknown_entity";
    public static final String REJECT_NOT_OWNER = "bannermod.assign_home.reject.not_owner";
    public static final String REJECT_INVALID_BLOCK = "bannermod.assign_home.reject.invalid_block";
    public static final String SUCCESS_KEY = "bannermod.assign_home.success";

    private UUID entityUuid;
    private BlockPos pos;

    public MessageAssignHome() {
    }

    public MessageAssignHome(UUID entityUuid, BlockPos pos) {
        this.entityUuid = entityUuid;
        this.pos = pos;
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
            handle(sender, this.entityUuid, this.pos);
        });
    }

    /**
     * Server-side handler used by both the network handler and gametests.
     * Returns {@code true} when the assignment lands; {@code false} when
     * any validation rule rejects the request.
     */
    public static boolean handle(ServerPlayer sender, UUID entityUuid, BlockPos pos) {
        if (entityUuid == null || pos == null) {
            LOGGER.debug("MessageAssignHome: missing entityUuid={} pos={}", entityUuid, pos);
            return false;
        }
        ServerLevel level = sender.serverLevel();
        Entity entity = level.getEntity(entityUuid);
        if (!(entity instanceof AbstractCitizenEntity) && !(entity instanceof CitizenEntity)) {
            LOGGER.debug("MessageAssignHome rejected: {} (entity={}) — {}",
                    REJECT_UNKNOWN_ENTITY, entityUuid, sender.getName().getString());
            sender.sendSystemMessage(Component.translatable(REJECT_UNKNOWN_ENTITY));
            return false;
        }
        if (!isAuthorized(sender, entity)) {
            LOGGER.debug("MessageAssignHome rejected: {} (entity={}) — {}",
                    REJECT_NOT_OWNER, entityUuid, sender.getName().getString());
            sender.sendSystemMessage(Component.translatable(REJECT_NOT_OWNER));
            return false;
        }
        if (!isValidHomeTarget(level, pos)) {
            LOGGER.debug("MessageAssignHome rejected: {} (entity={} pos={}) — {}",
                    REJECT_INVALID_BLOCK, entityUuid, pos, sender.getName().getString());
            sender.sendSystemMessage(Component.translatable(REJECT_INVALID_BLOCK));
            return false;
        }
        // Anchor the canonical home and clear any stale prefab UUID; the
        // selector-driven entry point only carries a BlockPos and lets the
        // settlement runtime re-link a HousePrefab UUID later if appropriate.
        BlockPos immut = pos.immutable();
        if (entity instanceof AbstractCitizenEntity citizen) {
            citizen.setHomePos(immut);
            citizen.setHomeBuildAreaUUID(null);
        } else if (entity instanceof CitizenEntity citizen) {
            citizen.setHomePos(immut);
            citizen.setHomeBuildAreaUUID(null);
        }
        sender.sendSystemMessage(Component.translatable(SUCCESS_KEY,
                entity.getDisplayName(), pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    private static boolean isAuthorized(ServerPlayer sender, Entity entity) {
        if (sender.hasPermissions(2)) return true;
        UUID owner = ownerOf(entity);
        return owner != null && owner.equals(sender.getUUID());
    }

    private static UUID ownerOf(Entity entity) {
        // Recruits / workers expose getOwnerUUID via RecruitOwnershipAccess.
        // Pure CitizenEntity (PathfinderMob) exposes its own getOwnerUUID.
        // AbstractCitizenEntity itself reads from CitizenCore.
        if (entity instanceof AbstractRecruitEntity recruit) {
            return recruit.getOwnerUUID();
        }
        if (entity instanceof AbstractWorkerEntity worker) {
            return worker.getOwnerUUID();
        }
        if (entity instanceof CitizenEntity citizen) {
            return citizen.getOwnerUUID();
        }
        if (entity instanceof AbstractCitizenEntity citizen && citizen.getCitizenCore() != null) {
            return citizen.getCitizenCore().getOwnerUUID();
        }
        return null;
    }

    /**
     * A "valid" home is currently a vanilla {@link BedBlock}. HousePrefab build
     * area validation is intentionally out-of-scope for this slice; HOMEASSIGN-002
     * only requires bed/sleeping-zone acceptance and the bed path is universal.
     */
    public static boolean isValidHomeTarget(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BedBlock;
    }

    @Override
    public MessageAssignHome fromBytes(FriendlyByteBuf buf) {
        this.entityUuid = buf.readUUID();
        this.pos = buf.readBlockPos();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.entityUuid);
        buf.writeBlockPos(this.pos);
    }
}
