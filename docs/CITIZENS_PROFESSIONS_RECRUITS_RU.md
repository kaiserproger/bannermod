# Жители, профессии, работники и рекруты

Intended survival-флоу завязан на vacancy: свободные citizens занимают места в валидных/созданных постройках и получают соответствующую профессию.

## Жизненный цикл citizen

- `CitizenEntity` может существовать с профессией `NONE`.
- На каждом server AI step он может попросить prefab staffing назначить ближайшую vacancy.
- Назначение записывает pending profession token и bound anchor UUID на citizen.
- Citizen конвертируется только когда подходит к bound anchor на расстояние до 3 блоков squared (`distanceToSqr <= 9`).
- Конвертация также проверяет, что в vacancy все еще есть свободное место.
- После конвертации старый citizen entity удаляется, а worker или recruit entity создается на той же позиции.

## Рабочие профессии

- Farmer.
- Lumberjack.
- Miner.
- Builder.
- Merchant.
- Fisherman.
- Animal farmer.
- Shepherd vacancies сейчас используют animal farmer entity/profession.

## Рекрутские профессии

- Recruit swordsman мапится на base recruit / `RECRUIT_SPEAR` citizen profession.
- Recruit archer мапится на bowman / `RECRUIT_BOWMAN`.
- Recruit pikeman мапится на shieldman / `RECRUIT_SHIELDMAN`.
- Recruit crossbow мапится на crossbowman / `RECRUIT_CROSSBOWMAN`.
- Recruit cavalry мапится на horseman / `RECRUIT_HORSEMAN`.
- Текущий встроенный barracks prefab использует `RECRUIT_SWORDSMAN` и дает четыре recruit vacancies.

## Как получить рекрутов

- В нормальном gameplay flow рекруты появляются из citizens, которые занимают места в бараках.
- Свободный citizen должен быть рядом с barracks vacancy anchor.
- Когда citizen доходит до anchor-а и есть свободный slot, он конвертируется в recruit type этой vacancy.
- Это отдельный путь от spawn egg-ов и debug/admin spawn.

## Автономные settlement workers

- Валидное стартовое поселение спавнит стартовых citizens/workers через bootstrap: farmer, miner, lumberjack, builder.
- Worker birth и autonomous claim worker growth включены config-ом по умолчанию.
- Default settlement worker profession pool: farmer, miner, lumberjack, builder.
- Default minimum villager count для settlement spawn/birth: 6.
- Default worker cap на friendly settlement claim: 4.
- Default autonomous settlement spawn cooldown: 3 Minecraft days.
- Default claim worker growth cooldown base: 24000 ticks, дальше scaling по текущему числу workers.

## Work orders и jobs

- Settlement runtime публикует work orders из building snapshots.
- Work orders могут быть pending, claimed, completed, canceled или released обратно при abandonment.
- Default claim expiry: 1200 ticks.
- Built-in job handlers: harvest и build.
- Workers claim-ят jobs по settlement/building context и выполняют job steps через settlement orchestrator.

## Жилье и occupancy

- Settlement runtime назначает homes через home assignment advisors, если snapshots содержат residents и homes.
- Citizens и workers учитываются как residents в settlement snapshots, когда находятся внутри claim bounds.
- Housing pressure влияет на settlement growth scoring.

## Управление игроком

- Рекруты сохраняют recruit command behavior: follow/hold/move, aggro state, combat stance, target assignment, group/selection commands.
- Combat stances: `LOOSE`, `LINE_HOLD`, `SHIELD_WALL`.
- Formation-aware combat включает leash behavior, cohesion mitigation, shield mitigation, flank damage, brace-against-cavalry mitigation и protected-target propagation.

## Текущие ограничения

- Manual validated buildings и prefab vacancies являются соседними системами; не каждый manual validation path сейчас создает такую же auto-staffing vacancy, как prefab completion.
- У shepherd пока нет отдельного entity, он мапится на animal farmer.
- Barracks сейчас по умолчанию использует swordsman/recruit vacancies.
