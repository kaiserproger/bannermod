# Developer Status

Last updated: 2026-04-28.

## Runtime State

- Active mod id: `bannermod`.
- Active source tree: root `src/**` under `com.talhanation.bannermod`.
- Archive source trees: `recruits/` and `workers/`; use them only as references.
- Active planning root: `.planning/`.
- Active backlog: `docs/BANNERMOD_BACKLOG.json` via `tools/backlog`.

## Done Recently

- Compact Phase 25 settlement runtime is live enough to publish and persist work-order claims, growth/project hints, job scheduling gates, market/stockpile snapshots, and targeted mutation refreshes.
- Compact Phase 26 combat AI is live: stance control, shield-wall behavior, reach weapons, second-rank poke, flank/cohesion/brace rules, unit counters, and Better Combat metadata/attack-presentation integration.
- War Room and political UI now cover political entity list/detail actions, siege-standard placement, siege-zone HUD status, government form toggles, cooldown-backed war spam protection, a synced battle-window phase banner with humanized open/close countdown, and a consent-based ally invite flow (leader-or-op invite, leader accept/decline/cancel, picker filtered by the shared `WarAllyPolicy`).
- War runtime is partially live beyond declarations: outcome actions can create occupations/annexations/tribute/vassalization/demilitarization, occupation tax accrues from a server ticker, due revolts auto-resolve from objective presence during battle windows, and recruits can attack enemy siege standards or escort same-side standards.
- Worker/settlement claim binding is being normalized away from legacy faction IDs toward political-entity UUIDs and scoreboard team names.
- Manual validated farm, mine, lumber camp, and architect workshop buildings now create citizen profession vacancies and show the vacancy output during validation/inspection.
- Claim deletion packets now resolve and delete only persisted server-side claims after the same owner/co-leader/admin authority check used by claim updates.
- Face, ranged-fire, and upkeep military packets now use the real server sender UUID for authority instead of client-supplied owner UUID fields.
- Leader group and companion group assignment packet paths now require direct recruit control and owned groups before mutating leader/group state.
- Claim protection is explicitly Overworld-only; Nether/End chunks at matching X/Z no longer inherit Overworld claim permissions.
- War/state client mirrors clear on login/logout, expose sync-pending state, and open War Room/state screens refresh automatically when snapshots change.
- Player login now sends full claim state only to the joining player; remaining full-claim broadcasts build one sync packet per event instead of one per recipient.
- War-state sync now uses dirty/version tracking and cached payloads instead of serializing all war records every idle second for hash polling.
- True-async pathfinding now checks scheduler capacity before snapshot capture and rejects over-budget snapshots with profiling counters.
- GameTests are being hardened around claim worker growth, dedicated-server ownership, player cycle, settlement degradation, and upkeep currency sourcing.

## Known Open Areas

- Full settlement onboarding remains incomplete: a new player still needs clearer starter-fort/town-hall, surveyor, wand, citizen, and profession guidance in-game.
- Worker AI still has legacy `current*Area` fields in live worker goals; `ValidatedBuildingRecord` should become the authoritative assignment source.
- Sea-trade production/consumption is documented as hints, but not yet a full gameplay loop.
- Remaining war gaps are depth/coverage gaps, not absent systems: some outcomes are still command/admin-heavy, occupation control is lighter than a full governance loop, revolt resolution depends on objective presence rather than a richer objective campaign, siege-standard AI is basic attack/escort behavior, and morale plus ranged-backline polish still need follow-up.
- `verifyGameTestStage` last had a known failure around `reconnectedownerrecoversauthorityafterownershiproundtrip` in a Better Combat-present smoke; do not claim full GameTest green until rerun successfully.

## Live Code References For War Status

- Allies: `src/main/java/com/talhanation/bannermod/war/runtime/WarAllyService.java`, `src/main/java/com/talhanation/bannermod/commands/war/WarAllyCommands.java`, `src/main/java/com/talhanation/bannermod/network/messages/war/MessageInviteAlly.java`, `src/main/java/com/talhanation/bannermod/network/messages/war/MessageRespondAllyInvite.java`, and `src/main/java/com/talhanation/bannermod/client/military/gui/war/WarAlliesScreen.java`.
- Occupations/outcomes/tax: `src/main/java/com/talhanation/bannermod/war/runtime/WarOutcomeApplier.java`, `src/main/java/com/talhanation/bannermod/war/runtime/OccupationRuntime.java`, `src/main/java/com/talhanation/bannermod/war/runtime/OccupationTaxRuntime.java`, `src/main/java/com/talhanation/bannermod/war/events/WarOccupationTaxTicker.java`, and `src/main/java/com/talhanation/bannermod/network/messages/war/MessageResolveWarOutcome.java`.
- Revolts: `src/main/java/com/talhanation/bannermod/war/runtime/WarRevoltScheduler.java`, `src/main/java/com/talhanation/bannermod/war/events/WarRevoltAutoResolver.java`, `src/main/java/com/talhanation/bannermod/war/runtime/ServerLevelObjectivePresenceProbe.java`, and `src/main/java/com/talhanation/bannermod/war/runtime/RevoltRuntime.java`.
- Siege objective AI: `src/main/java/com/talhanation/bannermod/ai/military/RecruitSiegeObjectiveAttackGoal.java`, `src/main/java/com/talhanation/bannermod/ai/military/RecruitSiegeEscortGoal.java`, `src/main/java/com/talhanation/bannermod/combat/SiegeObjectivePolicy.java`, and `src/main/java/com/talhanation/bannermod/war/runtime/SiegeStandardBlock.java`.

## Working Tree Warning

This repository is often used by multiple agents. Always run `tools/ai-context-proxy/bin/ctx status` before assuming a clean baseline, and do not revert unrelated changes.
