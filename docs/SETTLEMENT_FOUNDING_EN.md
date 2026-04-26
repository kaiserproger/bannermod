# Settlement Founding

This is the current survival founding path: build a fort yourself, validate it, then bootstrap the settlement.

## Required Setup

- The player should have a political state. Preferred UI path: War Room key -> `States` -> `Create` -> enter name. Command fallback: `/state create <name>`.
- The fort authority position must be in an owned/friendly claim, or the server-side bootstrap can create the first claim for the player's political state.
- The town center cannot violate `TownMinCenterDistance` against another town owned by the same nation. Default distance is 480 blocks.
- The normal player tool is `Settlement Surveyor Tool`.

## Surveyor Tool Controls

- Right-click a block without sneaking: set the survey anchor if none exists; otherwise record zone corners.
- Shift-right-click a block: cycle the selected `ZoneRole`.
- Right-click in air without sneaking: validate the current session.
- Shift-right-click in air: cycle `SurveyorMode`.
- A new tool session starts in `BOOTSTRAP_FORT` mode with anchor at `BlockPos.ZERO`.

## Survey Modes

- `BOOTSTRAP_FORT` validates a `STARTER_FORT`.
- `HOUSE` validates a house.
- `FARM` validates a farm.
- `MINE` validates a mine.
- `LUMBER_CAMP` validates a lumber camp.
- `SMITHY` validates a smithy.
- `STORAGE` validates storage.
- `ARCHITECT_BUILDER` validates an architect workshop.
- `INSPECT_EXISTING` prints the record for a validated building at the selected anchor.

## Starter Fort Requirements

- Required zones: `AUTHORITY_POINT` and `INTERIOR`.
- The anchor must be inside at least one selected zone.
- Any selected zone must have positive volume and cannot exceed the validator's max zone volume.
- `INTERIOR` is blocking-required.
- If `AUTHORITY_POINT` is missing or does not contain the anchor, validation warns.
- If no banner is within `SettlementFortBannerMaxDistance`, validation warns. Default radius is 8 blocks.
- If walkable interior blocks are under 64, validation warns.
- If roof coverage is under 80%, validation warns.
- If the entrance is unclear, validation warns.
- Success gives starter-fort capacity 4 and quality equal to roof coverage percent capped at 100.

## Bootstrap Result

- The bootstrap uses the validated fort anchor as authority position.
- If a claim already exists there, the player must own it or lead/co-lead the owning political entity.
- If no claim exists, bootstrap tries to create a one-chunk claim centered on the fort authority chunk.
- The new settlement record stores settlement id, owner player UUID, political entity id, claim id, dimension, authority position, town center, status `ACTIVE`, and creation game time.
- Starter citizens spawned after successful server-player bootstrap: farmer, miner, lumberjack, builder.

## Claim Notes

- A claim stores claimed chunks, center, owner political entity id, player info, and block interaction/placement/breaking permissions.
- World Map UI opens from the map key in the Overworld. Right-click context actions include claim area, claim chunk, edit claim, remove chunk, and admin-only delete/teleport actions.
- World-map claim area creation selects a 5x5 chunk square around the selected chunk client-side, but server update validation rejects new claims from non-admins unless they already exist.
- The starter-fort bootstrap path is therefore the intended way to create the first survival settlement claim.

## Failure Cases

- Missing player/session/validation data fails.
- Validation must be valid and type `STARTER_FORT`.
- Missing validation snapshot fails.
- Foreign claim at authority position fails.
- Missing political state prevents automatic claim creation.
- Too-close same-nation town fails.
- Missing claim manager prevents automatic claim creation.
