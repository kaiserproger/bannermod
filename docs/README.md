# BannerMod Docs

This directory contains active module documentation. Root-level Markdown is intentionally limited to repository entry points and agent rules.

## Active Docs

- `../README.md` - repository overview and build entry point.
- `../MULTIPLAYER_GUIDE_RU.md` - Russian player/server guide.
- `../MULTIPLAYER_GUIDE_EN.md` - English player/server guide.
- `STATUS.md` - current developer-facing project status.
- `CONTRIBUTING.md` - contribution flow for developers and agents.
- `DEVELOPMENT.md` - codebase map, hot spots, and validation shortcuts.
- `BANNERMOD_BACKLOG.sqlite` - canonical unfinished-work queue; use `../tools/backlog` to inspect or update it.
- `BANNERMOD_BACKLOG.md` - short pointer to the SQLite backlog and tool commands.
- `TOOLS.md` - local developer tools, including the context proxy multitool.
- Agent guardrail hooks/plugins are documented in `TOOLS.md` and live under `tools/agent-hooks/`, `.codex/`, and `.opencode/`.

## Planning History

`.planning/` at repository root remains the active planning database, not player documentation. Removed merge-era scratch files should be recovered from git history only if they are needed for archaeology.

## Cleanup Rule

If a new documentation file is not a root entry point, agent rule file, or active `.planning/` artifact, put it under `docs/`.
