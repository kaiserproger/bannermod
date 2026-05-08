package com.talhanation.bannermod.bootstrap;

import com.talhanation.bannermod.network.BannerModNetworkBootstrap;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.events.DamageEvent;
import com.talhanation.bannermod.events.PillagerEvents;
import com.talhanation.bannermod.events.RecruitCombatEvents;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.events.RecruitLifecycleEvents;
import com.talhanation.bannermod.events.VillagerEvents;
import com.talhanation.bannermod.events.WorkersVillagerEvents;
import com.talhanation.bannermod.events.civilian.SettlementMutationRefreshEvents;
import com.talhanation.bannermod.events.civilian.SettlementWorkOrderClaimReleaseEvents;
import com.talhanation.bannermod.client.civilian.events.ScreenEvents;
import com.talhanation.bannermod.client.military.events.ClientPlayerEvents;
import com.talhanation.bannermod.client.military.events.KeyEvents;
import com.talhanation.bannermod.client.military.gui.overlay.HudOverlayCoordinator;
import com.talhanation.bannermod.commands.military.PatrolSpawnCommand;
import com.talhanation.bannermod.commands.military.RecruitsAdminCommands;
import com.talhanation.bannermod.commands.war.BannerModWarCommands;
import com.talhanation.bannermod.compat.MedievalSiegeMachinesCompat;
import com.talhanation.bannermod.config.BannerModServerConfig;
import com.talhanation.bannermod.config.RecruitsClientConfig;
import com.talhanation.bannermod.war.config.WarServerConfig;
import com.talhanation.bannermod.war.events.WarPvpEvents;
import com.talhanation.bannermod.war.events.WarRevoltAutoResolver;
import com.talhanation.bannermod.war.events.WarStateBroadcaster;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.talhanation.bannermod.network.compat.BannerModChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(BannerModMain.MOD_ID)
public class BannerModMain {
    public static final String MOD_ID = "bannermod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static BannerModChannel SIMPLE_CHANNEL;

    // Compat booleans
    public static boolean isMusketModLoaded;
    public static boolean isSmallShipsLoaded;
    public static boolean isSmallShipsCompatible;
    public static boolean isSiegeWeaponsLoaded;
    public static boolean isEpicKnightsLoaded;
    public static boolean isCorpseLoaded;
    public static boolean isRPGZLoaded;

