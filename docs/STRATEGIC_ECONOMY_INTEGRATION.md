# Strategic Economy And VenaTerra Integration

Last updated: 2026-05-10.

## Purpose

This document captures the non-obvious integration facts discovered and implemented during the strategic economy second-act work. It is developer-facing context for BannerMod agents and humans working on `VEN-*`, `ECON-*`, `POL-*`, `WAR-*`, and `UI-*` backlog tasks.

## Current Branch State

- Active BannerMod feature branch for this work: `feature/strategic-economy-second-act`.
- Landed BannerMod commits in that branch:
  - `c8e4c1e5 feat: add strategic economy summary and VenaTerra bridge`
  - `e7e32b50 feat: add strategic mine site records`
  - `d75739d4 feat: add strategic mine yield hints`
- VenaTerra changes were made in `/home/kaiserroman/venaterra`, which is not a git repository in this workspace. Treat those changes as cross-repo workspace state until VenaTerra gets its own VCS/patch flow.

## Backlog State

Completed in this session:

- `VEN-001` - public VenaTerra deposit survey API.
- `VEN-002` - strategic ore bucket metadata.
- `ECON-020` - optional BannerMod VenaTerra bridge.
- `ECON-021` - claim strategic economy summary.
- `ECON-022` - strategic mine site records.
- `ECON-023` - mine yield cycle into economy summary hints.
- `ECON-024` - mine maintenance and soft depletion pressure.
- `ECON-025` - fort level state, caps, and next-level requirements.
- `ECON-029` - server-side NPC demand contract records.
- `ECON-031` - server-authoritative strategic resource debit/credit pipeline.

Use `tools/backlog ready <N>` for the next executable batch after these closures.

Use `tools/backlog show <ID>` for canonical acceptance. Do not copy task text out of planning docs.

## VenaTerra Workspace Facts

- VenaTerra lives at `/home/kaiserroman/venaterra`.
- It is a custom mod, not an archive tree under BannerMod.
- It is not a git repository in this workspace.
- It builds with its own Gradle wrapper.
- Useful existing VenaTerra internals before the public API:
  - `ru.kaiserroman.venaterra.deposit.OreDepositLookup.findMatches(ServerLevel, BlockPos)` finds deposits affecting one position.
  - `OreDepositLookup.findNearby(ServerLevel, BlockPos, int)` performs prospector-style nearby scans.
  - `OreDepositSavedData.getOrCreateRegion(...)` generates/migrates regions and calls `setDirty()`; do not use it from BannerMod compat read paths.
  - `OreDepositSavedData.consumeGeneratedOre(...)` is destructive and is used by the ore drop path; never use it for strategic surveying.
  - `OreDepositGenerator.generate(...)` can build transient deterministic regions without saving them.
  - `DepositMath.densityAt(...)`, `surveyScore(...)`, and `horizontalDistanceSquared(...)` are the reusable scoring helpers.
  - `ProspectorToolItem` and `/realisticores debug here|scan` are existing examples of deposit consumers.

## VenaTerra Public API Contract

The public API added for BannerMod lives under `ru.kaiserroman.venaterra.api`:

- `VenaterraDepositApi.findDepositsAt(ServerLevel, BlockPos)`.
- `VenaterraDepositApi.findDepositsNear(ServerLevel, BlockPos, int)`.
- `VenaterraDepositApi.findDepositsInArea(ServerLevel, BlockPos, int)`.
- `DepositSurveyResult` exposes `oreId`, `dropItemId`, `center`, `radius`, `coreRadius`, `halfHeight`, `richness`, `chance`, `minY`, `maxY`, `dimension`, and `category`.
- `StrategicOreCategory` values are `IRON`, `INDUSTRIAL_FUEL`, `PRECIOUS_COIN_VALUE`, `QUARRY_STONE`, and `UNKNOWN_OTHER`.
- `StrategicOreClassifier` maps vanilla iron, coal/fuel, gold/copper value, quarry/stone materials, and unknown/custom ores into the strategic categories.

Important API invariants:

- The public survey API must stay read-only with respect to saved deposit data.
- The API uses transient `OreDepositGenerator.generate(...)` output, not `OreDepositLookup` or `OreDepositSavedData`.
- The API must never call `consumeGeneratedOre(...)`.
- Ignored dimensions return an empty result.
- `DepositSurveyResult.center()` defensively stores an immutable `BlockPos`.
- `redstone` and `glowstone` must not become `QUARRY_STONE` merely because their names contain `stone`.
- The current structural test guards that the API source does not reference `OreDepositLookup` or `OreDepositSavedData`; this is useful but not a substitute for a future runtime saved-data mutation test.

## BannerMod Optional VenaTerra Bridge

BannerMod does not hard-depend on VenaTerra. The bridge lives under `com.talhanation.bannermod.compat.venaterra`:

- `VenaterraDepositProvider.create()` checks `ModList` for mod id `venaterra` and resolves the VenaTerra API reflectively.
- Missing mod, missing API classes, malformed result records, and API call failures degrade to an empty provider or skipped result instead of crashing. Unknown/future category enum names map to `UNKNOWN_OTHER` candidates.
- `findClaimDeposits(ServerLevel, RecruitsClaim)` queries a claim bounding area, then filters candidates by exact claimed `ChunkPos`, not only by bounding rectangle.
- `VenaterraDepositCandidate` carries `category`, `oreId`, `dropItemId`, immutable `center`, `richness`, `confidence`, and `SourceMetadata`.
- `VenaterraDepositCategory.fromApiCategory(...)` maps unknown/future VenaTerra enum names to `UNKNOWN_OTHER`.

## Claim Strategic Economy Summary

The claim summary read model lives in `com.talhanation.bannermod.settlement.economy`:

- `ClaimStrategicEconomySummary` contains one `ResourceLine` each for `food`, `iron`, `wood`, `stone`, and `coins`.
- Each resource line carries `stockpileHint`, `capacityHint`, `productionHint`, `consumptionHint`, `shortage`, `degraded`, and `unknown`.
- `ClaimStrategicEconomySummaryService.derive(snapshot, validatedBuildings, treasuryLedger)` keeps the original building-derived behavior.
- `ClaimStrategicEconomySummaryService.derive(snapshot, validatedBuildings, treasuryLedger, mineSites)` includes strategic mine yield hints.
- `ClaimStrategicEconomySummaryService.derive(snapshot, validatedBuildings, treasuryLedger, mineSites, fortLevel)` also attaches fort level/caps/requirements to debug/status output.
- Stale, missing, invalid, or conflicting building/work-area data should produce degraded/unknown credit, not full production.
- When strategic mine sites are supplied, generic mine-like building iron contribution is suppressed. This prevents quarry or precious mines from also producing phantom iron.

## Strategic Mine Site Model

Strategic mine site records also live under `com.talhanation.bannermod.settlement.economy`:

- `StrategicMineSite` binds a mine to `siteId`, `claimUuid`, owner political entity id, dimension, center, radius, source type, VenaTerra category, richness, assigned worker count, degraded flag, and unknown flag.
- Source types are `VALIDATED_MINE_BUILDING` and `CLAIM_MINE_WORK_AREA`.
- `StrategicMineSiteService` derives sites from valid `BuildingType.MINE` records and mine-like snapshot records such as `mine` or `mining_area`.
- Invalid validated mine records are remembered as known mine ids, so they do not reappear through snapshot fallback.
- Validated mines are matched against overlapping live mine work-area records when present, so live worker counts are used and the same mine does not double-count as both validated and snapshot fallback.
- VenaTerra candidates are matched by dimension and horizontal distance. The nearest reliable candidate wins; confidence and richness are tie-breakers.
- Unknown or unsupported candidates produce a degraded/unknown site with no inflated yield.

## Mine Yield Hints

`ECON-023` and `ECON-024` intentionally write mine output into strategic economy summary production hints only. They do not mutate storage, treasury, or item stacks.

- `IRON` mine sites add iron production hints.
- `QUARRY_STONE` mine sites add stone production hints.
- `PRECIOUS_COIN_VALUE` mine sites add coin/value production hints.
- Known staffed valid mines produce `1..3` hint units based on richness.
- Unstaffed mines, missing food, missing wood supports, or missing tools move the relevant resource line into degraded state and cap hint output at low efficiency.
- Reserved `mine_disruption`/`disruption` supply-signal units reduce yield without deleting the fort or mine record. This is a read-only hook for later contested/object-control work.
- Unknown mines add no production and mark the relevant summary state as degraded/unknown.
- Removed or invalidated mine sites stop contributing on the next derivation.

