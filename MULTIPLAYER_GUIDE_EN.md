# BannerMod Multiplayer Guide

Last updated: 2026-05-04.

BannerMod adds settlements, workers, armies, political states, and wars. This guide is written for regular server players, not for developers.

Main rule: land, settlement, workers, recruits, and political state must agree on ownership. If ownership diverges, the game can stop settlement work or reject an action.

## Three Separate Concepts

**Claim** means protected land on the map. It answers: "whose territory is this?"

**Settlement** means the live base inside a claim: buildings, storage, markets, workers, residents, projects, and work.

**Political state** means the side that owns land, joins wars, has leaders, and can have allies.

Do not treat these as the same thing. A claim can be land owned by a state, a settlement can live on that land, and the state makes political decisions.

## Default Hotkeys

Rebind in `Options → Controls → Key Binds → BannerMod / Workers`. Defaults:

| Key | Opens |
| --- | --- |
| `R` | Recruit command screen (army orders, formations, stances) |
| `U` | War Room (wars, states, siege standards, battle window) |
| `M` | Claim Map (claim chunks, place waypoints) |
| `X` | Worker command screen (orders for nearby workers) |
| `V` | Toggle building prefab preview rendering |

On the world map (`M`), use the top-left `Routes` control to create a route with `+`, select it from the list, then right-click explored map chunks to add waypoints. Left-drag a waypoint to move it, right-click a waypoint to edit or remove it, and use the gear button to rename, transfer, or delete the selected route. The route popups now keep the next step on-screen: blank or unchanged route names show why `Create Route` or `Save` is unavailable, waypoint order popups explain what `None` or `Wait` will do, and accepted route/waypoint edits flash a short parchment-style confirmation bar at the bottom of the map. Route markers now use numbered parchment chips and explicitly label chunks that are not yet server-ready instead of only changing color. Claim actions still label waiting-for-sync, stale edits, unclaimable chunks, and missing neighbor authority instead of silently hiding the action.

Formation-map move and attack orders now show a compact marker with `order sent` plus `server validation pending`. Treat that marker as feedback that your request left the client; the server still decides the real formation, path, and target state.

During normal play in the Overworld, the small top-right claim HUD shows whether you are in an allied claim, a foreign claim, or wilderness. It uses a compact banner-style marker plus text instead of color alone, keeps owner/state names visible in the compact read, and flashes a short action-bar cue when you cross into another territory, while still sitting under battle/siege chips and away from chat, crosshair, and the hotbar. Server-side claim rules still decide whether actions are allowed. If a protected-space action is denied, a short localized system message explains whether the block came from friendly claim locks, hostile claim protection, or server rules for unclaimed wilderness.

## First 10 Minutes (no admin tools)

1. Create a state first: type `/bannermod state create <name>` in chat (e.g. `/bannermod state create Karl-City`). You become its leader. Inspect with `/bannermod state info <id-fragment>` or open the War Room (`U`) and click `States`.
2. Pick a place for your base in the Overworld. If the map already offers a valid claim action, open it with `M`, right-click the chunk you want, choose `Claim chunk`, then expand by claiming nearby chunks one at a time so the server validates each edit. If the first claim action is unavailable, a successful starter-fort validation now creates the anchor chunk claim automatically for the leader/co-leader state that founded it. Claim cost and currency are server-defined (`AllowClaiming` must be true) and shown in the menu itself. Claim protection is Overworld-only; matching Nether or End X/Z chunks are not protected by Overworld claims.
3. Craft `Settlement Surveyor Tool` and `Building Placement Wand`. Both items are added by the mod and are how you validate your starter fort and register buildings. The surveyor recipe is 2 sticks, 2 oak planks and 1 iron ingot in a stairs-shaped pattern. The wand recipe is one gold block over one stick.
4. Place a `Starter Fort` — the keystone building, no settlement spawns without one. The Surveyor hologram is now a 21x21 U-shaped wooden fort plan: anchor in the courtyard center, medium palisade, four corner towers, an open gate arch with no gate leaves, and 5-block-wide side/back wings reserved for storage and barracks. Build it manually from the hologram, then hold the `Settlement Surveyor Tool`: it draws the gold 3D fort preview, an anchor flag, the required authority box, captured zone outlines, and a compact HUD checklist. Right-click in the air to open the survey board, or shift+right-click any block to reopen it while aiming at your build. In `BOOTSTRAP_FORT`, the tool starts on `AUTHORITY_POINT`; normal right-click the anchor block, then mark two opposite corners around the anchor. After that zone is captured the tool switches to `INTERIOR`; capture one large INTERIOR zone for the whole usable fort, courtyard, and side/back wings. Split fort interior zones are not supported yet, so do not mark only one brown wing or one small room if you expect starter-fort validation to pass. Color key: gold/wood lines show walls and towers, orange marks the authority anchor, blue marks usable interior/courtyard space, and brown marks wings, storage, or barracks space. If you make a mistake, use the board's `Actions` menu instead of hunting hidden gestures: `Cancel Corner A`, `Clear Current Role`, `Reset All Marks`, and `Pin Hologram` are there with explicit disabled reasons. The board and HUD now spell out the selected zone's job, what kind of blocks belong inside it, and role labels float over the hologram itself so you can tell which highlighted volume is authority, interior, sleeping space, storage, or work area. `Pin Hologram` stores a client-side copy of the current anchored preview so the fort skeleton keeps rendering even after you put the surveyor away; re-equip the tool when you want to move zones or validate again. Global blockers are: no selected zone, a missing required role, zone volume `<= 0` or `> 262,144`, anchor outside every selected zone, or for non-fort modes no settlement at the anchor/claim. The starter fort requires `AUTHORITY_POINT` and `INTERIOR`; keep the anchor inside at least one selected zone and ideally inside the authority zone too. The current validator warns, instead of blocking, when the authority zone exists but does not cover the anchor. `INTERIOR` means the usable air volume: air at feet, air at head, solid floor below; roof coverage checks for any roof block 2-8 blocks above those walkable cells. The hologram is a teaching plan, not a strict shape lock: the server validates the marked areas and the actual usable build, not whether every wall matches the preview block-for-block. When the HUD checklist is green, use the board's `Validate` button.
5. After a successful starter-fort validation the settlement bootstraps automatically: the existing claim is bound to a new `SettlementRecord`, or if no claim existed yet the server creates the anchor chunk claim for the founding state first. Starter workers then spawn near the anchor (farmer, miner, lumberjack, builder), and four free citizens spawn for vacancy jobs. Claim growth no longer auto-creates settlements on its own; founding stays gated behind a real validated fort. Starter workers now wait for player-marked or validated work areas instead of bootstrap ploughing a field on its own. The farmer needs a crop area, the miner needs a mine, the lumberjack needs a lumber camp, and the builder needs an architect workshop/build area; the bootstrap message and follow-up onboarding prompts point you at those exact next actions. Free citizens are a separate population source, not the same thing as these starter workers.
6. For the other manual buildings keep using the `Settlement Surveyor Tool`: switch modes to `Storage`, `Farm`, `House`, `Mine`, `Lumber Camp`, `Smithy`, `Architect Builder`, or `Barracks`. Each mode now keeps its own hologram visible after you mark zones, highlights the recommended storage/interior/sleeping/work area, auto-advances to the next required role for multi-zone buildings, and shows role-specific build hints directly in the board/HUD so you know whether a volume wants beds, chests, crops, furnaces, drafting space, or walkable interior air. Supported post-anchor modes also expose `Actions -> Suggest Draft`: it scans for likely beds, chests/barrels, farmland plus water, ore or stone faces, logs plus saplings, and furnace/anvil work clusters, then drafts only the still-missing zones into your current survey session. Those suggestions are a helper, not a guaranteed-correct validator: keep them, edit them, clear them, or overwrite them with normal manual marking before pressing `Validate`. Suggested zones never register a building on their own; validation still goes through the same surveyor registration/runtime path as a manual building of that mode. The surveyor still never places blocks; it only previews and validates what the player built. If you loaded or built an imported structure first, open `Actions` on the survey board and turn off the canned guide preview before marking zones. In that imported/post-build flow the overlay keeps only the anchor, your pending corner, and your captured zones visible, so odd imported footprints do not fight the teaching hologram. Validation still goes through the same surveyor registration/runtime path as a manual building of that mode, and it does not need any extra metadata file beside the built structure itself. The `Building Placement Wand` still exists for prefab placement, validation, and registration workflows, but its picker is now paged and less cluttered: fewer entries per page, shorter labels, and most details moved into hover tooltips. It now includes a `Gatehouse` prefab: select it while the wand is in `PLACE` mode and right-click the target block to spawn a roofed twin-tower entrance. This is a prefab-placement workflow, not a starter-fort surveyor mode. The surveyor modes are now enough to build and validate the core starter settlement chain by hand.
7. Nearby unassigned citizens only convert after they physically reach a registered building anchor with an open vacancy. Watch the surveyor/wand feedback after founding or validating buildings: it now tells you which vacancy opened and which building type is the next safe expansion step.
8. Configure workers: right-click a worker to open its inventory and assign a profession or task, or open the worker command screen with `X` and issue group orders.
9. If you recruit soldiers, keep food and payment in your inventory. Governed settlements also debit coarse army upkeep from settlement food stores and treasury coins; a missed cycle causes upkeep warnings and pressure instead of instantly deleting soldiers. Heavy, shielded, mounted, leader, or ranged troops can cost more than basic recruits. Issue orders with `R` (command screen): movement, formation, stance, aggression. Press `R` near a single recruit to assign per-unit tasks from its inventory.

