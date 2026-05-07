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
- A second daily-routine readability/stability refinement slice is now live:
  - the `GO_HOME -> REST` night loop now settles more cleanly instead of re-picking homeward movement for too long
  - the `LEAVE_HOME` morning bridge now yields into real work/social fan-out more clearly once the resident has stepped out of the house
  - routine intent selection now carries a lightweight intent-history / hysteresis layer so near-tied daily-life choices thrash less
  - social routing now prefers more readable gathering spots such as market / square / hall / hearth / tavern / well style anchors before falling back to a generic street cluster
  - citizen, worker, and dedicated AI screens now also expose a short route explanation in addition to the already existing chosen-goal reason
  - the refinement slice is covered by new unit tests plus focused GameTests for evening home social scenes, night settling, morning fan-out, and non-market square gathering
- A third AI stability / recovery / explainability refinement slice is now live:
  - the scheduler now keeps a small failure-memory record for the most recent resident goal outcome instead of instantly retrying the same broken path forever
  - timed-out or invalidated goals now enter a short backoff window so another safe routine can take over while the resident reassesses
  - family and memory pressure now pull harder on `GO_HOME`, `REST`, `EAT`, `SEEK_SUPPLIES`, `HIDE`, and `DEFEND`, while fear can suppress routine work more clearly under household pressure
  - citizen / worker routine summaries now explain the chosen action in a more human-readable “because” style instead of only repeating route text
  - the dedicated AI screen now also exposes compact current need pressure plus social-state pressure so players can tell whether fear, anger, fatigue, or hunger is driving the behavior
  - the refinement slice is covered by focused scheduler/scorer/snapshot tests for timeout backoff, blocked-goal recovery observability, stronger go-home stability, and dependent-aware hide behavior
- A fourth AI stability / household-readability / social-staging refinement slice is now live:
  - the scheduler now also soft-penalizes snapping straight back into the same failed intent family and gives short recovery weight to safer `GO_HOME`, `REST`, `EAT`, and `HIDE` fallbacks when a routine just broke
  - `ResidentGoalContext` and `NpcSocietyPhaseTwoIntentScorer` now treat recent blocked-goal failure as a first-class recovery signal, especially for family-linked residents with a valid home
  - citizen / worker routine summaries now foreground the visible route explanation, while the dedicated AI screen now leads with the readable route sentence and keeps the anchor as supporting detail instead of repeating it as the main line
  - evening / family social routing now leans more strongly toward home, and anchored social behavior now pulls more tightly toward nearby family or household companions for clearer small-group scenes near the player
  - the refinement slice is covered by compile validation plus focused scheduler / scorer / snapshot tests for family-home recovery bias, route-first explainability, and home-social stability
- A fifth AI recovery / food-fallback / household-staging refinement slice is now live:
  - `GO_HOME` can now act as a real daytime recovery fallback after a broken routine instead of waiting almost entirely for the evening return window
  - failed `EAT` attempts can now yield into `SEEK_SUPPLIES`, and supply access now also recognizes stockpile-backed fallback instead of only open market access
  - route selection and anchored execution now expose clearer regroup / rest-after-regroup / food-recovery explanations so the player can tell that the NPC is recovering rather than bugging out
  - household-near social behavior now holds tighter near home with denser family/household clustering instead of drifting outward too easily
  - the refinement slice is covered by compile validation plus focused scheduler / scorer / snapshot tests for daytime go-home recovery, failed-meal supply fallback, and recovering-state observability
- A sixth AI route-break / retry-hardening / recovery-readability refinement slice is now live:
  - `CONTEXT_INVALID` failures now back off a little harder than plain timeouts so residents do not immediately hammer the same broken route again
  - the scheduler now also soft-penalizes sideways retries inside the same work/logistics family after a broken work path, so a failed workplace route can yield into safer home recovery instead of bouncing into `FETCH` / `DELIVER` / `SELL`
  - the dedicated AI screen now labels the recovery-side blocked panel as the last broken goal so the player can more quickly tell what just failed
  - the refinement slice is covered by compile validation plus focused scheduler / snapshot / GameTest coverage for invalidated-path backoff, sibling-work retry suppression, and readable home-regroup observability
- A seventh AI recovery-lock / household-gravity / readable-recovery refinement slice is now live:
  - fresh safe fallback intents such as `GO_HOME`, `REST`, `HIDE`, `EAT`, and `SEEK_SUPPLIES` now hold a little more firmly right after a broken plan so residents do not instantly snap back into the same routine family on the next pick
  - `WORK`, `FETCH`, `DELIVER`, `SELL`, and early `SOCIALISE` now ease off more during that short recovery window instead of overriding regroup-at-home behavior through their floor priorities
  - failed `EAT` attempts now penalize immediate meal retry harder when supply access exists, so the resident more reliably switches into `SEEK_SUPPLIES` instead of hammering the same food path again
  - anchored home behavior now uses a small retarget deadband plus tighter household-companion clustering for `GO_HOME` / `REST` / `EAT` / `HIDE`, producing calmer near-home scenes and less visible micro-flipping
  - the dedicated AI screen now surfaces a compact “recovering after ...” line directly in the route panel so the player can see what just broke without parsing the lower blocked-goal box first
  - the refinement slice is covered by focused scheduler / scorer / snapshot tests for fresh home-recovery lock, failed-meal supply fallback, and readable recovery observability
