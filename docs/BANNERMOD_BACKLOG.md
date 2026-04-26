# BannerMod Backlog

Единственный канонический файл для нереализованных задач. Старые scratch-планы и handoff-доки удаляются/не используются; если задача не здесь, она не считается активной.

Документация по моду живет в `docs/`. Корневые `MULTIPLAYER_GUIDE_RU.md` и `MULTIPLAYER_GUIDE_EN.md` остаются player-facing гайдами; корневой `README.md` остается входной точкой репозитория.

## Правила

- Код и `src/**` важнее любых старых заметок.
- Не добавлять параллельные legacy-системы ради совместимости.
- Каждый пункт ниже должен закрываться отдельным проверяемым slice.
- Player-facing flow важнее набора админ-команд.

---

## UI-001 — State/Faction UI replacement

**Зачем.** Legacy faction UI удалён, но игроку нужен нормальный UI для political state вместо command-only управления.

**Scope.**

- State list/detail/create/edit screens.
- Status, capital, leader/co-leader/member display.
- Government form display/edit once `POL-001` exists.
- Server packets for mutations, gated leader-or-op.
- No `RecruitsFaction` UI resurrection.

**Acceptance.**

- Игрок создаёт и смотрит state без команд.
- UI явно различает settlement, claim и political state.
- `compileJava` passes; packet mutations are server-authoritative.

**Progress 2026-04-26.** Player-facing political entity list/detail UI lives over the synced war snapshot, reachable from the War Room. Three server-authoritative mutation packets (`MessageCreatePoliticalEntity`, `MessageRenamePoliticalEntity`, `MessageSetPoliticalEntityCapital`) wire the Create / Rename / Capital-here buttons; create reuses `PoliticalRegistryRuntime.canCreate`, rename uses a new `validateRename` + `updateName` runtime path, and both rename and set-capital enforce the new shared `PoliticalEntityAuthority.isLeaderOrOp` check. Packet round-trip tests, a registry rename test, and a leader-or-op auth test pass. Government-form UI/edit (POL-001) and color/charter editing still outstanding.

---

## UI-002 — Siege and War Room UI

**Зачем.** `WarListScreen`/War Room не должен оставаться read-only списком; siege flow должен быть понятен в MP.

**Scope.**

- Active war detail screen.
- Siege standard list, side, position, radius, placement validation.
- Battle-window HUD/status.
- War-zone overlay/marker.
- Attacker/defender objective panel.

**Acceptance.**

- Игрок видит активные войны, battle windows, siege standards и зоны без чтения команд.
- Неверное placement действие даёт понятную причину отказа.
- Defender получает понятное предупреждение и статус осады.

**Progress 2026-04-26.** War Room lists siege standards with side, position, and radius for the selected war. The War Room now ships a "Place siege here" button: it is enabled only when the local player is the leader of one of the selected war's sides and the war is not RESOLVED/CANCELLED. The button posts `MessagePlaceSiegeStandardHere`, which delegates to a new server-side `SiegeStandardPlacementService` shared with the slash command — validation outcomes (war closed, side not participant, not leader, ...) come back as a single denial-token enum, so the chat and packet paths can never disagree on what is legal. A new client HUD (`WarSiegeZoneOverlay`, registered above the hotbar) renders a top-center banner whenever the player is inside any siege standard's radius for an active war, showing the war name, owning side, and current war state.

The War Room now also ships a battle-window banner. `WarServerConfig.resolveSchedule()` is broadcast through the existing `WarClientState` snapshot (new `Schedule` ListTag entry) so the client knows the configured `BattleWindow` set without a separate config-sync packet. `BattleWindowSchedule`/`BattleWindow` carry NBT round-trip helpers (`toListTag`/`fromListTag`, `toTag`/`fromTag`); `WarStateBroadcaster` resolves and encodes the schedule on every snapshot push, and falls back to `BattleWindowSchedule.defaultSchedule()` when the Forge config is not yet loaded. A new pure-formatter `BattleWindowDisplay` turns a `BattleWindowClock.Phase` plus `Duration` into a single line ("Battle window: OPEN FRI 19:00-20:30 — closes in 45m" / "Battle window: CLOSED — next SUN 18:00-19:30 in 1d 22h" / "not scheduled"); `WarListScreen` renders it once, top-banner style, recolored green while open. Targeted JUnit covers the duration humanizer, the schedule NBT round-trip, malformed-entry skipping, and the open/closed phase line formatter.

