package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.ai.military.CombatStance;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.List;
import java.util.UUID;

public class MessageCombatStanceGui implements BannerModMessage<MessageCombatStanceGui> {

    private UUID recruitUuid;
    private CombatStance stance;

    public MessageCombatStanceGui() {
    }

    public MessageCombatStanceGui(UUID recruitUuid, CombatStance stance) {
        this.recruitUuid = recruitUuid;
        this.stance = stance;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            if (this.stance == null) {
                return;
            }

            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null) return;
            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitInInflatedBox(serverPlayer, this.recruitUuid, 16.0D);
            if (recruit == null) {
                return;
            }

            CommandTargeting.SingleRecruitSelection selection = CommandTargeting.forSingleRecruit(
                    serverPlayer.getUUID(),
                    serverPlayer.getTeam() == null ? null : serverPlayer.getTeam().getName(),
                    serverPlayer.hasPermissions(2),
                    this.recruitUuid,
                    List.of(new CommandTargeting.RecruitSnapshot(
                            recruit.getUUID(),
                            recruit.getOwnerUUID(),
                            recruit.getGroup(),
                            recruit.getTeam() == null ? null : recruit.getTeam().getName(),
                            recruit.isOwned(),
                            recruit.isAlive(),
                            recruit.getListen(),
                            recruit.distanceToSqr(serverPlayer)
                    ))
            );

            if (!selection.isSuccess()) {
                return;
            }

            CommandEvents.onCombatStanceCommand(serverPlayer.getUUID(), recruit, this.stance, null);
        });
    }

    public MessageCombatStanceGui fromBytes(FriendlyByteBuf buf) {
        this.recruitUuid = buf.readUUID();
        this.stance = CombatStance.fromName(buf.readUtf(32));
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruitUuid);
        buf.writeUtf((this.stance == null ? CombatStance.LOOSE : this.stance).name());
    }
}
