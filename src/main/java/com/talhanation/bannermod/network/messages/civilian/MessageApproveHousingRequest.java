package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.society.NpcHousingRequestAccess;
import com.talhanation.bannermod.society.NpcHousingRequestRecord;
import com.talhanation.bannermod.society.NpcHousingRequestStatus;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class MessageApproveHousingRequest implements BannerModMessage<MessageApproveHousingRequest> {
    private UUID householdId;

    public MessageApproveHousingRequest() {
    }

    public MessageApproveHousingRequest(UUID householdId) {
        this.householdId = householdId;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        ServerPlayer player = context.getSender();
        ServerLevel level = MessageRequestHamletSnapshot.serverLevel(player);
        if (player == null || level == null || this.householdId == null) {
            return;
        }
        NpcHousingRequestRecord request = NpcHousingRequestAccess.requestForHousehold(level, this.householdId);
        if (request == null) {
            MessageRequestHamletSnapshot.sendSystemMessage(player, Component.translatable("gui.bannermod.society.housing_request.command.not_found"));
            MessageRequestHousingSnapshot.sendSnapshot(player, MessageRequestHousingSnapshot.buildSnapshot(player));
            return;
        }
        RecruitsClaim claim = MessageRequestHamletSnapshot.claimById(request.claimUuid());
        PoliticalEntityRecord owner = MessageRequestHamletSnapshot.ownerRecord(level, claim);
        if (!PoliticalEntityAuthority.canAct(player, owner)) {
            MessageRequestHamletSnapshot.sendSystemMessage(player, PoliticalEntityAuthority.denialReason(player.getUUID(), player.hasPermissions(2), owner));
            MessageRequestHousingSnapshot.sendSnapshot(player, MessageRequestHousingSnapshot.buildSnapshot(player));
            return;
        }
        if (request.status() == NpcHousingRequestStatus.FULFILLED) {
            MessageRequestHamletSnapshot.sendSystemMessage(player, Component.translatable("gui.bannermod.society.housing_request.command.fulfilled_locked"));
            MessageRequestHousingSnapshot.sendSnapshot(player, MessageRequestHousingSnapshot.buildSnapshot(player));
            return;
        }
        NpcHousingRequestRecord updated = NpcHousingRequestAccess.approveHousehold(level, this.householdId, level.getGameTime());
        Component plot = updated.reservedPlotPos() == null
                ? Component.literal("-")
                : Component.literal(updated.reservedPlotPos().getX() + " "
                + updated.reservedPlotPos().getY() + " "
                + updated.reservedPlotPos().getZ());
        MessageRequestHamletSnapshot.sendSystemMessage(player, Component.translatable(
                "gui.bannermod.society.housing_request.command.approved",
                shortId(updated.residentUuid()),
                plot
        ));
        MessageRequestHousingSnapshot.sendSnapshot(player, MessageRequestHousingSnapshot.buildSnapshot(player));
    }

    @Override
    public MessageApproveHousingRequest fromBytes(FriendlyByteBuf buf) {
        this.householdId = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.householdId);
    }

    private static String shortId(UUID uuid) {
        if (uuid == null) {
            return "?";
        }
        String raw = uuid.toString();
        return raw.length() > 8 ? raw.substring(0, 8) : raw;
    }
}
