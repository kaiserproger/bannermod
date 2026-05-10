# Developer Status

Last updated: 2026-04-28.

## Runtime State

- Active mod id: `bannermod`.
- Active source tree: root `src/**` under `com.talhanation.bannermod`.
- Archive source trees: `recruits/` and `workers/`; use them only as references.
- Active planning root: `.planning/`.
- Active backlog: `docs/BANNERMOD_BACKLOG.sqlite` via `tools/backlog`.

## Done Recently

- Compact Phase 25 settlement runtime is live enough to publish and persist work-order claims, growth/project hints, job scheduling gates, market/stockpile snapshots, and targeted mutation refreshes.
- Compact Phase 26 combat AI is live: stance control, shield-wall behavior, reach weapons, second-rank poke, flank/cohesion/brace rules, unit counters, and Better Combat metadata/attack-presentation integration.
- War Room and political UI now cover political entity list/detail actions, siege-standard placement, siege-zone HUD status, government form toggles, cooldown-backed war spam protection, a synced battle-window phase banner with humanized open/close countdown, and a consent-based ally invite flow (leader-or-op invite, leader accept/decline/cancel, picker filtered by the shared `WarAllyPolicy`).
- War runtime is partially live beyond declarations: outcome actions can create occupations/annexations/tribute/vassalization/demilitarization, occupation tax accrues from a server ticker, due revolts auto-resolve from objective presence during battle windows, and recruits can attack enemy siege standards or escort same-side standards.
- Worker/settlement claim binding is being normalized away from legacy faction IDs toward political-entity UUIDs and scoreboard team names.
- Manual validated farm, mine, lumber camp, and architect workshop buildings now create citizen profession vacancies and show the vacancy output during validation/inspection.
- Civilian work-area editors now render a Minecraft-native panel layout, show top-right sync/owner state, and surface missing seeds, saplings, and tunnel-setting hints directly in the work-area screens.
- Political-entity rename/co-leader dialogs now accept typed input again, and starter-fort founding can create the first anchor claim automatically for the founding state when no claim exists yet.
- Claims now support a trusted-members list for shared settlement access: trusted players get claim-friendly block/build/work-area access and a localized management UI, but do not gain co-leader political authority.
- Settlement survey now explains zone purpose and expected build contents in-screen/HUD, renders role labels over hologram guide boxes, and can pin the anchored hologram client-side after the tool is put away; local RenaissanceMuskets optional-runtime wiring now stages Connector/Fabric bridge jars and passes live musket GameTests under `runGameTestServer` when the runtime property is enabled.
- Remaining recruit, messenger, claim-HUD, and world-map UI surfaces now follow the Minecraft-native parchment/wood/iron style pass, keep visible action-state or denial feedback on primary controls, and verify leader plus co-leader claim-authority HUD behavior in focused UI tests.
- Claim deletion packets now resolve and delete only persisted server-side claims after the same owner/co-leader/admin authority check used by claim updates.
- Face, ranged-fire, and upkeep military packets now use the real server sender UUID for authority instead of client-supplied owner UUID fields.
- Leader group and companion group assignment packet paths now require direct recruit control and owned groups before mutating leader/group state.
- Claim protection is explicitly Overworld-only; Nether/End chunks at matching X/Z no longer inherit Overworld claim permissions.
- Denied claim-protected interactions now send localized, cooldown-limited system feedback for friendly locks, hostile protection, and unclaimed wilderness rules.
- War/state client mirrors clear on login/logout, expose sync-pending state, and open War Room/state screens refresh automatically when snapshots change.
- Player login now sends full claim state only to the joining player; remaining full-claim broadcasts build one sync packet per event instead of one per recipient.
- War-state sync now uses dirty/version tracking and cached payloads instead of serializing all war records every idle second for hash polling.
- True-async pathfinding now checks scheduler capacity before snapshot capture and rejects over-budget snapshots with profiling counters.
- GameTests are being hardened around claim worker growth, dedicated-server ownership, player cycle, settlement degradation, and upkeep currency sourcing.

## Known Open Areas

- Full settlement onboarding remains incomplete: a new player still needs clearer starter-fort/town-hall, surveyor, wand, citizen, and profession guidance in-game.
- Worker assignment binding is split between two sources of truth: the entity-side `currentWorkArea` (cached on `AbstractWorkerEntity`) and the registry-side `ValidatedBuildingRecord` in the settlement validated-building registry. The remaining gap is making `ValidatedBuildingRecord` the authoritative assignment source and reducing `currentWorkArea` to a derived runtime cache.
- Sea-trade production/consumption is documented as hints, but not yet a full gameplay loop.
- Remaining war gaps are depth/coverage gaps, not absent systems: some outcomes are still command/admin-heavy, occupation control is lighter than a full governance loop, revolt resolution depends on objective presence rather than a richer objective campaign, siege-standard AI is basic attack/escort behavior, and morale plus ranged-backline polish still need follow-up.
- `verifyGameTestStage` last had a known failure around `reconnectedOwnerRecoversAuthorityAfterOwnershipRoundTrip` in a Better Combat-present smoke; do not claim full GameTest green until rerun successfully.

## Live Code References For War Status

- Allies: `src/main/java/com/talhanation/bannermod/war/runtime/WarAllyService.java`, `src/main/java/com/talhanation/bannermod/commands/war/WarAllyCommands.java`, `src/main/java/com/talhanation/bannermod/network/messages/war/MessageInviteAlly.java`, `src/main/java/com/talhanation/bannermod/network/messages/war/MessageRespondAllyInvite.java`, and `src/main/java/com/talhanation/bannermod/client/military/gui/war/WarAlliesScreen.java`.
- Occupations/outcomes/tax: `src/main/java/com/talhanation/bannermod/war/runtime/WarOutcomeApplier.java`, `src/main/java/com/talhanation/bannermod/war/runtime/OccupationRuntime.java`, `src/main/java/com/talhanation/bannermod/war/runtime/OccupationTaxRuntime.java`, `src/main/java/com/talhanation/bannermod/war/events/WarOccupationTaxTicker.java`, and `src/main/java/com/talhanation/bannermod/network/messages/war/MessageResolveWarOutcome.java`.
- Revolts: `src/main/java/com/talhanation/bannermod/war/runtime/WarRevoltScheduler.java`, `src/main/java/com/talhanation/bannermod/war/events/WarRevoltAutoResolver.java`, `src/main/java/com/talhanation/bannermod/war/runtime/ServerLevelObjectivePresenceProbe.java`, and `src/main/java/com/talhanation/bannermod/war/runtime/RevoltRuntime.java`.
- Siege objective AI: `src/main/java/com/talhanation/bannermod/ai/military/RecruitSiegeObjectiveAttackGoal.java`, `src/main/java/com/talhanation/bannermod/ai/military/RecruitSiegeEscortGoal.java`, `src/main/java/com/talhanation/bannermod/combat/SiegeObjectivePolicy.java`, and `src/main/java/com/talhanation/bannermod/war/runtime/SiegeStandardBlock.java`.

## Working Tree Warning

This repository is often used by multiple agents. Always run `tools/ai-context-proxy/bin/ctx status` before assuming a clean baseline, and do not revert unrelated changes.
