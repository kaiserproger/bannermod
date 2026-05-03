package com.talhanation.bannermod.client.civilian.gui;

import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.citizen.CitizenProfession;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.inventory.civilian.CitizenProfileMenu;
import com.talhanation.bannermod.society.NpcFamilyTreeSnapshot;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.society.NpcPhaseOneSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

import javax.annotation.Nullable;
import java.util.UUID;

public class CitizenProfileScreen extends AbstractContainerScreen<CitizenProfileMenu> {
    private static final int GUI_WIDTH = 248;
    private static final int GUI_HEIGHT = 254;

    private final CitizenEntity citizen;
    private final NpcPhaseOneSnapshot phaseOneSnapshot;
    private final NpcFamilyTreeSnapshot familyTreeSnapshot;

    public CitizenProfileScreen(CitizenProfileMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.citizen = menu.getCitizen();
        this.phaseOneSnapshot = menu.getPhaseOneSnapshot();
        this.familyTreeSnapshot = menu.getFamilyTreeSnapshot();
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        // "Assign Vacancy" button — opens a list of nearby work-areas (CropArea/MineArea/
        // etc.) and lets the player pin this citizen to a specific one instead of waiting
        // for assignCitizenToNearestVacancy to pick. The button is layout-stacked above
        // the inventory grid in the right pane (parchmentInset at x=92, y=108).
        int buttonX = this.leftPos + 96;
        int buttonY = this.topPos + 88;
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.translatable("gui.bannermod.citizen_profile.assign_vacancy.button"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new CitizenAssignVacancyScreen(this, this.citizen));
                    }
                }
        ).bounds(buttonX, buttonY, 134, 16).build());
        this.addRenderableWidget(new LedgerButton(
                this.leftPos + this.imageWidth - 62,
                this.topPos + 10,
                48,
                16,
                MilitaryGuiStyle.clampLabel(this.font, Component.translatable("gui.bannermod.family_tree.open"), 42),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new NpcFamilyTreeScreen(this, this.familyTreeSnapshot));
                    }
                }
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0x54160E08);
        MilitaryGuiStyle.parchmentPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        MilitaryGuiStyle.titleStrip(graphics, this.leftPos + 6, this.topPos + 6, this.imageWidth - 12, 14);
        MilitaryGuiStyle.insetPanel(graphics, this.leftPos + 14, this.topPos + 28, 70, 108);
        MilitaryGuiStyle.parchmentInset(graphics, this.leftPos + 92, this.topPos + 28, 142, 72);
        MilitaryGuiStyle.parchmentInset(graphics, this.leftPos + 92, this.topPos + 108, 142, 62);
        MilitaryGuiStyle.parchmentInset(graphics, this.leftPos + 14, this.topPos + 166, 220, 78);
        renderSlotGrid(graphics, this.leftPos + 96, this.topPos + 116, 9, 3);
        renderSlotGrid(graphics, this.leftPos + 14, this.topPos + 174, 9, 3);
        renderSlotGrid(graphics, this.leftPos + 14, this.topPos + 232, 9, 1);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                this.leftPos + 18,
                this.topPos + 34,
                this.leftPos + 80,
                this.topPos + 132,
                34,
                0.0F,
                (float) (this.leftPos + 48) - mouseX,
                (float) (this.topPos + 56) - mouseY,
                this.citizen);
        MilitaryGuiStyle.drawBadge(graphics, this.font, professionLabel(this.citizen.activeProfession()), this.leftPos + 96, this.topPos + 30, 134, MilitaryGuiStyle.TEXT_WARN);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MilitaryGuiStyle.drawCenteredTitle(graphics, this.font, this.title, 0, 10, this.imageWidth);
        // Inset starts at x=92 with width=142, so we have 138px of usable text space.
        final int textBoxWidth = 134;
        drawClamped(graphics, Component.translatable("gui.bannermod.citizen_profile.owner",
                ownerLabel().getString()), 92, 46, textBoxWidth, MilitaryGuiStyle.TEXT_DARK);
        drawClamped(graphics, Component.translatable("gui.bannermod.citizen_profile.assignment",
                assignmentLabel().getString()), 92, 58, textBoxWidth, 0xFF6E5535);
        drawClamped(graphics, Component.translatable("gui.bannermod.citizen_profile.home",
                homeSummary().getString()), 92, 70, textBoxWidth, 0xFF6E5535);
        drawClamped(graphics, Component.translatable("gui.bannermod.citizen_profile.routine",
                routineSummary().getString()), 92, 82, textBoxWidth, 0xFF6E5535);
        drawClamped(graphics, Component.translatable("gui.bannermod.citizen_profile.needs",
                needsSummary().getString()), 92, 94, textBoxWidth, MilitaryGuiStyle.TEXT_DARK);
        graphics.drawString(this.font, Component.translatable("gui.bannermod.citizen_profile.inventory"), 96, 108, MilitaryGuiStyle.TEXT_DARK, false);
        graphics.drawString(this.font, Component.translatable("gui.bannermod.citizen_profile.player_inventory"), 14, 166, MilitaryGuiStyle.TEXT_DARK, false);
    }

    private void drawClamped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        String raw = text.getString();
        if (this.font.width(raw) <= maxWidth) {
            graphics.drawString(this.font, raw, x, y, color, false);
            return;
        }
        // Reserve space for ellipsis so the clamped string fits visibly
        String ellipsis = "…";
        int ellipsisWidth = this.font.width(ellipsis);
        String head = this.font.plainSubstrByWidth(raw, Math.max(0, maxWidth - ellipsisWidth));
        graphics.drawString(this.font, head + ellipsis, x, y, color, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderSlotGrid(GuiGraphics graphics, int x, int y, int cols, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                MilitaryGuiStyle.insetPanel(graphics, x + col * 18, y + row * 18, 18, 18);
            }
        }
    }

    private Component ownerLabel() {
        UUID owner = this.citizen.getOwnerUUID();
        if (owner == null) {
            return Component.translatable("gui.bannermod.citizen_profile.owner.none");
        }
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getPlayerByUUID(owner) != null) {
            return this.minecraft.level.getPlayerByUUID(owner).getDisplayName();
        }
        // Try the online-players cache before giving up to a UUID prefix.
        if (ClientManager.onlinePlayers != null) {
            for (RecruitsPlayerInfo info : ClientManager.onlinePlayers) {
                if (info != null && owner.equals(info.getUUID()) && info.getName() != null && !info.getName().isBlank()) {
                    return Component.literal(info.getName());
                }
            }
        }
        return Component.translatable("gui.bannermod.citizen_profile.owner.unknown",
                owner.toString().substring(0, 8));
    }

    private Component assignmentLabel() {
        @Nullable UUID boundArea = this.citizen.getBoundWorkAreaUUID();
        if (boundArea == null) {
            return Component.translatable("gui.bannermod.citizen_profile.assignment.none");
        }
        // No client-side work-area name cache yet — at minimum label the truncated UUID
        // so the assignment field reads as "(area: 1a2b3c4d)" instead of a bare prefix.
        return Component.translatable("gui.bannermod.citizen_profile.assignment.area",
                boundArea.toString().substring(0, 8));
    }

    private Component homeSummary() {
        return Component.translatable(
                "gui.bannermod.citizen_profile.home.summary",
                NpcPhaseOneSnapshot.shortId(this.phaseOneSnapshot.homeBuildingUuid()),
                NpcPhaseOneSnapshot.shortId(this.phaseOneSnapshot.householdId()),
                this.phaseOneSnapshot.householdSize(),
                Component.translatable(this.phaseOneSnapshot.lifeStageTranslationKey()).getString(),
                Component.translatable(this.phaseOneSnapshot.sexTranslationKey()).getString()
        );
    }

    private Component routineSummary() {
        return Component.translatable(
                "gui.bannermod.citizen_profile.routine.summary",
                Component.translatable(this.phaseOneSnapshot.dailyPhaseTranslationKey()).getString(),
                Component.translatable(this.phaseOneSnapshot.currentIntentTranslationKey()).getString(),
                Component.translatable(this.phaseOneSnapshot.householdHousingStateTranslationKey()).getString(),
                Component.translatable(this.phaseOneSnapshot.housingRequestTranslationKey()).getString()
        );
    }

    private Component needsSummary() {
        return Component.translatable(
                "gui.bannermod.citizen_profile.needs.summary",
                this.phaseOneSnapshot.hungerNeed(),
                this.phaseOneSnapshot.fatigueNeed(),
                this.phaseOneSnapshot.socialNeed()
        );
    }

    private Component professionLabel(CitizenProfession profession) {
        return switch (profession == null ? CitizenProfession.NONE : profession) {
            case NONE -> Component.translatable("gui.bannermod.citizen_profile.profession.none");
            case FARMER -> Component.translatable("bannermod.prefab.profession.farmer");
            case LUMBERJACK -> Component.translatable("bannermod.prefab.profession.lumberjack");
            case MINER -> Component.translatable("bannermod.prefab.profession.miner");
            case ANIMAL_FARMER -> Component.translatable("bannermod.prefab.profession.animal_farmer");
            case BUILDER -> Component.translatable("bannermod.prefab.profession.builder");
            case MERCHANT -> Component.translatable("bannermod.prefab.profession.merchant");
            case FISHERMAN -> Component.translatable("bannermod.prefab.profession.fisherman");
            case RECRUIT_SPEAR -> Component.translatable("gui.bannermod.citizen_profile.profession.recruit_spear");
            case RECRUIT_BOWMAN -> Component.translatable("bannermod.prefab.profession.recruit_archer");
            case RECRUIT_CROSSBOWMAN -> Component.translatable("bannermod.prefab.profession.recruit_crossbow");
            case RECRUIT_HORSEMAN -> Component.translatable("bannermod.prefab.profession.recruit_cavalry");
            case RECRUIT_NOMAD -> Component.translatable("gui.bannermod.citizen_profile.profession.recruit_nomad");
            case RECRUIT_SCOUT -> Component.translatable("gui.bannermod.citizen_profile.profession.recruit_scout");
            case RECRUIT_SHIELDMAN -> Component.translatable("gui.bannermod.citizen_profile.profession.recruit_shieldman");
            case NOBLE -> Component.translatable("gui.bannermod.citizen_profile.profession.noble");
        };
    }

    private static class LedgerButton extends ExtendedButton {
        LedgerButton(int x, int y, int width, int height, Component label, OnPress handler) {
            super(x, y, width, height, label, handler);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            MilitaryGuiStyle.commandButton(graphics, Minecraft.getInstance().font, mouseX, mouseY,
                    getX(), getY(), width, height, getMessage(), active, false);
        }
    }
}
