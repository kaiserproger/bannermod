# BannerMod Gameplay Flow

This document describes the current survival flow in the active root mod code (`bannermod`). Legacy `recruits/` and `workers/` trees are reference only.

## Core Loop

1. Create or join a political state.
2. Found a settlement by building and validating a starter fort.
3. Let starter citizens occupy validated profession buildings.
4. Grow the settlement with housing, workplaces, storage, market access, and defenses.
5. Turn citizens into workers or recruits through building vacancies.
6. Promote an eligible recruit into governor after the settlement has a friendly claim.
7. Use the governor, economy, recruits, allies, and siege systems to manage conflict.

## Survival Start

- UI-first: press the War Room key, open `States`, press `Create`, enter a name, and the server creates a state with you as leader and your current position as capital.
- Command fallback: `/state create <name>` does the same state creation from chat.
- A settlement needs a friendly claim. The starter-fort bootstrap can create the first one if the player has a political state and the location is not too close to another town in the same nation.
- Build the fort manually first. Prefabs are optional convenience for later buildings; they are not required for founding.
- Craft the `Settlement Surveyor Tool` with planks, sticks, and one iron ingot.
- Craft the `Building Placement Wand` with one gold block over one stick. It exists for optional prefab placement/validation/register flows.

## Settlement Founding

- Use the Surveyor Tool in `BOOTSTRAP_FORT` mode.
- First normal right-click sets the anchor.
- Shift-right-click on blocks cycles the selected zone role.
- For each zone, click two corners. The selected role is stored on the tool.
- The starter fort requires `AUTHORITY_POINT` and `INTERIOR` zones.
- The anchor must be inside at least one selected zone.
- The authority point should include the anchor.
- A banner within the configured fort banner radius is recommended.
- The fort gets warnings for a small interior, low roof coverage, or unclear entrance, but blocking failures stop bootstrap.
- Normal right-click in air validates the current survey session.
- A valid starter fort creates or binds the claim, registers the settlement, and spawns starter citizens: farmer, miner, lumberjack, and builder.

## Building Growth

- Build a profession building manually and validate it with the Surveyor Tool, or place a prefab with the Building Placement Wand when convenience matters.
- Manual validation records the building in the settlement registry.
- Prefab completion creates a BuildArea-based building and can register profession vacancies automatically.
- Citizens without a profession search nearby vacancies and convert when they reach the assigned anchor.
- Worker buildings create worker professions. Barracks create recruit slots.

## Citizens, Workers, And Recruits

- `CitizenEntity` starts with `NONE` profession.
- When assigned to a vacancy, the citizen receives a pending profession and bound work-area/building UUID.
- The citizen converts only when close enough to that bound anchor and a slot is still available.
- Civilian vacancies become workers: farmer, lumberjack, miner, builder, merchant, fisherman, or animal farmer.
- Barracks vacancies become recruits. Current barracks slots are swordsman/recruit slots, with four recruit vacancies for the barracks prefab.
- Spawn eggs and direct entity registration exist in code, but they are not the normal survival flow.

## Governor Flow

- Open a recruit inventory and use Promote.
- Choose Governor in the promotion screen.
- Requirements: recruit XP level 7 or higher, recruit has an owner, and the recruit is in or tied to a friendly claimed settlement.
- The actor must be the owner, same team where allowed by authority rules, or a creative level-2 admin.
- A hostile, unclaimed, or degraded settlement denies the designation.
- On success, the recruit is not replaced by a new governor entity; the recruit UUID is recorded as the claim governor and the Governor screen opens.
- The Governor screen exposes settlement status, citizen count, taxes, incidents, recommendations, treasury data, and policy buttons.

## Governor Policies

- `GARRISON_PRIORITY`, `FORTIFICATION_PRIORITY`, and `TAX_PRESSURE` each range from 0 to 2.
- Labels are low, balanced, and high.
- Garrison and fortification priorities affect settlement growth scoring for defense-related projects.
- Tax pressure is stored and shown; the current heartbeat tax formula is fixed at 2 currency units per citizen before siege/status modifiers.

## Economy Snapshot

- A friendly governed claim counts villagers plus workers as citizens.
- Taxes are due at `2 * citizens`.
- Taxes are collected only from friendly claims and are set to zero while under siege.
- Governor incidents include hostile claim, degraded settlement, unclaimed settlement, under siege, worker shortage, blocked worker supply, and blocked recruit upkeep.
- Recommendations include hold course, increase garrison, strengthen fortifications, and relieve supply pressure.

## War And Politics

- Political states are managed from the War Room `States` screen for create, rename, capital, and government-form actions.
- The War Room key opens the war list UI. From there you can inspect attacker/defender states, open the state list, place a siege standard at your current position, refresh war state, and manage allies for the selected war.
- Allies are invited through the War Room allies UI and must accept. Commands mirror this flow for fallback/admin use.
- Siege standards can be placed from the War Room with `Place siege here`; `/siege place <warId> <side> [pos] [radius]` is the command equivalent.
- Current war declaration itself is command-side: `/war declare <attacker> <defender> <goal> [casusBelli]`.
- War outcomes currently include white peace, tribute, occupation, annexation, vassalization, demilitarization, cancel, and revolt resolution.

## Important Limits

- Full in-game onboarding is still incomplete; these docs describe the implemented flow.
- Sea-trade and some war outcomes exist as runtime hooks but are not a full player-facing loop yet.
- Manual building validation and prefab completion are related but not identical code paths.
