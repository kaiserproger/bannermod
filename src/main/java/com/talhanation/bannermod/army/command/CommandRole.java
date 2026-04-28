package com.talhanation.bannermod.army.command;

public enum CommandRole {
    /** No recruit command authority. Nation-level authority is explicitly unsupported. */
    NONE,
    /** The player and recruit are on the same server scoreboard team. */
    TEAMMATE,
    /** The player owns the recruit. */
    OWNER,
    /** The player has server operator command permissions. */
    ADMIN
}
