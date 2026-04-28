package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.army.command.CommandRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Pure command-target selection contract shared by Phase 4 packet handlers.
 * Group commands preserve the existing 100-block nearby-owned-or-allied-recruits radius,
 * while invalid or unauthorized inputs degrade to stable empty results.
 */
public final class CommandTargeting {

    public static final double GROUP_COMMAND_RADIUS = 100.0D;
    private static final double GROUP_COMMAND_RADIUS_SQUARED = GROUP_COMMAND_RADIUS * GROUP_COMMAND_RADIUS;

    private CommandTargeting() {
    }

    public static GroupCommandSelection forGroupCommand(UUID senderUuid, @Nullable String senderTeamId, boolean senderAdmin, UUID groupUuid, Iterable<RecruitSnapshot> nearbyRecruits) {
        if (senderUuid == null) {
            return GroupCommandSelection.empty(Failure.INVALID_SENDER);
        }

        if (nearbyRecruits == null) {
            return GroupCommandSelection.empty(Failure.INVALID_RECRUIT_SOURCE);
        }

        List<RecruitSnapshot> selectedRecruits = new ArrayList<>();
        for (RecruitSnapshot recruit : nearbyRecruits) {
            if (recruit == null || !recruit.canReceiveCommandFrom(senderUuid, senderTeamId, senderAdmin)) {
                continue;
            }

            if (!recruit.matchesGroup(groupUuid)) {
                continue;
            }

            selectedRecruits.add(recruit);
        }

        return new GroupCommandSelection(selectedRecruits, Failure.NONE);
    }

    public static SingleRecruitSelection forSingleRecruit(UUID senderUuid, @Nullable String senderTeamId, boolean senderAdmin, UUID recruitUuid, Iterable<RecruitSnapshot> nearbyRecruits) {
        if (senderUuid == null) {
            return SingleRecruitSelection.empty(Failure.INVALID_SENDER);
        }

        if (recruitUuid == null) {
            return SingleRecruitSelection.empty(Failure.INVALID_RECRUIT);
        }

        if (nearbyRecruits == null) {
            return SingleRecruitSelection.empty(Failure.INVALID_RECRUIT_SOURCE);
        }

        for (RecruitSnapshot recruit : nearbyRecruits) {
            if (recruit == null || !recruit.recruitUuid().equals(recruitUuid)) {
                continue;
            }

            if (!recruit.isCommandable()) {
                return SingleRecruitSelection.empty(Failure.NOT_COMMANDABLE);
            }

            if (!recruit.canReceiveCommandFrom(senderUuid, senderTeamId, senderAdmin)) {
                return SingleRecruitSelection.empty(Failure.NOT_AUTHORIZED);
            }

            if (!recruit.isWithinCommandRadius()) {
                return SingleRecruitSelection.empty(Failure.OUT_OF_RADIUS);
            }

            return new SingleRecruitSelection(Optional.of(recruit), Failure.NONE);
        }

        return SingleRecruitSelection.empty(Failure.RECRUIT_NOT_FOUND);
    }

    public enum Failure {
        NONE,
        INVALID_SENDER,
        INVALID_RECRUIT,
        INVALID_RECRUIT_SOURCE,
        RECRUIT_NOT_FOUND,
        NOT_COMMANDABLE,
        NOT_AUTHORIZED,
        OUT_OF_RADIUS,
        WRONG_GROUP
    }

    public record RecruitSnapshot(
            UUID recruitUuid,
            UUID ownerUuid,
            UUID groupUuid,
            @Nullable String teamId,
            boolean owned,
            boolean alive,
            boolean listening,
            double distanceSquared
    ) {
        public RecruitSnapshot {
            Objects.requireNonNull(recruitUuid, "recruitUuid");
            if (distanceSquared < 0.0D) {
                throw new IllegalArgumentException("distanceSquared must be non-negative");
            }
        }

        public boolean isWithinCommandRadius() {
            return distanceSquared <= GROUP_COMMAND_RADIUS_SQUARED;
        }

        public boolean isCommandable() {
            return owned && alive && listening;
        }

        public boolean canReceiveCommandFrom(UUID senderUuid, @Nullable String senderTeamId, boolean senderAdmin) {
            CommandRole role = CommandHierarchy.roleFor(senderUuid, senderTeamId, senderAdmin, ownerUuid, teamId, owned);
            return isCommandable() && role != CommandRole.NONE && isWithinCommandRadius();
        }

        public boolean matchesGroup(UUID requestedGroup) {
            if (requestedGroup == null) {
                return true;
            }

            return groupUuid != null && groupUuid.equals(requestedGroup);
        }
    }

    public record GroupCommandSelection(List<RecruitSnapshot> recruits, Failure failure) {
        public GroupCommandSelection {
            recruits = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(recruits, "recruits")));
            failure = Objects.requireNonNull(failure, "failure");
        }

        public static GroupCommandSelection empty(Failure failure) {
            return new GroupCommandSelection(List.of(), failure);
        }

        public boolean isSuccess() {
            return failure == Failure.NONE;
        }
    }

    public record SingleRecruitSelection(Optional<RecruitSnapshot> recruit, Failure failure) {
        public SingleRecruitSelection {
            recruit = Objects.requireNonNull(recruit, "recruit");
            failure = Objects.requireNonNull(failure, "failure");
        }

        public static SingleRecruitSelection empty(Failure failure) {
            return new SingleRecruitSelection(Optional.empty(), failure);
        }

        public boolean isSuccess() {
            return failure == Failure.NONE && recruit.isPresent();
        }
    }
}
