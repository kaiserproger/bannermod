package com.talhanation.bannermod.client.military.scenario;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.gui.CommandScreen;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.client.military.gui.PerkTreeScreen;
import com.talhanation.bannermod.client.military.gui.RecruitInventoryScreen;
import com.talhanation.bannermod.client.military.gui.RecruitMoreScreen;
import com.talhanation.bannermod.client.military.gui.group.RecruitsGroupListScreen;
import com.talhanation.bannermod.client.military.gui.war.PoliticalEntityListScreen;
import com.talhanation.bannermod.client.military.gui.war.WarDeclareScreen;
import com.talhanation.bannermod.client.military.gui.war.WarListScreen;
import com.talhanation.bannermod.client.military.gui.worldmap.WorldMapScreen;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.inventory.military.CommandMenu;
import com.talhanation.bannermod.inventory.military.RecruitInventoryMenu;
import com.talhanation.bannermod.network.messages.military.MessageRequestFormationMapSnapshot;
import com.talhanation.bannermod.scenario.VisualScenarioIds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class VisualScenarioClient {
    private static final ResourceLocation SCENARIO_LAYER = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "visual_scenario_overlay");
    private static final int STEP_TICKS = 100;
    private static final int PANEL_WIDTH = 224;
    private static final int PANEL_HEIGHT = 32;
    private static final int SAFE_TOP = 40;

    private static final List<SkillTreeVisualScenarioStep> SKILLTREE_STEPS = List.of(
            SkillTreeVisualScenarioStep.LOCKED,
            SkillTreeVisualScenarioStep.AVAILABLE,
            SkillTreeVisualScenarioStep.OWNED,
            SkillTreeVisualScenarioStep.PENDING,
            SkillTreeVisualScenarioStep.DENIED,
            SkillTreeVisualScenarioStep.RESPEC_CONFIRM
    );
    private static final List<String> SCREEN_OBSERVE_STEPS = List.of(
            "gui.bannermod.visual_scenario.screen.step.bounds",
            "gui.bannermod.visual_scenario.screen.step.actions",
            "gui.bannermod.visual_scenario.screen.step.feedback",
            "gui.bannermod.visual_scenario.screen.step.overlay_stack"
    );
    private static final List<String> COMMAND_STEPS = List.of(
            "gui.bannermod.visual_scenario.military_command.step.groups",
            "gui.bannermod.visual_scenario.military_command.step.categories",
            "gui.bannermod.visual_scenario.military_command.step.targets",
            "gui.bannermod.visual_scenario.military_command.step.read_only"
    );
    private static final List<String> RECRUIT_INVENTORY_STEPS = List.of(
            "gui.bannermod.visual_scenario.recruit_inventory.step.status",
            "gui.bannermod.visual_scenario.recruit_inventory.step.actions",
            "gui.bannermod.visual_scenario.recruit_inventory.step.perk_entry",
            "gui.bannermod.visual_scenario.recruit_inventory.step.read_only"
    );
    private static final List<String> RECRUIT_GROUP_STEPS = List.of(
            "gui.bannermod.visual_scenario.recruit_groups.step.list",
            "gui.bannermod.visual_scenario.recruit_groups.step.disabled",
            "gui.bannermod.visual_scenario.recruit_groups.step.selection",
            "gui.bannermod.visual_scenario.recruit_groups.step.read_only"
    );
    private static final List<String> RECRUIT_ACTION_STEPS = List.of(
            "gui.bannermod.visual_scenario.recruit_action_feedback.step.panel",
            "gui.bannermod.visual_scenario.recruit_action_feedback.step.decisions",
            "gui.bannermod.visual_scenario.recruit_action_feedback.step.denials",
            "gui.bannermod.visual_scenario.recruit_action_feedback.step.read_only"
    );
    private static final Map<String, ScenarioDefinition> DEFINITIONS = Map.of(
            VisualScenarioIds.SKILLTREE_PLAYER, ScenarioDefinition.skillTree(VisualScenarioIds.titleKey(VisualScenarioIds.SKILLTREE_PLAYER)),
            VisualScenarioIds.SKILLTREE_RECRUIT, ScenarioDefinition.skillTree(VisualScenarioIds.titleKey(VisualScenarioIds.SKILLTREE_RECRUIT)),
            VisualScenarioIds.MILITARY_COMMAND, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.MILITARY_COMMAND), COMMAND_STEPS),
            VisualScenarioIds.RECRUIT_INVENTORY, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.RECRUIT_INVENTORY), RECRUIT_INVENTORY_STEPS),
            VisualScenarioIds.RECRUIT_GROUPS, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.RECRUIT_GROUPS), RECRUIT_GROUP_STEPS),
            VisualScenarioIds.RECRUIT_ACTION_FEEDBACK, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.RECRUIT_ACTION_FEEDBACK), RECRUIT_ACTION_STEPS),
            VisualScenarioIds.WAR_ROOM, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.WAR_ROOM)),
            VisualScenarioIds.POLITICAL_ENTITIES, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.POLITICAL_ENTITIES)),
            VisualScenarioIds.WAR_DECLARE, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.WAR_DECLARE)),
            VisualScenarioIds.WORLD_MAP, ScenarioDefinition.screen(VisualScenarioIds.titleKey(VisualScenarioIds.WORLD_MAP))
    );

    private static ActiveScenario active;

    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, SCENARIO_LAYER, VisualScenarioClient::renderOverlay);
    }

    public static void start(String scenario, int recruitEntityId) {
        if (VisualScenarioIds.STOP.equals(scenario)) {
            stop();
            return;
        }
        ScenarioDefinition definition = DEFINITIONS.get(scenario);
        if (definition == null) {
            return;
        }
        active = new ActiveScenario(scenario, recruitEntityId, definition);
        openCurrentStep(Minecraft.getInstance(), active);
    }

    public static void stop() {
        ActiveScenario scenario = active;
        active = null;
        Minecraft mc = Minecraft.getInstance();
        if (scenario != null && isExpectedScreen(mc.screen, scenario)) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (active == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        if (!isExpectedScreen(mc.screen, active)) {
            stop();
            return;
        }
        active.ticksInStep++;
        if (active.ticksInStep >= STEP_TICKS) {
            active.ticksInStep = 0;
            active.stepIndex = (active.stepIndex + 1) % active.definition.stepCount();
            openCurrentStep(mc, active);
        }
    }

    @SubscribeEvent
    public void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (blocksReadOnlyInput(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (blocksReadOnlyInput(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (blocksReadOnlyInput(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (blocksReadOnlyInput(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (blocksReadOnlyInput(event.getScreen()) && event.getKeyCode() != GLFW.GLFW_KEY_ESCAPE) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (blocksReadOnlyInput(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    private static boolean blocksReadOnlyInput(Screen screen) {
        return active != null && !active.definition.isSkillTree() && isExpectedScreen(screen, active);
    }

    private static boolean isExpectedScreen(Screen screen, ActiveScenario scenario) {
        if (scenario.definition.isSkillTree()) return screen instanceof PerkTreeScreen;
        if (VisualScenarioIds.MILITARY_COMMAND.equals(scenario.name)) return screen instanceof CommandScreen;
        if (VisualScenarioIds.RECRUIT_INVENTORY.equals(scenario.name)) return screen instanceof RecruitInventoryScreen;
        if (VisualScenarioIds.RECRUIT_GROUPS.equals(scenario.name)) return screen instanceof RecruitsGroupListScreen;
        if (VisualScenarioIds.RECRUIT_ACTION_FEEDBACK.equals(scenario.name)) return screen instanceof RecruitMoreScreen;
        if (VisualScenarioIds.WAR_ROOM.equals(scenario.name)) return screen instanceof WarListScreen;
        if (VisualScenarioIds.POLITICAL_ENTITIES.equals(scenario.name)) return screen instanceof PoliticalEntityListScreen;
        if (VisualScenarioIds.WAR_DECLARE.equals(scenario.name)) return screen instanceof WarDeclareScreen;
        if (VisualScenarioIds.WORLD_MAP.equals(scenario.name)) return screen instanceof WorldMapScreen;
        return false;
    }

    private static void openCurrentStep(Minecraft mc, ActiveScenario scenario) {
        if (scenario.definition.isSkillTree()) {
            openSkillTreeStep(mc, scenario);
            return;
        }
        if (mc.player == null) return;
        if (VisualScenarioIds.MILITARY_COMMAND.equals(scenario.name)) {
            mc.setScreen(new CommandScreen(new CommandMenu(0, mc.player), mc.player.getInventory(), Component.literal("command_screen")));
            return;
        }
        if (VisualScenarioIds.RECRUIT_INVENTORY.equals(scenario.name)) {
            AbstractRecruitEntity recruit = recruitEntity(mc, scenario.recruitEntityId);
            if (recruit == null) {
                stop();
                return;
            }
            mc.setScreen(new RecruitInventoryScreen(new RecruitInventoryMenu(0, recruit, mc.player.getInventory()), mc.player.getInventory(), recruit.getName()));
            return;
        }
        if (VisualScenarioIds.RECRUIT_GROUPS.equals(scenario.name)) {
            mc.setScreen(new RecruitsGroupListScreen(mc.player));
            return;
        }
        if (VisualScenarioIds.RECRUIT_ACTION_FEEDBACK.equals(scenario.name)) {
            AbstractRecruitEntity recruit = recruitEntity(mc, scenario.recruitEntityId);
            if (recruit == null) {
                stop();
                return;
            }
            mc.setScreen(new RecruitMoreScreen(null, recruit, mc.player));
            return;
        }
        if (VisualScenarioIds.WAR_ROOM.equals(scenario.name)) {
            mc.setScreen(new WarListScreen(null));
            return;
        }
        if (VisualScenarioIds.POLITICAL_ENTITIES.equals(scenario.name)) {
            mc.setScreen(new PoliticalEntityListScreen(null));
            return;
        }
        if (VisualScenarioIds.WAR_DECLARE.equals(scenario.name)) {
            mc.setScreen(new WarDeclareScreen(new WarListScreen(null)));
            return;
        }
        if (VisualScenarioIds.WORLD_MAP.equals(scenario.name)) {
            if (mc.level != null && mc.level.dimension() == Level.OVERWORLD) {
                BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRequestFormationMapSnapshot());
            }
            mc.setScreen(new WorldMapScreen());
        }
    }

    private static void openSkillTreeStep(Minecraft mc, ActiveScenario scenario) {
        PerkTreeScreen screen;
        if (VisualScenarioIds.SKILLTREE_PLAYER.equals(scenario.name)) {
            screen = PerkTreeScreen.visualScenarioPlayerTree();
        } else {
            screen = PerkTreeScreen.visualScenarioRecruitTree(recruitEntity(mc, scenario.recruitEntityId));
        }
        mc.setScreen(screen);
        screen.applyVisualScenarioStep(scenario.definition.skillStep(scenario.stepIndex));
    }

    private static AbstractRecruitEntity recruitEntity(Minecraft mc, int entityId) {
        if (mc.level == null || entityId < 0) return null;
        Entity entity = mc.level.getEntity(entityId);
        return entity instanceof AbstractRecruitEntity recruit ? recruit : null;
    }

    private static void renderOverlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (active == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;

        Font font = mc.font;
        int x = 8;
        int y = SAFE_TOP;
        int width = Math.min(PANEL_WIDTH, graphics.guiWidth() - 16);
        MilitaryGuiStyle.parchmentPanel(graphics, x, y, width, PANEL_HEIGHT);
        int textWidth = width - 16;
        graphics.drawString(font, MilitaryGuiStyle.clampLabel(font, Component.translatable(active.definition.titleKey), textWidth), x + 8, y + 7,
                MilitaryGuiStyle.TEXT_DARK, false);
        graphics.drawString(font, MilitaryGuiStyle.clampLabel(font, Component.translatable(active.definition.stepTitleKey(active.stepIndex)), textWidth), x + 8, y + 19,
                MilitaryGuiStyle.TEXT_WARN, false);
    }

    private record ScenarioDefinition(String titleKey, List<String> stepTitleKeys, List<SkillTreeVisualScenarioStep> skillSteps) {
        static ScenarioDefinition skillTree(String titleKey) {
            return new ScenarioDefinition(titleKey, SKILLTREE_STEPS.stream().map(SkillTreeVisualScenarioStep::titleKey).toList(), SKILLTREE_STEPS);
        }

        static ScenarioDefinition screen(String titleKey) {
            return new ScenarioDefinition(titleKey, SCREEN_OBSERVE_STEPS, List.of());
        }

        static ScenarioDefinition screen(String titleKey, List<String> stepTitleKeys) {
            return new ScenarioDefinition(titleKey, stepTitleKeys, List.of());
        }

        boolean isSkillTree() {
            return !skillSteps.isEmpty();
        }

        int stepCount() {
            return stepTitleKeys.size();
        }

        String stepTitleKey(int index) {
            return stepTitleKeys.get(index);
        }

        SkillTreeVisualScenarioStep skillStep(int index) {
            return skillSteps.get(index);
        }
    }

    private static final class ActiveScenario {
        private final String name;
        private final int recruitEntityId;
        private final ScenarioDefinition definition;
        private int stepIndex;
        private int ticksInStep;

        private ActiveScenario(String name, int recruitEntityId, ScenarioDefinition definition) {
            this.name = name;
            this.recruitEntityId = recruitEntityId;
            this.definition = definition;
        }
    }
}
