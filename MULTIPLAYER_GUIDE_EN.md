# BannerMod Multiplayer Guide

Last updated: 2026-04-27.

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

## First 10 Minutes (no admin tools)

1. Pick a place for your base and claim at least one chunk in the Overworld. Open the map with `M`, right-click the chunk you want, choose `Claim chunk`. Claim cost and currency are server-defined (`AllowClaiming` must be true) and shown in the menu itself. Claim protection is Overworld-only; matching Nether or End X/Z chunks are not protected by Overworld claims.
2. Create a state so your claim has a side: type `/bannermod state create <name>` in chat (e.g. `/bannermod state create Karl-City`). You become its leader. Inspect with `/bannermod state info <id-fragment>` or open the War Room (`U`) and click `States`.
3. Craft `Settlement Surveyor Tool` and `Building Placement Wand`. Both items are added by the mod and are how you validate your starter fort and register buildings. The surveyor recipe is 2 sticks, 2 oak planks and 1 iron ingot in a stairs-shaped pattern. The wand recipe is one gold block over one stick.
4. Place a `Starter Fort` — the keystone building, no settlement spawns without one. Build it manually or use a prefab, then use the `Settlement Surveyor Tool`: right-click the anchor block, then right-click corners of each required zone. Shift+right-click cycles surveyor mode; shift+right-click on a block cycles the zone role (`AUTHORITY_POINT`, `INTERIOR`, `EXTERIOR`, ...). The starter fort must include `AUTHORITY_POINT` and `INTERIOR`; the anchor should be inside the authority area. When the session is filled, right-click in the air without shift to validate the fort.
5. After a successful starter-fort validation the settlement bootstraps automatically: a `SettlementRecord` is created, a starter claim is added if needed, starter workers spawn near the anchor (farmer, miner, lumberjack, builder), and four free citizens spawn for vacancy jobs. The farmer is ready immediately because bootstrap seeds a starter crop area. The miner, lumberjack, and builder are intentionally waiting until you create/register a mine, lumber camp, and architect workshop/build area; the bootstrap message lists those next actions. Free citizens are a separate population source, not the same thing as these starter workers.
6. For other buildings use the `Building Placement Wand`. The wand has 3 modes: `PLACE` (place a prefab), `VALIDATE` (verify what's already built), `REGISTER` (register the building with the settlement). Shift+right-click in the air cycles mode. Right-click a block to mark a corner of the validation/registration area. Manual farm, mine, lumber camp, and architect workshop validation now shows the profession vacancy it creates.
7. Mark work areas: farm, storage, market, mine, lumber yard, build site. Outline them with the wand the same way (pick a prefab or freeform area, mark corners, register). Nearby unassigned citizens can fill validated workplace vacancies like prefab vacancies.
8. Configure workers: right-click a worker to open its inventory and assign a profession or task, or open the worker command screen with `X` and issue group orders.
9. If you recruit soldiers, keep food and payment in your inventory. Issue orders with `R` (command screen): movement, formation, stance, aggression. Press `R` near a single recruit to assign per-unit tasks from its inventory.

If something fails, first check whether the action is inside your claim and whether the worker or recruit belongs to your side.

## States And Government Forms

State management is driven by slash commands:

- `/bannermod state create <name>` — create a new state (you become leader).
- `/bannermod state list` — list all states on the server.
- `/bannermod state info <entity>` — show a detail card. You can pass a UUID fragment.
- `/bannermod state setcapital <entity> [pos]` — set the capital. Without `pos` it uses the caller's position.
- `/bannermod state status <entity> <status>` — change status (`SETTLEMENT`, `STATE`, `VASSAL`, `PEACEFUL`). Promotion to `STATE` requires the settlement to register a starter fort or town hall, storage, and market — otherwise the command returns `infrastructure_insufficient`.

The same actions (Create / Rename / Capital here / Government form toggle) live in the War Room (`U`). Leaders can also use `Add co-leader` / `Remove co` with a player UUID; the state detail panel shows the current co-leaders and whether their authority is active. The `→ Republic` / `→ Monarchy` button is leader-only and switches government form:

- `MONARCHY`: leader-only authority for key decisions.
- `REPUBLIC`: co-leaders also gain authority for shared political actions such as status, capital, color, charter, claim editing, ally invites, siege placement, and legal war outcomes.

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

Settlements have internal work orders. Some order state already survives server reloads, including worker claims. Transport orders (`HAUL_RESOURCE`/`FETCH_INPUT`) carry source, destination, resource filter, and item count. Workers can claim, complete, cancel, or release jobs back to the settlement if abandoned.

Build Area screens show scan/build status at the bottom. Invalid names, empty scans, oversized dimensions, missing structure data, or server-side NBT/bounds rejection produce a visible reason; Build and creative Place requests also report accepted/rejected state in chat.

Storage Area route fields use an explicit `Apply route` button. The screen validates destination UUID, item-id filters, count, and priority before sending, normalizes valid values, and shows the current destination/blocked reason without relying on close-to-save.

For long land storage routes, eligible merchants/couriers can automatically use a nearby unoccupied horse or server-approved mount. If no valid mount is available, they continue the same route on foot; clearing or failing the route makes them dismount.

Citizens can fill building vacancies and convert into workers or recruits when they reach the assigned building anchor. Starter-fort bootstrap gives a normal survival settlement four free citizens immediately. If vacancies remain empty, there may be no free citizen close enough or unassigned yet. Manual validated houses count as housing capacity, validated storage counts toward settlement stockpile infrastructure, and validated farm/mine/lumber/architect buildings create vacancy jobs like prefab completions.

The worker command screen (`X`) supports simple group orders: follow, guard, move to position, stop.

Governors expose the settlement's status, citizen count, taxes, incidents, treasury data, policy buttons, and a read-only logistics panel. Use the logistics panel to check pending, claimed, and recently completed work orders, shortage blockers, stockpile capacity, missing goods, and the current project hint before digging through logs. Promote an eligible owned recruit from its inventory when it has enough experience and is tied to a friendly claimed settlement.

The logistics panel also labels the settlement's strategic role and route cost. Farms plus storage can make a surplus hub, market plus routes can make a junction market, fort plus route storage can make a chokepoint fort, and ports/water access become a water gate. Landlocked settlements with food or material production may show a specialty such as preserved food or worked materials. These labels are warnings and planning hints first: they expose logistics objectives and loyalty pressure before applying destructive penalties.

### Code-backed settlement mechanics reference

The settlement stack is not a single magic block. It is a pipeline of records and snapshots:

- `SettlementSurveyorToolItem` stores the current validation session on the item: selected mode, selected zone role, anchor, pending corner, and marked zones.
- `SurveyorMode` values are `BOOTSTRAP_FORT`, `HOUSE`, `FARM`, `MINE`, `LUMBER_CAMP`, `SMITHY`, `STORAGE`, `ARCHITECT_BUILDER`, and `INSPECT_EXISTING`.
- `ZoneRole` values are `AUTHORITY_POINT`, `INTERIOR`, `SLEEPING`, `WORK_ZONE`, `FORT_PERIMETER`, `ENTRANCE`, `STORAGE`, and `PREFAB_FOOTPRINT`.
- `SettlementSurveyorService` validates the session. `STARTER_FORT` bootstraps a settlement; every other building needs an existing settlement at the anchor or claim.
- Valid manual buildings are saved as `ValidatedBuildingRecord` entries. Valid records are merged into settlement snapshots with live work areas, while overlapping live work areas are deduped.
- A settlement snapshot drives resident capacity, workplace slots, stockpile summary, market state, desired goods, project candidates, trade-route handoff, supply signals, strategic role labels, and promotion checks.

Practical implication: if a mechanic is not visible in the governor/logistics panel, first check whether the building exists in the settlement snapshot. Use surveyor inspect mode on the anchor, re-register with the wand if needed, and make sure the building is inside the correct claim.

### Building and vacancy reference

- `HOUSE`: adds resident capacity. It does not create a worker job by itself.
- `STORAGE`: adds stockpile building count, container count, slot capacity, and storage type hints. It is one of the requirements for state promotion.
- `FARM`: creates a farmer vacancy and contributes to food-production role signals.
- `MINE`: creates a miner vacancy and contributes to material-production role signals.
- `LUMBER_CAMP`: creates a lumberjack vacancy and contributes to material-production role signals.
- `ARCHITECT_WORKSHOP`: creates a builder vacancy and contributes to construction/project signals.
- `SMITHY`: contributes material-production infrastructure; use it as part of a developed material settlement.
- `STARTER_FORT`: founding and promotion infrastructure. It must have the required authority/interior zones.

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

Port or sea-entry points can affect the settlement's trade hints — the snapshot stores `tradeRouteSeed` and `seaEntrypoint` for later economy slices. Sea trade is not a complete player-facing loop yet.

## Recruits And Armies

Recruits follow their owner. Allies on the same side can help with group commands if the server allows it and they are close enough.

The command screen (`R`) shows order buttons:

- movement: `Hold`, `Follow`, `Regroup`, `Wander`, `Come to me`, `Patrol`, `Move to position`;
- formation: read from the player's saved formation (Formation tab);
- stance: `LOOSE`, `LINE_HOLD`, `SHIELD_WALL`.

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
- **Detail panel** on the right: attacker, defender, war state, goal type, casus belli, declaration time, allies, target positions, siege standards, occupations, and revolt status.
- Buttons: `Attacker info` / `Defender info` (open `PoliticalEntityInfoScreen`), `States` (state list), `Declare war`, `Cancel war`, `Occupy here`, `Annex here`, `Tribute: op only`, `Place siege here` (drop a siege standard at your current position), `Refresh`, `Close`.

`Place siege here` is enabled only when you are the leader of one of the war's sides and the war is not `RESOLVED`/`CANCELLED`. It sends `MessagePlaceSiegeStandardHere`, and the server validates against the same rules as `/bannermod siege place`.

`Declare war` opens a small War Room wizard where a state leader picks attacker, defender, goal, and optional casus belli text. The server still applies the same validation and cooldown denial reasons as `/bannermod war declare`, and the command remains available.

War outcome buttons are server-authoritative. `Cancel war`, `Occupy here`, and `Annex here` unlock for the attacking state leader on live wars and report success or denial in chat. `Tribute: op only` is visibly locked for normal leaders; forced tribute/peace/vassalization/demilitarization remain admin outcomes.

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
4. Clicking a row sends the invite.
5. The leader of the invited state sees the invite in the list; left-click accepts (`MessageRespondAllyInvite` accept), DEL/BACKSPACE declines. The leader of the inviting side sees the same row with a `(click to cancel)` hint.

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
