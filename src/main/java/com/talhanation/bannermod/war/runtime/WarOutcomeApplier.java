package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import com.talhanation.bannermod.war.cooldown.WarCooldownKind;
import com.talhanation.bannermod.war.cooldown.WarCooldownRuntime;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WarOutcomeApplier {
    private final WarDeclarationRuntime declarations;
    private final SiegeStandardRuntime sieges;
    private final WarAuditLogSavedData audit;
    private final OccupationRuntime occupations;
    private final DemilitarizationRuntime demilitarizations;
    private final PoliticalRegistryRuntime registry;
    @Nullable
    private final WarCooldownRuntime cooldowns;
    private final long lostTerritoryImmunityTicks;
    @Nullable
    private final BannerModTreasuryManager treasury;
    @Nullable
    private final RecruitsClaimManager claimManager;

    public WarOutcomeApplier(WarDeclarationRuntime declarations,
                             SiegeStandardRuntime sieges,
                             WarAuditLogSavedData audit,
                             OccupationRuntime occupations,
                             DemilitarizationRuntime demilitarizations,
                             PoliticalRegistryRuntime registry) {
        this(declarations, sieges, audit, occupations, demilitarizations, registry, null, 0L, null, null);
    }

    public WarOutcomeApplier(WarDeclarationRuntime declarations,
                             SiegeStandardRuntime sieges,
                             WarAuditLogSavedData audit,
                             OccupationRuntime occupations,
                             DemilitarizationRuntime demilitarizations,
                             PoliticalRegistryRuntime registry,
                             @Nullable WarCooldownRuntime cooldowns,
                             long lostTerritoryImmunityTicks) {
        this(declarations, sieges, audit, occupations, demilitarizations, registry,
                cooldowns, lostTerritoryImmunityTicks, null, null);
    }

    public WarOutcomeApplier(WarDeclarationRuntime declarations,
                             SiegeStandardRuntime sieges,
                             WarAuditLogSavedData audit,
                             OccupationRuntime occupations,
                             DemilitarizationRuntime demilitarizations,
                             PoliticalRegistryRuntime registry,
                             @Nullable WarCooldownRuntime cooldowns,
                             long lostTerritoryImmunityTicks,
                             @Nullable BannerModTreasuryManager treasury,
                             @Nullable RecruitsClaimManager claimManager) {
        this.declarations = declarations;
        this.sieges = sieges;
        this.audit = audit;
        this.occupations = occupations;
        this.demilitarizations = demilitarizations;
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.lostTerritoryImmunityTicks = Math.max(0L, lostTerritoryImmunityTicks);
        this.treasury = treasury;
        this.claimManager = claimManager;
    }

    public Result applyWhitePeace(UUID warId, long gameTime) {
        Optional<WarDeclarationRecord> existing = declarations.byId(warId);
        if (existing.isEmpty()) {
            return Result.invalid("unknown_war");
        }
        WarDeclarationRecord war = existing.get();
        if (war.state() == WarState.RESOLVED || war.state() == WarState.CANCELLED) {
            return Result.invalid("already_closed");
        }
        declarations.updateState(warId, WarState.RESOLVED);
        clearSieges(warId);
        audit.append(warId, "OUTCOME_APPLIED", "type=WHITE_PEACE", gameTime);
        return Result.ok(WarOutcomeType.WHITE_PEACE);
    }

    public Result applyTribute(UUID warId, long tributeAmount, long gameTime) {
        Optional<WarDeclarationRecord> existing = declarations.byId(warId);
        if (existing.isEmpty()) {
            return Result.invalid("unknown_war");
        }
        if (tributeAmount < 0) {
            return Result.invalid("negative_tribute");
        }
        WarDeclarationRecord war = existing.get();
        if (war.state() == WarState.RESOLVED || war.state() == WarState.CANCELLED) {
            return Result.invalid("already_closed");
        }
        int requested = (int) Math.min(tributeAmount, Integer.MAX_VALUE);
        int transferred = transferTribute(
                war.defenderPoliticalEntityId(),
                war.attackerPoliticalEntityId(),
                requested,
                gameTime);
        declarations.updateState(warId, WarState.RESOLVED);
        clearSieges(warId);
        grantLostTerritoryImmunity(war.defenderPoliticalEntityId(), gameTime);
        audit.append(warId, "OUTCOME_APPLIED",
                "type=TRIBUTE;amount=" + tributeAmount + ";transferred=" + transferred,
                gameTime);
        return Result.ok(WarOutcomeType.TRIBUTE);
    }

    public Result applyOccupy(UUID warId, List<ChunkPos> chunks, long gameTime) {
        Optional<WarDeclarationRecord> existing = declarations.byId(warId);
        if (existing.isEmpty()) {
            return Result.invalid("unknown_war");
        }
        if (chunks == null || chunks.isEmpty()) {
            return Result.invalid("no_chunks");
        }
        WarDeclarationRecord war = existing.get();
        if (war.state() == WarState.RESOLVED || war.state() == WarState.CANCELLED) {
            return Result.invalid("already_closed");
        }
        Optional<OccupationRecord> placed = occupations.place(
                warId,
                war.attackerPoliticalEntityId(),
                war.defenderPoliticalEntityId(),
                chunks,
                gameTime);
        if (placed.isEmpty()) {
            return Result.invalid("occupation_rejected");
        }
        declarations.updateState(warId, WarState.RESOLVED);
        clearSieges(warId);
        grantLostTerritoryImmunity(war.defenderPoliticalEntityId(), gameTime);
        audit.append(warId, "OUTCOME_APPLIED",
                "type=OCCUPATION;chunks=" + chunks.size()
                        + ";occupationId=" + placed.get().id(),
                gameTime);
        return Result.ok(WarOutcomeType.OCCUPATION);
    }

    /**
     * Annexes the entire defender claim whose center sits at {@code centerChunk}. Annex must
     * target the center to flip a town wholesale; capturing a non-center chunk is rejected.
     * Per-claim assets that key on the claim UUID (treasury ledger, building registry) follow
     * the new owner automatically.
     */
    public Result applyAnnex(UUID warId, ChunkPos centerChunk, long gameTime,
                             ClaimRepublisher republisher) {
        Optional<WarDeclarationRecord> existing = declarations.byId(warId);
        if (existing.isEmpty()) {
            return Result.invalid("unknown_war");
        }
        if (centerChunk == null) {
            return Result.invalid("no_chunk");
        }
        if (claimManager == null) {
            return Result.invalid("claim_manager_unavailable");
        }
        WarDeclarationRecord war = existing.get();
        if (war.state() == WarState.RESOLVED || war.state() == WarState.CANCELLED) {
            return Result.invalid("already_closed");
        }
        UUID attackerId = war.attackerPoliticalEntityId();
        UUID defenderId = war.defenderPoliticalEntityId();
        RecruitsClaim claim = claimManager.getClaim(centerChunk);
        if (claim == null) {
            return Result.invalid("claim_not_found");
        }
        if (!defenderId.equals(claim.getOwnerPoliticalEntityId())) {
            return Result.invalid("claim_not_defender_owned");
        }
        ChunkPos claimCenter = claim.getCenter();
        if (claimCenter == null || claimCenter.x != centerChunk.x || claimCenter.z != centerChunk.z) {
            return Result.invalid("not_claim_center");
        }
        int chunks = claim.getClaimedChunks().size();
        claim.setOwnerPoliticalEntityId(attackerId);
        ClaimRepublisher publisher = republisher == null ? ClaimRepublisher.NOOP : republisher;
        publisher.republish(claim);
        declarations.updateState(warId, WarState.RESOLVED);
        clearSieges(warId);
        grantLostTerritoryImmunity(defenderId, gameTime);
        audit.append(warId, "OUTCOME_APPLIED",
                "type=ANNEX_LIMITED_CHUNKS;claim=" + claim.getUUID()
                        + ";chunks=" + chunks
                        + ";attacker=" + attackerId
                        + ";defender=" + defenderId,
                gameTime);
        return Result.ok(WarOutcomeType.ANNEX_LIMITED_CHUNKS);
    }

    private int transferTribute(UUID loserEntityId, UUID winnerEntityId,
                                int requested, long gameTime) {
        if (treasury == null || claimManager == null
                || requested <= 0 || loserEntityId == null || winnerEntityId == null) {
            return 0;
        }
        UUID winnerLedgerClaim = null;
        ChunkPos winnerLedgerAnchor = null;
        for (RecruitsClaim claim : claimManager.getAllClaims()) {
            if (winnerEntityId.equals(claim.getOwnerPoliticalEntityId())) {
                winnerLedgerClaim = claim.getUUID();
                winnerLedgerAnchor = claim.getCenter();
                break;
            }
        }
        if (winnerLedgerClaim == null) {
            return 0;
        }
        int remaining = requested;
        int transferred = 0;
        for (RecruitsClaim claim : claimManager.getAllClaims()) {
            if (remaining <= 0) break;
            if (!loserEntityId.equals(claim.getOwnerPoliticalEntityId())) continue;
            BannerModTreasuryLedgerSnapshot ledger = treasury.getLedger(claim.getUUID());
            if (ledger == null) continue;
            int balance = ledger.treasuryBalance();
            if (balance <= 0) continue;
            int debit = Math.min(balance, remaining);
            treasury.recordArmyUpkeepDebit(claim.getUUID(), claim.getCenter(), null, debit, gameTime);
            remaining -= debit;
            transferred += debit;
        }
        if (transferred > 0) {
            treasury.depositTaxes(winnerLedgerClaim, winnerLedgerAnchor, null, transferred, gameTime);
        }
        return transferred;
    }

    @Nullable
    private RecruitsClaim findFirstClaimOwnedBy(UUID politicalEntityId) {
        if (claimManager == null) return null;
        for (RecruitsClaim claim : claimManager.getAllClaims()) {
            if (politicalEntityId.equals(claim.getOwnerPoliticalEntityId())) return claim;
        }
        return null;
    }

    public Result cancel(UUID warId, long gameTime, String reason) {
        Optional<WarDeclarationRecord> existing = declarations.byId(warId);
        if (existing.isEmpty()) {
            return Result.invalid("unknown_war");
        }
        WarDeclarationRecord war = existing.get();
        if (war.state() != WarState.DECLARED) {
            return Result.invalid("not_cancellable");
        }
        declarations.updateState(warId, WarState.CANCELLED);
        clearSieges(warId);
        audit.append(warId, "WAR_CANCELLED", "reason=" + (reason == null ? "" : reason), gameTime);
        return Result.ok(WarOutcomeType.WHITE_PEACE);
    }

    public Result applyVassalize(UUID warId, long gameTime) {
        Optional<WarDeclarationRecord> existing = declarations.byId(warId);
        if (existing.isEmpty()) {
            return Result.invalid("unknown_war");
        }
        WarDeclarationRecord war = existing.get();
        if (war.state() == WarState.RESOLVED || war.state() == WarState.CANCELLED) {
            return Result.invalid("already_closed");
        }
        boolean changed = registry.updateStatus(war.defenderPoliticalEntityId(), PoliticalEntityStatus.VASSAL);
        if (!changed) {
            return Result.invalid("defender_not_found");
        }
        declarations.updateState(warId, WarState.RESOLVED);
        clearSieges(warId);
        grantLostTerritoryImmunity(war.defenderPoliticalEntityId(), gameTime);
        audit.append(warId, "OUTCOME_APPLIED",
                "type=VASSALIZATION;defender=" + war.defenderPoliticalEntityId(), gameTime);
        return Result.ok(WarOutcomeType.VASSALIZATION);
    }

    public Result applyDemilitarization(UUID warId, long durationTicks, long gameTime) {
        if (durationTicks <= 0) {
            return Result.invalid("invalid_duration");
        }
        Optional<WarDeclarationRecord> existing = declarations.byId(warId);
        if (existing.isEmpty()) {
            return Result.invalid("unknown_war");
        }
        WarDeclarationRecord war = existing.get();
        if (war.state() == WarState.RESOLVED || war.state() == WarState.CANCELLED) {
            return Result.invalid("already_closed");
        }
        Optional<DemilitarizationRecord> imposed = demilitarizations.impose(
                war.defenderPoliticalEntityId(), warId, gameTime + durationTicks);
        if (imposed.isEmpty()) {
            return Result.invalid("demilitarization_failed");
        }
        declarations.updateState(warId, WarState.RESOLVED);
        clearSieges(warId);
        grantLostTerritoryImmunity(war.defenderPoliticalEntityId(), gameTime);
        audit.append(warId, "OUTCOME_APPLIED",
                "type=FORCED_DEMILITARIZATION;defender=" + war.defenderPoliticalEntityId()
                        + ";endsAt=" + (gameTime + durationTicks),
                gameTime);
        return Result.ok(WarOutcomeType.FORCED_DEMILITARIZATION);
    }

    private void grantLostTerritoryImmunity(UUID loserId, long gameTime) {
        if (cooldowns == null || lostTerritoryImmunityTicks <= 0L || loserId == null) {
            return;
        }
        cooldowns.grant(loserId, WarCooldownKind.LOST_TERRITORY_IMMUNITY,
                gameTime + lostTerritoryImmunityTicks);
    }

    public boolean removeOccupationOnRevoltSuccess(UUID occupationId, long gameTime) {
        Optional<OccupationRecord> existing = occupations.byId(occupationId);
        if (existing.isEmpty()) {
            return false;
        }
        OccupationRecord record = existing.get();
        boolean removed = occupations.remove(occupationId);
        if (removed) {
            audit.append(record.warId(), "REVOLT_SUCCESS",
                    "occupation=" + occupationId
                            + ";rebel=" + record.occupiedEntityId()
                            + ";occupier=" + record.occupierEntityId(),
                    gameTime);
        }
        return removed;
    }

    private void clearSieges(UUID warId) {
        for (SiegeStandardRecord record : sieges.forWar(warId)) {
            sieges.remove(record.id());
        }
    }

    public record Result(boolean valid, String reason, WarOutcomeType outcome) {
        public static Result ok(WarOutcomeType outcome) {
            return new Result(true, "", outcome);
        }

        public static Result invalid(String reason) {
            return new Result(false, reason, null);
        }
    }
}
