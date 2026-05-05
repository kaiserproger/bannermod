package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.society.NpcHousingPriorityService;
import com.talhanation.bannermod.society.NpcHousingSnapshotContract;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class MessageRequestHousingSnapshot implements BannerModMessage<MessageRequestHousingSnapshot> {
    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        sendSnapshot(player, buildSnapshot(player));
    }

    static CompoundTag buildSnapshot(ServerPlayer player) {
        ServerLevel level = MessageRequestHamletSnapshot.serverLevel(player);
        if (level == null) {
            return NpcHousingSnapshotContract.encode(null, false,
                    "gui.bannermod.society.housing_request.command.no_claim", List.of());
        }
        RecruitsClaim claim = MessageRequestHamletSnapshot.currentClaim(player);
        if (claim == null) {
            return NpcHousingSnapshotContract.encode(null, false,
                    "gui.bannermod.society.housing_request.command.no_claim", List.of());
        }
        PoliticalEntityRecord owner = MessageRequestHamletSnapshot.ownerRecord(level, claim);
        boolean canManage = PoliticalEntityAuthority.canAct(player, owner);
        String denialKey = canManage ? "" : PoliticalEntityAuthority.denialReasonKey(player.getUUID(), player.hasPermissions(2), owner);
        return NpcHousingSnapshotContract.encode(
                claim.getUUID(),
                canManage,
                denialKey,
                NpcHousingPriorityService.activeEntriesForClaim(level, claim.getUUID(), level.getGameTime())
        );
    }

    static void sendSnapshot(ServerPlayer player, CompoundTag payload) {
        BannerModMain.SIMPLE_CHANNEL.send(
                BannerModPacketDistributor.PLAYER.with(() -> player),
                new MessageToClientUpdateHousingState(payload)
        );
    }

    @Override
    public MessageRequestHousingSnapshot fromBytes(FriendlyByteBuf buf) {
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }
}