    public BannerModMain(IEventBus modEventBus, Dist dist, ModContainer modContainer) {
        // Single unified server spec (CONFIGMERGE-001): both legacy
        // bannermod-recruits-server.toml and bannermod-workers-server.toml keys live under
        // BannerModServerConfig.SERVER as recruits.* and workers.* sub-paths. Existing
        // installations have their old files migrated automatically on first server start
        // by ServerLifecycleHooksMixin -> BannerModConfigMigration.
        modContainer.registerConfig(ModConfig.Type.CLIENT, RecruitsClientConfig.CLIENT, "bannermod-recruits-client.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, BannerModServerConfig.SERVER, "bannermod-server.toml");
        // Register war/RP config — separate spec, intentionally kept out of the merge per
        // CONFIGMERGE-001 scope (Recruits + Workers only).
        modContainer.registerConfig(ModConfig.Type.SERVER, WarServerConfig.SERVER, "bannermod-war-server.toml");

        // Lifecycle
        modEventBus.addListener(this::setup);
        modEventBus.addListener(BannerModNetworkBootstrap::registerPayloads);

        // Register military deferred registers (from bannermod.registry.military)
        com.talhanation.bannermod.registry.military.ModBlocks.BLOCKS.register(modEventBus);
        com.talhanation.bannermod.registry.military.ModPois.POIS.register(modEventBus);
        com.talhanation.bannermod.registry.military.ModProfessions.PROFESSIONS.register(modEventBus);
        com.talhanation.bannermod.registry.military.ModScreens.MENU_TYPES.register(modEventBus);
        com.talhanation.bannermod.registry.military.ModItems.ITEMS.register(modEventBus);
        com.talhanation.bannermod.registry.military.ModEntityTypes.ENTITY_TYPES.register(modEventBus);

        // Register civilian deferred registers (from bannermod.registry.civilian)
        com.talhanation.bannermod.registry.civilian.ModBlocks.BLOCKS.register(modEventBus);
        com.talhanation.bannermod.registry.civilian.ModPois.POIS.register(modEventBus);
        com.talhanation.bannermod.registry.civilian.ModProfessions.PROFESSIONS.register(modEventBus);
        com.talhanation.bannermod.registry.civilian.ModMenuTypes.MENU_TYPES.register(modEventBus);
        com.talhanation.bannermod.registry.civilian.ModItems.ITEMS.register(modEventBus);
        com.talhanation.bannermod.registry.civilian.ModEntityTypes.ENTITY_TYPES.register(modEventBus);

        // Register citizen unified entity type (Cit-02 onward)
        com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes.ENTITY_TYPES.register(modEventBus);
        com.talhanation.bannermod.registry.citizen.ModCitizenItems.ITEMS.register(modEventBus);

        // Register war deferred registers (block + item + block-entity for the SiegeStandard).
        // ModWarBlockEntities depends on ModWarBlocks via SIEGE_STANDARD.get(); registration order
        // matches the deferred-init contract: BLOCKS resolves first at common-setup, then BLOCK_ENTITY_TYPES.
        com.talhanation.bannermod.registry.war.ModWarBlocks.BLOCKS.register(modEventBus);
        com.talhanation.bannermod.registry.war.ModWarItems.ITEMS.register(modEventBus);
        com.talhanation.bannermod.registry.war.ModWarBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // Creative tabs
        modEventBus.addListener(this::addCreativeTabs);

        // Client-side setup
        if (dist == Dist.CLIENT) {
            modEventBus.addListener(BannerModMain.this::clientSetup);
            modEventBus.addListener(HudOverlayCoordinator::registerOverlays);
            modEventBus.addListener(com.talhanation.bannermod.registry.military.ModShortcuts::registerBindings);
            modEventBus.addListener(com.talhanation.bannermod.registry.civilian.ModShortcuts::registerBindings);
        }

        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PatrolSpawnCommand.register(event.getDispatcher());
        RecruitsAdminCommands.register(event.getDispatcher());
        BannerModWarCommands.register(event.getDispatcher());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setup(final FMLCommonSetupEvent event) {
        // Workers runtime events
        NeoForge.EVENT_BUS.register(new WorkersVillagerEvents());
        // Recruits runtime events — ports the legacy recruits/Main.java registrations into the
        // unified entrypoint. RecruitEvents.onServerStarting is what initializes the static
        // recruitsPlayerUnitManager / recruitsGroupsManager fields read by AbstractRecruitEntity.
        // Without these, right-click-to-hire (and every other recruits-side flow) trips an NPE.
        // See 21-UAT.md gap "Right-clicking a recruit opens the Hire GUI without server-side crash".
        NeoForge.EVENT_BUS.register(new RecruitLifecycleEvents());
        NeoForge.EVENT_BUS.register(new RecruitCombatEvents());
        NeoForge.EVENT_BUS.register(new com.talhanation.bannermod.events.RecruitShieldEvents());
        NeoForge.EVENT_BUS.register(new ClaimEvents());
        NeoForge.EVENT_BUS.register(new CommandEvents());
        NeoForge.EVENT_BUS.register(new DamageEvent());
        NeoForge.EVENT_BUS.register(new PillagerEvents());
        NeoForge.EVENT_BUS.register(new VillagerEvents());
        NeoForge.EVENT_BUS.register(new WarPvpEvents());
        NeoForge.EVENT_BUS.register(new WarRevoltAutoResolver());
        NeoForge.EVENT_BUS.register(new WarStateBroadcaster());
        NeoForge.EVENT_BUS.register(new com.talhanation.bannermod.war.events.WarOccupationTaxTicker());
        NeoForge.EVENT_BUS.register(new com.talhanation.bannermod.war.events.WarRetentionSweeper());
        NeoForge.EVENT_BUS.register(new SettlementMutationRefreshEvents());
        NeoForge.EVENT_BUS.register(new SettlementWorkOrderClaimReleaseEvents());
        // Wire the per-player packet rate limiter to the live server config so cooldown
        // changes apply without a restart. Limiter remains usable in tests if config is absent.
        com.talhanation.bannermod.network.throttle.PacketRateLimitConfig.install();

        // Create shared channel; recruits at [0..N), workers at [N..N+M)
        SIMPLE_CHANNEL = BannerModNetworkBootstrap.createSharedChannel();

        // Sync compat flags
        isMusketModLoaded = ModList.get().isLoaded("musketmod");
        isSmallShipsLoaded = ModList.get().isLoaded("smallships");
        isSmallShipsCompatible = isSmallShipsLoaded;
        isSiegeWeaponsLoaded = MedievalSiegeMachinesCompat.isLoaded();
        MedievalSiegeMachinesCompat.logDetectedState();
        isEpicKnightsLoaded = ModList.get().isLoaded("epicknights");
        isCorpseLoaded = ModList.get().isLoaded("corpse");
        isRPGZLoaded = ModList.get().isLoaded("rpgz");
    }

    @OnlyIn(Dist.CLIENT)
    public void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(com.talhanation.bannermod.persistence.civilian.StructureManager::copyDefaultStructuresIfMissing);
        // Recruits command-screen categories — pre-consolidation BannerlordMain.clientSetup
        // registered these three with priorities so they sort before WorkerCommandScreen
        // (priority 0). Without them the Command screen shows only the workers tab.
        com.talhanation.bannermod.client.military.events.CommandCategoryManager.register(
                new com.talhanation.bannermod.client.military.gui.commandscreen.CombatCategory(), -3);
        com.talhanation.bannermod.client.military.events.CommandCategoryManager.register(
                new com.talhanation.bannermod.client.military.gui.commandscreen.MovementCategory(), -2);
        com.talhanation.bannermod.client.military.events.CommandCategoryManager.register(
                new com.talhanation.bannermod.client.military.gui.commandscreen.OtherCategory(), -1);
        // Worker command screen
        com.talhanation.bannermod.client.military.events.CommandCategoryManager.register(
                new com.talhanation.bannermod.client.civilian.gui.WorkerCommandScreen());
        NeoForge.EVENT_BUS.register(new ScreenEvents());
        // Recruits client-side event handlers — same Phase-21 consolidation defect class
        // as 21-11 (recruits/Main.java was deprecated to a no-op shim and these registrations
        // were not ported into the unified entrypoint). KeyEvents owns the R/U/M hotkey
        // listener that opens Command/Faction/Map screens; ClientPlayerEvents owns
        // client-tick and world-load hooks; HudOverlayCoordinator renders the claim/war HUD stack.
        // See 21-UAT.md gap "Recruits hotkey screens (Command/Faction/Map) and the claim overlay open in dev client".
        NeoForge.EVENT_BUS.register(new KeyEvents());
        NeoForge.EVENT_BUS.register(new ClientPlayerEvents());
        NeoForge.EVENT_BUS.register(HudOverlayCoordinator.INSTANCE);
    }

    private void addCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
            event.accept(com.talhanation.bannermod.registry.citizen.ModCitizenItems.CITIZEN_SPAWN_EGG.get());
        } else if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(com.talhanation.bannermod.registry.civilian.ModItems.BANNERMOD_ALMANAC.get());
        }
    }
}