---

## SETTLEMENT-001 — First 10 minutes onboarding

**Зачем.** Новый игрок должен понять старт: fort/town hall, surveyor, wand, citizens, professions.

**Scope.**

- Starter fort / town hall placement flow.
- `SettlementSurveyorToolItem` feedback.
- `BuildingPlacementWandItem` placement/register/validate flow.
- Citizen spawn/hire/profession UI.
- Clear validation errors for buildings.

**Acceptance.**

- Игрок создаёт валидное поселение за 10 минут без чтения исходников.
- UI показывает почему здание невалидно.
- Citizens показывают профессию, назначение и проблему, если работы нет.

---

## SETTLEMENT-002 — Worker AI consumes ValidatedBuildingRecord

**Зачем.** Authoring уже идёт через building validation, но worker AI всё ещё местами живёт на legacy `currentXArea` полях.

**Scope.**

- Replace authoritative worker assignments with `ValidatedBuildingRecord` IDs.
- Migrate work goals away from `currentCropArea`, `currentLumberArea`, etc.
- Remove dead `MessageAddWorkArea` client/server path once unused.

**Acceptance.**

- Worker work selection does not depend on legacy area fields as source of truth.
- Wand-placed validated building can receive workers and produce behavior.
- `compileJava` and relevant tests pass.

**Progress 2026-04-26.** Settlement runtime publishes building/work-area orders through `BannerModSettlementOrchestrator`. `MessageAddWorkArea` had no remaining sender (only a registered handler + slot), so the class, the slot, and the dead Javadoc reference in `BannerModSettlementFactionEnforcementGameTests` are all gone; CIVILIAN_MESSAGES count is now 22 and the war packet base shifts down by one slot. `compileJava` is green. Live `current*Area` fields still survive on `FarmerEntity`/`FishermanEntity` and their work goals — migrating those to `ValidatedBuildingRecord` lookup is the next slice.

---

## SETTLEMENT-004 — Persistent settlement runtime state

**Зачем.** Several settlement runtimes still keep scheduler tasks, seller phases, household assignments, project queues, and work-order claims mostly in memory.

**Scope.**

- Persist resident scheduler state where needed.
- Persist seller/household/project/work-order claim state where gameplay depends on reload survival.
- Keep dirty marking stable for no-op restores.

**Acceptance.**

- Reload does not lose active meaningful settlement work state.
- No false dirty churn on identical reload/restore.
- Focused persistence tests pass.

**Progress 2026-04-26.** Work-order runtime persistence and no-op restore dirty checks are covered by `SettlementWorkOrderRuntimeTest`; targeted tests pass.

---

## SETTLEMENT-005 — Hauling and input-fetch work orders

**Зачем.** `HAUL_RESOURCE` and `FETCH_INPUT` are still analysis-only because current work orders do not safely carry source/destination/count/filter.

**Scope.**

- Add safe payload for source, destination, resource filter, and count.
- Add courier/worker assignment adapter.
- Execute haul/fetch orders through the settlement work-order runtime.

**Acceptance.**

- Workers/couriers can fetch inputs for production and haul outputs to storage.
- Orders survive release/expiry correctly.
- Tests cover payload serialization and claim behavior.

