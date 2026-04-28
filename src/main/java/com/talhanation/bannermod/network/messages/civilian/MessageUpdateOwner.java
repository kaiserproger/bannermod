package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementRefreshSupport;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;


public class MessageUpdateOwner implements Message<MessageUpdateOwner> {

    public UUID uuid;
    public UUID playerUUID;
    public String playerName;
    public MessageUpdateOwner() {

    }

    public MessageUpdateOwner(UUID uuid, RecruitsPlayerInfo playerInfo) {
        this.uuid = uuid;
        this.playerUUID = playerInfo.getUUID();
        this.playerName = playerInfo.getName();
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context){
        ServerPlayer player = context.getSender();
        if(player == null) return;

        AbstractWorkAreaEntity workArea = WorkAreaMessageSupport.resolveAuthorizedWorkArea(player, this.uuid, AbstractWorkAreaEntity.class);
        if (workArea == null) {
            return;
        }

        if (!this.updateWorkArea(workArea)) {
            return;
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, workArea.blockPosition());
        }

    }

    public boolean updateWorkArea(AbstractWorkAreaEntity workArea){
        Player player = workArea.level().getPlayerByUUID(playerUUID);
        return WorkAreaOwnerUpdate.apply(this.playerUUID, resolvedOwner(player), new WorkAreaOwnerUpdate.MutableWorkArea() {
            @Override
            public void setPlayerUUID(UUID playerUUID) {
                workArea.setPlayerUUID(playerUUID);
            }

            @Override
            public void setPlayerName(String playerName) {
                workArea.setPlayerName(playerName);
            }

            @Override
            public void setTeamStringID(String teamStringID) {
                workArea.setTeamStringID(teamStringID);
            }
        });
    }

    private WorkAreaOwnerUpdate.ResolvedOwner resolvedOwner(Player player) {
        if (player == null) {
            return null;
        }

        return new WorkAreaOwnerUpdate.ResolvedOwner() {
            @Override
            public UUID uuid() {
                return player.getUUID();
            }

            @Override
            public String name() {
                return player.getName().getString();
            }

            @Override
            public String teamName() {
                return player.getTeam() == null ? null : player.getTeam().getName();
            }
        };
    }
    public MessageUpdateOwner fromBytes(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.playerUUID = buf.readUUID();
        this.playerName = buf.readUtf();
        return this;
    }
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUUID(playerUUID);
        buf.writeUtf(playerName);
    }
}
