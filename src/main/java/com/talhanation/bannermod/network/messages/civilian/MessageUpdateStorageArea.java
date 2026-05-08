package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsAuthoringState;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementService;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageUpdateStorageArea implements BannerModMessage<MessageUpdateStorageArea> {

    public UUID uuid;
    public int mask;
    public String name;
    public String routeDestination;
    public String routeFilter;
    public String routeCount;
    public String routePriority;
    public boolean portEntrypoint;
    public MessageUpdateStorageArea() {

    }

    public MessageUpdateStorageArea(UUID uuid, int mask, String name, String routeDestination, String routeFilter, String routeCount, String routePriority, boolean portEntrypoint) {
        this.uuid = uuid;
        this.mask = mask;
        this.name = name;
        this.routeDestination = routeDestination;
        this.routeFilter = routeFilter;
        this.routeCount = routeCount;
        this.routePriority = routePriority;
        this.portEntrypoint = portEntrypoint;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if(player == null) return;

            StorageArea storageArea = WorkAreaMessageSupport.resolveAuthorizedWorkArea(player, this.uuid, StorageArea.class);
            if (storageArea == null) {
                return;
            }

            this.update(storageArea, player);
        });
    }

    public void update(StorageArea storageArea, ServerPlayer player){
        storageArea.setStorageTypes(mask);
        storageArea.setCustomName(Component.literal(name));
        storageArea.setPortEntrypoint(this.portEntrypoint);
        try {
            storageArea.setLogisticsRoute(BannerModLogisticsAuthoringState.parse(this.routeDestination, this.routeFilter, this.routeCount, this.routePriority));
            storageArea.clearRouteBlockedState();
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()));
        }

        if (player.level() instanceof ServerLevel serverLevel && ClaimEvents.claimManager() != null) {
            SettlementService.refreshClaimAt(
                    serverLevel,
                    ClaimEvents.claimManager(),
                    SettlementManager.get(serverLevel),
                    BannerModGovernorManager.get(serverLevel),
                    storageArea.blockPosition()
            );
        }
    }
    public MessageUpdateStorageArea fromBytes(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.mask = buf.readInt();
        this.name = buf.readUtf();
        this.routeDestination = buf.readUtf();
        this.routeFilter = buf.readUtf();
        this.routeCount = buf.readUtf();
        this.routePriority = buf.readUtf();
        this.portEntrypoint = buf.readBoolean();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeInt(mask);
        buf.writeUtf(name);
        buf.writeUtf(routeDestination == null ? "" : routeDestination);
        buf.writeUtf(routeFilter == null ? "" : routeFilter);
        buf.writeUtf(routeCount == null ? "" : routeCount);
        buf.writeUtf(routePriority == null ? "" : routePriority);
        buf.writeBoolean(portEntrypoint);
    }

}
