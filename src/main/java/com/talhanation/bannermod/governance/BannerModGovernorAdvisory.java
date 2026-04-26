package com.talhanation.bannermod.governance;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Translates raw {@link BannerModGovernorIncident} and {@link BannerModGovernorRecommendation}
 * tokens into player-readable advisory lines. Used by the Governor screen to give new players
 * actionable guidance about mod mechanics rather than opaque token strings.
 *
 * <p>All methods are pure (no side effects, no external state) so they can be called freely
 * from both client and server without synchronisation concerns.</p>
 */
public final class BannerModGovernorAdvisory {

    private BannerModGovernorAdvisory() {
    }

    /**
     * Returns a short, tutorial-style sentence explaining the given incident.
     * Shown beneath the incident list in the Governor screen.
     */
    public static String incidentTip(BannerModGovernorIncident incident) {
        return switch (incident) {
            case HOSTILE_CLAIM ->
                    "Another faction controls this land. Capture the claim banner to reclaim it for your settlement.";
            case DEGRADED_SETTLEMENT ->
                    "This settlement is in a degraded state. Assign workers and ensure your claim flag is planted.";
            case UNCLAIMED_SETTLEMENT ->
                    "No claim has been placed here. Right-click a Claim Banner on the ground to establish ownership.";
            case UNDER_SIEGE ->
                    "Your settlement is under siege! Tax collection is suspended until the threat is repelled.";
            case WORKER_SHORTAGE ->
                    "No workers are assigned. Hire workers from your settlement menu to produce resources and grow citizens.";
            case SUPPLY_BLOCKED ->
                    "Worker supply chains are blocked. Check that haul routes are clear and storage is not full.";
            case RECRUIT_UPKEEP_BLOCKED ->
                    "Your garrison cannot be paid. Increase treasury income or reduce troop count to restore upkeep.";
        };
    }

    /**
     * Returns an actionable advisory sentence for the given recommendation.
     * Shown in the 'Governor says:' panel of the Governor screen.
     */
    public static String recommendationTip(BannerModGovernorRecommendation recommendation) {
        return switch (recommendation) {
            case HOLD_COURSE ->
                    "Settlement is stable. Continue current operations.";
            case INCREASE_GARRISON ->
                    "Citizen count exceeds garrison size. Recruit more soldiers or set garrison priority to High.";
            case STRENGTHEN_FORTIFICATIONS ->
                    "Defences are thin. Build walls or place watchtowers inside your claimed chunks.";
            case RELIEVE_SUPPLY_PRESSURE ->
                    "Supply lines are strained. Clear blocked routes or expand storage near the settlement core.";
        };
    }

    /**
     * Produces a combined advisory text block for display, one line per active item.
     * Returns at least one line (the hold-course tip) so the panel is never blank.
     */
    public static List<String> buildAdvisoryLines(List<String> incidentTokens,
                                                   List<String> recommendationTokens) {
        List<String> lines = new ArrayList<>();

        EnumSet<BannerModGovernorIncident> activeIncidents = EnumSet.noneOf(BannerModGovernorIncident.class);
        for (String token : incidentTokens) {
            for (BannerModGovernorIncident incident : BannerModGovernorIncident.values()) {
                if (incident.token().equals(token)) {
                    activeIncidents.add(incident);
                }
            }
        }
        for (BannerModGovernorIncident incident : activeIncidents) {
            lines.add(incidentTip(incident));
        }

        for (String token : recommendationTokens) {
            for (BannerModGovernorRecommendation rec : BannerModGovernorRecommendation.values()) {
                if (rec.token().equals(token)) {
                    lines.add(recommendationTip(rec));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(recommendationTip(BannerModGovernorRecommendation.HOLD_COURSE));
        }
        return lines;
    }

    /**
     * Returns a one-line intro shown at the top of the Governor screen for players who
     * have not yet interacted with a Governor (indicated by a zero heartbeat tick).
     */
    public static String firstContactHint() {
        return "I am your Governor. I will manage this settlement and advise you on its needs.";
    }

    /**
     * Returns a short tutorial tooltip explaining the auto-manage toggle.
     */
    public static String autoManageTip(boolean enabled) {
        return enabled
                ? "Auto-manage ON: I will issue garrison hold orders automatically when troops are needed."
                : "Auto-manage OFF: I will advise only. You issue all orders manually.";
    }
}
