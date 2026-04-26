# Governor And Governance

The governor is a promoted owned recruit recorded against a claim. It is not a separate governor entity type.

## How To Get A Governor

1. Found or control a friendly claimed settlement.
2. Obtain a recruit through barracks vacancy flow or other owned recruit flow.
3. Level the recruit to XP level 7 or higher.
4. Open the recruit inventory.
5. Press `Promote` in the recruit inventory UI.
6. Choose `Governor` in the promote screen UI.
7. On success, the Governor screen opens immediately for that recruit.

## Designation Requirements

- Recruit must exist and be within network interaction range when the promote packet is handled.
- Recruit XP level must be at least 7.
- Recruit must have an owner UUID.
- The resolved claim/settlement binding must be `FRIENDLY_CLAIM`.
- The actor must pass authority checks: owner, permitted same-team actor, or creative permission-level-2 admin.
- The settlement cannot be unclaimed, hostile, or degraded.

## What Happens On Success

- Optional custom name from the promote screen is applied.
- The governor snapshot for the claim is created or updated.
- The recruit UUID and recruit owner UUID are stored as governor identity.
- Settlement snapshot refresh runs for the claim.
- The player gets a success message.
- The Governor screen opens for that recruit.

## Governor Screen Data

- Settlement binding status.
- Citizen count.
- Taxes due.
- Taxes collected.
- Last heartbeat tick.
- Garrison recommendation.
- Fortification recommendation.
- Policy values.
- Treasury balance.
- Last treasury net change.
- Projected treasury balance.
- Incident tokens.
- Recommendation tokens.

## Policies

- `GARRISON_PRIORITY`: 0 low, 1 balanced, 2 high.
- `FORTIFICATION_PRIORITY`: 0 low, 1 balanced, 2 high.
- `TAX_PRESSURE`: 0 low, 1 balanced, 2 high.
- Policy buttons step values up or down and clamp to 0-2.
- Garrison and fortification policies feed settlement growth scoring for military/defense projects.
- Tax pressure is currently persisted and displayed; the heartbeat tax amount is currently fixed per citizen.

## Heartbeat Rules

- Citizens counted by the governor heartbeat are villagers + workers in the claim.
- Base tax due is 2 per citizen.
- Friendly claims collect all due tax.
- Hostile, degraded, and unclaimed settlements collect zero.
- Under siege settlements collect zero even if otherwise friendly.
- No workers creates `worker_shortage` incident.
- Recruit count below `max(1, citizens / 2)` recommends increasing garrison and strengthening fortifications.
- Blocked worker supply recommends relieving supply pressure.
- Blocked recruit upkeep recommends relieving supply pressure.
- If no other recommendation exists, the recommendation is hold course.

## Incidents

- `hostile_claim`.
- `degraded_settlement`.
- `unclaimed_settlement`.
- `under_siege`.
- `worker_shortage`.
- `supply_blocked`.
- `recruit_upkeep_blocked`.

## Recommendations

- `hold_course`.
- `increase_garrison`.
- `strengthen_fortifications`.
- `relieve_supply_pressure`.

## Revocation And Control

- Revoking requires an assigned governor and authority over the governor owner/team.
- Revocation also requires the current settlement binding to remain controllable.
- If allowed, the governor recruit UUID and owner UUID are cleared from the snapshot.

## Common Denials

- Recruit has less than level 7.
- Recruit has no owner.
- No claim resolves at the recruit position.
- Settlement binding is unclaimed.
- Claim belongs to a hostile political entity.
- Settlement/claim binding is degraded or mismatched.
- Acting player does not pass authority checks.
