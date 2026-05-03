package com.talhanation.bannermod.items.civilian;

import com.talhanation.bannermod.client.civilian.gui.SettlementSurveyorScreen;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.SurveyorDraftSuggestionService;
import com.talhanation.bannermod.settlement.validation.SurveyorModeGuidance;
import com.talhanation.bannermod.settlement.validation.SettlementSurveyorService;
import com.talhanation.bannermod.settlement.validation.SurveyorMode;
import com.talhanation.bannermod.settlement.validation.SurveyorSessionCodec;
import com.talhanation.bannermod.settlement.validation.ValidationSession;
import com.talhanation.bannermod.util.ItemStackComponentData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

public class SettlementSurveyorToolItem extends Item {
    private static final String TAG_PENDING_CORNER = "bannermod:settlement_survey_pending_corner";
    private static final String TAG_SELECTED_ROLE = "bannermod:settlement_survey_selected_role";

    public SettlementSurveyorToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openScreen(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                openScreen(context.getHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ValidationSession session = getOrCreateSession(player, stack);
        if (session.anchorPos().equals(BlockPos.ZERO)) {
            SurveyorSessionCodec.write(stack, session.withAnchor(clicked));
            player.sendSystemMessage(Component.translatable("bannermod.surveyor.anchor_set", clicked.toShortString()).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        CompoundTag tag = ItemStackComponentData.read(stack);
        if (!tag.contains(TAG_PENDING_CORNER)) {
            ItemStackComponentData.update(stack, data -> data.putLong(TAG_PENDING_CORNER, clicked.asLong()));
            player.sendSystemMessage(Component.translatable("bannermod.surveyor.corner_a", clicked.toShortString()).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        BlockPos cornerA = BlockPos.of(tag.getLong(TAG_PENDING_CORNER));
        ItemStackComponentData.update(stack, data -> data.remove(TAG_PENDING_CORNER));
        ZoneRole role = selectedRole(stack);
        ZoneSelection capturedSelection = SurveyorModeGuidance.normalizeSelection(session.mode(), new ZoneSelection(role, cornerA, clicked, clicked));
        ValidationSession updated = session.upsertSelection(role, capturedSelection.min(), capturedSelection.max(), capturedSelection.marker());
        SurveyorSessionCodec.write(stack, updated);
        player.sendSystemMessage(Component.translatable("bannermod.surveyor.zone_captured", roleLabel(role)).withStyle(ChatFormatting.GREEN));
        maybeAdvanceRoleAfterCapture(player, stack, updated, role);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ValidationSession session = SurveyorSessionCodec.read(stack);
        SurveyorMode mode = session == null ? SurveyorMode.BOOTSTRAP_FORT : session.mode();
        List<ZoneRole> requiredRoles = SurveyorModeGuidance.requiredRoles(mode);
        tooltip.add(Component.translatable("bannermod.surveyor.tooltip.mode", modeLabel(mode))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("bannermod.surveyor.tooltip.role", roleLabel(selectedRole(stack)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("bannermod.surveyor.mode_hint." + mode.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.YELLOW));
        if (session != null) {
            tooltip.add(Component.translatable("bannermod.surveyor.tooltip.anchor", session.anchorPos().equals(BlockPos.ZERO) ? "-" : session.anchorPos().toShortString())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("bannermod.surveyor.tooltip.zones", session.selections().size())
                    .withStyle(ChatFormatting.GRAY));
        }
        CompoundTag tag = ItemStackComponentData.read(stack);
        if (tag != null && tag.contains(TAG_PENDING_CORNER)) {
            tooltip.add(Component.translatable("bannermod.surveyor.tooltip.pending", BlockPos.of(tag.getLong(TAG_PENDING_CORNER)).toShortString())
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("bannermod.surveyor.tooltip.use")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("bannermod.surveyor.tooltip.shift")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("bannermod.surveyor.tooltip.manual_only")
                .withStyle(ChatFormatting.YELLOW));
        if (!requiredRoles.isEmpty()) {
            tooltip.add(Component.translatable("bannermod.surveyor.tooltip.required_roles", roleList(requiredRoles))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (mode == SurveyorMode.BOOTSTRAP_FORT) {
            tooltip.add(Component.translatable("bannermod.surveyor.tooltip.fort_rules")
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("bannermod.surveyor.tooltip.loop_1")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("bannermod.surveyor.tooltip.loop_2")
                .withStyle(ChatFormatting.YELLOW));
    }

    public static ValidationSession getOrCreateSession(Player player, ItemStack stack) {
        ValidationSession existing = SurveyorSessionCodec.read(stack);
        if (existing != null) {
            ensureSelectedRole(stack, existing.mode());
            return existing;
        }
        ValidationSession created = new ValidationSession(player.getUUID(), SurveyorMode.BOOTSTRAP_FORT, BlockPos.ZERO, java.util.List.of(), true);
        SurveyorSessionCodec.write(stack, created);
        setSelectedRole(stack, defaultRoleForMode(created.mode()));
        return created;
    }

    public static void setMode(Player player, ItemStack stack, SurveyorMode mode) {
        if (player == null || stack == null || mode == null) {
            return;
        }
        ValidationSession session = getOrCreateSession(player, stack);
        ItemStackComponentData.update(stack, data -> data.remove(TAG_PENDING_CORNER));
        ValidationSession updated = session.mode() == mode
                ? session.withMode(mode)
                : new ValidationSession(player.getUUID(), mode, BlockPos.ZERO, List.of(), session.showGuidePreview());
        SurveyorSessionCodec.write(stack, updated);
        setSelectedRole(stack, defaultRoleForMode(mode));
    }

    public static void validateCurrentSession(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        ValidationSession session = SurveyorSessionCodec.read(stack);
        if (session == null) {
            player.sendSystemMessage(Component.translatable("bannermod.surveyor.no_session").withStyle(ChatFormatting.RED));
            return;
        }
        SettlementSurveyorService.validateCurrentSession(player, session);
    }

    public static void cancelPendingCorner(ServerPlayer player, ItemStack stack) {
        if (stack == null || !hasPendingCorner(stack)) {
            return;
        }
        ItemStackComponentData.update(stack, data -> data.remove(TAG_PENDING_CORNER));
        if (player != null) {
            player.sendSystemMessage(Component.translatable("bannermod.surveyor.pending_cleared").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void clearSelectedRoleZone(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        ValidationSession session = SurveyorSessionCodec.read(stack);
        if (session == null) {
            return;
        }
        ZoneRole role = selectedRole(stack);
        ValidationSession updated = session.withoutSelection(role);
        SurveyorSessionCodec.write(stack, updated);
        player.sendSystemMessage(Component.translatable("bannermod.surveyor.role_cleared", roleLabel(role)).withStyle(ChatFormatting.YELLOW));
    }

    public static void resetAllMarks(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        ValidationSession session = SurveyorSessionCodec.read(stack);
        SurveyorMode mode = session == null ? SurveyorMode.BOOTSTRAP_FORT : session.mode();
        ItemStackComponentData.update(stack, data -> data.remove(TAG_PENDING_CORNER));
        boolean showGuidePreview = session == null || session.showGuidePreview();
        SurveyorSessionCodec.write(stack, new ValidationSession(player.getUUID(), mode, BlockPos.ZERO, List.of(), showGuidePreview));
        setSelectedRole(stack, defaultRoleForMode(mode));
        player.sendSystemMessage(Component.translatable("bannermod.surveyor.marks_reset").withStyle(ChatFormatting.YELLOW));
    }

    public static void toggleGuidePreview(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        ValidationSession session = SurveyorSessionCodec.read(stack);
        if (session == null) {
            return;
        }
        ValidationSession updated = session.withGuidePreview(!session.showGuidePreview());
        SurveyorSessionCodec.write(stack, updated);
        player.sendSystemMessage(Component.translatable(
                updated.showGuidePreview()
                        ? "bannermod.surveyor.guide_preview.enabled"
                        : "bannermod.surveyor.guide_preview.disabled")
                .withStyle(updated.showGuidePreview() ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
    }

    public static void suggestDraftZones(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        ValidationSession session = SurveyorSessionCodec.read(stack);
        if (session == null) {
            player.sendSystemMessage(Component.translatable("bannermod.surveyor.no_session").withStyle(ChatFormatting.RED));
            return;
        }
        SurveyorDraftSuggestionService.DraftSuggestionResult result = SurveyorDraftSuggestionService.suggest(player.serverLevel(), session);
        switch (result.status()) {
            case APPLIED -> {
                ItemStackComponentData.update(stack, data -> data.remove(TAG_PENDING_CORNER));
                SurveyorSessionCodec.write(stack, result.session());
                player.sendSystemMessage(Component.translatable("bannermod.surveyor.draft_suggested", result.addedZones()).withStyle(ChatFormatting.AQUA));
            }
            case NO_MATCHES -> player.sendSystemMessage(Component.translatable("bannermod.surveyor.draft_none").withStyle(ChatFormatting.YELLOW));
            case MISSING_ANCHOR -> player.sendSystemMessage(Component.translatable("bannermod.surveyor.draft_need_anchor").withStyle(ChatFormatting.YELLOW));
            case UNSUPPORTED_MODE -> player.sendSystemMessage(Component.translatable("bannermod.surveyor.draft_unsupported").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static boolean hasPendingCorner(ItemStack stack) {
        CompoundTag tag = ItemStackComponentData.read(stack);
        return tag != null && tag.contains(TAG_PENDING_CORNER);
    }

    public static boolean hasZoneForSelectedRole(ItemStack stack) {
        ValidationSession session = SurveyorSessionCodec.read(stack);
        if (session == null) {
            return false;
        }
        ZoneRole role = selectedRole(stack);
        return session.selections().stream().anyMatch(selection -> selection.role() == role);
    }

    public static boolean hasAnyMarks(ItemStack stack) {
        ValidationSession session = SurveyorSessionCodec.read(stack);
        return hasPendingCorner(stack)
                || session != null && (!session.anchorPos().equals(BlockPos.ZERO) || !session.selections().isEmpty());
    }

    private static SurveyorMode nextMode(SurveyorMode mode) {
        SurveyorMode[] modes = SurveyorMode.values();
        int idx = mode.ordinal();
        return modes[(idx + 1) % modes.length];
    }

    public static void setSelectedRole(ItemStack stack, ZoneRole role) {
        if (stack == null || role == null) {
            return;
        }
        ItemStackComponentData.update(stack, tag -> tag.putString(TAG_SELECTED_ROLE, role.name()));
    }

    public static ZoneRole selectedRole(ItemStack stack) {
        ValidationSession session = SurveyorSessionCodec.read(stack);
        SurveyorMode mode = session == null ? SurveyorMode.BOOTSTRAP_FORT : session.mode();
        List<ZoneRole> requiredRoles = SurveyorModeGuidance.requiredRoles(mode);
        CompoundTag tag = ItemStackComponentData.read(stack);
        ZoneRole resolved;
        if (tag == null || !tag.contains(TAG_SELECTED_ROLE)) {
            resolved = defaultRoleForMode(mode);
        } else {
            try {
                resolved = ZoneRole.valueOf(tag.getString(TAG_SELECTED_ROLE));
            } catch (IllegalArgumentException ex) {
                resolved = defaultRoleForMode(mode);
            }
        }

        if (requiredRoles.isEmpty()) {
            return resolved;
        }
        ZoneRole nextMissingRole = SurveyorModeGuidance.nextMissingRole(mode, session);
        if (!requiredRoles.contains(resolved)) {
            return nextMissingRole != null ? nextMissingRole : requiredRoles.get(0);
        }
        if (nextMissingRole == null) {
            return resolved;
        }
        if (resolved == nextMissingRole) {
            return resolved;
        }
        int resolvedIndex = requiredRoles.indexOf(resolved);
        int nextMissingIndex = requiredRoles.indexOf(nextMissingRole);
        if (resolvedIndex > nextMissingIndex || hasRole(session, resolved)) {
            return nextMissingRole;
        }
        return resolved;
    }

    private static void maybeAdvanceRoleAfterCapture(Player player, ItemStack stack, ValidationSession session, ZoneRole capturedRole) {
        if (player == null || stack == null || session == null || capturedRole == null) {
            return;
        }
        List<ZoneRole> requiredRoles = orderedRolesForMode(session.mode());
        if (!requiredRoles.contains(capturedRole)) {
            return;
        }
        for (ZoneRole role : requiredRoles) {
            boolean captured = session.selections().stream().anyMatch(selection -> selection.role() == role);
            if (!captured) {
                setSelectedRole(stack, role);
                player.sendSystemMessage(Component.translatable("bannermod.surveyor.role_switched", roleLabel(role)).withStyle(ChatFormatting.YELLOW));
                return;
            }
        }
    }

    private static void ensureSelectedRole(ItemStack stack, SurveyorMode mode) {
        CompoundTag tag = ItemStackComponentData.read(stack);
        if (tag == null || !tag.contains(TAG_SELECTED_ROLE)) {
            setSelectedRole(stack, defaultRoleForMode(mode));
        }
    }

    private static boolean hasRole(@Nullable ValidationSession session, ZoneRole role) {
        return session != null && session.selections().stream().anyMatch(selection -> selection.role() == role);
    }

    private static ZoneRole defaultRoleForMode(SurveyorMode mode) {
        return SurveyorModeGuidance.defaultRole(mode);
    }

    private static List<ZoneRole> orderedRolesForMode(SurveyorMode mode) {
        return SurveyorModeGuidance.requiredRoles(mode);
    }

    private static Component roleList(List<ZoneRole> roles) {
        Component joined = Component.empty();
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) {
                joined = joined.copy().append(Component.literal(", "));
            }
            joined = joined.copy().append(roleLabel(roles.get(i)));
        }
        return joined;
    }

    @Nullable
    public static BlockPos pendingCorner(ItemStack stack) {
        CompoundTag tag = ItemStackComponentData.read(stack);
        if (tag == null || !tag.contains(TAG_PENDING_CORNER)) {
            return null;
        }
        return BlockPos.of(tag.getLong(TAG_PENDING_CORNER));
    }

    public static Component modeLabel(SurveyorMode mode) {
        return Component.translatable("bannermod.surveyor.mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static Component roleLabel(ZoneRole role) {
        return Component.translatable("bannermod.surveyor.role." + role.name().toLowerCase(java.util.Locale.ROOT));
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreen(InteractionHand hand) {
        SettlementSurveyorScreen.open(hand);
    }
}
