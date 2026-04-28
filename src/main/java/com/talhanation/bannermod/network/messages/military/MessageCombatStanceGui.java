package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.ai.military.CombatStance;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.CommandEvents;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageCombatStanceGui implements Message<MessageCombatStanceGui> {

    private UUID recruitUuid;
    private CombatStance stance;

    public MessageCombatStanceGui() {
    }

    public MessageCombatStanceGui(UUID recruitUuid, CombatStance stance) {
        this.recruitUuid = recruitUuid;
        this.stance = stance;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context) {
        if (this.stance == null) {
            return;
        }

        ServerPlayer serverPlayer = Objects.requireNonNull(context.getSender());
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
