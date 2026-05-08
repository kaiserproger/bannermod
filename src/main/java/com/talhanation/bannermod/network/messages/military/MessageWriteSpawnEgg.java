package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.citizen.CitizenProfession;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.registry.citizen.ModCitizenItems;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;

import java.util.UUID;

public class MessageWriteSpawnEgg implements BannerModMessage<MessageWriteSpawnEgg> {

    public UUID recruit;

    public MessageWriteSpawnEgg() {
    }

    public MessageWriteSpawnEgg(UUID recruit) {
        this.recruit = recruit;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Entity entity = player.serverLevel().getEntity(this.recruit);
            if (entity instanceof CitizenEntity citizenEntity && citizenEntity.distanceToSqr(player) <= 64.0D * 64.0D) {
                writeCitizenSpawnEggToHand(player, citizenEntity, InteractionHand.MAIN_HAND);
            }
        });
    }

    public static boolean writeCitizenSpawnEggToHand(ServerPlayer player, CitizenEntity citizenEntity, InteractionHand hand) {
        if (player == null || citizenEntity == null || hand == null) {
            return false;
        }
        if (!player.getItemInHand(hand).isEmpty()) {
            return false;
        }
        MessageWriteSpawnEgg writer = new MessageWriteSpawnEgg();
        ItemStack itemStack = writer.getItemStackForCitizenProfession(citizenEntity.activeProfession());
        if (itemStack.isEmpty()) {
            return false;
        }
        CompoundTag entityTag = new CompoundTag();
        entityTag.putString("CitizenProfession", citizenEntity.activeProfession().name());
        entityTag.putString("Name", citizenEntity.getName().getString());
        if (citizenEntity.getOwnerUUID() != null) {
            entityTag.putUUID("OwnerUUID", citizenEntity.getOwnerUUID());
        }
        if (citizenEntity.getTeam() != null) {
            entityTag.putString("Team", citizenEntity.getTeam().getName());
        }
        itemStack.set(DataComponents.ENTITY_DATA, CustomData.of(entityTag));
        player.setItemInHand(hand, itemStack);
        return true;
    }

    private ItemStack getItemStackForCitizenProfession(CitizenProfession profession) {
        if (profession == CitizenProfession.NONE) return ItemStack.EMPTY;
        return new ItemStack(ModCitizenItems.CITIZEN_SPAWN_EGG.get());
    }

    public MessageWriteSpawnEgg fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
    }
}
