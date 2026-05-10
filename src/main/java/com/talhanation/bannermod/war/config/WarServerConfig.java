package com.talhanation.bannermod.war.config;

import com.talhanation.bannermod.war.cooldown.WarCooldownPolicy;
import com.talhanation.bannermod.war.runtime.BattleWindow;
import com.talhanation.bannermod.war.runtime.BattleWindowSchedule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class WarServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SERVER;

    public static final ModConfigSpec.BooleanValue RegulatedPvpEnabled;
    public static final ModConfigSpec.ConfigValue<List<String>> BattleWindows;
    public static final ModConfigSpec.IntValue PeaceCooldownDays;
    public static final ModConfigSpec.IntValue DefenderDailyDeclarations;
    public static final ModConfigSpec.IntValue DefaultSiegeRadius;
    public static final ModConfigSpec.IntValue MinDeclarationDelayTicks;
    public static final ModConfigSpec.IntValue LostTerritoryImmunityDays;
    public static final ModConfigSpec.IntValue PeacefulToggleCooldownDays;
    public static final ModConfigSpec.BooleanValue SiegeProtectionAttackersExplosivesOnly;
    public static final ModConfigSpec.IntValue OccupationTaxAmountPerChunk;
    public static final ModConfigSpec.IntValue OccupationTaxIntervalDays;
    public static final ModConfigSpec.IntValue TributeTreatyIntervalDays;

    static {
        RegulatedPvpEnabled = BUILDER.comment("""
                        Master switch for the regulated warfare-RP PvP gate.
                        \tWhen false, the WarPvpGate is bypassed and damage is decided by other rules.
                        \tdefault: true""")
                .define("RegulatedPvpEnabled", true);

        BattleWindows = BUILDER.comment("""
                        Battle windows when war-PvP and siege progression activate.
                        \tFormat per entry: DAY,HH:MM,HH:MM (server local time).
                        \tDefault windows: WEDNESDAY 19:00-20:30, FRIDAY 19:00-20:30, SUNDAY 18:00-19:30.""")
                .define("BattleWindows", List.of(
                        "WEDNESDAY,19:00,20:30",
                        "FRIDAY,19:00,20:30",
                        "SUNDAY,18:00,19:30"
                ), value -> value instanceof List<?>);

        PeaceCooldownDays = BUILDER.comment("""
                        Days of forced peace between two states after a war is concluded or cancelled.
                        \tdefault: 7""")
                .defineInRange("PeaceCooldownDays", 7, 0, 365);

        DefenderDailyDeclarations = BUILDER.comment("""
                        Maximum number of offensive wars a single defender can receive per real day.
                        \tdefault: 1""")
                .defineInRange("DefenderDailyDeclarations", 1, 1, 100);

        DefaultSiegeRadius = BUILDER.comment("""
                        Default radius (blocks) of a Siege Standard war zone.
                        \tdefault: 64""")
                .defineInRange("DefaultSiegeRadius", 64, 8, 512);

        MinDeclarationDelayTicks = BUILDER.comment("""
                        Minimum delay (ticks) between declaration and earliest activation.
                        \tdefault: 6000 (5 minutes at 20 TPS)""")
                .defineInRange("MinDeclarationDelayTicks", 6000, 0, Integer.MAX_VALUE);

        LostTerritoryImmunityDays = BUILDER.comment("""
                        Days of forced immunity granted to a state that just lost territory through
                        \ttribute, vassalage, demilitarization, or annexation.
                        \tdefault: 3""")
                .defineInRange("LostTerritoryImmunityDays", 3, 0, 365);

        PeacefulToggleCooldownDays = BUILDER.comment("""
                        Days of cooldown enforced after a state toggles its PEACEFUL status. Prevents
                        \tabusive flip-flopping between peaceful and combat-eligible status.
                        \tdefault: 2""")
                .defineInRange("PeacefulToggleCooldownDays", 2, 0, 365);

        SiegeProtectionAttackersExplosivesOnly = BUILDER.comment("""
                        When true, claims under active siege block manual block-breaking, placement,
                        \tand interaction by non-friendly players (attackers). Explosions and siege
                        \tmachines bypass these checks naturally because they do not fire the
                        \tplayer-driven block events. Defenders and the claim owner are unaffected.
                        \tdefault: true""")
                .define("SiegeProtectionAttackersExplosivesOnly", true);

        OccupationTaxAmountPerChunk = BUILDER.comment("""
                        Coins debited per occupied chunk per OccupationTaxIntervalDays. The amount
                        \tis pulled from the defender's claim treasuries and credited to the
                        \toccupier's first-owned claim ledger. Set to 0 to disable occupation tax.
                        \tdefault: 5""")
                .defineInRange("OccupationTaxAmountPerChunk", 5, 0, 100_000);

        OccupationTaxIntervalDays = BUILDER.comment("""
                        Real-day cadence between occupation tax cycles. Each occupation accrues at
                        \tmost one cycle per call so a long server pause catches up gradually.
                        \tSet to 0 to disable occupation tax.
                        \tdefault: 1""")
                .defineInRange("OccupationTaxIntervalDays", 1, 0, 365);

        TributeTreatyIntervalDays = BUILDER.comment("""
                        Real-day cadence between ongoing tribute treaty payments created by the
                        \ttribute war outcome. Set to 0 to keep only the one-shot outcome payment.
                        \tdefault: 1""")
                .defineInRange("TributeTreatyIntervalDays", 1, 0, 365);

        SERVER = BUILDER.build();
    }

    public static long peaceCooldownTicks() {
        return Math.max(0L, PeaceCooldownDays.get().longValue()) * WarCooldownPolicy.TICKS_PER_DAY;
    }

    public static long lostTerritoryImmunityTicks() {
        return Math.max(0L, LostTerritoryImmunityDays.get().longValue()) * WarCooldownPolicy.TICKS_PER_DAY;
    }

    public static long peacefulToggleCooldownTicks() {
        return Math.max(0L, PeacefulToggleCooldownDays.get().longValue()) * WarCooldownPolicy.TICKS_PER_DAY;
    }

    public static long occupationTaxIntervalTicks() {
        return Math.max(0L, OccupationTaxIntervalDays.get().longValue()) * WarCooldownPolicy.TICKS_PER_DAY;
    }

    public static long tributeTreatyIntervalTicks() {
        return Math.max(0L, TributeTreatyIntervalDays.get().longValue()) * WarCooldownPolicy.TICKS_PER_DAY;
    }

    public static BattleWindowSchedule resolveSchedule() {
        List<BattleWindow> windows = new ArrayList<>();
        for (String entry : BattleWindows.get()) {
            BattleWindow window = parseWindow(entry);
            if (window != null) {
                windows.add(window);
            }
        }
        if (windows.isEmpty()) {
            return BattleWindowSchedule.defaultSchedule();
        }
        return new BattleWindowSchedule(windows);
    }

    private static BattleWindow parseWindow(String entry) {
        if (entry == null) {
            return null;
        }
        String[] parts = entry.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            DayOfWeek day = DayOfWeek.valueOf(parts[0].trim().toUpperCase(java.util.Locale.ROOT));
            LocalTime start = LocalTime.parse(parts[1].trim());
            LocalTime end = LocalTime.parse(parts[2].trim());
            return new BattleWindow(day, start, end);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return null;
        }
    }
}
