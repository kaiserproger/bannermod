package com.talhanation.bannermod.scenario;

import java.util.List;

public final class VisualScenarioIds {
    public static final String SKILLTREE_PLAYER = "skilltree_player";
    public static final String SKILLTREE_RECRUIT = "skilltree_recruit";
    public static final String MILITARY_COMMAND = "military_command";
    public static final String RECRUIT_INVENTORY = "recruit_inventory";
    public static final String RECRUIT_GROUPS = "recruit_groups";
    public static final String RECRUIT_ACTION_FEEDBACK = "recruit_action_feedback";
    public static final String WAR_ROOM = "war_room";
    public static final String POLITICAL_ENTITIES = "political_entities";
    public static final String WAR_DECLARE = "war_declare";
    public static final String WORLD_MAP = "world_map";
    public static final String STOP = "stop";

    private static final List<String> ALL = List.of(
            SKILLTREE_PLAYER,
            SKILLTREE_RECRUIT,
            MILITARY_COMMAND,
            RECRUIT_INVENTORY,
            RECRUIT_GROUPS,
            RECRUIT_ACTION_FEEDBACK,
            WAR_ROOM,
            POLITICAL_ENTITIES,
            WAR_DECLARE,
            WORLD_MAP
    );

    private VisualScenarioIds() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static boolean isKnown(String scenario) {
        return ALL.contains(scenario);
    }

    public static boolean usesNearestRecruit(String scenario) {
        return SKILLTREE_RECRUIT.equals(scenario)
                || RECRUIT_INVENTORY.equals(scenario)
                || RECRUIT_ACTION_FEEDBACK.equals(scenario);
    }

    public static boolean requiresRecruit(String scenario) {
        return RECRUIT_INVENTORY.equals(scenario)
                || RECRUIT_ACTION_FEEDBACK.equals(scenario);
    }

    public static String titleKey(String scenario) {
        return "gui.bannermod.visual_scenario." + scenario + ".title";
    }

    public static String labelKey(String scenario) {
        return "gui.bannermod.visual_scenario." + scenario + ".label";
    }
}
