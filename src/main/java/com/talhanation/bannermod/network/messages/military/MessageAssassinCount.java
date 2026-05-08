package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.AssassinLeaderEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.Objects;
import java.util.UUID;

public class MessageAssassinCount implements BannerModMessage<MessageAssassinCount> {

    private int count;
    private UUID uuid;

    public MessageAssassinCount(){
    }

    public MessageAssassinCount(int count, UUID uuid) {
        this.count = count;
        this.uuid = uuid;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer player = Objects.requireNonNull(context.getSender());
            Entity entity = player.serverLevel().getEntity(this.uuid);
            if (entity instanceof AssassinLeaderEntity leader
                    && (leader.isControlledBy(player) || player.hasPermissions(2))
                    && player.getBoundingBox().inflate(16.0D).intersects(leader.getBoundingBox())) {
                leader.setCount(this.count);
            }
        });
    }
    public MessageAssassinCount fromBytes(FriendlyByteBuf buf) {
        this.count = buf.readInt();
        this.uuid = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(count);
        buf.writeUUID(uuid);
    }

}
