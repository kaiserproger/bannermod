package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Single entry point for ally invite/accept/decline/cancel actions. Both the
 * slash commands and the War Room UI packets call into here so authorisation,
 * policy checks, and audit logging stay aligned.
 */
public final class WarAllyService {
    private WarAllyService() {
    }

    public enum Outcome {
        OK,
        WAR_NOT_FOUND,
        WAR_NOT_PRE_ACTIVE,
        SIDE_NOT_FOUND,
        NOT_LEADER,
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

    public record InviteResult(Outcome outcome, @Nullable WarAllyInviteRecord record) {
        public boolean ok() { return outcome == Outcome.OK; }
        public static InviteResult of(Outcome outcome) { return new InviteResult(outcome, null); }
        public static InviteResult success(WarAllyInviteRecord record) { return new InviteResult(Outcome.OK, record); }
    }

    public static InviteResult invite(ServerLevel level,
                                      @Nullable ServerPlayer actor,
                                      @Nullable UUID warId,
                                      @Nullable WarSide side,
                                      @Nullable UUID inviteeEntityId) {
        if (level == null || warId == null || side == null) return InviteResult.of(Outcome.WAR_NOT_FOUND);
        WarDeclarationRuntime declarations = WarRuntimeContext.declarations(level);
        Optional<WarDeclarationRecord> warOpt = declarations.byId(warId);
        if (warOpt.isEmpty()) return InviteResult.of(Outcome.WAR_NOT_FOUND);
        WarDeclarationRecord war = warOpt.get();
        if (!WarAllyPolicy.isPreActive(war)) return InviteResult.of(Outcome.WAR_NOT_PRE_ACTIVE);

        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        UUID sideEntityId = war.mainSideEntityId(side);
        Optional<PoliticalEntityRecord> sideOpt = registry.byId(sideEntityId);
        if (sideOpt.isEmpty()) return InviteResult.of(Outcome.SIDE_NOT_FOUND);
        if (!PoliticalEntityAuthority.isLeaderOrOp(actor, sideOpt.get())) return InviteResult.of(Outcome.NOT_LEADER);

        Optional<PoliticalEntityRecord> inviteeOpt = inviteeEntityId == null
                ? Optional.empty()
                : registry.byId(inviteeEntityId);
        WarAllyPolicy.Denial policy = WarAllyPolicy.canInvite(war, side, inviteeEntityId, inviteeOpt);
        if (policy != WarAllyPolicy.Denial.OK) return InviteResult.of(mapDenial(policy));

        WarAllyInviteRuntime invites = WarRuntimeContext.allyInvites(level);
        Optional<WarAllyInviteRecord> existing = invites.existing(warId, side, inviteeEntityId);
        if (existing.isPresent()) {
            return InviteResult.success(existing.get());
        }
        UUID inviterUuid = actor == null ? null : actor.getUUID();
        WarAllyInviteRecord record = invites.create(warId, side, inviteeEntityId, inviterUuid, level.getGameTime());
        appendAudit(level, warId, "ALLY_INVITED",
                "side=" + side.name() + ";invitee=" + inviteeEntityId
                        + (inviterUuid == null ? "" : ";inviter=" + inviterUuid));
        return InviteResult.success(record);
    }

    public static InviteResult accept(ServerLevel level,
                                      @Nullable ServerPlayer actor,
                                      @Nullable UUID inviteId) {
        return respond(level, actor, inviteId, true);
    }

    public static InviteResult decline(ServerLevel level,
                                       @Nullable ServerPlayer actor,
                                       @Nullable UUID inviteId) {
        return respond(level, actor, inviteId, false);
    }

    public static InviteResult cancel(ServerLevel level,
                                      @Nullable ServerPlayer actor,
                                      @Nullable UUID inviteId) {
        if (level == null || inviteId == null) return InviteResult.of(Outcome.INVITE_NOT_FOUND);
        WarAllyInviteRuntime invites = WarRuntimeContext.allyInvites(level);
        Optional<WarAllyInviteRecord> inviteOpt = invites.byId(inviteId);
        if (inviteOpt.isEmpty()) return InviteResult.of(Outcome.INVITE_NOT_FOUND);
        WarAllyInviteRecord invite = inviteOpt.get();

        WarDeclarationRuntime declarations = WarRuntimeContext.declarations(level);
        Optional<WarDeclarationRecord> warOpt = declarations.byId(invite.warId());
        if (warOpt.isEmpty()) return InviteResult.of(Outcome.WAR_NOT_FOUND);

        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        UUID sideEntityId = warOpt.get().mainSideEntityId(invite.side());
        Optional<PoliticalEntityRecord> sideOpt = registry.byId(sideEntityId);
        if (sideOpt.isEmpty()) return InviteResult.of(Outcome.SIDE_NOT_FOUND);
        if (!PoliticalEntityAuthority.isLeaderOrOp(actor, sideOpt.get())) return InviteResult.of(Outcome.NOT_LEADER);

        invites.remove(invite.id());
        appendAudit(level, invite.warId(), "ALLY_INVITE_CANCELLED",
                "side=" + invite.side().name() + ";invitee=" + invite.inviteePoliticalEntityId());
        return InviteResult.success(invite);
    }

