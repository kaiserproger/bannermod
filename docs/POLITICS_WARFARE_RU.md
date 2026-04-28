# Политика и война

Политика, claims, war declarations, союзники, siege standards, occupations и outcomes связаны с владением поселениями и authority губернатора.

## Политические государства

- UI-first: нажми клавишу War Room, нажми `States`, нажми `Create`, введи имя и подтверди.
- Command fallback: `/state create <name>`.
- Создатель становится leader.
- State record хранит id, name, status, leader UUID, co-leaders, capital position, color, charter, ideology, home region, creation game time и government form.
- Default status: `SETTLEMENT`.
- Default government form: `MONARCHY`.
- В `States` UI leaders могут rename-ить state, поставить capital в текущую позицию и переключить government form.
- `/state setcapital <entity> [pos]`, `/state status <entity> <status>`, `/state info <entity>` и `/state list` являются command equivalents или admin/fallback tools.

## War Room UI

- Нажми клавишу War Room, чтобы открыть war list.
- Выбери war row, чтобы увидеть attacker, defender, state, goal, casus belli, battle-window phase, active siege count и ally counts.
- `Attacker info` и `Defender info` открывают political entity detail screens.
- `States` открывает political entity list UI.
- `Allies for selected war` открывает ally management UI.
- `Place siege here` отправляет server request поставить siege standard в текущей позиции игрока за сторону, лидером которой является игрок.
- `Refresh` обновляет client-side war list из текущего synced state.

## Claims и ownership

- Claims могут принадлежать political entity UUID.
- Игрок считается political member, если он leader или co-leader entity.
- Settlement bootstrap использует membership для создания первого claim-а.
- Claim editing разрешен owners, political leaders/co-leaders или creative admin с permission level 2.
- Пересечение claim-а с другими claims отклоняется.
- Дистанция между городами одной нации enforced через `TownMinCenterDistance`.

## Команды объявления войны

- War Room exposes a declare-war wizard in `WarListScreen`; it reuses the same server-side validation and cooldown denial reasons as the command path.
- War Room also exposes an outcome panel: attacking leaders can cancel, occupy the current chunk, or annex the current chunk; tribute and forced peace/vassalize/demilitarize remain visibly op-only.
- Declare command remains available: `/war declare <attacker> <defender> <goal> [casusBelli]`.
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

## Союзники

- UI-first: War Room -> выбрать war -> `Allies for selected war`.
- Side leaders могут открыть invite picker для attacker или defender side.
- Picker фильтрует invalid candidates и отправляет `MessageInviteAlly` для valid candidate.
- Invite rows можно accept, decline или cancel из allies screen в зависимости от того, кто лидирует invitee или inviter side.
- Command equivalents: `/ally invite`, `/ally accept`, `/ally decline`, `/ally cancel`, `/ally list`.
- Ally invites consent-based: invited entities должны принять приглашение.

## Siege standards

- UI-first: War Room -> выбрать war -> `Place siege here`. Сервер ставит standard в текущей позиции игрока, если игрок leader одной из сторон выбранной войны.
- Command equivalent: `/siege place <warId> <side> [pos] [radius]`.
- Список всех или по войне: `/siege list [warId]`.
- Admin remove: `/siege remove <standardId>`.
- Рецепт `siege_standard`: gold blocks вокруг, banner в центре, diamonds слева/справа, emerald block сверху по центру, stick снизу по центру.
- Siege zones влияют на governor heartbeat, tax collection, settlement recommendations и militia/recruit-related systems.

## Outcomes

- White peace закрывает войну и очищает sieges.
- Tribute переводит доступную treasury из loser-owned claims победителю до requested amount.
- Occupy создает occupation records для chunks.
- Annex передает claim в контексте chunk/center позиции команды attacker-у и дает loser-у lost-territory immunity.
- Vassalize обновляет political status defender-а.
- Demilitarize накладывает duration на defender-а.
- Cancel закрывает войну с reason и очищает sieges.
- Успешный revolt может убрать occupation.

## Связь с поселениями

- Claims определяют, settlement friendly, hostile, unclaimed или degraded.
- Governor assignment и policy updates зависят от этой binding.
- Under-siege settlements перестают собирать governor taxes.
- Recruit/citizen militia behavior может реагировать на siege-standard zones.
- War ownership changes могут менять контроль над settlements и claims.

## Текущие ограничения

- Часть outcomes command/admin-driven, а не полностью автоматизированный player UI flow.
- War objective AI, occupation control depth, morale и ranged-backline polish остаются known open areas. Occupation records/tax, objective-presence revolt resolution, consent-based allies и basic siege-standard attack/escort уже live в `WarOutcomeApplier`, `WarOccupationTaxTicker`, `WarRevoltScheduler`/`WarRevoltAutoResolver`, `WarAllyService`, `RecruitSiegeObjectiveAttackGoal` и `RecruitSiegeEscortGoal`.
- Sea trade существует как logistics hooks и settlement hints, но не как полный war/economy loop.
