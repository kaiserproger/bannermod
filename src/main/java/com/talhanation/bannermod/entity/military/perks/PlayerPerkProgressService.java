package com.talhanation.bannermod.entity.military.perks;

import com.talhanation.bannermod.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public final class PlayerPerkProgressService {
    private static final int PERK_POINTS_PER_LEVEL = 1;

    private PlayerPerkProgressService() {
    }

    public static PerkProgress progress(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_PERKS);
    }

    public static int availablePoints(ServerPlayer player) {
        return progress(player).getAvailablePoints();
    }

    public static Set<String> unlockedPerkIds(ServerPlayer player) {
        return progress(player).getOwnedPerks();
    }

    public static void grantLevelPoints(ServerPlayer player, int gainedLevels) {
        if (gainedLevels <= 0) return;
        progress(player).grantPoints(gainedLevels * PERK_POINTS_PER_LEVEL);
    }

    public static PerkProgress.UnlockResult unlock(ServerPlayer player, String perkId) {
        PerkNode node = PerkRegistry.get(perkId).orElse(null);
        return progress(player).unlock(node);
    }

    public static int respec(ServerPlayer player) {
        return progress(player).respec();
    }
}
