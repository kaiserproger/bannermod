# AI Context Proxy

Use `tools/ai-context-proxy/bin/ctx` before raw repository reads.

- Use `ctx repo-map` when module location is unclear.
- Use `ctx search <pattern> [path]` instead of broad `rg` or `grep`.
- Use `ctx file <path>` before opening large files.
- Use `ctx symbol <path> <symbol>` or `ctx exact <path> <start> <end>` before editing.
- Use `ctx log -- <command...>` for noisy Gradle, Minecraft, and test output.
- Avoid raw `cat`, broad `sed`, broad `find`, and broad `rg`/`grep` context dumps unless `ctx` is insufficient and you explain why.

Optional shell setup:

```bash
source tools/ai-context-proxy/env.sh
```

## Backlog

- Use `tools/backlog` instead of reading `docs/BANNERMOD_BACKLOG.sqlite` directly.
- Use `tools/backlog batch --limit 5`, `tools/backlog show <ID>`, `tools/backlog add ... --dry-run`, `tools/backlog validate`, and `tools/backlog stage` for normal backlog work.
- Before marking a task done, self-verify every acceptance item and record the result with `tools/backlog done <ID> --verification "..."`.
- If a task changes UI, changes gameplay mechanics, or adds player-facing mechanics, update both `MULTIPLAYER_GUIDE_RU.md` and `MULTIPLAYER_GUIDE_EN.md` before marking it done.

## Minecraft UI

- For Minecraft GUI/HUD work, apply `.agents/skills/minecraft-ui-design/SKILL.md`.
- UI must be Minecraft-native, minimal, readable, localized, server-authoritative for gameplay mutations, and checked for overlap with hotbar/chat/crosshair/boss bars/existing BannerMod overlays.
