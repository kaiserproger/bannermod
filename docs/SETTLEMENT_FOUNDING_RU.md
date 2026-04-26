# Основание поселения

Текущий survival-путь основания: построить форт самому, провалидировать его, затем выполнить bootstrap поселения.

## Что нужно заранее

- У игрока должно быть политическое государство. Preferred UI path: клавиша War Room -> `States` -> `Create` -> ввести имя. Command fallback: `/state create <name>`.
- Authority position форта должен быть в своем/дружественном claim-е, либо server-side bootstrap может создать первый claim для политического государства игрока.
- Town center не должен нарушать `TownMinCenterDistance` относительно другого города той же нации. Значение по умолчанию: 480 блоков.
- Обычный player-facing инструмент: `Settlement Surveyor Tool`.

## Управление Surveyor Tool

- Правый клик по блоку без shift: поставить survey anchor, если его еще нет; иначе записывать углы зоны.
- Shift-right-click по блоку: переключить выбранный `ZoneRole`.
- Правый клик в воздух без shift: провалидировать текущую сессию.
- Shift-right-click в воздух: переключить `SurveyorMode`.
- Новая сессия начинается в режиме `BOOTSTRAP_FORT` с anchor в `BlockPos.ZERO`.

## Режимы survey

- `BOOTSTRAP_FORT` валидирует `STARTER_FORT`.
- `HOUSE` валидирует дом.
- `FARM` валидирует ферму.
- `MINE` валидирует шахту.
- `LUMBER_CAMP` валидирует лагерь лесоруба.
- `SMITHY` валидирует кузницу.
- `STORAGE` валидирует склад.
- `ARCHITECT_BUILDER` валидирует мастерскую архитектора.
- `INSPECT_EXISTING` выводит запись о валидированной постройке у выбранного anchor-а.

## Требования стартового форта

- Обязательные зоны: `AUTHORITY_POINT` и `INTERIOR`.
- Anchor должен быть внутри хотя бы одной выбранной зоны.
- Любая выбранная зона должна иметь положительный объем и не превышать max zone volume валидатора.
- `INTERIOR` обязателен как blocking-требование.
- Если `AUTHORITY_POINT` отсутствует или не содержит anchor, validation выдает warning.
- Если рядом нет баннера в радиусе `SettlementFortBannerMaxDistance`, validation выдает warning. Радиус по умолчанию: 8 блоков.
- Если walkable interior blocks меньше 64, validation выдает warning.
- Если roof coverage меньше 80%, validation выдает warning.
- Если вход неясен, validation выдает warning.
- Успех дает стартовому форту capacity 4 и quality, равный проценту roof coverage, capped at 100.

## Результат bootstrap

- Bootstrap использует anchor валидированного форта как authority position.
- Если claim уже есть в этой точке, игрок должен владеть им или быть leader/co-leader политического владельца.
- Если claim-а нет, bootstrap пытается создать one-chunk claim с центром в чанке authority position.
- Новая запись поселения хранит settlement id, UUID игрока-владельца, political entity id, claim id, dimension, authority position, town center, status `ACTIVE` и game time создания.
- Стартовые жители после успешного server-player bootstrap: farmer, miner, lumberjack, builder.

## Claim notes

- Claim хранит claimed chunks, center, owner political entity id, player info и permissions для block interaction/placement/breaking.
- World Map UI открывается map key в Overworld. Right-click context actions включают claim area, claim chunk, edit claim, remove chunk и admin-only delete/teleport actions.
- Создание claim area через world map выбирает квадрат 5x5 чанков вокруг выбранного чанка client-side, но server update validation отклоняет новые claims от non-admin игроков, если claim еще не существует.
- Поэтому starter-fort bootstrap является intended способом создать первый survival settlement claim.

## Причины провала

- Нет player/session/validation data.
- Validation должна быть valid и типа `STARTER_FORT`.
- Нет validation snapshot.
- Чужой claim в authority position.
- Нет политического государства, поэтому automatic claim creation невозможен.
- Город слишком близко к другому городу той же нации.
- Нет claim manager, поэтому automatic claim creation невозможен.
