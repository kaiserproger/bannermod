You are an AI coding agent working in the BannerMod repo. The same rule-set
applies to opencode and Claude Code (`CLAUDE.md` is a symlink to this file);
agent-specific quirks are noted inline.

Behavior rules:

1. Think before coding
- Do not assume silently.
- If there are multiple plausible interpretations, state them briefly and choose only if one is strongly supported by the code/context.
- If the task is ambiguous, say exactly what is unclear.
- If a simpler solution exists, prefer it and say why.

2. Simplicity first
- Implement the minimum change that solves the requested problem.
- Do not add flexibility, abstraction, configuration, or extensibility unless explicitly needed.
- Do not add speculative error handling for impossible or unsupported scenarios.
- Prefer boring, local code over framework-like design.

3. Surgical changes only
- Touch only files and lines required for the task.
- Do not refactor adjacent code unless it is required to complete the task.
- Do not rewrite comments, formatting, naming, or structure without need.
- Remove only the dead code/imports created by your own changes.

4. Goal-driven execution
- Convert the task into verifiable success criteria before coding.
- For bugs: reproduce first, then fix, then verify.
- For features: define acceptance checks, then implement, then verify.
- For refactors: preserve behavior and prove it with tests or equivalent checks.

5. Context-proxy discipline
- Use `tools/ai-context-proxy/bin/ctx` for repository inspection by default.
- Use `ctx repo-map` when module location is unclear.
- Use `ctx search <pattern> [path]` instead of broad `rg`/`grep`.
- Use `ctx file <path>` before opening large files; it returns summaries for files over 150 lines.
- Use `ctx symbol <path> <symbol>` or `ctx exact <path> <start> <end>` before editing.
- Use `ctx log -- <command...>` for noisy Gradle/Minecraft/test commands.
- Use `ctx status` instead of raw `git status` and `ctx diff` instead of broad raw diffs.
- Do not use built-in `read`, `glob`, or `grep` for repository inspection unless `ctx` fails or cannot express the query.
- Do not use raw `cat`, broad `sed`, broad `find`, broad `rg`/`grep`, or raw `git status`/`git diff` to dump context unless `ctx` is insufficient and you state why.
- For successful commands, report only the `ctx log` summary; for failures, report only the failed task, first relevant error, project stack frames, and last meaningful lines.
- Optional shell setup: `export PATH="$PWD/tools/ai-context-proxy/wrappers:$PWD/tools/ai-context-proxy/bin:$PATH"`.

6. Output format
Before coding, provide:
- Goal
- Assumptions
- Minimal plan
- Verification steps

After coding, provide:
- What changed
- Why those changes are sufficient
- What was intentionally not changed
- Verification results

7. Multi-agent rule isolation
- This repo carries multiple agent rule files: `AGENTS.md` (opencode + Claude Code), `CLAUDE.md` (symlink to `AGENTS.md`), `GEMINI.md` (Gemini), `.cursor/` (Cursor), `.windsurf/` (Windsurf), `.github/copilot-instructions.md` (Copilot).
- `AGENTS.md` and `CLAUDE.md` share one source of truth — edit `AGENTS.md`.
- The other files apply only to their own agent. When adding or changing a rule for one of them, edit only the file matching that agent — do not push its rules back into `AGENTS.md`.

8. Worktree freshness
- Before treating a task as starting from a clean baseline, run `ctx status`.
- Prior agent sessions routinely leave uncommitted modifications and untracked new packages that intersect the files about to be touched.
- "Clean checkout" is not the default state.

9. Commit hygiene
- Always work on a feature branch, never directly on `master`. Create the branch (e.g. `feature/<area>-<short-slug>`) before the first commit of a new task. Push only when the user asks for it.
- Default to atomic commits grouped by area / package.
- When the worktree is tangled with prior-session work, path-based grouping (one commit per directory or theme) is acceptable instead of fine-grained per-feature splits.
- Do not stage Markdown (`*.md`, `*.mdc`) unless explicitly asked. If Markdown is requested in a commit, only `AGENTS.md` and `CLAUDE.md` are allowed by default; do not commit planning docs or other guide files unless the user explicitly names them.
- For multi-line commit messages: write the message to `/tmp/commit_msg.txt` via the file-writing tool, then `git commit -F /tmp/commit_msg.txt`. The `ctx` pre-bash hook blocks heredoc patterns (`<<'EOF'`).

