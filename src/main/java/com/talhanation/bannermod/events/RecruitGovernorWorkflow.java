package com.talhanation.bannermod.events;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.governance.BannerModContractManager;
import com.talhanation.bannermod.governance.BannerModGovernorAuthority;
import com.talhanation.bannermod.governance.BannerModGovernorContract;
import com.talhanation.bannermod.governance.BannerModGovernorContractStatus;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorPolicy;
import com.talhanation.bannermod.governance.BannerModGovernorRecommendation;
import com.talhanation.bannermod.governance.BannerModGovernorService;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.inventory.military.GovernorContainer;
import com.talhanation.bannermod.network.messages.military.MessageOpenGovernorScreen;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateContractBoard;
import com.talhanation.bannermod.network.messages.military.MessageToClientUpdateGovernorScreen;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
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
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

final class RecruitGovernorWorkflow {

    private RecruitGovernorWorkflow() {
    }

    static boolean tryPromoteRecruit(AbstractRecruitEntity recruit, String name, ServerPlayer player) {
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
                        ClaimEvents.recruitsClaimManager,
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

    static void openGovernorScreen(Player player, AbstractRecruitEntity recruit) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
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

    static void syncGovernorScreen(ServerPlayer player, AbstractRecruitEntity recruit) {
        RecruitsClaim claim = resolveClaim(recruit);
        BannerModGovernorSnapshot snapshot = claim == null
                ? null
                : governorService((ServerLevel) recruit.getCommandSenderWorld()).getOrCreateGovernorSnapshot(claim);
        BannerModSettlementBinding.Binding binding = claim == null
                ? BannerModSettlementBinding.resolveSettlementStatus(ClaimEvents.recruitsClaimManager, recruit.blockPosition(), recruit.getTeam() == null ? null : recruit.getTeam().getName())
                : BannerModSettlementBinding.resolveSettlementStatus(claim, claim.getCenter() == null ? new ChunkPos(recruit.blockPosition()) : claim.getCenter(), claim.getOwnerPoliticalEntityId() == null ? null : claim.getOwnerPoliticalEntityId().toString());
        java.util.List<String> recommendations = snapshot == null ? java.util.List.of() : new java.util.ArrayList<>(snapshot.recommendationTokens());
        if (claim != null && recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
            BannerModSettlementSnapshot settlementSnapshot = BannerModSettlementManager.get(serverLevel).getSnapshot(claim.getUUID());
            if (settlementSnapshot != null) {
                recommendations.addAll(settlementSnapshot.tradeRouteHandoffSeed().seaTradeStatusLines());
            }
        }

        BannerModMain.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MessageToClientUpdateGovernorScreen(
                recruit.getUUID(),
                binding.status().name().toLowerCase(),
                snapshot == null ? 0 : snapshot.citizenCount(),
                snapshot == null ? 0 : snapshot.taxesDue(),
                snapshot == null ? 0 : snapshot.taxesCollected(),
                snapshot == null ? 0L : snapshot.lastHeartbeatTick(),
                recommendationLabel(snapshot, true),
                recommendationLabel(snapshot, false),
                snapshot == null ? BannerModGovernorPolicy.DEFAULT_VALUE : snapshot.garrisonPriority(),
                snapshot == null ? BannerModGovernorPolicy.DEFAULT_VALUE : snapshot.fortificationPriority(),
                snapshot == null ? BannerModGovernorPolicy.DEFAULT_VALUE : snapshot.taxPressure(),
                snapshot == null ? 0 : snapshot.treasuryBalance(),
                snapshot == null ? 0 : snapshot.lastTreasuryNet(),
                snapshot == null ? 0 : snapshot.projectedTreasuryBalance(),
                snapshot != null && snapshot.autoManage(),
                snapshot == null ? java.util.List.of() : snapshot.incidentTokens(),
                recommendations
        ));
    }

