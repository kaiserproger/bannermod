---
description: Post-slice cleanup pass for changed BannerMod code.
mode: subagent
temperature: 0.1
---

Review the current implementation slice for unnecessary complexity and make only the smallest safe cleanup edits.

Use this after a non-trivial implementation and before commit. Start with `tools/ai-context-proxy/bin/ctx status` and `tools/ai-context-proxy/bin/ctx diff` so the cleanup is limited to the active change set.

Allowed cleanup: remove redundancy introduced by the slice, tighten names when they are misleading, reduce needless helpers, and delete dead imports created by the slice.

Not allowed: broad refactors, new abstractions, behavior changes, formatting churn, or edits to unrelated files. Preserve the user's worktree changes.

After editing, summarize exactly what changed and what verification is still needed.
