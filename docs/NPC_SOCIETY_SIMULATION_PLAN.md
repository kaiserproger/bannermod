# BannerMod NPC Society Plan

## Purpose

This document replaces the older "very smart society" plan.

The new goal is simple:

- allow many NPCs to exist at once
- keep MSPT stable on a real server
- make NPCs look believable enough in play
- avoid deep simulation that Minecraft cannot carry cheaply

This is now an optimization-first design document, not a wishlist for a city-sim brain.

## Core Decision

BannerMod will not try to simulate fully social, memory-driven, highly autonomous people.

BannerMod will instead simulate cheap, readable, useful residents with:

- a home
- a workplace
- a small set of daily states
- simple danger fallback
- simple hunger and fatigue handling
- clear player-facing UI

If a feature makes NPCs feel smarter but significantly increases constant per-tick cost, it should be rejected.

## Hard Constraints

Minecraft server performance is the first constraint.

The system must assume:

- pathfinding is expensive
- repeated world scans are expensive
- NPC-to-NPC reasoning scales badly
- cross-chunk simulation is dangerous
- per-tick decision trees become unstable and costly fast

Therefore the target is not "smart NPCs".

The target is:

- low-cost state machines
- infrequent decisions
- cached world knowledge
- readable behavior
- stable behavior under load

## Final Scope

### Keep

- basic resident identity
- home assignment
- workplace assignment
- day schedule
- coarse needs: hunger, fatigue, danger
- simple state selection
- simple worker usefulness
- clear debug and profile UI
- ruler approval flows for explicit petitions

### Reduce

- social behavior
- family behavior
- recovery logic
- anchored micro-movement polish
- autonomous infrastructure behavior

### Remove From Target Scope

- deep social memory simulation
- relationship graph simulation
- emotional chain reactions through families and households
- partner-based social staging
- dense household clustering logic
- autonomous hamlet life simulation
- autonomous remote settlement expansion
- high-detail recovery and retry theory across many intent families
- any feature that requires frequent NPC-to-NPC evaluation to look correct

## Cheap NPC Model

Every NPC should be understandable as one of a few states.

### Main States

- `WORK`
- `EAT`
- `GO_HOME`
- `REST`
- `HIDE`
- `IDLE`

Optional states are allowed only if they are cheap and clearly useful.

Examples:

- `SEEK_SUPPLIES` may stay if it is really just a work or food fallback
- `DEFEND` may stay only for restricted roles

### State Priority Philosophy

The logic should be obvious:

- if it is work time and the NPC has a real assignment, work wins
- if hunger is high, food wins
- if it is night or fatigue is high, home or rest wins
- if danger is high, hide wins
- if nothing else is needed, idle or light wander wins

That is enough.

The system should not require dozens of micro-rules to produce a believable day.

## Update Budget Rules

NPCs must not think at full resolution every tick.

### Rules

- expensive intent selection should run on a heartbeat, not every tick
- current tasks should keep running between decision updates
- path recalculation should happen only when the target meaningfully changed or navigation clearly failed
- world lookups must prefer cached settlement data over live scans
- social reasoning must never require broad nearby-entity analysis for large crowds

### Forbidden Patterns

- scanning many nearby NPCs every tick to find the best social partner
- recomputing household, family, memory, and emotion effects every tick
- repeatedly trying alternate route families in the same short window
- spawning new work areas or new social structures as ordinary NPC behavior

## Data Model To Keep

These data are cheap enough and useful enough to keep:

- resident UUID
- home building UUID
- work building UUID
- coarse role
- current coarse state
- hunger
- fatigue
- danger or safety pressure

These data may remain only as light metadata, not as heavy runtime drivers:

- household ID
- spouse or parent or child links
- housing request state
- simple housing pressure label

## Data Model To Stop Treating As Runtime AI Drivers

The following can exist for flavor, UI, or save compatibility, but should not continuously drive behavior for large populations:

- trust
- fear as a social-memory network value
- anger as a relationship network value
- gratitude
- loyalty
- durable social memory chains
- multi-step remembered grievance propagation

If these remain in code, they should be demoted to lightweight labels or occasional modifiers, not constant simulation inputs.

## Social Behavior Policy

Social behavior is no longer a major system.

It becomes a cheap presentation layer.

### Allowed

