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

public class MessageOpenContractBoard implements Message<MessageOpenContractBoard> {
    private UUID recruit;

    public MessageOpenContractBoard() {
    }

    public MessageOpenContractBoard(UUID recruit) {
        this.recruit = recruit;
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
            RecruitEvents.openContractBoard(player, recruitEntity);
        }
    }

    @Override
    public MessageOpenContractBoard fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
    }
}
