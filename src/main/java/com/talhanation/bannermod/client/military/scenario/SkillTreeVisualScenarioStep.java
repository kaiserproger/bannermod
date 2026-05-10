package com.talhanation.bannermod.client.military.scenario;

public enum SkillTreeVisualScenarioStep {
    LOCKED("gui.bannermod.visual_scenario.skilltree.step.locked", "gui.bannermod.visual_scenario.skilltree.feedback.locked"),
    AVAILABLE("gui.bannermod.visual_scenario.skilltree.step.available", "gui.bannermod.perk_tree.feedback.synced"),
    OWNED("gui.bannermod.visual_scenario.skilltree.step.owned", "gui.bannermod.perk_tree.feedback.unlocked"),
    PENDING("gui.bannermod.visual_scenario.skilltree.step.pending", "gui.bannermod.perk_tree.pending"),
    DENIED("gui.bannermod.visual_scenario.skilltree.step.denied", "gui.bannermod.perk_tree.feedback.denied_points"),
    RESPEC_CONFIRM("gui.bannermod.visual_scenario.skilltree.step.respec", "gui.bannermod.perk_tree.feedback.respec");

    private final String titleKey;
    private final String feedbackKey;

    SkillTreeVisualScenarioStep(String titleKey, String feedbackKey) {
        this.titleKey = titleKey;
        this.feedbackKey = feedbackKey;
    }

    public String titleKey() {
        return titleKey;
    }

    public String feedbackKey() {
        return feedbackKey;
    }
}
