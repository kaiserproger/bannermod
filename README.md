# BannerMod

BannerMod is a multiplayer kingdom sandbox for Minecraft Forge 1.20.1. It merges the old Villager Recruits and Workers ideas into one live mod: settlements, workers, claims, political states, armies, formations, logistics, trade, sieges, and regulated wars all belong to the same gameplay loop.

The active mod lives in the root `src/**` tree under the `bannermod` runtime. The old `recruits/` and `workers/` folders are archive/reference trees only.

## Start Here

- Player/server guides: `MULTIPLAYER_GUIDE_RU.md`, `MULTIPLAYER_GUIDE_EN.md`
- Documentation index: `docs/README.md`
- Current developer status: `docs/STATUS.md`
- Contribution flow: `docs/CONTRIBUTING.md`
- Canonical unfinished-work queue: `docs/BANNERMOD_BACKLOG.sqlite` via `tools/backlog`

## Current Gameplay Shape

BannerMod is playable in pieces and still under active stabilization. The current runtime already has:

- political entities with settlement/state/vassal/peaceful status;
- claim ownership tied to political sides;
- settlement snapshots for residents, buildings, stockpile, market, trade, projects, jobs, and work orders;
- persisted work-order claims and transport payloads for hauling/fetch execution;
- infrastructure gating before a settlement can be promoted into a state;
- War Room UI for active wars, political entities, siege standards, and siege-zone HUD status;
- government-form editing for monarchy/republic authority differences;
- recruit formation stance control, shield-wall behavior, reach weapons, flanking, cohesion, brace, and counter rules.

Important unfinished areas remain in `docs/BANNERMOD_BACKLOG.sqlite`; use `tools/backlog batch --limit 5` instead of reading the database directly.

## Build

```bash
./gradlew compileJava
./gradlew test
```

Use `./gradlew verifyGameTestStage` when changing gameplay wiring, ownership, AI, networking, persistence, or multiplayer behavior.

## Repository Truth

- Active source: `src/**`
- Active mod id: `bannermod`
- Active planning: `.planning/`
- Active backlog: `docs/BANNERMOD_BACKLOG.sqlite` via `tools/backlog`
- Archive/reference source trees: `recruits/`, `workers/`

Do not revive old duplicate faction, diplomacy, worker, or siege systems as parallel gameplay. If code and old planning notes disagree, trust the live code and update the backlog or docs instead of adding compatibility layers without a concrete need.
