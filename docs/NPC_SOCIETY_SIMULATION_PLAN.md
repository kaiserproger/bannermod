# BannerMod NPC Society Simulation Plan

## Status

- Partial implementation is now live in code.
- Phases 0 and 1 foundations are implemented in a first server-authoritative slice.
- Phase 2 is now live in a first complete server-authoritative gameplay slice:
  - hunger, fatigue, social, and safety pressure are persisted and updated in runtime
  - resident intent now runs through an explicit shared utility scorer instead of only local priority tweaks
  - `eat`, `seek supplies`, `socialise`, `hide`, and `defend` are now first-class society intents in the scheduler/runtime layer
  - citizens and workers now have a first real physical daily-life execution pass for anchored intent behavior
  - Phase 2 behavior is now covered by dedicated GameTests and the full GameTest suite was restored to green after the courier-route regression fix
- The first dedicated household and family slice is now live:
  - household membership is stored separately from the home building id
  - household housing state now distinguishes settled, homeless, and overcrowded households
  - family GUI observability now exists for citizens and workers
- Phase 3 is now live in a first full memory-and-relationships slice:
  - bounded resident memory records are persisted in a dedicated runtime
  - trust, fear, anger, gratitude, and loyalty now derive from remembered events and are stored on live society profiles
  - violent player actions and protective player actions now spread memory pressure through family and household links
  - starvation and housing pressure now leave durable social memory instead of only transient need pressure
  - citizen and worker inspection now expose a dedicated social-memory ledger GUI
  - Phase 3 runtime and persistence are covered by dedicated tests and compile-time GameTest verification
- The first ruler-approved infrastructure autonomy slice is now live:
  - household housing petitions no longer auto-approve and now persist explicit `REQUESTED`, `DENIED`, `APPROVED`, and `FULFILLED` state
  - rulers can approve or deny housing petitions from clickable chat actions and `/bannermod society housing ...` commands
  - settlements can now also raise ruler-approved livelihood requests for `lumber camp`, `mine`, and `animal pen`
  - approved livelihood requests now flow into the prefab project path with exact prefab ids instead of only coarse growth categories
  - settlement-spawned workers now start with baseline profession tools, auto-bind to compatible existing claim work areas more aggressively, and can craft replacement stone tools for themselves at nearby crafting tables when materials are available
- This document now serves two purposes:
  - record what was actually shipped
  - define how the next refactor pass should restructure and extend it

## Current Implementation Snapshot

The current runtime already contains a first working NPC-society backbone.

### What Was Implemented

- A dedicated server-owned society store now exists under `src/main/java/com/talhanation/bannermod/society/`.
- `NpcSocietySavedData` and `NpcSocietyRuntime` now own persistent per-NPC social profiles instead of scattering new data across arbitrary entity NBT.
- `NpcSocietyProfile` now carries a first real social identity slice:
  - life stage
  - sex
  - household id
  - home building uuid
  - work building uuid
  - current daily phase
  - current intent
  - current anchor
  - hunger need
  - fatigue need
  - social need
  - safety need
- Existing settlement home assignment is now mirrored into society state from `BannerModSettlementClaimTickService`.
- Household is no longer just a UUID alias for the home building:
  - `NpcHouseholdSavedData` and `NpcHouseholdRuntime` now persist a dedicated household layer
  - a household now owns its own `householdId`
  - a household now stores member resident UUIDs separately from the house building UUID
  - one home currently maps to one household in the safe first live slice
- Household housing state is now live:
  - `NORMAL`
  - `HOMELESS`
  - `OVERCROWDED`
  - the state currently derives from home assignment plus validated resident capacity
- Phase 1 GUI observability is live:
  - `client/civilian/gui/CitizenProfileScreen.java`
  - `client/civilian/gui/WorkerStatusScreen.java`
- Both profile screens now surface more social-state detail:
  - household id
  - household size
  - household housing state
  - housing request state
