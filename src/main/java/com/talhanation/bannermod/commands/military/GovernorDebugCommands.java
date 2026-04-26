package com.talhanation.bannermod.commands.military;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorService;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

final class GovernorDebugCommands {
    private GovernorDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("governorDebug")
                .then(Commands.literal("summon")
                        .executes(ctx -> summonGovernor(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> printInfo(ctx.getSource())));
    }

    private static int summonGovernor(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        AbstractRecruitEntity recruit = ModEntityTypes.RECRUIT.get().create(level);
        if (recruit == null) {
            source.sendFailure(Component.literal("Failed to create recruit entity."));
            return 0;
        }

        Vec3 pos = player.position();
        recruit.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0f);
        recruit.setCustomName(Component.literal("Debug Governor"));
        recruit.setPersistenceRequired();
        recruit.setHunger(50);
        recruit.setMoral(50);
        recruit.setListen(true);
        recruit.setXpLevel(7);
        recruit.setOwnerUUID(Optional.of(player.getUUID()));

        level.addFreshEntityWithPassengers(recruit);

        // Try to auto-assign as governor to the claim at the player's location
        RecruitsClaim claim = resolveClaim(level, pos);
        if (claim != null) {
            BannerModGovernorService service = new BannerModGovernorService(BannerModGovernorManager.get(level));
            BannerModGovernorService.OperationResult result = service.assignGovernor(claim, player, recruit);
            if (result.allowed()) {
                source.sendSuccess(() -> Component.literal(
                        "Debug Governor spawned and assigned to claim \"" + claim.getName() + "\". Right-click to open."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "Debug Governor spawned (XP=7) but not auto-assigned: " + result.governorDecision().name().toLowerCase() +
                        ". Right-click → Promote → Governor to assign manually."), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Debug Governor spawned (XP=7). No claim here — right-click → Promote → Governor to assign."), false);
        }

        return 1;
    }

    private static int printInfo(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BannerModGovernorManager manager = BannerModGovernorManager.get(level);
        int count = manager.getAllSnapshots().size();
        source.sendSuccess(() -> Component.literal("Active governor snapshots: " + count), false);

        for (BannerModGovernorSnapshot snap : manager.getAllSnapshots()) {
            source.sendSuccess(() -> Component.literal(
                    "  claim=" + snap.claimUuid() + " governor=" + snap.governorRecruitUuid() +
                    " incidents=" + snap.incidentTokens().size() + " autoManage=" + snap.autoManage()), false);
        }
        return 1;
    }

    private static RecruitsClaim resolveClaim(ServerLevel level, Vec3 pos) {
        if (ClaimEvents.recruitsClaimManager == null) return null;
        ChunkPos chunk = new ChunkPos(BlockPos.containing(pos));
        return ClaimEvents.recruitsClaimManager.getClaim(chunk);
    }
}