- idle at home in evening
- idle near a public spot sometimes
- look at nearby NPCs occasionally
- small random variation in where an NPC stands

### Not Allowed As Core Runtime

- active partner selection loops
- companion stickiness systems
- household gathering choreography
- social pressure trying to outrank real work during the labor window
- fine-grained social scene management near the player

In short:

NPCs may appear social.

They should not be powered by expensive social simulation.

## Work Behavior Policy

Work is one of the main reasons these NPCs exist, so work behavior should be much simpler and stronger than social behavior.

### Rules

- assigned workers should prefer work during work time
- unassigned residents may idle or wander
- work should lose only to strong survival pressure such as hunger, sleep, or danger
- post-shift social or idle behavior is fine after work time ends

### Anti-Goal

We do not want a system where workers constantly look psychologically rich but fail to do useful work.

Useful and predictable work is more important than expressive social nuance.

## Home Behavior Policy

Home is still important, but home logic must stay cheap.

### Keep

- sleep at home at night
- go home when tired enough
- go home when danger is high enough
- simple homeless handling

### Remove

- large stacks of home-recovery hysteresis rules
- fine family-gravity tuning
- companion-based indoor clustering as a requirement
- repeated home-route reinterpretation for tiny state changes

The player only needs to understand that an NPC has a home and tends to return there.

That is enough for the illusion.

## Family And Household Policy

Family and household data may stay only where they help with:

- housing assignment
- family-tree UI
- identity flavor

They should not stay as a deep behavior engine.

### Keep

- household membership as data
- family-tree inspection
- home capacity and housing pressure

### Remove As Simulation Drivers

- dependent-aware behavior in many intent branches
- family-linked fear propagation as a central runtime system
- household social gravity as a major scheduling factor
- family-based companion clustering as a standard behavior requirement

## Hamlets And Remote Expansion Policy

Hamlets are the clearest part of the old plan that must be demoted.

### Keep

- ruler-visible records
- manual naming or registration if already created by controlled gameplay
- simple lot or housing metadata if needed

### Remove From Main AI Target

- autonomous remote family settlement growth
- routine remote plot reservation far from the core settlement
- hamlet maturation as a broad live simulation loop
- hamlet-driven pressure systems that constantly feed back into AI

Hamlets should be event-driven content, not a core always-on simulation layer.

## Autonomous World Mutation Policy

NPCs should rarely create new world structure on their own.

### Keep

- player-approved project execution
- explicit ruler-approved petitions
- bounded project pipelines

### Remove

- workers auto-creating starter fields during bootstrap
- widespread self-starting infrastructure behavior as a normal expectation
- NPC-led expansion as a core living-world loop

If NPCs change the world, it should be rare, explicit, and bounded.

## UI Policy

UI is worth keeping because it is cheap compared to simulation.

### Keep

- citizen profile
- worker profile
- simple AI state screen
- family tree screen
- petition ledger screens

### UI Content Goal

Show only:

- what the NPC is doing
- where it is going
- what blocked it
- what building or home it belongs to

Do not build UI that depends on an overcomplicated hidden AI just to justify itself.

## What Is Explicitly Deleted From The Old Direction

The following old direction is no longer the target:

- "smart society" as a major feature pillar
- memory-and-relationships as a core behavior driver
- deep social states for most residents
- near-player social staging as a major polish target
- many layered AI refinement slices that mostly exist to stabilize an overly complex model
- autonomous hamlet life and remote expansion as standard simulation

These ideas are not banned forever.

They are simply not acceptable under the current optimization goal.

## New Target Architecture

The intended architecture is:

1. settlement snapshot provides cached world facts
2. resident state machine chooses one coarse state on a heartbeat
3. resident executes that state cheaply between heartbeats
4. pathfinding is only refreshed on meaningful change
5. UI explains the current state clearly

That is the full loop.

If a future addition does not fit inside that loop cheaply, it should be rejected.

## New Phased Plan

### Phase A: Stabilize The Cheap Core

- keep work, home, rest, eat, hide, idle
- make work reliable during labor hours
- make home and rest reliable at night
- make danger interruption simple and stable
- remove obvious behavior thrash

### Phase B: Reduce Runtime Cost

- move decision updates to heartbeats where still needed
- reduce path refresh frequency
- cut broad entity scans
- remove hidden social complexity from ordinary residents

