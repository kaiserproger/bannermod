# Developer Status

Last updated: 2026-04-26.

## Runtime State

- Active mod id: `bannermod`.
- Active source tree: root `src/**` under `com.talhanation.bannermod`.
- Archive source trees: `recruits/` and `workers/`; use them only as references.
- Active planning root: `.planning/`.
- Active backlog: `docs/BANNERMOD_BACKLOG.md`.

## Done Recently

- Compact Phase 25 settlement runtime is live enough to publish and persist work-order claims, growth/project hints, job scheduling gates, market/stockpile snapshots, and targeted mutation refreshes.
- Compact Phase 26 combat AI is live: stance control, shield-wall behavior, reach weapons, second-rank poke, flank/cohesion/brace rules, unit counters, and Better Combat metadata/attack-presentation integration.
- War Room and political UI now cover political entity list/detail actions, siege-standard placement, siege-zone HUD status, government form toggles, cooldown-backed war spam protection, a synced battle-window phase banner with humanized open/close countdown, and a consent-based ally invite flow (leader-or-op invite, leader accept/decline/cancel, picker filtered by the shared `WarAllyPolicy`).
- Worker/settlement claim binding is being normalized away from legacy faction IDs toward political-entity UUIDs and scoreboard team names.
- GameTests are being hardened around claim worker growth, dedicated-server ownership, player cycle, settlement degradation, and upkeep currency sourcing.

## Known Open Areas

- Full settlement onboarding remains incomplete: a new player still needs clearer starter-fort/town-hall, surveyor, wand, citizen, and profession guidance in-game.
- Worker AI still has legacy `current*Area` fields in live worker goals; `ValidatedBuildingRecord` should become the authoritative assignment source.
- Sea-trade production/consumption is documented as hints, but not yet a full gameplay loop.
- War outcomes, occupation tax/control, objective-based revolts, allies, morale, cavalry, ranged backline behavior, and siege objective AI remain backlog items.
- `verifyGameTestStage` last had a known failure around `reconnectedownerrecoversauthorityafterownershiproundtrip` in a Better Combat-present smoke; do not claim full GameTest green until rerun successfully.

## Working Tree Warning

This repository is often used by multiple agents. Always run `tools/ai-context-proxy/bin/ctx status` before assuming a clean baseline, and do not revert unrelated changes.
