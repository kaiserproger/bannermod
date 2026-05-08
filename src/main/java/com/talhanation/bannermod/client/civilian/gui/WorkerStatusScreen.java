package com.talhanation.bannermod.client.civilian.gui;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.citizen.CitizenProfession;
import com.talhanation.bannermod.citizen.CitizenRole;
import com.talhanation.bannermod.client.civilian.input.AssignHomeTargetSelector;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.client.military.gui.widgets.ActionMenuButton;
import com.talhanation.bannermod.client.military.gui.widgets.ContextMenuEntry;
import com.talhanation.bannermod.entity.civilian.WorkerInspectionSnapshot;
import com.talhanation.bannermod.network.messages.civilian.MessageConvertWorkerToCitizen;
import com.talhanation.bannermod.network.messages.civilian.MessageDismissWorker;
import com.talhanation.bannermod.network.messages.civilian.MessageOpenWorkerScreen;
import com.talhanation.bannermod.network.messages.civilian.MessageReassignWorkerProfession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorkerStatusScreen extends Screen {
    private static final int WIDTH = 300;
    // 210 gives 22 px clearance between the bottom transport text box (ends at top+168)
    // and the action button row (starts at top + HEIGHT - 26 = top+184) so they never overlap.
    private static final int HEIGHT = 210;
    private static final int BTN_W = 54;
    private static final int BTN_H = 20;

    private final WorkerInspectionSnapshot snapshot;
    private int left;
    private int top;

    public WorkerStatusScreen(WorkerInspectionSnapshot snapshot) {
        super(Component.translatable("gui.bannermod.worker_screen.title"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;

        // Bottom action row: 5 evenly spaced buttons inside WIDTH.
        // Stride between centers = (WIDTH - 16) / 5 = 56 -> stays inside parchment frame.
        int rowY = this.top + HEIGHT - 26;
        int strideX = (WIDTH - 16) / 5;
        int firstCenter = this.left + 8 + strideX / 2;

        SmallCommandButton refresh = this.addRenderableWidget(new SmallCommandButton(
                firstCenter - BTN_W / 2, rowY, BTN_W, BTN_H,
                clamped("gui.bannermod.worker_screen.refresh"),
                button -> BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageOpenWorkerScreen(this.snapshot.workerUuid()))
        ));
        refresh.setTooltip(Tooltip.create(text("gui.bannermod.worker_screen.refresh.tooltip")));

        ActionMenuButton workerActions = new ActionMenuButton(
                firstCenter + strideX - BTN_W / 2, rowY, BTN_W, BTN_H,
                clamped("gui.bannermod.worker_screen.actions"),
                buildWorkerActionEntries()
        );
        workerActions.setOpenUpward(true);
        if (this.snapshot.convertBlockedReasonKey() != null) {
            workerActions.setTooltip(Tooltip.create(text(this.snapshot.convertBlockedReasonKey())));
        } else {
            workerActions.setTooltip(Tooltip.create(text("gui.bannermod.worker_screen.actions.tooltip")));
        }
        this.addRenderableWidget(workerActions);

        // Reassign — opens an ActionMenuButton with one row per available
        // CONTROLLED_WORKER profession (current one filtered out). Server is
        // authoritative; client only sends the intent.
        ActionMenuButton reassign = new ActionMenuButton(
                firstCenter + 2 * strideX - BTN_W / 2, rowY, BTN_W, BTN_H,
                clamped("gui.bannermod.worker_screen.reassign"),
                buildReassignEntries()
        );
        reassign.setOpenUpward(true);
        reassign.setTooltip(Tooltip.create(text("gui.bannermod.worker_screen.reassign.tooltip")));
        reassign.active = this.snapshot.canConvert();
        this.addRenderableWidget(reassign);

        SmallCommandButton assignHome = this.addRenderableWidget(new SmallCommandButton(
                firstCenter + 3 * strideX - BTN_W / 2, rowY, BTN_W, BTN_H,
                clamped("bannermod.assign_home.button"),
                button -> {
                    AssignHomeTargetSelector.start(this.snapshot.workerUuid());
                    this.onClose();
                }
        ));
        assignHome.setTooltip(Tooltip.create(text("bannermod.assign_home.tooltip")));

        SmallCommandButton close = this.addRenderableWidget(new SmallCommandButton(
                firstCenter + 4 * strideX - BTN_W / 2, rowY, BTN_W, BTN_H,
                clamped("gui.bannermod.worker_screen.close"),
                button -> this.onClose()
        ));
        close.setTooltip(Tooltip.create(text("gui.bannermod.worker_screen.close.tooltip")));
    }

    private List<ContextMenuEntry> buildReassignEntries() {
        List<ContextMenuEntry> entries = new ArrayList<>();
        String currentTag = this.snapshot.currentProfessionTag();
        for (CitizenProfession profession : CitizenProfession.values()) {
            if (profession.coarseRole() != CitizenRole.CONTROLLED_WORKER) {
                continue;
            }
            if (profession.name().equals(currentTag)) {
                continue;
            }
            String label = Component.translatable(
                    "gui.bannermod.worker_screen.reassign.option." + profession.name().toLowerCase(Locale.ROOT)
            ).getString();
            entries.add(new ContextMenuEntry(label, () -> {
                BannerModMain.SIMPLE_CHANNEL.sendToServer(
                        new MessageReassignWorkerProfession(this.snapshot.workerUuid(), profession.name())
                );
                this.onClose();
            }, true));
        }
        return entries;
    }

    private List<ContextMenuEntry> buildWorkerActionEntries() {
        List<ContextMenuEntry> entries = new ArrayList<>();
        entries.add(new ContextMenuEntry(
                Component.translatable("gui.bannermod.worker_screen.convert").getString(),
                () -> {
                    BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageConvertWorkerToCitizen(this.snapshot.workerUuid()));
                    this.onClose();
                },
                this.snapshot.canConvert()
        ));
        entries.add(new ContextMenuEntry(
                Component.translatable("gui.bannermod.worker_screen.dismiss").getString(),
                () -> {
                    BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageDismissWorker(this.snapshot.workerUuid()));
                    this.onClose();
                },
                true
        ));
        return entries;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        MilitaryGuiStyle.parchmentPanel(graphics, this.left, this.top, WIDTH, HEIGHT);
        MilitaryGuiStyle.titleStrip(graphics, this.left + 8, this.top + 8, WIDTH - 16, 16);
        MilitaryGuiStyle.drawCenteredTitle(graphics, this.font, this.title, this.left + 8, this.top + 12, WIDTH - 16);
        MilitaryGuiStyle.drawBadge(graphics, this.font, Component.translatable(this.snapshot.claimRelationKey()), this.left + 16, this.top + 32, 110, MilitaryGuiStyle.TEXT_WARN);
        graphics.drawString(this.font, this.snapshot.workerName(), this.left + 132, this.top + 35, MilitaryGuiStyle.TEXT_DARK, false);

        renderInfoBlock(graphics, this.left + 14, this.top + 52, WIDTH - 28, 58);
        drawLabelValue(graphics, text("gui.bannermod.worker_screen.profession"), Component.translatable(this.snapshot.professionKey()), this.left + 20, this.top + 58);
        drawLabelValue(graphics, text("gui.bannermod.worker_screen.owner"), Component.literal(this.snapshot.ownerLabel()), this.left + 20, this.top + 72);
        drawLabelValue(graphics, text("gui.bannermod.worker_screen.political"), Component.literal(this.snapshot.politicalLabel()), this.left + 20, this.top + 86);
        drawLabelValue(graphics, text("gui.bannermod.worker_screen.assignment"), Component.literal(this.snapshot.assignmentLabel()), this.left + 20, this.top + 100);

        renderTextBox(graphics, this.left + 14, this.top + 116, WIDTH - 28, 24,
                text("gui.bannermod.worker_screen.problem"),
                Component.literal(this.snapshot.problemLabel()),
                isClearState(this.snapshot.problemLabel()) ? MilitaryGuiStyle.TEXT_GOOD : MilitaryGuiStyle.TEXT_DENIED);
        renderTextBox(graphics, this.left + 14, this.top + 144, WIDTH - 28, 24,
                text("gui.bannermod.worker_screen.transport"),
                Component.literal(this.snapshot.transportLabel()),
                MilitaryGuiStyle.TEXT_DARK);
    }

    private void renderInfoBlock(GuiGraphics graphics, int x, int y, int width, int height) {
        MilitaryGuiStyle.insetPanel(graphics, x, y, width, height);
    }

    private void renderTextBox(GuiGraphics graphics, int x, int y, int width, int height, Component label, Component value, int color) {
        MilitaryGuiStyle.parchmentInset(graphics, x, y, width, height);
        graphics.drawString(this.font, label, x + 6, y + 4, MilitaryGuiStyle.TEXT_MUTED, false);
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(value, width - 12);
        if (!lines.isEmpty()) {
            graphics.drawString(this.font, lines.getFirst(), x + 6, y + 14, color, false);
        }
    }

    private void drawLabelValue(GuiGraphics graphics, Component label, Component value, int x, int y) {
        graphics.drawString(this.font, label, x, y, MilitaryGuiStyle.TEXT_MUTED, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value.getString(), 136), x + 78, y, MilitaryGuiStyle.TEXT_DARK, false);
    }

    private boolean isClearState(String value) {
        return value == null || value.isBlank() || "none reported".equalsIgnoreCase(value);
    }

    private Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private Component clamped(String key) {
        return MilitaryGuiStyle.clampLabel(this.font, Component.translatable(key), BTN_W - 6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Narrow command button styled with the same parchment-iron palette as
     * {@link com.talhanation.bannermod.client.military.gui.group.RecruitsCommandButton}
     * but width-configurable so four widgets fit inside WIDTH=252.
     */
    private static class SmallCommandButton extends ExtendedButton {
        SmallCommandButton(int x, int y, int width, int height, Component label, OnPress handler) {
            super(x, y, width, height, label, handler);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            MilitaryGuiStyle.commandButton(graphics, Minecraft.getInstance().font, mouseX, mouseY,
                    getX(), getY(), width, height, getMessage(), active, false);
        }
    }
}
