# BannerMod Multiplayer Guide

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

1. Pick a place for your base and claim at least one chunk. Open the map with `M`, right-click the chunk you want, choose `Claim chunk`. Claim cost and currency are server-defined (`AllowClaiming` must be true) and shown in the menu itself.
2. Create a state so your claim has a side: type `/bannermod state create <name>` in chat (e.g. `/bannermod state create Karl-City`). You become its leader. Inspect with `/bannermod state info <id-fragment>` or open the War Room (`U`) and click `States`.
3. Craft `Settlement Surveyor Tool` and `Building Placement Wand`. Both items are added by the mod and are how you validate your starter fort and register buildings. The surveyor recipe is 2 sticks, 2 oak planks and 1 iron ingot in a stairs-shaped pattern. The wand is registered the same way and can be obtained in creative or via server datapacks.
4. Place a `Starter Fort` — the keystone building, no settlement spawns without one. Surround it with the prefab requirements, then use the `Settlement Surveyor Tool`: right-click the anchor block, then right-click corners of each required zone. Shift+right-click cycles surveyor mode; shift+right-click on a block cycles the zone role (`INTERIOR`, `EXTERIOR`, ...). When the session is filled, right-click in the air without shift to validate the fort.
5. After a successful starter-fort validation the settlement bootstraps automatically: a `SettlementRecord` is created, a starter claim is added if needed, and starter residents spawn near the anchor.
6. For other buildings use the `Building Placement Wand`. The wand has 3 modes: `PLACE` (place a prefab), `VALIDATE` (verify what's already built), `REGISTER` (register the building with the settlement). Shift+right-click in the air cycles mode. Right-click a block to mark a corner of the validation/registration area.
7. Mark work areas: farm, storage, market, mine, lumber yard, build site. Outline them with the wand the same way (pick a prefab or freeform area, mark corners, register).
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

The same actions (Create / Rename / Capital here / Government form toggle) live in the War Room (`U`). The `→ Republic` / `→ Monarchy` button is leader-only and switches government form:

- `MONARCHY`: leader-only authority for key decisions.
- `REPUBLIC`: co-leaders also gain authority for some actions (status, capital).

## Promoting A Settlement Into A State

A settlement cannot become a full state without infrastructure. `/bannermod state status <entity> STATE` only succeeds when the settlement registers:

- a starter fort or town hall;
- storage;
- market.

If promotion is denied with a reason like `infrastructure_insufficient`, place and register the missing object (use the `Building Placement Wand` in `REGISTER` mode) and try again.

## Workers And Residents

Workers choose jobs through registered buildings and work areas. Storage, markets, farms, mines, and build areas give the settlement different capabilities.

Important checks:

- the work area must be inside a friendly claim;
- the worker must belong to the right side;
- the settlement must see the registered building or work area;
- if a claim is captured, removed, or becomes mismatched, work can stop.

Settlements have internal work orders. Some order state already survives server reloads, including worker claims. Transport orders (`HAUL_RESOURCE`/`FETCH_INPUT`) carry source, destination, resource filter, and item count.

The worker command screen (`X`) supports simple group orders: follow, guard, move to position, stop.

## Storage, Markets, And Trade

A `Storage Area` lets the settlement know where resources live. A `Market Area` plugs the settlement into the economy: merchants can open stalls and the settlement records what is sold and for which currency.

To register a market: outline a zone with the `Building Placement Wand` in `REGISTER` mode, pick a `Market` profile, and place the corners. Then assign a `Merchant` worker to the area. The merchant has its own trade screen (open it by interacting with the merchant), where you add `WorkersMerchantTrade` entries.

If the settlement lacks resources, check:

- storage exists inside the settlement;
- market is registered;
- a source of goods exists;
- trade is not blocked by war, ownership, or server configuration.

Port or sea-entry points can affect the settlement's trade hints — the snapshot stores `tradeRouteSeed` and `seaEntrypoint` for later economy slices.

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

You can set per-unit stances from the recruit's inventory (`RecruitInventoryScreen`); group stances live in the command screen.

## War Room (`U`)

War Room is the main in-game UI for war and politics:

- **Battle window banner**: top of the screen shows the current battle-window phase ("OPEN FRI 19:00-20:30 — closes in 45m" / "CLOSED — next SUN 18:00-19:30 in 1d 22h"). Green = open, gray = closed. The schedule is server-configured (`BattleWindows` in `bannermod-war-server.toml`) and synced to the client automatically.
- **Active wars list** on the left: each war shows state (`DECLARED`/`ACTIVE`/`IN_SIEGE_WINDOW`/`RESOLVED`/`CANCELLED`), attacker, defender.
- **Detail panel** on the right: attacker, defender, war state, goal type, casus belli, declaration time, allies, target positions, siege standards.
- Buttons: `Attacker info` / `Defender info` (open `PoliticalEntityInfoScreen`), `States` (state list), `Place siege here` (drop a siege standard at your current position), `Refresh`, `Close`.

`Place siege here` is enabled only when you are the leader of one of the war's sides and the war is not `RESOLVED`/`CANCELLED`. It sends `MessagePlaceSiegeStandardHere`, and the server validates against the same rules as `/bannermod siege place`.

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
- `/bannermod war revolts` — list pending revolts.
- `/bannermod war revolt resolve <revoltId> <outcome>` — admin-side resolution while objective-based revolts are still being implemented.

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
