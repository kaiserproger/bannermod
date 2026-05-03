package com.talhanation.bannermod.client.civilian.gui;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.network.messages.civilian.MessageOpenNpcProfile;
import com.talhanation.bannermod.society.NpcFamilyMemberSnapshot;
import com.talhanation.bannermod.society.NpcFamilyTreeSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

import java.util.ArrayList;
import java.util.List;

public class NpcFamilyTreeScreen extends Screen {
    private static final int WIDTH = 278;
    private static final int HEIGHT = 260;
    private static final int CARD_W = 76;
    private static final int CARD_H = 84;
    private static final int CHILD_CARD_H = 34;

    private final Screen parent;
    private final NpcFamilyTreeSnapshot snapshot;
    private final List<ClickableCard> clickableCards = new ArrayList<>();
    private int left;
    private int top;

    public NpcFamilyTreeScreen(Screen parent, NpcFamilyTreeSnapshot snapshot) {
        super(Component.translatable("gui.bannermod.family_tree.title"));
        this.parent = parent;
        this.snapshot = snapshot == null ? NpcFamilyTreeSnapshot.empty() : snapshot;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        this.addRenderableWidget(new FamilyButton(
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
        this.clickableCards.clear();
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        MilitaryGuiStyle.parchmentPanel(graphics, this.left, this.top, WIDTH, HEIGHT);
        MilitaryGuiStyle.titleStrip(graphics, this.left + 8, this.top + 8, WIDTH - 16, 16);
        MilitaryGuiStyle.drawCenteredTitle(graphics, this.font, this.title, this.left + 8, this.top + 12, WIDTH - 16);
        graphics.drawString(this.font, Component.translatable("gui.bannermod.family_tree.click_hint"), this.left + 14, this.top + 28, MilitaryGuiStyle.TEXT_MUTED, false);

        renderMemberCard(graphics, this.left + 14, this.top + 34, CARD_W, CARD_H, this.snapshot.mother(), "mother", true);
        renderMemberCard(graphics, this.left + 101, this.top + 26, CARD_W, 96, this.snapshot.self(), "self", true);
        renderMemberCard(graphics, this.left + 188, this.top + 34, CARD_W, CARD_H, this.snapshot.father(), "father", true);
        renderMemberCard(graphics, this.left + 101, this.top + 128, CARD_W, 44, this.snapshot.spouse(), "spouse", true);

        MilitaryGuiStyle.parchmentInset(graphics, this.left + 14, this.top + 182, WIDTH - 28, 52);
        graphics.drawString(this.font, Component.translatable("gui.bannermod.family_tree.children"), this.left + 20, this.top + 188, MilitaryGuiStyle.TEXT_MUTED, false);
        if (this.snapshot.children().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.bannermod.family_tree.children.none"), this.left + 20, this.top + 202, MilitaryGuiStyle.TEXT_DARK, false);
        } else {
            int columns = 3;
            int startX = this.left + 18;
            int startY = this.top + 198;
            int shown = Math.min(6, this.snapshot.children().size());
            for (int i = 0; i < shown; i++) {
                int row = i / columns;
                int col = i % columns;
                renderMemberCard(
                        graphics,
                        startX + col * 82,
                        startY + row * 18,
                        CARD_W,
                        CHILD_CARD_H,
                        this.snapshot.children().get(i),
                        "child",
                        false
                );
            }
            if (this.snapshot.children().size() > shown) {
                graphics.drawString(
                        this.font,
                        Component.translatable("gui.bannermod.family_tree.children.more", this.snapshot.children().size() - shown),
                        this.left + WIDTH - 86,
                        this.top + 216,
                        MilitaryGuiStyle.TEXT_MUTED,
                        false
                );
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (ClickableCard card : this.clickableCards) {
            if (card.contains(mouseX, mouseY)) {
                BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageOpenNpcProfile(card.member().residentUuid()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderMemberCard(GuiGraphics graphics,
                                  int x,
                                  int y,
                                  int width,
                                  int height,
                                  NpcFamilyMemberSnapshot member,
                                  String fallbackRelation,
                                  boolean renderEntity) {
        MilitaryGuiStyle.parchmentInset(graphics, x, y, width, height);
        Component relation = member == null
                ? Component.translatable("gui.bannermod.society.family_relation." + fallbackRelation)
                : Component.translatable(member.relationTranslationKey());
        graphics.drawString(this.font, relation, x + 6, y + 4, MilitaryGuiStyle.TEXT_MUTED, false);
        if (member == null) {
            graphics.drawString(this.font, "-", x + 6, y + 18, MilitaryGuiStyle.TEXT_DARK, false);
            return;
        }
        if (renderEntity) {
            LivingEntity entity = resolveEntity(member);
            if (entity != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics,
                        x + 8,
                        y + 18,
                        x + width - 8,
                        y + Math.min(height - 8, 70),
                        height <= 48 ? 14 : 22,
                        0.0F,
                        0.0F,
                        0.0F,
                        entity
                );
            }
        }
        int textY = height <= 40 ? y + 18 : y + 66;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(member.displayName(), width - 12), x + 6, textY, MilitaryGuiStyle.TEXT_DARK, false);
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(Component.translatable(member.lifeStageTranslationKey()).getString(), width - 12),
                x + 6,
                textY + 10,
                0xFF6E5535,
                false
        );
        this.clickableCards.add(new ClickableCard(x, y, width, height, member));
    }

    private LivingEntity resolveEntity(NpcFamilyMemberSnapshot member) {
        if (member == null || this.minecraft == null || this.minecraft.level == null || member.entityId() < 0) {
            return null;
        }
        Entity entity = this.minecraft.level.getEntity(member.entityId());
        if (!(entity instanceof LivingEntity living) || !member.residentUuid().equals(entity.getUUID())) {
            return null;
        }
        return living;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ClickableCard(int x, int y, int width, int height, NpcFamilyMemberSnapshot member) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private static class FamilyButton extends ExtendedButton {
        FamilyButton(int x, int y, int width, int height, Component label, OnPress handler) {
            super(x, y, width, height, label, handler);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            MilitaryGuiStyle.commandButton(graphics, Minecraft.getInstance().font, mouseX, mouseY,
                    getX(), getY(), width, height, getMessage(), active, false);
        }
    }
}
