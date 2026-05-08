package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessagePromoteRecruit implements BannerModMessage<MessagePromoteRecruit> {

    private UUID recruit;
    private int profession;
    private String name;
    public MessagePromoteRecruit(){
    }

    public MessagePromoteRecruit(UUID recruit, int profession, String name) {
        this.recruit = recruit;
        this.profession = profession;
        this.name = name;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context){
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitWithinDistance(sender, this.recruit, 16D * 16D);
            if (RecruitCommandAuthority.canDirectlyControl(sender, recruit)) {
                RecruitEvents.promoteRecruit(recruit, profession, name, sender);
            }
        });
    }
    public MessagePromoteRecruit fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.profession = buf.readInt();
        this.name = buf.readUtf();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(recruit);
        buf.writeInt(profession);
        buf.writeUtf(name);
    }

}
