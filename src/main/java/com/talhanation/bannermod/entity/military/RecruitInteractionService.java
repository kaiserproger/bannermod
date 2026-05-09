package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.events.CommandEvents;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.inventory.military.DebugInvMenu;
import com.talhanation.bannermod.inventory.military.RecruitHireMenu;
import com.talhanation.bannermod.inventory.military.RecruitInventoryMenu;
import com.talhanation.bannermod.network.messages.military.MessageDebugScreen;
import com.talhanation.bannermod.network.messages.military.MessageHireGui;
import com.talhanation.bannermod.network.messages.military.MessageRecruitGui;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateHireState;
import com.talhanation.bannermod.registry.military.ModItems;
import net.minecraft.core.BlockPos;
import com.talhanation.bannermod.util.BannerModNpcNamePool;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.scores.Team;
import com.talhanation.bannermod.network.compat.BannerModNetworkHooks;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;
import org.jetbrains.annotations.NotNull;

final class RecruitInteractionService {
    private RecruitInteractionService() {
    }

    static InteractionResult mobInteract(AbstractRecruitEntity recruit, @NotNull Player player, @NotNull InteractionHand hand) {
        String name = recruit.getName().getString();
        boolean isPlayerTarget = recruit.getTarget() != null && recruit.getTarget().equals(player);
        if (isPlayerTarget) return InteractionResult.PASS;

        if (recruit.getCommandSenderWorld().isClientSide) {
            boolean flag = recruit.isOwnedBy(player) || !recruit.canBeHired();
            return flag ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        if (player.isCreative() && player.getItemInHand(hand).getItem().equals(ModItems.RECRUIT_SPAWN_EGG.get())) {
            openDebugScreen(recruit, player);
            return InteractionResult.SUCCESS;
        }
        if (recruit instanceof VillagerNobleEntity noble && !noble.isTrading) {
            noble.openTradeGUI(player);
            return InteractionResult.SUCCESS;
        }
        if (recruit.isOwned() && player.getUUID().equals(recruit.getOwnerUUID())) {
            if (player.isCrouching()) {
                openGUI(recruit, player);
                recruit.stopNavigation();
                return InteractionResult.SUCCESS;
            }

            recruit.setUpkeepTimer(recruit.getUpkeepCooldown());
            if (recruit.getShouldMount()) recruit.setShouldMount(false);
            switch (recruit.getFollowState()) {
                default -> {
                    recruit.setFollowState(1);
                    player.sendSystemMessage(recruit.textFollow(name));
                }
                case 1 -> {
                    recruit.setFollowState(4);
                    player.sendSystemMessage(recruit.textHoldYourPos(name));
                }
                case 3 -> {
                    recruit.setFollowState(0);
                    player.sendSystemMessage(recruit.textWander(name));
                }
            }
            if (recruit instanceof AbstractLeaderEntity) CommandEvents.checkPatrolLeaderState(recruit);
            return InteractionResult.SUCCESS;
        }

        if (!recruit.isOwned() && recruit.canBeHired()) {
            openHireGUI(recruit, player);
            recruit.dialogue(name, player);
            recruit.stopNavigation();
            return InteractionResult.SUCCESS;
        }

        return recruit.superMobInteract(player, hand);
    }

    static void openGUI(AbstractRecruitEntity recruit, Player player) {
        BannerModNpcNamePool.ensureNamed(recruit);
        if (player instanceof ServerPlayer serverPlayer) {
            BannerModNetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return recruit.getName();
                }

                @Override
                public @NotNull AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new RecruitInventoryMenu(i, recruit, playerInventory);
                }
            }, packetBuffer -> packetBuffer.writeUUID(recruit.getUUID()));
        } else {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRecruitGui(player, recruit.getUUID()));
        }
    }

    static void openDebugScreen(AbstractRecruitEntity recruit, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            BannerModNetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return recruit.getName();
                }

                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new DebugInvMenu(i, recruit, playerInventory);
                }
            }, packetBuffer -> packetBuffer.writeUUID(recruit.getUUID()));
        } else {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageDebugScreen(player, recruit.getUUID()));
        }
    }

    static void openHireGUI(AbstractRecruitEntity recruit, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            recruit.stopNavigation();
            Team ownerTeam = player.getTeam();
            String stringId = ownerTeam != null ? ownerTeam.getName() : "";
            boolean canHire = RecruitEvents.playerUnitManager().canPlayerRecruit(stringId, player.getUUID());
            BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(() -> serverPlayer), new MessageToClientUpdateHireState(canHire));
            BannerModNetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return recruit.getName();
                }

                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new RecruitHireMenu(i, playerInventory.player, recruit, playerInventory);
                }
            }, packetBuffer -> packetBuffer.writeUUID(recruit.getUUID()));
        } else {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageHireGui(player, recruit.getUUID()));
        }
    }
}