- An eighth near-player stability / home-gravity / calm-social refinement slice is now live:
  - timed-out `GO_HOME`, `REST`, `HIDE`, `EAT`, `SEEK_SUPPLIES`, and household-near `SOCIALISE` slices can now refresh in place when they are still healthy instead of automatically poisoning the resident with another fake broken-plan failure every few ticks
  - home fallback scoring now pulls harder after invalidated work/routine routes and suppresses fresh work/social bounce-back more clearly while the resident is still trying to regroup
  - failed meal recovery now pivots more aggressively into `SEEK_SUPPLIES`, especially when the previous meal path was invalidated or the settlement only has stockpile-backed fallback instead of an open market meal
  - anchored home and household-social behavior now repaths less often, accepts a wider small deadband before retargeting, and clusters more tightly around nearby family/household companions for calmer near-player scenes
  - the dedicated AI screen now shows the recovery origin together with the broken-goal reason in the route panel so players can tell not just what failed, but why the NPC is regrouping
  - the refinement slice is covered by compile validation plus focused scheduler / scorer / snapshot tests for safe-slice refresh, stronger home-recovery suppression of bounce-back, and invalidated-meal supply fallback
- A ninth near-player calm / readable-recovery / food-loop refinement slice is now live:
  - safe recovery intents now hold longer and resist premature bounce-back more strongly, especially for `GO_HOME`, `REST`, `SEEK_SUPPLIES`, and household-near `SOCIALISE`
  - household-near social scenes now keep a stronger stay-put bias instead of flipping back into work-family retries too quickly
  - failed meal recovery now escalates more reliably from `EAT` into `SEEK_SUPPLIES`, especially when only stockpile-backed food access exists, and recovering supply runs no longer snap straight back into the same broken meal path
  - near-home anchored behavior now repaths less often, uses a wider deadband, and blends more calmly around nearby household companions for steadier home/family scenes near the player
  - citizen / worker / dedicated AI screens now explain recovery in one short readable line, and current / blocked goals are shown with player-readable localized labels instead of raw internal goal ids
  - the refinement slice is covered by compile validation plus focused scheduler / scorer / snapshot tests for household-social stability, supply-recovery lock, and readable AI route summaries
- A tenth near-player routine-calm / threat-settle / plain-language readability refinement slice is now live:
  - healthy timed-out `WORK` and non-household `SOCIALISE` slices can now refresh in place instead of constantly turning into fake failures and forcing unnecessary re-picks
  - post-danger settle behavior now holds `HIDE -> GO_HOME/REST` more calmly for a short window so residents do not snap straight back into `WORK` or `SOCIALISE` the moment fear starts falling
  - home/family anchors now use wider home arrival and companion deadbands plus slower home-near repath cadence, reducing visible micro-flips and tiny indoor retarget jitter near the player
  - supply-run explainability now distinguishes true homeless food fallback from “home exists but food is short”, while route/choice text was simplified toward short player-readable lines such as tired homeward return, safer hiding, and home food shortage
  - the refinement slice is covered by compile validation plus focused scheduler / scorer / snapshot tests for healthy work refresh, calm daytime social refresh, post-threat home settle suppression of work bounce-back, and stockpile-backed home food shortage explanation
- An eleventh AI interruption / runtime-consistency / test-hardening slice is now live:
  - household-near `SOCIALISE` no longer blindly refreshes through urgent hunger or danger pressure; fresh home/family social scenes now yield into `EAT` or `HIDE` when those needs become dominant instead of reading as stuck calm chatter
  - settlement home/household reconciliation no longer wipes live phase-one intent state back to `UNSPECIFIED` during ordinary claim ticks, so externally published routine behavior survives the home-assignment pass instead of momentarily losing its route/intent identity
  - externally reconciled active routine state now synthesizes a minimal executing decision snapshot when needed, keeping scheduler, observability, and anchor movement in sync for manually seeded or recovery-published behavior instead of storing contradictory “active intent but no current goal” state
  - anchored routine execution now starts movement immediately on goal start, hold-position logic more aggressively stops stale formation navigation after cross-dimension orphaning, and public/home social anchors now route more directly to the intended named gathering spot instead of orbiting a looser street-side offset first
  - society ruler/ledger packet paths for housing and hamlets now enqueue onto the main thread explicitly, bounded society `SavedData` now carries the same `DataVersion` v1 plumbing as the rest of the runtime, and the Java test harness now forces `UTF-8` plus URI-based classpath scanning so Russian contract strings and Windows path discovery no longer fail spuriously during verification
  - the slice is covered by new scheduler / scorer / snapshot tests for urgent social interruption plus full unit-suite validation; live GameTest follow-up narrowed from several society/runtime regressions down to the remaining authored courier-route execution failure
- The first dedicated household and family slice is now live:
  - household membership is stored separately from the home building id
  - household housing state now distinguishes settled, homeless, and overcrowded households
  - family GUI observability now exists for citizens and workers
  - citizen and worker inspection now also expose the current household head plus a compact housing-pressure explanation directly in the base profile screens
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
  - housing petitions are now also ranked through one shared server-side fairness scorer that accounts for homelessness, overcrowding, household size, waiting age, and current request state
  - the `U` War Room path now also exposes a dedicated housing ledger screen so rulers can review and resolve the same ranked petition queue without staying chat-command-only
  - settlements can now also raise ruler-approved livelihood requests for `lumber camp`, `mine`, and `animal pen`
  - approved livelihood requests now flow into the prefab project path with exact prefab ids instead of only coarse growth categories
  - settlement-spawned workers now start with baseline profession tools, auto-bind to compatible existing claim work areas more aggressively, and can craft replacement stone tools for themselves at nearby crafting tables when materials are available
  - claim-grown farmers now also seed a starter field for themselves when the claim has no prepared crop area yet, and claim-grown fishermen can seed a fishing area from nearby water instead of idling