**Progress 2026-04-26.** `SettlementWorkOrder` carries source position, destination position, resource hint/filter, and item count for `FETCH_INPUT`/`HAUL_RESOURCE`. `SettlementOrderWorkGoal` executes transport orders through a four-phase state machine (move-to-source → withdraw → move-to-destination → deposit) using a stateless `TransportContainerExchange` helper that resolves containers via the nearest `StorageArea` (or a direct block-entity at the anchor pos) and respects the resource-hint filter and requested item count. `StockpileTransportWorkOrderPublisher` turns each authored stockpile route into a `HAUL_RESOURCE` order; runtime dedup keys on (building, type, destination) so republishing is a no-op. Targeted JUnit covers filter parsing, publisher helpers, and a transport claim/release/complete cycle. Live in-game smoke verification of cross-storage item movement under the new state machine remains open.

---

## SETTLEMENT-006 — Civil mutation refresh hooks

**Зачем.** Settlement refresh is more event-driven now, but not every important civil mutation path is hooked.

**Scope.**

- Prefab auto-staffing.
- Worker death/removal.
- Container placement/update.
- Creative work-area discard.
- Any remaining owner/binding/build completion paths found by audit.

**Acceptance.**

- Settlement snapshots update after all important civil world changes.
- GameTest or equivalent live-world coverage exists before broad hook wiring.

**Progress 2026-04-26.** Build completion (`BuildArea.tick` after `isDone()`), creative work-area discard (`AbstractWorkAreaEntity.hurt`), worker death (`LivingDeathEvent`), worker destroy-removal (`EntityLeaveLevelEvent` filtered by `RemovalReason.shouldDestroy()`), and container place/break events all now trigger `BannerModSettlementRefreshSupport.refreshSnapshot`. Container hooks gate on `SettlementContainerHookPolicy.shouldRefresh(isContainer, insideStorageArea)` so distant chests do not pay the snapshot cost; the predicate is unit-tested. Live GameTest coverage of the new event paths is still outstanding.

---

## SETTLEMENT-007 — Sea-trade production consumer loop

**Зачем.** Sea-trade entrypoints are recognized, but production/consumption gameplay is still thin.

**Scope.**

- Connect sea-trade availability to settlement production needs and outputs.
- Consume/import/export goods through treasury/logistics where appropriate.
- Show player-facing trade status in settlement UI.

**Acceptance.**

- Sea trade changes what settlement can produce/obtain.
- UI explains trade bottlenecks and benefits.

**Progress 2026-04-26.** Existing settlement snapshot trade-route/sea-entrypoint hints are documented for players; no new production consumer loop landed in this slice.

---

## SETTLEMENT-003 — Infrastructure gate for STATE promotion

**Зачем.** `SETTLEMENT -> STATE` must require actual infrastructure, not just a status command.

**Scope.**

- Promotion policy checks required buildings: town hall, storage, market at minimum.
- Command/UI rejects promotion with `infrastructure_insufficient`-style reason.
- Configurable required building set if minimal.

**Acceptance.**

- Promotion without required buildings fails.
- Promotion with required buildings succeeds.
- Pure policy test exists.

**Progress 2026-04-26.** Added `PoliticalStatePromotionPolicy` and command-side `STATE` promotion gate. Promotion now requires a settlement snapshot with town hall/starter fort, storage, and market infrastructure; pure policy tests pass.

---

## POL-001 — Government forms

**Зачем.** Political state needs RP structure and authority rules; settlement must not be confused with government.

**Scope.**

- Add government form to political state data/spec.
- Minimum forms: `MONARCHY`, `REPUBLIC`.
- Authority policy for status/capital/war declarations.
- UI display/edit via `UI-001`.

**Acceptance.**

- State has visible government form.
- Monarchy/republic have different authority behavior.
- Claims/settlements remain separate concepts.

**Progress 2026-04-26.** Added `GovernmentForm` enum (`MONARCHY`, `REPUBLIC`); persisted on `PoliticalEntityRecord` with backward-compatible 11-arg constructor and tag fallback to `MONARCHY` for old saves. New `MessageSetGovernmentForm` packet (leader-only) toggles via the runtime's `updateGovernmentForm`. UI shows the current form on the political-entity detail panel and ships a "→ Republic / → Monarchy" toggle button on `PoliticalEntityListScreen` enabled only for leaders. `PoliticalEntityAuthority.canAct` extends authority to co-leaders when the form is `REPUBLIC`; `MONARCHY` keeps it leader-only. `GovernmentFormTest` covers enum default, authority delegation, runtime mutation, tag round-trip, and legacy-save fallback.

