package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageUpdateGovernorPolicy implements BannerModMessage<MessageUpdateGovernorPolicy> {
    private UUID recruit;
    private int policyOrdinal;
    private int value;

    public MessageUpdateGovernorPolicy() {
    }

    public MessageUpdateGovernorPolicy(UUID recruit, BannerModGovernorPolicy policy, int value) {
        this.recruit = recruit;
        this.policyOrdinal = policy.ordinal();
        this.value = value;
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
            BannerModGovernorPolicy[] policies = BannerModGovernorPolicy.values();
            if (this.policyOrdinal < 0 || this.policyOrdinal >= policies.length) {
                return;
            }
            BannerModGovernorPolicy policy = policies[this.policyOrdinal];
            AbstractRecruitEntity recruitEntity = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(player, this.recruit, 16.0D);
            if (recruitEntity != null) {
                RecruitEvents.updateGovernorPolicy(player, recruitEntity, policy, this.value);
            }
        });
    }

    @Override
    public MessageUpdateGovernorPolicy fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.policyOrdinal = buf.readInt();
        this.value = buf.readInt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeInt(this.policyOrdinal);
        buf.writeInt(this.value);
    }
}
