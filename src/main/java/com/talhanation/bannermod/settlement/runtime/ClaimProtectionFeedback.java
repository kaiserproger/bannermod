package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;

final class ClaimProtectionFeedback {
    private static final long COOLDOWN_TICKS = 40L;
    private static final String LAST_FEEDBACK_TICK_TAG = "BannerModClaimProtectionFeedbackAt";

    private ClaimProtectionFeedback() {
    }

    static void sendDenied(Player player, LevelAccessor level, BlockPos pos, RecruitsClaimManager claimManager) {
        if (player.level().isClientSide()) return;
        long gameTime = player.level().getGameTime();
        if (player.getPersistentData().contains(LAST_FEEDBACK_TICK_TAG)) {
            long lastFeedbackAt = player.getPersistentData().getLong(LAST_FEEDBACK_TICK_TAG);
            if (gameTime - lastFeedbackAt < COOLDOWN_TICKS) return;
        }

        player.getPersistentData().putLong(LAST_FEEDBACK_TICK_TAG, gameTime);
        player.sendSystemMessage(Component.translatable(deniedMessageKey(level, pos, player, claimManager)).withStyle(ChatFormatting.RED));
    }

    static String deniedMessageKey(LevelAccessor level, BlockPos pos, Player player, RecruitsClaimManager claimManager) {
        RecruitsClaim claim = ClaimAccessQueries.getClaim(claimManager, level, pos);
        if (claim == null) {
            return deniedMessageKey(Territory.UNCLAIMED);
        }
        return deniedMessageKey(ClaimAccessQueries.isFriendlyToClaim(player, claim) ? Territory.FRIENDLY : Territory.HOSTILE);
    }

    static String deniedMessageKey(Territory territory) {
        return switch (territory) {
            case FRIENDLY -> "chat.bannermod.claim_protection.denied.friendly";
            case HOSTILE -> "chat.bannermod.claim_protection.denied.hostile";
            case UNCLAIMED -> "chat.bannermod.claim_protection.denied.unclaimed";
        };
    }

    enum Territory {
        FRIENDLY,
        HOSTILE,
        UNCLAIMED
    }
}
