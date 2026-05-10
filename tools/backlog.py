#!/usr/bin/env python3
"""Small SQLite-backed backlog tool for BannerMod agents.

The goal is intentionally modest: keep the backlog ordered, validate required
fields, and return bounded task batches without dumping the whole database into
agent context.
"""

from __future__ import annotations

import argparse
from copy import deepcopy
import json
import re
import sqlite3
import subprocess
import sys
from datetime import date
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BACKLOG_DB_PATH = ROOT / "docs" / "BANNERMOD_BACKLOG.sqlite"
BACKLOG_JSON_PATH = ROOT / "docs" / "BANNERMOD_BACKLOG.json"
VALID_STATUSES = {"open", "in_progress", "done"}
SCHEMA_VERSION = 2
DB_SCHEMA_VERSION = 1
REQUIRED_TASK_FIELDS = ("id", "title", "status", "why", "scope", "acceptance", "dependencies", "updated")
TASK_ID_RE = re.compile(r"^[A-Z][A-Z0-9]+-[0-9]+[A-Z0-9-]*$")
MAX_BACKLOG_JSON_BYTES = 5 * 1024 * 1024
MAX_BACKLOG_DB_BYTES = 20 * 1024 * 1024
MAX_BATCH_LIMIT = 50


def main() -> int:
    parser = argparse.ArgumentParser(description="BannerMod backlog mini-Jira")
    parser.add_argument("--db", default=str(BACKLOG_DB_PATH), help="Backlog SQLite database path")
    parser.add_argument("--file", dest="legacy_file", help=argparse.SUPPRESS)
    sub = parser.add_subparsers(dest="command", required=True)

    p_list = sub.add_parser("list", help="List task summaries")
    add_common_filters(p_list)

    p_batch = sub.add_parser("batch", help="Print a bounded batch of tasks")
    add_common_filters(p_batch)
    p_batch.add_argument("--limit", type=int, default=5, help="Maximum tasks to print")
    p_batch.add_argument("--offset", type=int, default=0, help="Skip matching tasks")
    p_batch.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    p_batch.add_argument("--compact", action="store_true", help="One line per task: ID [status] title")

    p_ready = sub.add_parser("ready", help="Print N open tasks whose dependencies are already done")
    p_ready.add_argument("limit", type=int, help="Maximum tasks to print")
    p_ready.add_argument("--offset", type=int, default=0, help="Skip matching tasks")
    p_ready.add_argument("--prefix", help="ID prefix filter, e.g. UI or PERF")
    p_ready.add_argument("--query", help="Case-insensitive text filter over id/title/why")
    p_ready.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    p_ready.add_argument("--compact", action="store_true", help="One line per task: ID [status] title")

    p_show = sub.add_parser("show", help="Show one full task")
    p_show.add_argument("id", help="Task id")
    p_show.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    p_show.add_argument("--compact", action="store_true", help="One line: ID [status] title (skip body)")

    p_add = sub.add_parser("add", help="Append a new open task")
    p_add.add_argument("id", help="Task id, e.g. UI-008")
    p_add.add_argument("title", help="Task title")
    p_add.add_argument("--why", required=True, help="Why this task exists")
    p_add.add_argument("--scope", action="append", required=True, help="Concrete deliverable; repeatable")
    p_add.add_argument("--acceptance", action="append", required=True, help="Verifiable acceptance item; repeatable")
    p_add.add_argument("--depends-on", action="append", default=[], help="Task id dependency; repeatable")
    p_add.add_argument("--evidence", action="append", default=[], help="Evidence reference; repeatable")
    p_add.add_argument("--dry-run", action="store_true", help="Validate and preview without writing")

    p_progress = sub.add_parser("progress", help="Append a progress note")
    p_progress.add_argument("id", help="Task id")
    p_progress.add_argument("text", help="Progress text")
    p_progress.add_argument("--date", default=today(), help="Progress date")

    p_set_deps = sub.add_parser("set-deps", help="Replace one task's dependency list")
    p_set_deps.add_argument("id", help="Task id")
    p_set_deps.add_argument("--depends-on", action="append", default=[], help="Task id dependency; repeatable")

    p_split = sub.add_parser("split", help="Split one parent task into child tasks and wire dependencies")
    p_split.add_argument("id", help="Parent task id")
    p_split.add_argument("--child-file", required=True, help="JSON file containing child task objects")
    p_split.add_argument("--progress", required=True, help="Parent progress note describing landed and moved work")
    p_split.add_argument("--date", default=today(), help="Progress/update date")
    p_split.add_argument("--dry-run", action="store_true", help="Validate and preview without writing")

    p_done = sub.add_parser("done", help="Mark a task done after verification")
    p_done.add_argument("id", help="Task id")
    p_done.add_argument("--verification", required=True, help="Verification result proving acceptance is satisfied")
    p_done.add_argument("--date", default=today(), help="Done date")

    p_validate = sub.add_parser("validate", help="Validate backlog structure")
    p_validate.add_argument("--json", action="store_true", help="Print machine-readable JSON")

    p_stage = sub.add_parser("stage", help="Stage canonical backlog files with git")
    p_stage.add_argument("--no-validate", action="store_true", help="Skip validation before staging")

    p_migrate_json = sub.add_parser("migrate-json", help="Import legacy JSON backlog into SQLite")
    p_migrate_json.add_argument("--json-file", default=str(BACKLOG_JSON_PATH), help="Legacy JSON backlog path")
    p_migrate_json.add_argument("--force", action="store_true", help="Overwrite an existing SQLite backlog")
    p_migrate_json.add_argument("--remove-json", action="store_true", help="Remove the legacy JSON file after import")

    sub.add_parser("migrate-schema2", help="Upgrade schema 1 backlog to schema 2 dependencies format")

    args = parser.parse_args()
    path = Path(args.legacy_file or args.db)
    if args.command == "migrate-json":
        return cmd_migrate_json(path, args)
    if args.command == "stage":
        return cmd_stage(path, args)

    data = load(path)

    if args.command == "list":
        return cmd_list(data, args)
    if args.command == "batch":
        return cmd_batch(data, args)
    if args.command == "ready":
        return cmd_ready(data, args)
    if args.command == "show":
        return cmd_show(data, args)
    if args.command == "add":
        return cmd_add(path, data, args)
    if args.command == "progress":
        return cmd_progress(path, data, args)
    if args.command == "set-deps":
        return cmd_set_deps(path, data, args)
    if args.command == "split":
        return cmd_split(path, data, args)
    if args.command == "done":
        return cmd_done(path, data, args)
    if args.command == "validate":
        return cmd_validate(data, args)
    if args.command == "migrate-schema2":
        return cmd_migrate_schema2(path, data)

    parser.error(f"unknown command: {args.command}")
    return 2


