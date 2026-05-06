package com.talhanation.bannermod.client.civilian.gui;

import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.society.NpcPhaseOneSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

public class NpcAiDecisionScreen extends Screen {
    private static final int WIDTH = 278;
    private static final int HEIGHT = 214;

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
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        MilitaryGuiStyle.parchmentPanel(graphics, this.left, this.top, WIDTH, HEIGHT);
        MilitaryGuiStyle.titleStrip(graphics, this.left + 8, this.top + 8, WIDTH - 16, 16);
        MilitaryGuiStyle.drawCenteredTitle(graphics, this.font, this.title, this.left + 8, this.top + 12, WIDTH - 16);

        renderSmallField(graphics, this.left + 14, this.top + 36, 120,
                Component.translatable("gui.bannermod.society.ai.state"),
                Component.translatable(this.snapshot.aiStateTranslationKey()).getString(),
                MilitaryGuiStyle.TEXT_DARK);
        renderSmallField(graphics, this.left + 144, this.top + 36, 120,
                Component.translatable("gui.bannermod.society.ai.phase"),
                Component.translatable(this.snapshot.dailyPhaseTranslationKey()).getString(),
                MilitaryGuiStyle.TEXT_DARK);
        renderSmallField(graphics, this.left + 14, this.top + 66, 120,
                Component.translatable("gui.bannermod.society.ai.intent"),
                Component.translatable(this.snapshot.currentIntentTranslationKey()).getString(),
                MilitaryGuiStyle.TEXT_DARK);
        renderSmallField(graphics, this.left + 144, this.top + 66, 120,
                Component.translatable("gui.bannermod.society.ai.anchor"),
                Component.translatable(this.snapshot.currentAnchorTranslationKey()).getString(),
                MilitaryGuiStyle.TEXT_DARK);

        renderLargeField(graphics, this.left + 14, this.top + 102, WIDTH - 28,
                Component.translatable("gui.bannermod.society.ai.goal"),
                Component.literal(this.snapshot.aiCurrentGoalLabel()),
                Component.translatable(this.snapshot.aiChoiceReasonTranslationKey()),
                MilitaryGuiStyle.TEXT_WARN);
        renderLargeField(graphics, this.left + 14, this.top + 142, WIDTH - 28,
                Component.translatable("gui.bannermod.society.ai.blocked_goal"),
                Component.literal(this.snapshot.aiBlockedGoalLabel()),
                Component.translatable(this.snapshot.aiBlockedReasonTranslationKey()),
                "-".equals(this.snapshot.aiBlockedGoalLabel()) ? MilitaryGuiStyle.TEXT_DARK : MilitaryGuiStyle.TEXT_DENIED);

        super.render(graphics, mouseX, mouseY, partialTick);
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
                                  Component label,
                                  Component primary,
                                  Component secondary,
                                  int primaryColor) {
        MilitaryGuiStyle.parchmentInset(graphics, x, y, width, 34);
        graphics.drawString(this.font, label, x + 6, y + 4, MilitaryGuiStyle.TEXT_MUTED, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(primary.getString(), width - 12), x + 6, y + 14, primaryColor, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(secondary.getString(), width - 12), x + 6, y + 24, MilitaryGuiStyle.TEXT_DARK, false);
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
