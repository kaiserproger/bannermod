# Губернатор и управление

Губернатор - это повышенный owned recruit, записанный в claim. Отдельного governor entity type сейчас нет.

## Как получить губернатора

1. Основать или контролировать дружественное claimed settlement.
2. Получить рекрута через vacancy flow бараков или другой owned recruit flow.
3. Прокачать рекрута до XP level 7 или выше.
4. Открыть инвентарь рекрута.
5. Нажать `Promote` в recruit inventory UI.
6. Выбрать `Governor` в promote screen UI.
7. При успехе Governor screen сразу открывается для этого рекрута.

## Требования назначения

- Recruit должен существовать и быть в network interaction range, когда обрабатывается promote packet.
- Recruit XP level должен быть минимум 7.
- У recruit должен быть owner UUID.
- Resolved claim/settlement binding должен быть `FRIENDLY_CLAIM`.
- Actor должен пройти authority checks: owner, разрешенный same-team actor или creative admin с permission level 2.
- Settlement не может быть unclaimed, hostile или degraded.

## Что происходит при успехе

- Optional custom name из promote screen применяется к рекруту.
- Governor snapshot для claim создается или обновляется.
- Recruit UUID и recruit owner UUID сохраняются как governor identity.
- Для claim запускается refresh settlement snapshot.
- Игрок получает success message.
- Для этого рекрута открывается Governor screen.

## Данные Governor screen

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

## Политики

- `GARRISON_PRIORITY`: 0 low, 1 balanced, 2 high.
- `FORTIFICATION_PRIORITY`: 0 low, 1 balanced, 2 high.
- `TAX_PRESSURE`: 0 low, 1 balanced, 2 high.
- Policy buttons увеличивают или уменьшают значения и clamp-ят их в 0-2.
- Garrison и fortification policies влияют на settlement growth scoring для military/defense projects.
- Tax pressure сейчас сохраняется и отображается; heartbeat tax amount сейчас фиксирован на citizen.

## Heartbeat rules

- Citizens в governor heartbeat = villagers + workers внутри claim.
- Base tax due = 2 за citizen.
- Friendly claims собирают весь due tax.
- Hostile, degraded и unclaimed settlements собирают 0.
- Under siege settlements собирают 0, даже если otherwise friendly.
- Отсутствие workers создает incident `worker_shortage`.
- Recruit count ниже `max(1, citizens / 2)` рекомендует increase garrison и strengthen fortifications.
- Blocked worker supply рекомендует relieve supply pressure.
- Blocked recruit upkeep рекомендует relieve supply pressure.
- Если других recommendations нет, recommendation = hold course.

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

## Revoke и контроль

- Revocation требует assigned governor и authority над governor owner/team.
- Revocation также требует, чтобы текущий settlement binding оставался controllable.
- Если allowed, governor recruit UUID и owner UUID очищаются из snapshot.

## Частые причины отказа

- Recruit ниже level 7.
- У recruit нет owner.
- По позиции recruit не находится claim.
- Settlement binding unclaimed.
- Claim принадлежит hostile political entity.
- Settlement/claim binding degraded или mismatched.
- Игрок не проходит authority checks.
