package com.talhanation.bannermod.settlement.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class ClaimInteractionTargetResolver {

    private ClaimInteractionTargetResolver() {
    }

    static boolean handTriggersPlacement(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem
                || player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BucketItem;
    }

    static BlockPos resolveItemInteractionTarget(Player player, InteractionHand hand) {
        if (!handTriggersPlacement(player, hand)) return null;
        HitResult hitResult = player.pick(5.0D, 0.0F, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) hitResult).getBlockPos();
    }
}