    private static InviteResult respond(ServerLevel level,
                                        @Nullable ServerPlayer actor,
                                        @Nullable UUID inviteId,
                                        boolean accept) {
        if (level == null || inviteId == null) return InviteResult.of(Outcome.INVITE_NOT_FOUND);
        WarAllyInviteRuntime invites = WarRuntimeContext.allyInvites(level);
        Optional<WarAllyInviteRecord> inviteOpt = invites.byId(inviteId);
        if (inviteOpt.isEmpty()) return InviteResult.of(Outcome.INVITE_NOT_FOUND);
        WarAllyInviteRecord invite = inviteOpt.get();

        WarDeclarationRuntime declarations = WarRuntimeContext.declarations(level);
        Optional<WarDeclarationRecord> warOpt = declarations.byId(invite.warId());
        WarDeclarationRecord war = warOpt.orElse(null);
        WarAllyPolicy.Denial policy = WarAllyPolicy.canRespond(war, invite);
        if (policy != WarAllyPolicy.Denial.OK) {
            // war gone or no longer pre-active — clean up the dangling invite either way.
            if (policy == WarAllyPolicy.Denial.WAR_NOT_FOUND || policy == WarAllyPolicy.Denial.WAR_NOT_PRE_ACTIVE) {
                invites.remove(invite.id());
            }
            return InviteResult.of(mapDenial(policy));
        }

        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        Optional<PoliticalEntityRecord> inviteeOpt = registry.byId(invite.inviteePoliticalEntityId());
        if (inviteeOpt.isEmpty()) return InviteResult.of(Outcome.INVITEE_UNKNOWN);
        if (!PoliticalEntityAuthority.isLeaderOrOp(actor, inviteeOpt.get())) {
            return InviteResult.of(Outcome.NOT_LEADER);
        }

        if (accept) {
            // Re-check static policy in case status changed between invite and accept (e.g. invitee turned PEACEFUL).
            WarAllyPolicy.Denial recheck = WarAllyPolicy.canInvite(war, invite.side(), invite.inviteePoliticalEntityId(), inviteeOpt);
            if (recheck != WarAllyPolicy.Denial.OK) {
                invites.remove(invite.id());
                return InviteResult.of(mapDenial(recheck));
            }
            declarations.appendAlly(invite.warId(), invite.side(), invite.inviteePoliticalEntityId());
            invites.remove(invite.id());
            appendAudit(level, invite.warId(), "ALLY_JOINED",
                    "side=" + invite.side().name() + ";entity=" + invite.inviteePoliticalEntityId());
        } else {
            invites.remove(invite.id());
            appendAudit(level, invite.warId(), "ALLY_INVITE_DECLINED",
                    "side=" + invite.side().name() + ";entity=" + invite.inviteePoliticalEntityId());
        }
        return InviteResult.success(invite);
    }

    private static void appendAudit(ServerLevel level, UUID warId, String type, String detail) {
        WarAuditLogSavedData audit = WarRuntimeContext.audit(level);
        audit.append(warId, type, detail, level.getGameTime());
    }

    private static Outcome mapDenial(WarAllyPolicy.Denial denial) {
        return switch (denial) {
            case OK -> Outcome.OK;
            case WAR_NOT_FOUND -> Outcome.WAR_NOT_FOUND;
            case WAR_NOT_PRE_ACTIVE -> Outcome.WAR_NOT_PRE_ACTIVE;
            case INVITEE_UNKNOWN -> Outcome.INVITEE_UNKNOWN;
            case INVITEE_IS_MAIN_SIDE -> Outcome.INVITEE_IS_MAIN_SIDE;
            case INVITEE_ALREADY_ON_SIDE -> Outcome.INVITEE_ALREADY_ON_SIDE;
            case INVITEE_ON_OPPOSING_SIDE -> Outcome.INVITEE_ON_OPPOSING_SIDE;
            case PEACEFUL_CANNOT_JOIN_ATTACKER -> Outcome.PEACEFUL_CANNOT_JOIN_ATTACKER;
            case INVITE_NOT_FOUND -> Outcome.INVITE_NOT_FOUND;
            case INVITE_WAR_MISMATCH -> Outcome.INVITE_WAR_MISMATCH;
        };
    }
}
