package com.talhanation.bannermod.network.messages.war;

import com.talhanation.bannermod.war.runtime.WarAllyService;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/**
 * Client → server: side leader cancels an outstanding invitation they issued.
 * Mirrors {@code /bannermod war ally cancel}.
 */
public class MessageCancelAllyInvite implements Message<MessageCancelAllyInvite> {
    private UUID inviteId;

    public MessageCancelAllyInvite() {
    }

    public MessageCancelAllyInvite(UUID inviteId) {
        this.inviteId = inviteId;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || this.inviteId == null) return;
        ServerLevel level = player.serverLevel().getServer().overworld();
        if (level == null) return;
        WarAllyService.InviteResult result = WarAllyService.cancel(level, player, this.inviteId);
        if (result.ok()) {
            player.sendSystemMessage(Component.literal("Invite cancelled."));
        } else {
            player.sendSystemMessage(Component.literal("Cancel denied: " + result.outcome().token()));
        }
    }

    @Override
    public MessageCancelAllyInvite fromBytes(FriendlyByteBuf buf) {
        this.inviteId = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.inviteId);
    }
}
