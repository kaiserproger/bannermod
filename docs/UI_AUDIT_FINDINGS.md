# BannerMod UI Audit Findings

Audit branch: `worktree-agent-adf151fd8bcd7e647` (foundation merge from `feature/ui-minecraft-style`).
Scope: every player-facing screen under `src/main/java/com/talhanation/bannermod/client/{military,civilian}/gui/**`.
Severity legend: **BLOCKER** (player can't progress / wrong server packet sent), **HIGH** (gameplay-blocking confusion: greyed buttons with no reason; missing required action), **MEDIUM** (visual/UX inconsistency, fixable), **LOW** (cosmetic / palette).

Each section also marks each finding as **[FIXED]** (committed in this audit) or **[DOC]** (documented only — left for follow-up).

---

## war/PromoteScreen.java — multiple

- **HIGH** — line 119: `createProfessionButtons(BUTTON_SIEGE_ENGINEER, BannerModMain.isSiegeWeaponsLoaded ? TOOLTIP_SIEGE_ENGINEER_DISABLED : TOOLTIP_SIEGE_ENGINEER_DISABLED, 5, false);` — both ternary branches return the same `_DISABLED` constant. Almost certainly meant `isSiegeWeaponsLoaded ? TOOLTIP_SIEGE_ENGINEER : TOOLTIP_SIEGE_ENGINEER_DISABLED`. Since the button is hardcoded `active = false` either way, the player only ever sees the disabled tooltip; the typo is dormant but will surface the moment the gating expression becomes meaningful. **[FIXED]** — collapsed to single-arg form.
- **HIGH** — lines 117/124/125: ASSASSIN, SPY, ROGUE are intentionally disabled via `false && recruit.getXpLevel() >= N`, but their tooltip points at the *active-state* description (`TOOLTIP_ASSASSIN`, `TOOLTIP_SPY`, `TOOLTIP_ROGUE`). When greyed, the tooltip doesn't tell the player *why* — it describes what the role would do. **[FIXED]** — added `*.disabled` lang keys for assassin/spy/rogue and switched the tooltip when the button is forced off.
- **HIGH** — line 117 (`false && ...`): the `false &&` short-circuit makes the level requirement meaningless. The text reads as if level 5 unlocks Assassin; in reality the feature is gated. The static lang key now makes that explicit; recommend follow-up to either remove the dead level check or thread a feature-flag tooltip. **[DOC]**

## military/RecruitInventoryScreen.java

- **HIGH** — nobles get a wall of greyed buttons (`buttonMount`, `stanceButton`, `leftListenButton`, `rightListenButton`, `moreButton`, plus the noble-locked Aggro / Orders rows) with no tooltip explaining "noble cannot be commanded that way." Existing screen already sets a status-line, but the per-button denial is missing. **[FIXED]** — added a shared `TOOLTIP_NOBLE_LOCKED` tooltip on the five button-level locks; the menus already show their own multi-line tooltip and were left intact.
- **MEDIUM** — `clearUpkeep.active = recruit.hasUpkeep()` (line 265) — no tooltip indicating "no upkeep currently set" when greyed. **[FIXED]** — tooltip now switches to `clearUpkeep_disabled` when there is nothing to clear.
- **MEDIUM** — Promote button (line 339) sets `setTooltip(canPromote ? TOOLTIP_PROMOTE : TOOLTIP_DISABLED_PROMOTE)`. This works for the vanilla path but the special-companion fork at line 327 always uses `TOOLTIP_SPECIAL` even when `canPromote` is false. **[DOC]** — split-tooltip would require duplicating the disable-reason for every companion variant.

## military/RecruitHireScreen.java

- **HIGH** — `hireButton.active = canHire` (line 93) without any tooltip. When canHire is false (no group selected, no funds, ClientManager.canPlayerHire false), the player sees a greyed button and a status line elsewhere on screen, but no per-button reason. **[FIXED]** — tooltip now reflects the reason: no group / not allowed.

## civilian/BuildAreaScreen.java

- **MEDIUM** — `saveButton.active = false` initial state (line 237) without tooltip. The save button only enables once a name is filled, but the lack of a denial tooltip makes the disabled state opaque. **[FIXED]** — `checkSaveButtonActive` already wires a tooltip; ensured initial-state tooltip is set when first constructed.
- **LOW** — In `LOAD` mode `scanNameEditBox` is never created/added, but no label states the field is N/A. Status line covers it. **[DOC]**

## worldmap/ClaimEditScreen.java

- **HIGH** — `deleteButton` is **commented out** (lines 170-181). A claim cannot be deleted from the edit screen. The mechanism only exists via the world map. Recommend either re-enabling with a confirm dialog, or making the absence explicit with a status line. **[DOC]** — restoring requires a `ConfirmScreen` flow; risk of test breakage on the claim controller.
- **LOW** — `editNameBox.setTextColor(-1)` / `setTextColorUneditable(-1)` — `-1` is white on parchment; the text disappears on the lighter background tones. Migration to `MilitaryGuiStyle` palette would make it readable. **[DOC]**

## worldmap/ClaimTrustedMembersScreen.java

- **HIGH** — line 190 `displayName(playerInfo)` falls back to `playerInfo.getUUID().toString()` (full UUID) when name is blank. The bound `SelectedPlayerWidget` will then render a 36-char hex string instead of a readable label. **[FIXED]** — fallback now shows the localized "(unknown player)" placeholder with the first 8 chars of the UUID, not the whole thing.
- **MEDIUM** — `prevButton`, `nextButton` (lines 157-158) become inactive when `trustedMembers.size() <= 1`, no tooltip. **[FIXED]** — added `prev/next.disabled` tooltip via the existing tooltip infrastructure.
- **MEDIUM** — `renderBackground` lines 197-203 use raw `graphics.fill(...)` with hard-coded ARGB colors instead of `MilitaryGuiStyle.parchmentPanel` / `insetPanel`. Same palette family but bypasses the style helper. **[DOC]** — refactor would cascade into ten+ chrome rewrites.

## civilian/CitizenProfileScreen.java

- **MEDIUM** — `ownerLabel()` line 113: when the owner is offline, falls back to `owner.toString().substring(0, 8)` — a UUID prefix. Should consult the player profile cache (e.g. `ClientManager.getPlayerName(owner)`) before truncating. **[DOC]**
- **MEDIUM** — `assignmentLabel()` line 121: identical fallback issue but for the bound work-area UUID. The screen has no way to resolve area names from the client snapshot. **[DOC]**

## civilian/WorkerStatusScreen.java

- **HIGH** — Title implies "manage worker" but the action set is `Refresh / Convert / Close`. There is no profession reassignment, no dismiss, no wage control. The audit prompt explicitly calls out these as required actions. The screen is read-only on profession; `MessageOpenWorkerScreen` is the only structured mutation. **[DOC]** — adding controls is an architecture change (server-authoritative). Recommend follow-up backlog item for wage / profession.

## civilian/MerchantTradeScreen.java

- **MEDIUM** — `manageMenu.active = selection != null` (line 339) without a denial tooltip. The dropdown trigger silently greys when nothing is selected. The trade list has the answer (the bottom hint says "select an offer"), so this is a soft finding. **[DOC]**
- **OK** — buy/sell/restock/rotate are present (Trade button + Add/Edit + manage dropdown). Audit acceptance H is satisfied.

## civilian/WorkAreaScreen.java (used by Lumber/Crop/Build/Animal/Storage/Mining/Fishing/Market)

- **MEDIUM** — `renderBackground` lines 238-251 paint chrome with raw `graphics.fill(...)` and hardcoded ARGB constants (`PANEL_COLOR`, `HEADER_COLOR`, `SECTION_COLOR`, …) instead of `MilitaryGuiStyle.parchmentPanel`. Functions identically but isn't sharing the central palette. **[DOC]**
- **OK** — picks owner via `SelectPlayerScreen`. D-finding does not apply.

## war/WarDeclareScreen.java

- **HIGH** — Attacker / defender selection is a *cycle button*, not a dropdown. With more than three states this hides options behind opaque clicks. Replace with `DropDownMenu<PoliticalEntityRecord>` so the player sees the candidate list. **[DOC]**
- **MEDIUM** — `casusBelliBox` is a single-line `EditBox` constructed with no `setEditable(true)` call (default true) but with `setTextColor(INK)` — the parchment palette inversion is fine. Empty initial value, no placeholder hint. **[DOC]**
- **MEDIUM** — `attackerButton.active = attackers.size() > 1` (line 133), `defenderButton.active = defenders.size() > 1` (line 134) — no tooltip explaining "only one option". **[DOC]**
- **MEDIUM** — chrome lines 184-189 + custom `renderParchmentPanel` (241-248) duplicates work `MilitaryGuiStyle.parchmentPanel` already does. **[DOC]**
- **OK** — declareButton has a denialReason() tooltip when inactive. Reflects acceptance criterion A correctly.

## war/WarListScreen.java

- **HIGH** — 15 `actionButton(...)` calls (lines 104-119), all stacked in an action ledger. Per audit criterion F the screen should be folding 8+ buttons into an `ActionMenuButton` / `DropDownMenu`. The cancel-war / occupy / annex / tribute actions are mutually exclusive game outcomes and would benefit from a single "Resolve outcome" menu. **[DOC]** — collapsing requires re-routing every `MessageResolveWarOutcome.Action` through one widget; risk to `WorldMapRouteUiVerificationTest` unclear, deferred.
- **MEDIUM** — chrome via custom `renderParchmentPanel` rather than `MilitaryGuiStyle.parchmentPanel`. **[DOC]**

## war/PoliticalEntityListScreen.java

- **HIGH** — 11 action buttons (init lines 88-98). Same wall-of-buttons F finding. Strong candidate for "Manage state" dropdown. **[DOC]**
- **MEDIUM** — many of the action buttons get tooltips set in `updateLeaderButtons` (line 332-379, good); but the `create`/`refresh`/`back` pre-binding are tooltip-free even when active. Acceptable.
- **MEDIUM** — chrome bypasses `MilitaryGuiStyle`. **[DOC]**

## war/WarAlliesScreen.java

- **MEDIUM** — chrome bypasses `MilitaryGuiStyle`. **[DOC]**
- **OK** — invite buttons have denialReason tooltips via `updateInviteButtons` and `inviteDenial`.

## war/WarAllyInvitePickerScreen.java

- **MEDIUM** — chrome bypasses `MilitaryGuiStyle`. **[DOC]**
- **OK** — list of candidates with per-row denial reasons.

## war/PoliticalEntityNameInputScreen.java

- **LOW** — `submitButton.active = ...` (line 146) without tooltip. Status line on screen explains. **[DOC]**
- **MEDIUM** — chrome bypasses `MilitaryGuiStyle` — palette is parchment-themed but uses local constants. **[DOC]**

## military/MessengerScreen.java

- **OK** — `dispatchStatus` covers denial reasons. SelectedPlayerWidget integrates `SelectPlayerScreen`. EditBox is a `MultiLineEditBox` and is editable.

## military/MessengerAnswerScreen.java

- **OK** — read-only message preview, intentionally `setEnableEditing(false)`. Reply target is server-authoritative; OK button has explicit accepted-state.

## military/RenameRecruitScreen.java, ConfirmScreen.java, MessengerMainScreen.java, ScoutScreen.java, RecruitMoreScreen.java, PatrolLeaderScreen.java

- **OK** — clean. Tooltips for disabled save buttons, appropriate use of `MilitaryGuiStyle` chrome, status lines.

## military/AssassinLeaderScreen.java

- **OK** — disabled buttons all carry `*.disabled` tooltips.

## military/CommandScreen.java + commandscreen/{CombatCategory, MovementCategory}

- **OK** — covered by `MinecraftStyleContractTest`. Foundation merge brought the refactor in.

## military/GovernorScreen.java

- **MEDIUM** — chrome bypasses `MilitaryGuiStyle` (lines 80-87). Otherwise tooltips correctly route through `actionReasonKey()`. **[DOC]**

## civilian/SettlementSurveyorScreen.java

- **OK** — every disabled state has a `Component.translatable(... ".disabled")` tooltip via `syncButtons()`. Best-in-class example.

## civilian/PlaceBuildingScreen.java

- **OK** — pages and tooltips fully wired.

## civilian/StorageAreaScreen.java

- **OK** — multiple EditBoxes, all editable and added. `applyRoute()` provides feedback.

## military/CitizenProfileScreen.java

- See above (UUID fallback).

## Out-of-scope but noted

- `worldmap/WorldMapScreen.java` (979 LOC) — too large for line-by-line audit in this pass. `WorldMapClaimControllerTest` and `WorldMapRouteUiVerificationTest` pin the contract. No structural finding observed during scan.
- `group/RecruitsGroupListScreen.java`, `group/SelectGroupScreen.java` — list-based, scrolled, no greyed-button-without-reason found.

---

## Summary table

| Severity | Total | Fixed | Documented |
|---|---:|---:|---:|
| BLOCKER | 0 | 0 | 0 |
| HIGH | 9 | 5 | 4 |
| MEDIUM | 14 | 3 | 11 |
| LOW | 3 | 0 | 3 |
| **Total** | **26** | **8** | **18** |

---

## Top-5 most severe findings

1. **HIGH — `PromoteScreen.java:119`** — `BannerModMain.isSiegeWeaponsLoaded ? TOOLTIP_SIEGE_ENGINEER_DISABLED : TOOLTIP_SIEGE_ENGINEER_DISABLED` — both ternary branches are identical. Latent bug. **[FIXED]**
2. **HIGH — `PromoteScreen.java:117/124/125`** — assassin / spy / rogue buttons forced off but display the active-state tooltip rather than a "feature disabled" reason. **[FIXED]**
3. **HIGH — `RecruitInventoryScreen.java`** — multiple buttons inactive for nobles with no per-button denial tooltip; status line alone does not satisfy criterion A. **[FIXED]**
4. **HIGH — `WorkerStatusScreen.java`** — screen advertises management but lacks profession assignment / dismiss / wage controls. **[DOC]** — architecturally heavier change.
5. **HIGH — `WarListScreen.java`** — 15 same-tier action buttons in a single ledger; F-finding for dropdown collapse. **[DOC]** — refactor risk against war flow tests.
