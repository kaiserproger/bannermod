package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.network.messages.war.MessageInviteAlly;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRecord;
import com.talhanation.bannermod.war.runtime.WarAllyPolicy;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarSide;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Modal-style picker that lists every political entity which can legally be invited
 * to the chosen side (mirrors {@link WarAllyPolicy#canInvite}). Click a row to send
 * the invite packet.
 */
class WarAllyInvitePickerScreen extends Screen {
    private static final int W = 300;
    private static final int H = 220;
    private static final int ROW_H = 16;
    private static final int LIST_VISIBLE = 10;

    private final Screen parent;
    private final WarDeclarationRecord war;
    private final WarSide side;
    private int guiLeft;
    private int guiTop;
    private int scrollOffset = 0;
    private List<PoliticalEntityRecord> options = List.of();

    WarAllyInvitePickerScreen(Screen parent, WarDeclarationRecord war, WarSide side) {
        super(Component.literal("Invite Ally"));
        this.parent = parent;
        this.war = war;
        this.side = side;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - W) / 2;
        this.guiTop = (this.height - H) / 2;

        List<PoliticalEntityRecord> eligible = new ArrayList<>();
        for (PoliticalEntityRecord candidate : WarClientState.entities()) {
            if (denialFor(candidate) == WarAllyPolicy.Denial.OK) {
                eligible.add(candidate);
            }
        }
        this.options = eligible;

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(guiLeft + W - 80, guiTop + H - 28, 70, 18).build());
    }

    private WarAllyPolicy.Denial denialFor(PoliticalEntityRecord candidate) {
        if (candidate == null) return WarAllyPolicy.Denial.INVITEE_UNKNOWN;
        if (candidate.id().equals(war.attackerPoliticalEntityId())
                || candidate.id().equals(war.defenderPoliticalEntityId())) {
            return WarAllyPolicy.Denial.INVITEE_IS_MAIN_SIDE;
        }
        if (war.attackerAllyIds().contains(candidate.id())
                || war.defenderAllyIds().contains(candidate.id())) {
            return WarAllyPolicy.Denial.INVITEE_ON_OPPOSING_SIDE;
        }
        for (WarAllyInviteRecord invite : WarClientState.allyInvitesForWar(war.id())) {
            if (invite.side() == side && invite.inviteePoliticalEntityId().equals(candidate.id())) {
                return WarAllyPolicy.Denial.INVITEE_ALREADY_ON_SIDE;
            }
        }
        if (side == WarSide.ATTACKER && candidate.status() == PoliticalEntityStatus.PEACEFUL) {
            return WarAllyPolicy.Denial.PEACEFUL_CANNOT_JOIN_ATTACKER;
        }
        return WarAllyPolicy.canInvite(war, side, candidate.id(), Optional.of(candidate));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(guiLeft, guiTop, guiLeft + W, guiTop + H, 0xC0101010);
        graphics.renderOutline(guiLeft, guiTop, W, H, 0xFFFFFFFF);

        graphics.drawCenteredString(font, "Invite to " + side.name(),
                guiLeft + W / 2, guiTop + 6, 0xFFFFFF);

        int listX = guiLeft + 8;
        int listY = guiTop + 24;
        int listW = W - 16;
        int listH = LIST_VISIBLE * ROW_H;
        graphics.fill(listX, listY, listX + listW, listY + listH, 0x60000000);

        if (options.isEmpty()) {
            graphics.drawCenteredString(font, "No eligible political entities.",
                    listX + listW / 2, listY + listH / 2 - 4, 0xAAAAAA);
        } else {
            int rendered = Math.min(LIST_VISIBLE, Math.max(0, options.size() - scrollOffset));
            for (int i = 0; i < rendered; i++) {
                PoliticalEntityRecord candidate = options.get(scrollOffset + i);
                int rowY = listY + i * ROW_H;
                boolean hovered = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= rowY && mouseY < rowY + ROW_H;
                if (hovered) {
                    graphics.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, 0x60FFFFFF);
                }
                String label = (candidate.name().isBlank() ? "(unnamed)" : candidate.name())
                        + "  [" + candidate.status().name() + "]";
                graphics.drawString(font, font.plainSubstrByWidth(label, listW - 8),
                        listX + 4, rowY + 4, 0xFFFFFF, false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listX = guiLeft + 8;
            int listY = guiTop + 24;
            int listW = W - 16;
            int listH = LIST_VISIBLE * ROW_H;
            if (mouseX >= listX && mouseX < listX + listW
                    && mouseY >= listY && mouseY < listY + listH) {
                int idx = scrollOffset + (int) ((mouseY - listY) / ROW_H);
                if (idx >= 0 && idx < options.size()) {
                    PoliticalEntityRecord chosen = options.get(idx);
                    BannerModMain.SIMPLE_CHANNEL.sendToServer(
                            new MessageInviteAlly(war.id(), side, chosen.id()));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, options.size() - LIST_VISIBLE);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(delta)));
        return true;
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

    @Nullable
    public WarSide side() {
        return side;
    }
}