- Family GUI observability is now live:
  - `client/civilian/gui/NpcFamilyTreeScreen.java`
  - citizen profile now exposes a family button
  - worker status screen now exposes a family button
  - the family screen currently shows self, spouse, mother, father, and children
  - loaded nearby relatives can be rendered as live entity previews in the screen
- Entity conversion continuity is live: when a citizen becomes a worker or recruit, the society profile is moved to the new entity UUID instead of being lost.
- Entity conversion continuity now also carries household and family continuity:
  - household membership survives citizen <-> worker/recruit conversion
  - spouse/parent/child references are retargeted to the new entity UUID
- Adolescents are now seeded for ordinary citizens and are rendered smaller via `client/citizen/render/CitizenRenderer.java` plus synced life-stage data on `CitizenEntity`.
- Phase 2 utility intent is now live in code:
  - `NpcSocietyNeedRuntime` updates hunger, fatigue, social, and safety need
  - `NpcSocietyPhaseTwoIntentScorer` compares candidate intents on a shared scale
  - `BannerModResidentGoalScheduler` now schedules first-class society intents for `eat`, `seek supplies`, `socialise`, `hide`, and `defend`
  - worker labor/logistics goals now yield correctly when the current society intent is non-work
  - active courier storage flow was explicitly preserved so authored courier logistics still run under the new behavior gates
- A first real daily-life execution pass is now live:
  - `NpcSocietyAnchorGoal` drives citizens and workers toward home/market/street/barracks-style anchors from current intent
  - `go home`, `rest`, `eat`, `seek supplies`, `socialise`, `hide`, and `defend` now resolve to visible anchored movement/loiter behavior
  - `socialise` now has a cheap visible scene pass where residents gather and look toward nearby social partners
- Phase 2 observability and verification are now live:
  - citizen and worker screens now surface safety pressure in addition to hunger/fatigue/social
  - dedicated GameTests now cover hunger -> `EAT`, fatigue/home -> `GO_HOME`, social -> `SOCIALISE`, threat -> `HIDE`/`DEFEND`, worker labor gating, and citizen social-anchor movement
- House self-build has a first backend path:
  - households in housing pressure can create housing requests
  - requests are stored in dedicated saved data
  - requests are now keyed by household, with a representative resident retained for GUI/notifications
  - requests now notify the lord and wait for explicit approve/deny instead of silently auto-approving
  - approved requests become `PendingProject` house builds
  - project execution reuses the existing `HousePrefab` and settlement build-area pipeline
- A first ruler-approved livelihood-infrastructure path now exists:
  - settlements can create dedicated saved-data requests for `lumber camp`, `mine`, and `animal pen`
  - requests are keyed by claim plus livelihood type rather than being folded into generic growth hints
  - approved requests now become exact-prefab `PendingProject` entries instead of falling back to a coarse category guess
  - the first shipped slice intentionally bootstraps the approved livelihood build immediately after placement so the village does not deadlock on “needs tools/resources before it can build the workplace that would produce those resources”
- Worker self-sufficiency now has a first live runtime path:
  - settlement-spawned workers start with baseline stone profession tools
  - worker bootstrap now reuses existing compatible claim work areas for farmer, miner, lumberjack, fisherman, and animal-farmer paths where possible
  - workers can now craft replacement stone tools for themselves at nearby crafting tables when they can obtain wood and cobblestone through their current inventory/storage flow
  - this first slice covers basic survival tools only; it is not yet a full smithing or workshop economy
- A first real family identity slice now exists in persisted code:
  - `NpcFamilySavedData` and `NpcFamilyRuntime` persist family records per resident
  - family records now carry spouse, mother, father, and child UUID links
  - households now also carry a persisted head resident UUID
  - family links are no longer rebuilt only for GUI display; they are now stored and preserved across later reconciles

### How It Was Implemented

