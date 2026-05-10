# Phase 25 Strategic Economy: The Second Act

> Planning context, not an active task queue. The executable decomposition now lives in `docs/BANNERMOD_BACKLOG.sqlite`; use `tools/backlog show <ID>` for canonical scope and acceptance.

## Goal

Turn BannerMod's current first act into a repeatable strategy loop:

```text
Fort -> workers -> food/wood/stone -> smithy/market/barracks -> mine -> weapons/armor/money
  -> army -> raids/blockades/object control -> more income -> fort upgrades/status/new forts
```

The design target is not more generic PvP. The target is dependency: land produces resources, resources support power, power controls land, and other players can interrupt that chain without needing to destroy a main base.

## Current Foundation

BannerMod already has enough substrate to build this as slices rather than a rewrite:

- Claims, political entities, starter-fort founding, trusted members, and claim protection already define territorial ownership.
- Validated buildings already formalize farms, mines, lumber camps, smithies, storage, markets, barracks, and starter forts.
- Settlement snapshots already collect buildings, residents, stockpile summary, market state, desired goods, supply signals, and growth/project hints.
- Work orders already let farms, mines, lumber camps, build areas, fishing, animal pens, and storage transport publish operational tasks.
- Treasury ledgers, governor heartbeat accounting, army-upkeep debits, occupation tax, war declarations, siege standards, occupations, tribute outcomes, vassal status, revolts, and ally flow already exist.
- Strategic mines should integrate with the custom VenaTerra mod on disk at `/home/kaiserroman/venaterra`: it already owns realistic ore deposits through `OreDepositLookup`, `OreDeposit`, `OreDefinition`, `OreDepositSavedData`, prospector scans, and debug commands. BannerMod should consume a stable VenaTerra API/compat facade instead of duplicating deposit generation or reading VenaTerra internals directly.

The missing layer is one coherent strategic read model and rule set that turns these existing operational systems into scarcity, specialization, maintenance, and object-centered conflict.

## Design Principles

- One fort must not efficiently produce everything.
- Mines and resource sites must be strategic objects, not just prettier ore spots.
- Armies must be expensive enough that economy matters.
- Territory must produce income only while controlled, staffed, supplied, and not disrupted.
- War must have useful stages below total base destruction: raid, blockade, outpost capture, caravan robbery, contested mine, occupation, tribute.
- Money must be a required system input, not a cosmetic extra.
- Low online population must still have demand through NPC contracts.
- Implementation must stay claim-bound, server-authoritative, heartbeat/coarse-cycle driven, and additive over current Phase 24/25 systems.

## Strategic Resource Model

Use five first-class resources for beta balance:

| Resource | Primary meaning | Main sinks |
| --- | --- | --- |
| Food | Keeps workers and armies alive | worker upkeep, army upkeep, garrison readiness, settlement growth |
| Iron | Military and industrial backbone | weapons, armor, tools, heavy soldiers, fort repairs |
| Wood | Construction and mine support | buildings, outposts, roads, siege works, mine maintenance |
| Stone | Fortification and upgrades | walls, fort levels, towers, city upgrades |
| Coins | Liquid political power | wages, contracts, licenses, NPC orders, diplomacy, mercenaries |

Do not start with every ore, alloy, food type, and luxury good. Normalize physical items into these resource buckets in the strategic layer first. Add special goods later only when they create a new decision.

VenaTerra may expose richer geology metadata such as coal, copper, tin, bronze inputs, or custom ores. Phase 25 should preserve that metadata for future balance, but the MVP must map it into the five strategic buckets or `unknown/other` until a downstream task gives it a concrete sink.

## Strategic Objects

Objects should be explicit records or projections over existing validated buildings/claims before adding new block systems.

| Object | Gameplay meaning | Existing base to reuse |
| --- | --- | --- |
| Fort | Political center and authority radius | starter fort, claim, settlement snapshot, political entity |
| Mine | Military-industrial source and war target | VenaTerra deposits, `MINE`, `MiningArea`, work orders, claim ownership |
| Farm | Food and army-supply source | `FARM`, crop work orders, food stockpile |
| Lumber camp | Construction and mine-support source | `LUMBER_CAMP`, lumber work orders |
| Trading post/market | Coins, NPC demand, contracts | `MARKET`, `MarketArea`, merchant/seller dispatch |
| Storage/stockpile | Resource inventory and route endpoint | `StorageArea`, stockpile summary, logistics routes |
| Road/bridge | Supply-line/chokepoint target | logistics route endpoints first, authored route state later |
| Outpost | Small capturable control node | claim sub-object or siege-standard-like objective |

