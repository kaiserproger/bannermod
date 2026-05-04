package com.talhanation.bannermod.client.civilian.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.render.ClientRenderPrimitives;
import com.talhanation.bannermod.items.civilian.KinlotStaffItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = BannerModMain.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class KinlotStaffRenderEvents {
    private static final int LOT_COLOR = 0xFFB98542;

    private KinlotStaffRenderEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack stack = kinlotStaffStack(player);
        if (stack.isEmpty()) {
            return;
        }
        BlockPos plotPos = KinlotStaffItem.renderPlotPos(stack);
        if (plotPos == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        AABB lotBox = lotBox(plotPos);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        RenderSystem.disableDepthTest();
        float[] rgb = rgb(LOT_COLOR);
        ClientRenderPrimitives.lineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), lotBox, rgb[0], rgb[1], rgb[2], 1.0F);
        renderLabel(poseStack, bufferSource, lotBox, KinlotStaffItem.renderLabel(stack), KinlotStaffItem.renderHouseholdId(stack));
        RenderSystem.enableDepthTest();
        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static ItemStack kinlotStaffStack(LocalPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof KinlotStaffItem) {
            return main;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        return off.getItem() instanceof KinlotStaffItem ? off : ItemStack.EMPTY;
    }

    private static AABB lotBox(BlockPos plotPos) {
        int half = KinlotStaffItem.LOT_HALF_SPAN;
        return new AABB(
                plotPos.getX() - half,
                plotPos.getY(),
                plotPos.getZ() - half,
                plotPos.getX() + half + 1.0D,
                plotPos.getY() + 4.0D,
                plotPos.getZ() + half + 1.0D
        ).inflate(0.03D);
    }

    private static void renderLabel(PoseStack poseStack,
                                    MultiBufferSource buffers,
                                    AABB lotBox,
                                    String label,
                                    String householdId) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String main = label == null || label.isBlank() ? "Household" : label;
        String sub = householdId == null || householdId.isBlank() ? "" : "Household " + householdId;
        Vec3 center = lotBox.getCenter();

        drawFloatingText(poseStack, buffers, font, Component.literal(main).getVisualOrderText(), center.x, lotBox.maxY + 0.85D, center.z, 0xFFF7E3B2);
        if (!sub.isBlank()) {
            drawFloatingText(poseStack, buffers, font, Component.literal(sub).getVisualOrderText(), center.x, lotBox.maxY + 0.52D, center.z, 0xFFD8B36B);
        }
    }

    private static void drawFloatingText(PoseStack poseStack,
                                         MultiBufferSource buffers,
                                         Font font,
                                         FormattedCharSequence text,
                                         double x,
                                         double y,
                                         double z,
                                         int color) {
        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        float textX = -font.width(text) / 2.0F;
        font.drawInBatch(
                text,
                textX,
                0.0F,
                color,
                false,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.SEE_THROUGH,
                0x66000000,
                LightTexture.FULL_BRIGHT
        );
        poseStack.popPose();
    }

    private static float[] rgb(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F
        };
    }
}
