package com.talhanation.bannermod.client.military.events;

import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.civilian.gui.WorkerCommandScreen;
import com.talhanation.bannermod.client.civilian.render.WorkerAreaRenderer;
import com.talhanation.bannermod.client.military.gui.war.WarListScreen;
import com.talhanation.bannermod.client.military.gui.worldmap.WorldMapScreen;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.registry.military.ModShortcuts;
import com.talhanation.bannermod.network.messages.military.MessageRequestFormationMapSnapshot;
import com.talhanation.bannermod.network.messages.military.MessageWriteSpawnEgg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)

public class KeyEvents {
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer clientPlayerEntity = minecraft.player;
        if (clientPlayerEntity == null)
            return;

        if (ModShortcuts.COMMAND_SCREEN_KEY.isDown()) {
            CommandEvents.openCommandScreen(clientPlayerEntity);
        }

        if (com.talhanation.bannermod.registry.civilian.ModShortcuts.COMMAND_SCREEN_KEY != null
                && com.talhanation.bannermod.registry.civilian.ModShortcuts.COMMAND_SCREEN_KEY.consumeClick()) {
            selectWorkerCommandCategory(clientPlayerEntity);
            CommandEvents.openCommandScreen(clientPlayerEntity);
        }

        if (ModShortcuts.MAP_SCREEN_KEY.isDown()) {
            if (minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD) {
                BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRequestFormationMapSnapshot());
                minecraft.setScreen(new WorldMapScreen());
            }
        }

        if (ModShortcuts.WAR_ROOM_KEY.consumeClick()) {
            minecraft.setScreen(new WarListScreen(null));
        }

        if (com.talhanation.bannermod.registry.civilian.ModShortcuts.TOGGLE_PREFAB_RENDER_KEY != null
                && com.talhanation.bannermod.registry.civilian.ModShortcuts.TOGGLE_PREFAB_RENDER_KEY.consumeClick()) {
            boolean enabled = WorkerAreaRenderer.toggleStructurePreviewRendering();
            clientPlayerEntity.displayClientMessage(Component.translatable(
                    enabled ? "key.workers.toggle_prefab_render.enabled" : "key.workers.toggle_prefab_render.disabled"), true);
        }
    }

    @SubscribeEvent
    public void onPlayerPick(InputEvent.InteractionKeyMappingTriggered event){
        if(event.isPickBlock()){
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer clientPlayerEntity = minecraft.player;
            if (clientPlayerEntity == null || !clientPlayerEntity.isCreative())
                return;
            

            Entity target = ClientEvent.getEntityByLooking();
            if(target instanceof AbstractRecruitEntity recruitEntity){
                BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageWriteSpawnEgg(recruitEntity.getUUID()));
                event.setCanceled(true);
            }
        }
    }

    private static void selectWorkerCommandCategory(LocalPlayer player) {
        int workerCategory = CommandCategoryManager.getIndex(WorkerCommandScreen.class);
        if (workerCategory < 0) return;

        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        nbt.putInt("RecruitsCategory", workerCategory);
        playerNBT.put(Player.PERSISTED_NBT_TAG, nbt);
    }

}
