package com.talhanation.bannermod.persistence.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateUnitInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;

import java.util.*;

public class RecruitsPlayerUnitManager {
    private Map<UUID, Integer> recruitCountMap = new HashMap<>();
    public void load(ServerLevel level) {
        RecruitPlayerUnitSaveData data = RecruitPlayerUnitSaveData.get(level);

        recruitCountMap = data.getRecruitCountMap();
    }

    public void save(ServerLevel level) {
        RecruitPlayerUnitSaveData data = RecruitPlayerUnitSaveData.get(level);

        for (Map.Entry<UUID, Integer> entry : recruitCountMap.entrySet()) {
            data.setRecruitCount(entry.getKey(), entry.getValue());
        }

        data.setDirty();
    }

    public int getRecruitCount(UUID playerUUID) {
        return recruitCountMap.getOrDefault(playerUUID, 0);
    }

    public void setRecruitCount(Player player, int count) {
        setRecruitCount(player.getUUID(), count, getLiveSaveData());
    }

    public void addRecruits(UUID playerUUID, int count) {
        addRecruits(playerUUID, count, getLiveSaveData());
    }

    public void removeRecruits(UUID playerUUID, int count) {
        removeRecruits(playerUUID, count, getLiveSaveData());
    }

    void setRecruitCount(UUID playerUUID, int count, RecruitPlayerUnitSaveData data) {
        if (getRecruitCount(playerUUID) == count) return;

        recruitCountMap.put(playerUUID, count);
        if (data != null) {
            data.setRecruitCount(playerUUID, count);
            data.setDirty();
        }
    }

    void addRecruits(UUID playerUUID, int count, RecruitPlayerUnitSaveData data) {
        setRecruitCount(playerUUID, getRecruitCount(playerUUID) + count, data);
    }

    void removeRecruits(UUID playerUUID, int count, RecruitPlayerUnitSaveData data) {
        setRecruitCount(playerUUID, Math.max(getRecruitCount(playerUUID) - count, 0), data);
    }

    private RecruitPlayerUnitSaveData getLiveSaveData() {
        if (RecruitEvents.server() == null) {
            return null;
        }
        return RecruitPlayerUnitSaveData.get(RecruitEvents.server().overworld());
    }

    public boolean canPlayerRecruit(String stringId, UUID playerUUID) {
        int currentRecruitCount = getRecruitCount(playerUUID);
        int maxRecruitCount = RecruitsServerConfig.MaxRecruitsForPlayer.get();
        return currentRecruitCount < maxRecruitCount;
    }
    public int getRemainingRecruitSlots(String stringId, UUID playerUUID) {
        int currentRecruitCount = getRecruitCount(playerUUID);
        int maxRecruitCount = RecruitsServerConfig.MaxRecruitsForPlayer.get();
        int remaining = maxRecruitCount - currentRecruitCount;
        return Math.max(remaining, 0);
    }

    public void broadCastUnitInfoToPlayer(Player player) {
        if (player == null) return;

        String factionID = null;
        if(player.getTeam() != null){
            factionID = player.getTeam().getName();
        }

        BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(()-> (ServerPlayer) player),
                new MessageToClientUpdateUnitInfo(
                        RecruitsServerConfig.NobleVillagerNeedsVillagers.get(),
                        getRemainingRecruitSlots(factionID, player.getUUID())
                ));
    }

}
