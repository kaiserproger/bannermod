package com.talhanation.bannermod.client.military.gui;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.inventory.military.GovernorContainer;
import com.talhanation.bannermod.network.messages.military.MessageOpenGovernorScreen;
import com.talhanation.bannermod.network.messages.military.MessageUpdateGovernorPolicy;
import de.maxhenkel.corelib.inventory.ScreenBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.widget.ExtendedButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GovernorScreen extends ScreenBase<GovernorContainer> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(BannerModMain.MOD_ID, "textures/gui/professions/blank_gui.png");
    private static GovernorViewState latestState = GovernorViewState.empty();

    private final Player player;
    private final AbstractRecruitEntity recruit;

    public GovernorScreen(GovernorContainer container, Inventory playerInventory, Component title) {
        super(TEXTURE, container, playerInventory, title);
        this.imageWidth = 320;
        this.imageHeight = 188;
        this.player = container.getPlayerEntity();
        this.recruit = container.getRecruit();
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        addPolicyButtons(BannerModGovernorPolicy.GARRISON_PRIORITY, 120);
        addPolicyButtons(BannerModGovernorPolicy.FORTIFICATION_PRIORITY, 144);
        addPolicyButtons(BannerModGovernorPolicy.TAX_PRESSURE, 168);
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageOpenGovernorScreen(this.recruit.getUUID(), false));
    }

    private void addPolicyButtons(BannerModGovernorPolicy policy, int yOffset) {
        addRenderableWidget(new ExtendedButton(this.leftPos + 150, this.topPos + yOffset, 16, 16, Component.literal("-"), button -> stepPolicy(policy, -1)));
        addRenderableWidget(new ExtendedButton(this.leftPos + 170, this.topPos + yOffset, 16, 16, Component.literal("+"), button -> stepPolicy(policy, 1)));
    }

    private void stepPolicy(BannerModGovernorPolicy policy, int delta) {
        GovernorViewState state = latestState;
        int currentValue = switch (policy) {
            case GARRISON_PRIORITY -> state.garrisonPriority;
            case FORTIFICATION_PRIORITY -> state.fortificationPriority;
            case TAX_PRESSURE -> state.taxPressure;
        };
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageUpdateGovernorPolicy(this.recruit.getUUID(), policy, policy.clamp(currentValue + delta)));
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0C6B98D);
        guiGraphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xF0E6D8B8);
        guiGraphics.fill(leftPos + 202, topPos + 22, leftPos + imageWidth - 8, topPos + imageHeight - 8, 0x503B2F20);
    }

    public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        GovernorViewState state = latestState;
        int x = leftPos + 10;
        int y = topPos + 10;
        guiGraphics.drawString(font, Component.literal("Governor: " + recruit.getName().getString()), x, y, 4210752, false);
        y += 12;
        guiGraphics.drawString(font, Component.literal("Settlement: " + state.settlementStatus), x, y, 4210752, false);
        y += 12;
        guiGraphics.drawString(font, Component.literal("Citizens: " + state.citizenCount), x, y, 4210752, false);
        y += 12;
        guiGraphics.drawString(font, text("gui.bannermod.governor.tax_obligation", state.taxesCollected, state.taxesDue, text(taxObligationStateKey(state)).getString()), x, y, taxObligationColor(state), false);
        y += 12;
        guiGraphics.drawString(font, text(taxObligationConsequenceKey(state)), x, y, taxObligationColor(state), false);
        y += 12;
        guiGraphics.drawString(font, Component.literal("Treasury: " + state.treasuryBalance + " (net " + state.lastTreasuryNet + ")"), x, y, 4210752, false);
        y += 12;
        guiGraphics.drawString(font, Component.literal("Projected: " + state.projectedTreasuryBalance), x, y, 4210752, false);
        y += 12;
        guiGraphics.drawString(font, Component.literal("Heartbeat: " + state.lastHeartbeatTick), x, y, 4210752, false);
        y += 12;
        guiGraphics.drawString(font, Component.literal("Incidents:"), x, y, 4210752, false);
        y += 12;
        int visibleIncidents = Math.min(1, state.incidents.size());
        for (int i = 0; i < visibleIncidents; i++) {
            guiGraphics.drawString(font, Component.literal("- " + state.incidents.get(i)), x, y, 4210752, false);
            y += 10;
        }
        if (state.incidents.isEmpty()) {
            guiGraphics.drawString(font, Component.literal("- none"), x, y, 4210752, false);
            y += 10;
        } else if (state.incidents.size() > visibleIncidents) {
            guiGraphics.drawString(font, text("gui.bannermod.governor.incidents_more", state.incidents.size() - visibleIncidents), x, y, 4210752, false);
            y += 10;
        }
        guiGraphics.drawString(font, Component.literal("garrison priority: " + BannerModGovernorPolicy.GARRISON_PRIORITY.valueLabel(state.garrisonPriority)), leftPos + 10, topPos + 124, 4210752, false);
        guiGraphics.drawString(font, Component.literal("fortification priority: " + BannerModGovernorPolicy.FORTIFICATION_PRIORITY.valueLabel(state.fortificationPriority)), leftPos + 10, topPos + 148, 4210752, false);
        guiGraphics.drawString(font, Component.literal("tax pressure: " + BannerModGovernorPolicy.TAX_PRESSURE.valueLabel(state.taxPressure)), leftPos + 10, topPos + 172, 4210752, false);

        int logisticsX = leftPos + 210;
        int logisticsY = topPos + 10;
        guiGraphics.drawString(font, Component.literal("Logistics"), logisticsX, logisticsY, 4210752, false);
        logisticsY += 14;
        for (String line : state.logisticsLines) {
            guiGraphics.drawString(font, Component.literal(shorten(line, 26)), logisticsX, logisticsY, 4210752, false);
            logisticsY += 11;
        }
        logisticsY += 4;
        guiGraphics.drawString(font, Component.literal("Advice"), logisticsX, logisticsY, 4210752, false);
        logisticsY += 12;
        guiGraphics.drawString(font, Component.literal(shorten("Garrison: " + readableToken(state.garrisonRecommendation), 26)), logisticsX, logisticsY, 4210752, false);
        logisticsY += 11;
        guiGraphics.drawString(font, Component.literal(shorten("Fort: " + readableToken(state.fortificationRecommendation), 26)), logisticsX, logisticsY, 4210752, false);
        logisticsY += 11;
        for (int i = 0; i < Math.min(3, state.recommendations.size()); i++) {
            guiGraphics.drawString(font, Component.literal(shorten("- " + readableToken(state.recommendations.get(i)), 26)), logisticsX, logisticsY, 4210752, false);
            logisticsY += 10;
        }
    }

    public static void applyUpdate(UUID recruitId,
                                    String settlementStatus,
                                    int citizenCount,
                                    int taxesDue,
                                    int taxesCollected,
                                    long lastHeartbeatTick,
                                    String garrisonRecommendation,
                                    String fortificationRecommendation,
                                     int garrisonPriority,
                                     int fortificationPriority,
                                     int taxPressure,
                                     int treasuryBalance,
                                     int lastTreasuryNet,
                                     int projectedTreasuryBalance,
                                      List<String> incidents,
                                      List<String> recommendations,
                                      List<String> logisticsLines) {
        latestState = new GovernorViewState(recruitId, settlementStatus, citizenCount, taxesDue, taxesCollected, lastHeartbeatTick,
                garrisonRecommendation, fortificationRecommendation, garrisonPriority, fortificationPriority, taxPressure,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                new ArrayList<>(incidents), new ArrayList<>(recommendations), new ArrayList<>(logisticsLines));
    }

    private static String readableToken(String token) {
        return token == null || token.isBlank() ? "none" : token.replace('_', ' ');
    }

    private static String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String taxObligationStateKey(GovernorViewState state) {
        if (state.taxesDue <= 0) {
            return "gui.bannermod.governor.tax_state.none";
        }
        if (state.taxesCollected >= state.taxesDue) {
            return "gui.bannermod.governor.tax_state.satisfied";
        }
        return "gui.bannermod.governor.tax_state.unpaid";
    }

    private static String taxObligationConsequenceKey(GovernorViewState state) {
        if (state.taxesDue <= 0) {
            return "gui.bannermod.governor.tax_consequence.none";
        }
        if (state.taxesCollected >= state.taxesDue) {
            return "gui.bannermod.governor.tax_consequence.satisfied";
        }
        return "gui.bannermod.governor.tax_consequence.unpaid";
    }

    private static int taxObligationColor(GovernorViewState state) {
        if (state.taxesDue <= 0) {
            return 0x555555;
        }
        if (state.taxesCollected >= state.taxesDue) {
            return 0x2E7D32;
        }
        return 0x8A1F11;
    }

    private record GovernorViewState(UUID recruitId,
                                     String settlementStatus,
                                     int citizenCount,
                                     int taxesDue,
                                     int taxesCollected,
                                     long lastHeartbeatTick,
                                     String garrisonRecommendation,
                                     String fortificationRecommendation,
                                     int garrisonPriority,
                                     int fortificationPriority,
                                     int taxPressure,
                                     int treasuryBalance,
                                     int lastTreasuryNet,
                                      int projectedTreasuryBalance,
                                      List<String> incidents,
                                      List<String> recommendations,
                                      List<String> logisticsLines) {
        private static GovernorViewState empty() {
            return new GovernorViewState(new UUID(0L, 0L), "unknown", 0, 0, 0, 0L,
                    "hold_course", "hold_course",
                    BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE, BannerModGovernorPolicy.DEFAULT_VALUE,
                    0, 0, 0,
                    List.of(), List.of(), List.of("No settlement logistics snapshot"));
        }
    }
}