---

## WAR-001

**Status: DONE 2026-04-26.**

**Зачем.** Outcomes must be real gameplay, not audit-only text.

**Scope.**

- Verify current `WarOutcomeApplier` behavior against code.
- Tribute treasury transfer.
- Occupy real claims/chunks without legacy faction bridge.
- Annex limited claims/chunks with strict cap.
- Vassalize/demilitarize audit and UI state.

**Acceptance.**

- Each outcome changes exactly the intended state and writes audit.
- Annex respects chunk/claim limit.
- Tribute affects treasury, not only audit.
- UI shows outcome result.

**Progress 2026-04-26.** All five outcomes are now real state changes; behavior is locked by `WarOutcomeApplierTest` (15 cases). `applyTribute` walks defender-owned `RecruitsClaim`s, debits each ledger's available balance through `BannerModTreasuryManager` up to the requested amount, and credits the same total into the first attacker-owned ledger; audit `OUTCOME_APPLIED type=TRIBUTE` records both `amount=` (requested) and `transferred=` (actually moved); zero-balance and zero-claim edges are explicit and tested. `applyOccupy(warId, chunks, gameTime)` registers a real `OccupationRecord` via `OccupationRuntime.place`, resolves the war, clears sieges, grants `LOST_TERRITORY_IMMUNITY`, and audits `type=OCCUPATION;chunks=N;occupationId=...`. `applyAnnex(warId, centerChunk, gameTime, republisher)` flips an entire defender claim wholesale when the targeted chunk is its center: `RecruitsClaim.setOwnerPoliticalEntityId(attacker)` + `republisher.republish(claim)` so chunks and per-claim treasury ledger follow the new owner naturally; the command-side helper `WarAnnexEffects.rebindEntitiesToNewOwner` then re-teams workers/citizens/recruits inside the annexed claim's chunks via the scoreboard, and updates `AbstractWorkAreaEntity.setTeamStringID` for work-area entities. New `WarServerConfig.SiegeProtectionAttackersExplosivesOnly` (default true) hooks `ClaimProtectionPolicy` to deny manual block-break/place/interaction by non-friendly players inside any claim that `WarSiegeQueries.isClaimUnderSiege` reports as besieged — explosions and Medieval Siege Machines bypass naturally because they don't pass through the player-block-event paths. New slash nodes: `/bannermod war occupy <warId> [radius]` (radius 0..8, square area) and `/bannermod war annex <warId>` (annexes the claim under the source's current chunk; must be the claim center). Both attacker-leader-or-op gated. Vassalize and demilitarization were already real state changes (defender → `VASSAL` and `DemilitarizationRuntime.impose` respectively); not retouched.

---

## WAR-002 — Occupation tax and control

**Зачем.** Occupation must matter economically and politically.

**Scope.**

- Occupation tax per chunk/claim over time.
- Idempotent tax timestamping.
- Treasury transfer or debt/audit if unpaid.
- Occupied claim/control display in UI.

**Acceptance.**

- Occupier gains tax; occupied pays or records debt.
- Tax cannot double-charge after reload/tick repeats.
- Audit entries exist for paid/defaulted tax.

---

## WAR-003 — Objective-based revolt resolution

**Зачем.** Timer-based auto-success is not gameplay.

**Scope.**

- Revolt schedules into battle window.
- Rebel/occupier presence or objective control decides success/failure.
- Success removes occupation; failure records cooldown/audit.
- UI shows pending revolt, window, objective, result.

**Acceptance.**

- Empty revolt does not auto-win.
- Rebel-controlled objective succeeds.
- Occupier defense can fail the revolt.

---

## WAR-004 — Cooldowns and immunity cleanup

**Зачем.** MP war spam needs protection.

**Scope.**

- Lost-territory immunity.
- Peaceful-toggle cooldown.
- Clear denial reasons in command/UI.
- Persist cooldown records.

