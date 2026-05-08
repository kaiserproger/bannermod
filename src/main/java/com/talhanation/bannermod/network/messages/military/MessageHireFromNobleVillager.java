package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.VillagerNobleEntity;
import com.talhanation.bannermod.entity.military.runtime.VillagerConversionService;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.persistence.military.RecruitsHireTrade;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;

import java.util.Optional;
import java.util.UUID;

public class MessageHireFromNobleVillager implements BannerModMessage<MessageHireFromNobleVillager> {
    private UUID nobleUUID;
    private UUID villagerUUID;
    private int cost;
    private boolean needsVillager;
    private boolean closing;
    private ResourceLocation resource;
    private UUID groupUUID;
    public MessageHireFromNobleVillager() {
    }

    public MessageHireFromNobleVillager(UUID nobleUUID, UUID villagerUUID, RecruitsHireTrade trade, RecruitsGroup group, boolean needsVillager, boolean closing) {
        this.nobleUUID = nobleUUID;
        this.villagerUUID = villagerUUID;
        this.groupUUID = group.getUUID();
        if(trade != null){
            this.cost = trade.cost;
            this.resource = trade.resourceLocation;
        }
        else{
            this.cost = 0;
            this.resource = ResourceLocation.fromNamespaceAndPath("", "");
        }

        this.needsVillager = needsVillager;
        this.closing = closing;
    }

    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ServerLevel serverLevel = player.serverLevel();
            Entity nobleEntity = serverLevel.getEntity(this.nobleUUID);
            if (!(nobleEntity instanceof VillagerNobleEntity villagerNoble)
                    || !villagerNoble.isAlive()
                    || villagerNoble.distanceToSqr(player) > 32.0D * 32.0D) {
                return;
            }

            if(closing){
                villagerNoble.isTrading(false);
                return;
            }

            RecruitsGroup group = RecruitEvents.groupsManager().getGroup(groupUUID);

            if(this.needsVillager){
                Entity villagerEntity = serverLevel.getEntity(this.villagerUUID);
                if (villagerEntity instanceof Villager villager
                        && villager.isAlive()
                        && villager.distanceToSqr(player) <= 32.0D * 32.0D) {
                    this.createRecruit(serverLevel, villager, villagerNoble, player, group);
                }
            }
            else{
                String string = resource.toString();
                Optional<EntityType<?>> optionalType = EntityType.byString(string);
                optionalType.ifPresent(type -> VillagerConversionService.spawnHiredRecruit(serverLevel, (EntityType<? extends AbstractRecruitEntity>) type, player, group));

                villagerNoble.doTrade(resource);
            }

            String stringID = player.getTeam() != null ? player.getTeam().getName() : "";
            boolean canHire = RecruitEvents.playerUnitManager().canPlayerRecruit(stringID, player.getUUID());
            BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(()-> player), new MessageToClientUpdateHireState(canHire));
        });
    }
    public void createRecruit(ServerLevel serverLevel, Villager villager, VillagerNobleEntity villagerNoble, Player player, RecruitsGroup group){
        String string = resource.toString();
        Optional<EntityType<?>> optionalType = EntityType.byString(string);

        optionalType.ifPresent(type -> {
            VillagerConversionService.createHiredRecruitFromVillager(serverLevel, villager, (EntityType<? extends AbstractRecruitEntity>) type, player, group);
        });

        villagerNoble.doTrade(resource);
    }

    public MessageHireFromNobleVillager fromBytes(FriendlyByteBuf buf) {
        this.nobleUUID = buf.readUUID();
        this.villagerUUID = buf.readUUID();
        this.cost = buf.readInt();
        this.resource = buf.readResourceLocation();
        this.needsVillager = buf.readBoolean();
        this.closing = buf.readBoolean();
        this.groupUUID = buf.readUUID();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.nobleUUID);
        buf.writeUUID(this.villagerUUID);
        buf.writeInt(this.cost);
        buf.writeResourceLocation(resource);
        buf.writeBoolean(needsVillager);
        buf.writeBoolean(closing);
        buf.writeUUID(this.groupUUID);
    }
}
