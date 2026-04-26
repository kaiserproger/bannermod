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

public class MessageAcceptContract implements Message<MessageAcceptContract> {
    private UUID recruit;
    private UUID contractId;

    public MessageAcceptContract() {
    }

    public MessageAcceptContract(UUID recruit, UUID contractId) {
        this.recruit = recruit;
        this.contractId = contractId;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        AbstractRecruitEntity recruitEntity = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.recruit, 32.0D);
        if (recruitEntity != null) {
            RecruitEvents.acceptContract(player, recruitEntity, this.contractId);
        }
    }

    @Override
    public MessageAcceptContract fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.contractId = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
        buf.writeUUID(contractId);
    }
}
