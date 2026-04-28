package com.talhanation.bannermod.army.command;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical recruit command authority roles.
 *
 * <p>Supported authority is owner, same scoreboard team, and server admin. Nation-level authority is
 * intentionally unsupported until recruits have a server-authoritative nation command membership.</p>
 */
public final class CommandHierarchy {
    private CommandHierarchy() {
    }

    public static CommandRole roleFor(ServerPlayer commander, AbstractRecruitEntity recruit) {
        if (commander == null || recruit == null || !recruit.isAlive() || !recruit.isOwned()) {
            return CommandRole.NONE;
        }
        return roleFor(
                commander.getUUID(),
                teamName(commander),
                commander.hasPermissions(2),
                recruit.getOwnerUUID(),
                teamName(recruit),
                recruit.isOwned()
        );
    }

    public static CommandRole roleFor(
            @Nullable UUID commanderUuid,
            @Nullable String commanderTeam,
            boolean commanderAdmin,
            @Nullable UUID recruitOwnerUuid,
            @Nullable String recruitTeam,
            boolean recruitOwned
    ) {
        if (commanderUuid == null || !recruitOwned) {
            return CommandRole.NONE;
        }
        if (Objects.equals(commanderUuid, recruitOwnerUuid)) {
            return CommandRole.OWNER;
        }
        if (commanderTeam != null && recruitTeam != null && commanderTeam.equals(recruitTeam)) {
            return CommandRole.TEAMMATE;
        }
        if (commanderAdmin) {
            return CommandRole.ADMIN;
        }
        return CommandRole.NONE;
    }

    public static boolean canCommand(ServerPlayer commander, AbstractRecruitEntity recruit) {
        return roleFor(commander, recruit) != CommandRole.NONE;
    }

    private static String teamName(ServerPlayer commander) {
        return commander.getTeam() == null ? null : commander.getTeam().getName();
    }

    private static String teamName(AbstractRecruitEntity recruit) {
        return recruit.getTeam() == null ? null : recruit.getTeam().getName();
    }
}
