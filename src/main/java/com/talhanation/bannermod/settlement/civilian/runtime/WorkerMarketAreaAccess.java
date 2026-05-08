package com.talhanation.bannermod.settlement.civilian.runtime;

import com.talhanation.bannermod.entity.civilian.workarea.MarketArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public final class WorkerMarketAreaAccess {

    private WorkerMarketAreaAccess() {
    }

    public static boolean shouldBlockInteraction(Player player, Level level, BlockPos pos) {
        AABB queryBox = new AABB(pos).inflate(8);
        double queryRadius = Math.sqrt(queryBox.getXsize() * queryBox.getXsize()
                + queryBox.getYsize() * queryBox.getYsize()
                + queryBox.getZsize() * queryBox.getZsize()) / 2.0D;
        List<MarketArea> markets = WorkAreaIndex.instance().queryInRange(level, queryBox.getCenter(), queryRadius, MarketArea.class);
        if (markets.isEmpty()) {
            return false;
        }

        markets.removeIf(marketArea -> !isInsideMarketArea(pos, marketArea));
        if (markets.isEmpty() || !(level.getBlockEntity(pos) instanceof Container)) {
            return false;
        }

        MarketArea market = markets.get(0);
        UUID ownerUUID = market.getPlayerUUID();
        boolean isOwner = ownerUUID != null && player.getUUID().equals(ownerUUID);
        boolean isAdmin = player.isCreative() && player.hasPermissions(2);
        return !isOwner && !isAdmin;
    }

    private static boolean isInsideMarketArea(BlockPos pos, MarketArea marketArea) {
        AABB area = marketArea.getArea();
        return pos.getX() >= area.minX && pos.getX() <= area.maxX
                && pos.getY() >= area.minY && pos.getY() <= area.maxY
                && pos.getZ() >= area.minZ && pos.getZ() <= area.maxZ;
    }
}