If something fails, first check whether the action is inside your claim and whether the worker or recruit belongs to your side.

## States And Government Forms

State management is driven by slash commands:

- `/bannermod state create <name>` — create a new state (you become leader).
- `/bannermod state list` — list all states on the server.
- `/bannermod state info <entity>` — show a detail card. You can pass a UUID fragment.
- `/bannermod state setcapital <entity> [pos]` — set the capital. Without `pos` it uses the caller's position.
- `/bannermod state status <entity> <status>` — change status (`SETTLEMENT`, `STATE`, `VASSAL`, `PEACEFUL`). Promotion to `STATE` requires the settlement to register a starter fort or town hall, storage, and market — otherwise the command returns `infrastructure_insufficient`.

The same actions (Create / Rename / Capital here / Government form toggle) live in the War Room (`U`). The state screens now show an in-screen ledger line before you click: waiting for sync, select-a-realm first, read-only authority lock, or the next server-checked step. Leaders can also use `Add co-leader` / `Remove co` with a player UUID; the state detail panel shows the current co-leaders and whether their authority is active. The `→ Republic` / `→ Monarchy` button is leader-only and switches government form:

- `MONARCHY`: leader-only authority for key decisions.
- `REPUBLIC`: co-leaders also gain authority for shared political actions such as status, capital, color, charter, claim editing, ally invites, siege placement, and legal war outcomes.

If you only want to let another player live and build inside your settlement without giving them political powers, use the claim editor instead of co-leaders: open the map (`M`), edit the claim, then open `Trusted Members`. Trusted members can build, interact, and manage local work areas in that claim, but they still cannot edit claims, change state law, invite allies, or use war powers.

### How to add a player to your settlement

Step by step:

1. Press `M` to open the world map.
2. Right-click the chunk that belongs to your settlement claim and pick `Edit claim`.
3. In the claim editor click `Trusted Members`.
4. Press `Add Player`, choose the target player from the picker, and `Save`.

Removing a member uses the same screen — select the player row, click the `x` button on the selected-player widget, then `Save`.

### Forming a state out of multiple settlements

Each settlement is one claim and is auto-bound to the political entity of the player who created it. Two ways to bring multiple settlements into the same state today:

- The same leader (or a republic co-leader) creates all the new settlements while in the state. Their starter forts auto-attach to that state.
- Use co-leadership: the additional player accepts a co-leader seat (`Add co-leader` in the state screen), switch the government form to `REPUBLIC`, and any future settlement they author lands in the same state.

If you already have an existing claim that needs to live under a different state, you can transfer it in place. Open the world map, right-click the claim, choose `Edit claim`, and use the **Owning state:** dropdown to pick the target state — only states where you currently hold authority are listed. Press `Transfer to state`, confirm in the prompt, and the server moves the claim to the chosen state (the previous owner loses authority). The dropdown is empty if you are not a leader or co-leader anywhere else; the button is greyed when you have no authority in the current owning state.

