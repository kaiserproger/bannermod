package com.talhanation.bannermod.commands.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdminRecoveryCommands {
    private static final SimpleCommandExceptionType INVALID_UUID = new SimpleCommandExceptionType(
            Component.literal("claimUuid must be a valid UUID")
    );
    private static final SimpleCommandExceptionType SERVER_ONLY = new SimpleCommandExceptionType(
            Component.literal("This command can only run on a server")
    );
    private static final SimpleCommandExceptionType WORKER_NOT_FOUND = new SimpleCommandExceptionType(
            Component.literal("entityId must reference a loaded worker")
    );
    private static final SimpleCommandExceptionType CHUNK_NOT_LOADED = new SimpleCommandExceptionType(
            Component.literal("chunk must be loaded")
    );
    private static final SimpleCommandExceptionType NO_WORKERS_IN_CHUNK = new SimpleCommandExceptionType(
            Component.literal("chunk must contain at least one loaded worker")
    );

    private AdminRecoveryCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> settlement() {
        return Commands.literal("settlement")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("prune")
                        .then(Commands.argument("claimUuid", StringArgumentType.word())
                                .executes(AdminRecoveryCommands::pruneSettlement)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> treasury() {
        return Commands.literal("treasury")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("claimUuid", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(AdminRecoveryCommands::setTreasury))))
                .then(Commands.literal("show")
                        .then(Commands.argument("claimUuid", StringArgumentType.word())
                                .executes(AdminRecoveryCommands::showTreasury)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> claim() {
        return Commands.literal("claim")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("trust")
                        .then(Commands.literal("prune-dead-uuids")
                                .executes(AdminRecoveryCommands::pruneDeadTrustedUuids)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> worker() {
        return Commands.literal("worker")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("unbind")
                        .then(Commands.argument("entityId", IntegerArgumentType.integer(0))
                                .executes(AdminRecoveryCommands::unbindWorker)))
                .then(Commands.literal("rehome")
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(AdminRecoveryCommands::rehomeWorkers))));
    }

    private static int pruneSettlement(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        UUID claimUuid = claimUuid(context);
        boolean removed = BannerModSettlementManager.get(level).removeSnapshot(claimUuid) != null;
        context.getSource().sendSuccess(() -> Component.literal(
                removed ? "Pruned settlement snapshot " + claimUuid : "No settlement snapshot found for " + claimUuid
        ), false);
        return removed ? 1 : 0;
    }

    private static int setTreasury(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        UUID claimUuid = claimUuid(context);
        int amount = IntegerArgumentType.getInteger(context, "amount");
        BannerModTreasuryManager treasury = BannerModTreasuryManager.get(level);
        BannerModTreasuryLedgerSnapshot previous = treasury.getLedger(claimUuid);
        ChunkPos anchor = previous == null ? new ChunkPos(0, 0) : previous.anchorChunk();
        String settlementFactionId = previous == null ? null : previous.settlementFactionId();
        treasury.putLedger(new BannerModTreasuryLedgerSnapshot(
                claimUuid,
                anchor.x,
                anchor.z,
                settlementFactionId,
                amount,
                0,
                amount,
                level.getGameTime(),
                0,
                0L
        ));
        context.getSource().sendSuccess(() -> Component.literal("Treasury " + claimUuid + " set to " + amount), false);
        return 1;
    }

    private static int showTreasury(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        UUID claimUuid = claimUuid(context);
        BannerModTreasuryLedgerSnapshot ledger = BannerModTreasuryManager.get(level).getLedger(claimUuid);
        int amount = ledger == null ? 0 : ledger.treasuryBalance();
        context.getSource().sendSuccess(() -> Component.literal("Treasury " + claimUuid + " balance: " + amount), false);
        return 1;
    }

    private static int pruneDeadTrustedUuids(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        int removed = 0;
        for (RecruitsClaim claim : ClaimEvents.claimManager().getAllClaims()) {
            List<RecruitsPlayerInfo> trustedPlayers = claim.getTrustedPlayers();
            List<RecruitsPlayerInfo> pruned = new ArrayList<>();
            Set<UUID> seen = new LinkedHashSet<>();
            for (RecruitsPlayerInfo trustedPlayer : trustedPlayers) {
                if (trustedPlayer == null || trustedPlayer.getUUID() == null || !seen.add(trustedPlayer.getUUID())) {
                    removed++;
                    continue;
                }
                pruned.add(trustedPlayer);
            }
            if (pruned.size() != trustedPlayers.size()) {
                claim.setTrustedPlayers(pruned);
                ClaimEvents.claimManager().addOrUpdateClaim(level, claim);
            }
        }
        int removedCount = removed;
        context.getSource().sendSuccess(() -> Component.literal("Pruned " + removedCount + " dead trusted UUID entries"), false);
        return removed;
    }

    private static int unbindWorker(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        int entityId = IntegerArgumentType.getInteger(context, "entityId");
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof AbstractWorkerEntity worker)) {
            throw WORKER_NOT_FOUND.create();
        }
        worker.setCurrentWorkArea(null);
        context.getSource().sendSuccess(() -> Component.literal("Unbound worker " + entityId), false);
        return 1;
    }

    private static int rehomeWorkers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        if (!level.hasChunk(chunkX, chunkZ)) {
            throw CHUNK_NOT_LOADED.create();
        }

        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        BlockPos home = new BlockPos(chunk.getMiddleBlockX(), level.getSeaLevel(), chunk.getMiddleBlockZ());
        AABB chunkBounds = new AABB(
                chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1
        );
        List<AbstractWorkerEntity> workers = level.getEntitiesOfClass(AbstractWorkerEntity.class, chunkBounds);
        if (workers.isEmpty()) {
            throw NO_WORKERS_IN_CHUNK.create();
        }
        for (AbstractWorkerEntity worker : workers) {
            worker.setHomePos(home);
            worker.setHomeBuildAreaUUID(null);
        }
        int count = workers.size();
        context.getSource().sendSuccess(() -> Component.literal("Rehomed " + count + " worker(s) to chunk " + chunkX + "," + chunkZ), false);
        return count;
    }

    private static UUID claimUuid(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            return UUID.fromString(StringArgumentType.getString(context, "claimUuid"));
        } catch (IllegalArgumentException exception) {
            throw INVALID_UUID.create();
        }
    }

    private static ServerLevel serverLevel(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (level == null || level.isClientSide()) {
            throw SERVER_ONLY.create();
        }
        return level;
    }
}
