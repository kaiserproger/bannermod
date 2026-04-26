package com.talhanation.bannermod.network.messages.war;

import com.talhanation.bannermod.war.runtime.WarAllyService;
import com.talhanation.bannermod.war.runtime.WarSide;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/**
 * Client → server: side leader invites a political entity to join one side of a war.
 * Mirrors {@code /bannermod war ally invite <warId> <side> <entity>}; both flows go
 * through {@link WarAllyService#invite}.
 */
public class MessageInviteAlly implements Message<MessageInviteAlly> {
    private UUID warId;
    private boolean attackerSide;
    private UUID inviteeEntityId;

    public MessageInviteAlly() {
    }

    public MessageInviteAlly(UUID warId, WarSide side, UUID inviteeEntityId) {
        this.warId = warId;
        this.attackerSide = side == WarSide.ATTACKER;
        this.inviteeEntityId = inviteeEntityId;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || this.warId == null || this.inviteeEntityId == null) return;
        ServerLevel level = player.serverLevel().getServer().overworld();
        if (level == null) return;
        WarSide side = attackerSide ? WarSide.ATTACKER : WarSide.DEFENDER;
        WarAllyService.InviteResult result = WarAllyService.invite(level, player, this.warId, side, this.inviteeEntityId);
        if (result.ok()) {
            player.sendSystemMessage(Component.literal("Ally invite issued."));
        } else {
            player.sendSystemMessage(Component.literal("Invite denied: " + result.outcome().token()));
        }
    }

    @Override
    public MessageInviteAlly fromBytes(FriendlyByteBuf buf) {
        this.warId = buf.readUUID();
        this.attackerSide = buf.readBoolean();
        this.inviteeEntityId = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.warId);
        buf.writeBoolean(this.attackerSide);
        buf.writeUUID(this.inviteeEntityId);
    }
}
