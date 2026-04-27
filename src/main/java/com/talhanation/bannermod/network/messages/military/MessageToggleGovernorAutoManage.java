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

public class MessageToggleGovernorAutoManage implements Message<MessageToggleGovernorAutoManage> {
    private UUID recruit;
    private boolean autoManage;

    public MessageToggleGovernorAutoManage() {
    }

    public MessageToggleGovernorAutoManage(UUID recruit, boolean autoManage) {
        this.recruit = recruit;
        this.autoManage = autoManage;
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
            RecruitEvents.updateGovernorAutoManage(player, recruitEntity, this.autoManage);
        }
    }

    @Override
    public MessageToggleGovernorAutoManage fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.autoManage = buf.readBoolean();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeBoolean(this.autoManage);
    }
}
