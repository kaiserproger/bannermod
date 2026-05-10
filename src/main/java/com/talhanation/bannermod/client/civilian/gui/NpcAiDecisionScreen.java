package com.talhanation.bannermod.client.civilian.gui;

import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.society.NpcPhaseOneSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

public class NpcAiDecisionScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 314;

    private final Screen parent;
    private final NpcPhaseOneSnapshot snapshot;
    private int left;
    private int top;

    public NpcAiDecisionScreen(Screen parent, NpcPhaseOneSnapshot snapshot) {
        super(Component.translatable("gui.bannermod.society.ai.title"));
        this.parent = parent;
        this.snapshot = snapshot == null ? NpcPhaseOneSnapshot.empty() : snapshot;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        this.addRenderableWidget(new DecisionButton(
                this.left + WIDTH - 62,
                this.top + HEIGHT - 26,
                48,
                16,
                MilitaryGuiStyle.clampLabel(this.font, Component.translatable("gui.bannermod.common.back"), 42),
                button -> onClose()
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x54160E08);
        MilitaryGuiStyle.parchmentPanel(graphics, this.left, this.top, WIDTH, HEIGHT);
        MilitaryGuiStyle.titleStrip(graphics, this.left + 8, this.top + 8, WIDTH - 16, 16);
        MilitaryGuiStyle.drawCenteredTitle(graphics, this.font, this.title, this.left + 8, this.top + 12, WIDTH - 16);

        renderSmallField(graphics, this.left + 14, this.top + 36, 140,
                Component.translatable("gui.bannermod.society.ai.state"),
                Component.translatable(this.snapshot.aiStateTranslationKey()).getString(),
                this.snapshot.isBlockedState()
                        ? MilitaryGuiStyle.TEXT_DENIED
                        : MilitaryGuiStyle.TEXT_DARK);
        renderSmallField(graphics, this.left + 166, this.top + 36, 140,
                Component.translatable("gui.bannermod.society.ai.phase"),
                Component.translatable(this.snapshot.dailyPhaseTranslationKey()).getString(),
                MilitaryGuiStyle.TEXT_DARK);
        renderSmallField(graphics, this.left + 14, this.top + 66, 140,
                Component.translatable("gui.bannermod.society.ai.intent"),
                Component.translatable(this.snapshot.currentIntentTranslationKey()).getString(),
                MilitaryGuiStyle.TEXT_DARK);
        renderSmallField(graphics, this.left + 166, this.top + 66, 140,
                Component.translatable("gui.bannermod.society.ai.anchor"),
                Component.translatable(this.snapshot.currentAnchorTranslationKey()).getString(),
                MilitaryGuiStyle.TEXT_DARK);

        renderLargeField(graphics, this.left + 14, this.top + 96, WIDTH - 28, 44,
                Component.translatable("gui.bannermod.society.ai.route"),
                Component.translatable(this.snapshot.aiRouteReasonTranslationKey()),
                this.snapshot.aiRouteSecondaryComponent(),
                MilitaryGuiStyle.TEXT_DARK);

        renderLargeField(graphics, this.left + 14, this.top + 146, WIDTH - 28, 44,
                Component.translatable("gui.bannermod.society.ai.goal"),
                this.snapshot.aiCurrentGoalComponent(),
                Component.translatable(this.snapshot.aiChoiceReasonTranslationKey()),
                MilitaryGuiStyle.TEXT_WARN);
        renderLargeField(graphics, this.left + 14, this.top + 196, WIDTH - 28, 44,
                Component.translatable("gui.bannermod.society.ai.blocked_goal"),
                this.snapshot.aiBlockedGoalComponent(),
                Component.translatable(this.snapshot.aiBlockedReasonTranslationKey()),
                this.snapshot.hasAiBlockedGoal() ? MilitaryGuiStyle.TEXT_DENIED : MilitaryGuiStyle.TEXT_DARK);

        renderStackedField(graphics, this.left + 14, this.top + 250, WIDTH - 28, 34,
                Component.translatable("gui.bannermod.society.ai.pressures"),
                shortNeedLineLeft(),
                shortNeedLineRight(),
                MilitaryGuiStyle.TEXT_DARK);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderSmallField(GuiGraphics graphics, int x, int y, int width, Component label, String value, int color) {
        MilitaryGuiStyle.parchmentInset(graphics, x, y, width, 24);
        graphics.drawString(this.font, label, x + 6, y + 4, MilitaryGuiStyle.TEXT_MUTED, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value, width - 12), x + 6, y + 14, color, false);
    }

    private void renderLargeField(GuiGraphics graphics,
                                  int x,
                                  int y,
                                  int width,
                                  int height,
                                  Component label,
                                  Component primary,
                                  Component secondary,
                                  int primaryColor) {
        MilitaryGuiStyle.parchmentInset(graphics, x, y, width, height);
        graphics.drawString(this.font, label, x + 6, y + 4, MilitaryGuiStyle.TEXT_MUTED, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(primary.getString(), width - 12), x + 6, y + 16, primaryColor, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(secondary.getString(), width - 12), x + 6, y + 28, MilitaryGuiStyle.TEXT_DARK, false);
    }

    private void renderStackedField(GuiGraphics graphics,
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    Component label,
                                    String lineOne,
                                    String lineTwo,
                                    int valueColor) {
        MilitaryGuiStyle.parchmentInset(graphics, x, y, width, height);
        graphics.drawString(this.font, label, x + 6, y + 4, MilitaryGuiStyle.TEXT_MUTED, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(lineOne, width - 12), x + 6, y + 14, valueColor, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(lineTwo, width - 12), x + 6, y + 24, valueColor, false);
    }

    private String shortNeedLineLeft() {
        return Component.translatable("gui.bannermod.society.ai.pressures.line_one",
                this.snapshot.hungerNeed(), this.snapshot.fatigueNeed()).getString();
    }

    private String shortNeedLineRight() {
        return Component.translatable("gui.bannermod.society.ai.pressures.line_two",
                this.snapshot.safetyNeed(),
                NpcPhaseOneSnapshot.shortId(this.snapshot.homeBuildingUuid()),
                NpcPhaseOneSnapshot.shortId(this.snapshot.workBuildingUuid())).getString();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class DecisionButton extends ExtendedButton {
        DecisionButton(int x, int y, int width, int height, Component label, OnPress handler) {
            super(x, y, width, height, label, handler);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            MilitaryGuiStyle.commandButton(graphics, Minecraft.getInstance().font, mouseX, mouseY,
                    getX(), getY(), width, height, getMessage(), active, false);
        }
    }
}