- The first family-lot observability slice is now live:
  - starter-fort bootstrap now seeds 2-4 family households instead of only flat identical free adults
  - approved housing petitions now reserve an explicit family lot inside the claim and the finished house is handed back to that requesting household first
  - the `Kinlot Staff` / `Родовая межа` now highlights the nearest reserved family lot while held and renders a floating household label over it
- The first bounded hamlet-housing slice is now live:
  - exported vanilla `structure block` `.nbt` house templates can now flow through the internal prefab/build-area path
  - the first player-authored `землянка` / `zemlyanka` template is now shipped as a real prefab-backed hamlet house
  - ordinary fort housing still uses the existing compact `HousePrefab`; the new zemlyanka path is reserved for remote hamlet-family placement only
  - pressured family households can now reserve housing plots 3-4 claim chunks away from the settlement anchor instead of only near the fort center
  - approved remote-family plots now place a fenced homestead version of the zemlyanka with a small yard/gate/pen slice instead of only the old flat fort house footprint
- The first persisted hamlet runtime slice is now live:
  - settled remote-family zemlyanka homesteads can now mature into named hamlets with explicit `INFORMAL`, `REGISTERED`, and `ABANDONED` state
  - hamlet identity is now persisted separately from the raw housing request and can cluster multiple nearby remote households under one hamlet record
  - rulers can inspect and formalize hamlets through `/bannermod society hamlet list`, `register`, and `rename`
  - the `U` War Room path now exposes a dedicated hamlet ledger screen in the same parchment-style UI instead of forcing ruler observability to stay chat-command-only
  - `Kinlot Staff` / `Родовая межа` can now surface hamlet identity in addition to household lot state once a reserved lot becomes a real hamlet homestead
  - hostile block-breaking against an inhabited informal hamlet now leaves durable social memory instead of only deleting blocks silently
  - active hamlets can now push a first food-support hint through the existing livelihood request path by pressuring `animal pen` requests
- The next approved execution priority is now explicitly narrowed:
  - do not expand broad new social feature count first
  - finish near-player AI stability, readable fallback behavior, and stronger home/family-centered routine logic first
  - treat religion, unrest depth, lineage growth, and wider hamlet autonomy as follow-up work until everyday resident behavior is calm, understandable, and reliable
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
  - household head identity and the resident's current household role
  - compact housing-pressure cause/urgency context instead of only raw request state
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
- The next readability/stability refinement is now also live in code:
  - `ResidentGoalContext` now distinguishes active, leisure, departing-home, returning-home, and rest transitions more explicitly
  - `NpcSocietyDecisionSnapshot` now also persists the last intent, the start time of the current intent, and a compact route-reason tag for GUI observability
  - `NpcSocietyPhaseTwoIntentScorer` now adds small history-aware stability pressure plus stronger late-evening / early-morning routine shaping
  - `BannerModResidentGoalScheduler` now allows the home-return loop to settle into `REST` and the leave-home bridge to fan out into real daytime intents sooner
  - `NpcSocietyAnchorGoal` now routes `SOCIALISE` through a dedicated spot selector instead of only generic market-or-street fallback
  - `NpcSocietySocialSpotSelector` now resolves compact named gathering anchors from existing settlement building records without introducing a second world-POI subsystem
  - `CitizenProfileScreen`, `WorkerStatusScreen`, and `NpcAiDecisionScreen` now surface a short player-readable “why this NPC is going there” route line instead of showing only the abstract chosen-goal reason
- A further anti-thrashing / recovery / explainability refinement is now also live in code:
  - `BannerModResidentGoalScheduler` now remembers the most recent goal outcome, applies short failure backoff after `TIMED_OUT` / `CONTEXT_INVALID`, and soft-penalizes immediately re-picking the same failed goal
  - the same scheduler now gives fresh in-progress intents a little more switch resistance while still relaxing that resistance once an intent has already run for a while
  - `NpcSocietyPhaseTwoIntentScorer` now applies wider intent-history stability across home / rest / eat / work / supply / hide / defend instead of only the earlier social-only stickiness
  - family/dependent pressure now influences fearful defenders more conservatively so “I have children, I should hide first” can beat pure anger in some edge cases
  - `NpcSocietyDecisionSnapshot` can now surface recent timeout / invalid-context recovery as a blocked-goal reason instead of hiding that failure from the player
  - `NpcSocietyPhaseOneRuntime` now publishes more readable route reasons such as `EVENING_HOME_CIRCLE`, `WORKING_FOR_HOUSEHOLD`, and `HIDING_CLOSE_TO_HOUSEHOLD`
  - `NpcAiDecisionScreen` now also shows compact current needs plus trust/fear/anger/loyalty pressure to make AI state easier to read at a glance
- A further family-home recovery / route-first readability refinement is now also live in code:
  - `ResidentGoalContext` now exposes compact recent blocked-goal recovery state so scheduler, scorer, and GUI explanation can all react to the same failure signal instead of only the raw active intent
  - `BannerModResidentGoalScheduler` now gives short recovery preference to safer home/rest/hide/eat follow-ups after a failed routine and soft-penalizes bouncing immediately into another goal from the same failed intent family
  - `NpcSocietyPhaseTwoIntentScorer` now pulls tired family-linked residents home more aggressively after recent failures, suppresses immediate fresh work/social retries after the same intent just broke, and strengthens evening family-home social pull
  - `NpcSocietyDecisionSnapshot`, `CitizenProfileScreen`, `WorkerStatusScreen`, and `NpcAiDecisionScreen` now present route-first explanations more directly so players see where the NPC is trying to go before the lower-level goal id detail
  - `NpcSocietyAnchorGoal` now blends social targets toward nearby partners and prefers nearby family/household companions for home arrival scenes, producing tighter visible clusters instead of flatter lone loitering
