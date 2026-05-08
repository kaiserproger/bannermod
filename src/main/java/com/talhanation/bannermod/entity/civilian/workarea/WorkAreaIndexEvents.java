package com.talhanation.bannermod.entity.civilian.workarea;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Forge-bus hooks that keep {@link WorkAreaIndex} populated. Registered statically via
 * {@link EventBusSubscriber} so no explicit registration call is needed.
 */
@EventBusSubscriber(modid = BannerModMain.MOD_ID)
public final class WorkAreaIndexEvents {
    private WorkAreaIndexEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        WorkAreaIndex.instance().onEntityJoin(event.getEntity());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        WorkAreaIndex.instance().onEntityLeave(entity);
        if (!(entity instanceof AbstractWorkAreaEntity area)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Entity.RemovalReason reason = area.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) return;
        for (Entity loadedEntity : serverLevel.getAllEntities()) {
            if (loadedEntity instanceof AbstractWorkerEntity worker
                    && area.getUUID().equals(worker.getBoundWorkAreaUUID())) {
                worker.setCurrentWorkArea(null);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            WorkAreaIndex.instance().clear(serverLevel.dimension());
        }
    }
}
