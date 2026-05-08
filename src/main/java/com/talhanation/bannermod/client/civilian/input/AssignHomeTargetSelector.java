package com.talhanation.bannermod.client.civilian.input;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.network.messages.civilian.MessageAssignHome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

public final class AssignHomeTargetSelector {
    private static final long TIMEOUT_MS = 30_000L;
    private static final int PANEL_WIDTH = 214;
    private static final int PANEL_HEIGHT = 30;
    private static final int RIGHT_SAFE_MARGIN = 8;

    private static UUID entityUuid;
    private static long startedAtMs;

    private AssignHomeTargetSelector() {
    }

    public static void start(UUID targetEntityUuid) {
        entityUuid = targetEntityUuid;
        startedAtMs = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("bannermod.assign_home.prompt"), true);
        }
    }

    public static boolean isActive() {
        return entityUuid != null;
    }

    public static void tick() {
        if (!isActive()) return;
        if (System.currentTimeMillis() - startedAtMs >= TIMEOUT_MS) {
            cancel("bannermod.assign_home.cancel.timeout");
        }
    }

    public static boolean cancelWithEscape() {
        if (!isActive()) return false;
        cancel("bannermod.assign_home.cancel.escape");
        return true;
    }

    public static boolean handleUseOnTargetedBlock() {
        if (!isActive()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        UUID uuid = entityUuid;
        BlockPos pos = hit.getBlockPos().immutable();
        clear();
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageAssignHome(uuid, pos));
        return true;
    }

    public static int renderPrompt(GuiGraphics graphics, Minecraft mc, int y) {
        if (!isActive() || mc.player == null) return y;
        Font font = mc.font;
        int x = Math.max(6, graphics.guiWidth() - PANEL_WIDTH - RIGHT_SAFE_MARGIN);
        MilitaryGuiStyle.parchmentPanel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.drawString(font, Component.translatable("bannermod.assign_home.hud.title"), x + 8, y + 6,
                MilitaryGuiStyle.TEXT_DARK, false);
        graphics.drawString(font, Component.translatable("bannermod.assign_home.hud.remaining", remainingSeconds()),
                x + 8, y + 18, MilitaryGuiStyle.TEXT_MUTED, false);
        return y + PANEL_HEIGHT + 4;
    }

    private static int remainingSeconds() {
        long remainingMs = Math.max(0L, TIMEOUT_MS - (System.currentTimeMillis() - startedAtMs));
        return (int) Math.ceil(remainingMs / 1000.0D);
    }

    private static void cancel(String messageKey) {
        clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    private static void clear() {
        entityUuid = null;
        startedAtMs = 0L;
    }
}