**Acceptance.**

- Recently defeated/occupied target cannot be spam-attacked.
- PEACEFUL cannot be toggled abusively.
- Denials are visible and persisted.

**Progress 2026-04-26.** New `WarCooldownKind` (`LOST_TERRITORY_IMMUNITY`, `PEACEFUL_TOGGLE_RECENT`), `WarCooldownRecord`, `WarCooldownRuntime`, and `WarCooldownSavedData` mirror the existing demilitarization persistence pattern. `WarCooldownPolicy.canDeclareWithImmunity` wraps the existing `canDeclare` and adds a defender-immunity check; `canTogglePeacefulStatus` gates PEACEFUL flips. `WarOutcomeApplier` grants `LOST_TERRITORY_IMMUNITY` to the defender after `applyTribute`, `applyVassalize`, and `applyDemilitarization`; `PoliticalRegistryCommands.setStatus` records `PEACEFUL_TOGGLE_RECENT` on every PEACEFUL flip and refuses subsequent toggles until the cooldown expires. `WarServerConfig` exposes `LostTerritoryImmunityDays` (default 3) and `PeacefulToggleCooldownDays` (default 2). Targeted JUnit covers the runtime grant/expiry/dirty-listener semantics, the immunity gate on declaration, and the peaceful-toggle gate.

## WAR-005 — Allies in war (consent flow)

**Status: DONE 2026-04-26.**

**Зачем.** War records can model sides; player workflow must support allies.

**Scope.**

- Add allies during declaration or pre-active phase.
- Ally accept/decline flow gated by leader authority.
- PEACEFUL cannot join attacker side.
- UI support in War Room.

**Acceptance.**

- Allies become valid PvP participants on their side.
- Consent is required.
- UI and audit reflect side membership.

