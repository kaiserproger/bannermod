# Постройки, валидация и префабы

Постройки поселения можно получать двумя путями: построить вручную и провалидировать, либо опционально использовать префабы через Building Placement Wand.

## Ручная валидация построек

- Используй `Settlement Surveyor Tool`.
- Выбери подходящий survey mode.
- Поставь anchor, выбери роли зон, кликни два угла для каждой зоны и запусти validation.
- Успешная validation создает `ValidatedBuildingRecord` в saved building data поселения.
- Нефортовые постройки требуют существующее поселение у anchor-а или поселение, привязанное к claim-у у anchor-а.
- Постройки разных типов не могут пересекаться конфликтующими зонами `INTERIOR`, `SLEEPING` и `WORK_ZONE` внутри одного поселения.

## Роли зон

- `AUTHORITY_POINT`: authority/center marker, используется стартовыми фортами.
- `INTERIOR`: закрытый walkable интерьер.
- `SLEEPING`: зона кроватей для домов.
- `WORK_ZONE`: рабочая зона для farm, mine, lumber camp, smithy и architect workshop.
- `FORT_PERIMETER`: роль определена, но сейчас не требуется default building definitions.
- `ENTRANCE`: роль определена, но сейчас не требуется default building definitions.
- `STORAGE`: зона контейнеров для склада.
- `PREFAB_FOOTPRINT`: роль для prefab-related разметки.

## Требования ручных построек

- House: `INTERIOR` и `SLEEPING`; минимум 8 walkable interior blocks; минимум 70% roof coverage; минимум одна кровать в/рядом со sleeping или interior; неясный вход дает warning. Capacity = min(valid beds, walkable blocks / 8), после успеха минимум 1.
- Farm: `WORK_ZONE`; anchor в пределах 24 блоков от work zone; минимум 24 farmland/crop-capable blocks. Capacity = farmland / 48, clamp 1-4.
- Mine: `WORK_ZONE`; anchor в пределах 32 блоков от work zone; минимум 24 exposed stone/ore/deepslate blocks; открытый небу anchor дает warning. Capacity = 1 + mine-face blocks / 64, clamp 1-4.
- Lumber camp: `WORK_ZONE`; anchor в пределах 32 блоков; productivity из logs плюс половина saplings должна быть минимум 12. Capacity = productivity / 12, clamp 1-3.
- Smithy: `INTERIOR` и `WORK_ZONE`; минимум 70% roof coverage; минимум одна anvil; минимум furnace или blast furnace; anvil в пределах 4 блоков от furnace/blast furnace. Capacity зависит от anchor sets и interior space, максимум 2.
- Storage: `STORAGE`; минимум один container. Capacity = 0; quality = container count * 10, capped at 100.
- Architect workshop: `INTERIOR` и `WORK_ZONE`; минимум 16 walkable interior blocks; минимум 70% roof coverage; минимум один crafting table placeholder. Capacity = min(crafting tables, interior walkable blocks / 24).

## Building Placement Wand

- Рецепт: один золотой блок над одной палкой.
- Обычное использование открывает prefab selection screen client-side. Это player UI для выбора prefab перед placement.
- Shift-use переключает режимы: place, validate, register.
- Shift-use по блоку сбрасывает tap state.
- Place mode ставит выбранный prefab как BuildArea на offset от кликнутой стороны и с направлением игрока.
- Validate mode записывает corner A, corner B, затем center и отправляет validation request.
- Register mode записывает corner A, corner B, center, затем key block и отправляет registration request.
- Если prefab не выбран, wand сообщает no selection и ничего не делает.

## Surveyor UI flow

- Surveyor Tool использует in-world UI: chat/system messages показывают mode changes, anchor selection, zone role selection, corner capture, validation warnings и validation success/failure.
- Отдельный screen для manual validation не открывается; выбранный mode и selected role живут в item/session.

## Встроенные префабы

- `bannermod:farm`: 7x3x7, farmer, иконка wheat seed.
- `bannermod:lumber_camp`: 9x5x9, lumberjack, иконка iron axe.
- `bannermod:mine`: 7x4x7, miner, иконка iron pickaxe.
- `bannermod:pasture`: 11x3x11, shepherd, иконка shears; сейчас мапится на animal farmer.
- `bannermod:animal_pen`: 9x4x9, animal farmer, иконка egg.
- `bannermod:fishing_dock`: 7x3x11, fisherman, иконка fishing rod.
- `bannermod:market_stall`: 7x5x7, merchant, иконка emerald.
- `bannermod:storage`: 9x7x9, без профессии, иконка chest.
- `bannermod:house`: 7x5x7, без профессии, иконка oak door.
- `bannermod:barracks`: 11x8x9, recruit swordsman, иконка iron sword.
- `bannermod:town_hall`: 13x9x11, без профессии, иконка bell.

## Завершение префаба

- Placement создает `BuildArea` с dimensions, facing, owner/team data и generated structure NBT.
- Когда BuildArea завершен, prefab staffing ищет prefab и embedded work area.
- Worker-prefab обычно регистрирует одну vacancy.
- Barracks регистрируют четыре recruit vacancies.
- Prefab без профессии не регистрирует vacancy.

## Важное отличие

- Ручная validation доказывает, что player-built building существует.
- Префабы являются optional convenience для игроков, которым не хочется проектировать/строить вручную.
- Основание поселения использует именно вручную построенный и провалидированный starter fort.