## Phase Ownership

This document defines the second-act gameplay program, but not every slice belongs to Phase 25.

| Owner | Responsibility in this plan |
| --- | --- |
| Phase 25 | Strategic economy read model, fort levels, resource caps, mine yield/maintenance, NPC contracts, economy-pressure inputs for existing upkeep, and governance/economy records for treaty payments if implemented before full war gating. |
| Phase 26 | Soft war objectives, object control, raid/blockade effects, and any recruit/combat behavior around those objectives. |
| Phase 27 | Settlement/War Room/contract-board UI and player operation surfaces over stable read models. |
| Phase 28 | Balance presets, telemetry, rollout controls, migration safety, and operator tuning. |

Phase 25 should therefore produce data and server-side rules that later phases can consume. It should not implement full war-objective campaigns, broad UI, or final rollout tooling inside the settlement economy slice.

## Current Authority Constraint

`docs/STATUS.md` still calls out a split source of truth between entity-side `currentWorkArea` and registry-side `ValidatedBuildingRecord`. Any strategic summary, cap, mine ownership, or worker assignment rule must handle that explicitly:

- Prefer `ValidatedBuildingRecord` and settlement snapshot data for strategic projections when available.
- Treat live entity/work-area state as operational evidence, not the long-term authority, until the assignment binding cleanup is complete.
- Mark stale, missing, or conflicting building/worker bindings as degraded/unknown instead of granting full production or cap credit.

## Implementation Slices

### Slice 1: Strategic Economy Read Model

Create a server-side, claim-keyed strategic projection derived from existing settlement snapshots and treasury ledgers.

Deliverables:

- Add a compact settlement economy summary with the five resource buckets, production hints, consumption hints, storage capacity, and shortage flags.
- Derive resource buckets from current stockpiles and known item tags where safe; fall back to building/profile signals when exact inventory is unavailable.
- Keep it read-model first: no new global economy manager, no per-tick simulation.
- Surface the summary through existing snapshot/debug/UI paths enough for verification.

Acceptance checks:

- A fort with farm/mine/lumber/storage buildings reports distinct food/iron/wood/stone/coin capacity or production signals.
- Removing or invalidating a building changes the next strategic summary.
- The projection remains claim-bound and survives reload if persisted fields are added.

### Slice 2: Settlement Specialization And Fort Levels

Make forts progress through clear levels and make local geography/building mix matter.

Proposed levels:

| Level | Name | Workers | Soldiers | Mines | Outposts | Meaning |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| 1 | Outpost/Zastava | 4 | 4 | 0-1 | 0 | foothold, starter economy |
| 2 | Fort | 8 | 10 | 1 | 1 | first military economy |
| 3 | Stronghold | 16 | 25 | 2 | 2 | regional power |
| 4 | Town/City | 32 | 50 | 3 | 4 | political center |

Deliverables:

- Add fort-level state to settlement/claim/political read model with upgrade requirements expressed in the five resources.
- Gate worker, soldier, mine, and outpost limits by fort level.
- Connect growth/project scoring to the next fort-level requirement instead of vague “more buildings”.
- Make upgrade cost consume from treasury/stockpile only through server-authoritative actions.

Acceptance checks:

- A starter fort has visible next-level requirements.
- A level blocks over-limit mines/outposts/recruit expansion with a clear denial reason.
- Upgrading changes the limits and survives reload.

### Slice 3: Mine Ownership, Yield, And Maintenance

Turn mines into named strategic assets.

Deliverables:

- Define mine site records from validated mine buildings or claim-bound mine cores.
- Assign each mine a category from VenaTerra deposit metadata when available: iron, coal, copper/tin/bronze, silver/gold, stone/quarry.
- Add periodic strategic yield into the owning settlement economy summary or treasury pipeline.
- Add maintenance costs: workers, food, wood supports, tools, guard/safety readiness.
- Add soft depletion: overworking reduces efficiency until maintenance/restoration catches up.
- Reserve a read-only external disruption field so Phase 26 can later feed contested/blocked state into yield calculations without Phase 25 owning object-control rules.
- Keep a vanilla-safe fallback for worlds without VenaTerra or for deposits with unknown metadata.

First beta balance:

- Small iron mine: modest iron every cycle.
- Large/rich iron mine: higher iron and rare bonus chance.
- Silver/gold mine: lower military value, higher coin/tax/trade value.
- Quarry: stone for fort upgrades and walls.

