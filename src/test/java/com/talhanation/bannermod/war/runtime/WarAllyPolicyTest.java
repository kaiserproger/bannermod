package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.war.registry.GovernmentForm;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarAllyPolicyTest {

    private static final UUID ATTACKER = UUID.randomUUID();
    private static final UUID DEFENDER = UUID.randomUUID();
    private static final UUID INVITEE = UUID.randomUUID();

    private WarDeclarationRecord war(WarState state, List<UUID> attackerAllies, List<UUID> defenderAllies) {
        return new WarDeclarationRecord(
                UUID.randomUUID(),
                ATTACKER,
                DEFENDER,
                WarGoalType.WHITE_PEACE,
                "",
                List.of(),
                attackerAllies,
                defenderAllies,
                0L,
                0L,
                state
        );
    }

    private PoliticalEntityRecord entity(UUID id, PoliticalEntityStatus status) {
        return new PoliticalEntityRecord(id, "name", status, null, List.of(),
                BlockPos.ZERO, "", "", "", "", 0L, GovernmentForm.MONARCHY);
    }

    @Test
    void rejectsWhenWarMissing() {
        assertEquals(WarAllyPolicy.Denial.WAR_NOT_FOUND,
                WarAllyPolicy.canInvite(null, WarSide.ATTACKER, INVITEE,
                        Optional.of(entity(INVITEE, PoliticalEntityStatus.STATE))));
    }

    @Test
    void rejectsAfterPreActive() {
        WarDeclarationRecord active = war(WarState.ACTIVE, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.WAR_NOT_PRE_ACTIVE,
                WarAllyPolicy.canInvite(active, WarSide.ATTACKER, INVITEE,
                        Optional.of(entity(INVITEE, PoliticalEntityStatus.STATE))));
    }

    @Test
    void rejectsUnknownInvitee() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.INVITEE_UNKNOWN,
                WarAllyPolicy.canInvite(declared, WarSide.ATTACKER, INVITEE, Optional.empty()));
    }

    @Test
    void rejectsMainSideAsAlly() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.INVITEE_IS_MAIN_SIDE,
                WarAllyPolicy.canInvite(declared, WarSide.ATTACKER, ATTACKER,
                        Optional.of(entity(ATTACKER, PoliticalEntityStatus.STATE))));
    }

    @Test
    void rejectsOpposingMainSide() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.INVITEE_ON_OPPOSING_SIDE,
                WarAllyPolicy.canInvite(declared, WarSide.ATTACKER, DEFENDER,
                        Optional.of(entity(DEFENDER, PoliticalEntityStatus.STATE))));
    }

    @Test
    void rejectsAlreadyOnSameSide() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(INVITEE), List.of());
        assertEquals(WarAllyPolicy.Denial.INVITEE_ALREADY_ON_SIDE,
                WarAllyPolicy.canInvite(declared, WarSide.ATTACKER, INVITEE,
                        Optional.of(entity(INVITEE, PoliticalEntityStatus.STATE))));
    }

    @Test
    void rejectsAlreadyOnOpposingSide() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of(INVITEE));
        assertEquals(WarAllyPolicy.Denial.INVITEE_ON_OPPOSING_SIDE,
                WarAllyPolicy.canInvite(declared, WarSide.ATTACKER, INVITEE,
                        Optional.of(entity(INVITEE, PoliticalEntityStatus.STATE))));
    }

    @Test
    void rejectsPeacefulOnAttackerSide() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.PEACEFUL_CANNOT_JOIN_ATTACKER,
                WarAllyPolicy.canInvite(declared, WarSide.ATTACKER, INVITEE,
                        Optional.of(entity(INVITEE, PoliticalEntityStatus.PEACEFUL))));
    }

    @Test
    void allowsPeacefulOnDefenderSide() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.OK,
                WarAllyPolicy.canInvite(declared, WarSide.DEFENDER, INVITEE,
                        Optional.of(entity(INVITEE, PoliticalEntityStatus.PEACEFUL))));
    }

    @Test
    void allowsValidStateOnAttackerSide() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.OK,
                WarAllyPolicy.canInvite(declared, WarSide.ATTACKER, INVITEE,
                        Optional.of(entity(INVITEE, PoliticalEntityStatus.STATE))));
    }

    @Test
    void respondMatchesInviteWar() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        WarAllyInviteRecord invite = new WarAllyInviteRecord(
                UUID.randomUUID(), declared.id(), WarSide.ATTACKER, INVITEE, null, 0L);
        assertEquals(WarAllyPolicy.Denial.OK, WarAllyPolicy.canRespond(declared, invite));
    }

    @Test
    void respondRejectsWarMismatch() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        WarAllyInviteRecord invite = new WarAllyInviteRecord(
                UUID.randomUUID(), UUID.randomUUID(), WarSide.ATTACKER, INVITEE, null, 0L);
        assertEquals(WarAllyPolicy.Denial.INVITE_WAR_MISMATCH, WarAllyPolicy.canRespond(declared, invite));
    }

    @Test
    void respondRejectsAfterPreActive() {
        WarDeclarationRecord active = war(WarState.ACTIVE, List.of(), List.of());
        WarAllyInviteRecord invite = new WarAllyInviteRecord(
                UUID.randomUUID(), active.id(), WarSide.ATTACKER, INVITEE, null, 0L);
        assertEquals(WarAllyPolicy.Denial.WAR_NOT_PRE_ACTIVE, WarAllyPolicy.canRespond(active, invite));
    }

    @Test
    void respondRejectsMissingInvite() {
        WarDeclarationRecord declared = war(WarState.DECLARED, List.of(), List.of());
        assertEquals(WarAllyPolicy.Denial.INVITE_NOT_FOUND, WarAllyPolicy.canRespond(declared, null));
    }
}
