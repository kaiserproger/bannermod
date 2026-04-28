# Tools

## ai-context-proxy Multitool

`tools/ai-context-proxy/` is a local context multitool for coding agents. It gives compact repository maps, file summaries, exact ranges, symbol reads, compressed searches, compact git status/diff output, and short Gradle/Minecraft/test log summaries.

Use it when a normal command would dump too much context:

```bash
tools/ai-context-proxy/bin/ctx repo-map
tools/ai-context-proxy/bin/ctx file src/main/java/com/talhanation/bannermod/bootstrap/BannerModMain.java
tools/ai-context-proxy/bin/ctx search "CombatStance" src/main/java
tools/ai-context-proxy/bin/ctx log -- ./gradlew compileJava
```

The tool is a guardrail, not a compiler or sandbox. Before editing code, read the exact symbol or exact line range you intend to change.

Full tool notes live in `../tools/ai-context-proxy/README.md`.

## Backlog Tool

`tools/backlog` is the bounded interface to the canonical backlog at `docs/BANNERMOD_BACKLOG.json`. Use it instead of reading the full JSON during normal agent work.

```bash
tools/backlog batch --limit 5
tools/backlog batch --ready --limit 5
tools/backlog ready 5
tools/backlog show WAR-007
tools/backlog list --status open
tools/backlog add UI-008 "Readable title" --why "Why this matters" --scope "Concrete deliverable" --acceptance "Observable success check" --depends-on WAR-007 --dry-run
tools/backlog set-deps UI-008 --depends-on WAR-007
tools/backlog validate
```

`tools/backlog add` validates new tasks before writing: ID format, duplicate IDs, non-empty `why`, at least one `scope`, at least one `acceptance` item, and the schema-required `dependencies` field. Use `--dry-run` to preview a task without mutating the backlog.

Safety limits are deliberate: the tool refuses backlog files over 5 MB, caps `batch --limit` at 50 tasks, and writes through an atomic temp-file replace. This keeps normal use from producing huge outputs or partial JSON writes.

Mutate the backlog sequentially, not in parallel. Each individual write is atomic, but concurrent `tools/backlog add/progress/done` calls can race each other and leave one writer operating on stale file contents.

Backlog tasks that change UI, change mechanics, or add player-facing mechanics should include guide-update work for both `MULTIPLAYER_GUIDE_RU.md` and `MULTIPLAYER_GUIDE_EN.md`.

Use dependencies only for real blockers. If a task truly cannot complete before another lands, record that relation with `--depends-on` or `set-deps`. If there is no blocker, keep `dependencies: []` so the task stays available for parallel execution.

`tools/backlog ready <N>` returns the first `N` open tasks whose dependencies are already done, which is the main dependency-safe queue for parallel pickup. `tools/backlog list --ready` and `tools/backlog batch --ready` remain useful for filtered inspection.

Once a task is actively being executed, the executor should either carry it to done with verification or split the remaining scope into child tasks and wire the parent dependencies to those children. Do not leave oversized half-finished work sitting in `in_progress` without an explicit split.

When parallelizing execution with subagents, use one dedicated git worktree and one dedicated feature branch per task. Review the exact diff from each task worktree before merge. For dependency chains, branch the next task from the updated tip of the previous task branch rather than from a stale common base.

Use `tools/task-worktree` as the one-command bootstrap flow:

```bash
# Independent task from the current base branch tip.
tools/task-worktree WAR-007 --base origin/master

# Dependency-chain task after WAR-007 has been completed and committed.
tools/task-worktree UI-008 --parent-branch feature/war-007
```

The helper creates `feature/<lowercase-task-id>` under `/home/user/bannermod-task-worktrees/<TASK-ID>` by default. Use `--dry-run` to verify the resolved branch, worktree path, and base before creating anything.

### Acceptance Verification Loop

Use this loop for every backlog task:

1. `tools/backlog show <ID>` and copy each acceptance item and dependency into concrete checks and blockers.
2. Run the cheapest relevant proof first: compile, focused unit test, focused GameTest, or a direct manual scenario.
3. If any acceptance item is still unmet, or if a dependency is still open, append `tools/backlog progress <ID> "<passed checks>; <missing acceptance>; <blockers>"`.
4. Only when every acceptance item is observably satisfied and all dependencies are done, close with `tools/backlog done <ID> --verification "1) ... 2) ... 3) ..."`.
5. The `--verification` note should map back to the acceptance items, not just say `tests passed`.

## Agent Guardrails

Shared local guardrail scripts live in `tools/agent-hooks/`.

- Claude Code uses `.claude/settings.local.json`, which runs `tools/ai-context-proxy/hooks/claude-pre-bash.py`; that wrapper delegates to `tools/agent-hooks/pre-bash-guardrails.py`.
- Codex supports project hooks through `.codex/config.toml` with `[features].codex_hooks = true`; this repo wires `PreToolUse` for Bash to the shared guardrail script.
- OpenCode supports project plugins under `.opencode/plugins/`; this repo uses `.opencode/plugins/project-guardrails.js` to block direct backlog JSON access and raw context dumps before Bash execution.
- Cursor, Windsurf, Gemini, and Copilot rule files currently provide instruction-level guardrails only; no repo-local executable hook format is configured here for them.

Repo-local execution commands:

- OpenCode: `/backlog-execute [N]` via `.opencode/commands/backlog-execute.md`
- Claude Code: `/backlog-execute [N]` via `.claude/commands/backlog-execute.md`

Both commands default to `N=5`, pull work from `tools/backlog ready N`, and must follow the dependency, worktree-per-task, feature-branch-per-task, diff-review, and finish-or-split rules.

## UI Design Skill

`minecraft-ui-design` is a repo-local open-agent skill for Minecraft GUI/HUD work:

- Canonical skill: `.agents/skills/minecraft-ui-design/SKILL.md` for Codex and OpenCode-compatible agents.
- Claude adapter: `.claude/commands/minecraft-ui-design.md`, which points Claude Code at the same design contract.

Use it for War Room, political screens, settlement screens, storage/build-area screens, command UI, world-map panels, and HUD overlays. The skill enforces Minecraft-native visual language, non-overlap constraints, server-authoritative UX, localization, and multiplayer-guide updates when player-facing mechanics change.

When finishing backlog work, self-verify every acceptance item and record the result:

```bash
tools/backlog done WAR-007 --verification "compileJava passed; UI declaration flow verified against all acceptance items"
```
