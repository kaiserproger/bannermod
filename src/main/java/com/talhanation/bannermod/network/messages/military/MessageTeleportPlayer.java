package com.talhanation.bannermod.network.messages.military;

import de.maxhenkel.corelib.net.Message;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

public class MessageTeleportPlayer implements Message<MessageTeleportPlayer> {

    private static final int LOW_HEIGHTMAP_OFFSET = 164;

    public BlockPos pos;
    public MessageTeleportPlayer() {
    }

    public MessageTeleportPlayer(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();

        if(player == null)return;
        if (!isAuthorized(player.isCreative(), player.hasPermissions(2))) return;

        BlockPos target = resolveSafeTeleportTarget(player.serverLevel(), pos);
        player.teleportTo(target.getX(), target.getY(), target.getZ());
    }

    static boolean isAuthorized(boolean creative, boolean hasPermission) {
        return creative && hasPermission;
    }

    static BlockPos resolveSafeTeleportTarget(ServerLevel level, BlockPos requested) {
        return correctSafeTeleportTarget(
                level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, requested),
                level.getMinBuildHeight()
        );
    }

    static BlockPos correctSafeTeleportTarget(BlockPos surface, int minBuildHeight) {
        BlockPos corrected = surface;
        if (corrected.getY() < minBuildHeight) {
            corrected = corrected.offset(0, LOW_HEIGHTMAP_OFFSET, 0);
        }
        int landingY = Math.max(corrected.getY() + 2, minBuildHeight + 1);
        return new BlockPos(corrected.getX(), landingY, corrected.getZ());
    }

    @Override
    public MessageTeleportPlayer fromBytes(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }
}