10. Backlog hygiene
- `docs/BANNERMOD_BACKLOG.sqlite` is the single canonical backlog. `docs/BANNERMOD_BACKLOG.md` is only a pointer and must not contain task data.
- Use `tools/backlog` for normal backlog work instead of reading or dumping the SQLite DB directly: `tools/backlog batch --limit 5`, `tools/backlog show <ID>`, `tools/backlog list --status open`, `tools/backlog validate`.
- Use `tools/backlog stage` to stage canonical backlog DB changes; do not run raw `git add docs/BANNERMOD_BACKLOG.sqlite`.
- When adding a task, use `tools/backlog add <ID> <title> --why ... --scope ... --acceptance ...`. Every task must include `id`, `title`, `why`, concrete scope deliverables, and verifiable acceptance checks.
- **DONE = every acceptance item is observably satisfied right now.** Closing a task is a binary check against the existing acceptance list, not a judgement call. If any acceptance item describes gameplay-observable behaviour you cannot demonstrate from current code, the task stays open even when supporting infrastructure landed.
- Before marking a task done, verify your own implementation against every acceptance item. Record the exact verification result with `tools/backlog done <ID> --verification "..."`; if verification cannot be run, record what blocked it instead of pretending it passed.
- If a backlog task changes UI, changes gameplay mechanics, or adds player-facing mechanics that a non-technical player must know, update `MULTIPLAYER_GUIDE_RU.md` and `MULTIPLAYER_GUIDE_EN.md` in the same slice before marking the task done.
- Also update `docs/BANNERMOD_ALMANAC.html` for player-facing UI/mechanic changes. It is the compact medieval-book player almanac derived from the root guides plus current code/status; if repeated manual updates become noisy, add a `tools/` generator rather than letting it drift.
- For partial work, use `tools/backlog progress <ID> "..."` and explicitly call out which acceptance items are not yet met. Append progress; do not rewrite history.
- Before claiming a closure in chat, run `tools/backlog validate` and `tools/backlog show <ID>` and ensure the task status and verification match what you are about to say.

11. Contribution flow
- Read `docs/STATUS.md` before picking up brownfield work.
- Use `docs/CONTRIBUTING.md` as the contribution flow for code, tests, docs, and commits.
- Use `tools/backlog` to inspect and update the canonical active backlog (`docs/BANNERMOD_BACKLOG.sqlite`).
- Put module documentation under `docs/`; keep root player guides split as `MULTIPLAYER_GUIDE_RU.md` and `MULTIPLAYER_GUIDE_EN.md`.
- The local context multitool is documented in `tools/ai-context-proxy/README.md` and summarized in `docs/TOOLS.md`.
- If the user invokes `/backlog-execute [N]`, treat it as an execution command, not a planning request: default `N=5`, run `tools/backlog ready N`, pick that batch as the active queue, and begin execution under the backlog dependency, worktree, feature-branch, and finish-or-split rules.
- For `/backlog-execute [N]` with parallel subagents, create one dedicated worktree and one dedicated feature branch per task before any edits. For dependency chains, complete the first task and branch the dependent task from the updated tip of the first task branch.

Documentation index for agents:
- `docs/README.md` - top-level documentation index.
- `docs/STATUS.md` - current developer status, known open areas, and live system references.
- `docs/CONTRIBUTING.md` - contribution, verification, and review flow.
- `docs/DEVELOPMENT.md` - codebase map, hot spots, and validation shortcuts.
- `docs/TOOLS.md` - local tools, context proxy, backlog helper, and repo skills.
- `docs/BANNERMOD_BACKLOG.sqlite` - canonical backlog; use `tools/backlog`, not raw SQLite reads/writes.
- `docs/BANNERMOD_TECHNICAL_DESIGN.md` - architecture, state model, authority model, invariants, and target end state.
- `docs/STRATEGIC_ECONOMY_INTEGRATION.md` - VenaTerra-backed strategic economy, mine-site, yield, backlog, and verification notes.
- `docs/AI_MINECRAFT_UI_STYLE_GUIDE.md` - required style rules for Minecraft-native UI/HUD work.
- `MULTIPLAYER_GUIDE_RU.md` / `MULTIPLAYER_GUIDE_EN.md` - player-facing guides; update them for shipped player-visible mechanics.
- `docs/BANNERMOD_ALMANAC.html` - compact player almanac; update it with player-facing UI/mechanic changes.

