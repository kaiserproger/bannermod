# Politics And Warfare

Politics, claims, war declarations, allies, siege standards, occupations, and outcomes are connected to settlement ownership and governor authority.

## Political States

- UI-first: press the War Room key, click `States`, click `Create`, enter the name, and submit.
- Command fallback: `/state create <name>`.
- The creator becomes leader.
- State records store id, name, status, leader UUID, co-leaders, capital position, color, charter, ideology, home region, creation game time, and government form.
- Default status is `SETTLEMENT`.
- Default government form is `MONARCHY`.
- In the `States` UI, leaders can rename their state, set capital to their current position, and toggle government form.
- `/state setcapital <entity> [pos]`, `/state status <entity> <status>`, `/state info <entity>`, and `/state list` are command equivalents or admin/fallback tools.

## War Room UI

- Press the War Room key to open the war list.
- Select a war row to see attacker, defender, state, goal, casus belli, battle-window phase, active siege count, and ally counts.
- `Attacker info` and `Defender info` open political entity detail screens.
- `States` opens the political entity list UI.
- `Allies for selected war` opens the ally management UI.
- `Place siege here` sends a server request to place a siege standard at the player's current position for the side the player leads.
- `Refresh` reloads the client-side war list from current synced state.

## Claims And Ownership

- Claims can belong to a political entity UUID.
- A player is treated as a political member if they are leader or co-leader of an entity.
- Settlement bootstrap uses this membership to create the first claim.
- Claim editing allows owners, political leaders/co-leaders, or creative permission-level-2 admins.
- Claim overlap with other claims is rejected.
- Same-nation town distance is enforced through `TownMinCenterDistance`.

## War Declaration Commands

- Current UI coverage starts after a war exists. The current code has no declare-war button in `WarListScreen`; declaring a new war uses the command path.
- Declare: `/war declare <attacker> <defender> <goal> [casusBelli]`.
- Info: `/war info <warId>`.
- List: `/war list`.
- Cancel: `/war cancel <warId>`.
- Admin white peace: `/war whitepeace <warId>`.
- Admin tribute: `/war tribute <warId> <amount>`.
- Admin vassalize: `/war vassalize <warId>`.
- Admin demilitarize: `/war demilitarize <warId> <days>`.
- Occupy: `/war occupy <warId> [radius]`.
- Annex: `/war annex <warId>`.
- List occupations: `/war occupations`.
- List revolts: `/war revolts`.
- Admin resolve revolt: `/war revolt resolve <revoltId> <outcome>`.

## Allies

- UI-first: War Room -> select war -> `Allies for selected war`.
- Side leaders can open an invite picker for attacker or defender side.
- The picker filters invalid candidates and sends `MessageInviteAlly` for a valid candidate.
- Invite rows can be accepted, declined, or canceled from the allies screen depending on who leads the invitee or inviter side.
- Command equivalents: `/ally invite`, `/ally accept`, `/ally decline`, `/ally cancel`, `/ally list`.
- Ally invites are consent-based; invited entities must accept.

## Siege Standards

- UI-first: War Room -> select war -> `Place siege here`. The server places it at the player's current position if the player leads one side of the selected war.
- Command equivalent: `/siege place <warId> <side> [pos] [radius]`.
- List all or by war: `/siege list [warId]`.
- Admin remove: `/siege remove <standardId>`.
- Crafting recipe for `siege_standard`: gold blocks around, banner center, diamonds left/right, emerald block top center, stick bottom center.
- Siege zones affect governor heartbeat, tax collection, settlement recommendations, and militia/recruit-related systems.

## Outcomes

- White peace closes the war and clears sieges.
- Tribute transfers available treasury from loser-owned claims to winner up to requested amount.
- Occupy places occupation records for chunks.
- Annex transfers a claim at the command source chunk/center context to the attacker and grants lost-territory immunity to the loser.
- Vassalize updates defender political status.
- Demilitarize imposes a duration on the defender.
- Cancel closes the war with a reason and clears sieges.
- Revolt success can remove an occupation.

## Interaction With Settlements

- Claims determine whether a settlement is friendly, hostile, unclaimed, or degraded.
- Governor assignment and policy updates depend on this binding.
- Under-siege settlements stop collecting governor taxes.
- Recruit/citizen militia behavior can respond to siege-standard zones.
- War ownership changes can change who controls settlements and claims.

## Current Limits

- Some outcomes are command/admin-driven rather than fully automated player UI flows.
- War objective AI, occupation tax/control depth, morale, cavalry/ranged backline behavior, and siege objective AI are known open areas.
- Sea trade exists as logistics hooks and settlement hints, not a complete war/economy loop.