## Promoting A Settlement Into A State

A settlement cannot become a full state without infrastructure. `/bannermod state status <entity> STATE` only succeeds when the settlement registers:

- a starter fort or town hall;
- storage;
- market.

If promotion is denied with a reason like `infrastructure_insufficient`, place and register the missing object (use the `Building Placement Wand` in `REGISTER` mode) and try again.

## Workers And Residents

Workers choose jobs through registered buildings and work areas. Storage, markets, farms, mines, homes, barracks, and build areas give the settlement different capabilities.

Important checks:

- the work area must be inside a friendly claim;
- the worker must belong to the right side;
- the settlement must see the registered building or work area;
- if a claim is captured, removed, or becomes mismatched, work can stop.

Civilian work-area editors now show a sync state in the top-right corner, an explicit owner reminder when nobody is assigned yet, and per-screen hints for missing seeds/saplings or tunnel settings. Market and storage editors also spell out the next step directly in the settings panel: an open market without a merchant, a closed stall with a merchant assigned, a missing storage route destination, or a blocked courier route now show visible guidance instead of relying on guesswork. Hold `Shift` while moving an area to nudge it by five blocks instead of one.

Right-clicking a worker now opens a compact worker ledger instead of dumping chat lines. The ledger shows owner, authority token, claim relation, assignment, current problem, and transport state in one place. If it shows `Ownership mismatch` or `Foreign claim`, fix claim/state/work-area ownership first; workers only run inside friendly authority and on the correct political side. The worker ledger, Citizen Profile, and `AI` trace also now phrase routine recovery in one short sentence: what the resident is doing now, what last broke, and whether it is regrouping at home, food, supplies, or cover.

The same ledger also has an `Actions` menu. `To Citizen` converts that worker into a free citizen on the server and applies a short auto-assignment pause so the citizen does not instantly snap back into the same vacancy before you can move or repurpose it. `Dismiss` is the explicit removal path: it asks the server to release the worker's current work area and remove the worker entity. Only the worker owner or an admin can dismiss; other players get denied feedback and no worker state changes.

Citizen, worker, and recruit detail screens also expose `Assign Home`. Press it to close the screen and enter a 30-second selector: aim at a bed and right-click use to send that bed position to the server as the entity's home. The HUD shows the remaining time. Press `Esc` to cancel, or wait for the selector to time out; either cancellation clears the selector without changing the home. Only a valid bed and a server-authorized owner/admin request update the entity home.

Next to `Actions` the ledger now has a `Reassign` action menu. It opens a dropdown of every worker profession (Farmer, Lumberjack, Miner, Animal Farmer, Builder, Merchant, Fisherman) except the one this worker already holds. Picking an option asks the server to swap the worker's profession in place: ownership, bound work area, and position are preserved, the old worker entity is replaced with the chosen profession's worker entity at the same anchor, and the same ownership / political authority gate that allows `To Citizen` is reused for `Reassign`. There is no recurring wage system in BannerMod; profession changes only re-hire if the original spawn cost has not yet been deducted, and once the worker exists no payroll is charged. Because workers have no wage value to set, the ledger does not show wage +/- controls unless a future payroll system adds a real server-authoritative wage field.

The current work-area editor now shows its zone box again while the screen is open, and the civilian overlay key `B` toggles nearby work areas you are allowed to control in your settlement. The overlay culls distant and fully hidden zones instead of drawing every marker through walls. For crop areas, the seed is chosen in the crop-area screen itself from the seed list built from your own inventory.

Settlements have internal work orders. Some order state already survives server reloads, including worker claims. Transport orders (`HAUL_RESOURCE`/`FETCH_INPUT`) carry source, destination, resource filter, and item count. Workers can claim, complete, cancel, or release jobs back to the settlement if abandoned.

Build Area screens show scan/build status at the bottom. Invalid names, empty scans, oversized dimensions, missing structure data, or server-side NBT/bounds rejection produce a visible reason; Build and creative Place requests also report accepted/rejected state in chat.

The same Build Area load picker also accepts imported structure files from your Minecraft game directory under `workers/scan`. Supported files are `.nbt`, `.schem`, `.schematic`, and `.litematic`; put the file there, open the Build Area screen, switch to `Load`, and choose it from the existing folder list. Imported schematics stay sparse: BannerMod reads only the authored blocks from the file for preview, build execution, and required-material counts instead of filling the whole bounding box.

Storage Area route fields use an explicit `Apply route` button. The screen validates destination UUID, item-id filters, count, and priority before sending, normalizes valid values, and shows the current destination, next-step hint, and blocked reason without relying on close-to-save.

Merchant stalls now call out offer state inside the trade GUI itself: no posted offers, no selected offer, a closed offer, sold-out stock, or a full player inventory all show an immediate reason and next step. In the add/edit trade screen, the charter header now explains whether payment goods are missing, the sale item is missing, max trades is zero, or the offer is ready, so incomplete setup no longer fails silently.

For long land storage routes, eligible merchants/couriers can automatically use a nearby unoccupied horse or server-approved mount. If no valid mount is available, they continue the same route on foot; clearing or failing the route makes them dismount. Right-click a worker to inspect whether it is mounted, why it is walking, and which fallback route behavior is active.

Citizens can fill building vacancies and convert into workers or recruits when they reach the assigned building anchor. Starter-fort bootstrap gives a normal survival settlement four free citizens immediately. If vacancies remain empty, there may be no free citizen close enough or unassigned yet, or there may be no housing keeping citizens near the settlement. Manual validated houses count as housing capacity, validated storage counts toward settlement stockpile infrastructure, and validated farm/mine/lumber/architect buildings create vacancy jobs like prefab completions. The in-game onboarding prompts now call out these vacancy openings directly.

New worker spawns are now gated on free housing. If a claim has no home with free `residentCapacity`, worker birth and claim/settlement spawns are frozen (the rule emits `NO_FREE_HOUSING`). To unfreeze population growth, place another validated house — the next spawn is automatically bound to a free slot and counted against the housing ledger. When a new worker is spawned the settlement now picks the profession by deficit: the allowed profession with the lowest current headcount in the claim wins (ties resolve by declaration order in `allowedProfessions`), so a lost smith is refilled first instead of being skipped by a round-robin cycle.

