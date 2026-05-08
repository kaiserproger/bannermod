package com.talhanation.bannermod.events;

import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.*;
import com.talhanation.bannermod.ai.military.villager.VillagerBecomeNobleGoal;
import com.talhanation.bannermod.entity.military.runtime.VillageGuardSpawnService;
import com.talhanation.bannermod.entity.military.runtime.VillagerConversionService;
import com.talhanation.bannermod.entity.military.runtime.VillagerProfessionTradeRegistrationService;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import com.talhanation.bannermod.registry.military.ModProfessions;
import com.talhanation.bannermod.persistence.military.RecruitsHireTradesRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.bus.api.SubscribeEvent;


import java.util.HashMap;
import java.util.Random;
import java.util.List;
import java.util.Map;

public class VillagerEvents {
    protected final Random random = new Random();

    private static final Map<VillagerProfession, EntityType<? extends AbstractRecruitEntity>> ENTITIES_BY_PROFESSION = new HashMap<>() {{
        put(ModProfessions.RECRUIT.get(), ModEntityTypes.RECRUIT.get());
        put(ModProfessions.BOWMAN.get(), ModEntityTypes.BOWMAN.get());
        put(ModProfessions.SHIELDMAN.get(), ModEntityTypes.RECRUIT_SHIELDMAN.get());
        put(ModProfessions.HORSEMAN.get(), ModEntityTypes.HORSEMAN.get());
        put(ModProfessions.NOMAD.get(), ModEntityTypes.NOMAD.get());
        put(ModProfessions.CROSSBOWMAN.get(), ModEntityTypes.CROSSBOWMAN.get());
    }};

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RecruitsHireTradesRegistry.registerTrades();
    }
    @SubscribeEvent
    public void onPlayerJoiningServer(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide() && event.getEntity() instanceof Player player){
            if(Minecraft.getInstance().player.getUUID().equals(player.getUUID())){
                RecruitsHireTradesRegistry.registerTrades();
            }
        }
    }
    @SubscribeEvent
    public void onVillagerJoinWorld(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if(!RecruitsServerConfig.NobleVillagerSpawn.get()) return;

        if (entity instanceof Villager villager) {
            villager.goalSelector.addGoal(0, new VillagerBecomeNobleGoal(villager));
        }
    }

    @SubscribeEvent
    public void onVillagerLivingUpdate(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof Villager villager) {
            VillagerProfession profession = villager.getVillagerData().getProfession();

            EntityType<? extends AbstractRecruitEntity> recruitType = ENTITIES_BY_PROFESSION.get(profession);
            if (recruitType != null) {
                VillagerConversionService.createRecruit(villager, recruitType);
            }
        }

        if (entity instanceof IronGolem ironGolemEntity) {

            if (!ironGolemEntity.isPlayerCreated() && RecruitsServerConfig.OverrideIronGolemSpawn.get()){
                List<AbstractRecruitEntity> list1 = entity.getCommandSenderWorld().getEntitiesOfClass(
                        AbstractRecruitEntity.class,
                        ironGolemEntity.getBoundingBox().inflate(32)
                );
                list1.removeIf(recruit -> recruit instanceof VillagerNobleEntity);

                if (list1.size() > 1) {
                    ironGolemEntity.remove(Entity.RemovalReason.KILLED);
                }
                else {
                    VillageGuardSpawnService.handleIronGolemOverride(ironGolemEntity, this.random);
                }
            }
        }
    }

    @SubscribeEvent
    public void villagerTrades(VillagerTradesEvent event) {
        if(!shouldRegisterProfessionBlockTrades()) return;
        VillagerProfessionTradeRegistrationService.registerProfessionBlockTrades(event);
    }

    private static boolean shouldRegisterProfessionBlockTrades() {
        try {
            return RecruitsServerConfig.ShouldProfessionBlocksTrade.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
