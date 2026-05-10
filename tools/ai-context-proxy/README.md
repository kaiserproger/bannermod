# ai-context-proxy

Local context middleware for coding agents. It reduces token waste by returning compact structural views before raw file contents, compressing broad searches, and shortening noisy command logs.

This is intentionally an MVP: no daemon, no MCP server, no sandbox, no external dependencies.

Java parsing is heuristic. It is good enough for navigation and context routing, not a replacement for compiler/LSP truth.

## When To Use

Use this when an agent is likely to waste context by reading large files, dumping broad `rg` results, or pasting long Gradle/Minecraft logs.

Do not use summaries as the final source of truth before editing. Before patching code, read the exact symbol or exact line range.

## Setup

From the repository root:

```bash
export PATH="$PWD/tools/ai-context-proxy/bin:$PATH"
```

Optional wrappers for common shell habits:

```bash
export PATH="$PWD/tools/ai-context-proxy/wrappers:$PWD/tools/ai-context-proxy/bin:$PATH"
```

Equivalent source hook:

```bash
source tools/ai-context-proxy/env.sh
```

The wrappers redirect simple `cat`, `rg`, `grep`, `sed`, `find`, and broad `git status`/`git diff` calls into compact context-safe behavior. Complex command forms fall back to the system tool.

## Commands

```bash
ctx repo-map
ctx file <path>
ctx exact <path> <start> <end>
ctx symbols <path>
ctx symbol <path> <symbol-name>
ctx search <pattern> [path]
ctx log -- <command...>
ctx status
ctx diff
```

## Behavior

`ctx file <path>` prints raw numbered contents for files up to 150 lines. Larger files return a summary. Use `ctx file --raw <path>` only when the full file is needed.

For Java files, summaries include:

- package
- imports summary
- class/interface/enum/record declarations
- fields
- methods with line ranges
- rough call hints
- TODO/FIXME/HACK/XXX lines

`ctx exact <path> <start> <end>` prints an exact numbered range. Ranges over 120 lines require `--force`.

`ctx symbol <path> <symbol>` prints exact code for a Java method or type. Symbols over 160 lines require `--force`. Use this before editing.

`ctx search <pattern>` wraps `rg`. Up to 40 matches are printed normally. Larger result sets are grouped by file with one sample per file. Use `--full` only when every match is needed.

`ctx log -- <command...>` runs a command and prints an ultra-brief summary by default. Successful commands show status, duration, Gradle summary, and collapsed warnings. Failed commands add failed task, first relevant error, project stack frames, and the last meaningful lines. Use `--verbose` for older first/last-lines compression or `--raw` for passthrough output.

`ctx status` prints compact git status counts and top changed areas. Use `--full` for the full porcelain list.

`ctx diff` prints compact diff stats and top changed areas. Use `--full` for the full patch or `--staged` for staged changes.

## Recommended Agent Flow

1. Run `ctx repo-map` if module location is unclear.
2. Run `ctx search <term>` to identify likely files.
3. Run `ctx file <path>` to inspect a compact file digest.
4. Run `ctx symbol <path> <name>` or `ctx exact <path> <start> <end>` before editing.
5. Run `ctx log -- <test command>` for noisy verification commands.

## Wrapper Notes

The wrappers are a soft guardrail, not a sandbox. An agent can still bypass them with absolute paths or other interpreters if it has direct repository access.

For hard enforcement, run the agent in an environment where repository reads go only through this tool or a future MCP/server implementation.

## Agent Entry Points

This repository wires the tool into common agent entrypoints:

- `AGENTS.md` tells opencode/Codex-style agents to use `ctx` first.
- `CLAUDE.md` tells Claude Code to use `ctx` first.
- `.cursor/rules/ai-context-proxy.mdc` applies the same policy in Cursor.
- `.github/copilot-instructions.md` covers GitHub Copilot Chat.
- `GEMINI.md` covers Gemini-style agents.
- `.windsurf/rules/ai-context-proxy.md` covers Windsurf-style agents.
- `.claude/settings.local.json` registers a Claude Code `PreToolUse` hook for Bash.

The Claude hook blocks common raw context dumps through `cat`, `sed`, `find`, `rg`, and `grep`, and points the agent at the equivalent `ctx` command. It is intentionally a nudge, not a security boundary.

Backlog access is intentionally routed through `tools/backlog` rather than direct `docs/BANNERMOD_BACKLOG.sqlite` reads. The same Bash hook blocks direct backlog access and points agents at bounded commands such as `tools/backlog batch --limit 5` and `tools/backlog stage`.

Additional agent guardrails live under `tools/agent-hooks/`, `.codex/config.toml`, and `.opencode/plugins/`. They reuse the same policy where supported by the host agent: bounded backlog access and context-proxy-first repository inspection.

## Cache

Java summaries are cached under `.analysis/ai-context-proxy/` by path, size, and mtime. Delete that directory if needed.