Citizen-on-citizen birth runs as a separate optional pass (off by default — enable via `CitizenBirthEnabled`). When active, each claim is scanned every `CitizenBirthCooldownTicks` (default ~1 in-game day) for an opposite-gender adult pair; if there is at least one pair, the claim has free housing capacity, the baby cap (`CitizenBirthMaxBabiesPerClaim`) is not yet reached, and the claim's StorageArea containers together hold at least `CitizenBirthFoodMinUnits` vanilla food items (default 8; set to 0 to disable the food precondition), the server spawns a baby citizen at the mother's position. If a settlement runs out of food, births pause (rule emits `NO_FOOD`) until food is restocked into a registered StorageArea inside the claim. The baby becomes an adult after `CitizenBirthGrowUpTicks` (default ~7 days), after which auto-staffing can assign it a profession through the normal flow. Citizen gender is randomized at first spawn and persisted in NBT.

Housing pressure is no longer silently auto-approved. When a homeless or overcrowded household petitions for a house, the settlement ruler now gets a clickable server-side notice and can also review open petitions with `/bannermod society housing list`. The simple first slice supports approve and deny directly from chat, and approved petitions are the ones that proceed into the housing-project path.

Housing petitions now reserve an explicit family lot inside the claim as soon as the petition is raised. The ruler's notice and `/bannermod society housing list` both show that reserved plot, approved house builds prefer that exact lot, and when the finished home is validated the assignment pass gives it back to the same requesting household before general free-home assignment can take it.

Settlements can now also raise basic livelihood building requests on their own when workers are idle and key survival infrastructure is missing. The first slice covers `lumber camp`, `mine`, and `animal pen`, and the ruler can review them with `/bannermod society livelihood list` or approve/deny them from clickable chat notices. Only approved requests enter the prefab project pipeline.

Settlement-spawned workers now start with basic profession tools and try to bind themselves to existing friendly claim work areas of the matching type instead of idling as often after bootstrap. In practice this means a miner, lumberjack, fisherman, or animal farmer can begin working sooner when the settlement already has a registered mine, lumber camp, fishing area, or pen, and their gathered goods still go through settlement storage.

Claim-grown workers now follow the same cheap work-area rule as starter workers. They can immediately bind to an existing friendly `Crop Area`, `Fishing Area`, `Mine`, `Lumber Area`, or `Animal Pen` inside the claim, but if no suitable player-marked or validated zone exists yet they stay idle and report that missing assignment instead of creating a starter field or fishing area on their own.

Workers can now also craft replacement basic stone tools for themselves when a nearby crafting table is available and they can get the needed materials. This first slice covers the common survival tools for farmers, lumberjacks, miners, animal farmers, and builders; it is not yet a full workshop economy, but it reduces cases where a worker stalls forever after losing a tool.

New worker spawns are now gated on free housing. If a claim has no home with free `residentCapacity`, worker birth and claim/settlement spawns are frozen (the rule emits `NO_FREE_HOUSING`). To unfreeze population growth, place another validated house — the next spawn is automatically bound to a free slot and counted against the housing ledger. When a new worker is spawned the settlement now picks the profession by deficit: the allowed profession with the lowest current headcount in the claim wins (ties resolve by declaration order in `allowedProfessions`), so a lost smith is refilled first instead of being skipped by a round-robin cycle.

Citizen-on-citizen birth runs as a separate optional pass (off by default — enable via `CitizenBirthEnabled`). When active, each claim is scanned every `CitizenBirthCooldownTicks` (default ~1 in-game day) for an opposite-gender adult pair; if there is at least one pair, the claim has free housing capacity, the baby cap (`CitizenBirthMaxBabiesPerClaim`) is not yet reached, and the claim's StorageArea containers together hold at least `CitizenBirthFoodMinUnits` vanilla food items (default 8; set to 0 to disable the food precondition), the server spawns a baby citizen at the mother's position. If a settlement runs out of food, births pause (rule emits `NO_FOOD`) until food is restocked into a registered StorageArea inside the claim. The baby becomes an adult after `CitizenBirthGrowUpTicks` (default ~7 days), after which auto-staffing can assign it a profession through the normal flow. Citizen gender is randomized at first spawn and persisted in NBT.

Starter-fort bootstrap now seeds 2-4 family households instead of only a flat pile of identical free adults. In practice that means the first settlement population can include married couples, young families, adolescents, and children. Children and adolescents stay with their households and do not auto-convert into worker vacancies until they grow into the adult path.

Citizen behavior is intentionally cheap and readable rather than memory-driven: daily decisions are centered on work, food, home, rest, and safety. Use the `Kinlot Staff` (`Родовая межа`) near a claimed house lot to inspect which household reserved it; while held it shows the nearest reserved family lot in the action bar, and right-clicking the lot prints the household summary, petition state, and current build-area marker.

The worker command screen (`X`) supports simple group orders: follow, guard, move to position, stop.

Governors expose the settlement's mirrored server snapshot: loading/stale/fresh state, citizen count, taxes, incidents, treasury data, policy buttons, and a read-only logistics panel. If the mirror says loading or stale, wait for the server refresh before trusting the panel; policy buttons explain why they are disabled until the server can validate the change. War, claim, governor, and work-area screens now use distinct waiting, empty, stale, and ready labels so you can tell whether to wait, select something, or fix authority. Promote an eligible owned recruit from its inventory when it has enough experience and is tied to a friendly claimed settlement.

Messenger, noble-trade, governor, patrol, and scout screens now keep their primary action state visible in-screen instead of failing silently. If a courier has no recipient, a trade has no valid contract, or a patrol leader has no route, the screen tells you the missing step directly; when an order is accepted, the same screen confirms that the request was sent.

The logistics panel also labels the settlement's strategic role and route cost. Farms plus storage can make a surplus hub, market plus routes can make a junction market, fort plus route storage can make a chokepoint fort, and ports/water access become a water gate. Landlocked settlements with food or material production may show a specialty such as preserved food or worked materials. These labels are warnings and planning hints first: they expose logistics objectives and loyalty pressure before applying destructive penalties.

