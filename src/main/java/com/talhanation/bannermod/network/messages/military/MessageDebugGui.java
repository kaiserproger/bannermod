package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.army.command.CommandRole;
import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.entity.military.runtime.DebugEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageDebugGui implements BannerModMessage<MessageDebugGui> {

    private int id;
    private UUID uuid;
    private String name;

    public MessageDebugGui() {
    }

    public MessageDebugGui(int id, UUID uuid, String name) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.uuid, 16.0D);
            if (recruit != null) {
                if (!shouldHandleDebugMessage(id, player, recruit)) {
                    return;
                }

                DebugEvents.handleMessage(id, recruit, player);
                recruit.setCustomName(Component.literal(name));
            }
        });
    }

    static boolean shouldHandleDebugMessage(int id, ServerPlayer player, AbstractRecruitEntity recruit) {
        return hasDebugAuthority(player, recruit);
    }

    static boolean shouldHandleDebugMessage(int id, UUID senderUuid, String senderTeamName, boolean senderOp, UUID recruitOwnerUuid, String recruitTeamName, boolean recruitOwned) {
        return hasDebugAuthority(senderUuid, senderTeamName, senderOp, recruitOwnerUuid, recruitTeamName, recruitOwned);
    }

    static boolean hasDebugAuthority(ServerPlayer player, AbstractRecruitEntity recruit) {
        return player.hasPermissions(2) || RecruitCommandAuthority.canDirectlyControl(player, recruit);
    }

    static boolean hasDebugAuthority(UUID senderUuid, String senderTeamName, boolean senderOp, UUID recruitOwnerUuid, String recruitTeamName, boolean recruitOwned) {
        return senderOp
                || CommandHierarchy.roleFor(senderUuid, senderTeamName, false, recruitOwnerUuid, recruitTeamName, recruitOwned) != CommandRole.NONE;
    }

    public MessageDebugGui fromBytes(FriendlyByteBuf buf) {
        this.id = buf.readInt();
        this.uuid = buf.readUUID();
        this.name = buf.readUtf();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(id);
        buf.writeUUID(uuid);
        buf.writeUtf(name);
    }
}