- A further daytime-recovery / food-run fallback / recovering-state readability refinement is now also live in code:
  - `GoHomeResidentGoal` now allows a true daytime regroup-at-home fallback after recent routine failure for residents with a valid home instead of keeping that path almost entirely night-gated
  - `ResidentGoalContext`, `NpcSocietyPhaseTwoIntentScorer`, and `BannerModResidentGoalScheduler` now treat failed meal attempts as a first-class recovery case that can shift from `EAT` into `SEEK_SUPPLIES`
  - supply access now also recognizes stockpile-backed fallback, and anchored `SEEK_SUPPLIES` movement now routes toward that fallback path instead of only assuming open-market access
  - `NpcSocietyDecisionSnapshot` now exposes an explicit `RECOVERING` state for active fallback behavior, while citizen / worker / dedicated AI screens surface that state more directly in routine summaries
  - route explanations now cover `REGROUPING_AT_HOME`, `RESTING_AFTER_REGROUP`, `FOOD_RECOVERY_RUN`, `HOUSEHOLD_YARD_GATHERING`, and `HOUSEHOLD_RECOVERY_CIRCLE` so fallback behavior reads clearly to the player
- A further route-break / retry-hardening / recovery-readability refinement is now also live in code:
  - `NpcSocietyDecisionSnapshot` now exposes shared blocked-reason tags for `TASK_TIMED_OUT` vs `CONTEXT_INVALIDATED` so scheduler, GUI, and tests stop relying on scattered raw string literals
  - `BannerModResidentGoalScheduler` now applies a stronger short backoff after `CONTEXT_INVALID`, and it also soft-penalizes sibling `WORK` / `SELL` / `FETCH` / `DELIVER` retries after the same broken work-family route instead of bouncing sideways into another near-identical failure
  - `NpcAiDecisionScreen` now reframes the recovery-side blocked panel as the last broken goal so the player can read the failed plan and the active regroup path together more quickly
  - focused scheduler / snapshot tests plus a dedicated GameTest now cover invalidated-path backoff, home-regroup recovery after a broken work route, and readable recovery observability
- A further recovery-lock / household-gravity / readable-recovery refinement is now also live in code:
  - `ResidentGoalContext`, `NpcSocietyIntentRules`, `NpcSocietyPhaseTwoIntentScorer`, and `BannerModResidentGoalScheduler` now treat fresh safe fallback intents as a short stabilization window instead of letting residents bounce straight back into `WORK` / `FETCH` / `DELIVER` / `SELL` / fresh `SOCIALISE`
  - `GoHomeResidentGoal` now allows broken routine families to fall back into home regrouping more broadly whenever a valid home exists, while failed meals now push harder away from immediate `EAT` retry and toward `SEEK_SUPPLIES`
  - `WorkResidentGoal`, `FetchResidentGoal`, `DeliverResidentGoal`, `SellerResidentGoal`, and `SocialiseResidentGoal` now drop their floor-priority pressure during that fresh recovery window so safe fallback paths can actually stay in control long enough to read well in play
  - `NpcSocietyAnchorGoal` now keeps near-home targets steadier and pulls `GO_HOME` / `REST` / `EAT` / `HIDE` behavior a little closer to nearby household companions, producing calmer family/home scenes instead of tiny repath oscillation
  - `NpcSocietyPhaseOneRuntime` now keeps post-failure `HIDE` routing household-near whenever a home anchor exists, while `NpcAiDecisionScreen` shows a direct recovery-origin line in the route panel
  - focused scheduler / scorer / snapshot tests now cover fresh home-recovery lock plus the stronger failed-meal supply fallback path
- A further near-player stability / home-gravity / calm-social refinement is now also live in code:
  - `ResidentGoalContext` now exposes refresh checks for safe recovery intents and household-near social scenes so stable regroup/home/social slices do not automatically age into fake path-failure memory
  - `BannerModResidentGoalScheduler` now refreshes healthy timed-out `GO_HOME` / `REST` / `HIDE` / `EAT` / `SEEK_SUPPLIES` / household-near `SOCIALISE` slices in place instead of always recording another `TIMED_OUT` failure when the NPC is simply still carrying out the same readable fallback
  - `NpcSocietyPhaseTwoIntentScorer` now pulls broken daytime routines home harder after invalidated routes, suppresses fresh work/social rebound more during that regroup window, and shifts invalidated meal retries more strongly toward `SEEK_SUPPLIES`
  - `NpcSocietyAnchorGoal` now uses a wider home/social retarget deadband, slower home-near repath cadence, and tighter partner blending for family/home scenes so near-player behavior looks calmer and less twitchy
  - `NpcSocietyPhaseOneRuntime` plus `NpcSocietyDecisionSnapshot` now explain those home and household-social fallback choices more consistently, while `NpcAiDecisionScreen` now includes the broken-goal reason directly in the recovery route line