Strategic economy status now treats the starter fort as level 1 and exposes the next fort-level requirements in food, iron, wood, stone, and coins through server status/debug output. Mines are not infinite passive income: a mine with no assigned worker, missing food, missing wood supports, missing tools, or a reserved disruption pressure can show degraded yield. Governed armies also draw coarse upkeep from settlement food accounting and treasury coins; shortages surface as recruit upkeep incidents in the governor mirror, and heavier or ranged troop classes increase the requested cost when the server can infer them safely. Server-side NPC demand contracts can appear even with one player online; markets and trading posts improve their cadence or quality. Use `/bannermod economy contracts <claimUuid>` for the server-backed contract list, or the admin debug economy commands for deeper inspection.

### Code-backed settlement mechanics reference

The settlement stack is not a single magic block. It is a pipeline of records and snapshots:

- `SettlementSurveyorToolItem` stores the current validation session on the item: selected mode, selected zone role, anchor, pending corner, and marked zones.
- `SurveyorMode` values are `BOOTSTRAP_FORT`, `HOUSE`, `FARM`, `MINE`, `LUMBER_CAMP`, `SMITHY`, `STORAGE`, `ARCHITECT_BUILDER`, and `INSPECT_EXISTING`.
- `ZoneRole` values are `AUTHORITY_POINT`, `INTERIOR`, `SLEEPING`, `WORK_ZONE`, `FORT_PERIMETER`, `ENTRANCE`, `STORAGE`, and `PREFAB_FOOTPRINT`.
- `SettlementSurveyorService` validates the session. `STARTER_FORT` bootstraps a settlement; every other building needs an existing settlement at the anchor or claim.
- Valid manual buildings are saved as `ValidatedBuildingRecord` entries. Valid records are merged into settlement snapshots with live work areas, while overlapping live work areas are deduped.
- A settlement snapshot drives resident capacity, workplace slots, stockpile summary, market state, desired goods, project candidates, trade-route handoff, supply signals, strategic role labels, and promotion checks.

Practical implication: if a mechanic is not visible in the governor/logistics panel, first check whether the building exists in the settlement snapshot. Use surveyor inspect mode on the anchor, re-register with the wand if needed, and make sure the building is inside the correct claim.

### Surveyor validator reference

Global rules:

- at least one zone must be selected;
- required roles depend on mode: `STARTER_FORT = AUTHORITY_POINT + INTERIOR`, `HOUSE = INTERIOR + SLEEPING`, `FARM = WORK_ZONE`, `MINE = WORK_ZONE`, `LUMBER_CAMP = WORK_ZONE`, `SMITHY = INTERIOR + WORK_ZONE`, `STORAGE = STORAGE`, `ARCHITECT_WORKSHOP = INTERIOR + WORK_ZONE`;
- every zone volume must be `> 0` and `<= 262,144` blocks;
- the anchor must be inside at least one selected zone;
- every non-fort mode also requires an existing settlement at the anchor or claim;
- inside one settlement, `INTERIOR`, `SLEEPING`, and `WORK_ZONE` cannot overlap the same primary roles of another building type.

Board and HUD guidance:

- the selected role now shows both its gameplay job and the kind of blocks that belong inside that volume;
- floating role labels are rendered above the hologram guide boxes so you can match the colored volume to its purpose;
- `Actions -> Pin Hologram` keeps the current anchored preview visible on your client even after the surveyor tool is no longer in hand.
- the Build Area preview board now explicitly tells you when it is still empty, whether you should scan or load next, and how to rotate/reset the preview model.

`INTERIOR` semantics:

- mark the usable air volume, not just the walls;
- a walkable cell counts only when the feet block is air, the head block is air, and the block below is solid;
- roof coverage means any non-air block 2 to 8 blocks above a walkable cell;
- the entrance hint looks for a 2-block-high boundary opening with outside air adjacent.

Per-building thresholds:

- `STARTER_FORT`: both required zones; warnings only if authority zone misses anchor, no banner is near anchor, interior has fewer than `64` walkable cells, roof coverage is below `80%`, or entrance is unclear; success bootstraps the settlement.
- `HOUSE`: at least `8` walkable interior cells, at least `70%` roof coverage, and at least one bed. Beds are accepted in `SLEEPING`, within 1 block of `SLEEPING`, in `INTERIOR`, within 1 block of `INTERIOR`, or anywhere within `12` blocks of anchor. Unclear entrance is a warning only.
- `FARM`: anchor within `24` blocks of `WORK_ZONE`; at least `24` farmland/crop-capable blocks; capacity scales by `48` valid field blocks.
- `MINE`: anchor within `32` blocks of `WORK_ZONE`; at least `24` valid face blocks from stone, deepslate, cobblestone, or ores; sky-exposed anchor is a warning only; capacity scales by `64` valid face blocks.
- `LUMBER_CAMP`: anchor within `32` blocks of `WORK_ZONE`; productivity must be `>= 12`, where productivity is `logs + saplings/2`; capacity scales by `productivity / 12`.
- `SMITHY`: at least `70%` roof coverage, at least one anvil, at least one furnace or blast furnace, and at least one anvil-furnace pair within `4` blocks.
- `STORAGE`: at least one chest or barrel block entity inside the `STORAGE` zone.
- `ARCHITECT_WORKSHOP`: at least `16` walkable interior cells, at least `70%` roof coverage, and at least one crafting table in the `WORK_ZONE`.
- `INSPECT_EXISTING`: reads type, state, capacity, quality, and vacancy hint from a validated anchor; it does not create a building.

### Building and vacancy reference

- `HOUSE`: adds resident capacity. It does not create a worker job by itself.
- `STORAGE`: adds stockpile building count, container count, slot capacity, and storage type hints. It is one of the requirements for state promotion.
- `FARM`: creates a farmer vacancy and contributes to food-production role signals.
- `MINE`: creates a miner vacancy and contributes to material-production role signals.
- `LUMBER_CAMP`: creates a lumberjack vacancy and contributes to material-production role signals.
- `ARCHITECT_WORKSHOP`: creates a builder vacancy and contributes to construction/project signals.
- `SMITHY`: contributes material-production infrastructure; use it as part of a developed material settlement.
- `STARTER_FORT`: founding and promotion infrastructure. Missing required zones blocks validation; banner/roof/entrance quality issues only warn.
- `MARKET`: not a surveyor validator mode. Register it with the `Building Placement Wand` in `REGISTER` mode; it is still required for state promotion.
- the wand's prefab board now keeps the chosen plan highlighted on the item; after selection, close the board and right-click the target block. Placement and registration still stay server-authoritative.