- The implementation deliberately reused live systems instead of introducing a parallel AI stack.
- Home ownership stays grounded in the existing settlement home-assignment runtime, then flows into the society profile.
- Household identity now stays grounded in home assignment, but is stored in a dedicated runtime instead of aliasing the home UUID directly.
- Intent state stays grounded in the current resident-goal scheduler, then flows into the society profile as readable social state.
- Need pressure is layered on top of the existing resident scheduler rather than replacing it wholesale in one pass.
- Household pressure is deliberately still simple in the shipped slice:
  - it currently derives from current member count versus resident capacity
  - it does not yet model reserves, prestige, lineage pressure, or multi-home household structures
- Family links are deliberately still conservative in the shipped slice:
  - spouse/head/parent-child links are now persisted
  - candidate pairings still come from simple household-local rules when no prior stable link exists
  - this is a scaffolding pass, not a full genealogical simulator yet
- Housing construction reuses:
  - `settlement/project/BannerModSettlementProjectRuntime.java`
  - `settlement/project/BannerModSettlementProjectWorldExecution.java`
  - `settlement/prefab/impl/HousePrefab.java`
  - builder/build-area execution already present in the settlement runtime
- Ruler-approved livelihood construction currently reuses the same settlement project stack:
  - `society/NpcLivelihoodProjectPlanner.java`
  - `society/NpcLivelihoodRequestSavedData.java`
  - `settlement/project/BannerModSettlementProjectWorldExecution.java`
  - prefab-backed `MinePrefab`, `LumberCampPrefab`, and `AnimalPenPrefab`
- Worker self-crafting deliberately stays local to worker runtime instead of inventing a second crafting subsystem:
  - a dedicated worker goal checks nearby crafting tables
  - the worker consumes held or stored materials directly from inventory
  - missing materials still flow through the existing storage-request mechanism

### What Is Still Missing In The Live Runtime

- Household is now a real runtime with persistent members and a first housing-pressure state, but it is still not a complete social household simulation.
- Family is now a real persisted identity layer, but it is still only a first structured slice.
- The current family model is still incomplete:
  - spouse pairing is still selected from simple in-household rules when no stable pair already exists
  - parent-child links are still assigned from current household structure rather than a true birth-history pipeline
  - there is still no pregnancy, infancy, or generational lifecycle simulation
  - there is still no widowhood, remarriage, inheritance, or household fission logic
- Lord permission for house building is only partially realized:
  - requests exist
  - notification exists
  - manual approve/deny now exists in a first chat-command/chat-action slice
  - a richer dedicated GUI still does not exist yet
- Household housing requests are now household-driven, but they are still incomplete:
  - there is still no fairness queue between competing households
  - there is still no direct reservation of the newly built home back onto the requesting household by explicit request ownership rules
- House self-build currently reuses the existing settlement builder pipeline; it is not yet a full citizen-driven gather-carry-place loop owned by the requesting household.
- Livelihood self-build is now live in a first practical slice, but it is still intentionally coarse:
  - requests currently cover only `lumber camp`, `mine`, and `animal pen`
  - the village currently asks the ruler first, then uses prefab-backed project placement instead of emergent freeform site planning
  - the first shipped slice grants immediate build completion after ruler approval to break bootstrap deadlocks; it does not yet prove a full resource-haul-and-place construction loop
- Worker self-crafting is now live in a first practical slice, but it is still limited:
  - only baseline stone tool replacement is covered
  - workers do not yet reserve recipes globally or negotiate shared access to a workshop
  - there is still no deeper household crafting chain, smithing progression, or tool-quality economy
- The current scheduler/runtime still had one important first-slice bug that was fixed while landing this work:
  - resident day/night phase had been derived from absolute game time instead of visible world day time, which could produce obvious “night rest during daytime” behavior after time shifts
  - surveyor mode-switching had also preserved the previous anchor, which could make later building validation accidentally stay tied to the starter-fort beacon until the mode change now resets the session anchor
