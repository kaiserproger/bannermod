# Buildings, Validation, And Prefabs

Settlement buildings can be made in two ways: manually build and validate them, or use optional prefabs through the Building Placement Wand.

## Manual Building Validation

- Use `Settlement Surveyor Tool`.
- Choose the matching survey mode.
- Set an anchor, select zone roles, click two corners per zone, then validate.
- Validation success creates a `ValidatedBuildingRecord` in saved settlement building data.
- Non-fort buildings require an existing settlement at the anchor or a settlement bound to the claim at the anchor.
- Buildings from different types cannot overlap conflicting `INTERIOR`, `SLEEPING`, and `WORK_ZONE` zones inside the same settlement.

## Zone Roles

- `AUTHORITY_POINT`: authority/center marker, used by starter forts.
- `INTERIOR`: enclosed walkable interior.
- `SLEEPING`: bed zone for houses.
- `WORK_ZONE`: workplace area for farms, mines, lumber camps, smithies, and architect workshops.
- `FORT_PERIMETER`: defined role, not currently required by default building definitions.
- `ENTRANCE`: defined role, not currently required by default building definitions.
- `STORAGE`: container zone for storage.
- `PREFAB_FOOTPRINT`: defined role for prefab-related area marking.

## Manual Building Requirements

- House: `INTERIOR` and `SLEEPING`; at least 8 walkable interior blocks; at least 70% roof coverage; at least one bed in/near sleeping or interior; unclear entrance is a warning. Capacity is min(valid beds, walkable blocks / 8), clamped to at least 1 after success.
- Farm: `WORK_ZONE`; anchor within 24 blocks of work zone; at least 24 farmland/crop-capable blocks. Capacity is farmland / 48 clamped 1-4.
- Mine: `WORK_ZONE`; anchor within 32 blocks of work zone; at least 24 exposed stone/ore/deepslate blocks; exposed anchor warns. Capacity is 1 + mine-face blocks / 64 clamped 1-4.
- Lumber camp: `WORK_ZONE`; anchor within 32 blocks; productivity from logs plus half saplings must be at least 12. Capacity is productivity / 12 clamped 1-3.
- Smithy: `INTERIOR` and `WORK_ZONE`; at least 70% roof coverage; at least one anvil; at least one furnace or blast furnace; anvil must be within 4 blocks of furnace/blast furnace. Capacity is based on anchor sets and interior space, capped at 2.
- Storage: `STORAGE`; at least one container. Capacity is 0; quality is container count * 10 capped at 100.
- Architect workshop: `INTERIOR` and `WORK_ZONE`; at least 16 walkable interior blocks; at least 70% roof coverage; at least one crafting table placeholder. Capacity is min(crafting tables, interior walkable blocks / 24).

## Building Placement Wand

- Recipe: one gold block above one stick.
- Normal use opens the prefab selection screen client-side. This is the player UI for choosing a prefab before placing it.
- Shift-use cycles modes: place, validate, register.
- Shift-use on a block clears tap state.
- Place mode places the selected prefab as a BuildArea at the clicked face offset and player-facing direction.
- Validate mode records corner A, corner B, then center and sends a validation request.
- Register mode records corner A, corner B, center, then key block and sends a registration request.
- No selected prefab means the wand reports no selection and does nothing.

## Surveyor UI Flow

- The Surveyor Tool is in-world UI: chat/system messages report mode changes, anchor selection, zone role selection, corner capture, validation warnings, and validation success/failure.
- It does not open a separate screen for manual validation; the selected mode and selected role live on the item/session.

## Built-In Prefabs

- `bannermod:farm`: 7x3x7, farmer, wheat seed icon.
- `bannermod:lumber_camp`: 9x5x9, lumberjack, iron axe icon.
- `bannermod:mine`: 7x4x7, miner, iron pickaxe icon.
- `bannermod:pasture`: 11x3x11, shepherd, shears icon; currently maps to animal farmer.
- `bannermod:animal_pen`: 9x4x9, animal farmer, egg icon.
- `bannermod:fishing_dock`: 7x3x11, fisherman, fishing rod icon.
- `bannermod:market_stall`: 7x5x7, merchant, emerald icon.
- `bannermod:storage`: 9x7x9, no profession, chest icon.
- `bannermod:house`: 7x5x7, no profession, oak door icon.
- `bannermod:barracks`: 11x8x9, recruit swordsman, iron sword icon.
- `bannermod:town_hall`: 13x9x11, no profession, bell icon.

## Prefab Completion

- Placement creates a `BuildArea` with dimensions, facing, owner/team data, and generated structure NBT.
- When the BuildArea completes, prefab staffing looks up the prefab and embedded work area.
- Worker prefabs usually register one vacancy.
- Barracks register four recruit vacancies.
- Non-profession prefabs register no vacancy.

## Important Distinction

- Manual validation is the intended way to prove a player-built building exists.
- Prefabs are optional convenience for players who do not want to design/build a structure manually.
- Settlement founding specifically uses a manually built and validated starter fort.
