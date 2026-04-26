package com.talhanation.bannermod.war.runtime;

/** Which side of a {@link WarDeclarationRecord} an action targets. */
public enum WarSide {
    ATTACKER,
    DEFENDER;

    public static WarSide parse(String token) {
        if (token == null) return null;
        try {
            return WarSide.valueOf(token.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