- A further near-player routine-calm / threat-settle / plain-language readability refinement is now also live in code:
  - `ResidentGoalContext` now exposes refresh checks for healthy `WORK` and ordinary daytime `SOCIALISE` slices so readable routine behavior does not create false timeout-memory just because a short task window elapsed
  - the same context now also exposes a compact post-threat settle window so recent `HIDE` pressure can keep `GO_HOME` / `REST` in control briefly while the resident calms down near home instead of rebounding instantly into routine labor or chatter
  - `BannerModResidentGoalScheduler` now refreshes healthy timed-out `WORK` and non-household `SOCIALISE` tasks in place, and it also raises the switch margin from rest-like intents back into routine intents during that short post-threat settle window
  - `NpcSocietyPhaseTwoIntentScorer` now gives extra short-lived weight to post-threat `GO_HOME` / `REST` / `HIDE` and suppresses immediate `WORK` / `SOCIALISE` bounce-back more clearly when the resident is still settling after danger
  - `NpcSocietyAnchorGoal` now uses wider home arrival radius, wider home/social target deadbands, and slower home-near repath timing so indoor home scenes and household clustering read more steadily near the player
  - `NpcSocietyDecisionSnapshot` now distinguishes stockpile-backed household shortage from true no-home food fallback through `HOME_FOOD_SHORTAGE`, while `NpcSocietyPhaseOneRuntime` now also exposes a dedicated `TIRED_HOMEBOUND` route and the AI localization strings were shortened into plainer player-facing explanations
- A further AI interruption / runtime-consistency / verification-hardening refinement is now also live in code:
  - `ResidentGoalContext` now treats urgent hunger or serious danger as a hard interruption to household-near social refresh/hold, so a calm family scene can stop cleanly when survival pressure really changes instead of overriding `EAT` / `HIDE`
  - `BannerModSettlementClaimTickService` now preserves already-published phase-one daily phase / intent / anchor / decision state when reconciling home and household metadata, preventing ordinary settlement ticks from briefly erasing live behavior back to `UNSPECIFIED`
  - `NpcSocietyRuntime` now normalizes externally reconciled non-idle routine state into a minimal executing snapshot when no current-goal metadata was supplied, which keeps anchor execution, scheduler stickiness, and GUI observability aligned for manually seeded or recovery-published routines
  - `NpcSocietyAnchorGoal` now starts its first navigation step immediately, public `SOCIALISE` routing through square/market-style anchors keeps the selected civic spot itself as the target instead of always adding a second street offset first, and worker anchor/home goals now explicitly yield when a courier route is already active so logistics movement is not stolen by background routine anchors
  - `RecruitHoldPosGoal` now stops stale navigation more aggressively once a recruit is already effectively at hold position or the formation leader has become cross-dimension-invalid, reducing the residual one-step drift that remained after earlier dimension-orphan guards
  - housing / hamlet civilian packets now use explicit `context.enqueueWork(...)` main-thread handoff, society `SavedData` classes (`NpcSocietySavedData`, household/family/memory/housing/livelihood/hamlet) now all stamp and migrate `DataVersion`, and the unit-harness infrastructure now uses `UTF-8` Java compilation plus URI-based classpath scanning so Windows path handling and localized contract strings validate consistently
- House self-build has a first backend path:
  - households in housing pressure can create housing requests
  - requests are stored in dedicated saved data
  - requests are now keyed by household, with a representative resident retained for GUI/notifications
  - requests now notify the lord and wait for explicit approve/deny instead of silently auto-approving
  - request ranking now runs through `NpcHousingPriorityService` so command/chat/GUI observability all share the same fairness order and urgency explanation
  - approved requests become `PendingProject` house builds
  - project execution reuses the existing `HousePrefab` and settlement build-area pipeline
  - approved requests now also reserve a concrete family lot position in the claim, surface that lot in ruler-facing chat/command observability, and try to place/return the completed house back onto that lot for the same household
- The first bounded hamlet-housing execution slice is now live:
  - `StructureTemplateLoader` now also converts exported vanilla `structure block` `.nbt` templates into the internal sparse BuildArea structure format instead of only importing `.litematic` / `.schem`
  - the first shipped player-authored template lives at `assets/bannermod/structures/zemlyanka.nbt`
  - `settlement/prefab/impl/HamletZemlyankaPrefab.java` wraps that template in a fenced homestead lot so the remote-family slice places a real yard instead of only bare house walls
  - `NpcHousingPlotPlanner` now distinguishes fort-near plots from remote hamlet plots and only offers the 3-4 chunk remote band to pressured multi-member households
  - `NpcHousingProjectPlanner` now routes those remote-family housing projects through the dedicated hamlet zemlyanka prefab while preserving the older compact `HousePrefab` for near-fort housing
- The first persisted hamlet runtime slice is now live in code:
  - `NpcHamletSavedData` and `NpcHamletRuntime` persist claim-adjacent hamlet records separately from households and housing requests
  - a hamlet record now stores name, anchor, founder household, linked household homes, registration state, and hostile-action cooldown state
  - settlement home assignment now reconciles eligible remote-family households into those hamlet records instead of leaving remote zemlyankas as anonymous houses in the field
  - society commands now expose `hamlet list`, `hamlet register`, and `hamlet rename`
  - `Kinlot Staff` now shows hamlet name/status when a reserved family lot has already matured into a hamlet
  - `NpcSocietyEvents` plus `NpcMemoryAccess` now treat hostile player block-breaking near inhabited informal hamlets as a real remembered social event
- A first ruler-approved livelihood-infrastructure path now exists:
  - settlements can create dedicated saved-data requests for `lumber camp`, `mine`, and `animal pen`
  - requests are keyed by claim plus livelihood type rather than being folded into generic growth hints
  - approved requests now become exact-prefab `PendingProject` entries instead of falling back to a coarse category guess
  - the first shipped slice intentionally bootstraps the approved livelihood build immediately after placement so the village does not deadlock on “needs tools/resources before it can build the workplace that would produce those resources”