    static void updateGovernorPolicy(ServerPlayer player, AbstractRecruitEntity recruit, BannerModGovernorPolicy policy, int value) {
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

    static void updateGovernorAutoManage(ServerPlayer player, AbstractRecruitEntity recruit, boolean autoManage) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) {
            return;
        }
        BannerModGovernorService.OperationResult result = governorService(serverLevel)
                .updateAutoManage(resolveClaim(recruit), BannerModGovernorAuthority.actor(player), autoManage);
        if (!result.allowed()) {
            player.sendSystemMessage(Component.literal("Governor auto-manage update denied: " + result.governorDecision().name().toLowerCase()));
            return;
        }
        syncGovernorScreen(player, recruit);
    }

    private static BannerModGovernorService governorService(ServerLevel level) {
        return new BannerModGovernorService(BannerModGovernorManager.get(level));
    }

    private static RecruitsClaim resolveClaim(AbstractRecruitEntity recruit) {
        return ClaimEvents.recruitsClaimManager == null
                ? null
                : ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(recruit.blockPosition()));
    }

    static void openContractBoard(ServerPlayer player, AbstractRecruitEntity recruit) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return;
        RecruitsClaim claim = resolveClaim(recruit);
        if (claim == null) {
            player.sendSystemMessage(Component.literal("No claim found at governor's position."));
            return;
        }
        BannerModGovernorSnapshot snapshot = governorService(serverLevel).getOrCreateGovernorSnapshot(claim);
        boolean isOwner = player.getUUID().equals(snapshot.governorOwnerUuid());
        BannerModContractManager contractManager = BannerModContractManager.get(serverLevel);
        java.util.List<MessageToClientUpdateContractBoard.ContractDto> dtos = contractManager
                .getContractsForClaim(claim.getUUID()).stream()
                .map(c -> new MessageToClientUpdateContractBoard.ContractDto(
                        c.contractId(), c.type().token(), c.calculatedReward(),
                        c.status().name().toLowerCase(), c.deadlineTick(), c.pinned(),
                        c.acceptedByUuid() != null))
                .collect(java.util.stream.Collectors.toList());
        BannerModMain.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MessageToClientUpdateContractBoard(recruit.getUUID(), claim.getUUID(),
                        isOwner, snapshot.maxContractReward(), serverLevel.getGameTime(), dtos));
    }

    static void acceptContract(ServerPlayer player, AbstractRecruitEntity recruit, java.util.UUID contractId) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return;
        RecruitsClaim claim = resolveClaim(recruit);
        if (claim == null) return;
        BannerModContractManager contractManager = BannerModContractManager.get(serverLevel);
        BannerModGovernorContract contract = contractManager.getContract(contractId);
        if (contract == null || !contract.isOpen()) {
            player.sendSystemMessage(Component.literal("Contract is no longer available."));
            return;
        }
        contractManager.putContract(contract.withAcceptedBy(player.getUUID()));
        player.sendSystemMessage(Component.literal("Contract accepted. Complete the task and return to claim completion."));
        openContractBoard(player, recruit);
    }

    static void cancelContract(ServerPlayer player, AbstractRecruitEntity recruit, java.util.UUID contractId) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return;
        RecruitsClaim claim = resolveClaim(recruit);
        if (claim == null) return;
        BannerModGovernorSnapshot snapshot = governorService(serverLevel).getOrCreateGovernorSnapshot(claim);
        if (!player.getUUID().equals(snapshot.governorOwnerUuid()) && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can cancel contracts."));
            return;
        }
        BannerModContractManager contractManager = BannerModContractManager.get(serverLevel);
        BannerModGovernorContract contract = contractManager.getContract(contractId);
        if (contract != null) {
            contractManager.putContract(contract.withStatus(BannerModGovernorContractStatus.CANCELLED));
        }
        openContractBoard(player, recruit);
    }

    static void pinContract(ServerPlayer player, AbstractRecruitEntity recruit, java.util.UUID contractId, boolean pinned) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return;
        RecruitsClaim claim = resolveClaim(recruit);
        if (claim == null) return;
        BannerModGovernorSnapshot snapshot = governorService(serverLevel).getOrCreateGovernorSnapshot(claim);
        if (!player.getUUID().equals(snapshot.governorOwnerUuid()) && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("Only the claim owner can pin contracts."));
            return;
        }
        BannerModContractManager contractManager = BannerModContractManager.get(serverLevel);
        BannerModGovernorContract contract = contractManager.getContract(contractId);
        if (contract != null) contractManager.putContract(contract.withPinned(pinned));
        openContractBoard(player, recruit);
    }

    static void setContractMaxReward(ServerPlayer player, AbstractRecruitEntity recruit, int maxReward) {
        if (!(recruit.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return;
        BannerModGovernorService.OperationResult result = governorService(serverLevel)
                .updateMaxContractReward(resolveClaim(recruit), BannerModGovernorAuthority.actor(player), maxReward);
        if (!result.allowed()) {
            player.sendSystemMessage(Component.literal("Max reward update denied."));
            return;
        }
        openContractBoard(player, recruit);
    }

    private static String recommendationLabel(BannerModGovernorSnapshot snapshot, boolean garrison) {
        if (snapshot == null) {
            return BannerModGovernorRecommendation.HOLD_COURSE.token();
        }
        for (String token : snapshot.recommendationTokens()) {
            if (garrison && BannerModGovernorRecommendation.INCREASE_GARRISON.token().equals(token)) {
                return token;
            }
            if (!garrison && BannerModGovernorRecommendation.STRENGTHEN_FORTIFICATIONS.token().equals(token)) {
                return token;
            }
        }
        return BannerModGovernorRecommendation.HOLD_COURSE.token();
    }
}
