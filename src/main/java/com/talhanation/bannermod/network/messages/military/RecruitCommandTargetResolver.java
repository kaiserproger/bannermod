package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import net.minecraft.server.level.ServerPlayer;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class RecruitCommandTargetResolver {
    private RecruitCommandTargetResolver() {
    }

    static List<AbstractRecruitEntity> resolveGroupTargets(Player sender, UUID playerUuid, UUID group, String commandName) {
        if (sender == null || playerUuid == null || !sender.getUUID().equals(playerUuid)) {
            BannerModMain.LOGGER.debug("Ignored {} command with mismatched sender UUID", commandName);
            return List.of();
        }

        List<AbstractRecruitEntity> nearby = RecruitIndex.instance().groupInRange(
                sender.getCommandSenderWorld(),
                group,
                sender.position(),
                CommandTargeting.GROUP_COMMAND_RADIUS
        );
        if (nearby == null) {
            RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
            nearby = sender.getCommandSenderWorld().getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    sender.getBoundingBox().inflate(CommandTargeting.GROUP_COMMAND_RADIUS)
            );
        }
        else if (nearby.isEmpty()) {
            List<AbstractRecruitEntity> scanned = sender.getCommandSenderWorld().getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    sender.getBoundingBox().inflate(CommandTargeting.GROUP_COMMAND_RADIUS)
            );
            if (!scanned.isEmpty()) {
                RuntimeProfilingCounters.increment("recruit.index.fallback_scans");
                nearby = scanned;
            }
        }

        if (sender instanceof ServerPlayer serverPlayer) {
            nearby.removeIf(recruit -> !CommandHierarchy.canCommand(serverPlayer, recruit));
        }

        CommandTargeting.GroupCommandSelection selection = CommandTargeting.forGroupCommand(
                sender.getUUID(),
                sender.getTeam() == null ? null : sender.getTeam().getName(),
                sender.hasPermissions(2),
                group,
                nearby.stream().map(recruit -> new CommandTargeting.RecruitSnapshot(
                        recruit.getUUID(),
                        recruit.getOwnerUUID(),
                        recruit.getGroup(),
                        recruit.getTeam() == null ? null : recruit.getTeam().getName(),
                        recruit.isOwned(),
                        recruit.isAlive(),
                        recruit.getListen(),
                        recruit.distanceToSqr(sender)
                )).toList()
        );

        if (!selection.isSuccess()) {
            BannerModMain.LOGGER.debug("Ignored {} command: {}", commandName, selection.failure());
            return List.of();
        }

        Set<UUID> targetIds = new HashSet<>();
        for (CommandTargeting.RecruitSnapshot recruit : selection.recruits()) {
            targetIds.add(recruit.recruitUuid());
        }

        nearby.removeIf(recruit -> !targetIds.contains(recruit.getUUID()));
        return nearby;
    }
}