### Phase C: Simplify Data Usage

- keep family and household as metadata
- stop using memory and relationship depth as broad runtime scoring inputs
- preserve save compatibility where practical

### Phase D: Keep Only Cheap Observability

- maintain simple readable UI
- keep blocked reason and current task text
- keep ruler petition visibility

### Phase E: Reintroduce Optional Flavor Carefully

- only add flavor that is local, cheap, and optional
- never reintroduce full graph-based society simulation
- never make social polish outrank server performance

## Current Implementation Status

The cleanup described in this plan is already underway in the live root `src/**` code.

### Already Removed Or Heavily Cut Back

- deep social-memory runtime
- memory ledger UI
- trust / fear / anger / gratitude / loyalty as active runtime profile state
- socialise goal and dead socialise intent path
- hamlet runtime, hamlet UI, hamlet packets, hamlet commands, and hamlet saved-data classes
- hamlet-specific prefab and related remote-expansion code
- autonomous claim-growth starter field / fishing-area creation
- autonomous livelihood project enqueueing during ordinary settlement claim ticks
- recovery-heavy scheduler refresh behavior that kept rearming timed-out tasks

### Already Simplified

- resident scheduling now stays centered on coarse work / eat / go-home / rest / hide / idle behavior
- hide routing and UI wording now describe simple shelter seeking rather than fear-memory logic
- housing plot inspection tooling was reduced to a simple housing / household inspector instead of hamlet-aware logic
- claim-grown workers now reuse only existing work areas and otherwise wait with explicit missing-zone feedback
- citizen / worker UI and docs were updated to reflect the cheap-NPC direction instead of the older smart-society direction

### Intentionally Still Kept

- home assignment
- workplace assignment
- family-tree and household metadata where it helps housing and identity
- housing requests and ruler approval flows
- readable AI / citizen profile screens for current task, route, blocked reason, and needs

### Verification Already Run

- repository-wide cleanup removed code references to hamlet, social-memory, and socialise systems in active Java sources
- `compileJava` and `testClasses` pass when Gradle is run with a local JDK 21 toolchain

This means the repository is no longer merely planning this direction.

It is already being converted toward the cheap resident model described above.

## Acceptance Criteria

The system is successful when:

- many NPCs can exist without severe MSPT spikes
- assigned workers mostly work when they should
- residents go home or rest predictably
- danger causes clear fallback behavior
- the player can understand NPC state from the UI
- NPCs feel alive enough without requiring deep cognition

The system is not successful when:

- NPCs look psychologically rich but tank server performance
- workers constantly choose chatter over labor
- behavior depends on fragile chains of micro-rules
- pathfinding and social logic dominate tick cost
- each new refinement slice mostly exists to compensate for the complexity of earlier ones

## Final Summary

BannerMod should aim for believable, useful, low-cost residents.

It should not aim for a full social simulator inside Minecraft.

That older direction is the part we are now explicitly abandoning.

## Code Removal Targets

This section is intentionally blunt.

The items below are not just "low priority". They are the first things that should be cut back, removed, or demoted to metadata/UI if the goal is to support many NPCs safely.

### Remove Or Demote First

1. Deep memory-and-relationships runtime as a behavior driver

What to remove or demote:

- trust / fear / anger / gratitude / loyalty as constantly active scoring inputs
- durable social-memory propagation across family and household links
- remembered-event chains that alter everyday behavior for ordinary residents

Preferred replacement:

- keep these only as flavor labels, save compatibility data, or UI text

Commits that introduced or expanded this direction:

- `61252670` `feat(society): add phase three social memory runtime`

2. Partner and household-companion social choreography

What to remove or demote:

- social partner preference systems
- household companion preference systems
- family clustering and household clustering as required runtime behavior
- home-social stickiness whose purpose is mostly scene polish

Preferred replacement:

- cheap public-idle or home-idle behavior with only occasional look-at-nearby-entity flavor

Commits that expanded this direction:

- `6f16d5e4` `update society AI home recovery and social staging`
- `1ea18c9b` `update society AI recovery fallback and household readability`
- `de1fd465` `update society AI recovery lock and home regrouping`
- `b97242a2` `update society AI near-player stability and home regrouping`
- `47f555e9` `update society AI home calm and route recovery`