12. Minecraft UI design
- For Minecraft GUI/HUD work, load and apply the repo skill `minecraft-ui-design` from `.agents/skills/minecraft-ui-design/SKILL.md`.
- UI must be Minecraft-native, minimal, readable, server-authoritative for gameplay mutations, localized, and checked for overlap with hotbar/chat/crosshair/boss bars/existing BannerMod overlays.
- AI agents changing BannerMod GUI, HUD, overlays, or placement holograms must also follow `docs/AI_MINECRAFT_UI_STYLE_GUIDE.md`.
- Do not spam the player with many always-visible action buttons. When one UI needs several secondary actions, prefer a compact context menu or similar progressive disclosure instead of exposing every action at once.

13. Army command pipeline
All server-side movement / face / attack / aggro / stance / strategic-fire commands for recruits MUST flow through the unified pipeline. Bypassing it (e.g. calling `recruit.setMovePos(...)` directly from a packet handler) breaks queueing, priority, and the `CommandIntentLog` audit trail.

- **Entry point:** build a `CommandIntent` (record types in `army/command/CommandIntent.java`) and call `CommandIntentDispatcher.dispatch(player, intent, actors)`. The dispatcher handles selection narrowing, queue vs immediate, and routing to `CommandEvents`.
- **`CommandIntent.Movement` signature:** `(long issuedAtGameTime, int priority, boolean queueMode, int movementState, int formation, boolean tight, @Nullable Vec3 targetPos)`. `priority` is an `int` from `CommandIntentPriority` constants (`LOW=1`, `NORMAL=3`, `HIGH=5`, `IMMEDIATE=10`) — not an enum.
- **`movementState` semantics** (see `MovementFormationCommandService.onMovementCommand`): `0` hold, `1` follow, `2` regroup, `3` wander, `4` come-to-me, `5` patrol, `6` move-to-pos, `7`/`8` formation forward/back. Formation pipeline only triggers when `formation != 0 && movementState ∈ {2, 4, 6, 7, 8}`; otherwise falls through to per-recruit move.
- **Formation is server-authoritative.** Player's saved formation lives in `Player.PERSISTED_NBT_TAG → "Formation"`. Read it on the server with `CommandEvents.getSavedFormation(player)` — do not pass formation indices from the client unless the UI is explicitly picking one. Hardcoding a non-zero formation in a packet silently rebinds the group.
- **Explicit target positions:** when the move target arrives via network (world-map click, etc.) instead of `player.pick(...)`, use the 6-arg overload `CommandEvents.onMovementCommand(player, recruits, state, formation, tight, Vec3)` — the underlying `MovementFormationCommandService` short-circuits the hit-result lookup when `explicitTargetPos != null`.
- **Verifying a wiring change:** `./gradlew compileJava` via `ctx log` is the cheap gate. For runtime verification of formation behavior, save a formation in the command screen, then exercise the command path; `formation == 0` means the player never opened the formation UI and the per-recruit fallback is the correct path.

14. Agent plugins/tools
- `code-simplifier` — mandatory post-slice clean-up pass before final verification/closure. Apply only justified, behavior-preserving cleanup.
- `code-reviewer` — mandatory independent review after `code-simplifier` and before marking work done or committing. Resolve or explicitly document every finding.
- `context7` — live documentation lookup for libraries, frameworks, SDKs, CLI tools (NeoForge, Mojang, Gradle plugins, JUnit, etc.). Use whenever a task touches third-party APIs instead of relying on training-data recollection.
- `jd-tls` / `jdtls` — Java decompilation, class inspection, LSP diagnostics, and compiled API tracing for NeoForge and vendored jars. Use when tracing through bytecode is faster than spelunking sources.

Prefer these tools over ad-hoc shell commands or local scripts when the task fits. The normal finish order is implementation, `code-simplifier`, verification, `code-reviewer`, fixes if any, and final verification/closure.

## Project

**BannerMod Merge Workspace**

This workspace is the realized merged runtime of the Forge mods historically living in `recruits/` and `workers/`. The active root build, runtime, and planning context are already unified under `bannermod`; ongoing work is stabilization, architecture cleanup, and gameplay repair without losing historical context.

**Current merge stance:** active code lives under root `src/**`. `recruits/` and `workers/` remain on disk as archive/reference trees only unless a root doc explicitly points to them.

## Workflow

- Use `.planning/` as the active planning context.
- Prefer the real code over legacy plans when they disagree, and record material conflicts in `.planning/STATE.md` or `docs/STATUS.md`.
