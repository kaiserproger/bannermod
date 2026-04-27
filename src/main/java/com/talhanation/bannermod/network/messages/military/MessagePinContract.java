package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;

public class MessagePinContract implements Message<MessagePinContract> {
    private UUID recruit;
    private UUID contractId;
    private boolean pinned;

    public MessagePinContract() {
    }

    public MessagePinContract(UUID recruit, UUID contractId, boolean pinned) {
        this.recruit = recruit;
        this.contractId = contractId;
        this.pinned = pinned;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        AbstractRecruitEntity recruitEntity = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.recruit, 16.0D);
        if (recruitEntity != null) {
            RecruitEvents.pinContract(player, recruitEntity, this.contractId, this.pinned);
        }
    }

    @Override
    public MessagePinContract fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.contractId = buf.readUUID();
        this.pinned = buf.readBoolean();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
        buf.writeUUID(contractId);
        buf.writeBoolean(pinned);
    }
}