- Worker self-sufficiency now has a first live runtime path:
  - settlement-spawned workers start with baseline stone profession tools
  - worker bootstrap now reuses existing compatible claim work areas for farmer, miner, lumberjack, fisherman, and animal-farmer paths where possible
  - if no prepared crop area exists, a claim-grown farmer now lays out a starter field and binds to that new crop area instead of waiting for manual prep
  - if nearby water exists, a claim-grown fisherman now seeds a fishing area and starts using it instead of staying permanently area-less
  - workers can now craft replacement stone tools for themselves at nearby crafting tables when they can obtain wood and cobblestone through their current inventory/storage flow
  - this first slice covers basic survival tools only; it is not yet a full smithing or workshop economy
- A first real family identity slice now exists in persisted code:
  - `NpcFamilySavedData` and `NpcFamilyRuntime` persist family records per resident
  - family records now carry spouse, mother, father, and child UUID links
  - households now also carry a persisted head resident UUID
  - family links are no longer rebuilt only for GUI display; they are now stored and preserved across later reconciles
  - starter bootstrap now seeds first households directly into this family/household runtime instead of only relying on later passive reconcile to infer all early settlement families

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
  - manual approve/deny now exists in chat-command, chat-action, and dedicated ledger-GUI slices
- Household housing requests are now household-driven, but they are still incomplete:
  - a first shared fairness queue now exists for competing households, but it is still intentionally lightweight and does not yet model reserves, prestige, or dynasty policy
- House self-build currently reuses the existing settlement builder pipeline; it is not yet a full citizen-driven gather-carry-place loop owned by the requesting household.
- Family-lot rendering is now visible through the `Kinlot Staff`, but it is still intentionally lightweight:
  - the highlighted lot is a reserved plot marker, not a full parcel-survey polygon system
  - the floating label can now also surface the hamlet identity slice after settlement, but it is still not a deep surname/lineage naming system
- Livelihood self-build is now live in a first practical slice, but it is still intentionally coarse:
  - requests currently cover only `lumber camp`, `mine`, and `animal pen`
  - the village currently asks the ruler first, then uses prefab-backed project placement instead of emergent freeform site planning
  - the first shipped slice grants immediate build completion after ruler approval to break bootstrap deadlocks; it does not yet prove a full resource-haul-and-place construction loop
  - the first persisted hamlet runtime now exists: remote family homesteads can become named hamlets, rulers can register them, and player destruction of inhabited informal hamlets now leaves memory consequences
  - however, the hamlet slice is still intentionally bounded: it does not yet provide independent polity, a full local self-sufficient economy, deep parcel surveying, or a true off-fort migration AI that deliberately moves under-employed households to an existing hamlet anchor before housing is built
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
  - head-of-household state is now visible in the base citizen/worker profile screens, but it is still not surfaced as a dedicated field inside the family tree screen itself
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
- That scoring system must also understand recent failure, short backoff, and safe fallback preference instead of only raw need pressure.

### 3. Add A Real Execution Layer For Daily Life

- Society intent should no longer stop at labels in GUI.
- Residents should physically:
  - walk home
  - remain near home during rest
  - gather at market, street, or household-near anchors depending on time and pressure
  - run cheap social scenes
- This should remain server-authoritative and piggyback on the current low-level entity behavior where possible.

### 3A. Make Broken Plans Recover Gracefully

- The next AI pass should treat failed goals as a first-class gameplay problem, not a small tuning issue.
- When a route, work task, or context-dependent routine breaks, the resident should not thrash or instantly retry forever.
- The recovery path should prefer cheap, readable, safe fallbacks such as:
  - `GO_HOME` when the resident has a valid household anchor and no stronger threat blocks that move
  - `REST` when fatigue or night pressure is high
  - `HIDE` when fear or danger dominates
  - `EAT` or `SEEK_SUPPLIES` when hunger is the main unresolved pressure
- Recovery should be visible both in behavior and in GUI explanation so the player can tell that the NPC is regrouping instead of bugging out.

### 3B. Treat Home And Family As The Default Gravity

- Home and household should be the default stabilizer for uncertain or interrupted daily-life behavior.
- Stronger home/family pull is especially required for:
  - evening return
  - night rest
  - fear and post-failure regrouping
  - hunger or supply stress
  - socializing when public anchors are weak, blocked, or too far away
- Social behavior should prefer household-near scenes, family-near scenes, or small nearby clusters before wider settlement wandering whenever that still satisfies the current need.
- Homeless and overcrowded states should stay visible and matter to routing, but should not make NPC behavior look random or permanently broken.

### 4. Rework House Construction Into A True Social Loop

- The current implementation proves that residents can request and trigger house projects.
- A first bounded observability/prioritization step of that direction is now live:
  - rulers can open a dedicated housing ledger UI from the `U` War Room path
  - `/bannermod society housing list` and the ledger now share one server-side fairness order instead of ad-hoc severity sorting
  - petitions now surface an explicit urgency band and primary priority reason in addition to raw request status
- The next version should add:
  - reservation of newly built homes for the requesting resident or household
  - direct linkage between household shortage and project urgency
  - clearer use of resource gathering and hauling before or during build execution

### 4A. First Hamlet Autonomy Slice

- The next concrete execution slice after the current worker-autonomy pass should be a bounded `hamlet` runtime rather than a freeform rewrite of all settlement AI.
- That slice should stay near the existing claim and reuse current ownership, housing, livelihood-request, and memory systems.
- A first partial execution step of that direction is now live:
  - pressured multi-member households can already drift into a remote 3-4 chunk housing band
  - those remote household housing projects can already resolve to a dedicated player-authored zemlyanka homestead prefab instead of the default fort house
  - the first shipped slice deliberately stops at remote housing placement plus fenced lot presentation; it does not yet persist a standalone hamlet record or full local economy
