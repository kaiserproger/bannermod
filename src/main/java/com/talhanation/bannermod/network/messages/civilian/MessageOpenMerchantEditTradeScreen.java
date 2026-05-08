package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.entity.civilian.MerchantAccessControl;
import com.talhanation.bannermod.entity.civilian.MerchantEntity;
import com.talhanation.bannermod.persistence.civilian.WorkersMerchantTrade;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageOpenMerchantEditTradeScreen implements BannerModMessage<MessageOpenMerchantEditTradeScreen> {
    private UUID player;
    private UUID merchantUuid;
    private CompoundTag nbt;
    public MessageOpenMerchantEditTradeScreen() {
        this.player = new UUID(0L, 0L);
    }

    public MessageOpenMerchantEditTradeScreen(Player player, UUID merchantUuid, WorkersMerchantTrade trade) {
        this.player = player.getUUID();
        this.merchantUuid = merchantUuid;
        this.nbt = trade.toNbt();
    }
    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }
    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!player.getUUID().equals(this.player)) {
                return;
            }
            Entity entity = player.serverLevel().getEntity(this.merchantUuid);
            if (entity instanceof MerchantEntity merchant
                    && merchant.isAlive()
                    && player.getBoundingBox().inflate(32.0D).intersects(merchant.getBoundingBox())) {
                if (!MerchantAccessControl.canManage(merchant.getOwnerUUID(), player.getUUID(), player.hasPermissions(2))) {
                    return;
                }
                merchant.openAddEditTradeGUI(player, WorkersMerchantTrade.fromNbt(nbt));
            }
        });
    }
    @Override
    public MessageOpenMerchantEditTradeScreen fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.merchantUuid = buf.readUUID();
        this.nbt = buf.readNbt();
        return this;
    }
    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUUID(this.merchantUuid);
        buf.writeNbt(this.nbt);
    }
}
