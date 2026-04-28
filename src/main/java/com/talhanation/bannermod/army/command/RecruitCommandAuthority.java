package com.talhanation.bannermod.army.command;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.RecruitEvents;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side authority boundary for recruit command ownership.
 *
 * <p>Command hierarchy is intentionally simple and centralized in {@link CommandHierarchy}.
 * Selection may narrow command targets later, but it must not grant authority.</p>
 */
public final class RecruitCommandAuthority {
    private RecruitCommandAuthority() {
    }

    public static boolean canDirectlyControl(ServerPlayer player, AbstractRecruitEntity recruit) {
        return CommandHierarchy.canCommand(player, recruit);
    }

    public static boolean ownsGroup(ServerPlayer player, @Nullable UUID groupUuid) {
        if (player == null || groupUuid == null || RecruitEvents.recruitsGroupsManager == null) {
            return false;
        }
        RecruitsGroup group = RecruitEvents.recruitsGroupsManager.getGroup(groupUuid);
        return group != null && player.getUUID().equals(group.getPlayerUUID());
    }

    @Nullable
    public static RecruitsGroup ownedGroup(ServerPlayer player, @Nullable UUID groupUuid) {
        if (!ownsGroup(player, groupUuid)) {
            return null;
        }
        return RecruitEvents.recruitsGroupsManager.getGroup(groupUuid);
    }

    public static List<UUID> filterOwnedGroups(ServerPlayer player, List<UUID> groupUuids) {
        if (player == null || groupUuids == null || groupUuids.isEmpty()) {
            return List.of();
        }
        List<UUID> owned = new ArrayList<>(groupUuids.size());
        for (UUID groupUuid : groupUuids) {
            if (ownsGroup(player, groupUuid)) {
                owned.add(groupUuid);
            }
        }
        return owned;
    }
}