- Minimum deliverables for that slice:
  - persist a small claim-adjacent hamlet record with anchor, founder household, and registration state
  - let unassigned or under-employed households drift to a nearby hamlet anchor when local housing/work pressure stays high
  - let the hamlet raise its own first food/housing needs through the existing request pipeline instead of inventing a second economy system
  - allow the player ruler to formally register the hamlet into the parent claim/settlement flow, or leave it informal
  - treat player destruction of an unregistered but inhabited hamlet as a negative remembered event that raises fear/anger in linked households
- Non-goals for that first hamlet slice:
  - full off-claim sovereignty
  - deep parcel surveying
  - independent political entities
  - complete hunting/foraging simulation before food autonomy near the claim is stable

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

## Immediate Priority Reset

The next major work should not be a breadth expansion. It should be a quality pass over the AI that already exists.

### Priority 1. AI Stability And Recovery

- remove remaining thrash loops, indecisive route flipping, and blind retry behavior
- make blocked or timed-out goals fall into readable safe fallback behavior
- keep recovery cheap, local, and server-authoritative rather than inventing a second planner

### Priority 2. Player-Readable Behavior

- make it easy to understand why an NPC chose the current action
- surface route, reason, and recent failure/recovery in compact GUI language
- strengthen visible morning, evening, homecoming, rest, and regroup scenes over hidden math

### Priority 3. Family/Home-Centered Logic

- make home, household, dependents, and housing pressure matter more in routine selection
- prefer family-near social scenes over abstract town-center wandering when both satisfy the same need
- make fear, fatigue, hunger, and household instability pull residents back toward safer household behavior sooner

### Explicitly Deprioritized Until The Above Feels Good

- deeper religion gameplay
- broader unrest escalation
- large new hamlet autonomy systems
- child-growth expansion beyond what is needed for household readability
- additional hidden needs that do not create obvious visible behavior

## Purpose

BannerMod already has workers, citizens, recruits, settlements, politics, and war. What it does not yet have is a convincing medieval society. Current NPCs are still too close to task executors attached to buildings or command state.

This document now focuses on one narrower target: make NPCs feel intelligent, readable, and socially grounded in normal Minecraft play.

The target is not "maximum realism" or "more AI for its own sake". The target is a readable, reactive, scalable medieval society that:

- feels alive near the player
- explains itself through visible behavior and GUI observability
- reacts to family, home, danger, hunger, and player actions
- remains affordable at settlement scale

Anything that adds hidden complexity without strong visible gameplay value should be delayed or removed.

## North Star

NPCs should stop feeling like automation nodes and start feeling like people who:

- belong to a home, family, and settlement first, with wider identity systems added only after the core daily-life loop is solid
- remember what happened to them and to their relatives
- react to the player as a social and political actor, not just as a nearby entity
- can cooperate, comply, resist, flee, or retaliate in understandable ways
- continue to make sense under multiplayer and server-authoritative rules

The practical design goal is closer to "Kingdom Come feeling inside Minecraft constraints" than to a full historical-society simulator.

## Success Threshold

The simulation is "alive enough" when a player can explain why an NPC is where it is, what it is trying to do, and what safe fallback it will take when the current plan breaks.

Minimum believable threshold:

- NPCs have a day and night routine.
- NPCs have homes and family links.
- NPCs recover from broken goals without obvious thrashing or permanent confusion.
- NPCs remember violence, theft, hunger, and protection.
- NPCs talk, gather, rest, regroup, and work at sensible times.
- NPC evening return, night rest, morning fan-out, and fear response visibly bias toward home or household safety.
- NPCs can fear or hate the player for persistent reasons.
- A settlement can become tense, fearful, or resistant without direct scripting.

Non-threshold ideas that should not block core AI quality:

- deep religion simulation
- detailed witness chains
- detailed class hierarchy
- hamlet autonomy
- heavy off-screen society simulation

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

Keep this layer intentionally compact. If a value is not visible in behavior, GUI, or clear settlement consequences, it should not become a first-class axis yet.

This layer changes slowly through events, memory decay, and settlement conditions.

### 3. Needs Layer

Short-to-medium-term internal drivers:

- hunger
- fatigue
- safety
- social need

Optional later expansion only after the core four feel good in live play:

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

However, memory spread should stay simple in the main plan:

- direct memory on the victim
- weaker echo to family
- weaker echo to household
- optional settlement-level pressure bump for major events

Do not build a heavyweight witness, rumor-chain, or forensic simulation unless the cheap social spread model proves insufficient.

### 5. Intent Layer

High-level current intention, selected by utility scoring:

- sleep
- go home
- work
- eat
- socialize
- seek supplies
- flee
- defend

The intent layer should update on a timer budget or on events, not every tick.

`worship`, `protest`, and `riot` are no longer core-plan requirements. They can return later only if the everyday social AI is already strong and readable.

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

Children stay in the core plan because they provide immediate visible social texture, family stakes, and stronger emotional consequences for violence, hunger, and displacement.

### Sex And Demography

The initial plan assumes a simple sex state only as family-identity scaffolding, not as a standalone simulation pillar.

It may affect:

- reproduction and birth modeling
- family structures
- inheritance or household continuity if those systems are later added
- some social norms if culture or religion uses them

It should not create trivial stat stereotypes or demand a full demographic simulator before family behavior is already strong.

