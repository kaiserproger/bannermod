package com.talhanation.bannermod.registry.civilian;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class ModShortcuts {

    public static KeyMapping COMMAND_SCREEN_KEY;
    public static KeyMapping TOGGLE_PREFAB_RENDER_KEY;

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        COMMAND_SCREEN_KEY = new KeyMapping("key.workers.open_command_screen", GLFW.GLFW_KEY_X, "category.bannermod");
        TOGGLE_PREFAB_RENDER_KEY = new KeyMapping("key.workers.toggle_prefab_render", GLFW.GLFW_KEY_V, "Workers");
        event.register(COMMAND_SCREEN_KEY);
        event.register(TOGGLE_PREFAB_RENDER_KEY);
    }
}