Prefab professions also exist for `MERCHANT`, `FISHERMAN`, `ANIMAL_FARMER`, `SHEPHERD`, and recruit roles (`RECRUIT_SWORDSMAN`, `RECRUIT_ARCHER`, `RECRUIT_PIKEMAN`, `RECRUIT_CROSSBOW`, `RECRUIT_CAVALRY`). Those are prefab/staffing declarations; the settlement still needs valid ownership, vacancies, and available citizens.

### Settlement life-cycle checklist

1. Claim land in the Overworld.
2. Create or join a state.
3. Validate a starter fort with `AUTHORITY_POINT` and `INTERIOR` zones.
4. Confirm bootstrap chat success and free citizen count.
5. Register storage so stockpile and route state can appear.
6. Register farm or other production so desired goods and worker jobs have a source.
7. Register market to unlock market state and state promotion.
8. Add homes if population pressure appears.
9. Use the governor logistics panel to read shortage blockers, project hints, role labels, route costs, and loyalty pressure.
10. Promote the political entity only after fort/town hall, storage, and market are present.

## Storage, Markets, And Trade

A `Storage Area` lets the settlement know where resources live. A `Market Area` plugs the settlement into the economy: merchants can open stalls and the settlement records what is sold and for which currency.

To register a market: outline a zone with the `Building Placement Wand` in `REGISTER` mode, pick a `Market` profile, and place the corners. Then assign a `Merchant` worker to the area. The merchant has its own trade screen (open it by interacting with the merchant), where you add `WorkersMerchantTrade` entries.

If the settlement lacks resources, check:

- storage exists inside the settlement;
- market is registered;
- a source of goods exists;
- trade is not blocked by war, ownership, or server configuration.

Port or sea-entry points can now drive the Small Ships sea-trade loop when a server has compatible ship carriers available. The settlement snapshot and governor logistics panel show each ship-backed route with route id, carrier id or `unassigned`, phase, cargo progress, and reason. `Needs ship` means the route has no bound carrier; `blocked cargo` explains source shortage, no loaded cargo, destination full, or carrier failure. There is no compile-time Small Ships requirement, so servers without that integration still show normal route hints without spawning ship work.

The governor screen shows the current tax obligation as collected/due. A friendly settlement that pays shows the obligation as satisfied and adds to treasury; if a siege or settlement mismatch blocks payment, the same line turns unpaid and explains that treasury growth, upkeep, and defense funding can stall.

Noble trade uses the right-hand status ribbon as the final authority for the next step: `Choose a contract`, `No contracts`, `Not enough currency/villagers`, or `Accepted`. Do not assume a greyed-out hire button means the same thing every time; read the ribbon or tooltip for the specific blocker.

## Recruits And Armies

Recruits follow their owner. Allies on the same side can help with group commands if the server allows it and they are close enough.

The command screen (`R`) shows order buttons:

- movement: `Hold`, `Follow`, `Regroup`, `Wander`, `Come to me`, `Patrol`, `Move to position`;
- formation: read from the player's saved formation (Formation tab);

Patrol and scout field-order screens now follow the same rule: select the route/group first, then look at the bottom status strip before you leave the screen. It will tell you whether the leader is idle, patrolling, paused, or whether the latest order was accepted by the client and sent to the server.
- stance: `LOOSE`, `LINE_HOLD`, `SHIELD_WALL`.

The recruit command, recruit inventory, hiring, rename, promotion, and group-management screens now keep a compact footer/status line in view. If a button is disabled, read that line or its tooltip first: it tells you whether you still need to select a company/player, aim at ground or a unit, type a name, or save the company before using secondary actions.

Stances change behavior and shield mitigation:

- `LOOSE`: normal flexible behavior, shield blocks ~45% of incoming damage.
- `LINE_HOLD`: holds formation more strongly, shield blocks ~55%.
- `SHIELD_WALL`: auto-blocks frontally, slower movement and turning, shield absorbs ~70% within a directional 120° front cone.

Battle tips:

- formations beat loose crowds;
- shields work best when facing the enemy;
- spears and pikes reach farther than short weapons (per-item `WeaponReach`);
- second-rank spearmen can attack through allies (`FriendlyLineOfSight`);
- side hits do ×1.15, back hits do ×1.5;
- pikes get an anti-cavalry bonus in `BRACE` (knockback resistance +0.5, ×0.7 incoming cavalry damage);
- ranged units need room behind the front line — the `LINE_HOLD` / `SHIELD_WALL` leash automatically pulls them back.

You can set per-unit stances from the recruit's inventory (`RecruitInventoryScreen`); group stances live in the command screen. Recruit commands now report chat acknowledgements: accepted recruit count for immediate orders, rejected empty/no-eligible selections, replaced queued orders, or pending order counts when a queued command path is used.

If a crossbowman is holding a musketmod gun, the recruit inventory now shows whether that firearm is supported, whether cartridges are present, or whether the gun is unsupported for recruit use. Check that feedback before assuming the recruit combat runtime is broken.

Recruit and player perks are server-side. Level-ups grant perk points, kill credit can add player perk progress, and unlocked perks can add max health, knockback resistance, melee damage, attack speed, movement speed, ranged accuracy, or projectile velocity. Recruit archetype perks apply only to the matching role: swordsman, bowman, crossbowman, pikeman/shieldman, or cavalry. Open a recruit's parchment perk tree from the recruit inventory `Perks` button; open your own parchment skill tree with the `Open Player Skill Tree` keybind (`K` by default). Locked, available, and owned states are shown in the tree. Unlock and respec requests are sent to the server, which validates ownership, points, and prerequisites before sending the refreshed tree back.

### Recruit command pipeline details

Server-side military commands are normalized into `CommandIntent` records before they reach legacy command services. This matters because selection narrowing, queue mode, priority, and audit/logging hooks all live in the unified command path.

