package com.talhanation.bannermod.war.registry;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public final class PoliticalRegistryValidation {
    public static final int MIN_NAME_LENGTH = 3;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_CHARTER_LENGTH = 256;

    private PoliticalRegistryValidation() {
    }

    public static Result validateCreate(String name, UUID leaderUuid, Collection<PoliticalEntityRecord> existing) {
        String normalized = normalizeName(name);
        if (normalized.length() < MIN_NAME_LENGTH) {
            return Result.invalid("name_too_short");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            return Result.invalid("name_too_long");
        }
        if (leaderUuid == null) {
            return Result.invalid("missing_leader");
        }
        for (PoliticalEntityRecord record : existing) {
            if (leaderUuid.equals(record.leaderUuid())) {
                return Result.invalid("leader_already_has_entity");
            }
            if (record.name().equalsIgnoreCase(normalized)) {
                return Result.invalid("duplicate_name");
            }
        }
        return Result.ok();
    }

    /**
     * Validate a rename of {@code currentEntityId} to {@code newName} against the existing
     * registry. Identical to {@link #validateCreate} but ignores any duplicate match for the
     * entity being renamed (so a leader can re-issue the same name as a no-op safety) and
     * does not require a leader uuid.
     */
    public static Result validateRename(String newName,
                                        UUID currentEntityId,
                                        Collection<PoliticalEntityRecord> existing) {
        String normalized = normalizeName(newName);
        if (normalized.length() < MIN_NAME_LENGTH) {
            return Result.invalid("name_too_short");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            return Result.invalid("name_too_long");
        }
        for (PoliticalEntityRecord record : existing) {
            if (currentEntityId != null && currentEntityId.equals(record.id())) {
                continue;
            }
            if (record.name().equalsIgnoreCase(normalized)) {
                return Result.invalid("duplicate_name");
            }
        }
        return Result.ok();
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    /**
     * Validate a color string for the political-entity color field. Empty / null is allowed
     * (clears the color). Otherwise the string must be a 6- or 8-hex-digit colour with an
     * optional leading '#' as accepted by {@link PoliticalColorParser}. Pure — no side effects.
     */
    public static Result validateColor(String color) {
        if (color == null) {
            return Result.ok();
        }
        String trimmed = color.trim();
        if (trimmed.isEmpty()) {
            return Result.ok();
        }
        String hex = trimmed.charAt(0) == '#' ? trimmed.substring(1) : trimmed;
        if (hex.length() != 6 && hex.length() != 8) {
            return Result.invalid("color_bad_length");
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hexDigit) {
                return Result.invalid("color_not_hex");
            }
        }
        return Result.ok();
    }

    /** Returns the canonical stored form for a validated color string (trimmed; empty allowed). */
    public static String normalizeColor(String color) {
        return color == null ? "" : color.trim();
    }

    /**
     * Validate a charter (free-text RP description). Empty / null is allowed. Length capped at
     * {@link #MAX_CHARTER_LENGTH} after trim to bound packet size and UI render cost.
     */
    public static Result validateCharter(String charter) {
        String normalized = normalizeCharter(charter);
        if (normalized.length() > MAX_CHARTER_LENGTH) {
            return Result.invalid("charter_too_long");
        }
        return Result.ok();
    }

    /** Trim-only normalisation — keeps internal whitespace so RP formatting survives. */
    public static String normalizeCharter(String charter) {
        return charter == null ? "" : charter.trim();
    }

    public static String slug(String name) {
        return normalizeName(name).toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public record Result(boolean valid, String reason) {
        public static Result ok() {
            return new Result(true, "");
        }

        public static Result invalid(String reason) {
            return new Result(false, reason);
        }
    }
}