### Household

Household is the main social atom of the settlement.

Each household should eventually track:

- adults
- children
- home anchor
- simple household pressure
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

If an event does not clearly change later AI choice, it should not be promoted into the first memory set.

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

These values should drive intent selection, speech flavor, and crowd behavior.

Keep the live model small. `grief`, `piety`, `status`, and other nuanced axes should stay out until the core five produce clear gameplay.

## Collective Reaction Model

The player should be able to push NPCs too far.

### Escalation Ladder

1. discomfort
2. distrust
3. fear
4. grievance
5. refusal or passive resistance
6. local self-defense
7. settlement unrest

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
- armed residents may form local self-defense clusters
- settlement-level unrest becomes active

## Religion And Cultural Fault Lines

Religion and culture are no longer active core-plan pillars. If present, they should begin only as lightweight identity tags.

Possible later uses:

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

Do not let religion or culture delay core AI work around home, family, memory, work, safety, and daily routines.

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

Priority adjustment:
- finishing the AI brain is now more important than adding new social subsystems
- stability, anti-thrashing, family-aware decisions, and memory-aware decisions should be treated as the next core AI work
- that stabilization slice now also covers post-failure home regrouping, route-first GUI explainability, and denser family-home social staging, but longer-horizon planning, deeper execution recovery, and richer family-local behavior still remain open follow-up work

### Phase 2A. Stability, Recovery, And Household Readability

- remove remaining cases where residents bounce between intents, retry the same broken route too quickly, or visibly stall in public for no readable reason
- formalize a small safe-fallback policy for broken goals:
  - danger-led failure -> `HIDE` or nearby household safety
  - fatigue/night-led failure -> `GO_HOME` then `REST`
  - hunger-led failure -> `EAT` then `SEEK_SUPPLIES` if needed
  - blocked work/social failure -> regroup at home or at the nearest sensible household-near anchor
- increase household/home weighting so family-linked residents settle, regroup, and socialize near household space more often than at generic town-center anchors when both options are viable
- expand compact GUI explainability so the player can see:
  - what failed recently
  - why the fallback was chosen
  - whether the resident is regrouping, resting, hiding, or seeking food
- add focused tests for blocked route recovery, repeated timeout backoff, evening return stability, household-near social fallback, and hunger/fear fallback correctness

Current shipped result:
- daytime `GO_HOME` regroup fallback is now live for recent broken routines when the resident has a valid home
- failed-meal recovery can now shift into `SEEK_SUPPLIES`, including stockpile-backed supply fallback when no open market path exists
- dedicated AI / citizen / worker observability now exposes an explicit recovering state plus clearer regroup / food-recovery route language
- household-near social anchoring now keeps family-home scenes tighter and less wander-prone near the player
- focused scheduler / scorer / snapshot tests now cover daytime home regroup, failed-meal supply fallback, and recovering-state visibility

Deliverable goal: NPCs stop feeling broken or random when everyday plans fail, and instead look cautious, readable, and household-grounded.

Exit criteria before broader feature growth:

- broken routines do not immediately snap back into the same bad intent family
- the most common failure cases end in visible safe fallback behavior
- family/home pull is obvious in evening, night, fear, and supply-stress situations
- GUI makes recovery legible without opening a debug-heavy dashboard

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
- do not broaden this phase significantly until Phase 2A stability/recovery goals are visibly met in near-player play

### Phase 4. Collective Defense And Justice

- build local witness and rumor spread
- add household and guard reactions to abuse
- add passive resistance and local retaliation
- let residents attack the player when thresholds are crossed

Deliverable goal: the player can no longer abuse people without social consequences.

Scope correction:
- keep rumor spread abstract and cheap
- do not build a detailed witness-chain simulation
- prefer household/family/settlement propagation over per-conversation rumor tracing

### Phase 5. Religion, Status, And Unrest

- add faith and class or status pressures
- add legitimacy effects for rulers and occupiers
- add settlement tension accumulation
- add protest, refusal, and riot intents

Deliverable goal: conflict emerges from social structure, not only direct combat.

This phase is now explicitly lower priority than AI stability, readable fallback behavior, family-home routing, children, and memory consequences.

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

This phase should not expand before near-player AI already feels convincingly intelligent, stable after failure, and easy for the player to read.

## Risks

- Overfitting realism before basic readability exists.
- Treating hidden simulation depth as a substitute for smart visible behavior.
- Expanding new systems before recovery behavior and household-centered routing are trustworthy.
- Writing too much data to individual entities instead of stable household or settlement structures.
- Letting async planners read live world state directly.
- Making every NPC evaluate too many expensive options too often.
- Building GUI detail without a compact information hierarchy.
- Letting public-anchor social behavior overpower home/family logic and make settlements look random again.

## Non-Goals For The First Slice

- fully simulated medieval law code
- dozens of emotions or traits per NPC
- universal dialogue trees
- deep romance simulation before household and memory foundations exist
- full historical economy before basic daily life is solved
- detailed witness chains and rumor graphs
- detailed class hierarchy
- hamlet autonomy as a mainline system
- deep religion gameplay

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
- broken goals fall into a visible, sensible, safe fallback instead of blind retry loops
- evening, night, fear, hunger, and blocked-social cases all show stronger home/household bias when appropriate
- the player can tell from GUI whether a resident is acting normally, recovering, hiding, resting, or seeking supplies
- memory, religion, and revolt are connected to one shared social model rather than isolated feature islands
- household requests and household ownership do not drift into two competing systems
- newly built houses are reserved correctly for the requesting resident or household