War/object-control rules are still out of scope for this layer; those belong to later `WAR-*` tasks.

## Fort Levels And Strategic Caps

`ECON-025` adds persisted claim fort level state:

- `RecruitsClaim` persists `fortLevel`, clamped to `1..4`.
- `FortLevelDefinition` defines beta-table worker, soldier, mine, and outpost caps plus next-level requirements in `food`, `iron`, `wood`, `stone`, and `coins`.
- `/bannermod debug economy claim <claimUuid>` prints fort level, caps, and next-level requirements.
- This slice defines and exposes caps; enforcement at worker/recruit/mine/outpost creation points remains future gameplay wiring.

## NPC Demand Contracts

`ECON-029` adds server-side NPC demand contract records:

- `NpcDemandContract` records buyer, resource bucket, requested item ids, amount, created tick, deadline tick, reward coins, and status.
- `NpcDemandContractSavedData` persists open/expired contracts and next eligible spawn ticks.
- `NpcDemandContractService` ticks per settlement snapshot; isolated forts can receive contracts, while markets and trading posts improve cadence and quality.
- `/bannermod debug economy contracts <claimUuid>` and `/bannermod economy contracts <claimUuid>` expose server-backed status output without a contract-board UI.

## Strategic Resource Accounting

`ECON-031` adds the server-authoritative debit/credit backend:

- `StrategicResourceBucket` covers `food`, `iron`, `wood`, `stone`, and `coins`.
- `StrategicResourceAccountingManager` persists non-coin strategic account balances by claim.
- `StrategicResourceAccountingService` routes coin credit/debit through `BannerModTreasuryManager` and non-coin buckets through settlement-owned strategic accounting.
- Failed debits return structured `SHORTAGE` or `SOURCE_UNAVAILABLE` results without changing balances.
- There are no client packet entry points; callers must provide `ServerLevel`, claim id, bucket, amount, and server tick.

## Admin Debug Surface

The current operator-facing verification paths are in `AdminDebugCommands`:

- `/bannermod debug economy claim <claimUuid>` prints the claim strategic economy summary and includes strategic mine yield when a claim can be resolved.
- `/bannermod debug economy mines <claimUuid>` prints strategic mine site debug lines.
- `/bannermod debug economy contracts <claimUuid>` prints NPC demand contract debug lines.
- `/bannermod economy contracts <claimUuid>` prints the server-backed contract status path outside the admin debug tree.

These are command/status paths, not full player-facing UI. Player-facing screens are deferred to `UI-020` through `UI-023`.

## Verification Commands Used

VenaTerra:

- `./gradlew test` with working directory `/home/kaiserroman/venaterra`.
- `./gradlew compileJava` with working directory `/home/kaiserroman/venaterra`.

BannerMod focused gates:

- `./gradlew test --tests com.talhanation.bannermod.compat.venaterra.VenaterraDepositProviderTest`
- `./gradlew test --tests com.talhanation.bannermod.settlement.economy.ClaimStrategicEconomySummaryServiceTest`
- `./gradlew test --tests com.talhanation.bannermod.settlement.economy.StrategicMineSiteServiceTest`
- `./gradlew test --tests com.talhanation.bannermod.settlement.economy.FortLevelDefinitionTest`
- `./gradlew test --tests com.talhanation.bannermod.settlement.economy.NpcDemandContractServiceTest`
- `./gradlew test --tests com.talhanation.bannermod.settlement.economy.StrategicResourceAccountingServiceTest`
- Combined focused runs for the tests above.
- `./gradlew compileJava`
- `tools/backlog validate`

Known verification gap:

- No in-game execution of `/bannermod debug economy claim <claimUuid>`, `/bannermod debug economy mines <claimUuid>`, `/bannermod debug economy contracts <claimUuid>`, or `/bannermod economy contracts <claimUuid>` has been recorded yet.
- No runtime test with a real loaded VenaTerra jar on BannerMod classpath has been recorded yet; BannerMod bridge tests use reflective fakes.
- No live world save/reload smoke has been recorded for NPC demand contracts or strategic resource accounting; focused NBT/unit coverage exists.
- Full test suite was not run during this session.
