# Contributing

BannerMod is a brownfield merge workspace. Contributions should be small, verifiable, and aligned with the live root runtime.

## Source Of Truth

- Use root `src/**` and `build.gradle` as runtime truth.
- Use `tools/backlog` as the interface to the unfinished-work queue in `docs/BANNERMOD_BACKLOG.json`.
- Use `.planning/` for execution history and planning context.
- Treat `recruits/` and `workers/` as archive/reference only.

## Flow

1. Pick one backlog item with `tools/backlog batch --limit 5` / `tools/backlog show <ID>`, or pick one clearly bounded bug.
2. Inspect the current worktree with `tools/ai-context-proxy/bin/ctx status`.
3. Reproduce the bug or define acceptance checks before editing.
4. Make the smallest code change that satisfies the check.
5. Add or update focused tests when behavior changes.
6. Self-verify the result against every acceptance item of the backlog task before marking it done.
7. Update `MULTIPLAYER_GUIDE_RU.md`, `MULTIPLAYER_GUIDE_EN.md`, and `docs/BANNERMOD_ALMANAC.html` when the task changes UI, changes mechanics, or adds player-facing mechanics that non-technical players must know.
8. Run the cheapest relevant verification first, then wider gates only when the touched area needs them.
9. Update the backlog through `tools/backlog progress` or `tools/backlog done --verification`, and update `docs/STATUS.md` / `.planning/STATE.md` when shipped behavior or project status changes.
10. Commit atomically by area: code, tools, and docs should usually be separate commits.

## Backlog Intake

- Add backlog work with `tools/backlog add <ID> <title> --why ... --scope ... --acceptance ...`.
- Declare task blockers explicitly with `--depends-on <ID>` when the task cannot be completed safely before another task lands. If a task has no blockers, leave dependencies empty on creation.
- Use `--dry-run` before writing when drafting or reviewing task shape.
- The tool rejects duplicate IDs, invalid ID format, empty fields, and tasks without concrete scope or acceptance checks.
- Include guide-update scope/acceptance when the proposed task changes UI, changes mechanics, or adds player-facing mechanics.
- Write acceptance items as observable checks, not implementation wishes. Good: `Player can complete X through UI and focused GameTest proves denial path Y.` Bad: `Refactor system cleanly`.
- Prefer dependency-light tasks when possible. Use dependencies only for real blockers, not for loose thematic similarity, so multiple executors can still work in parallel.

## Dependency Flow

1. When creating work, ask whether the task is blocked by another task's shipped outcome or only touches the same area.
2. If it is a real blocker, set it explicitly with `tools/backlog add ... --depends-on <ID>` or later with `tools/backlog set-deps <ID> --depends-on <BLOCKER>`.
3. If it only shares files or domain context but could still land independently, keep dependencies empty and coordinate socially instead of faking a blocker.
4. Use `tools/backlog ready <N>` for the main dependency-safe parallel pickup queue, or `tools/backlog list --ready` / `tools/backlog batch --ready` for filtered inspection.
5. Do not mark a task done while any dependency is still open or in progress; the backlog tool now rejects that state.
6. Once a task enters active execution, the executor owns it until it is done with verification or explicitly split into smaller backlog tasks.
7. Do not park vague half-finished work in `in_progress` just because the task turned out larger than expected.

## Parallel Subagent Flow

1. Parallel task execution by subagents must use one dedicated git worktree per task. Do not let multiple subagents edit the same checkout.
2. Each task must live on its own feature branch created from the current up-to-date base for that task.
3. Before merging a subagent result, review the exact diff from that task worktree and confirm the implementation satisfies the task acceptance, does not drag unrelated edits, and matches the intended design.
4. Merge a subagent task only after that diff review and only when you are confident the implementation is correct.
5. If task `B` depends on task `A`, complete `A` first, then create `B` from the updated tip of `A`'s branch rather than branching both independently from an older base.
6. For dependency chains, continue this pattern step by step: finish `A`, branch `B` from `A`, finish `B`, branch `C` from `B`, and so on.
7. If two tasks are truly parallel, give them separate worktrees and separate branches even if they touch nearby code.