- `Movement`: move, hold, follow, regroup, wander, come-to-me, patrol, move-to-position, formation forward/back. Movement states are numeric internally: `0` hold, `1` follow, `2` regroup, `3` wander, `4` come-to-me, `5` patrol, `6` move-to-position, `7/8` formation forward/back.
- `Face`: rotate the selected formation without moving.
- `Attack`: group attack command.
- `StrategicFire`: ranged fire on/off.
- `Aggro`: passive, neutral, aggressive, raid-like behavior.
- `CombatStanceChange`: `LOOSE`, `LINE_HOLD`, `SHIELD_WALL`.
- `SiegeMachine`: mount/return-to-mount/siege-machine crew orders.

Formation is server-authoritative. The saved formation lives on the player and is read server-side; client packets should not invent formation indices. If you never opened the formation UI, formation `0` means per-recruit fallback behavior is expected.

### Combat behavior in practice

- `PASSIVE`: recruit should not initiate attacks.
- `NEUTRAL`: attacks hostile living things and entities attacking owner/self.
- `AGGRESSIVE`: attacks enemy players and recruits on sight.
- `RAID`: broad hostile behavior; use carefully in multiplayer.
- Same political side is protected by political relations and friendly-fire checks.
- Recruits treat active political wars as the supported diplomacy model; old hidden team treaties are not the player-facing model.

## War Room (`U`)

War Room is the main in-game UI for war and politics:

- **Battle window banner**: top of the screen shows the current battle-window phase ("OPEN FRI 19:00-20:30 — closes in 45m" / "CLOSED — next SUN 18:00-19:30 in 1d 22h"). Green = open, gray = closed. The schedule is server-configured (`BattleWindows` in `bannermod-war-server.toml`) and synced to the client automatically.
- **Active wars list** on the left: each war shows state (`DECLARED`/`ACTIVE`/`IN_SIEGE_WINDOW`/`RESOLVED`/`CANCELLED`), attacker, defender.
- **Detail panel** on the right: attacker, defender, war state, goal type, casus belli, declaration time, allies, target positions, siege standards, occupations, revolt status, and a consequence summary for occupation tax, revolt pressure, or tribute aftermath.
- Buttons: `Attacker info` / `Defender info` (open `PoliticalEntityInfoScreen`), `States` (state list), `Declare war`, `Cancel war`, `Occupy here`, `Annex here`, `Revolt won`, `Revolt failed`, `Tribute: op only`, `Place siege here` (drop a siege standard at your current position), `Refresh`, `Close`.

`Place siege here` is enabled only when you are the leader of one of the war's sides and the war is not `RESOLVED`/`CANCELLED`. It sends `MessagePlaceSiegeStandardHere`, and the server validates against the same rules as `/bannermod siege place`.

`Declare war` opens a small War Room wizard where a state leader picks attacker, defender, goal, and optional casus belli text. Disabled buttons and the War Room footer show the server's denial reason: leader-only authority, republic co-leader authority, monarchy blocking co-leaders, peaceful/status limits, or declaration cooldowns. The command remains available and uses the same server checks.

War outcome buttons are server-authoritative. `Cancel war`, `Occupy here`, and `Annex here` unlock only when the selected live war supports that outcome and you are the attacking state leader; mismatched goals show a disabled reason. `Collect tribute` appears for operator attacking leaders on `TRIBUTE` wars, while normal leaders see `Tribute: op only`. Every outcome request reports accepted or denied feedback in the War Room footer as well as chat; forced peace, vassalization, and demilitarization remain admin outcomes.

If an occupation outcome starts on claim chunks, players can see `OCCUPIED` in the claim HUD when standing in the occupied claim and see occupation lines in the War Room detail panel. Occupation is server-authoritative: the occupied owner loses manual block/entity control in the occupied claim chunks, while the occupier's political entity can act there; occupation tax remains visible in the War Room as `tax=t...`.

When you stand inside the radius of a siege standard belonging to an active war, an additional HUD banner shows the war name, owning side, and current war state.

## Wars

Slash commands are available to all players, but most actions require you to be a leader of one of the sides or a server operator:

- `/bannermod war declare <attacker> <defender> <goal> [casusBelli]` — declare war. `goal` is one of `WarGoalType` values (e.g. `OCCUPATION`, `VASSALIZATION`, `DEMILITARIZATION`).
- `/bannermod war list` — list all wars.
- `/bannermod war info <warId>` — show war details.
- `/bannermod war cancel <warId>` — cancel (sides with permission).
- `/bannermod war whitepeace <warId>` — white peace (op-only).
- `/bannermod war tribute <warId> <amount>` — close with tribute (op-only).
- `/bannermod war vassalize <warId>` — close with vassalization (op-only).
- `/bannermod war demilitarize <warId> <days>` — close with demilitarization for N days (op-only).
- `/bannermod war occupations` / `/bannermod war revolts` — list occupations and pending revolts.
- `/bannermod war revolt resolve <revoltId> <outcome>` — admin-resolve a revolt (op-only).

Outcomes that grant land or change defender state (`tribute`, `vassalize`, `demilitarize`, annexation) give the defender `LOST_TERRITORY_IMMUNITY` for a configurable number of days (default 3) — no offensive war can be declared on them during this window. `PEACEFUL` toggles also have a cooldown (`PEACEFUL_TOGGLE_RECENT`, default 2 days).

## Allies In War

While a war is still in the `DECLARED` phase (pre-active), the leader of either main side may invite a third political entity to join as an ally. Ally membership is consent-based: invite → accept / decline.

From the War Room (`U`):

1. Select an active war in the list.
2. Click the `Allies for selected war` button below the list — `WarAlliesScreen` opens.
3. If you are the leader of one of the main sides, `Invite to Attacker` / `Invite to Defender` are active. They open a picker — the list of political entities legal to invite (the client mirrors the same `WarAllyPolicy` the server uses, so main sides, entities already in the war, and `PEACEFUL` entities trying to join the attacker side are filtered out).
4. Both the allies screen and the picker now show a visible ledger hint for the next step: waiting for sync, open the correct side's roster, accept/decline a pending invite, or click a realm to send the invite.
5. Clicking a row sends the invite.
6. The leader of the invited state sees the invite in the list; left-click accepts (`MessageRespondAllyInvite` accept), DEL/BACKSPACE declines. The leader of the inviting side sees the same row with a visible cancel hint.

Slash commands provide the same flow without UI:

- `/bannermod war ally invite <warId> <side> <entity>` — leader of `side` invites the entity (`<side>` = `ATTACKER` or `DEFENDER`).
- `/bannermod war ally accept <inviteId>` — invitee leader accepts.
- `/bannermod war ally decline <inviteId>` — invitee leader declines.
- `/bannermod war ally cancel <inviteId>` — inviter leader cancels.
- `/bannermod war ally list <warId>` — list current allies + pending invites for the war.

Every invite and response writes a `WarAuditLogSavedData` entry (`ALLY_INVITED`, `ALLY_JOINED`, `ALLY_INVITE_DECLINED`, `ALLY_INVITE_CANCELLED`). When a war leaves `DECLARED`, dangling invites are auto-removed on next access.

Typical denial reasons: `war_not_found`, `war_not_pre_active` (the war has been activated — no more invites), `invitee_is_main_side`, `invitee_already_on_side`, `invitee_on_opposing_side`, `peaceful_cannot_join_attacker`, `not_leader`.

## Sieges And Siege Standards

To place a siege standard:

1. Craft a `Siege Standard` (`bannermod:siege_standard`). 3×3 recipe:
   ```
   G N G
   D B D
   G S G
   ```
   `G` = gold block, `N` = emerald block, `D` = diamond, `B` = any banner (`minecraft:banners` tag), `S` = stick. Stack size 16.
2. Make sure a war exists: `/bannermod war declare <attacker> <defender> <goal>` (or wait for one).
3. Open the War Room (`U`) and select an active war.
4. Stand at the spot where you want to plant the standard and click `Place siege here`. Or use the slash form: `/bannermod siege place <warId> <side> [pos] [radius]`.

The default radius is configured in `WarServerConfig` (`DefaultSiegeRadius`, 64 blocks). The standard is not just a placeholder block: `SiegeStandardBlockEntity` syncs the owner (`warId` + `sidePoliticalEntityId`) to the client, and `SiegeStandardBlockEntityRenderer` paints a small political-color cap on top using the side's color.

What happens around a siege standard:

- The server records the standard in `SiegeStandardRuntime` with war id, side political id, position, radius, placed tick, control pool, and max control pool.
- The default control pool is 100. Damage is clamped and applied through `SiegeObjectivePolicy.applyDamage`; when it reaches zero, the standard is destroyed/removed.
- Same-side recruits use the siege escort goal: if idle and too far from a friendly standard, they drift back toward it.
- Enemy-side recruits use the siege objective attack goal: they can path to enemy standards, look at them, and apply periodic objective damage.
- Siege-machine and cannon blasts now trim terrain damage to tighter breach-sized holes instead of deleting large wall sections with one hit; repeated shots still matter for breaking an approach.
- The strategic logistics panel can name related war objectives: stockpile, route junction, water gate, and surplus store. These tell both sides what is worth attacking or defending.
- Player PvP rules are stricter than recruit objective logic: player war damage requires an active/in-siege war, open battle window, and registered participants.

Good siege placement is not cosmetic. Put standards where a defender must care: route junctions, stockpiles, bridges, ports, market gates, surplus farms, or a chokepoint fort. A random field standard may create a zone, but it will not pressure the settlement economy.

Listing standards:

- `/bannermod siege list [warId]` — list standards (filtered by war or all).
- `/bannermod siege remove <standardId>` — remove a standard (op-only).

PvP damage between players during a war is allowed only while:

- the war is `ACTIVE` or `IN_SIEGE_WINDOW`;
- a battle window is currently open (see the War Room banner);
- both players are registered participants of that war.

## Revolts

When your territory is occupied by a hostile state you can declare a revolt from that occupation:

- `/bannermod state revolt declare <occupationId>` — declare a revolt (use a UUID fragment from `/bannermod war occupations`).
- `/bannermod war revolts` — list pending and resolved revolts.
- `/bannermod war revolt resolve <revoltId> <outcome>` — admin-side resolution for operators.

The War Room detail panel also shows revolt pressure. Pending revolts explain that occupied land is rebelling, the due tick, and the objective chunk that rebels must hold during an open battle window with no defenders present. Resolved revolts show the aftermath: success removes the occupation, while failure means the occupier held the objective and the occupation remains.

Operators can resolve the first pending revolt on the selected war directly in the War Room with `Revolt won` or `Revolt failed`. The buttons are disabled when no war is selected, no pending revolt exists, or the local player is not an operator; the server repeats those checks and sends an explicit denial message if the action is rejected.

## When An Action Is Denied

If a war or siege action is denied, the usual reasons are:

- you are not leader or operator;
- you are on the wrong side;
- it is not the right battle window (see the banner);
- the placement position is invalid;
- territory belongs to the wrong side;
- settlement infrastructure is missing (for `STATE` promotion);
- the target has a temporary cooldown (`LOST_TERRITORY_IMMUNITY` or `PEACEFUL_TOGGLE_RECENT`).

## Quick Troubleshooting

- **Workers do nothing**: check claim, worker side, registered building (use `Building Placement Wand` in `REGISTER` mode), storage/market presence, and whether territory was captured.
- **Recruits ignore orders**: move closer, check owner/side, open the `R` screen and inspect active orders, relog after a server restart.
- **Starter fort fails to validate**: confirm the `Settlement Surveyor Tool` has an anchor set, every required prefab zone (`INTERIOR`, `EXTERIOR`, ...) is captured, and no obstruction breaks the geometry.
- **State promotion fails**: add a starter fort or town hall, storage, and market inside the settlement and register them with the wand.
- **Siege placement fails**: open War Room, select an active war, and verify you are leader of the correct side. If the button is gray, hover over it to read the denial reason.
- **War declaration denied**: check `/bannermod war list` (an active war to that defender already?) and `/bannermod state info <defender>` (`PEACEFUL` status or an active cooldown block declarations).
- **Battle window banner always gray**: check `BattleWindows` in `bannermod-war-server.toml` on the server; an empty value means no window is ever open.

In short: claim land on the map (`M`), validate a starter fort with the surveyor, register buildings with the wand, create a state with `/bannermod state create`, keep workers and armies on the same side, use stances and formations in battle, and open War Room (`U`) before war — that's where the battle-window clock and the siege placement button live.
