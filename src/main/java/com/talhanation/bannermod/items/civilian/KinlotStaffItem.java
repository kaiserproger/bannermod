package com.talhanation.bannermod.items.civilian;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.society.NpcHousingPlotPlanner;
import com.talhanation.bannermod.society.NpcHousingRequestRecord;
import com.talhanation.bannermod.util.ItemStackComponentData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public class KinlotStaffItem extends Item {
    private static final double ACTIONBAR_RADIUS = 10.0D;
    private static final double DETAIL_RADIUS = 12.0D;
    public static final int LOT_HALF_SPAN = 4;
    private static final String TAG_RENDER_PLOT = "bannermod:kinlot_plot";
    private static final String TAG_RENDER_LABEL = "bannermod:kinlot_label";
    private static final String TAG_RENDER_HOUSEHOLD = "bannermod:kinlot_household";
    private static final String TAG_RENDER_STATUS = "bannermod:kinlot_status";

    public KinlotStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        RecruitsClaim claim = claimAt(context.getClickedPos());
        if (claim == null) {
            serverPlayer.sendSystemMessage(Component.translatable("item.bannermod.kinlot_staff.no_claim").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        NpcHousingPlotPlanner.HousingPlotInfo info = NpcHousingPlotPlanner.nearestPlotInfo(
                serverPlayer.serverLevel(),
                claim.getUUID(),
                context.getClickedPos(),
                DETAIL_RADIUS
        );
        if (info == null) {
            clearRenderData(context.getItemInHand());
            serverPlayer.sendSystemMessage(Component.translatable("item.bannermod.kinlot_staff.no_plot").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        writeRenderData(context.getItemInHand(), info, serverPlayer);
        sendDetails(serverPlayer, info);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide() || !isSelected || !(entity instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        RecruitsClaim claim = claimAt(player.blockPosition());
        if (claim == null) {
            clearRenderData(stack);
            return;
        }
        NpcHousingPlotPlanner.HousingPlotInfo info = NpcHousingPlotPlanner.nearestPlotInfo(
                player.serverLevel(),
                claim.getUUID(),
                player.blockPosition(),
                ACTIONBAR_RADIUS
        );
        if (info == null) {
            clearRenderData(stack);
            return;
        }
        writeRenderData(stack, info, player);
        player.displayClientMessage(Component.translatable(
                "item.bannermod.kinlot_staff.actionbar",
                shortId(info.request().householdId()),
                info.household() == null
                        ? Component.literal("-")
                        : Component.translatable("gui.bannermod.society.household_housing."
                        + info.household().housingState().name().toLowerCase(Locale.ROOT)),
                info.plotPos().getX(),
                info.plotPos().getZ()
        ).withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bannermod.kinlot_staff.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bannermod.kinlot_staff.tooltip.2").withStyle(ChatFormatting.GRAY));
    }

    public static BlockPos renderPlotPos(ItemStack stack) {
        CompoundTag tag = ItemStackComponentData.read(stack);
        return tag != null && tag.contains(TAG_RENDER_PLOT) ? BlockPos.of(tag.getLong(TAG_RENDER_PLOT)) : null;
    }

    public static String renderLabel(ItemStack stack) {
        CompoundTag tag = ItemStackComponentData.read(stack);
        return tag != null && tag.contains(TAG_RENDER_LABEL) ? tag.getString(TAG_RENDER_LABEL) : null;
    }

    public static String renderHouseholdId(ItemStack stack) {
        CompoundTag tag = ItemStackComponentData.read(stack);
        return tag != null && tag.contains(TAG_RENDER_HOUSEHOLD) ? tag.getString(TAG_RENDER_HOUSEHOLD) : null;
    }

    public static String renderStatus(ItemStack stack) {
        CompoundTag tag = ItemStackComponentData.read(stack);
        return tag != null && tag.contains(TAG_RENDER_STATUS) ? tag.getString(TAG_RENDER_STATUS) : null;
    }

    private static void sendDetails(ServerPlayer player, NpcHousingPlotPlanner.HousingPlotInfo info) {
        NpcHousingRequestRecord request = info.request();
        Component housingState = info.household() == null
                ? Component.literal("-")
                : Component.translatable("gui.bannermod.society.household_housing."
                + info.household().housingState().name().toLowerCase(Locale.ROOT));
        int members = info.household() == null ? 0 : info.household().memberResidentUuids().size();
        Component status = Component.translatable("gui.bannermod.society.housing_request."
                + request.status().name().toLowerCase(Locale.ROOT));
        player.sendSystemMessage(Component.translatable(
                "item.bannermod.kinlot_staff.detail.header",
                shortId(request.householdId()),
                info.plotPos().getX(),
                info.plotPos().getY(),
                info.plotPos().getZ()
        ).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable(
                "item.bannermod.kinlot_staff.detail.line",
                shortId(request.residentUuid()),
                members,
                housingState,
                status,
                request.buildAreaUuid() == null ? "-" : shortId(request.buildAreaUuid())
        ).withStyle(ChatFormatting.GRAY));
    }

    private static RecruitsClaim claimAt(BlockPos pos) {
        if (pos == null || ClaimEvents.claimManager() == null) {
            return null;
        }
        return ClaimEvents.claimManager().getClaim(new ChunkPos(pos));
    }

    private static void writeRenderData(ItemStack stack, NpcHousingPlotPlanner.HousingPlotInfo info, ServerPlayer player) {
        if (stack == null || info == null) {
            return;
        }
        String label = residentDisplayName(player, info.request());
        ItemStackComponentData.update(stack, tag -> {
            tag.putLong(TAG_RENDER_PLOT, info.plotPos().asLong());
            tag.putString(TAG_RENDER_LABEL, label);
            tag.putString(TAG_RENDER_HOUSEHOLD, shortId(info.request().householdId()));
            tag.putString(TAG_RENDER_STATUS, info.request().status().name().toLowerCase(Locale.ROOT));
        });
    }

    private static void clearRenderData(ItemStack stack) {
        if (stack == null) {
            return;
        }
        ItemStackComponentData.update(stack, tag -> {
            tag.remove(TAG_RENDER_PLOT);
            tag.remove(TAG_RENDER_LABEL);
            tag.remove(TAG_RENDER_HOUSEHOLD);
            tag.remove(TAG_RENDER_STATUS);
        });
    }

    private static String residentDisplayName(ServerPlayer player, NpcHousingRequestRecord request) {
        if (player == null || request == null) {
            return "Household";
        }
        Entity resident = player.serverLevel().getEntity(request.residentUuid());
        if (resident != null) {
            return resident.getName().getString();
        }
        return "House of " + shortId(request.residentUuid());
    }

    private static String shortId(java.util.UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }
}