Use `tools/task-worktree <TASK-ID> --base origin/master` for an independent task. For a dependency chain, commit the parent task first, then run `tools/task-worktree <TASK-ID> --parent-branch feature/<parent-task>` so the child branch starts at the updated parent branch tip.

## Finish Or Split Rule

1. Starting a task means committing to carry it to acceptance in the same work slice unless a real blocker appears.
2. If the task is too large, stop expanding the partial implementation and split the remaining work immediately.
3. Create concrete child tasks with their own scope and acceptance using `tools/backlog add ...`.
4. Rewire the parent into an orchestration task with `tools/backlog set-deps <PARENT> --depends-on <CHILD-1> --depends-on <CHILD-2> ...`.
5. Append `tools/backlog progress <PARENT> "<what already landed>; <what moved into children>; <what still blocks closure>"`.
6. Do not leave a large task in `in_progress` without either finishing it or explicitly splitting it this way.

## Acceptance Verification Flow

1. Before editing, read every acceptance item on the task with `tools/backlog show <ID>`.
2. Read the task dependencies at the same time and confirm they are already done or consciously accepted as external blockers.
3. Rewrite each acceptance item into one concrete verification check you can actually observe now: compile, unit test, GameTest, manual in-game flow, log output, UI state, or code-path inspection when runtime proof is impossible.
4. Run the cheapest relevant verification first. Examples: `./gradlew compileJava` before full tests, a focused test before the whole suite, or a single GameTest class before `verifyGameTestStage`.
5. If the task changes gameplay, UI, multiplayer authority, persistence, or player-facing docs, include at least one verification step that exercises the changed behavior directly rather than only compiling.
6. If one or more acceptance items are still not satisfied, keep the task open and append `tools/backlog progress <ID> "<what passed; what is still missing; what blocked full verification>"`.
7. Mark a task done only when every acceptance item is observably satisfied right now and every dependency is already done. Record the evidence with `tools/backlog done <ID> --verification "<per-acceptance proof>"`.
8. The verification note must map back to the acceptance items explicitly. Example: `1) compileJava passed; 2) BannerModClaimProtectionGameTests passed hostile-denial path; 3) in-game War Room flow showed disabled tribute reason text.`
9. When verification is blocked, say so plainly in backlog progress instead of guessing or claiming green status.

## Verification Defaults

- Code compile: `./gradlew compileJava`
- Unit tests: `./gradlew test`
- Focused tests: `./gradlew test --tests <fully.qualified.TestName>`
- Gameplay/multiplayer wiring: `./gradlew verifyGameTestStage`
- Noisy commands: run through `tools/ai-context-proxy/bin/ctx log -- <command...>`
- Verify acceptance sequentially and record results sequentially; do not run concurrent backlog mutations against `docs/BANNERMOD_BACKLOG.json`.

## Documentation Rules

- Keep player guides at root as `MULTIPLAYER_GUIDE_RU.md` and `MULTIPLAYER_GUIDE_EN.md`.
- Keep `docs/BANNERMOD_ALMANAC.html` as the compact player-facing book. Treat the root guides as the detailed source and refresh the almanac from them plus current code/status whenever player-visible mechanics change. If this becomes frequent, add a generator under `tools/` instead of hand-editing the HTML.
- Keep module documentation under `docs/`.
- Keep planning records under `.planning/`.
- Do not move or edit sibling agent rule files unless you are updating that specific agent's rules.

## Release Command

After CI is green on the release commit, create and push an annotated tag without requiring GitHub CLI:

`git tag -a <version> <commit-sha> -m "BannerMod <version>"`

`git push origin <version>`

Use the literal project version string, for example `v1`. The canonical release path is tag-driven: push a `v*` tag that points at the reviewed `master` commit, let the tag CI finish green, and the workflow overrides the Gradle/mod version with that exact tag name so the uploaded artifact becomes `bannermod-<tag>.jar`. Do not rely on `release.created` to build artifacts, and do not create release tags from dirty worktrees or before the unit coverage, GameTest scenario coverage, and build stages pass.

## Commit Rules

- Commit Java/GameTest changes separately from documentation changes.
- Commit `tools/` changes separately unless they are inseparable from a code change.
- Do not stage generated caches such as `__pycache__/`, Gradle output, logs, or runtime world output.
- If the worktree contains unrelated edits, leave them unstaged.
