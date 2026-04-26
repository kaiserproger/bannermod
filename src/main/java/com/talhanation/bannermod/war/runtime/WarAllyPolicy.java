package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Pure rules deciding whether an ally invitation can be sent or accepted.
 *
 * <p>Both the slash command and packet entry points share these denial tokens
 * so chat replies and the War Room UI never disagree on what is legal. The
 * policy is data-only: callers resolve {@link WarDeclarationRecord} and
 * {@link PoliticalEntityRecord} themselves before asking.
 */
public final class WarAllyPolicy {
    private WarAllyPolicy() {
    }

    public enum Denial {
        OK,
        WAR_NOT_FOUND,
        WAR_NOT_PRE_ACTIVE,
        INVITEE_UNKNOWN,
        INVITEE_IS_MAIN_SIDE,
        INVITEE_ALREADY_ON_SIDE,
        INVITEE_ON_OPPOSING_SIDE,
        PEACEFUL_CANNOT_JOIN_ATTACKER,
        INVITE_NOT_FOUND,
        INVITE_WAR_MISMATCH;

        public String token() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Pre-active wars (DECLARED) accept new allies. Once a war advances we lock the side. */
    public static boolean isPreActive(WarDeclarationRecord war) {
        return war != null && war.state() == WarState.DECLARED;
    }

    /** Validates a proposed invite against the war + invitee state. */
    public static Denial canInvite(WarDeclarationRecord war,
                                   WarSide side,
                                   UUID inviteeEntityId,
                                   Optional<PoliticalEntityRecord> invitee) {
        if (war == null) return Denial.WAR_NOT_FOUND;
        if (!isPreActive(war)) return Denial.WAR_NOT_PRE_ACTIVE;
        if (side == null || inviteeEntityId == null) return Denial.INVITEE_UNKNOWN;
        if (invitee.isEmpty()) return Denial.INVITEE_UNKNOWN;

        UUID mainSide = war.mainSideEntityId(side);
        if (inviteeEntityId.equals(mainSide)) return Denial.INVITEE_IS_MAIN_SIDE;

        UUID otherMain = war.mainSideEntityId(otherSide(side));
        if (inviteeEntityId.equals(otherMain)) return Denial.INVITEE_ON_OPPOSING_SIDE;

        if (war.alliesFor(side).contains(inviteeEntityId)) return Denial.INVITEE_ALREADY_ON_SIDE;
        if (war.alliesFor(otherSide(side)).contains(inviteeEntityId)) return Denial.INVITEE_ON_OPPOSING_SIDE;

        PoliticalEntityRecord record = invitee.get();
        if (side == WarSide.ATTACKER && record.status() == PoliticalEntityStatus.PEACEFUL) {
            return Denial.PEACEFUL_CANNOT_JOIN_ATTACKER;
        }
        return Denial.OK;
    }

    /** Validates that an existing invite can still be accepted/declined against current war state. */
    public static Denial canRespond(WarDeclarationRecord war,
                                    WarAllyInviteRecord invite) {
        if (invite == null) return Denial.INVITE_NOT_FOUND;
        if (war == null) return Denial.WAR_NOT_FOUND;
        if (!war.id().equals(invite.warId())) return Denial.INVITE_WAR_MISMATCH;
        if (!isPreActive(war)) return Denial.WAR_NOT_PRE_ACTIVE;
        return Denial.OK;
    }

    public static WarSide otherSide(WarSide side) {
        return side == WarSide.ATTACKER ? WarSide.DEFENDER : WarSide.ATTACKER;
    }
}
