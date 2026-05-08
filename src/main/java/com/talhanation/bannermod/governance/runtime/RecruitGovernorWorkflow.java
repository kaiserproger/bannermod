package com.talhanation.bannermod.governance.runtime;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModGovernorAuthority;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.governance.BannerModGovernorService;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.inventory.military.GovernorContainer;
import com.talhanation.bannermod.network.messages.military.MessageOpenGovernorScreen;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateGovernorScreen;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Envelope;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.Payload;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementClientSnapshotContract.RefreshTrigger;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import com.talhanation.bannermod.settlement.BannerModSettlementService;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import com.talhanation.bannermod.network.compat.BannerModNetworkHooks;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;
import org.jetbrains.annotations.NotNull;

public final class RecruitGovernorWorkflow {

    private RecruitGovernorWorkflow() {
    }

    public static boolean tryPromoteRecruit(AbstractRecruitEntity recruit, String name, ServerPlayer player) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) {
            return true;
        }
        if (recruit.getXpLevel() < 7 || recruit.getOwnerUUID() == null) {
            player.sendSystemMessage(Component.literal("Governor designation denied: recruit is not eligible"));
            return true;
        }
        if (name != null && !name.isBlank()) {
            recruit.setCustomName(Component.literal(name));
        }

        BannerModGovernorService.OperationResult result = governorService(serverLevel)
                .assignGovernor(resolveClaim(recruit), player, recruit);
        if (result.allowed()) {
            RecruitsClaim claim = resolveClaim(recruit);
            if (claim != null) {
                BannerModSettlementService.refreshClaim(
                        serverLevel,
                        ClaimEvents.claimManager(),
                        BannerModSettlementManager.get(serverLevel),
                        BannerModGovernorManager.get(serverLevel),
                        claim
                );
            }
            player.sendSystemMessage(Component.literal(recruit.getName().getString() + " designated as governor"));
            openGovernorScreen(player, recruit);
        } else {
            player.sendSystemMessage(Component.literal("Governor designation denied: " + result.governorDecision().name().toLowerCase()));
        }
        return true;
    }

    public static void openGovernorScreen(Player player, AbstractRecruitEntity recruit) {
        if (player instanceof ServerPlayer serverPlayer) {
            BannerModNetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.literal("Governor");
                }

                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new GovernorContainer(i, playerEntity, recruit);
                }
            }, packetBuffer -> packetBuffer.writeUUID(recruit.getUUID()));
            syncGovernorScreen(serverPlayer, recruit);
        } else {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageOpenGovernorScreen(recruit.getUUID(), true));
        }
    }

    public static void syncGovernorScreen(ServerPlayer player, AbstractRecruitEntity recruit) {
        RecruitsClaim claim = resolveClaim(recruit);
        long gameTime = recruit.getCommandSenderWorld().getGameTime();
        Envelope envelope = Envelope.empty(0L, gameTime, RefreshTrigger.SCREEN_OPEN);
        if (claim != null && recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
            BannerModGovernorSnapshot governorSnapshot = governorService(serverLevel).getOrCreateGovernorSnapshot(claim);
            BannerModSettlementSnapshot settlementSnapshot = BannerModSettlementManager.get(serverLevel).getSnapshot(claim.getUUID());
            envelope = buildEnvelope(claim, settlementSnapshot, governorSnapshot, gameTime, RefreshTrigger.SCREEN_OPEN);
        }

        BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(() -> player),
                new MessageToClientUpdateGovernorScreen(recruit.getUUID(), envelope));
    }

    public static void syncGovernorSnapshotsOnLogin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BannerModGovernorManager governorManager = BannerModGovernorManager.get(level);
        BannerModSettlementManager settlementManager = BannerModSettlementManager.get(level);
        long gameTime = level.getGameTime();
        for (BannerModGovernorSnapshot governorSnapshot : governorManager.getAllSnapshots()) {
            if (!player.getUUID().equals(governorSnapshot.governorOwnerUuid()) || governorSnapshot.governorRecruitUuid() == null) {
                continue;
            }
            BannerModSettlementSnapshot settlementSnapshot = settlementManager.getSnapshot(governorSnapshot.claimUuid());
            Envelope envelope = buildEnvelope(governorSnapshot.claimUuid(), settlementSnapshot, governorSnapshot,
                    gameTime, RefreshTrigger.LOGIN);
            sendGovernorUpdate(player, governorSnapshot.governorRecruitUuid(), envelope);
        }
    }

    public static void syncGovernorMutationRefresh(ServerLevel level, RecruitsClaim claim) {
        BannerModGovernorSnapshot governorSnapshot = BannerModGovernorManager.get(level).getSnapshot(claim.getUUID());
        if (governorSnapshot == null || governorSnapshot.governorRecruitUuid() == null || governorSnapshot.governorOwnerUuid() == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(governorSnapshot.governorOwnerUuid());
        if (player == null) {
            return;
        }
        BannerModSettlementSnapshot settlementSnapshot = BannerModSettlementManager.get(level).getSnapshot(claim.getUUID());
        Envelope envelope = buildEnvelope(claim, settlementSnapshot, governorSnapshot, level.getGameTime(), RefreshTrigger.MUTATION_REFRESH);
        sendGovernorUpdate(player, governorSnapshot.governorRecruitUuid(), envelope);
    }

    public static Envelope buildEnvelope(RecruitsClaim claim,
                                  BannerModSettlementSnapshot settlementSnapshot,
                                  BannerModGovernorSnapshot governorSnapshot,
                                  long gameTime,
                                  RefreshTrigger trigger) {
        return buildEnvelope(claim.getUUID(), settlementSnapshot, governorSnapshot, gameTime, trigger);
    }

    public static Envelope buildEnvelope(java.util.UUID claimUuid,
                                  BannerModSettlementSnapshot settlementSnapshot,
                                  BannerModGovernorSnapshot governorSnapshot,
                                  long gameTime,
                                  RefreshTrigger trigger) {
        return Envelope.ready(gameTime, gameTime, trigger,
                new Payload(claimUuid, settlementSnapshot, governorSnapshot));
    }

    private static void sendGovernorUpdate(ServerPlayer player, java.util.UUID recruitUuid, Envelope envelope) {
        BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(() -> player),
                new MessageToClientUpdateGovernorScreen(recruitUuid, envelope));
    }

    public static void updateGovernorPolicy(ServerPlayer player, AbstractRecruitEntity recruit, BannerModGovernorPolicy policy, int value) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) {
            return;
        }
        BannerModGovernorService.OperationResult result = governorService(serverLevel)
                .updatePolicy(resolveClaim(recruit), BannerModGovernorAuthority.actor(player), policy, value);
        if (!result.allowed()) {
            player.sendSystemMessage(Component.literal("Governor policy update denied: " + result.governorDecision().name().toLowerCase()));
            return;
        }
        syncGovernorScreen(player, recruit);
    }

    private static BannerModGovernorService governorService(ServerLevel level) {
        return new BannerModGovernorService(BannerModGovernorManager.get(level));
    }

    private static RecruitsClaim resolveClaim(AbstractRecruitEntity recruit) {
        return ClaimEvents.claimManager() == null
                ? null
                : ClaimEvents.claimManager().getClaim(new ChunkPos(recruit.blockPosition()));
    }

}
