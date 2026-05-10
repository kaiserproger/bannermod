package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NpcDemandContractService {
    private static final long ISOLATED_INTERVAL_TICKS = 24_000L;
    private static final long MARKET_INTERVAL_TICKS = 12_000L;
    private static final long TRADING_POST_INTERVAL_TICKS = 9_000L;
    private static final long CONTRACT_DEADLINE_TICKS = 72_000L;
    private static final int MAX_OPEN_CONTRACTS_PER_CLAIM = 3;
    private static final List<ContractTemplate> TEMPLATES = List.of(
            new ContractTemplate("frontier_quartermaster", "food", List.of("minecraft:wheat", "minecraft:bread"), 48, 96),
            new ContractTemplate("mason_guild", "stone", List.of("minecraft:cobblestone", "minecraft:stone"), 64, 112),
            new ContractTemplate("carpenter_guild", "wood", List.of("minecraft:oak_log", "minecraft:spruce_log"), 64, 108),
            new ContractTemplate("smith_factor", "iron", List.of("minecraft:iron_ingot"), 24, 160)
    );

    private final Map<UUID, List<NpcDemandContract>> contractsByClaim = new HashMap<>();
    private final Map<UUID, Long> nextEligibleTickByClaim = new HashMap<>();
    private final Runnable dirtyCallback;

    public NpcDemandContractService() {
        this(null);
    }

    NpcDemandContractService(Runnable dirtyCallback) {
        this.dirtyCallback = dirtyCallback == null ? () -> { } : dirtyCallback;
    }

    public void tickClaim(SettlementSnapshot snapshot, long gameTime) {
        if (snapshot == null || snapshot.claimUuid() == null) {
            return;
        }
        UUID claimUuid = snapshot.claimUuid();
        expireContracts(claimUuid, gameTime);
        if (openContracts(claimUuid).size() >= MAX_OPEN_CONTRACTS_PER_CLAIM) {
            scheduleNext(claimUuid, snapshot, gameTime);
            return;
        }
        long nextEligibleTick = this.nextEligibleTickByClaim.getOrDefault(claimUuid, Long.MIN_VALUE);
        if (gameTime < nextEligibleTick) {
            return;
        }
        addContract(claimUuid, createContract(snapshot, gameTime));
        scheduleNext(claimUuid, snapshot, gameTime);
    }

    public List<NpcDemandContract> contracts(UUID claimUuid) {
        if (claimUuid == null) {
            return List.of();
        }
        return this.contractsByClaim.getOrDefault(claimUuid, List.of()).stream()
                .sorted(Comparator.comparingLong(NpcDemandContract::createdAtGameTime))
                .toList();
    }

    public List<String> statusLines(UUID claimUuid) {
        List<NpcDemandContract> contracts = contracts(claimUuid);
        if (contracts.isEmpty()) {
            return List.of("No NPC demand contracts for claim " + claimUuid);
        }
        List<String> lines = new ArrayList<>();
        lines.add("NPC demand contracts for claim " + claimUuid + ": " + contracts.size());
        for (NpcDemandContract contract : contracts) {
            lines.add(contract.debugLine());
        }
        return lines;
    }

    private void expireContracts(UUID claimUuid, long gameTime) {
        List<NpcDemandContract> contracts = this.contractsByClaim.get(claimUuid);
        if (contracts == null || contracts.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < contracts.size(); i++) {
            NpcDemandContract current = contracts.get(i);
            NpcDemandContract expired = current.expired(gameTime);
            if (expired != current) {
                contracts.set(i, expired);
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    private List<NpcDemandContract> openContracts(UUID claimUuid) {
        return this.contractsByClaim.getOrDefault(claimUuid, List.of()).stream()
                .filter(contract -> contract.status() == NpcDemandContract.Status.OPEN)
                .toList();
    }

    private void addContract(UUID claimUuid, NpcDemandContract contract) {
        this.contractsByClaim.computeIfAbsent(claimUuid, ignored -> new ArrayList<>()).add(contract);
        markDirty();
    }

    private void scheduleNext(UUID claimUuid, SettlementSnapshot snapshot, long gameTime) {
        this.nextEligibleTickByClaim.put(claimUuid, gameTime + contractInterval(snapshot));
        markDirty();
    }

    void loadFromTag(CompoundTag tag) {
        this.contractsByClaim.clear();
        this.nextEligibleTickByClaim.clear();
        if (tag.contains("Contracts", Tag.TAG_LIST)) {
            ListTag contracts = tag.getList("Contracts", Tag.TAG_COMPOUND);
            for (Tag entry : contracts) {
                NpcDemandContract contract = NpcDemandContract.fromTag((CompoundTag) entry);
                this.contractsByClaim.computeIfAbsent(contract.claimUuid(), ignored -> new ArrayList<>()).add(contract);
            }
        }
        if (tag.contains("NextEligibleTicks", Tag.TAG_LIST)) {
            ListTag ticks = tag.getList("NextEligibleTicks", Tag.TAG_COMPOUND);
            for (Tag entry : ticks) {
                CompoundTag tickTag = (CompoundTag) entry;
                if (tickTag.hasUUID("ClaimUuid")) {
                    this.nextEligibleTickByClaim.put(tickTag.getUUID("ClaimUuid"), tickTag.getLong("GameTime"));
                }
            }
        }
    }

    void saveToTag(CompoundTag tag) {
        ListTag contracts = new ListTag();
        for (List<NpcDemandContract> claimContracts : this.contractsByClaim.values()) {
            for (NpcDemandContract contract : claimContracts) {
                contracts.add(contract.toTag());
            }
        }
        tag.put("Contracts", contracts);

        ListTag ticks = new ListTag();
        this.nextEligibleTickByClaim.forEach((claimUuid, gameTime) -> {
            CompoundTag tickTag = new CompoundTag();
            tickTag.putUUID("ClaimUuid", claimUuid);
            tickTag.putLong("GameTime", gameTime);
            ticks.add(tickTag);
        });
        tag.put("NextEligibleTicks", ticks);
    }

    private void markDirty() {
        this.dirtyCallback.run();
    }

    private NpcDemandContract createContract(SettlementSnapshot snapshot, long gameTime) {
        int index = Math.floorMod(snapshot.claimUuid().hashCode() + (int) (gameTime / 20L), TEMPLATES.size());
        ContractTemplate template = TEMPLATES.get(index);
        int qualityBonus = qualityBonus(snapshot);
        int amount = template.baseAmount() + qualityBonus * 8;
        int reward = template.baseReward() + qualityBonus * 32;
        return new NpcDemandContract(
                deterministicContractUuid(snapshot.claimUuid(), gameTime),
                snapshot.claimUuid(),
                template.buyer(),
                template.resourceBucket(),
                template.requestedItems(),
                amount,
                gameTime,
                gameTime + CONTRACT_DEADLINE_TICKS,
                reward,
                NpcDemandContract.Status.OPEN
        );
    }

    private long contractInterval(SettlementSnapshot snapshot) {
        if (hasTradingPostAccess(snapshot)) {
            return TRADING_POST_INTERVAL_TICKS;
        }
        if (hasMarketAccess(snapshot)) {
            return MARKET_INTERVAL_TICKS;
        }
        return ISOLATED_INTERVAL_TICKS;
    }

    private int qualityBonus(SettlementSnapshot snapshot) {
        int bonus = 0;
        if (hasMarketAccess(snapshot)) {
            bonus++;
        }
        if (hasTradingPostAccess(snapshot)) {
            bonus += 2;
        }
        return bonus;
    }

    private boolean hasMarketAccess(SettlementSnapshot snapshot) {
        return snapshot.marketState().openMarketCount() > 0 || snapshot.marketState().readySellerDispatchCount() > 0;
    }

    private boolean hasTradingPostAccess(SettlementSnapshot snapshot) {
        return snapshot.tradeRouteHandoffSnapshot().portEntrypointCount() > 0
                || snapshot.tradeRouteHandoffSnapshot().routedStorageCount() > 0
                || snapshot.tradeRouteHandoffSnapshot().readySellerDispatchCount() > 0;
    }

    private UUID deterministicContractUuid(UUID claimUuid, long gameTime) {
        return UUID.nameUUIDFromBytes((claimUuid + ":npc_contract:" + gameTime).getBytes(StandardCharsets.UTF_8));
    }

    private record ContractTemplate(String buyer,
                                    String resourceBucket,
                                    List<String> requestedItems,
                                    int baseAmount,
                                    int baseReward) {
    }
}