Acceptance checks:

- Owning a mine changes iron/stone/coin income in the strategic summary.
- Unstaffed or unmaintained mines degrade output instead of staying infinite.
- A mine can report normal/degraded/unknown economic state, and later Phase 26 disruption input can reduce yield without deleting the main fort.

### Slice 4: Army Upkeep As Economy Pressure

Make army size an economic decision.

Deliverables:

- Extend existing upkeep accounting so recruits consume food and/or coins from their home/upkeep claim on a coarse cycle.
- Add heavier maintenance classes for heavy infantry/cavalry/ranged elite when equipment or recruit type can be inferred.
- Add bounded penalties: unpaid, hungry, undersupplied, recruitment blocked, desertion risk only after repeated failures.
- Connect soldier cap to fort level and barracks availability.

Acceptance checks:

- A settlement with no food/coins cannot indefinitely support a growing army.
- Upkeep failures are visible in governor/war/settlement status.
- Penalties are bounded and do not instantly delete armies on one missed cycle.

### Slice 5: NPC Demand And Contracts

Create demand that works with 3-5 online players.

Deliverables:

- Add periodic NPC contract records: buyer, requested resource bucket/item set, amount, deadline, reward, reputation/influence reward.
- Start with server records plus command/debug/status output rather than full caravan simulation or a contract-board UI.
- Pay coins, rare goods, reputation/influence, or unlock rights.
- Let markets/trading posts increase contract quality or frequency.

Example contracts:

- City buys 128 iron.
- Monastery needs bread and leather.
- Mercenary company buys weapons.
- Port city trades salt for silver.
- Guild buys wool or timber.

Acceptance checks:

- Contracts spawn even with one player online.
- Completing a contract consumes goods and pays a useful reward.
- Markets/trading posts make contracts strategically better than isolated forts.

### Slice 6: Soft War Objectives Around Economy

Phase owner: Phase 26, consuming Phase 25 economy/object records.

Add conflict targets below full siege.

Deliverables:

- Add objective records tied to war or local hostility: mine dispute, road blockade, outpost capture, caravan raid, farm raid, stockpile raid.
- Reuse siege-standard concepts for objective presence/control instead of inventing a second combat-objective framework.
- Add “blocked” and “raided” states that reduce income/yield for a limited time instead of destroying player bases.
- Expose server-side objective state/read-model fields for later Phase 27 War Room/map/HUD presentation.

Acceptance checks:

- A mine can be contested and have reduced output while the fort remains intact.
- A road/storage route can be marked blocked and lower supply effectiveness.
- Raids produce limited spoils and cooldowns, not unlimited grief.

### Slice 7: Tribute, Vassalage, And Casus Belli

Phase owner: cross-phase. Phase 25/governance owns ongoing tribute/vassal obligation records and treasury payment execution; Phase 26 owns CB/war-goal gating and war consequences; Phase 27 owns player-facing negotiation UI.

Turn existing one-shot outcomes into ongoing politics.

Deliverables:

Phase 25/governance-economy:

- Add tribute treaty records: payer, receiver, amount/resource, interval, missed payments, source claim.
- Add vassal relationship records with overlord id, obligations, tribute terms, protection terms, and revolt/liberation hooks.
- Emit missed-payment and broken-term facts for the war layer.

Phase 26/war:

- Add typed casus belli records: mine dispute, unpaid tribute, caravan raid, worker killing, border violation, outpost capture, occupied land liberation.
- Gate war goals and peace terms by CB type.

Acceptance checks:

- Refusing or missing tribute creates a visible CB.
- Vassal status points to an overlord and produces periodic obligations, not just a label.
- War declarations explain “why this war is valid” in-world.

### Slice 8: UI And Player Operations

Phase owner: Phase 27, after Phase 25/26 read models are stable.

Make the system readable before making it deeper.

Deliverables:

- Settlement/fort screen: level, resource buckets, income/consumption, shortages, next upgrade.
- Strategic-object list: mines, farms, lumber camps, markets, outposts, blocked/raided/contested state.
- War Room: objectives, pressure, allowed terms, locked-term explanations.
- Contract board: NPC demand, rewards, deadlines, delivery status.

Acceptance checks:

- A new player can answer: “What do I lack, what should I build/capture/trade for, and what is threatening me?”
- Denied actions give server-backed reasons.
- The UI does not mutate gameplay state client-side.

### Slice 9: Balance, Telemetry, And Rollout