- Adolescents are only safely shipped for the citizen path right now; worker/recruit-wide visual and gameplay handling still needs a broader pass.
- The family GUI is useful and live, but still limited:
  - it depends on nearby loaded entities for live model previews
  - it does not yet expose head-of-household state directly in the screen
  - it does not yet show extended kin, multiple generations, or a scrollable lineage tree
- Phase 2 is complete for the first shipped slice, but still intentionally limited:
  - the utility pass does not yet include belonging, morale, health stress, religion, or memory-driven emotion
  - anchored execution is still a lightweight pass layered over existing entity behavior, not a full authored social animation system
  - `eat` and `seek supplies` currently use simple anchor-driven behavior rather than a deep food economy or full household consumption simulation

## Required Refactor Direction

The next pass should not just append features. It should cleanly separate what already exists into clearer ownership layers.

### 1. Separate Profile, Household, And Request Ownership

- Keep `NpcSocietyProfile` as the per-actor identity and lightweight state record.
- Household membership, housing state, and first family links are now separated into dedicated runtimes instead of being encoded indirectly through home UUIDs.
- The next pass should extend those runtimes rather than collapsing data back into the profile.
- Reserve state, lineage depth, and household continuity rules still need to move into or grow from this dedicated household layer.
- Keep housing requests in their own queue/runtime and do not let them grow into a shadow household system.

### 2. Replace Priority Tweaks With A Real Utility Layer

- Current Phase 2 works by feeding needs into existing goal priorities.
- That was the correct minimum slice, but it should evolve into an explicit utility scoring pass that compares candidate intents on one shared scale.
- `eat`, `sleep`, `work`, `socialize`, `seek supplies`, and `hide` should all compete through the same scoring system.

### 3. Add A Real Execution Layer For Daily Life

- Society intent should no longer stop at labels in GUI.
- Residents should physically:
  - walk home
  - remain near home during rest
  - gather at market or street anchors
  - run cheap social scenes
- This should remain server-authoritative and piggyback on the current low-level entity behavior where possible.

### 4. Rework House Construction Into A True Social Loop

- The current implementation proves that residents can request and trigger house projects.
- The next version should add:
  - explicit lord approval or denial UI
  - request priority and fairness rules
  - reservation of newly built homes for the requesting resident or household
  - direct linkage between household shortage and project urgency
  - clearer use of resource gathering and hauling before or during build execution

### 5. Expand Adolescents Beyond A Data Flag

- Adolescents should eventually affect:
  - allowed jobs
  - work pressure
  - combat participation
  - movement and animation
  - household role
- The current citizen-only scaling pass is a safe first slice, not the end state.

### 6. Prepare Memory To Attach To The Same Model

- Memory should attach to the same actor/household model already introduced here.
- Do not build memory as a separate island disconnected from needs, household, and legitimacy.

## Purpose

BannerMod already has workers, citizens, recruits, settlements, politics, and war. What it does not yet have is a convincing medieval society. Current NPCs are still too close to task executors attached to buildings or command state.

This document defines a phased plan to evolve NPCs into self-contained social actors with:

- age and life stages
- sex and demographic continuity
- household and kinship
- memory and grudges
- social needs and conversations
- loyalty, fear, anger, and collective retaliation
- revolt potential
- religion and cultural fault lines
- expanded resident GUI surfaces that expose this state clearly to the player

The target is not "more AI for its own sake". The target is a readable, reactive, scalable medieval society that feels alive near the player and remains affordable at settlement scale.

## North Star

NPCs should stop feeling like automation nodes and start feeling like people who:

- belong to a home, family, faith, and settlement
- remember what happened to them and to their relatives
- react to the player as a social and political actor, not just as a nearby entity
- can cooperate, comply, resist, flee, retaliate, or revolt
- continue to make sense under multiplayer and server-authoritative rules

## Success Threshold

