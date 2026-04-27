package com.talhanation.bannermod.governance;

import com.talhanation.bannermod.config.BannerModGovernorContractConfig;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BannerModContractPolicy {

    private BannerModContractPolicy() {
    }

    public static int calculateReward(BannerModGovernorContractType type, int ownerMaxReward, boolean urgent) {
        int serverCap = BannerModGovernorContractConfig.DEFAULT_MAX_REWARD.get();
        int effectiveCap = ownerMaxReward > 0 ? Math.min(ownerMaxReward, serverCap) : serverCap;
        int base = switch (type) {
            case SUPPLY_RESOURCES -> BannerModGovernorContractConfig.SUPPLY_RESOURCES_BASE_REWARD.get();
            case CLEAR_HOSTILES -> BannerModGovernorContractConfig.CLEAR_HOSTILES_BASE_REWARD.get();
            case RECRUIT_WORKERS -> BannerModGovernorContractConfig.RECRUIT_WORKERS_BASE_REWARD.get();
        };
        int raw = urgent ? (int) (base * 1.5) : base;
        return Math.min(raw, effectiveCap);
    }

    /**
     * Auto-posts contracts based on active incidents in the heartbeat report.
     * Skips posting if the claim already has an open contract of that type or is at the cap.
     * Called from the Governor heartbeat batch after each snapshot is updated.
     */
    public static void autoPost(BannerModGovernorSnapshot snapshot,
                                BannerModGovernorHeartbeat.HeartbeatReport report,
                                BannerModContractManager contractManager,
                                long gameTime) {
        UUID claimUuid = snapshot.claimUuid();
        int maxOpen = BannerModGovernorContractConfig.MAX_OPEN_CONTRACTS_PER_CLAIM.get();

        List<BannerModGovernorContract> open = contractManager.getOpenContractsForClaim(claimUuid);
        if (open.size() >= maxOpen) return;

        Set<BannerModGovernorContractType> existingTypes = new HashSet<>();
        for (BannerModGovernorContract c : open) {
            existingTypes.add(c.type());
        }

        int maxReward = snapshot.maxContractReward();
        long deadline = gameTime + BannerModGovernorContractConfig.CONTRACT_DEADLINE_TICKS.get();

        for (BannerModGovernorIncident incident : report.incidents()) {
            BannerModGovernorContractType type = incidentToContractType(incident);
            if (type == null || existingTypes.contains(type)) continue;
            if (open.size() + existingTypes.size() >= maxOpen) break;

            boolean urgent = incident == BannerModGovernorIncident.UNDER_SIEGE
                    || incident == BannerModGovernorIncident.HOSTILE_CLAIM;

            BannerModGovernorContract contract = new BannerModGovernorContract(
                    UUID.randomUUID(),
                    claimUuid,
                    type,
                    calculateReward(type, maxReward, urgent),
                    maxReward,
                    null,
                    gameTime,
                    deadline,
                    false,
                    BannerModGovernorContractStatus.OPEN
            );
            contractManager.putContract(contract);
            existingTypes.add(type);
        }
    }

    @Nullable
    private static BannerModGovernorContractType incidentToContractType(BannerModGovernorIncident incident) {
        return switch (incident) {
            case SUPPLY_BLOCKED, WORKER_SHORTAGE -> BannerModGovernorContractType.SUPPLY_RESOURCES;
            case HOSTILE_CLAIM, UNDER_SIEGE -> BannerModGovernorContractType.CLEAR_HOSTILES;
            case RECRUIT_UPKEEP_BLOCKED -> BannerModGovernorContractType.RECRUIT_WORKERS;
            default -> null;
        };
    }

    /** Returns a player-readable description of what a contract requires. */
    public static String contractDescription(BannerModGovernorContractType type) {
        return switch (type) {
            case SUPPLY_RESOURCES -> "Deliver needed resources to the settlement's storage.";
            case CLEAR_HOSTILES -> "Clear hostile mobs and enemy NPCs from within the claim.";
            case RECRUIT_WORKERS -> "Recruit and assign workers to the settlement's buildings.";
        };
    }

    public static String deadlineLabel(long deadlineTick, long currentTick, boolean pinned) {
        if (pinned) return "no expiry (pinned)";
        long remaining = deadlineTick - currentTick;
        if (remaining <= 0) return "expired";
        long days = remaining / 24000L;
        long hours = (remaining % 24000L) / 1000L;
        return days > 0 ? days + "d " + hours + "h" : hours + "h";
    }
}
