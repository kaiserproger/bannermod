# BannerMod Backlog

The canonical backlog is now `docs/BANNERMOD_BACKLOG.sqlite`.

Do not read or dump the SQLite database directly during normal agent work. Use the backlog tool instead:

```bash
tools/backlog batch --limit 5
tools/backlog show WAR-007
tools/backlog list --status open
tools/backlog add UI-008 "Readable title" --why "Why this matters" --scope "Concrete deliverable" --acceptance "Observable success check" --dry-run
tools/backlog validate
tools/backlog stage
```

## Rules

- `docs/BANNERMOD_BACKLOG.sqlite` is the single source of truth for unfinished backlog work.
- This Markdown file is only a human-facing pointer and must not carry task data.
- New tasks must include `id`, `title`, `why`, concrete `scope`, and verifiable `acceptance` checks.
- Use `tools/backlog add ... --dry-run` first when drafting a task; the tool validates ID format, duplicate IDs, and required non-empty fields before writing.
- `DONE` means every acceptance item is observably satisfied in the current codebase.
- Before marking a task done, verify the implementation against the task acceptance checks and record the verification result in the task.
- If a task changes UI, changes gameplay mechanics, or adds player-facing mechanics, update both `MULTIPLAYER_GUIDE_RU.md` and `MULTIPLAYER_GUIDE_EN.md` before marking it done.
- Add progress entries instead of rewriting old progress history.

## Tool

`tools/backlog` is the mini-Jira wrapper for this repository. It returns bounded task batches so agents do not need to load the entire backlog into context. The Python implementation lives in `tools/backlog.py`.

The tool validates new tasks before writing, caps batch output, stores task records in SQLite, and exposes `tools/backlog stage` as the allowed way to stage backlog DB changes.