The simulation is "alive enough" when a player can explain why an NPC is where it is and why it feels the way it does.

Minimum believable threshold:

- NPCs have a day and night routine.
- NPCs have homes and family links.
- NPCs remember violence, theft, hunger, and protection.
- NPCs talk, gather, rest, and work at sensible times.
- NPCs can fear or hate the player for persistent reasons.
- A settlement can shift from obedience to unrest without direct scripting.

## Design Constraints

- Server-authoritative mutations remain mandatory.
- Near-player simulation can be rich; far simulation must be cheap.
- Async work is allowed for planning, scoring, routing, and snapshot analysis, but not for direct world mutation.
- Current runtime slices must be migrated incrementally. This is not a rewrite-in-place project.
- GUI additions must stay Minecraft-native and compact, not turn into dashboard panels.

## High-Level Architecture

The NPC society runtime should be split into six layers.

### 1. Identity Layer

Persistent facts about an NPC:

- name
- sex
- birth time or age stage
- household id
- parent ids
- spouse or partner id
- child ids
- culture id
- faith id
- class or status tier
- home anchor
- work anchor

This layer changes rarely.

### 2. Social State Layer

Longer-lived values that define social behavior:

- loyalty to settlement authority
- trust toward player or other actors
- fear toward player or hostile groups
- anger or grievance values
- piety or religious commitment
- social standing
- unrest contribution

This layer changes slowly through events, memory decay, and settlement conditions.

### 3. Needs Layer

Short-to-medium-term internal drivers:

- hunger
- fatigue
- safety
- social need
- belonging
- morale
- health stress

This layer drives everyday utility scoring.

### 4. Memory Layer

Significant remembered events and relationship deltas.

Memory types:

- personal memory: "the player hit me"
- family memory: "the player killed my brother"
- settlement memory: "our village starved under this ruler"
- cultural memory: "this faction is hostile to our faith"

Memory is required for durable consequences. Without it, NPCs only feel alive in the moment.

### 5. Intent Layer

High-level current intention, selected by utility scoring:

- sleep
- go home
- work
- eat
- socialize
- worship
- seek supplies
- flee
- defend
- protest
- riot

The intent layer should update on a timer budget or on events, not every tick.

### 6. Execution Layer

Concrete low-level actions:

- walk to anchor
- interact with block or storage
- face another NPC
- sit, idle, talk, pray
- join crowd, defend point, attack target

This remains close to traditional Minecraft entity behavior, but driven by the layers above it.

## Async And Performance Model

The design assumes aggressive use of snapshots and async planning.

### Allowed Async Work

- utility scoring over cached NPC state
- social tension aggregation
- route planning over snapshots
- household and settlement need analysis
- threat map generation
- crowd or riot staging suggestions
- far-settlement progression

### Main-Thread-Only Work

- entity state mutation
- inventory mutation
- damage and combat resolution
- block interaction
- authority checks using live sender context
- final commit of async results

### Commit Rule

Every async result must be validated on commit:

- target still exists
- household or claim state still matches
- authority is still valid
- result is not stale against a newer version or timestamp

### LOD Strategy

- `LOD0`: full simulation near players
- `LOD1`: reduced social and tactical updates in the same active area
- `LOD2`: aggregate household and settlement simulation off-screen
- `LOD3`: statistical background only for distant settlements

This is required if the mod is expected to support large settlements and large wars at once.

## Social Simulation Model

### Age And Life Stages

The system should model at least these life stages:

- infant or child
- adolescent
- adult
- elder

Requirements:

- children are visibly smaller on spawn or birth
- life stage affects allowed jobs, combat ability, movement, and household role
- adulthood unlocks full labor, combat, household creation, and parenthood
- elders remain socially important even if less efficient physically

### Sex And Demography

The initial plan assumes binary sex state because the user goal is medieval demographic simulation, not a generic body system.

It should affect:

- reproduction and birth modeling
- family structures
- inheritance or household continuity if those systems are later added
- some social norms if culture or religion uses them

It should not create trivial "male gets strength, female gets weakness" arcade logic. Any such differences should come from role, age, status, and equipment first.

### Household

Household is the main social atom of the settlement.

Each household should eventually track:

- adults
- children
- home anchor
- household storage or reserve state
- class tier
- faith
- tension or insecurity

Household-level simulation is cheaper and more believable than trying to simulate everyone as a lone actor.

### Social Desire

NPCs should want to socialize for reasons, not at random.

Drivers:

- low social fulfillment
- evening leisure window
- family proximity
- friendly relations
- shared faith or culture
- relief after danger or work shift

Cheap forms of social behavior:

- pause and face another NPC
- gather at market, fire, square, or hall
- short paired talk scene
- household co-presence at home
- worship attendance

## Memory Model

Memory must be compact and selective.

### Memory Event Types

Start with only meaningful events:

- assaulted by actor
- robbed by actor
- protected by actor
- fed or paid by actor
- relative injured or killed
- lost home
- starved or nearly starved
- forced labor or abusive taxation
- insult to faith or shrine
- revolt participation
- punishment by authority

### Memory Storage Strategy

Per NPC:

- a bounded list of important event records
- compact relationship deltas per known actor
- family and household links stored separately from the event list

Old low-value events should decay or collapse into aggregates such as:

- repeated abuse by player
- repeated protection by local lord

### Relationship Axes

Per important actor or group:

- trust
- fear
- anger
- gratitude
- loyalty
- grief

These values should drive intent selection, speech flavor, and crowd behavior.

## Collective Reaction Model

The player should be able to push NPCs too far.

### Escalation Ladder

1. discomfort
2. distrust
3. fear
4. active grievance
5. refusal or passive resistance
6. local self-defense
7. organized unrest
8. revolt

### Collective Inputs

- violence against residents
- violence against household members
- hunger and supply failures
- perceived illegitimate rule
- excessive taxation or coercion
- cultural or religious hostility
- military occupation or humiliation

### Outputs

- guards become aggressive sooner
- civilians flee or hide
- households refuse labor or tax compliance
- rumor and memory spread through kin and neighbors
- armed residents form mobs or militias
- settlement-level revolt state becomes active

## Religion And Cultural Fault Lines

Religion should be treated as a social system, not a buff source.

Minimal first-class uses:

- identity and belonging
- ritual gathering windows
- piety and moral legitimacy
- inter-group tension
- revolt justification or pacification

Potential fault lines:

- faith mismatch
- class resentment
- outsider occupation
- blood feud between households
- cultural contempt or ethnic hostility

These values should be allowed to stay dormant until activated by memory and pressure.

## Resident GUI Expansion

This section is required work for the design, even before code, because the player must be able to understand why an NPC is behaving a certain way.

Existing surfaces to extend:

- `client/civilian/gui/CitizenProfileScreen.java`
- `client/civilian/gui/WorkerStatusScreen.java`
- `inventory/civilian/CitizenProfileMenu.java`
- `entity/civilian/WorkerInspectionSnapshot.java`

### GUI Principles

- Keep the current parchment, wood, iron, and compact Minecraft-native presentation.
- Show causes, not only labels.
- Prefer summary plus progressive disclosure over one huge always-visible sheet.
- Use stable categories so players can learn to read the screen quickly.
- Every warning or negative state must explain the next expected cause or pressure.

### Citizen Profile Expansion

`CitizenProfileScreen` should eventually show more than profession, owner, assignment, and state.

New target sections:

- identity
  - age stage
  - sex
  - culture
  - faith
  - household name or id
- family
  - parents
  - spouse or partner
  - children count
  - notable living relatives nearby
- condition
  - hunger
  - fatigue
  - morale
  - fear
  - loyalty
- social state
  - current intent
  - current grievance or stress source
  - notable friend, rival, or enemy summary
