# Citizens, Professions, Workers, And Recruits

The intended survival flow is vacancy-driven: free citizens occupy validated/provided building slots and become the matching profession.

## Citizen Lifecycle

- `CitizenEntity` can exist with profession `NONE`.
- Each server AI step can ask prefab staffing to assign a nearby vacancy.
- Assignment stores a pending profession token and bound anchor UUID on the citizen.
- The citizen converts only when it reaches within 3 blocks squared distance (`distanceToSqr <= 9`) of the bound anchor.
- Conversion also checks that the vacancy still has a free slot.
- After conversion, the old citizen entity is discarded and a worker or recruit entity is spawned at the same position.

## Worker Professions

- Farmer.
- Lumberjack.
- Miner.
- Builder.
- Merchant.
- Fisherman.
- Animal farmer.
- Shepherd vacancies currently reuse the animal farmer entity/profession.

## Recruit Professions

- Recruit swordsman maps to base recruit / `RECRUIT_SPEAR` citizen profession.
- Recruit archer maps to bowman / `RECRUIT_BOWMAN`.
- Recruit pikeman maps to shieldman / `RECRUIT_SHIELDMAN`.
- Recruit crossbow maps to crossbowman / `RECRUIT_CROSSBOWMAN`.
- Recruit cavalry maps to horseman / `RECRUIT_HORSEMAN`.
- The current built-in barracks prefab uses `RECRUIT_SWORDSMAN` and grants four recruit vacancies.

## How Recruits Are Obtained

- In the normal gameplay flow, recruits come from citizens occupying barracks vacancies.
- A free citizen must be near the barracks vacancy anchor.
- When the citizen reaches the anchor and a slot is free, it converts into the recruit type for that vacancy.
- This is separate from spawn eggs and debug/admin spawning.

## Autonomous Settlement Workers

- A valid starter settlement spawns starter citizens/workers through the bootstrap path: farmer, miner, lumberjack, builder.
- Worker birth and autonomous claim worker growth are enabled by config by default.
- Default settlement worker profession pool: farmer, miner, lumberjack, builder.
- Default minimum villager count for settlement spawn/birth is 6.
- Default worker cap per friendly settlement claim is 4.
- Default autonomous settlement spawn cooldown is 3 Minecraft days.
- Default claim worker growth cooldown base is 24000 ticks and scales by current worker count.

## Work Orders And Jobs

- Settlement runtime publishes work orders from building snapshots.
- Work orders can be pending, claimed, completed, canceled, or released back when abandoned.
- Claim expiry default is 1200 ticks.
- Built-in job handlers are harvest and build.
- Workers claim jobs by settlement/building context and execute job steps through the settlement orchestrator.

## Housing And Occupancy

- Settlement runtime assigns homes using home assignment advisors when snapshots contain residents and homes.
- Citizens and workers are tracked as residents in settlement snapshots when inside the claim bounds.
- Housing pressure contributes to settlement growth scoring.

## Player Control Notes

- Recruits retain recruit command behavior: follow/hold/move, aggro state, combat stance, target assignment, and group/selection based commands.
- Combat stances are `LOOSE`, `LINE_HOLD`, and `SHIELD_WALL`.
- Formation-aware combat includes leash behavior, cohesion mitigation, shield mitigation, flank damage handling, brace-against-cavalry mitigation, and protected-target propagation.

## Current Limits

- Manual validated buildings and prefab vacancies are adjacent systems; not every manual validation path currently creates the same auto-staffing vacancy behavior as prefab completion.
- Shepherd has no dedicated entity yet and maps to animal farmer.
- Barracks currently uses swordsman/recruit vacancies by default.