**Progress 2026-04-26.** Pre-active wars now accept allies via a consent-based invitation flow. New persistence layer mirrors the existing pattern: `WarAllyInviteRecord` (id/warId/side/invitee/inviter/createdAt with NBT round-trip), `WarAllyInviteRuntime` (CRUD + `existing(warId,side,invitee)` dedup + dirty listener), `WarAllyInviteSavedData` (file id `bannermodWarAllyInvites`), and a new `WarRuntimeContext.allyInvites(level)` accessor. Pure `WarAllyPolicy` returns one denial enum (`OK`, `WAR_NOT_FOUND`, `WAR_NOT_PRE_ACTIVE`, `INVITEE_UNKNOWN`, `INVITEE_IS_MAIN_SIDE`, `INVITEE_ALREADY_ON_SIDE`, `INVITEE_ON_OPPOSING_SIDE`, `PEACEFUL_CANNOT_JOIN_ATTACKER`, `INVITE_NOT_FOUND`, `INVITE_WAR_MISMATCH`); `WarAllyService` is the single entry point shared by slash commands and packets, performs leader-or-op auth, re-checks the policy on accept (so a status flip between invite and accept doesn't sneak through), removes dangling invites when the war advances, and writes `ALLY_INVITED`/`ALLY_JOINED`/`ALLY_INVITE_DECLINED`/`ALLY_INVITE_CANCELLED` audit entries. Slash subtree `/bannermod war ally invite|accept|decline|cancel|list`. Three new client→server packets (`MessageInviteAlly`, `MessageRespondAllyInvite`, `MessageCancelAllyInvite`) wire the War Room UI: a new `WarAlliesScreen` lists pending invites and current allies of each side and exposes Invite-to-Attacker/Defender buttons that open `WarAllyInvitePickerScreen` (filtered by client-side mirror of the same policy). Invites are synced through `WarClientState` (new `AllyInvites` ListTag). Targeted JUnit covers every denial token in the policy, the runtime ally append/remove dirty semantics, the invite NBT round-trip, dedup by (war, side, invitee), and removeForWar bulk cleanup. Ally membership already drives PvP gating via `WarDeclarationRecord.opposingSides`, locked in by `WarPvpGateTest.allowsWhenAttackerAllyHitsDefenderMain` / `allowsWhenDefenderAllyHitsAttackerMain` / `allowsWhenAttackerAllyHitsDefenderAlly`. Right-click decline on `WarAlliesScreen` invite rows replaces the discoverability-only DEL/BACKSPACE shortcut; the picker still uses left-click for invite selection.

---

## WAR-006 — Dynamic siege standard banner/color

**Зачем.** Static model/texture now exists; next step is political readability.

**Done.** Iron-block placeholder removed; model, texture, recipe, and mining tags exist.

**Scope.**

- Block entity renderer or model tint for political color/banner.
- Item tooltip/model communicates war use.

**Acceptance.**

- Standard color/banner matches placing side or political entity.
- No fallback iron-block visuals.

**Progress 2026-04-26.** `SiegeStandardBlockEntity` now syncs `warId` and `sidePoliticalEntityId` to the client via `getUpdateTag` / `getUpdatePacket`. New `SiegeStandardBlockEntityRenderer` paints a small political-color cap (cuboid + outline) above the static model; the cap colour is parsed from the bound side's `PoliticalEntityRecord.color` through a dedicated, unit-tested `PoliticalColorParser` (accepts `RRGGBB` or `AARRGGBB`, falls back to white). The siege standard `BlockItem` is now a `SiegeStandardBlockItem` with a two-line tooltip pointing players at the War Room "Place siege here" flow. Native banner-pattern overlay still outstanding.

---

## COMBAT-001 — Morale, suppression, rout

**Зачем.** Recruits should not always fight to death; MP battles need readable pressure and collapse.

**Scope.**

- Squad/local morale policy from casualties, odds, flanking, commander presence, recent damage.
- Suppression under sustained ranged fire.
- Fallback/rout behavior with visible feedback.

**Acceptance.**

- Badly outnumbered squads can rout.
- Commander/nearby allies improve morale.
- Player sees why units routed.

---

## COMBAT-002 — Officer/leader discipline aura

**Зачем.** Commanders should matter in battlefield cohesion.

**Scope.**

- Captain/commander aura affects morale/discipline.
- Same political entity checks.
- Configurable radius/strength.

**Acceptance.**

- Units near commander hold longer than isolated units.
- Aura does not buff enemies/neutral units.

---

## COMBAT-003 — Role-aware formation planner and shield-wall pressure

**Зачем.** Formations need to behave like tactical units, not loose mobs.

**Scope.**

- Infantry/shield front ranks.
- Ranged rear ranks/firing lanes.
- Cavalry flank/harass slots.
- Shield wall forward pressure behavior.
- Isolated formation integrity penalty.

**Acceptance.**

- Mixed groups form layered lines.
- Shield wall advances slowly and coherently.
- Isolated units lose formation benefits.

---

## COMBAT-004 — Cavalry charge and pike counterplay

**Зачем.** Cavalry and pikes need distinct battlefield roles.

**Scope.**

- Cavalry charge intent, burst, first-hit bonus, exhaustion window.
- Pike anti-cavalry hold-ground bonus.
- UI/command support if needed.

**Acceptance.**

- Cavalry charge punishes unsupported infantry.
- Pike line punishes frontal cavalry charge.
- Exhaustion prevents charge spam.

---

## COMBAT-005 — Ranged backline spacing and fallback

**Зачем.** Ranged units should not stand in melee line and die passively.

**Scope.**

- Maintain distance from own melee line and enemies.
- Fallback when enemies close.
- Preserve firing lanes where possible.

**Acceptance.**

- Archers/crossbows prefer rear positions.
- They fallback when cavalry/melee breaks through.

---

## COMBAT-006 — Siege objective targeting and escort

**Зачем.** Armies need to interact with `SiegeStandardBlock`, not only players.

**Scope.**

- Attack enemy siege standard during battle window.
- Defend/escort own standard.
- Standard health/control pool and audit on destruction.

**Acceptance.**

- Ordered attackers can destroy enemy standard.
- Defenders hold around own standard.
- Destruction changes siege state/audit.

---

## PERF-001 — Async navigation audit for every custom mob

**Зачем.** MP-scale fights/settlements need non-blocking navigation for all custom mobs, not just some recruits.

**Scope.**

- Audit every `createNavigation` / `PathNavigation` override.
- Cover recruits, mounted units, sailors, citizens, workers, nobles, militia.
- Replace unjustified sync vanilla navigation with async-safe navigation.

**Acceptance.**

- No custom mob uses expensive sync pathing without explicit reason.
- Compile/test coverage or documented verification for navigation class selection.

---

## PERF-002 — Crowd render optimization beyond LOD layers

**Зачем.** Existing recruit render LOD skips some expensive layers at distance, but large MP battles may still be dominated by base model, layer passes, nameplates, state changes, and animation work.

**Scope.**

- Profile recruit render costs: base model, layers, nameplates, texture/state switches, animation/model pose churn.
- Evaluate distant/crowd simplified renderer or simpler model.
- Collapse cosmetic/team/biome overlays where possible.
- Skip held item/armor/nameplate work for non-near/non-selected crowds when safe.

**Acceptance.**

- Large recruit crowds have measured render improvement.
- Close-range readability is preserved.
- Optimization is backed by profiling evidence, not guesswork.

---

## PORT-001 — NeoForge 1.21.1 port

**Зачем.** Future platform migration may be needed, but it is not active gameplay work and should not live as a separate stale Cursor plan.

**Scope.**

- Create a branch only when this becomes active.
- Upgrade Gradle/toolchain/mod metadata for NeoForge 1.21.1.
- Migrate registries, networking, events, item NBT/data components, and dependency APIs.
- Rebuild tests and smoke run after compile.

**Acceptance.**

- Root build targets NeoForge 1.21.1 on the port branch.
- `compileJava`, tests, and in-game smoke pass.
- Old Forge 1.20.1 assumptions are either removed or documented.

---

## LEGACY-001 — Final legacy cleanup audit

**Зачем.** Old faction/diplomacy/siege leftovers must not contradict regulated warfare.

**Scope.**

- Search and classify remaining `RecruitsFaction`, `RecruitsPlayerInfo`, `FactionEvents`, `RecruitsTeamSaveData` references.
- Search old siege remnants: `isUnderSiege`, `SiegeEvent`, `ClaimSiegeRuntime`, `siegeSpeedPercent`.
- Remove stale naming where semantics are already political UUIDs.

**Acceptance.**

- No live old faction/diplomacy/siege gameplay path remains.
- Remaining names are either removed or documented as non-gameplay compatibility seams.
- `compileJava` and tests pass.

---

## COMPAT-001 — Save-data and packet compatibility decision

**Зачем.** Historical planning still names unified save-data and packet compatibility as deferred. The project needs an explicit decision: migrate, drop, or support only narrow critical paths.

**Scope.**

- Audit active SavedData and packet compatibility seams.
- Decide which legacy worlds/packets are supported, if any.
- Remove unsupported compatibility code or document narrow migration helpers.

**Acceptance.**

- Compatibility policy is explicit and reflected in code/docs.
- No hidden promise of broad old-world compatibility remains.

---

## COMPAT-002 — Archive tree retirement decision

**Зачем.** `recruits/` and `workers/` are reference archives, not active runtime. Decide whether to keep them, move them, or delete them after stabilization.

**Scope.**

- Confirm no build/test path depends on archive trees.
- Decide archive retention policy.
- Update docs and repo layout accordingly.

**Acceptance.**

- Archive status is unambiguous.
- Active build remains root `src/**` only.

---

## OPS-001 — Warfare-RP UAT runbook

**Зачем.** Smoke tests are scattered; server operator needs one reproducible script.

**Scope.**

- Create a concise UAT section or file after UI/runtime slices stabilize.
- Cover state create, settlement setup, declaration, battle window, siege standard, PvP gate, outcome, occupation/revolt.

**Acceptance.**

- A dev server can run the flow in 5-10 minutes.
- Every step has expected result and failure signal.
