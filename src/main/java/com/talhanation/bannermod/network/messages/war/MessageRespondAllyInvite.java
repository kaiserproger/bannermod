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
 * Client → server: invitee leader accepts (or declines) an outstanding ally
 * invitation. Mirrors {@code /bannermod war ally accept|decline}.
 */
public class MessageRespondAllyInvite implements Message<MessageRespondAllyInvite> {
    private UUID inviteId;
    private boolean accept;

    public MessageRespondAllyInvite() {
    }

    public MessageRespondAllyInvite(UUID inviteId, boolean accept) {
        this.inviteId = inviteId;
        this.accept = accept;
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
        WarAllyService.InviteResult result = accept
                ? WarAllyService.accept(level, player, this.inviteId)
                : WarAllyService.decline(level, player, this.inviteId);
        if (result.ok()) {
            player.sendSystemMessage(Component.literal(accept ? "Joined as ally." : "Invite declined."));
        } else {
            player.sendSystemMessage(Component.literal((accept ? "Accept" : "Decline")
                    + " denied: " + result.outcome().token()));
        }
    }

    @Override
    public MessageRespondAllyInvite fromBytes(FriendlyByteBuf buf) {
        this.inviteId = buf.readUUID();
        this.accept = buf.readBoolean();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.inviteId);
        buf.writeBoolean(this.accept);
    }
}