- memory summary
  - recent important memory
  - long-term grievance
  - recent positive bond event
- political or legal state
  - settlement allegiance
  - unrest contribution
  - under suspicion, protected, grieving, or vengeful markers

Recommended layout behavior:

- first panel: identity and immediate state
- second panel: family and household
- third panel: memory, loyalty, fear, and unrest
- optional tab or page for historical details if needed later

### Worker Status Expansion

`WorkerStatusScreen` should stop being only an assignment or conversion panel and become a readable labor-and-social status panel.

New target sections:

- worker identity
  - age stage
  - sex
  - home household
  - owner and political allegiance
- labor status
  - current profession
  - work shift window
  - tools state
  - transport burden
  - blocked-by reason with severity
- personal state
  - hunger
  - fatigue
  - morale
  - social fulfillment
- loyalty and unrest
  - settlement loyalty
  - grievance score
  - revolt risk bucket: calm, strained, angry, dangerous
- recent memory
  - recent abuse, loss, starvation, or reward summary
- social obligations
  - has dependents
  - household pressure
  - mourning or injury effects

Recommended UI behavior:

- show short summaries by default
- allow one contextual expansion row or tooltip layer for deeper details
- avoid filling the screen with raw numbers; combine state words with compact gauges where useful

### Why GUI Matters

Without GUI support, deep NPC simulation will feel random or broken to the player. Expanded information is not optional polish; it is necessary observability for a complex society system.

## Implementation Phases

### Phase 0. Foundations

- define data model and saved-state ownership boundaries
- define snapshot versioning rules
- define async scheduler contracts for social planning
- define what belongs on entity state versus settlement or household state

Current shipped result:
- server-owned `society` saved data exists and is now the owner of first-slice per-NPC identity/state
- separate housing-request saved data exists
- GUI snapshot plumbing exists for citizen and worker inspection surfaces

Still needs refactor:
- snapshot versioning and migration rules are still lightweight and should be formalized before memory/religion land

### Phase 1. Identity And Daily Life

- add age stage and sex
- add home and household identity
- add day and night routines
- add social anchors such as market, hearth, square, temple, tavern, barracks
- expand resident GUI with identity and basic condition

Deliverable goal: NPCs stop feeling permanently glued to work posts.

Current shipped result:
- life stage and sex exist in live profiles
- ordinary citizens can now seed as adolescents
- home and household identity are persisted and shown in GUI
- daily phase / intent / anchor state are exposed in GUI

Still needs refactor:
- day/night routine is still mostly a high-level scheduler state, not a full physical daily-life executor
- social anchors are still lightweight market/street/barracks labels, not a rich anchor registry
- worker/recruit-wide life-stage rendering and restrictions still need a broader pass

### Phase 2. Needs And Utility Intent

- introduce hunger, fatigue, safety, and social need
- replace binary always-work behavior with utility scoring
- add intent categories: work, eat, sleep, socialize, hide, defend
- add first cheap social scenes

Deliverable goal: NPCs visibly change behavior with time and pressure.

Current shipped result:
- hunger, fatigue, and social need are implemented
- safety need is now implemented in the same persisted/runtime model
- residents now choose between intents through one explicit shared utility scorer
- `eat`, `seek supplies`, `hide`, and `defend` are now first-class society intents
- residents now physically execute anchored daily-life behavior for `go home`, `rest`, `eat`, `seek supplies`, `socialise`, `hide`, and `defend`
- cheap visible social scenes now exist through social-anchor gathering and nearby-partner facing behavior
- worker labor/logistics goals now respect the current society intent instead of always pushing through as work
- dedicated GameTests now cover the Phase 2 behavior slice and the suite is green with those tests included

Still needs refactor:
- safety, belonging, morale, and health stress are not yet part of the same shared model
- the current utility model is still a first pass rather than a final long-horizon planner

