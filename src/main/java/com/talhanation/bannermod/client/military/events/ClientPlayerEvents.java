package com.talhanation.bannermod.client.military.events;

import com.talhanation.bannermod.client.civilian.input.AssignHomeTargetSelector;
import com.talhanation.bannermod.client.military.gui.worldmap.ChunkTileManager;
import com.talhanation.bannermod.client.military.gui.worldmap.WorldMapScreen;
import com.talhanation.bannermod.config.RecruitsClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class ClientPlayerEvents {
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        AssignHomeTargetSelector.tick();

        if (!(Minecraft.getInstance().screen instanceof WorldMapScreen screen)) return;
        if (!RecruitsClientConfig.UpdateMapTiles.get()) return;

        updateMapTiles(!screen.isNavigatingMap());
    }

    private void updateMapTiles(boolean updateNeighbors) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        ChunkTileManager.getInstance().updateCurrentTile(updateNeighbors);
    }

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            ChunkTileManager.getInstance().initialize((Level) event.getLevel());
        }
    }

    @SubscribeEvent
    public void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ChunkTileManager.getInstance().close();
        }
    }
}
