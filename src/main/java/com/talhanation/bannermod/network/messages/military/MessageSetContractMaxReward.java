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

public class MessageSetContractMaxReward implements Message<MessageSetContractMaxReward> {
    private UUID recruit;
    private int maxReward;

    public MessageSetContractMaxReward() {
    }

    public MessageSetContractMaxReward(UUID recruit, int maxReward) {
        this.recruit = recruit;
        this.maxReward = maxReward;
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
            RecruitEvents.setContractMaxReward(player, recruitEntity, this.maxReward);
        }
    }

    @Override
    public MessageSetContractMaxReward fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.maxReward = buf.readInt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
        buf.writeInt(maxReward);
    }
}