### Phase 3. Memory And Relationships

- introduce bounded memory records
- add trust, fear, anger, gratitude, loyalty axes
- link memory spread to family and household
- surface memory summaries in GUI

Deliverable goal: NPCs remember what the player and settlement did to them.

Current shipped result:
- `NpcMemorySavedData` and `NpcMemoryRuntime` now persist bounded per-resident social memories in dedicated saved data.
- `NpcSocietyProfile` now carries derived trust, fear, anger, gratitude, and loyalty scores alongside needs and daily-life state.
- Player-caused harm now writes durable assault memories and propagates weaker family and household echoes through persisted kinship links.
- Player protection now writes positive memory that raises trust, gratitude, and loyalty instead of only clearing a momentary threat.
- Severe hunger plus homeless/overcrowded household states now create durable negative memory instead of only short-lived pressure spikes.
- `NpcSocietyPhaseTwoIntentScorer` now lets memory-driven fear and anger influence `HIDE`, `DEFEND`, `WORK`, `GO_HOME`, `REST`, and `SOCIALISE` scoring.
- Citizen and worker inspections now expose the new social state through a dedicated memory-ledger screen with recent remembered events.

Still needs refactor:
- memory is now durable and propagated, but it is still a lightweight event ledger rather than a full witness/rumor/history pipeline
- social axes are currently aggregate resident scores, not per-actor relationship ledgers yet
- memory-triggered retaliation still stops at intent pressure; explicit justice, guard response, and revolt behavior remain Phase 4+

### Phase 4. Collective Defense And Justice

- build local witness and rumor spread
- add household and guard reactions to abuse
- add passive resistance and local retaliation
- let residents attack the player when thresholds are crossed

Deliverable goal: the player can no longer abuse people without social consequences.

### Phase 5. Religion, Status, And Unrest

- add faith and class or status pressures
- add legitimacy effects for rulers and occupiers
- add settlement tension accumulation
- add protest, refusal, and riot intents

Deliverable goal: conflict emerges from social structure, not only direct combat.

### Phase 6. Birth, Growth, And Continuity

- add child spawn or birth flow
- add small body sizes for early life stages
- add adulthood transitions
- tie household continuity to demographic survival

Deliverable goal: settlement population becomes a living lineage, not a static roster.

### Phase 7. Far Simulation And Scale Hardening

- move distant households and settlements to aggregate updates
- preserve social continuity without full live entity thinking off-screen
- batch memory decay, births, deaths, and unrest progression

Deliverable goal: the social model scales beyond one loaded village.

## Risks

- Overfitting realism before basic readability exists.
- Writing too much data to individual entities instead of stable household or settlement structures.
- Letting async planners read live world state directly.
- Making every NPC evaluate too many expensive options too often.
- Building GUI detail without a compact information hierarchy.

## Non-Goals For The First Slice

- fully simulated medieval law code
- dozens of emotions or traits per NPC
- universal dialogue trees
- deep romance simulation before household and memory foundations exist
- full historical economy before basic daily life is solved

## Open Questions

- Which data should stay on per-NPC society profiles versus a future dedicated household runtime?
- Should religion start as a fixed tag, or as a settlement institution with clergy and sites?
- How much direct player editing or debugging of NPC memory should be exposed in admin tools?
- Should child growth be real-time, game-time bucketed, or milestone based?
- How much of revolt is household-driven versus political-entity-driven?
- Should lord housing approval remain policy-driven by default, or become strictly manual once a UI exists?

## Verification Checklist For The Ongoing Refactor

Before the next major slice lands, verify that:

- every phase has a data source, runtime owner, and GUI surface
- every expensive system has an LOD or async story
- player-facing GUI remains readable and Minecraft-native
- memory, religion, and revolt are connected to one shared social model rather than isolated feature islands
- household requests and household ownership do not drift into two competing systems
- newly built houses are reserved correctly for the requesting resident or household
