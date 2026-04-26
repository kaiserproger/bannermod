package com.talhanation.bannermod.client.military.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.governance.BannerModContractPolicy;
import com.talhanation.bannermod.network.messages.military.MessageAcceptContract;
import com.talhanation.bannermod.network.messages.military.MessageCancelContract;
import com.talhanation.bannermod.network.messages.military.MessagePinContract;
import com.talhanation.bannermod.network.messages.military.MessageSetContractMaxReward;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateContractBoard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.widget.ExtendedButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GovernorContractScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(BannerModMain.MOD_ID, "textures/gui/professions/blank_gui.png");

    private static ContractBoardState latestState = ContractBoardState.empty();

    private final int imageWidth = 260;
    private final int imageHeight = 240;
    private int leftPos;
    private int topPos;

    private EditBox maxRewardBox;

    public GovernorContractScreen() {
        super(Component.literal("Contract Board"));
    }

    public static void applyUpdate(UUID governorRecruitId, UUID claimUuid, boolean isOwner,
                                   int maxContractReward, long currentTick,
                                   List<MessageToClientUpdateContractBoard.ContractDto> contracts) {
        latestState = new ContractBoardState(governorRecruitId, claimUuid, isOwner, maxContractReward, currentTick,
                new ArrayList<>(contracts));
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GovernorContractScreen)) {
            mc.setScreen(new GovernorContractScreen());
        }
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - imageWidth) / 2;
        this.topPos = (this.height - imageHeight) / 2;

        ContractBoardState state = latestState;

        if (state.isOwner) {
            maxRewardBox = new EditBox(this.font, leftPos + 130, topPos + 14, 60, 12,
                    Component.literal("max reward"));
            maxRewardBox.setMaxLength(6);
            maxRewardBox.setValue(state.maxContractReward > 0 ? String.valueOf(state.maxContractReward) : "");
            addRenderableWidget(maxRewardBox);

            addRenderableWidget(new ExtendedButton(leftPos + 195, topPos + 12, 50, 14,
                    Component.literal("Set"),
                    btn -> commitMaxReward()));
        }

        int yBase = topPos + 40;
        for (int i = 0; i < state.contracts.size() && i < 6; i++) {
            MessageToClientUpdateContractBoard.ContractDto dto = state.contracts.get(i);
            int rowY = yBase + i * 32;
            int col = leftPos + 6;

            if (state.isOwner) {
                addRenderableWidget(new ExtendedButton(col + 200, rowY + 2, 50, 12,
                        Component.literal(dto.pinned() ? "Unpin" : "Pin"),
                        btn -> sendPin(dto.contractId(), !dto.pinned())));
                if ("open".equalsIgnoreCase(dto.status())) {
                    addRenderableWidget(new ExtendedButton(col + 200, rowY + 16, 50, 12,
                            Component.literal("Cancel"),
                            btn -> sendCancel(dto.contractId())));
                }
            } else if ("open".equalsIgnoreCase(dto.status()) && !dto.hasAcceptor()) {
                addRenderableWidget(new ExtendedButton(col + 200, rowY + 8, 50, 14,
                        Component.literal("Accept"),
                        btn -> sendAccept(dto.contractId())));
            }
        }
    }

    private void commitMaxReward() {
        if (maxRewardBox == null) return;
        try {
            int value = Integer.parseInt(maxRewardBox.getValue().trim());
            BannerModMain.SIMPLE_CHANNEL.sendToServer(
                    new MessageSetContractMaxReward(latestState.governorRecruitId, Math.max(0, value)));
        } catch (NumberFormatException ignored) {
        }
    }

    private void sendAccept(UUID contractId) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(
                new MessageAcceptContract(latestState.governorRecruitId, contractId));
    }

    private void sendCancel(UUID contractId) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(
                new MessageCancelContract(latestState.governorRecruitId, contractId));
    }

    private void sendPin(UUID contractId, boolean pin) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(
                new MessagePinContract(latestState.governorRecruitId, contractId, pin));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        ContractBoardState state = latestState;
        int x = leftPos + 6;
        int y = topPos + 6;

        guiGraphics.drawString(font, Component.literal("Contract Board — " + state.claimUuid.toString().substring(0, 8) + "…"), x, y, 0x4E3320, false);

        if (state.isOwner) {
            guiGraphics.drawString(font, Component.literal("Max reward cap: "), x, topPos + 16, 4210752, false);
        }

        if (state.contracts.isEmpty()) {
            guiGraphics.drawString(font, Component.literal("No contracts posted yet."), x, topPos + 50, 4210752, false);
        }

        int yBase = topPos + 40;
        for (int i = 0; i < state.contracts.size() && i < 6; i++) {
            MessageToClientUpdateContractBoard.ContractDto dto = state.contracts.get(i);
            int rowY = yBase + i * 32;
            guiGraphics.fill(leftPos + 4, rowY, leftPos + imageWidth - 4, rowY + 30, 0x22000000);
            guiGraphics.drawString(font, Component.literal(dto.type().replace('_', ' ')), x + 2, rowY + 2, 0x205020, false);
            guiGraphics.drawString(font, Component.literal(BannerModContractPolicy.contractDescription(
                    com.talhanation.bannermod.governance.BannerModGovernorContractType.fromToken(dto.type())
            )), x + 2, rowY + 12, 4210752, false);
            String deadline = BannerModContractPolicy.deadlineLabel(dto.deadlineTick(), state.currentTick, dto.pinned());
            guiGraphics.drawString(font, Component.literal("Reward: " + dto.reward() + "  |  " + dto.status() + "  |  " + deadline),
                    x + 2, rowY + 20, 0x666666, false);
        }

        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ContractBoardState(UUID governorRecruitId, UUID claimUuid, boolean isOwner,
                                      int maxContractReward, long currentTick,
                                      List<MessageToClientUpdateContractBoard.ContractDto> contracts) {
        static ContractBoardState empty() {
            return new ContractBoardState(new UUID(0, 0), new UUID(0, 0), false, 0, 0L, List.of());
        }
    }
}