def add_common_filters(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--status", choices=sorted(VALID_STATUSES), default="open", help="Task status filter")
    parser.add_argument("--prefix", help="ID prefix filter, e.g. UI or PERF")
    parser.add_argument("--query", help="Case-insensitive text filter over id/title/why")
    parser.add_argument("--ready", action="store_true", help="Only show tasks whose dependencies are all done")


def load(path: Path) -> dict[str, Any]:
    return load_sqlite(path)


def save(path: Path, data: dict[str, Any]) -> None:
    validate_or_exit(data)
    save_sqlite(path, data)


def load_json(path: Path) -> dict[str, Any]:
    try:
        size = path.stat().st_size
        if size > MAX_BACKLOG_JSON_BYTES:
            raise SystemExit(
                f"backlog JSON is {size} bytes, over the {MAX_BACKLOG_JSON_BYTES} byte safety limit; "
                "split/archive old entries before using tools/backlog"
            )
        with path.open("r", encoding="utf-8") as fh:
            data = json.load(fh)
    except FileNotFoundError:
        raise SystemExit(f"backlog not found: {path}")
    except json.JSONDecodeError as exc:
        raise SystemExit(f"invalid JSON in {path}: {exc}")
    if not isinstance(data, dict):
        raise SystemExit("backlog root must be an object")
    return data


def load_sqlite(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SystemExit(
            f"backlog database not found: {path}\n"
            f"Run: tools/backlog migrate-json --json-file {BACKLOG_JSON_PATH.relative_to(ROOT)}"
        )
    size = path.stat().st_size
    if size > MAX_BACKLOG_DB_BYTES:
        raise SystemExit(
            f"backlog database is {size} bytes, over the {MAX_BACKLOG_DB_BYTES} byte safety limit"
        )
    try:
        with sqlite3.connect(path) as conn:
            conn.row_factory = sqlite3.Row
            metadata = dict(conn.execute("SELECT key, value FROM metadata").fetchall())
            task_rows = conn.execute(
                "SELECT task_json FROM tasks ORDER BY sort_order ASC, id ASC"
            ).fetchall()
    except sqlite3.DatabaseError as exc:
        raise SystemExit(f"invalid backlog database {path}: {exc}")

    try:
        schema = int(metadata.get("schema", "0"))
        rules = json.loads(metadata.get("rules", "[]"))
        loaded_tasks = [json.loads(row["task_json"]) for row in task_rows]
    except (TypeError, ValueError, json.JSONDecodeError) as exc:
        raise SystemExit(f"invalid backlog database payload {path}: {exc}")

    return {
        "schema": schema,
        "description": metadata.get("description", ""),
        "rules": rules,
        "tasks": loaded_tasks,
    }


def save_sqlite(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with sqlite3.connect(path) as conn:
            init_db(conn)
            conn.execute("DELETE FROM metadata")
            conn.executemany(
                "INSERT INTO metadata(key, value) VALUES (?, ?)",
                [
                    ("schema", str(data.get("schema", SCHEMA_VERSION))),
                    ("description", str(data.get("description", ""))),
                    ("rules", json.dumps(data.get("rules", []), ensure_ascii=False)),
                ],
            )
            conn.execute("DELETE FROM tasks")
            for index, task in enumerate(tasks(data)):
                conn.execute(
                    "INSERT INTO tasks(id, sort_order, task_json) VALUES (?, ?, ?)",
                    (
                        str(task.get("id", "")),
                        index,
                        json.dumps(task, ensure_ascii=False),
                    ),
                )
            conn.execute(f"PRAGMA user_version = {DB_SCHEMA_VERSION}")
    except sqlite3.DatabaseError as exc:
        raise SystemExit(f"failed to write backlog database {path}: {exc}")


def init_db(conn: sqlite3.Connection) -> None:
    conn.execute(
        "CREATE TABLE IF NOT EXISTS metadata ("
        "key TEXT PRIMARY KEY, "
        "value TEXT NOT NULL"
        ")"
    )
    conn.execute(
        "CREATE TABLE IF NOT EXISTS tasks ("
        "id TEXT PRIMARY KEY, "
        "sort_order INTEGER NOT NULL, "
        "task_json TEXT NOT NULL"
        ")"
    )


def tasks(data: dict[str, Any]) -> list[dict[str, Any]]:
    value = data.get("tasks")
    if not isinstance(value, list):
        raise SystemExit("backlog.tasks must be a list")
    return value


def find_task(data: dict[str, Any], task_id: str) -> dict[str, Any]:
    wanted = task_id.upper()
    for task in tasks(data):
        if str(task.get("id", "")).upper() == wanted:
            return task
    raise SystemExit(f"task not found: {task_id}")


def matching(data: dict[str, Any], args: argparse.Namespace) -> list[dict[str, Any]]:
    result = []
    query = (args.query or "").lower()
    prefix = (args.prefix or "").upper()
    for task in tasks(data):
        if task.get("status") != args.status:
            continue
        task_id = str(task.get("id", ""))
        if prefix and not task_id.upper().startswith(prefix):
            continue
        haystack = " ".join(str(task.get(k, "")) for k in ("id", "title", "why")).lower()
        if query and query not in haystack:
            continue
        if args.ready and open_dependency_ids(data, task):
            continue
        result.append(task)
    return result


def cmd_list(data: dict[str, Any], args: argparse.Namespace) -> int:
    for task in matching(data, args):
        print(f"{task['id']} [{task['status']}] {task['title']}")
    return 0


def cmd_batch(data: dict[str, Any], args: argparse.Namespace) -> int:
    if args.limit < 1:
        raise SystemExit("--limit must be at least 1")
    if args.limit > MAX_BATCH_LIMIT:
        raise SystemExit(f"--limit must be <= {MAX_BATCH_LIMIT}")
    if args.offset < 0:
        raise SystemExit("--offset must be >= 0")
    selected = matching(data, args)[args.offset : args.offset + args.limit]
    if args.json:
        print(json.dumps(selected, indent=2, ensure_ascii=False))
        return 0
    for task in selected:
        print_task(task, compact=args.compact)
        if not args.compact:
            print()
    if not selected:
        print("No matching tasks.")
    return 0


def cmd_ready(data: dict[str, Any], args: argparse.Namespace) -> int:
    if args.limit < 1:
        raise SystemExit("limit must be at least 1")
    if args.limit > MAX_BATCH_LIMIT:
        raise SystemExit(f"limit must be <= {MAX_BATCH_LIMIT}")
    if args.offset < 0:
        raise SystemExit("--offset must be >= 0")
    selected = ready_tasks(data, args.prefix, args.query)[args.offset : args.offset + args.limit]
    if args.json:
        print(json.dumps(selected, indent=2, ensure_ascii=False))
        return 0
    for task in selected:
        print_task(task, compact=args.compact)
        if not args.compact:
            print()
    if not selected:
        print("No ready tasks.")
    return 0


def cmd_show(data: dict[str, Any], args: argparse.Namespace) -> int:
    task = find_task(data, args.id)
    if args.json:
        print(json.dumps(task, indent=2, ensure_ascii=False))
    else:
        print_task(task, compact=args.compact)
    return 0


def ready_tasks(data: dict[str, Any], prefix: str | None, query: str | None) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    wanted_prefix = (prefix or "").upper()
    wanted_query = (query or "").lower()
    for task in tasks(data):
        if task.get("status") != "open":
            continue
        task_id = str(task.get("id", ""))
        if wanted_prefix and not task_id.upper().startswith(wanted_prefix):
            continue
        haystack = " ".join(str(task.get(k, "")) for k in ("id", "title", "why")).lower()
        if wanted_query and wanted_query not in haystack:
            continue
        if open_dependency_ids(data, task):
            continue
        result.append(task)
    return result


def cmd_add(path: Path, data: dict[str, Any], args: argparse.Namespace) -> int:
    task_id = normalize_task_id(args.id)
    if any(str(task.get("id", "")).upper() == task_id for task in tasks(data)):
        raise SystemExit(f"task already exists: {task_id}")
    task = {
        "id": task_id,
        "title": required_text(args.title, "title"),
        "status": "open",
        "updated": today(),
        "why": required_text(args.why, "why"),
        "scope": clean_list(args.scope, "scope"),
        "acceptance": clean_list(args.acceptance, "acceptance"),
        "dependencies": clean_optional_task_ids(args.depends_on, "depends-on"),
        "progress": [],
        "verification": [],
        "evidence": clean_optional_list(args.evidence, "evidence"),
    }
    draft = deepcopy(data)
    draft["tasks"] = [*tasks(data), task]
    validate_or_exit(draft)
    if args.dry_run:
        print("Add validation OK; no file written.")
        print_task(task, compact=False)
        return 0
    data["tasks"] = draft["tasks"]
    save(path, data)
    print(f"Added {task_id}: {task['title']}")
    return 0


def cmd_progress(path: Path, data: dict[str, Any], args: argparse.Namespace) -> int:
    task = find_task(data, args.id)
    task.setdefault("progress", []).append({"date": args.date, "text": args.text})
    task["updated"] = args.date
    if task.get("status") == "open":
        task["status"] = "in_progress"
    save(path, data)
    print(f"Updated progress for {task['id']}")
    return 0


def cmd_set_deps(path: Path, data: dict[str, Any], args: argparse.Namespace) -> int:
    task = find_task(data, args.id)
    task_id = str(task.get("id", "")).upper()
    dependencies = clean_optional_task_ids(args.depends_on, "depends-on")
    if task_id in dependencies:
        raise SystemExit(f"{task_id} cannot depend on itself")
    task["dependencies"] = dependencies
    task["updated"] = today()
    save(path, data)
    if dependencies:
        print(f"Updated dependencies for {task_id}: {', '.join(dependencies)}")
    else:
        print(f"Updated dependencies for {task_id}: none")
    return 0


def cmd_split(path: Path, data: dict[str, Any], args: argparse.Namespace) -> int:
    parent = find_task(data, args.id)
    parent_id = str(parent.get("id", "")).upper()
    progress_text = required_text(args.progress, "progress")
    child_specs = load_child_specs(Path(args.child_file))
    child_ids = [str(task["id"]) for task in child_specs]
    for child in child_specs:
        child["updated"] = args.date
    if parent_id in child_ids:
        raise SystemExit(f"{parent_id} cannot be split into itself")
    existing_ids = {str(task.get("id", "")).upper() for task in tasks(data)}
    duplicates = [task_id for task_id in child_ids if task_id in existing_ids]
    if duplicates:
        raise SystemExit(f"child task already exists: {', '.join(duplicates)}")

    draft = deepcopy(data)
    draft_parent = find_task(draft, parent_id)
    draft_parent["dependencies"] = child_ids
    draft_parent["updated"] = args.date
    draft_parent.setdefault("progress", []).append({"date": args.date, "text": progress_text})
    if draft_parent.get("status") == "open":
        draft_parent["status"] = "in_progress"
    draft["tasks"] = [*tasks(draft), *child_specs]
    validate_or_exit(draft)

    if args.dry_run:
        print("Split validation OK; no file written.")
        print(f"Parent {parent_id} dependencies would become: {', '.join(child_ids)}")
        for child in child_specs:
            print_task(child, compact=True)
        return 0

    data.clear()
    data.update(draft)
    save(path, data)
    print(f"Split {parent_id} into children: {', '.join(child_ids)}")
    return 0


def cmd_done(path: Path, data: dict[str, Any], args: argparse.Namespace) -> int:
    task = find_task(data, args.id)
    blocked_by = open_dependency_ids(data, task)
    if blocked_by:
        raise SystemExit(
            f"cannot mark {task['id']} done; unresolved dependencies: {', '.join(blocked_by)}"
        )
    task["status"] = "done"
    task["doneDate"] = args.date
    task["updated"] = args.date
    task.setdefault("verification", []).append({"date": args.date, "result": args.verification})
    save(path, data)
    print(f"Marked {task['id']} done")
    return 0


def cmd_migrate_schema2(path: Path, data: dict[str, Any]) -> int:
    migrate_schema2_data(data)
    save(path, data)
    print(f"Migrated backlog to schema {SCHEMA_VERSION}")
    return 0


def migrate_schema2_data(data: dict[str, Any]) -> None:
    schema = data.get("schema")
    if schema not in (1, SCHEMA_VERSION):
        raise SystemExit(f"unsupported schema for migration: {schema!r}")
    data["schema"] = SCHEMA_VERSION
    rules = list(data.get("rules") or [])
    if not rules:
        rules = [
            "The SQLite database is the single canonical backlog; docs/BANNERMOD_BACKLOG.md is only a human-facing pointer.",
            "Use tools/backlog.py batch/list/show to inspect work instead of dumping the whole database into context.",
            "Every task must include id, title, status, why, scope, acceptance, dependencies, and updated date.",
            "DONE means every acceptance item is observably satisfied in the current codebase, not merely supported by a lower-level policy or partial slice.",
            "Before marking a task done, run the relevant verification and record the result in verification. If a check cannot be run, record why.",
            "If a task changes UI, changes gameplay mechanics, or adds player-facing mechanics, update both MULTIPLAYER_GUIDE_RU.md and MULTIPLAYER_GUIDE_EN.md before marking it done.",
            "Append progress entries instead of rewriting history. Keep old evidence and progress unless it is factually wrong.",
            "Add new work as an open task with concrete deliverables, explicit dependencies, and verifiable acceptance checks. Do not add vague reminders.",
        ]
    else:
        rules = [
            "The SQLite database is the single canonical backlog; docs/BANNERMOD_BACKLOG.md is only a human-facing pointer."
            if rule == "This JSON file is the single canonical backlog; docs/BANNERMOD_BACKLOG.md is only a human-facing pointer."
            else "Use tools/backlog.py batch/list/show to inspect work instead of dumping the whole database into context."
            if rule == "Use tools/backlog.py batch/list/show to inspect work instead of dumping the whole JSON file into context."
            else
            "Every task must include id, title, status, why, scope, acceptance, dependencies, and updated date."
            if rule == "Every task must include id, title, status, why, scope, acceptance, and updated date."
            else "Add new work as an open task with concrete deliverables, explicit dependencies, and verifiable acceptance checks. Do not add vague reminders."
            if rule == "Add new work as an open task with concrete deliverables and verifiable acceptance checks. Do not add vague reminders."
            else rule
            for rule in rules
        ]
        dependency_rule = "Dependencies must be explicit. Use an empty list when a task has no blockers; otherwise list the blocking task ids so work can be parallelized safely."
        if dependency_rule not in rules:
            rules.append(dependency_rule)
    data["rules"] = rules
    for task in tasks(data):
        task.setdefault("dependencies", [])


def cmd_validate(data: dict[str, Any], args: argparse.Namespace) -> int:
    errors = validate(data)
    if args.json:
        print(json.dumps({"ok": not errors, "errors": errors}, indent=2, ensure_ascii=False))
    elif errors:
        print("Backlog validation failed:")
        for error in errors:
            print(f"- {error}")
    else:
        open_count = sum(1 for task in tasks(data) if task.get("status") == "open")
        in_progress_count = sum(1 for task in tasks(data) if task.get("status") == "in_progress")
        done_count = sum(1 for task in tasks(data) if task.get("status") == "done")
        print(f"Backlog OK: {open_count} open, {in_progress_count} in_progress, {done_count} done")
    return 1 if errors else 0


def cmd_migrate_json(db_path: Path, args: argparse.Namespace) -> int:
    json_path = Path(args.json_file)
    if db_path.exists() and not args.force:
        raise SystemExit(f"backlog database already exists: {db_path}; use --force to replace it")
    data = load_json(json_path)
    if data.get("schema") == 1:
        migrate_schema2_data(data)
    validate_or_exit(data)
    if db_path.exists():
        db_path.unlink()
    save_sqlite(db_path, data)
    if args.remove_json:
        json_path.unlink()
    print(f"Migrated {len(tasks(data))} tasks to {db_path}")
    if args.remove_json:
        print(f"Removed legacy JSON backlog: {json_path}")
    return 0


def cmd_stage(db_path: Path, args: argparse.Namespace) -> int:
    if not args.no_validate:
        validate_or_exit(load(db_path))
    paths = [repo_relative(db_path)]
    if BACKLOG_JSON_PATH.exists() or git_tracks(BACKLOG_JSON_PATH):
        paths.append(repo_relative(BACKLOG_JSON_PATH))
    completed = subprocess.run(
        ["git", "add", "--", *paths],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip() or "git add failed"
        raise SystemExit(message)
    print("Staged backlog files: " + ", ".join(paths))
    return 0


def repo_relative(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(ROOT))
    except ValueError:
        raise SystemExit(f"path is outside repository root: {path}")


def git_tracks(path: Path) -> bool:
    relative = repo_relative(path)
    return subprocess.run(
        ["git", "ls-files", "--error-unmatch", "--", relative],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    ).returncode == 0


def validate_or_exit(data: dict[str, Any]) -> None:
    errors = validate(data)
    if errors:
        raise SystemExit("backlog validation failed before save:\n" + "\n".join(f"- {e}" for e in errors))


def validate(data: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    all_tasks = tasks(data)
    if data.get("schema") != SCHEMA_VERSION:
        errors.append(f"schema must be {SCHEMA_VERSION}")
    if not isinstance(data.get("rules"), list) or not data.get("rules"):
        errors.append("rules must be a non-empty list")
    task_map: dict[str, dict[str, Any]] = {}
    for index, task in enumerate(all_tasks, start=1):
        if not isinstance(task, dict):
            errors.append(f"task #{index} must be an object")
            continue
        task_id = str(task.get("id", ""))
        for field in REQUIRED_TASK_FIELDS:
            if field not in task:
                errors.append(f"{task_id or '#'+str(index)} missing {field}")
        if not TASK_ID_RE.match(task_id):
            errors.append(f"{task_id or '#'+str(index)} has invalid id format; expected PREFIX-001")
        if task_id in seen:
            errors.append(f"duplicate task id: {task_id}")
        seen.add(task_id)
        task_map[task_id] = task
        if task.get("status") not in VALID_STATUSES:
            errors.append(f"{task_id} has invalid status {task.get('status')!r}")
        for field in ("title", "why", "updated"):
            value = task.get(field)
            if not isinstance(value, str) or not value.strip():
                errors.append(f"{task_id} must have non-empty string {field}")
        for field in ("scope", "acceptance"):
            value = task.get(field)
            if not isinstance(value, list) or not value or not all(isinstance(item, str) and item.strip() for item in value):
                errors.append(f"{task_id} must have non-empty string list {field}")
        dependencies = task.get("dependencies")
        if not isinstance(dependencies, list) or not all(isinstance(item, str) and item.strip() for item in dependencies):
            errors.append(f"{task_id} must have string list dependencies")
        else:
            normalized_dependencies: list[str] = []
            for dep in dependencies:
                dep_id = dep.strip().upper()
                if not TASK_ID_RE.match(dep_id):
                    errors.append(f"{task_id} has invalid dependency id {dep!r}")
                    continue
                if dep_id == task_id:
                    errors.append(f"{task_id} cannot depend on itself")
                if dep_id in normalized_dependencies:
                    errors.append(f"{task_id} lists duplicate dependency {dep_id}")
                normalized_dependencies.append(dep_id)
        for field in ("progress", "verification", "evidence"):
            if field in task and not isinstance(task[field], list):
                errors.append(f"{task_id} {field} must be a list")
        if task.get("status") == "done" and not task.get("verification"):
            errors.append(f"{task_id} is done but has no verification entry")
    for task_id, task in task_map.items():
        for dep_id in dependency_list(task):
            if dep_id not in task_map:
                errors.append(f"{task_id} depends on missing task {dep_id}")
        if task.get("status") == "done":
            blocked_by = [dep_id for dep_id in dependency_list(task) if task_map.get(dep_id, {}).get("status") != "done"]
            if blocked_by:
                errors.append(f"{task_id} is done but has unresolved dependencies: {', '.join(blocked_by)}")
    for cycle in find_dependency_cycles(task_map):
        errors.append(f"dependency cycle detected: {' -> '.join(cycle)}")
    return errors


def normalize_task_id(value: str) -> str:
    task_id = required_text(value, "id").upper()
    if not TASK_ID_RE.match(task_id):
        raise SystemExit(f"invalid task id {value!r}; expected format like UI-008 or SEC-001")
    return task_id


def required_text(value: str, field: str) -> str:
    text = value.strip()
    if not text:
        raise SystemExit(f"{field} must not be empty")
    return text


def clean_list(values: list[str], field: str) -> list[str]:
    cleaned = [item.strip() for item in values if item.strip()]
    if not cleaned:
        raise SystemExit(f"{field} must contain at least one non-empty item")
    return cleaned


def clean_optional_list(values: list[str], field: str) -> list[str]:
    cleaned = [item.strip() for item in values if item.strip()]
    if len(cleaned) != len(values):
        raise SystemExit(f"{field} contains an empty item")
    return cleaned


def clean_optional_task_ids(values: list[str], field: str) -> list[str]:
    cleaned = clean_optional_list(values, field)
    normalized = [normalize_task_id(item) for item in cleaned]
    if len(set(normalized)) != len(normalized):
        raise SystemExit(f"{field} contains duplicate task ids")
    return normalized


def load_child_specs(path: Path) -> list[dict[str, Any]]:
    try:
        with path.open("r", encoding="utf-8") as fh:
            value = json.load(fh)
    except FileNotFoundError:
        raise SystemExit(f"child spec not found: {path}")
    except json.JSONDecodeError as exc:
        raise SystemExit(f"invalid JSON in child spec {path}: {exc}")
    if not isinstance(value, list) or not value:
        raise SystemExit("child spec must be a non-empty JSON array")

    children: list[dict[str, Any]] = []
    seen: set[str] = set()
    for index, item in enumerate(value, start=1):
        if not isinstance(item, dict):
            raise SystemExit(f"child spec #{index} must be an object")
        task_id = normalize_task_id(str(item.get("id", "")))
        if task_id in seen:
            raise SystemExit(f"child spec contains duplicate task id {task_id}")
        seen.add(task_id)
        children.append(
            {
                "id": task_id,
                "title": required_text(str(item.get("title", "")), "title"),
                "status": "open",
                "updated": today(),
                "why": required_text(str(item.get("why", "")), "why"),
                "scope": clean_list(child_spec_list(item, index, "scope"), "scope"),
                "acceptance": clean_list(child_spec_list(item, index, "acceptance"), "acceptance"),
                "dependencies": clean_optional_task_ids(child_spec_list(item, index, "dependencies"), "dependencies"),
                "progress": [],
                "verification": [],
                "evidence": clean_optional_list(child_spec_list(item, index, "evidence"), "evidence"),
            }
        )
    return children


def child_spec_list(item: dict[str, Any], index: int, field: str) -> list[str]:
    value = item.get(field, [])
    if not isinstance(value, list):
        raise SystemExit(f"child spec #{index} {field} must be a list")
    if not all(isinstance(entry, str) for entry in value):
        raise SystemExit(f"child spec #{index} {field} must contain only strings")
    return value


def dependency_list(task: dict[str, Any]) -> list[str]:
    return [str(item).strip().upper() for item in task.get("dependencies", []) if str(item).strip()]


def open_dependency_ids(data: dict[str, Any], task: dict[str, Any]) -> list[str]:
    task_by_id = {str(item.get("id", "")).upper(): item for item in tasks(data)}
    blocked_by: list[str] = []
    for dep_id in dependency_list(task):
        dep = task_by_id.get(dep_id)
        if dep is None or dep.get("status") != "done":
            blocked_by.append(dep_id)
    return blocked_by


def find_dependency_cycles(task_map: dict[str, dict[str, Any]]) -> list[list[str]]:
    cycles: list[list[str]] = []
    state: dict[str, int] = {}
    stack: list[str] = []

    def visit(task_id: str) -> None:
        marker = state.get(task_id, 0)
        if marker == 1:
            if task_id in stack:
                start = stack.index(task_id)
                cycles.append(stack[start:] + [task_id])
            return
        if marker == 2:
            return
        state[task_id] = 1
        stack.append(task_id)
        for dep_id in dependency_list(task_map[task_id]):
            if dep_id in task_map:
                visit(dep_id)
        stack.pop()
        state[task_id] = 2

    for task_id in task_map:
        visit(task_id)

    unique_cycles: list[list[str]] = []
    seen_keys: set[tuple[str, ...]] = set()
    for cycle in cycles:
        key = tuple(cycle)
        if key not in seen_keys:
            seen_keys.add(key)
            unique_cycles.append(cycle)
    return unique_cycles


def print_task(task: dict[str, Any], compact: bool) -> None:
    print(f"{task['id']} [{task['status']}] {task['title']}")
    if compact:
        return
    print(f"Updated: {task.get('updated', 'unknown')}")
    dependencies = dependency_list(task)
    print(f"Dependencies: {', '.join(dependencies) if dependencies else 'none'}")
    print(f"Why: {task.get('why', '')}")
    print("Scope:")
    for item in task.get("scope", []):
        print(f"- {item}")
    print("Acceptance:")
    for item in task.get("acceptance", []):
        print(f"- {item}")
    evidence = task.get("evidence") or []
    if evidence:
        print("Evidence:")
        for item in evidence:
            print(f"- {item}")


def today() -> str:
    return date.today().isoformat()


if __name__ == "__main__":
    raise SystemExit(main())