Phase owner: Phase 28, after the economy and conflict loops have measurable runtime behavior.

Stabilize the beta economy before adding complexity.

Deliverables:

- Configurable cycle lengths, yields, upkeep, caps, and contract frequency.
- Debug commands/report for claim economy summary and active strategic objects.
- Telemetry counters for production, consumption, raids, blockades, contracts, tribute payments, missed payments, and desertion-risk triggers.
- Beta presets for small servers.

Acceptance checks:

- Admins can inspect why a settlement is rich, starving, blocked, or over-militarized.
- Balance values can be changed without code edits.
- The system can be disabled or kept read-only if a beta save needs emergency mitigation.

## Narrow Phase 25 MVP

The smallest Phase 25 deliverable should be:

1. Strategic economy summary with food, iron, wood, stone, coins.
2. Fort levels with worker/soldier/mine/outpost caps.
3. Mine ownership and periodic yield.
4. Army upkeep consuming food/coins and blocking reckless recruitment.
5. NPC contracts that buy goods for coins/reputation.

Nearest beta can run with manual/admin rules for raids, tribute, and mine disputes while Phase 26/27/28 turn those into full systems.

## Full Second-Act MVP

The smallest playable second act across phases should be:

1. Phase 25 strategic economy summary with food, iron, wood, stone, coins.
2. Phase 25 fort levels with worker/soldier/mine/outpost caps.
3. Phase 25 mine ownership and periodic yield.
4. Phase 25 army upkeep pressure consuming food/coins and blocking reckless recruitment.
5. Phase 25 NPC contracts that buy goods for coins/reputation.
6. Phase 26 mine/outpost/route objective states: normal, contested, blocked, raided.
7. Phase 25/26/27 manual first, then UI-assisted tribute treaties before a full diplomacy suite.

This is enough to create dependencies:

- Mountain fort has iron and stone, but needs food.
- River settlement has food and contracts, but needs protection.
- Forest outpost has wood/leather/mobile troops, but needs metal.
- Raiders can profit by cutting roads or caravans without ending a player's save.

## Canonical Backlog Decomposition

The executable queue lives in `docs/BANNERMOD_BACKLOG.sqlite`; use `tools/backlog show <ID>` for full scope and acceptance. The first decomposition is:

| Area | Tasks |
| --- | --- |
| VenaTerra cross-repo prerequisites | `VEN-001`, `VEN-002` |
| BannerMod Phase 25 economy MVP | `ECON-020` through `ECON-030` |
| Resource mutation prerequisite | `ECON-031` |
| Treaty/economy politics | `POL-020` |
| Phase 26 economic war objectives | `WAR-020` through `WAR-026` |
| Phase 27 player-facing surfaces | `UI-020` through `UI-023` |

`VEN-001` and `VEN-002` are intentionally tracked in the BannerMod backlog as cross-repo prerequisites because BannerMod's mine economy depends on VenaTerra. Their implementation and verification happen in `/home/kaiserroman/venaterra`; BannerMod-side closure must reference the VenaTerra commit/test evidence plus the BannerMod compat consumer evidence.

Do not create new work from this document by copying old draft IDs. Add or split tasks with `tools/backlog` so dependencies and validation remain canonical.

## Not In The First Pass

- Full CK-style diplomacy, dynasties, claims fabrication, or law systems.
- Global market simulation with dynamic prices for every item.
- Fully physical caravan hauling for all taxes and contracts.
- Infinite resource taxonomy with dozens of goods before the five-resource loop works.
- Automatic total-war resolution without admin/operator tools during beta.
- Punitive grief mechanics that can erase a settlement through one offline raid.

## Verification Strategy

- Start with pure unit tests for strategic summary, caps, yields, upkeep costs, contract generation, and treaty payment rules.
- Add focused server/GameTest coverage only when a slice mutates live entities, claims, work areas, or storage.
- Use `ctx log -- ./gradlew compileJava` as the cheap gate for wiring slices.
- Use player-facing UAT scenarios for the beta loop: mountain fort, river settlement, forest outpost, contested mine, NPC contract, tribute default.

## Player-Facing Documentation Impact

When any slice becomes live gameplay, update these in the same implementation batch:

- `MULTIPLAYER_GUIDE_RU.md`
- `MULTIPLAYER_GUIDE_EN.md`
- `docs/BANNERMOD_ALMANAC.html`

The player message should stay simple: build a fort, identify your shortage, secure a resource object, trade or raid to cover deficits, keep your army supplied, and use politics to avoid or profit from war.
