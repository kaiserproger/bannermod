package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.war.registry.PoliticalColorParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class PoliticalEntityColorPaletteScreen extends Screen {
    private static final int PANEL_W = 252;
    private static final int PANEL_H = 194;
    private static final String[] PALETTE = {
            "F9FFFE", "F9801D", "C74EBD", "3AB3DA",
            "FED83D", "80C71F", "F38BAA", "474F52",
            "9D9D97", "169C9C", "8932B8", "3C44AA",
            "835432", "5E7C16", "B02E26", "1D1D21"
    };

    private final Screen parent;
    private final Consumer<String> onSubmit;
    private String currentColor;
    private int guiLeft;
    private int guiTop;

    public PoliticalEntityColorPaletteScreen(Screen parent, String currentColor, Consumer<String> onSubmit) {
        super(Component.translatable("gui.bannermod.states.dialog.color.title"));
        this.parent = parent;
        this.currentColor = currentColor == null ? "" : currentColor.trim();
        this.onSubmit = onSubmit;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - PANEL_W) / 2;
        this.guiTop = (this.height - PANEL_H) / 2;

        int swatchSize = 28;
        int gap = 6;
        int gridLeft = this.guiLeft + 22;
        int gridTop = this.guiTop + 72;
        for (int i = 0; i < PALETTE.length; i++) {
            String hex = PALETTE[i];
            int column = i % 4;
            int row = i / 4;
            addRenderableWidget(new SwatchButton(
                    gridLeft + column * (swatchSize + gap),
                    gridTop + row * (swatchSize + gap),
                    swatchSize,
                    hex,
                    button -> choose(hex)));
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.states.palette.clear"), button -> choose(""))
                .bounds(this.guiLeft + 150, this.guiTop + 82, 80, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.bannermod.states.palette.clear.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.states.palette.custom"), button -> openCustomDialog())
                .bounds(this.guiLeft + 150, this.guiTop + 108, 80, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.bannermod.states.palette.custom.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.common.back"), button -> onClose())
                .bounds(this.guiLeft + 150, this.guiTop + 134, 80, 20)
                .build());
    }

    private void choose(String color) {
        this.currentColor = color == null ? "" : color;
        this.onSubmit.accept(this.currentColor);
        this.minecraft.setScreen(this.parent);
    }

    private void applyCustomColor(String color) {
        this.currentColor = color == null ? "" : color;
        this.onSubmit.accept(this.currentColor);
    }

    private void openCustomDialog() {
        Minecraft.getInstance().setScreen(new PoliticalEntityNameInputScreen(
                this,
                Component.translatable("gui.bannermod.states.dialog.color.title"),
                Component.translatable("gui.bannermod.states.dialog.color.prompt"),
                this.currentColor,
                this::applyCustomColor,
                9,
                true));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        MilitaryGuiStyle.parchmentPanel(graphics, this.guiLeft, this.guiTop, PANEL_W, PANEL_H);
        MilitaryGuiStyle.titleStrip(graphics, this.guiLeft + 6, this.guiTop + 6, PANEL_W - 12, 14);
        MilitaryGuiStyle.drawCenteredTitle(graphics, this.font, this.title, this.guiLeft, this.guiTop + 9, PANEL_W);

        graphics.drawString(this.font,
                Component.translatable("gui.bannermod.states.palette.current", currentLabel()),
                this.guiLeft + 18,
                this.guiTop + 28,
                MilitaryGuiStyle.TEXT_DARK,
                false);
        graphics.drawString(this.font,
                Component.translatable("gui.bannermod.states.palette.hint"),
                this.guiLeft + 18,
                this.guiTop + 44,
                MilitaryGuiStyle.TEXT_MUTED,
                false);

        int previewColor = PoliticalColorParser.parseArgb(this.currentColor, 0xFFB8A17A);
        graphics.fill(this.guiLeft + 18, this.guiTop + 58, this.guiLeft + 230, this.guiTop + 60, 0x665A4025);
        graphics.fill(this.guiLeft + 150, this.guiTop + 30, this.guiLeft + 230, this.guiTop + 72, 0xFF201810);
        graphics.fill(this.guiLeft + 154, this.guiTop + 34, this.guiLeft + 226, this.guiTop + 68, previewColor);
        graphics.renderOutline(this.guiLeft + 150, this.guiTop + 30, 80, 42, 0xFF8A6A3A);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component currentLabel() {
        if (this.currentColor.isBlank()) {
            return Component.translatable("gui.bannermod.common.none");
        }
        return Component.literal(this.currentColor);
    }

    private static boolean sameColor(String left, String right) {
        return PoliticalColorParser.parseArgb(left, Integer.MIN_VALUE)
                == PoliticalColorParser.parseArgb(right, Integer.MAX_VALUE);
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class SwatchButton extends Button {
        private final String hexColor;
        private final int argbColor;

        private SwatchButton(int x, int y, int size, String hexColor, OnPress onPress) {
            super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
            this.hexColor = hexColor;
            this.argbColor = PoliticalColorParser.parseArgb(hexColor);
            setTooltip(Tooltip.create(Component.literal("#" + hexColor)));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int size = getWidth();
            boolean hovered = isHoveredOrFocused();
            boolean selected = sameColor(currentColor, this.hexColor);
            int border = selected ? 0xFFE0B86A : hovered ? 0xFFB8A17A : 0xFF5A4025;

            graphics.fill(x, y, x + size, y + size, 0xFF201810);
            graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, this.argbColor);
            graphics.renderOutline(x, y, size, size, border);
            if (selected) {
                Font font = Minecraft.getInstance().font;
                graphics.drawCenteredString(font, "*", x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
            }
        }
    }
}