3. Heavy recovery and anti-thrashing refinement layers

What to remove or simplify:

- failure-family penalties
- sibling retry suppression
- recovery locks
- route-recovery windows
- threat-settle bridge windows
- many overlapping hysteresis rules whose purpose is to stabilize a too-complex state machine

Preferred replacement:

- one short cooldown after failed tasks
- one simple rule for danger interruption
- one simple rule for work-vs-home-vs-food precedence

Commits that mark this heavy direction:

- `2806fdc1` `update society AI stability and explainability`
- `030b60d9` `update NPC society daily routine stability`
- `b6391bca` `update society AI recovery and explainability`
- `6f16d5e4` `update society AI home recovery and social staging`
- `1ea18c9b` `update society AI recovery fallback and household readability`
- `de1fd465` `update society AI recovery lock and home regrouping`
- `b97242a2` `update society AI near-player stability and home regrouping`
- `9391c41b` `update society AI calm recovery and readable routines`
- `4f3e9937` `update society AI runtime consistency and test hardening`
- `57b186f9` `update society AI invalidation recovery hardening`
- `f0feeff3` `update society AI calm recovery and readable routines`
- `47f555e9` `update society AI home calm and route recovery`

4. Autonomous livelihood world mutation as normal NPC behavior

What to remove or restrict:

- workers auto-creating starter fields as ordinary expected behavior
- workers auto-seeding new fishing areas as ordinary expected behavior
- any broad expectation that NPCs should create the infrastructure they need by themselves during routine play

Preferred replacement:

- player-marked or ruler-approved work areas only
- if autonomy remains at all, keep it rare, bounded, and event-driven

Commits that introduced or expanded this direction:

- `c05e0c9a` `feat(society): add ruler-approved livelihood requests and worker self-sufficiency`
- `a01b6b28` `feat(society): bootstrap autonomous claim livelihoods`

5. Remote hamlet autonomy and remote-family expansion as live simulation

What to remove or demote:

- remote-family autonomous expansion as a general system
- hamlet maturation as a broad live runtime loop
- hamlet pressure feeding back into normal resident AI
- remote settlement-style world mutation as routine behavior

Preferred replacement:

- hamlets may exist as manual or event-driven content records
- hamlet UI and naming can stay if runtime autonomy is cut down

Commits that introduced or expanded this direction:

- `001878bf` `feat(society): seed remote hamlet zemlyanka housing`
- `78aa0430` `feat(society): add hamlet runtime and war room ledger`
- `b0f76211` `docs(society): record hamlet ledger flow`

6. Family and household as major scheduling pressure on everyday behavior

What to remove or demote:

- dependent-aware branching across many intents
- family-linked fear and recovery pull as a major scoring driver
- household-gravity tuning as an ordinary scheduling requirement

Preferred replacement:

- keep family-tree UI and housing metadata
- do not let family logic dominate ordinary worker schedules

Commits that introduced or expanded this direction:

- `4952abe4` `feat(society): add household family runtime and gui`
- `622543dd` `feat(society): reserve and highlight family lots`

### Keep Even If The Above Is Cut Back

The following slices are still worth keeping after simplification:

- `dea21a23` `feat(society): add npc phase one-two foundations`
  - keep as the cheap base layer
- `10b9974e` `feat(society): complete phase two daily intent loop`
  - keep only the coarse work/eat/home/rest/hide structure, not every later refinement rule
- `58ff4776` `feat(society): rank housing petitions and clarify household pressure`
  - keep because petitions are cheap compared to AI

### Practical Cleanup Order

If runtime performance becomes the main priority, cleanup should happen in this order:

1. demote memory-and-relationship runtime effects
2. remove partner/companion social choreography
3. collapse recovery logic into a much simpler scheduler
4. stop autonomous livelihood world mutation from ordinary NPC behavior
5. demote hamlet autonomy into records/UI only
6. keep only the cheap core state machine plus readable UI

### Important Note On Commits

The commit list above is not a blind revert script.

Many later commits are mixed stabilization slices and may also contain useful bug fixes.

Use them as:

- history markers for where complexity was added
- search anchors for code removal
- grouping hints for future cleanup PRs

Do not assume the correct cleanup is always a raw revert. In many cases the right move will be selective removal or demotion of the expensive runtime logic while keeping harmless UI or persistence pieces.
