package com.talhanation.bannermod.war.runtime;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarPvpGateTest {

    private static WarDeclarationRecord war(UUID attacker, UUID defender, WarState state) {
        return new WarDeclarationRecord(
                UUID.randomUUID(),
                attacker,
                defender,
                WarGoalType.WHITE_PEACE,
                "",
                List.of(),
                List.of(),
                List.of(),
                0L,
                0L,
                state
        );
    }

    private static WarDeclarationRecord warWithAllies(UUID attacker,
                                                      UUID defender,
                                                      List<UUID> attackerAllies,
                                                      List<UUID> defenderAllies,
                                                      WarState state) {
        return new WarDeclarationRecord(
                UUID.randomUUID(),
                attacker,
                defender,
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

    private static BattleWindowSchedule openSchedule() {
        return new BattleWindowSchedule(List.of(
                new BattleWindow(DayOfWeek.WEDNESDAY, LocalTime.of(0, 0), LocalTime.of(23, 59))));
    }

    private static ZonedDateTime aWednesday() {
        // 2026-04-29 is a Wednesday
        return LocalDateTime.of(LocalDate.of(2026, 4, 29), LocalTime.of(12, 0))
                .atZone(ZoneOffset.UTC);
    }

    private static ZonedDateTime aMonday() {
        return LocalDateTime.of(LocalDate.of(2026, 4, 27), LocalTime.of(12, 0))
                .atZone(ZoneOffset.UTC);
    }

    @Test
    void blocksOutsideBattleWindow() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        boolean ok = WarPvpGate.allowsWarPvp(a, b, List.of(war(a, b, WarState.DECLARED)),
                openSchedule(), aMonday(), true);
        assertFalse(ok);
    }

    @Test
    void blocksWhenSelfTargeted() {
        UUID a = UUID.randomUUID();
        boolean ok = WarPvpGate.allowsWarPvp(a, a, List.of(war(a, a, WarState.DECLARED)),
                openSchedule(), aWednesday(), true);
        assertFalse(ok);
    }

    @Test
    void blocksWhenNotInsideWarZone() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        boolean ok = WarPvpGate.allowsWarPvp(a, b, List.of(war(a, b, WarState.DECLARED)),
                openSchedule(), aWednesday(), false);
        assertFalse(ok);
    }

    @Test
    void blocksWhenWarResolved() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        boolean ok = WarPvpGate.allowsWarPvp(a, b, List.of(war(a, b, WarState.RESOLVED)),
                openSchedule(), aWednesday(), true);
        assertFalse(ok);
    }

    @Test
    void allowsWhenAllConditionsMet() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        boolean ok = WarPvpGate.allowsWarPvp(a, b, List.of(war(a, b, WarState.DECLARED)),
                openSchedule(), aWednesday(), true);
        assertTrue(ok);
    }

    @Test
    void blocksWhenNoWarBetweenSides() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        boolean ok = WarPvpGate.allowsWarPvp(a, b, List.of(war(a, c, WarState.DECLARED)),
                openSchedule(), aWednesday(), true);
        assertFalse(ok);
    }

    @Test
    void allowsWhenAttackerAllyHitsDefenderMain() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID attackerAlly = UUID.randomUUID();
        WarDeclarationRecord record = warWithAllies(attacker, defender,
                List.of(attackerAlly), List.of(), WarState.DECLARED);
        boolean ok = WarPvpGate.allowsWarPvp(attackerAlly, defender, List.of(record),
                openSchedule(), aWednesday(), true);
        assertTrue(ok);
    }

    @Test
    void allowsWhenDefenderAllyHitsAttackerMain() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID defenderAlly = UUID.randomUUID();
        WarDeclarationRecord record = warWithAllies(attacker, defender,
                List.of(), List.of(defenderAlly), WarState.DECLARED);
        boolean ok = WarPvpGate.allowsWarPvp(attacker, defenderAlly, List.of(record),
                openSchedule(), aWednesday(), true);
        assertTrue(ok);
    }

    @Test
    void allowsWhenAttackerAllyHitsDefenderAlly() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID attackerAlly = UUID.randomUUID();
        UUID defenderAlly = UUID.randomUUID();
        WarDeclarationRecord record = warWithAllies(attacker, defender,
                List.of(attackerAlly), List.of(defenderAlly), WarState.DECLARED);
        boolean ok = WarPvpGate.allowsWarPvp(attackerAlly, defenderAlly, List.of(record),
                openSchedule(), aWednesday(), true);
        assertTrue(ok);
    }
}
