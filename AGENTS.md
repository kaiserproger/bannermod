You are working inside opencode with GPT 5.4.

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
- This repo carries multiple agent rule files: `AGENTS.md` (opencode), `CLAUDE.md` (Claude Code), `GEMINI.md` (Gemini), `.cursorrules` + `.cursor/` (Cursor), `.windsurf/` (Windsurf), `.github/copilot-instructions.md` (Copilot).
- Each file applies only to its own agent.
- When adding or changing a rule, edit only the file matching the agent currently running — do not push opencode-specific rules into a sibling file.

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
- `docs/BANNERMOD_BACKLOG.md` is the single canonical backlog. When you add a new task, write the section in full: `## <ID> — <Title>`, **Зачем**, **Scope** (concrete deliverables), **Acceptance** (verifiable success criteria). Skipping any of these makes the task invisible to future sessions.
- When a task is finished, mark it closed by inserting a `**Status: DONE <YYYY-MM-DD>.**` line at the top of its section (right under the heading) and keeping the existing scope/acceptance/progress paragraphs in place as the historical record. Never silently delete a closed task.
- Open tasks have no `Status:` line; in-progress slices use a `**Progress <YYYY-MM-DD>.**` paragraph at the bottom of the section.

11. Contribution flow
- Read `docs/STATUS.md` before picking up brownfield work.
- Use `docs/CONTRIBUTING.md` as the contribution flow for code, tests, docs, and commits.
- Use `docs/BANNERMOD_BACKLOG.md` as the canonical active backlog.
- Put module documentation under `docs/`; keep root player guides split as `MULTIPLAYER_GUIDE_RU.md` and `MULTIPLAYER_GUIDE_EN.md`.
- The local context multitool is documented in `tools/ai-context-proxy/README.md` and summarized in `docs/TOOLS.md`.

<!-- GSD:project-start source:.planning/PROJECT.md -->
## Project

**BannerMod Merge Workspace**

This workspace is the realized merged runtime of the Forge mods historically living in `recruits/` and `workers/`. The active root build, runtime, and planning context are already unified under `bannermod`; ongoing work is stabilization, architecture cleanup, and gameplay repair without losing historical context.

**Current merge stance:** active code lives under root `src/**`. `recruits/` and `workers/` remain on disk as archive/reference trees only unless a root doc explicitly points to them.
<!-- GSD:project-end -->

<!-- GSD:workflow-start -->
## Workflow

- Use `.planning/` as the active planning context.
- Prefer the real code over legacy plans when they disagree, and record material conflicts in `.planning/STATE.md` or `docs/STATUS.md`.
<!-- GSD:workflow-end -->
