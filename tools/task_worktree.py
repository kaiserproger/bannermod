#!/usr/bin/env python3
"""Create a dedicated backlog-task git worktree and feature branch."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WORKTREE_ROOT = Path("/home/user/bannermod-task-worktrees")
TASK_ID_RE = re.compile(r"^[A-Z][A-Z0-9]+-[0-9]+[A-Z0-9-]*$")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bootstrap one dedicated worktree and feature branch for a backlog task."
    )
    parser.add_argument("task_id", help="Backlog task id, e.g. PROC-001")
    parser.add_argument(
        "--branch",
        help="Feature branch to create; defaults to feature/<lowercase-task-id>",
    )
    parser.add_argument(
        "--path",
        type=Path,
        help="Worktree path; defaults to /home/user/bannermod-task-worktrees/<TASK-ID>",
    )
    parser.add_argument(
        "--worktree-root",
        type=Path,
        default=DEFAULT_WORKTREE_ROOT,
        help="Parent directory for the default worktree path",
    )
    base_group = parser.add_mutually_exclusive_group()
    base_group.add_argument(
        "--base",
        help="Base ref for an independent task; defaults to origin/master when available, otherwise master",
    )
    base_group.add_argument(
        "--parent-branch",
        help="Completed dependency branch whose current tip should become this task's base",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the resolved flow without creating the branch or worktree",
    )
    args = parser.parse_args()

    if not TASK_ID_RE.fullmatch(args.task_id):
        parser.error("task_id must look like PROC-001")

    branch = args.branch or f"feature/{args.task_id.lower()}"
    worktree_path = args.path or args.worktree_root / args.task_id
    base_ref = args.parent_branch or args.base or default_base_ref()
    base_kind = "dependency parent branch" if args.parent_branch else "independent base ref"

    ensure_git_repo()
    ensure_ref_exists(base_ref)

    print(f"task: {args.task_id}")
    print(f"branch: {branch}")
    print(f"worktree: {worktree_path}")
    print(f"base: {base_ref} ({base_kind})")

    if args.dry_run:
        print("dry-run: would run:")
        print(f"  git worktree add -b {shellish(branch)} {shellish(str(worktree_path))} {shellish(base_ref)}")
        return 0

    if branch_exists(branch):
        print(f"error: branch already exists: {branch}", file=sys.stderr)
        return 1
    if worktree_path.exists():
        print(f"error: worktree path already exists: {worktree_path}", file=sys.stderr)
        return 1

    run_git(["worktree", "add", "-b", branch, str(worktree_path), base_ref])
    print("created task worktree")
    return 0


def default_base_ref() -> str:
    if git_ok(["rev-parse", "--verify", "origin/master^{commit}"]):
        return "origin/master"
    return "master"


def ensure_git_repo() -> None:
    result = run_git(["rev-parse", "--show-toplevel"], capture=True)
    if Path(result.stdout.strip()).resolve() != ROOT.resolve():
        raise SystemExit("error: run this helper from the BannerMod repository")


def ensure_ref_exists(ref: str) -> None:
    if not git_ok(["rev-parse", "--verify", f"{ref}^{{commit}}"]):
        raise SystemExit(f"error: base ref does not exist or is not a commit: {ref}")


def branch_exists(branch: str) -> bool:
    return git_ok(["rev-parse", "--verify", f"refs/heads/{branch}"])


def git_ok(args: list[str]) -> bool:
    return subprocess.run(
        ["git", *args], cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False
    ).returncode == 0


def run_git(args: list[str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=True,
    )


def shellish(value: str) -> str:
    if re.fullmatch(r"[A-Za-z0-9_./:@%+=,-]+", value):
        return value
    return "'" + value.replace("'", "'\\''") + "'"


if __name__ == "__main__":
    raise SystemExit(main())
