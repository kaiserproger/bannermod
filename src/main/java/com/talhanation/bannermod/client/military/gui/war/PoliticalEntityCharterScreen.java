package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.client.military.gui.component.RecruitsMultiLineEditBox;
import com.talhanation.bannermod.war.registry.PoliticalRegistryValidation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class PoliticalEntityCharterScreen extends Screen {
    private static final int W = 320;
    private static final int H = 224;

    private final Screen parent;
    private final Consumer<String> onSubmit;
    private final String initialValue;
    private int guiLeft;
    private int guiTop;
    private RecruitsMultiLineEditBox charterBox;

    public PoliticalEntityCharterScreen(Screen parent, String initialValue, Consumer<String> onSubmit) {
        super(Component.translatable("gui.bannermod.states.dialog.charter.title"));
        this.parent = parent;
        this.onSubmit = onSubmit;
        this.initialValue = initialValue == null ? "" : initialValue;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - W) / 2;
        this.guiTop = Math.max(8, (this.height - H) / 2);

        this.charterBox = new RecruitsMultiLineEditBox(font, guiLeft + 14, guiTop + 68, W - 28, 100, Component.empty(), Component.empty());
        this.charterBox.setValue(this.initialValue);
        this.charterBox.setEnableEditing(true);
        this.charterBox.setCharacterLimit(PoliticalRegistryValidation.MAX_CHARTER_LENGTH);
        this.charterBox.setFocused(true);
        addRenderableWidget(this.charterBox);
        setInitialFocus(this.charterBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.common.submit"), button -> submit())
                .bounds(guiLeft + 14, guiTop + H - 28, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.states.dialog.charter.clear"), button -> clearDraft())
                .bounds(guiLeft + 116, guiTop + H - 28, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.common.back"), button -> onClose())
                .bounds(guiLeft + W - 102, guiTop + H - 28, 88, 20)
                .build());
    }

    private void submit() {
        this.onSubmit.accept(this.charterBox.getValue().trim());
        onClose();
    }

    private void clearDraft() {
        this.charterBox.setValue("");
        this.charterBox.setFocused(true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        MilitaryGuiStyle.parchmentPanel(graphics, guiLeft, guiTop, W, H);
        MilitaryGuiStyle.titleStrip(graphics, guiLeft + 8, guiTop + 8, W - 16, 14);
        MilitaryGuiStyle.parchmentInset(graphics, guiLeft + 10, guiTop + 24, W - 20, 36);
        MilitaryGuiStyle.insetPanel(graphics, guiLeft + 12, guiTop + 66, W - 24, 104);

        MilitaryGuiStyle.drawCenteredTitle(graphics, font, title, guiLeft, guiTop + 11, W);
        drawWrapped(graphics,
                Component.translatable("gui.bannermod.states.dialog.charter.subtitle"),
                guiLeft + 16,
                guiTop + 30,
                W - 32,
                MilitaryGuiStyle.TEXT_DARK);
        graphics.drawString(font,
                Component.translatable("gui.bannermod.states.dialog.charter.prompt", PoliticalRegistryValidation.MAX_CHARTER_LENGTH),
                guiLeft + 14,
                guiTop + 58,
                MilitaryGuiStyle.TEXT_MUTED,
                false);

        if (this.charterBox != null) {
            String count = Component.translatable(
                    "gui.bannermod.states.dialog.charter.count",
                    this.charterBox.getValue().length(),
                    PoliticalRegistryValidation.MAX_CHARTER_LENGTH).getString();
            graphics.drawString(font, count, guiLeft + 14, guiTop + 176, MilitaryGuiStyle.TEXT_MUTED, false);
            Component preview = this.charterBox.getValue().isBlank()
                    ? Component.translatable("gui.bannermod.states.dialog.charter.empty")
                    : Component.translatable("gui.bannermod.states.dialog.charter.preview", this.charterBox.getValue().replaceAll("\\s+", " ").trim());
            graphics.drawString(font,
                    font.plainSubstrByWidth(preview.getString(), W - 32),
                    guiLeft + 14,
                    guiTop + 188,
                    this.charterBox.getValue().isBlank() ? MilitaryGuiStyle.TEXT_MUTED : MilitaryGuiStyle.TEXT_DARK,
                    false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(text, width);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
