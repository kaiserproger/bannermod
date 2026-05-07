package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.army.command.CommandIntent;
import com.talhanation.bannermod.army.command.CommandIntentDispatcher;
import com.talhanation.bannermod.army.command.CommandIntentPriority;
import com.talhanation.bannermod.army.command.RecruitCommandAuthority;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.*;
import java.util.function.Function;

public class MessageMountEntityGui implements BannerModMessage<MessageMountEntityGui> {
    private UUID recruit;
    private boolean back;

    public MessageMountEntityGui() {
    }

    public MessageMountEntityGui(UUID recruit, boolean back) {
        this.recruit = recruit;
        this.back = back;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @SuppressWarnings({"all"})
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = Objects.requireNonNull(context.getSender());

            AbstractRecruitEntity recruit = RecruitMessageEntityResolver.resolveRecruitWithinDistance(player, this.recruit, 32.0D * 32.0D);
            if (recruit != null) {
                this.mount(player, recruit);
            }
        });
    }

    @SuppressWarnings({"all"})
    private void mount(ServerPlayer player, AbstractRecruitEntity recruit) {
        if (!RecruitCommandAuthority.canDirectlyControl(player, recruit)) {
            return;
        }

        if (this.back && recruit.getMountUUID() != null) {
            CommandIntentDispatcher.dispatch(player, new CommandIntent.SiegeMachine(
                    player.level().getGameTime(), CommandIntentPriority.HIGH, false, null, null, true), List.of(recruit));
        } else if (recruit.getVehicle() == null) {
            List<Entity> list = recruit.getCommandSenderWorld().getEntitiesOfClass(
                    Entity.class,
                    recruit.getBoundingBox().inflate(8),
                    (mount) -> !(mount instanceof AbstractHorse horse &&
                            horse.hasControllingPassenger()) &&
                            RecruitsServerConfig.MountWhiteList.get().contains(mount.getEncodeId())
            );

            double d0 = -1.0D;
            Entity horse = null;

            for (Entity entity : list) {
                double d1 = entity.distanceToSqr(recruit);
                if (d0 == -1.0D || d1 < d0) {
                    horse = entity;
                    d0 = d1;
                }
            }

            if (horse == null) {
                recruit.getOwner().sendSystemMessage(TEXT_NO_MOUNT(recruit.getName().getString()));
                return;
            }

            CommandIntentDispatcher.dispatch(player, new CommandIntent.SiegeMachine(
                    player.level().getGameTime(), CommandIntentPriority.HIGH, false, horse.getUUID(), null, false), List.of(recruit));
        }
    }

    public MessageMountEntityGui fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.back = buf.readBoolean();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeBoolean(this.back);
    }

    private static MutableComponent TEXT_NO_MOUNT(String name) {
        return Component.translatable("chat.recruits.text.noMount", name);
    }
}
