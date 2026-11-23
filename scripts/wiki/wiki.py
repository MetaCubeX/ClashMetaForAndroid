#!/usr/bin/env python3
"""Small, dependency-free checks for a repository's Markdown wiki.

Supported Markdown: inline/reference links, images, ATX headings, explicit HTML
anchors, and standalone @path imports in AGENTS.md / CLAUDE.md. This is not a
Markdown renderer or a proof of the prose's correctness.
"""

from __future__ import annotations

import argparse
import bisect
import ctypes
import errno
import difflib
import fnmatch
import hashlib
import json
import os
import posixpath
import re
import shlex
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import unicodedata
from dataclasses import asdict, dataclass
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit


VERSION = "0.1.0"
DEFAULT_CONFIG = "docs/wiki.json"
DEFAULT_EXCLUDES = {".git", ".venv", "venv", "node_modules", "__pycache__"}
REGULAR_FILE_MODES = {"100644", "100755"}
ROOT_ROLES = {"knowledge", "process", "archive"}
PAGE_ROLES = ROOT_ROLES | {"entry", "redirect", "reference", "guide", "generated"}
DEFAULT_DECISION_PATTERNS = ["docs/decisions/*", "*/docs/decisions/*"]
REVIEW_TRAILER = "Docs-Reviewed"
HISTORY_SCAN_LIMIT = 2000
# days_since_commit walks at most this many commits back from HEAD looking for a
# substantial change to a plan or inbox item (B3). Plans change hands and get touched
# mechanically far less often than watched code, so a short window keeps the cost low.
STALE_SCAN_LIMIT = 30
HOOK_MARKERS = {"pre-commit": "check --staged", "commit-msg": "remind --staged"}
CONFIG_FIELDS = frozenset({
    "version", "project_name", "roots", "archive_root", "modules", "entrypoints", "pages",
    "exclude", "orphan_ignore", "status_values", "status_frontmatter", "adopted_at", "requirements", "verification", "reconcile",
    "reviews", "dir_budgets", "baseline", "generated_index", "archive_index", "verify", "remind", "repos",
    "size_limits",
})
DEFAULT_PAGE_CHARS = 100000
DEFAULT_LINE_CHARS = 10000
REMIND_FIELDS = frozenset({
    "watch", "threshold", "block_at", "ask_user", "decisions", "plans", "inbox", "plan_stale_days", "plan_idle_days",
})
WATCH_FIELDS = frozenset({"name", "code", "docs"})


class WikiError(Exception):
    pass


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def relative_path(value: object) -> str:
    if not isinstance(value, str) or not value or "\\" in value:
        raise WikiError(f"Expected a repository-relative POSIX path, got {value!r}")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or value == ".":
        raise WikiError(f"Path must stay inside the selected root: {value}")
    return path.as_posix()


def under(path: str, directory: str) -> bool:
    return path == directory or path.startswith(directory.rstrip("/") + "/")


def run_git(root: Path, *args: str) -> bytes:
    result = subprocess.run(
        ["git", "-C", str(root), *args], capture_output=True, check=False
    )
    if result.returncode:
        message = result.stderr.decode("utf-8", "replace").strip()
        raise WikiError(f"Git snapshot unavailable: {message or 'git command failed'}")
    return result.stdout


class Snapshot:
    """Read one namespace; index reads use captured blob IDs, never the worktree."""

    def __init__(self, root: Path, staged: bool = False):
        self.root = root.resolve()
        self.staged = staged
        self.cache: dict[str, bytes] = {}
        self.entries: dict[str, tuple[str, str]] = {}
        self.repository = self.root
        self.identifier = "working-tree"
        self._workspace_files: set[str] | None = None
        self._ignored: tuple[set[str], set[str]] | bool | None = None
        self._submodules: set[str] | None = None
        self._index_files: set[str] = set()
        self._directories: set[str] | None = None
        self.head_oid: str | None = None
        self.head_entries: dict[str, tuple[str, str]] = {}
        self.head_cache: dict[str, bytes] = {}
        self.added_files: set[str] = set()
        if not self.root.is_dir():
            raise WikiError(f"Root does not exist: {self.root}")
        if staged:
            self.repository = Path(
                run_git(self.root, "rev-parse", "--show-toplevel").decode().strip()
            ).resolve()
            try:
                prefix = self.root.relative_to(self.repository).as_posix()
            except ValueError as exc:
                raise WikiError("Selected root is outside the Git repository") from exc
            prefix = "" if prefix == "." else prefix + "/"
            head = subprocess.run(
                ["git", "-C", str(self.repository), "rev-parse", "--verify", "-q", "HEAD"],
                capture_output=True, check=False,
            )
            if head.returncode == 0:
                self.head_oid = head.stdout.decode("ascii").strip()
            elif head.returncode != 1:
                raise WikiError("Cannot capture HEAD for the added-document comparison")
            listing = run_git(self.repository, "ls-files", "--stage", "-z")
            self.identifier = "git-index-sha256:" + digest(listing)
            for item in listing.split(b"\0"):
                if not item:
                    continue
                meta, filename = item.split(b"\t", 1)
                mode, oid, stage = meta.decode("ascii").split()
                name = filename.decode("utf-8", "surrogateescape")
                if stage != "0":
                    raise WikiError(f"Unmerged index entry: {name}; resolve conflicts first")
                if name.startswith(prefix):
                    self.entries[name[len(prefix):]] = (mode, oid)
            self._index_files = set(self.entries)
            prior_files: set[str] = set()
            if self.head_oid:
                names = run_git(self.repository, "ls-tree", "-r", "--name-only", "-z", self.head_oid)
                for raw_name in names.split(b"\0"):
                    name = raw_name.decode("utf-8", "surrogateescape")
                    if name.startswith(prefix):
                        prior_files.add(name[len(prefix):])
                head_listing = run_git(self.repository, "ls-tree", "-r", "-z", self.head_oid)
                for item in head_listing.split(b"\0"):
                    if not item:
                        continue
                    meta, filename = item.split(b"\t", 1)
                    mode, _kind, oid = meta.decode("ascii").split()
                    name = filename.decode("utf-8", "surrogateescape")
                    if name.startswith(prefix):
                        self.head_entries[name[len(prefix):]] = (mode, oid)
            self.added_files = self._index_files - prior_files

    def git_ignored(self) -> tuple[set[str], set[str]] | None:
        """Untracked paths that Git ignores under the root, as (files, directories).

        None when the root is not inside a Git work tree, Git is unavailable, or the
        root itself is ignored; the working-tree walk then reads every file.
        """
        if self._ignored is None:
            try:
                result = subprocess.run(
                    ["git", "-C", str(self.root), "ls-files", "-z", "--others", "--ignored",
                     "--exclude-standard", "--directory"],
                    capture_output=True, check=False,
                )
            except OSError:
                self._ignored = False
                return None
            if result.returncode:
                self._ignored = False
                return None
            files: set[str] = set()
            directories: set[str] = set()
            for raw in result.stdout.split(b"\0"):
                if not raw:
                    continue
                name = raw.decode("utf-8", "surrogateescape")
                if name in ("./", "."):
                    self._ignored = False
                    return None
                if name.endswith("/"):
                    directories.add(name.rstrip("/"))
                else:
                    files.add(name)
            self._ignored = (files, directories)
        return self._ignored or None

    def submodule_paths(self) -> set[str]:
        """Paths under the root that Git records as submodules (mode 160000)."""
        if self._submodules is None:
            found: set[str] = set()
            if self.staged:
                found = {name for name, (mode, _) in self.entries.items() if mode == "160000"}
            else:
                try:
                    result = subprocess.run(["git", "-C", str(self.root), "ls-files", "--stage", "-z"],
                                            capture_output=True, check=False)
                except OSError:
                    result = None
                if result is not None and result.returncode == 0:
                    for item in result.stdout.split(b"\0"):
                        meta, _, name = item.partition(b"\t")
                        if meta.split()[:1] == [b"160000"]:
                            found.add(name.decode("utf-8", "surrogateescape"))
            self._submodules = found
        return self._submodules

    def submodule_checked_out(self, path: str) -> bool:
        """A submodule counts as checked out when its working tree has its own .git entry.

        A staged check reads only the index, which records the submodule's commit but not
        its files, so a submodule is never readable there.
        """
        return not self.staged and (self.root / path / ".git").exists()

    def unreadable_submodule(self, path: str) -> str | None:
        """The submodule that holds path, when it is not checked out; None otherwise."""
        for sub in self.submodule_paths():
            if path != sub and under(path, sub) and not self.submodule_checked_out(sub):
                return sub
        return None

    def ignore_source(self) -> str:
        if self.staged:
            return "index"
        return "git-ignored-skipped" if self.git_ignored() is not None else "not-applied"

    def files(self) -> set[str]:
        if self.staged:
            return self._index_files
        if self._workspace_files is None:
            ignored_files, ignored_dirs = self.git_ignored() or (set(), set())
            paths: set[str] = set()
            for directory, dirs, files in os.walk(self.root, followlinks=False):
                base = Path(directory).relative_to(self.root).as_posix()
                prefix = "" if base == "." else base + "/"
                dirs[:] = sorted(
                    d for d in dirs if d not in DEFAULT_EXCLUDES and prefix + d not in ignored_dirs
                )
                for name in files:
                    if prefix + name not in ignored_files:
                        paths.add(prefix + name)
            self._workspace_files = paths
        return self._workspace_files

    def exists(self, path: str) -> bool:
        # Exact names are used even on a case-insensitive host.
        files = self.files()
        if path in files or path == ".":
            return True
        if self._directories is None:
            self._directories = {
                parent.as_posix() for name in files for parent in PurePosixPath(name).parents
            }
        return path in self._directories

    def is_file(self, path: str) -> bool:
        if self.staged:
            return path in self.entries and self.entries[path][0] in REGULAR_FILE_MODES
        return path in self.files() and (self.root / path).is_file()

    def read(self, path: str) -> bytes:
        path = relative_path(path)
        if path in self.cache:
            return self.cache[path]
        if self.staged:
            if path not in self.entries:
                raise WikiError(f"Not present in the Git index: {path}")
            mode, oid = self.entries[path]
            if mode not in REGULAR_FILE_MODES:
                raise WikiError(f"Unsupported index file mode {mode}: {path}")
            data = run_git(self.repository, "cat-file", "blob", oid)
        else:
            target = self.root / path
            try:
                target.resolve().relative_to(self.root)
            except ValueError as exc:
                raise WikiError(f"File resolves outside the selected root: {path}") from exc
            try:
                data = target.read_bytes()
            except OSError as exc:
                raise WikiError(f"Cannot read {path}: {exc.strerror}") from exc
        self.cache[path] = data
        return data

    def text(self, path: str) -> str:
        try:
            return self.read(path).decode("utf-8")
        except UnicodeDecodeError as exc:
            raise WikiError(f"File is not UTF-8: {path}") from exc

    def head_read(self, path: str) -> bytes:
        """Read a path from the captured HEAD tree, without consulting the live index."""
        path = relative_path(path)
        if path in self.head_cache:
            return self.head_cache[path]
        if path not in self.head_entries:
            return b""
        data = run_git(self.repository, "cat-file", "blob", self.head_entries[path][1])
        self.head_cache[path] = data
        return data


@dataclass(frozen=True)
class Finding:
    rule: str
    severity: str
    path: str
    message: str
    line: int | None = None
    key: str | None = None

    def fingerprint(self) -> str:
        """Stable identity for baselines; line numbers move when unrelated text changes."""
        return f"{self.rule}|{self.path}|{self.key if self.key is not None else self.message}"


# Every line keyword the tool reads, as (Chinese, English). English matches without regard to case.
# scripts/wiki/formats.md lists the same words, and a test keeps the two in step, so a keyword
# added here without documentation fails the test suite.
KEYWORDS = {
    "status": ("状态", "Status"),
    "owner": ("负责", "Owner"),
    "reason": ("原因", "Reason"),
    "verified": ("核对", "Verified"),
    "review_by": ("复核期限", "Review by"),
    "acceptance_check": ("验收核对", "Acceptance check"),
    "manual": ("人工验证", "manual"),
    "evidence": ("证据", "Evidence"),
    "covers_requirements": ("覆盖需求", "Requirements"),
    "covers_findings": ("覆盖问题", "Covers"),
    "author": ("作者", "Author"),
    "check_table": ("核对", "Verification"),
    "verdict_column": ("判定", "Verdict"),
    "plan_section": ("方案", "Plan"),
}


def kw(name: str) -> str:
    """Regex alternation of one keyword's Chinese and English forms."""
    return "|".join(re.escape(word) for word in KEYWORDS[name])


STATUS_CLASSES = {"draft", "active", "blocked", "current", "finished", "superseded", "retired", "open"}
DEFAULT_STATUS_VALUES = {
    "草案": "draft", "draft": "draft",
    "执行中": "active", "active": "active",
    "阻塞": "blocked", "blocked": "blocked",
    "当前有效": "current", "current": "current",
    "已完成": "finished", "finished": "finished",
    "被替代": "superseded", "superseded": "superseded",
    "废止": "retired", "retired": "retired",
    "待分诊": "open", "open": "open",
}
STATUS_LINE = re.compile(rf"^(?:{kw('status')})\s*[:：]\s*([^\s。.,，;；]+)", re.I)
DEFAULT_PLAN_PATTERNS = ["dev/plans/*", "dev/reviews/*/README.md", "dev/reviews/*/plan-*.md"]
DEFAULT_REVIEW_PATTERNS = ["dev/reviews/*/README.md"]
# One file per inbox item, inside a type directory; dev/inbox/README.md is the inbox's own entry.
DEFAULT_INBOX_PATTERNS = ["dev/inbox/*/*.md"]
VERDICTS = {
    "待核对": "pending", "pending": "pending",
    "成立": "confirmed", "confirmed": "confirmed",
    "收窄": "narrowed", "narrowed": "narrowed",
    "不成立": "rejected", "rejected": "rejected",
    "已知": "known", "known": "known",
    "重复": "duplicate", "duplicate": "duplicate",
}
DEFAULT_REQUIREMENT_ID = r"[A-Z][A-Z0-9]*-\d+"
MANUAL_MARK = re.compile(rf"[（(]\s*(?:{kw('manual')})\s*[）)]", re.I)


def requirement_patterns(id_pattern: str) -> dict[str, re.Pattern]:
    """Build the requirement parsers around one ID pattern.

    An acceptance ID is always the requirement ID followed by '.' and a number,
    for example PW-11.1 or REQ-AUTH-001.2. Named groups keep any groups inside a
    project's own pattern from shifting the captures.
    """
    rid = f"(?:{id_pattern})"
    return {
        "heading": re.compile(rf"^(?P<level>#{{2,4}})\s+(?P<id>{rid})(?=\s|$)"),
        "item": re.compile(rf"^\s*[-*]\s+(?:\*\*)?(?P<id>{rid}\.\d+)(?:\*\*)?(?=\s|$)"),
        "check": re.compile(
            rf"^\s*(?:{kw('acceptance_check')})\s*[:：]\s*(?P<id>{rid}\.\d+)\s*[，,]\s*"
            r"(?:human|agent):\S+\s*[，,]\s*\d{4}-\d{2}-\d{2}\s*[。.]?\s*$", re.I),
        "reference": re.compile(rf"(?<![A-Za-z0-9_])(?P<ref>{rid}(?:\.\d+)?)(?![0-9A-Za-z_])"),
    }


def requirement_prefix(identifier: str) -> str:
    """The part of an ID before its first digit, e.g. 'PW-' for PW-11 or 'REQ-AUTH-' for REQ-AUTH-001."""
    return re.match(r"\D*", identifier).group(0)
REQUIREMENT_STATUSES = {
    "提出": "proposed", "proposed": "proposed",
    "已接受": "accepted", "accepted": "accepted",
    "已实现": "implemented", "implemented": "implemented",
    "已验证": "verified", "verified": "verified",
    "已废止": "retired", "retired": "retired",
}
DEFAULT_REQUIREMENT_TESTS = ["tests/*", "*/tests/*"]
VERIFY_LINE = re.compile(rf"^(?:{kw('verified')})\s*[:：]\s*(.*)$", re.I)
HEADER_START = re.compile(rf"^(?:{kw('verified')}|{kw('review_by')})\s*[:：]", re.I)
REVIEW_BY = re.compile(rf"(?:{kw('review_by')})\s*[:：]\s*([0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}})?([^\s。.；;]*)", re.I)
# A human checker named in prose or a table; placeholders such as human:<名字> or human:【待填】 are not names.
HUMAN_ACTOR = re.compile(r"(?<![A-Za-z0-9_])human:([^\s<【，,。；;|）)`]+)")
ACTOR = re.compile(r"^(human|agent):(\S+)$")
ACTOR_PREFIX = re.compile(r"^([A-Za-z]+):\S")
UNSAFE_NAME = re.compile(r"[\s#%()]")
# Only template names that are never a sensible final name. "requirements" and
# "route" stay allowed: docs/requirements.md is a recommended layout, and a page
# about routing may reasonably be called route.md.
NAME_PLACEHOLDERS = {"plan", "decision", "inbox-item"}
NAME_STATUS_OR_VERSION = re.compile(r"(?:^|[-_.])(?:draft|final|latest|active|done|completed|v\d+)(?=[-_.]|$)", re.I)


def parse_date(value: str):
    import datetime
    try:
        return datetime.date.fromisoformat(value)
    except ValueError:
        return None


def verification_header(text: str) -> dict:
    """Read the 核对 line and 复核期限 between the level-one title and the first level-two heading."""
    result = {"line": None, "entries": [], "problems": [], "review_by": None, "review_line": None}
    lines = without_code(text, inline=False).splitlines()
    start = next((i for i, l in enumerate(lines) if re.match(r"^#\s+\S", l)), None)
    if start is None:
        return result
    for number in range(start + 1, len(lines)):
        line = lines[number].strip()
        if re.match(r"^#{1,6}\s", line):
            break
        # Only lines that start with 核对 or 复核期限 count; prose and list items that mention them do not.
        if not HEADER_START.match(line):
            continue
        due = REVIEW_BY.search(line)
        if due:
            result["review_line"] = number + 1
            parsed = parse_date(due.group(1) or "") if not due.group(2) else None
            if parsed is None:
                result["problems"].append((number + 1, "复核期限 must be a date written as YYYY-MM-DD"))
            else:
                result["review_by"] = parsed
            line = line[:due.start()].strip()
        match = VERIFY_LINE.match(line)
        if not match:
            continue
        result["line"] = number + 1
        body = match.group(1).strip().rstrip("。.").strip()
        if not body:
            result["problems"].append((number + 1, "核对 line has no entries"))
        for raw in filter(None, (x.strip() for x in re.split(r"[；;]", body))):
            parts = [p.strip() for p in re.split(r"[，,]", raw) if p.strip()]
            actor = ACTOR.match(parts[0]) if parts else None
            date = parse_date(parts[1]) if len(parts) == 2 else None
            if not actor or date is None:
                result["problems"].append((number + 1, f"Entry '{raw}' must be 'human:<name>，YYYY-MM-DD' or 'agent:<name>，YYYY-MM-DD'"))
                continue
            result["entries"].append((actor.group(1), actor.group(2), date))
    return result


def trust_level(entries: list) -> str | None:
    if not entries:
        return None
    return "human-checked" if any(kind == "human" for kind, _, _ in entries) else "agent-checked"
FINDING_HEADING = re.compile(r"^##\s+([A-Za-z][A-Za-z0-9]*-\d+[a-z]?)(?=\s|$)")
FINDING_ID = re.compile(r"(?<![A-Za-z0-9-])([A-Za-z][A-Za-z0-9]*-\d+[a-z]?)(?![A-Za-z0-9-])")


def status_line_index(text: str) -> int | None:
    """Return the status line's index, following the shared Markdown parsing rules."""
    lines = without_code(text, inline=False).splitlines()
    for index, line in enumerate(lines):
        if re.match(r"^#\s+\S", line):
            for i in range(index + 1, len(lines)):
                if lines[i].strip():
                    return i if STATUS_LINE.match(lines[i].strip()) else None
            return None
    return None


def status_line(text: str) -> str | None:
    """Return the first non-blank line after the level-one title when it is a status line."""
    index = status_line_index(text)
    return text.splitlines()[index].strip() if index is not None else None


def frontmatter_value(text: str, field: str) -> str | None:
    """Return a top-level scalar from YAML frontmatter that opens on the first line, or None."""
    lines = text.lstrip("\ufeff").splitlines()
    if not lines or lines[0].strip() != "---":
        return None
    for line in lines[1:]:
        if line.strip() in ("---", "..."):
            return None
        match = re.match(rf"^{re.escape(field)}\s*:\s*(.*?)\s*$", line)
        if match:
            value = match.group(1)
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1].strip()
            else:
                value = re.sub(r"\s+#.*$", "", value)
            return value or None
    return None


def status_value(text: str) -> str | None:
    """Return the raw value from the first line after the level-one title."""
    line = status_line(text)
    return STATUS_LINE.match(line).group(1) if line else None


OWNER = re.compile(rf"(?:{kw('owner')})\s*[:：]\s*([^\s。；;，,]+)", re.I)


def status_owner(text: str) -> str | None:
    """Return the owner written on the status line, e.g. '负责：agent:kiro。'."""
    line = status_line(text)
    match = OWNER.search(line) if line else None
    return match.group(1).rstrip(".") if match else None


REASON = re.compile(rf"(?:{kw('reason')})\s*[:：]\s*([^。]*)", re.I)


def status_reason(text: str) -> str | None:
    """Return the reason written on the status line, e.g. '状态：阻塞。原因：等待接口评审。'."""
    line = status_line(text)
    if not line:
        return None
    match = REASON.search(line)
    if not match:
        return None
    reason = match.group(1).strip(" .，,；;").strip()
    return reason or None


class RepoView:
    """A registered neighbouring repository, read only to resolve the targets of links.

    A workspace check reads its working tree, which is editing feedback. A staged check reads
    the commit its HEAD pointed to when the check started, so the result can be reproduced.
    """

    def __init__(self, root: Path, name: str, path: str, staged: bool):
        self.name, self.path, self.staged = name, path, staged
        self.directory = (root / path).resolve()
        self.links_checked = 0
        self.links_unchecked = 0
        self._status: str | None = None
        self._head: str | None = None
        self._entries: dict[str, tuple[str, str] | None] = {}
        self._listing: dict[str, set[str]] = {}

    def _git(self, *args: str) -> subprocess.CompletedProcess | None:
        try:
            return subprocess.run(["git", "-C", str(self.directory), *args], capture_output=True, check=False)
        except OSError:
            return None

    def status(self) -> str:
        """checked-out, not-checked-out (missing or empty), or no-git-head (staged only)."""
        if self._status is None:
            try:
                present = self.directory.is_dir() and any(self.directory.iterdir())
            except OSError:
                present = False
            head = self._git("rev-parse", "--verify", "-q", "HEAD") if present else None
            if head is not None and head.returncode == 0:
                self._head = head.stdout.decode("ascii").strip()
            if not present:
                self._status = "not-checked-out"
            elif self.staged and self._head is None:
                self._status = "no-git-head"
            else:
                self._status = "checked-out"
        return self._status

    def _entry(self, rel: str) -> tuple[str, str] | None:
        if rel not in self._entries:
            found = None
            result = self._git("ls-tree", "-z", self._head or "HEAD", "--", rel)
            if result is not None and result.returncode == 0:
                for item in result.stdout.split(b"\0"):
                    meta, _, name = item.partition(b"\t")
                    if item and name.decode("utf-8", "surrogateescape") == rel:
                        mode, _kind, oid = meta.decode("ascii").split()
                        found = (mode, oid)
            self._entries[rel] = found
        return self._entries[rel]

    def _contained(self, target: Path) -> bool:
        real = os.path.realpath(target)
        base = str(self.directory)
        return real == base or real.startswith(base + os.sep)

    def exists(self, rel: str) -> bool:
        """Exact-case existence of a file or directory; a symlink that leaves the repository does not count."""
        if rel == "":
            return True
        if self.staged:
            return self._entry(rel) is not None
        parent = self.directory
        for part in PurePosixPath(rel).parts:
            names = self._listing.get(str(parent))
            if names is None:
                try:
                    names = set(os.listdir(parent))
                except OSError:
                    return False
                self._listing[str(parent)] = names
            if part not in names:
                return False
            parent = parent / part
        return self._contained(parent)

    def is_file(self, rel: str) -> bool:
        if self.staged:
            entry = self._entry(rel)
            return entry is not None and entry[0] in REGULAR_FILE_MODES
        return rel != "" and (self.directory / rel).is_file()

    def text(self, rel: str) -> str:
        if self.staged:
            entry = self._entry(rel)
            if entry is None or entry[0] not in REGULAR_FILE_MODES:
                raise WikiError(f"Not a regular file in {self.name} at HEAD: {rel}")
            data = self._git("cat-file", "blob", entry[1])
            if data is None or data.returncode:
                raise WikiError(f"Cannot read {rel} from {self.name} at HEAD")
            raw = data.stdout
        else:
            target = self.directory / rel
            if not self._contained(target):
                raise WikiError(f"File resolves outside the repository {self.name}: {rel}")
            try:
                raw = target.read_bytes()
            except OSError as exc:
                raise WikiError(f"Cannot read {rel} in {self.name}: {exc.strerror}") from exc
        try:
            return raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise WikiError(f"File is not UTF-8: {rel} in {self.name}") from exc

    def describe(self) -> dict:
        return {"name": self.name, "path": self.path, "status": self.status(),
                "read": "git-head" if self.staged else "working-tree",
                "head": self._head[:12] if self._head else None,
                "links_checked": self.links_checked, "links_unchecked": self.links_unchecked}


@dataclass(frozen=True)
class Link:
    target: str
    line: int
    kind: str = "markdown"
    defined_at: int | None = None  # Line of a reference definition, where the target text is written.


class Wiki:
    def __init__(self, snapshot: Snapshot, config_path: str = DEFAULT_CONFIG):
        self.snapshot = snapshot
        self._adoption_commits: set[str] | None = None
        self.config_path = relative_path(config_path)
        raw = snapshot.read(self.config_path)
        self.config_hash = digest(raw)
        try:
            self.config = json.loads(raw)
        except (ValueError, UnicodeDecodeError) as exc:
            raise WikiError(f"Invalid JSON in {config_path}: {exc}") from exc
        if not isinstance(self.config, dict) or type(self.config.get("version")) is not int or self.config["version"] != 1:
            raise WikiError("Configuration must be an object with version: 1")
        unknown = sorted(set(self.config) - CONFIG_FIELDS)
        if unknown:
            raise WikiError("configuration has unknown fields: " + ", ".join(unknown))
        self.roots = self._records("roots", required=True)
        self.modules = self._records("modules")
        self.pages = self._records("pages")
        self.entrypoints = self._paths("entrypoints", required=True)
        self.excludes = self.config.get("exclude", [])
        if not isinstance(self.excludes, list) or not all(isinstance(x, str) for x in self.excludes):
            raise WikiError("exclude must be an array of path patterns")
        seen_roots: set[str] = set()
        for root in self.roots:
            root["path"] = relative_path(root.get("path"))
            if root.get("role") not in ROOT_ROLES:
                raise WikiError(f"Invalid root role for {root['path']}")
            if root["path"] in seen_roots:
                raise WikiError(f"Root registered twice: {root['path']}")
            seen_roots.add(root["path"])
        seen_modules: set[str] = set()
        for module in self.modules:
            if not isinstance(module.get("name"), str) or not module["name"]:
                raise WikiError("Each module needs a non-empty name")
            if module["name"] in seen_modules:
                raise WikiError(f"Module registered twice: {module['name']}")
            seen_modules.add(module["name"])
            for field in ("path", "entry"):
                module[field] = relative_path(module.get(field))
            if "docs" in module:
                module["docs"] = relative_path(module["docs"])
        seen_pages: set[str] = set()
        for page in self.pages:
            page["path"] = relative_path(page.get("path"))
            if page["path"] in seen_pages:
                raise WikiError(f"Page registered twice: {page['path']}")
            seen_pages.add(page["path"])
            if page.get("role", "knowledge") not in PAGE_ROLES:
                raise WikiError(f"Unknown page role for {page['path']}")
            if "max_chars" in page and (type(page["max_chars"]) is not int or page["max_chars"] < 1):
                raise WikiError(f"max_chars must be a positive integer: {page['path']}")
            for key in ("id", "scope"):
                if key in page and (not isinstance(page[key], str) or not page[key]):
                    raise WikiError(f"{key} must be a non-empty string: {page['path']}")
        self.generated_index = self.config.get("generated_index")
        if self.generated_index is not None:
            self.generated_index = relative_path(self.generated_index)
            if not self.generated_index.endswith(".json"):
                raise WikiError("generated_index must be a JSON path, never a Markdown navigation page")
        self.page_map = {p["path"]: p for p in self.pages}
        for page in self.pages:
            if page.get("role") == "archive" and self.root_role(page["path"]) != "archive":
                raise WikiError(f"Page role archive requires a registered archive root: {page['path']}")
        self.primary_archive = self.config.get("archive_root")
        if self.primary_archive is not None:
            self.primary_archive = relative_path(self.primary_archive)
            if not any(r["path"] == self.primary_archive and r["role"] == "archive" for r in self.roots):
                raise WikiError("archive_root must identify a registered archive root")
        self.remind = self._remind_config(self.config.get("remind"))
        extra = self.config.get("status_values", {})
        if not isinstance(extra, dict) or not all(isinstance(k, str) and k and v in STATUS_CLASSES for k, v in extra.items()):
            raise WikiError("status_values must map status text to one of: " + ", ".join(sorted(STATUS_CLASSES)))
        self.status_values = {**DEFAULT_STATUS_VALUES, **{k.lower(): v for k, v in extra.items()}}
        self.decision_patterns = (self.remind or {}).get("decisions", DEFAULT_DECISION_PATTERNS)
        self.plan_patterns = (self.remind or {}).get("plans", DEFAULT_PLAN_PATTERNS)
        self.baseline_path = self.config.get("baseline")
        if self.baseline_path is not None:
            self.baseline_path = relative_path(self.baseline_path)
            if not self.baseline_path.endswith(".json"):
                raise WikiError("baseline must be a JSON path")
        self._status_cache: dict[str, tuple[str | None, str | None]] = {}
        self.review_patterns = self._patterns(self.config.get("reviews", DEFAULT_REVIEW_PATTERNS), "reviews")
        budgets = self.config.get("dir_budgets", {})
        if not isinstance(budgets, dict) or not all(isinstance(k, str) and k and type(v) is int and v > 0 for k, v in budgets.items()):
            raise WikiError("dir_budgets must map directory patterns to positive integers")
        self.dir_budgets = budgets
        self.size_limits = self._size_limits_config(self.config.get("size_limits"))
        raw_verification = self.config.get("verification", {})
        if not isinstance(raw_verification, dict) or set(raw_verification) - {"review_by_required", "human_required"}:
            raise WikiError("verification may only contain review_by_required and human_required")
        self.verification = {
            "review_by_required": self._patterns(raw_verification.get("review_by_required", []), "verification.review_by_required"),
            "human_required": self._patterns(raw_verification.get("human_required", []), "verification.human_required"),
        }
        self._verify_cache: dict[str, dict] = {}
        self.requirements_coverage: list[dict] = []
        self.requirement_groups = self._requirements_config(self.config.get("requirements"))
        field = self.config.get("status_frontmatter")
        if field is not None and (not isinstance(field, str) or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_-]*", field)):
            raise WikiError("status_frontmatter must be the name of one frontmatter field, e.g. \"status\"")
        self.status_frontmatter = field
        self._frontmatter_status: dict[str, str | None] = {}
        adopted = self.config.get("adopted_at")
        if adopted is not None and (not isinstance(adopted, str) or not adopted.strip() or adopted.startswith("-")):
            raise WikiError("adopted_at must name the commit where the project adopted these rules, e.g. a commit ID")
        self.adopted_at = adopted
        self._legacy: tuple[str, set[str]] | None = None
        self.archive_index = self.config.get("archive_index")
        if self.archive_index is not None:
            self.archive_index = relative_path(self.archive_index)
            if not self.archive_index.lower().endswith((".md", ".markdown")):
                raise WikiError("archive_index must be a Markdown path")
        self.verify = self._verify_config(self.config.get("verify"))
        self.orphan_ignore = self._patterns(self.config.get("orphan_ignore", []), "orphan_ignore")
        self.reconcile = self._reconcile_config(self.config.get("reconcile", []))
        self.repo_views = self._repos_config(self.config.get("repos"))
        self.require_repos = False
        self.submodule_unchecked: dict[str, int] = {}
        self._unregistered: tuple[set[str], list[str]] | None = None

    def _repos_config(self, raw: object) -> list[RepoView]:
        if raw is None:
            return []
        if not isinstance(raw, list) or not raw or not all(isinstance(x, dict) for x in raw):
            raise WikiError("repos must be a non-empty array of objects with a name and a path")
        views: list[RepoView] = []
        for item in raw:
            name, path = item.get("name"), item.get("path")
            if set(item) - {"name", "path"}:
                raise WikiError(f"repos[{name}] has unknown fields: " + ", ".join(sorted(set(item) - {"name", "path"})))
            if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+", name) or any(v.name == name for v in views):
                raise WikiError("Each repos entry needs a unique name made of letters, digits, '.', '_' or '-'")
            if not isinstance(path, str) or not path or "\\" in path or path.startswith("/"):
                raise WikiError(f"repos[{name}].path must be a relative POSIX path such as ../OtherRepo")
            normal = posixpath.normpath(path)
            if not normal.startswith("../"):
                raise WikiError(f"repos[{name}].path must point outside the project root, like ../OtherRepo; "
                                "submodules inside the root need no entry")
            view = RepoView(self.snapshot.root, name, normal, self.snapshot.staged)
            if view.directory == self.snapshot.root or self.snapshot.root in view.directory.parents:
                raise WikiError(f"repos[{name}].path resolves inside the project root")
            for other in views:
                if normal == other.path or normal.startswith(other.path + "/") or other.path.startswith(normal + "/"):
                    raise WikiError(f"repos[{name}] and repos[{other.name}] overlap; list each repository once")
            views.append(view)
        return views

    def repo_for(self, external: str) -> tuple[RepoView, str] | None:
        """The registered repository holding a normalised path that leaves the root, and the path inside it."""
        for view in self.repo_views:
            if external == view.path:
                return view, ""
            if external.startswith(view.path + "/"):
                return view, external[len(view.path) + 1:]
        return None

    def unregistered_docs(self) -> set[str]:
        """Directories named docs that hold Markdown outside every registered root, module and page."""
        if self._unregistered is None:
            found: set[str] = set()
            for path in self.snapshot.files():
                if self.excluded(path) or not path.lower().endswith((".md", ".markdown")):
                    continue
                parts = PurePosixPath(path).parts
                for index, part in enumerate(parts[:-1]):
                    if part == "docs" and not self.registered(path):
                        found.add("/".join(parts[:index + 1]))
            others = sorted(
                p for p in self.snapshot.files()
                if p.lower().endswith((".md", ".markdown")) and not self.excluded(p) and not self.registered(p)
                and not any(under(p, d) for d in found)
            )
            self._unregistered = (found, others)
        return self._unregistered[0]

    def unregistered_markdown(self) -> list[str]:
        """Markdown files that no root, module, page or entry point covers, so `check` does not read them."""
        self.unregistered_docs()
        return self._unregistered[1]

    def legacy(self) -> tuple[str, set[str]] | None:
        """(commit ID, files under the root at that commit) for adopted_at, or None when it is not set.

        Paths that already existed when the project adopted the rules are legacy: rules that
        would otherwise force rewriting old pages apply only to files added later.
        """
        if self.adopted_at is None:
            return None
        if self._legacy is None:
            result = subprocess.run(["git", "-C", str(self.snapshot.root), "rev-parse", "--verify", "-q", f"{self.adopted_at}^{{commit}}"],
                                    capture_output=True, check=False)
            if result.returncode:
                raise WikiError(f"adopted_at '{self.adopted_at}' is not a commit in this Git repository; legacy files cannot be told apart")
            commit = result.stdout.decode("ascii").strip()
            names = run_git(self.snapshot.root, "ls-tree", "-r", "--name-only", "-z", commit)
            self._legacy = (commit, {n.decode("utf-8", "surrogateescape") for n in names.split(b"\0") if n})
        return self._legacy

    def is_legacy(self, path: str) -> bool:
        legacy = self.legacy()
        return legacy is not None and path in legacy[1]

    def _requirements_config(self, raw: object) -> list[dict]:
        """One object is a single ID space; an array gives each named group its own ID space."""
        if raw is None:
            return []
        if isinstance(raw, dict):
            items, label = [raw], lambda _: "requirements"
        elif isinstance(raw, list) and raw and all(isinstance(x, dict) for x in raw):
            items, label = raw, lambda item: f"requirements[{item.get('name')}]"
        else:
            raise WikiError("requirements must be an object, or a non-empty array of objects with a name each")
        groups: list[dict] = []
        names: set[str] = set()
        for item in items:
            where = label(item)
            unknown = sorted(set(item) - {"name", "files", "tests", "id_pattern", "docs"})
            if unknown:
                raise WikiError(f"{where} has unknown fields: " + ", ".join(unknown))
            name = item.get("name")
            if isinstance(raw, list):
                if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+", name) or name in names:
                    raise WikiError("Each requirements group needs a unique name made of letters, digits, '.', '_' or '-'")
                names.add(name)
            elif name is not None and (not isinstance(name, str) or not name):
                raise WikiError("requirements.name must be a non-empty string")
            id_pattern = item.get("id_pattern", DEFAULT_REQUIREMENT_ID)
            if not isinstance(id_pattern, str) or not id_pattern.strip():
                raise WikiError(f"{where}.id_pattern must be a non-empty regular expression string")
            try:
                patterns = requirement_patterns(id_pattern)
            except re.error as exc:
                raise WikiError(f"{where}.id_pattern is not a valid regular expression: {exc}") from exc
            if re.fullmatch(f"(?:{id_pattern})", ""):
                raise WikiError(f"{where}.id_pattern must not match an empty string")
            groups.append({
                "name": name,
                "files": self._patterns(item.get("files"), f"{where}.files", required=True),
                "tests": self._patterns(item.get("tests", DEFAULT_REQUIREMENT_TESTS), f"{where}.tests"),
                "docs": self._patterns(item.get("docs", []), f"{where}.docs"),
                "id_pattern": id_pattern,
                "patterns": patterns,
            })
        return groups

    def is_requirement_file(self, path: str) -> bool:
        return any(matches(path, group["files"]) for group in self.requirement_groups)

    def _verify_config(self, raw: object) -> dict | None:
        if raw is None:
            return None
        if not isinstance(raw, dict) or set(raw) - {"commands", "files"}:
            raise WikiError("verify may only contain commands and files")
        commands = raw.get("commands")
        if not isinstance(commands, list) or not commands or not all(isinstance(c, dict) for c in commands):
            raise WikiError("verify.commands must be a non-empty array of objects")
        names: set[str] = set()
        for command in commands:
            name = command.get("name")
            fields = {"name", "run", "cwd", "env", "timeout", "summary_lines", "summary_from", "kind"}
            if set(command) - fields:
                raise WikiError(f"verify.commands[{name}] has unknown fields: " + ", ".join(sorted(set(command) - fields)))
            if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+", name) or name in names:
                raise WikiError("Each verify command needs a unique name made of letters, digits, '.', '_' or '-'")
            names.add(name)
            run = command.get("run")
            if not isinstance(run, list) or not run or not all(isinstance(x, str) and x for x in run):
                raise WikiError(f"verify.commands[{name}].run must be a non-empty array of strings; commands do not go through a shell")
            cwd = command.get("cwd", ".")
            try:
                command["cwd"] = "." if cwd == "." else relative_path(cwd)
            except WikiError as exc:
                raise WikiError(f"verify.commands[{name}].cwd must stay inside the project root: {exc}") from exc
            env = command.get("env", {})
            if not isinstance(env, dict) or not all(isinstance(k, str) and k and isinstance(v, str) for k, v in env.items()):
                raise WikiError(f"verify.commands[{name}].env must map names to strings")
            command["env"] = env
            command["timeout"] = self._positive(command.get("timeout", 900), f"verify.commands[{name}].timeout")
            command["summary_lines"] = self._positive(command.get("summary_lines", 2), f"verify.commands[{name}].summary_lines")
            command["summary_from"] = command.get("summary_from", "end")
            if command["summary_from"] not in {"start", "end"}:
                raise WikiError(f"verify.commands[{name}].summary_from must be 'start' or 'end'")
            kind = command.get("kind")
            if kind is not None and kind not in {"test", "check", "build"}:
                raise WikiError(f"verify.commands[{name}].kind must be 'test', 'check' or 'build'")
        return {"commands": commands, "files": self._patterns(raw.get("files", []), "verify.files")}

    def _reconcile_config(self, raw: object) -> list[dict]:
        if not isinstance(raw, list) or not all(isinstance(x, dict) for x in raw):
            raise WikiError("reconcile must be an array of objects")
        names: set[str] = set()
        allowed = {"name", "docs", "doc_pattern", "code", "code_pattern", "ignore"}
        for item in raw:
            name = item.get("name")
            if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+", name) or name in names:
                raise WikiError("Each reconcile entry needs a unique name made of letters, digits, '.', '_' or '-'")
            names.add(name)
            if set(item) - allowed:
                raise WikiError(f"reconcile[{name}] has unknown fields: " + ", ".join(sorted(set(item) - allowed)))
            item["docs"] = self._patterns(item.get("docs"), f"reconcile[{name}].docs", required=True)
            item["code"] = self._patterns(item.get("code"), f"reconcile[{name}].code", required=True)
            for key in ("doc_pattern", "code_pattern"):
                try:
                    pattern = re.compile(item.get(key) if isinstance(item.get(key), str) else "", re.M)
                except re.error as exc:
                    raise WikiError(f"reconcile[{name}].{key} is not a valid regular expression: {exc}") from exc
                if not item.get(key) or pattern.groups > 1:
                    raise WikiError(f"reconcile[{name}].{key} must be a regular expression with at most one capture group")
                item[key] = pattern
            ignore = item.get("ignore", [])
            if not isinstance(ignore, list) or not all(isinstance(x, str) for x in ignore):
                raise WikiError(f"reconcile[{name}].ignore must be an array of names")
            item["ignore"] = set(ignore)
        return raw

    def status(self, path: str) -> tuple[str | None, str | None]:
        """Return (raw value, class); class is None when unrecognised.

        The status line wins. Only when the project registers status_frontmatter and the
        page has no status line does the frontmatter field supply the value.
        """
        if path not in self._status_cache:
            try:
                text = self.snapshot.text(path)
            except WikiError:
                text = ""
            raw = status_value(text)
            front = frontmatter_value(text, self.status_frontmatter) if self.status_frontmatter else None
            self._frontmatter_status[path] = front
            if raw is None:
                raw = front
            self._status_cache[path] = (raw, self.status_values.get(raw.lower()) if raw else None)
        return self._status_cache[path]

    def frontmatter_status(self, path: str) -> tuple[str | None, str | None]:
        """(raw value, class) of the registered frontmatter field, read alongside status()."""
        self.status(path)
        front = self._frontmatter_status.get(path)
        return front, self.status_values.get(front.lower()) if front else None

    def verification_of(self, path: str) -> dict:
        if path not in self._verify_cache:
            try:
                self._verify_cache[path] = verification_header(self.snapshot.text(path))
            except WikiError:
                self._verify_cache[path] = verification_header("")
        return self._verify_cache[path]

    def is_decision(self, path: str) -> bool:
        return matches(path, self.decision_patterns)

    def is_plan(self, path: str) -> bool:
        return matches(path, self.plan_patterns)

    def is_entry(self, path: str) -> bool:
        return path in self.entrypoints or self.page_map.get(path, {}).get("role") == "entry"

    @staticmethod
    def _patterns(value: object, name: str, required: bool = False) -> list[str]:
        if not isinstance(value, list) or not all(isinstance(x, str) and x for x in value) or (required and not value):
            raise WikiError(f"{name} must be {'a non-empty' if required else 'an'} array of path patterns")
        return value

    def _size_limits_config(self, raw: object) -> dict:
        """Thresholds for page-oversized and line-oversized; omitted fields use the defaults."""
        if raw is None:
            return {"page_chars": DEFAULT_PAGE_CHARS, "line_chars": DEFAULT_LINE_CHARS, "ignore": []}
        if not isinstance(raw, dict) or set(raw) - {"page_chars", "line_chars", "ignore"}:
            raise WikiError("size_limits may only contain page_chars, line_chars and ignore")
        for key in ("page_chars", "line_chars"):
            if key in raw and (type(raw[key]) is not int or raw[key] < 1):
                raise WikiError(f"size_limits.{key} must be a positive integer")
        return {
            "page_chars": raw.get("page_chars", DEFAULT_PAGE_CHARS),
            "line_chars": raw.get("line_chars", DEFAULT_LINE_CHARS),
            "ignore": self._patterns(raw.get("ignore", []), "size_limits.ignore"),
        }

    def _remind_config(self, raw: object) -> dict | None:
        if raw is None:
            return None
        if not isinstance(raw, dict):
            raise WikiError("remind must be an object")
        unknown = sorted(set(raw) - REMIND_FIELDS)
        if unknown:
            raise WikiError("remind has unknown fields: " + ", ".join(unknown))
        watches = raw.get("watch")
        if not isinstance(watches, list) or not watches or not all(isinstance(w, dict) for w in watches):
            raise WikiError("remind.watch must be a non-empty array of objects")
        names: set[str] = set()
        for watch in watches:
            name = watch.get("name")
            if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+", name):
                raise WikiError("Each remind.watch entry needs a name made of letters, digits, '.', '_' or '-'")
            if name in names:
                raise WikiError(f"remind.watch name registered twice: {name}")
            names.add(name)
            unknown = sorted(set(watch) - WATCH_FIELDS)
            if unknown:
                raise WikiError(f"remind.watch[{name}] has unknown fields: " + ", ".join(unknown))
            watch["code"] = self._patterns(watch.get("code"), f"remind.watch[{name}].code", required=True)
            watch["docs"] = self._patterns(watch.get("docs"), f"remind.watch[{name}].docs", required=True)

        def limits(value: object, name: str, required: bool) -> dict:
            if value is None and not required:
                return {}
            if not isinstance(value, dict) or not value or set(value) - {"commits", "lines"}:
                raise WikiError(f"{name} must be an object with commits and/or lines")
            for key, number in value.items():
                if type(number) is not int or number < 1:
                    raise WikiError(f"{name}.{key} must be a positive integer")
            return value

        return {
            "watch": watches,
            "threshold": limits(raw.get("threshold"), "remind.threshold", True),
            "block_at": limits(raw.get("block_at"), "remind.block_at", False),
            "ask_user": self._patterns(raw.get("ask_user", []), "remind.ask_user"),
            "decisions": self._patterns(raw.get("decisions", DEFAULT_DECISION_PATTERNS), "remind.decisions"),
            "plans": self._patterns(raw.get("plans", DEFAULT_PLAN_PATTERNS), "remind.plans"),
            "inbox": self._patterns(raw.get("inbox", DEFAULT_INBOX_PATTERNS), "remind.inbox"),
            "plan_stale_days": self._positive(raw.get("plan_stale_days", 14), "remind.plan_stale_days"),
            "plan_idle_days": self._positive(raw.get("plan_idle_days", 60), "remind.plan_idle_days"),
        }

    @staticmethod
    def _positive(value: object, name: str) -> int:
        if type(value) is not int or value < 1:
            raise WikiError(f"{name} must be a positive integer")
        return value

    def _records(self, name: str, required: bool = False) -> list[dict]:
        records = self.config.get(name, [])
        if not isinstance(records, list) or not all(isinstance(x, dict) for x in records):
            raise WikiError(f"{name} must be an array of objects")
        if required and not records:
            raise WikiError(f"{name} must not be empty")
        return records

    def _paths(self, name: str, required: bool = False) -> list[str]:
        paths = self.config.get(name, [])
        if not isinstance(paths, list) or (required and not paths):
            raise WikiError(f"{name} must be a non-empty array of paths")
        return [relative_path(p) for p in paths]

    def excluded(self, path: str) -> bool:
        if any(part in DEFAULT_EXCLUDES for part in PurePosixPath(path).parts):
            return True
        return any(
            fnmatch.fnmatchcase(path, pattern)
            or fnmatch.fnmatchcase(path + "/", pattern)
            or (pattern.endswith("/**") and path == pattern[:-3])
            for pattern in self.excludes
        )

    def root_role(self, path: str) -> str | None:
        matching = [r for r in self.roots if under(path, r["path"])]
        if matching:
            return max(matching, key=lambda r: len(r["path"]))["role"]
        return None

    def role(self, path: str) -> str:
        root_role = self.root_role(path)
        if root_role == "archive":
            return "archive"
        return self.page_map.get(path, {}).get("role", root_role or "knowledge")

    def scope(self, path: str) -> str:
        if "scope" in self.page_map.get(path, {}):
            return self.page_map[path]["scope"]
        matching = [m for m in self.modules if under(path, m["path"])]
        return max(matching, key=lambda m: len(m["path"]))["name"] if matching else "project"

    def registered(self, path: str) -> bool:
        return (
            path in self.entrypoints
            or path in self.page_map
            or self.root_role(path) is not None
            or any(path == m["entry"] or (m.get("docs") and under(path, m["docs"])) for m in self.modules)
        )

    def documents(self) -> list[str]:
        return sorted(
            p for p in self.snapshot.files()
            if p.lower().endswith((".md", ".markdown")) and not self.excluded(p) and self.registered(p)
        )

    def archive_roots(self) -> list[str]:
        return [r["path"] for r in self.roots if r["role"] == "archive"]

    def archive_root(self) -> str | None:
        """Primary archive destination; None when the project registers no archive root."""
        roots = self.archive_roots()
        if self.primary_archive:
            return self.primary_archive
        if "archive" in roots:
            return "archive"
        if len(roots) > 1:
            raise WikiError("Set archive_root to select a primary archive destination")
        return roots[0] if roots else None

    def require_archive_root(self) -> str:
        root = self.archive_root()
        if root is None:
            raise WikiError("No archive root is registered; add a root with role archive or set archive_root")
        return root

    def legacy_archives(self) -> list[str]:
        primary = self.archive_root()
        return [p for p in self.archive_roots() if p != primary]


def without_code(text: str, inline: bool = True) -> str:
    output: list[str] = []
    fence: str | None = None
    for line in text.splitlines(keepends=True):
        match = re.match(r"^ {0,3}(`{3,}|~{3,})", line)
        if match and fence is None:
            fence = match.group(1)
            output.append(" " * len(line.rstrip("\r\n")) + line[len(line.rstrip("\r\n")):])
        elif fence is not None:
            if match and match.group(1)[0] == fence[0] and len(match.group(1)) >= len(fence):
                fence = None
            output.append(" " * len(line.rstrip("\r\n")) + line[len(line.rstrip("\r\n")):])
        else:
            output.append(re.sub(r"(`+)(.+?)\1", lambda m: " " * len(m.group()), line) if inline else line)
    return "".join(output)


def target_part(raw: str) -> str:
    raw = raw.strip()
    if raw.startswith("<"):
        end = raw.find(">")
        return raw[1:end] if end >= 0 else raw
    match = re.match(r"((?:\\.|[^\s])+)", raw)
    return re.sub(r"\\([()\[\] ])", r"\1", match.group(1)) if match else ""


_LINK_CACHE: dict[tuple[str, str], tuple[Link, ...]] = {}


def markdown_links(text: str, path: str) -> list[Link]:
    """Links in one document. Several checks read the same page, so each text is parsed once."""
    key = (path, hashlib.sha256(text.encode("utf-8", "surrogatepass")).hexdigest())
    if key not in _LINK_CACHE:
        _LINK_CACHE[key] = tuple(_parse_links(text, path))
    return list(_LINK_CACHE[key])


def _parse_links(text: str, path: str, target_spans: list[tuple[int, int]] | None = None) -> list[Link]:
    clean = without_code(text)
    definitions: dict[str, str] = {}
    defined_at: dict[str, int] = {}
    lines = clean.splitlines(keepends=True)
    def_key = lambda value: " ".join(value.lower().split())
    for number, line in enumerate(lines):
        match = re.match(r"^ {0,3}\[([^\]]+)\]:\s*(.+)", line)
        # A footnote definition ([^name]: note) is not a reference link. Registering it
        # would make the first word of the note a link target.
        if match and not match.group(1).startswith("^"):
            definitions[def_key(match.group(1))] = target_part(match.group(2))
            defined_at[def_key(match.group(1))] = number + 1
            lines[number] = " " * len(line.rstrip("\r\n")) + line[len(line.rstrip("\r\n")):]
    clean = "".join(lines)
    # Line numbers come from a sorted list of newline offsets; counting newlines from the
    # start of the text for every link grows with the square of the page length.
    newlines = [m.start() for m in re.finditer("\n", clean)]
    found: list[Link] = []
    i = 0
    while i < len(clean):
        start = clean.find("[", i)
        if start < 0:
            break
        if start and clean[start - 1] == "\\":
            i = start + 1
            continue
        end, depth = start + 1, 1
        while end < len(clean) and depth:
            if clean[end] == "\\":
                end += 2
                continue
            depth += (clean[end] == "[") - (clean[end] == "]")
            end += 1
        if depth:
            break
        label = clean[start + 1:end - 1]
        line = bisect.bisect_left(newlines, start) + 1
        if end < len(clean) and clean[end] == "(":
            cursor, depth = end + 1, 1
            while cursor < len(clean) and depth:
                if clean[cursor] == "\\":
                    cursor += 2
                    continue
                depth += (clean[cursor] == "(") - (clean[cursor] == ")")
                cursor += 1
            if not depth:
                raw = clean[end + 1:cursor - 1]
                found.append(Link(target_part(raw), line))
                if target_spans is not None:
                    target = re.match(r"\s*(<[^>]*>|(?:\\.|[^\s])+)", raw)
                    if target:
                        target_spans.append((end + 1 + target.start(1), end + 1 + target.end(1)))
                i = cursor
                continue
        elif end < len(clean) and clean[end] == "[":
            ref_end = clean.find("]", end + 1)
            if ref_end >= 0:
                key = def_key(clean[end + 1:ref_end] or label)
                found.append(Link(definitions.get(key, key), line, "markdown" if key in definitions else "missing-reference", defined_at.get(key)))
                i = ref_end + 1
                continue
        elif def_key(label) in definitions:
            found.append(Link(definitions[def_key(label)], line, "markdown", defined_at[def_key(label)]))
        i = end
    if PurePosixPath(path).name in {"AGENTS.md", "CLAUDE.md"}:
        for number, line in enumerate(clean.splitlines(), 1):
            match = re.fullmatch(r"\s*@([^\s]+)\s*", line)
            if match:
                found.append(Link(match.group(1), number, "import"))
    return found


def heading_slug(value: str) -> str:
    value = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", value)
    value = re.sub(r"<[^>]+>", "", value).replace("`", "").lower().strip()
    value = "".join(c for c in value if c in "_-" or c.isspace() or unicodedata.category(c)[0] in "LN")
    return re.sub(r"\s", "-", value)


def anchors(text: str) -> set[str]:
    clean = without_code(text, inline=False)
    result = set(re.findall(r"<(?:a|[a-z][\w-]*)\b[^>]*\b(?:id|name)=[\"']([^\"']+)[\"']", clean, re.I))
    counts: dict[str, int] = {}
    heading_ids: set[str] = set()
    for line in clean.splitlines():
        match = re.match(r"^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$", line)
        if match:
            slug = heading_slug(match.group(1))
            count = counts.get(slug, 0)
            candidate = slug if count == 0 else f"{slug}-{count}"
            while candidate in heading_ids:
                count += 1
                candidate = f"{slug}-{count}"
            counts[slug] = count + 1
            heading_ids.add(candidate)
            result.add(candidate)
    return result


def resolve_link(source: str, target: str) -> tuple[str | None, str, str | None]:
    """Return local path, fragment, or an unsupported-path reason."""
    parsed = urlsplit(target)
    if parsed.netloc or (parsed.scheme and parsed.scheme != "file"):
        return None, "", None
    if parsed.scheme == "file" or target.startswith("/") or "\\" in target:
        return None, "", "Machine-absolute or root-absolute link; use a portable relative path"
    path = unquote(parsed.path)
    path = re.sub(r":\d+(?::\d+)?$", "", path)
    resolved = posixpath.normpath(posixpath.join(posixpath.dirname(source), path)) if path else source
    if resolved == ".." or resolved.startswith("../"):
        return None, "", "Link leaves the selected project root"
    return resolved, unquote(parsed.fragment), None


LEAVES_ROOT = "Link leaves the selected project root"


def external_path(source: str, target: str) -> str | None:
    """Normalised path of a relative link that leaves the project root, else None."""
    parsed = urlsplit(target)
    path = re.sub(r":\d+(?::\d+)?$", "", unquote(parsed.path))
    resolved = posixpath.normpath(posixpath.join(posixpath.dirname(source), path)) if path else source
    return resolved if resolved == ".." or resolved.startswith("../") else None


def link_fix_hint(wiki: "Wiki", source: str, target: str) -> str:
    """Suggest the link text that resolves when one '../' too many or too few was written.

    Moving a document or copying a link from another directory often leaves the depth wrong,
    and an editor shows no sign of it. Only candidates that point at something that exists count.
    """
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc or target.startswith("/") or "\\" in target:
        return ""
    path = re.sub(r":\d+(?::\d+)?$", "", unquote(parsed.path))
    if not path:
        return ""
    candidates = []
    if path.startswith("../"):
        candidates.append(path[3:])
        if path[3:].startswith("../"):
            candidates.append(path[6:])
    candidates.append("../" + path)
    base = posixpath.dirname(source)
    for candidate in candidates:
        resolved = posixpath.normpath(posixpath.join(base, candidate))
        if resolved == ".." or resolved.startswith("../"):
            hit = wiki.repo_for(resolved)
            found = bool(hit and hit[1] and hit[0].status() == "checked-out" and hit[0].exists(hit[1]))
        else:
            found = resolved != "." and wiki.snapshot.exists(resolved)
        if found:
            suffix = f"#{parsed.fragment}" if parsed.fragment else ""
            return f" Written as '{candidate}{suffix}' it would resolve to {resolved}."
    return ""


def repo_link_findings(wiki: "Wiki", view: RepoView, rel: str, fragment: str, link: "Link", path: str,
                       severity: str, anchor_cache: dict[str, set[str]]) -> list[Finding]:
    """Check one link into a registered repository; count it as unchecked when that repository cannot be read."""
    if view.status() != "checked-out":
        view.links_unchecked += 1
        return []
    view.links_checked += 1
    if not view.exists(rel):
        return [Finding("broken-link", severity, path,
                        f"Target does not exist with exact case in repository {view.name}: {link.target}", link.line, link.target)]
    if fragment and rel.lower().endswith((".md", ".markdown")) and view.is_file(rel):
        key = f"{view.name}\0{rel}"
        try:
            if key not in anchor_cache:
                anchor_cache[key] = anchors(view.text(rel))
            if fragment not in anchor_cache[key]:
                return [Finding("broken-anchor", severity, path,
                                f"ATX/explicit anchor does not exist in repository {view.name}: {link.target}", link.line, link.target)]
        except WikiError as exc:
            return [Finding("unreadable-target", severity, path, str(exc), link.line, link.target)]
    return []


def naming_findings_for_path(path: str) -> list[Finding]:
    """Check naming properties that affect safe moves or create warnings."""
    findings: list[Finding] = []
    parts = PurePosixPath(path).parts
    unsafe = [part for part in parts if UNSAFE_NAME.search(part)]
    if unsafe:
        findings.append(Finding("name-unsafe-character", "error", path,
                                "New document paths cannot contain spaces, '#', '%', or parentheses; archive link rewriting cannot safely preserve them"))
    name = PurePosixPath(path).name
    stem = PurePosixPath(name).stem
    if stem.lower() in NAME_PLACEHOLDERS:
        findings.append(Finding("name-template-placeholder", "warning", path,
                                f"'{name}' is a template placeholder; rename the copied document to a descriptive topic"))
    if NAME_STATUS_OR_VERSION.search(stem):
        findings.append(Finding("name-status-or-version", "warning", path,
                                "The filename contains a status or version token; keep status in the document and use a stable topic name when possible"))
    return findings


def naming_check(wiki: Wiki) -> list[Finding]:
    findings: list[Finding] = []
    names: dict[tuple[str, str], str] = {}
    documents = set(wiki.documents())
    for path in sorted(documents):
        key = (posixpath.dirname(path).casefold(), PurePosixPath(path).name.casefold())
        prior = names.get(key)
        if prior and prior != path:
            findings.append(Finding("name-case-collision", "error", path,
                                    f"Document name differs from {prior} only by case; case-insensitive filesystems cannot keep both paths", key=prior))
        else:
            names[key] = path
    snap = wiki.snapshot
    if snap.staged:
        # Archiving and moving show up as added paths. A path under an archive
        # root keeps its original name on purpose, and a file whose unchanged
        # content left another directory under the same file name is a move.
        # Neither is a newly chosen name; a rename to a different name is.
        removed: dict[str, set[str]] = {}
        for path, (_, oid) in snap.head_entries.items():
            if path not in snap.entries:
                removed.setdefault(oid, set()).add(PurePosixPath(path).name)
        for path in sorted(snap.added_files & documents):
            if wiki.root_role(path) == "archive" or PurePosixPath(path).name in removed.get(snap.entries[path][1], set()):
                continue
            findings.extend(naming_findings_for_path(path))
    return findings


def unchecked_repo_findings(wiki: Wiki) -> list[Finding]:
    """One warning per repository or submodule whose links could not be followed; incomplete under --require-repos."""
    findings: list[Finding] = []
    missing: list[str] = []
    for view in wiki.repo_views:
        status = view.status()
        if status != "checked-out":
            missing.append(f"{view.name} ({view.path})")
        if view.links_unchecked:
            reason = ("the directory is missing or empty" if status == "not-checked-out"
                      else "it has no HEAD commit that a staged check could read")
            findings.append(Finding("repo-not-checked-out", "warning", wiki.config_path,
                                    f"{view.links_unchecked} links into repository {view.name} were not checked: {reason} at {view.path}. "
                                    "Check it out there to have them verified.", key=view.name))
    for sub, count in sorted(wiki.submodule_unchecked.items()):
        reason = ("a staged check reads only the index, which records the submodule's commit but not its files"
                  if wiki.snapshot.staged else f"it is not checked out; run git submodule update --init {sub}")
        findings.append(Finding("repo-not-checked-out", "warning", sub, f"{count} links into submodule {sub} were not checked: {reason}.", key=sub))
        missing.append(f"submodule {sub}")
    if wiki.require_repos and missing:
        raise WikiError("--require-repos: cannot read " + ", ".join(missing) + "; links into them were not verified")
    return findings


def check(wiki: Wiki) -> list[Finding]:
    findings: list[Finding] = []
    snap = wiki.snapshot
    required = set(wiki.entrypoints) | set(wiki.page_map)
    for module in wiki.modules:
        required.add(module["entry"])
        if "docs" in module:
            required.add(module["docs"])
    for root in wiki.roots:
        if root["role"] != "archive":
            required.add(root["path"])
    for path in sorted(required):
        if wiki.excluded(path):
            findings.append(Finding("required-excluded", "error", path, "Required path is excluded by configuration"))
        elif not snap.exists(path):
            findings.append(Finding("missing-entry", "error", path, "Required path is missing from this snapshot"))
    findings.extend(naming_check(wiki))
    for path in sorted(wiki.unregistered_docs()):
        findings.append(Finding("unregistered-docs", "error", path, "Documentation root is not registered or explicitly excluded"))
    unregistered = wiki.unregistered_markdown()
    if unregistered:
        listed = ", ".join(unregistered[:20]) + (f", and {len(unregistered) - 20} more" if len(unregistered) > 20 else "")
        findings.append(Finding("unregistered-markdown", "warning", wiki.config_path,
                                f"{len(unregistered)} Markdown files are outside every registered root, module, page and entry point, so check "
                                f"does not read their links or status: {listed}. Register their directory or exclude it.", key=str(len(unregistered))))
    if snap.staged:
        added_documents = sorted(set(wiki.documents()) & snap.added_files)
        added_current = [p for p in added_documents if wiki.role(p) != "archive"]
        if added_current and wiki.archive_root():
            for path in added_current:
                historical = f"{wiki.archive_root()}/{path}"
                if snap.is_file(historical):
                    findings.append(Finding("historical-path-match", "warning", path, f"New in the captured index relative to captured HEAD; history exists at {historical}. Distinguish restoration, continuation, and a new topic."))
            if wiki.legacy_archives():
                findings.append(Finding("legacy-history-unmatched", "warning", wiki.config_path, "Added-path matching covers the primary archive only; legacy roots are classified and checked but their old layouts are not inferred: " + ", ".join(wiki.legacy_archives())))
    current_ids: dict[tuple[str, str], str] = {}
    for page in wiki.pages:
        path = page["path"]
        if page.get("id") and wiki.role(path) != "archive":
            key = (wiki.scope(path), page["id"])
            if key in current_ids:
                findings.append(Finding("duplicate-current-id", "error", path, f"Current ID also belongs to {current_ids[key]} in scope {key[0]}"))
            current_ids[key] = path
    anchor_cache: dict[str, set[str]] = {}
    for path in wiki.documents():
        severity = "warning" if wiki.role(path) == "archive" else "error"
        try:
            text = snap.text(path)
        except WikiError as exc:
            findings.append(Finding("unreadable", severity, path, str(exc)))
            continue
        budget = wiki.page_map.get(path, {}).get("max_chars")
        if budget and len(text) > budget:
            findings.append(Finding("page-budget", severity, path, f"{len(text)} characters exceeds max_chars={budget}"))
        findings.extend(size_findings(wiki, path, text, budget))
        for link in markdown_links(text, path):
            if link.kind == "missing-reference":
                findings.append(Finding("missing-reference", severity, path, f"Undefined reference link: {link.target}", link.line))
                continue
            if link.kind == "import" and (urlsplit(link.target).scheme or urlsplit(link.target).netloc):
                findings.append(Finding("unsupported-import", severity, path, f"Only a local standalone @path import is supported: {link.target}", link.line))
                continue
            target, fragment, problem = resolve_link(path, link.target)
            if problem:
                external = external_path(path, link.target) if problem == LEAVES_ROOT and link.kind != "import" else None
                hit = wiki.repo_for(external) if external else None
                if hit:
                    # resolve_link returns no fragment for a link that leaves the root, so read it from the link.
                    findings.extend(repo_link_findings(wiki, hit[0], hit[1], unquote(urlsplit(link.target).fragment),
                                                       link, path, severity, anchor_cache))
                    continue
                # Imports form a required loading edge; unsupported imports fail closed.
                level = severity if link.kind == "import" or problem == LEAVES_ROOT else "warning"
                message = f"{problem}: {link.target}"
                # The baseline identifies this finding by the original message, so the hint stays out of the key.
                key = message
                if problem == LEAVES_ROOT:
                    message += "." + (link_fix_hint(wiki, path, link.target) or " To link into another repository, register it under repos in the configuration.")
                findings.append(Finding("unsupported-link", level, path, message, link.line, key))
                continue
            if target is None:
                continue
            if target in snap.submodule_paths():
                continue  # The submodule directory itself exists; nothing inside it is read.
            sub = snap.unreadable_submodule(target)
            if sub is not None:
                wiki.submodule_unchecked[sub] = wiki.submodule_unchecked.get(sub, 0) + 1
                continue
            if not snap.exists(target):
                hint = link_fix_hint(wiki, path, link.target)
                findings.append(Finding("broken-link", severity, path,
                                        f"Target does not exist with exact case: {link.target}" + ("." + hint if hint else ""),
                                        link.line, link.target))
                continue
            if fragment and target.lower().endswith((".md", ".markdown")):
                try:
                    if target not in anchor_cache:
                        anchor_cache[target] = anchors(snap.text(target))
                    if fragment not in anchor_cache[target]:
                        findings.append(Finding("broken-anchor", severity, path, f"ATX/explicit anchor does not exist: {link.target}", link.line, link.target))
                except WikiError as exc:
                    findings.append(Finding("unreadable-target", severity, path, str(exc), link.line, link.target))
            if (severity == "error" and not wiki.is_decision(path) and snap.is_file(target)
                    and target.lower().endswith((".md", ".markdown")) and wiki.role(target) != "archive"):
                raw, kind = wiki.status(target)
                if kind in {"superseded", "retired"}:
                    level = "error" if wiki.is_entry(path) else "warning"
                    findings.append(Finding("links-replaced-document", level, path,
                                            f"Links to a document whose status is '{raw}': {link.target}. Link its replacement or the current design instead.",
                                            link.line, link.target))
    findings.extend(unchecked_repo_findings(wiki))
    for path in wiki.documents():
        if wiki.role(path) == "archive" or not (wiki.is_decision(path) or wiki.is_plan(path)):
            continue
        raw, kind = wiki.status(path)
        if raw is None and not wiki.is_legacy(path):
            findings.append(Finding("status-missing", "warning", path, "Plans and decision records need a status line right below the title, e.g. '状态：执行中。'"))
        elif kind is None:
            findings.append(Finding("status-unknown", "warning", path, f"Status '{raw}' is not recognised; add it to status_values in the configuration or use a listed value", key=raw))
        front, front_kind = wiki.frontmatter_status(path)
        if front is not None and front != raw:
            # Both the status line and the registered frontmatter field are written.
            field = wiki.status_frontmatter
            if front_kind is None:
                findings.append(Finding("status-unknown", "warning", path,
                                        f"Frontmatter {field} '{front}' is not recognised; add it to status_values or use a listed value", key=front))
            elif kind is not None and front_kind != kind:
                findings.append(Finding("status-mismatch", "warning", path,
                                        f"Status line says '{raw}' ({kind}) but frontmatter {field} says '{front}' ({front_kind}). "
                                        "Make them agree, or drop one; the status line is what the tools use", key=f"{field}:{front}"))
        if wiki.is_plan(path):
            owner = status_owner(snap.text(path))
            if owner and not ACTOR.match(owner):
                findings.append(Finding("owner-format", "error", path, f"Owner '{owner}' must be written as human:<name> or agent:<name>", key=owner))
            if kind == "blocked" and not wiki.is_legacy(path) and not status_reason(snap.text(path)):
                findings.append(Finding("plan-blocked-no-reason", "warning", path,
                                        "[agent] Plan status is '阻塞' (blocked) but the status line gives no reason. "
                                        "Write what it is waiting for, e.g. '状态：阻塞。原因：等待 X 的接口评审。'"))
            if kind == "current" and not wiki.is_legacy(path):
                findings.append(Finding("plan-status-current", "warning", path,
                                        "A plan describes work that ends; '当前有效' (current) is for decisions, not plans. "
                                        "Mark finished work '已完成' or superseded work '被替代'. If the work has landed and this page "
                                        "still describes current behaviour, write the lasting content back into docs/ and archive the plan."))
    findings.extend(entry_duplicate_check(wiki))
    findings.extend(orphan_check(wiki))
    findings.extend(review_check(wiki))
    findings.extend(budget_check(wiki))
    findings.extend(trace_check(wiki))
    findings.extend(verification_check(wiki))
    findings.extend(reconcile_check(wiki))
    return findings


def orphan_check(wiki: Wiki) -> list[Finding]:
    """Current knowledge pages and assets that no current document links to."""
    snap = wiki.snapshot
    current = [p for p in wiki.documents() if wiki.role(p) != "archive"]
    linked: set[str] = set()
    for source in current:
        try:
            text = snap.text(source)
        except WikiError:
            continue
        for link in markdown_links(text, source):
            target, _, problem = resolve_link(source, link.target)
            if target and not problem and target != source:
                linked.add(target)
    linked_dirs = {t for t in linked if not snap.is_file(t)}
    reached = lambda p: p in linked or any(under(p, d) for d in linked_dirs)
    exempt = set(wiki.entrypoints) | {m["entry"] for m in wiki.modules}
    findings: list[Finding] = []
    for path in current:
        if (path in exempt or wiki.is_entry(path) or wiki.role(path) in {"process", "redirect"}
                or matches(path, wiki.orphan_ignore) or reached(path) or wiki.is_legacy(path)):
            continue
        findings.append(Finding("orphan-page", "warning", path,
                                "[agent] No current document links to this page; link it from the matching index or entry, merge it into another page, or archive it"))
    asset_roots = [r["path"] for r in wiki.roots if r["role"] == "knowledge"] + [m["docs"] for m in wiki.modules if m.get("docs")]
    tool_files = {wiki.config_path, wiki.generated_index, wiki.baseline_path}
    for path in sorted(snap.files()):
        if (path.lower().endswith((".md", ".markdown")) or path in tool_files or wiki.excluded(path)
                or any(part.startswith(".") for part in PurePosixPath(path).parts)
                or not any(under(path, r) for r in asset_roots) or wiki.root_role(path) == "archive"
                or matches(path, wiki.orphan_ignore) or reached(path) or wiki.is_legacy(path)):
            continue
        findings.append(Finding("orphan-asset", "warning", path,
                                "[agent] No current document links to this file; link it where it is used and say what it shows, or delete it"))
    return findings


def reconcile_names(wiki: Wiki, patterns: list[str], regex: re.Pattern) -> tuple[list[str], dict[str, tuple[str, int]]]:
    """Return matched files and the first location of each extracted name."""
    files = sorted(p for p in wiki.snapshot.files() if matches(p, patterns) and wiki.snapshot.is_file(p))
    found: dict[str, tuple[str, int]] = {}
    for path in files:
        text = wiki.snapshot.text(path)
        for match in regex.finditer(text):
            name = match.group(1) if regex.groups else match.group(0)
            if name and name not in found:
                found[name] = (path, text.count("\n", 0, match.start()) + 1)
    return files, found


def reconcile_check(wiki: Wiki) -> list[Finding]:
    """Compare a set of names written in documents with the same set extracted from code."""
    findings: list[Finding] = []
    for item in wiki.reconcile:
        name = item["name"]
        doc_files, in_docs = reconcile_names(wiki, item["docs"], item["doc_pattern"])
        code_files, in_code = reconcile_names(wiki, item["code"], item["code_pattern"])
        empty = [side for side, files, names in (("docs", doc_files, in_docs), ("code", code_files, in_code)) if not files or not names]
        if empty:
            findings.append(Finding("reconcile-empty", "error", wiki.config_path,
                                    f"reconcile[{name}]: no files or no names matched on the {' and '.join(empty)} side; check the paths and patterns", key=name))
            continue
        for value in sorted(set(in_code) - set(in_docs) - item["ignore"]):
            path, line = in_code[value]
            findings.append(Finding("reconcile-missing-in-doc", "error", doc_files[0],
                                    f"[agent] reconcile[{name}]: '{value}' appears in {path}:{line} but in none of the documents; describe it or add it to ignore",
                                    key=f"{name}:{value}"))
        for value in sorted(set(in_docs) - set(in_code) - item["ignore"]):
            path, line = in_docs[value]
            findings.append(Finding("reconcile-missing-in-code", "error", path,
                                    f"[agent] reconcile[{name}]: '{value}' is documented but no longer appears in code; remove or correct it, or add it to ignore",
                                    line, key=f"{name}:{value}"))
    return findings


def verification_check(wiki: Wiki) -> list[Finding]:
    """Date-independent checks of 核对 lines; due dates are reported by remind."""
    findings: list[Finding] = []
    for path in wiki.documents():
        if wiki.role(path) == "archive":
            continue
        info = wiki.verification_of(path)
        for line, message in info["problems"]:
            findings.append(Finding("verify-format", "error", path, message, line))
        if info["review_by"] and info["entries"]:
            latest = max(date for _, _, date in info["entries"])
            if info["review_by"] < latest:
                findings.append(Finding("verify-review-before-check", "error", path,
                                        f"复核期限 {info['review_by']} is earlier than the latest check {latest}", info["review_line"]))
        if matches(path, wiki.verification["review_by_required"]) and info["review_by"] is None:
            findings.append(Finding("review-by-missing", "warning", path, "This path requires a 复核期限 line below the title"))
        if matches(path, wiki.verification["human_required"]) and not any(kind == "human" for kind, _, _ in info["entries"]):
            if not matches(path, wiki.review_patterns) and not wiki.is_plan(path):
                findings.append(Finding("verify-no-human", "warning", path,
                                        "This path requires a human check; add '核对：human:<name>，YYYY-MM-DD' after a person has reviewed it"))
    return findings


def parse_requirements(wiki: Wiki, group: dict) -> tuple[dict[str, dict], list[Finding]]:
    """Requirement headings with their status, acceptance items and evidence, from one group's files."""
    findings: list[Finding] = []
    requirements: dict[str, dict] = {}
    patterns = group["patterns"]
    for path in wiki.documents():
        if wiki.role(path) == "archive" or not matches(path, group["files"]):
            continue
        clean = without_code(wiki.snapshot.text(path), inline=False).splitlines()
        current: dict | None = None
        level = 0
        for number, line in enumerate(clean, 1):
            heading = re.match(r"^(#{1,6})\s", line)
            match = patterns["heading"].match(line)
            if heading and (match or len(heading.group(1)) <= level):
                current = None
            if match:
                level = len(match.group("level"))
                requirement_id = match.group("id")
                if requirement_id in requirements:
                    findings.append(Finding("trace-duplicate-id", "error", path,
                                            f"Requirement {requirement_id} is also defined in {requirements[requirement_id]['path']}", number, requirement_id))
                    continue
                current = {"path": path, "line": number, "status": None, "raw_status": None,
                           "accept": {}, "manual_records": set(), "evidence": False, "seen_body": False}
                requirements[requirement_id] = current
                continue
            if current is None or not line.strip():
                continue
            if not current["seen_body"]:
                current["seen_body"] = True
                status = STATUS_LINE.match(line.strip())
                if status:
                    current["raw_status"] = status.group(1)
                    current["status"] = REQUIREMENT_STATUSES.get(status.group(1).lower())
                    continue
            item = patterns["item"].match(line)
            if item:
                current["accept"][item.group("id")] = bool(MANUAL_MARK.search(line))
            manual = patterns["check"].match(line)
            if manual and manual.group("id") in current["accept"]:
                current["manual_records"].add(manual.group("id"))
            if re.match(rf"^\s*(?:{kw('evidence')})\s*[:：]", line, re.I) and markdown_links(line, path):
                current["evidence"] = True
    return requirements, findings


def trace_check(wiki: Wiki) -> list[Finding]:
    """Check each requirements group in its own ID space; one group per configured object."""
    groups = wiki.requirement_groups
    if len(groups) > 1:
        for path in wiki.documents():
            owners = [g["name"] for g in groups if matches(path, g["files"])]
            if len(owners) > 1 and wiki.role(path) != "archive":
                raise WikiError(f"{path} matches the files of requirements groups {', '.join(owners)}; each requirements file belongs to one group")
    findings: list[Finding] = []
    defined = False
    coverage: list[dict] = []
    for group in groups:
        group_findings, group_coverage = trace_group(wiki, group)
        findings.extend(group_findings)
        if group_coverage is not None:
            defined = True
            coverage.append(group_coverage)
    wiki.requirements_coverage = coverage
    for path in sorted(wiki.snapshot.files()):
        if not wiki.is_requirement_file(path) or wiki.excluded(path):
            continue
        location = wiki.root_role(path)
        if location in {"process", "archive"}:
            findings.append(Finding("trace-requirements-location", "warning", path,
                                    f"Requirements live under a {location} root; they are current knowledge until retired, "
                                    "so keep them under a knowledge root. Relocate the file with `move`.", key=path))
    if not defined:
        return findings
    review_dirs = {posixpath.dirname(p) for p in wiki.documents() if matches(p, wiki.review_patterns)}
    for path in wiki.documents():
        if (wiki.role(path) == "archive" or not wiki.is_plan(path) or any(posixpath.dirname(path) == d for d in review_dirs)
                or wiki.status(path)[1] in {"finished", "superseded", "retired"} or wiki.is_legacy(path)):
            continue
        if not re.search(rf"^(?:{kw('covers_requirements')})\s*[:：]\s*\S", wiki.snapshot.text(path), re.M | re.I):
            findings.append(Finding("trace-plan-scope", "warning", path,
                                    "Plan does not say which requirements it covers; add '覆盖需求：PW-01' or '覆盖需求：无（原因）'"))
    return findings


def trace_group(wiki: Wiki, group: dict) -> tuple[list[Finding], dict | None]:
    """Findings for one group, and its coverage summary (None when the group defines no requirement)."""
    requirements, findings = parse_requirements(wiki, group)
    if not requirements:
        return findings, None
    for requirement_id, item in requirements.items():
        if item["status"] is None:
            message = (f"Requirement {requirement_id} needs a status line below its heading: 状态：提出 / 已接受 / 已实现 / 已验证 / 已废止。"
                       if item["raw_status"] is None else f"Requirement {requirement_id} has unknown status '{item['raw_status']}'")
            findings.append(Finding("trace-status-missing", "error", item["path"], message, item["line"], requirement_id))
        for accept_id in item["accept"]:
            if not accept_id.startswith(requirement_id + "."):
                findings.append(Finding("trace-acceptance-id", "error", item["path"],
                                        f"Acceptance {accept_id} is listed under {requirement_id}; its prefix must match", item["line"], accept_id))
    # Each acceptance ID maps to its requirement; IDs such as FR-1.2.1 cannot be split at the first '.'.
    owner = {r: r for r in requirements} | {a: r for r, item in requirements.items() for a in item["accept"]}
    known = set(owner)
    # Only text sharing a prefix with a defined ID counts as a reference, so UTF-8 or SHA-256 are not read as IDs.
    prefixes = {requirement_prefix(r) for r in requirements}
    reference = group["patterns"]["reference"]
    test_refs: set[str] = set()

    def scan(path: str, text: str, kind: str) -> None:
        for number, line in enumerate(text.splitlines(), 1):
            for found in reference.finditer(line):
                ref = found.group("ref")
                if requirement_prefix(ref) not in prefixes:
                    continue
                if ref not in known:
                    findings.append(Finding("trace-unknown-ref", "error", path, f"References {ref}, which is not a defined requirement or acceptance item", number, ref))
                elif kind == "test":
                    test_refs.add(ref)
                    test_refs.add(owner[ref])

    for path in wiki.documents():
        if wiki.role(path) == "archive":
            continue
        is_requirement_file = matches(path, group["files"])
        # A group with docs patterns reads references only from those pages and its own files,
        # so another product's PRD-001 is not taken as this group's PRD-001.
        if group["docs"] and not is_requirement_file and not matches(path, group["docs"]):
            continue
        text = wiki.snapshot.text(path)
        if is_requirement_file:
            scan(path, text, "requirement")
        elif wiki.root_role(path) == "process":
            scan(path, text, "process")
        else:
            scan(path, text, "design")
    for path in sorted(wiki.snapshot.files()):
        if matches(path, group["tests"]) and not wiki.excluded(path) and wiki.root_role(path) != "archive":
            try:
                scan(path, wiki.snapshot.text(path), "test")
            except WikiError:
                continue
    for requirement_id, item in requirements.items():
        status = item["status"]
        if status in {"accepted", "implemented", "verified"} and not item["accept"]:
            findings.append(Finding("trace-no-acceptance", "warning", item["path"],
                                    f"Requirement {requirement_id} has no acceptance items like '- {requirement_id}.1 …'", item["line"], requirement_id))
        # A 验收核对 record stands in for a test only on items marked （人工验证）; on any other item it
        # would let a written claim replace a test that a command could run.
        not_manual = [a for a, manual in item["accept"].items() if not manual and a in item["manual_records"]]
        if not_manual:
            findings.append(Finding("trace-check-not-manual", "warning", item["path"],
                                    f"Requirement {requirement_id} has 验收核对 records for " + ", ".join(not_manual)
                                    + ", which are not marked （人工验证）. The record does not replace a test: write a test that cites the ID, "
                                    "or mark the item （人工验证） if no command can prove it.", item["line"], requirement_id))
        if status in {"implemented", "verified"}:
            untested = [a for a, manual in item["accept"].items() if not manual and a not in test_refs]
            if untested:
                findings.append(Finding("trace-no-test", "warning", item["path"],
                                        f"Requirement {requirement_id} is {item['raw_status']} but no test references " + ", ".join(untested)
                                        + ". Reference the acceptance ID in a test, or, if no command can prove it, mark it （人工验证） "
                                        "and add '验收核对：<ID>，agent:<name>，YYYY-MM-DD。'.", item["line"], requirement_id))
            missing_manual = [a for a, manual in item["accept"].items()
                              if manual and a not in item["manual_records"]]
            if missing_manual:
                findings.append(Finding("trace-manual-evidence", "warning", item["path"],
                                        f"Requirement {requirement_id} marks " + ", ".join(missing_manual)
                                        + " as manual verification but has no acceptance check record; add '验收核对：<ID>，agent:<name>，YYYY-MM-DD。'.",
                                        item["line"], requirement_id))
        if status == "verified" and not item["evidence"]:
            findings.append(Finding("trace-no-evidence", "warning", item["path"],
                                    f"Requirement {requirement_id} is verified but has no '证据：' line with a link", item["line"], requirement_id))
        # 至少有 2 条验收、状态为已实现或已验证、这些验收全部只有人工验证没有测试引用时提示先写测试。
        if status in {"implemented", "verified"} and len(item["accept"]) >= 2:
            if all(manual and a not in test_refs for a, manual in item["accept"].items()):
                findings.append(Finding("trace-all-manual", "warning", item["path"],
                                        f"Requirement {requirement_id} has {len(item['accept'])} acceptance items, all marked "
                                        "manual verification with no test. Manual verification is for items no command can prove; "
                                        "write tests for the ones that can be tested, and mark the rest 人工验证.",
                                        item["line"], requirement_id))
    accept_total = tested = manual = untested = 0
    status_counts: dict[str, int] = {}
    for item in requirements.values():
        if item["raw_status"]:
            status_counts[item["raw_status"]] = status_counts.get(item["raw_status"], 0) + 1
        for accept_id, is_manual in item["accept"].items():
            accept_total += 1
            if accept_id in test_refs:
                tested += 1
            elif is_manual:
                manual += 1
            else:
                untested += 1
    coverage = {
        "name": group["name"] or "default",
        "requirements": len(requirements),
        "acceptance": accept_total,
        "tested": tested,
        "manual": manual,
        "untested": untested,
        "status": status_counts,
    }
    return findings, coverage


def finding_sections(text: str) -> list[tuple[str, int, list[str]]]:
    """Split a review file into (finding ID, heading line, section lines) at level-two headings."""
    lines = without_code(text, inline=False).splitlines()
    raw = text.splitlines()
    sections: list[tuple[str, int, list[str]]] = []
    current: tuple[str, int, list[str]] | None = None
    for number, line in enumerate(lines, 1):
        if re.match(r"^#{1,2}\s", line):
            if current:
                sections.append(current)
            match = FINDING_HEADING.match(line)
            current = (match.group(1), number, []) if match else None
        elif current is not None:
            current[2].append(raw[number - 1] if number - 1 < len(raw) else line)
    if current:
        sections.append(current)
    return sections


def verification_rows(lines: list[str]) -> list[tuple[str, str]]:
    """(checker, verdict) for each data row of the first table after a '核对' or 'Verification' marker."""
    rows: list[tuple[str, str]] = []
    started = in_table = False
    for line in lines:
        stripped = line.strip()
        if not started:
            started = bool(re.match(rf"^(\*\*)?(?:{kw('check_table')})(\*\*)?\s*$", stripped, re.I))
            continue
        if stripped.startswith("|"):
            in_table = True
            cells = [c.strip() for c in stripped.strip("|").split("|")]
            header = {word.lower() for word in KEYWORDS["verdict_column"]}
            if len(cells) >= 3 and not set("".join(cells)) <= set("-: ") and cells[2].lower() not in header:
                rows.append((cells[1], cells[2]))
        elif in_table:
            break
    return rows


def review_check(wiki: Wiki) -> list[Finding]:
    findings: list[Finding] = []
    documents = wiki.documents()
    for readme in documents:
        if wiki.role(readme) == "archive" or not matches(readme, wiki.review_patterns):
            continue
        directory = posixpath.dirname(readme)
        files = [p for p in documents if posixpath.dirname(p) == directory]
        plans = [p for p in files if PurePosixPath(p).name.startswith("plan-")]
        seen: dict[str, str] = {}
        entries: list[tuple[str, str, int, list[str]]] = []
        for path in files:
            if path in plans:
                continue
            for finding_id, line, body in finding_sections(wiki.snapshot.text(path)):
                if finding_id in seen:
                    findings.append(Finding("review-duplicate-id", "error", path, f"Finding {finding_id} is also defined in {seen[finding_id]}", line, finding_id))
                    continue
                seen[finding_id] = path
                entries.append((finding_id, path, line, body))
        if wiki.is_legacy(readme):
            # A review that existed at adopted_at keeps its own format; only duplicate IDs are checked.
            continue
        covered: set[str] = set()
        for plan in plans:
            text = wiki.snapshot.text(plan)
            match = re.search(rf"^(?:{kw('covers_findings')})\s*[:：]\s*(.+)$", text, re.M | re.I)
            if not match:
                findings.append(Finding("review-plan-scope", "warning", plan, "Plan does not list the findings it covers; add a line '覆盖问题：r01-1、r02-3'"))
                continue
            for finding_id in FINDING_ID.findall(match.group(1)):
                if finding_id not in seen:
                    findings.append(Finding("review-plan-unknown-id", "error", plan, f"Plan covers {finding_id}, which is not a finding in this review", key=finding_id))
                covered.add(finding_id)
        for finding_id, path, line, body in entries:
            first = next((x.strip() for x in body if x.strip()), "")
            status = STATUS_LINE.match(first)
            verdict = VERDICTS.get(status.group(1).lower()) if status else None
            if verdict is None:
                findings.append(Finding("review-verdict-missing", "error", path,
                                        f"Finding {finding_id} needs a verdict line below its heading: 状态：待核对 / 成立 / 收窄 / 不成立 / 已知 / 重复。",
                                        line, finding_id))
                continue
            if verdict == "pending":
                findings.append(Finding("review-unverified", "warning", path, f"Finding {finding_id} has not been verified yet", line, finding_id))
                continue
            section = "\n".join(body)
            rows = verification_rows(body)
            author = re.search(rf"(?:{kw('author')})\s*[:：]\s*([^，,；;）)\s]+)", section, re.I)
            author_name = author.group(1) if author else None
            for checker, _ in rows:
                if ACTOR_PREFIX.match(checker) and not ACTOR.match(checker):
                    findings.append(Finding("review-actor-format", "error", path,
                                            f"Finding {finding_id}: checker '{checker}' must use the human: or agent: prefix", line, finding_id))
            if rows and matches(path, wiki.verification["human_required"]) and not any(c.startswith("human:") for c, _ in rows):
                findings.append(Finding("review-no-human-check", "warning", path,
                                        f"Finding {finding_id} has no human: checker, but this review path requires a human check", line, finding_id))
            if not rows:
                findings.append(Finding("review-check-missing", "warning", path, f"Finding {finding_id} has a verdict but no verification table rows", line, finding_id))
            else:
                if author_name and all(checker == author_name for checker, _ in rows):
                    findings.append(Finding("review-no-independent-check", "warning", path,
                                            f"Finding {finding_id} was only checked by its author {author_name}", line, finding_id))
                last = VERDICTS.get(rows[-1][1].lower())
                if last and last != verdict:
                    findings.append(Finding("review-verdict-mismatch", "warning", path,
                                            f"Finding {finding_id} status says {status.group(1)} but the last verification row says {rows[-1][1]}", line, finding_id))
            if verdict == "duplicate":
                others = [x for x in FINDING_ID.findall(section) if x != finding_id and x in seen]
                if not others:
                    findings.append(Finding("review-duplicate-target", "error", path, f"Duplicate finding {finding_id} must name the finding it duplicates", line, finding_id))
            elif verdict == "known":
                if not any(link.kind == "markdown" for link in markdown_links(section, path)):
                    findings.append(Finding("review-known-target", "error", path, f"Known finding {finding_id} must link to the existing plan, gap or decision", line, finding_id))
            elif verdict in {"confirmed", "narrowed"} and finding_id not in covered:
                if not re.search(rf"^(\*\*)?(?:{kw('plan_section')})(\*\*)?", section, re.M | re.I):
                    findings.append(Finding("review-plan-missing", "warning", path,
                                            f"Finding {finding_id} is {status.group(1)} but has no 方案 section and no plan-*.md covers it", line, finding_id))
    return findings


def size_findings(wiki: Wiki, path: str, text: str, budget: int | None) -> list[Finding]:
    """Default page and line length warnings; reads only the text already loaded."""
    if wiki.role(path) == "archive" or matches(path, wiki.size_limits["ignore"]):
        return []
    findings: list[Finding] = []
    page_chars = wiki.size_limits["page_chars"]
    # A page that registers its own max_chars has already stated its size; page-budget covers it.
    if budget is None and len(text) > page_chars:
        findings.append(Finding("page-oversized", "warning", path,
                                f"{len(text)} characters exceed the default page limit {page_chars}. "
                                "Registration tables should keep only open entries and archive closed ones by the page; "
                                "set max_chars in pages if this length is intended."))
    line_chars = wiki.size_limits["line_chars"]
    long_lines = [(number, length) for number, length in
                  ((i, len(line)) for i, line in enumerate(text.splitlines(), 1)) if length > line_chars]
    if long_lines:
        first_line, first_length = long_lines[0]
        findings.append(Finding("line-oversized", "warning", path,
                                f"Line {first_line} has {first_length} characters, over the default line limit {line_chars}; "
                                f"{len(long_lines)} line(s) in this document are over the limit. "
                                "Write one record per line instead of packing a whole table into one line.",
                                first_line))
    return findings


def entry_duplicate_check(wiki: Wiki) -> list[Finding]:
    """AGENTS.md and CLAUDE.md in the same directory that hold the same content verbatim."""
    snap = wiki.snapshot
    directories: dict[str, dict[str, str]] = {}
    for path in snap.files():
        name = posixpath.basename(path)
        if name in ("AGENTS.md", "CLAUDE.md") and not wiki.excluded(path):
            directories.setdefault(posixpath.dirname(path), {})[name] = path
    findings: list[Finding] = []
    for pair in directories.values():
        if "AGENTS.md" not in pair or "CLAUDE.md" not in pair:
            continue
        try:
            agents = snap.text(pair["AGENTS.md"]).strip()
            claude = snap.text(pair["CLAUDE.md"]).strip()
        except WikiError:
            continue
        if agents == claude and len(agents) > 200:
            findings.append(Finding("entry-duplicate", "warning", pair["CLAUDE.md"],
                                    f"Holds the same content as {pair['AGENTS.md']} verbatim. "
                                    "Keep one copy and replace the other with a single line '@AGENTS.md' import.",
                                    key=pair["AGENTS.md"]))
    return findings


def budget_check(wiki: Wiki) -> list[Finding]:
    if not wiki.dir_budgets:
        return []
    counts: dict[str, int] = {}
    for path in wiki.documents():
        if wiki.is_legacy(path):
            continue  # With adopted_at, only documents added since adoption count.
        directory = posixpath.dirname(path)
        counts[directory] = counts.get(directory, 0) + 1
    findings = []
    for directory, count in sorted(counts.items()):
        # When several patterns match one directory, the most specific (longest) pattern
        # sets the budget; the directory is reported against that one pattern only.
        matching = [(pattern, limit) for pattern, limit in wiki.dir_budgets.items()
                    if fnmatch.fnmatchcase(directory, pattern)]
        if not matching:
            continue
        pattern, limit = max(matching, key=lambda item: len(item[0]))
        if count > limit:
            counted = "documents added since adopted_at" if wiki.adopted_at else "documents"
            findings.append(Finding("dir-budget", "warning", directory,
                                    f"{count} {counted} directly in this directory exceed the budget {limit} for '{pattern}'. "
                                    "Merge small records into sections or archive finished work.", key=pattern))
    return findings


def load_baseline(wiki: Wiki, snapshot: Snapshot) -> set[str]:
    if not wiki.baseline_path or not snapshot.exists(wiki.baseline_path):
        return set()
    try:
        data = json.loads(snapshot.read(wiki.baseline_path))
    except ValueError as exc:
        raise WikiError(f"Invalid JSON in {wiki.baseline_path}: {exc}") from exc
    items = data.get("findings") if isinstance(data, dict) and data.get("version") == 1 else None
    if not isinstance(items, list) or not all(isinstance(x, str) for x in items):
        raise WikiError(f"{wiki.baseline_path} must be {{\"version\": 1, \"findings\": [fingerprint, ...]}}")
    return set(items)


def head_baseline(wiki: Wiki) -> set[str] | None:
    """Baseline recorded at the captured HEAD, or None when HEAD has no baseline file."""
    snap = wiki.snapshot
    if not snap.staged or not snap.head_oid or not wiki.baseline_path:
        return None
    prefix = posixpath.relpath(str(snap.root), str(snap.repository))
    name = wiki.baseline_path if prefix == "." else f"{prefix}/{wiki.baseline_path}"
    result = subprocess.run(["git", "-C", str(snap.repository), "show", f"{snap.head_oid}:{name}"], capture_output=True, check=False)
    if result.returncode:
        return None
    try:
        data = json.loads(result.stdout)
        return set(data.get("findings", []))
    except (ValueError, AttributeError):
        return None


def apply_baseline(wiki: Wiki, findings: list[Finding]) -> tuple[list[Finding], int]:
    """Known findings stop failing; new ones keep their severity; fixed entries ask for a smaller baseline."""
    known = load_baseline(wiki, wiki.snapshot)
    if not known:
        return findings, 0
    current = {f.fingerprint() for f in findings if f.severity == "error"}
    kept = [f for f in findings if not (f.severity == "error" and f.fingerprint() in known)]
    accepted = len(findings) - len(kept)
    fixed = known - current
    if fixed:
        kept.append(Finding("baseline-stale", "warning", wiki.baseline_path,
                            f"{len(fixed)} recorded findings no longer occur. Regenerate the baseline so it can only shrink: "
                            f"python3 scripts/wiki/wiki.py baseline --root . > {wiki.baseline_path}"))
    previous = head_baseline(wiki)
    if previous is not None:
        grown = known - previous
        if grown:
            kept.append(Finding("baseline-grown", "warning", wiki.baseline_path,
                                f"[user] The staged baseline accepts {len(grown)} findings that HEAD did not accept. "
                                "New problems should be fixed, not recorded. Confirm with the user before committing a larger baseline."))
    return kept, accepted


def report(wiki: Wiki, findings: list[Finding], command: str, **extra: object) -> dict:
    errors = sum(f.severity == "error" for f in findings)
    warnings = sum(f.severity == "warning" for f in findings)
    return {
        "command": command,
        "result": "failed" if errors else "passed_with_warnings" if warnings else "passed",
        "tool_version": VERSION,
        "snapshot": wiki.snapshot.identifier,
        "head_baseline": (wiki.snapshot.head_oid or "unborn") if wiki.snapshot.staged else None,
        "config": wiki.config_path,
        "config_sha256": wiki.config_hash,
        "coverage": {
            "roots": wiki.roots,
            "modules": [m["name"] for m in wiki.modules],
            "excluded": wiki.excludes,
            "git_ignored": wiki.snapshot.ignore_source(),
            "adopted_at": wiki.legacy()[0] if wiki.adopted_at else None,
            "unregistered_markdown": {"count": len(wiki.unregistered_markdown()), "paths": wiki.unregistered_markdown()[:20]},
            **({"repos": [view.describe() for view in wiki.repo_views]} if wiki.repo_views else {}),
            **({"submodules": [{"path": sub, "checked_out": wiki.snapshot.submodule_checked_out(sub),
                                "links_unchecked": wiki.submodule_unchecked.get(sub, 0)}
                               for sub in sorted(wiki.snapshot.submodule_paths())]} if wiki.snapshot.submodule_paths() else {}),
            "not_checked": ["prose correctness", "arbitrary Markdown/host syntax", "runtime contract behavior", "external URLs"],
        },
        "errors": errors,
        "warnings": warnings,
        "findings": [asdict(f) for f in findings],
        **extra,
    }


def config_not_committed_report(snapshot: Snapshot, command: str, config_path: str) -> dict:
    """Staged check or reminder when the configuration is in neither the index nor HEAD.

    The project has not committed its adoption yet, so there is nothing to check against a
    committed baseline. Report that nothing was checked, as a warning, and exit 0 so the hook
    does not block a project that has not finished adopting. A configuration present in HEAD but
    removed from the index is a different case (this commit deletes it) and stays incomplete.
    """
    message = (f"[agent] {config_path} is in neither the Git index nor HEAD, so this project has not committed "
               f"its ProjectWiki adoption yet. No staged content was checked; commit the configuration to enable "
               f"staged checks.")
    return {
        "command": command,
        "result": "not_checked",
        "tool_version": VERSION,
        "snapshot": snapshot.identifier,
        "head_baseline": (snapshot.head_oid or "unborn"),
        "config": config_path,
        "coverage": {"not_checked": ["all staged content: the configuration is not committed"]},
        "errors": 0,
        "warnings": 1,
        "findings": [asdict(Finding("config-not-committed", "warning", config_path, message))],
    }


def output_report(result: dict, as_json: bool) -> None:
    if as_json:
        print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
        return
    print(f"{result['command']}: {result['result']} ({result.get('errors', 0)} errors, {result.get('warnings', 0)} warnings)")
    print(f"Snapshot: {result['snapshot']}")
    if result.get("coverage", {}).get("git_ignored") == "not-applied":
        print("Git ignore rules: not applied (no Git work tree, Git unavailable, or the root itself is ignored); every file under the root was read")
    for group in result.get("coverage", {}).get("requirements", []):
        print(f"requirements[{group['name']}]: {group['requirements']} requirements, {group['acceptance']} acceptance items: "
              f"{group['tested']} with tests, {group['manual']} manual only, {group['untested']} untested")
    for item in result.get("findings", []):
        location = item["path"] + (f":{item['line']}" if item.get("line") else "")
        print(f"{item['severity'].upper()} {item['rule']} {location}: {item['message']}")
    for key in ("source", "destination", "source_sha256", "plan_sha256", "history_match", "hooks", "git_hooks", "archive_ignore", "output", "exported", "written", "after_check"):
        if key in result:
            print(f"{key}: {result[key]}")
    for item in result.get("writes") or []:
        print(f"WRITE {item}")
    for item in result.get("watch_status", []):
        print(f"WATCH {item['name']}: {item['commits']} commits, {item['lines']} lines since {item['since'] or 'start of scanned history'}")
    for item in result.get("active_plans", []):
        line = f"PLAN {item['path']}: {item['status']}, owner {item['owner'] or 'not written'}"
        if "reason" in item:
            line += f", reason {item['reason'] or 'not written'}"
        print(line)
    for change in result.get("affected_links", []):
        print(f"LINK {change['path']}:{change['line']}: {change['target']} -> {change['suggested_target']} ({change['note']})")


def index_data(wiki: Wiki) -> dict:
    pages = []
    for path in wiki.documents():
        text = wiki.snapshot.text(path)
        title = re.search(r"^#\s+(.+)$", without_code(text, inline=False), re.M)
        page = {"path": path, "title": title.group(1).strip() if title else PurePosixPath(path).stem, "role": wiki.role(path), "scope": wiki.scope(path)}
        if wiki.page_map.get(path, {}).get("id"):
            page["id"] = wiki.page_map[path]["id"]
        raw, kind = wiki.status(path)
        if raw:
            page["status"] = raw
            if kind:
                page["status_class"] = kind
        info = wiki.verification_of(path)
        if info["entries"]:
            page["trust"] = trust_level(info["entries"])
            page["verified"] = [{"by": f"{k}:{n}", "at": d.isoformat()} for k, n, d in info["entries"]]
        if info["review_by"]:
            page["review_by"] = info["review_by"].isoformat()
        pages.append(page)
    return {"version": 1, "project_name": wiki.config.get("project_name", ""), "pages": pages}


def new_path_report(wiki: Wiki, path: str) -> dict:
    path = relative_path(path)
    findings: list[Finding] = naming_findings_for_path(path)
    if wiki.snapshot.exists(path):
        findings.append(Finding("current-path-occupied", "error", path, "Current path already exists; inspect it before creating another document"))
    root = wiki.archive_root()
    archived = f"{root}/{path}" if root else None
    matched = bool(archived) and wiki.snapshot.is_file(archived)
    if matched:
        findings.append(Finding("historical-path-match", "warning", path, f"History exists at {archived}; distinguish restoration, continuation, and a new topic. This does not reserve the filename forever."))
    if wiki.legacy_archives():
        findings.append(Finding("legacy-history-unmatched", "warning", path, "Exact history matching covers the primary archive only; inspect legacy roots separately: " + ", ".join(wiki.legacy_archives())))
    return report(wiki, findings, "check-new", source=path, history_match=archived if matched else None,
                  history_match_scope={"primary_root": root, "legacy_roots_not_matched": wiki.legacy_archives()})


LINK_TARGET_PREFIX = re.compile(r"(?:\]\(\s*<?|\]:\s*<?|^\s*@)$")


def rewrite_link_targets(text: str, changes: dict[tuple[int, str, str], int]) -> str | None:
    """Replace link targets on the given 1-based lines; None when a target cannot be located exactly.

    `changes` maps (line, old target, new target) to the number of occurrences expected on
    that line. Lines are split on "\\n" only, the same way markdown_links numbers them, so
    every other byte, including "\\r" and text inside code, stays as it was.
    """
    lines = text.split("\n")
    clean = without_code(text).split("\n")
    if len(clean) != len(lines):
        return None
    edits: dict[int, list[tuple[int, str, str]]] = {}
    for (line, old, new), count in changes.items():
        if not 0 < line <= len(lines):
            return None
        original, cleaned = lines[line - 1], clean[line - 1]
        spots = [m.start() for m in re.finditer(re.escape(old), cleaned)
                 if LINK_TARGET_PREFIX.search(cleaned[:m.start()]) and cleaned[m.end():m.end() + 1] in {")", ">", " ", "\t", "\r", ""}]
        if len(spots) != count or any(original[p:p + len(old)] != old for p in spots):
            return None
        edits.setdefault(line, []).extend((p, old, new) for p in spots)
    for line, items in edits.items():
        original = lines[line - 1]
        items.sort()
        if any(a[0] + len(a[1]) > b[0] for a, b in zip(items, items[1:])):
            return None
        # Right to left, so earlier positions stay valid.
        for position, old, new in reversed(items):
            original = original[:position] + new + original[position + len(old):]
        lines[line - 1] = original
    return "\n".join(lines)


def archive_plan(wiki: Wiki, path: str) -> dict:
    """Compute everything an archive move would do, without writing."""
    root = wiki.require_archive_root()
    return relocation_plan(wiki, path, f"{root}/{relative_path(path)}", kind="archive")


def relocation_plan(wiki: Wiki, path: str, destination: str, kind: str = "archive") -> dict:
    """Compute everything a move would do, without writing.

    `archive` passes a destination under its archive root; `move` passes an
    arbitrary registered destination. Everything else (the carried files, the
    incoming and outgoing links to rewrite, the collision and manifest hashing,
    the plan hash) is identical, so both share this core.
    """
    path = relative_path(path)
    destination = relative_path(destination)
    if kind == "archive" and wiki.root_role(path) == "archive":
        raise WikiError("Source is already under an archive root")
    documents = wiki.documents()
    is_directory = not wiki.snapshot.is_file(path) and wiki.snapshot.exists(path)
    if is_directory:
        sources = {p for p in documents if under(p, path)}
        if not sources:
            raise WikiError("Directory contains no registered Markdown documents")
        # The move carries everything on disk, including excluded and untracked
        # files, so the plan lists and hashes exactly that.
        manifest = directory_manifest(wiki.snapshot.root, path)
        others = [item[0] for item in manifest if item[1] != "directory" and item[0] not in sources]
    else:
        if not path.lower().endswith((".md", ".markdown")):
            raise WikiError("Archive preview supports a complete Markdown document or a directory")
        if path not in documents:
            raise WikiError("Source must be a registered, readable Markdown document")
        sources, others, manifest = {path}, [], []

    def moved(p: str) -> str:
        if p == path:
            return destination
        if is_directory and under(p, path):
            return destination + p[len(path):]
        return p

    findings: list[Finding] = []
    protected = move_protected_source(wiki, path)
    if protected:
        findings.append(Finding("move-source-protected", "error", path, f"Source cannot be moved because {protected}"))
    if under(destination, path):
        findings.append(Finding("move-invalid", "error", destination, "Destination is inside the source directory"))
    for checked_path in sorted({path, destination} | sources):
        if not relocation_path_safe(wiki.snapshot.root, checked_path):
            findings.append(Finding("archive-unsafe-path", "error", checked_path,
                                    "A path used by the move contains a symbolic link or leaves the project root"))
    blocked_by = archive_destination_conflict(wiki.snapshot.root, destination)
    if wiki.snapshot.exists(destination) or blocked_by:
        findings.append(Finding("archive-collision", "error", destination,
                                f"Archive target is blocked: {blocked_by or 'it already exists'}; no overwrite is allowed, even if content matches"))
    if others:
        findings.append(Finding("archive-non-markdown", "warning", path,
                                f"{len(others)} other files move with the directory; their links are not inspected: " + ", ".join(others[:5]) + (" ..." if len(others) > 5 else "")))
    affected = []
    changes: dict[str, dict[tuple[int, str, str], int]] = {}
    for source in documents:
        text = wiki.snapshot.text(source)
        for link in markdown_links(text, source):
            target, fragment, problem = resolve_link(source, link.target)
            if source in sources and (problem or link.kind == "missing-reference"):
                findings.append(Finding("archive-unresolved-link", "warning", source, f"Cannot propose a portable move for {link.target}: {problem or 'undefined reference'}", link.line))
            if problem or target is None or link.kind == "missing-reference":
                continue
            inside_source = source in sources
            inside_target = target in sources or (is_directory and under(target, path))
            if inside_source == inside_target:
                # Unrelated, or both ends move together and the relative path stays valid.
                continue
            if inside_source and not wiki.snapshot.exists(target):
                findings.append(Finding("archive-unresolved-target", "warning", source, f"Existing target cannot be located: {link.target}", link.line))
                continue
            new_source, new_target = moved(source), moved(target)
            suggested = posixpath.relpath(new_target, posixpath.dirname(new_source) or ".")
            if fragment:
                suggested += "#" + fragment
            direction = "outgoing" if inside_source else "incoming"
            if kind == "archive" and direction == "incoming" and wiki.is_entry(source) and wiki.role(source) != "archive":
                findings.append(Finding("archive-entry-link", "warning", source,
                                        f"[agent] Entry page will link into history ({suggested}); link the current document instead if one exists", link.line, link.target))
            written_at = link.defined_at or link.line
            affected.append({"path": source, "line": written_at, "target": link.target, "suggested_target": suggested, "direction": direction,
                             "note": "rewritten by --apply; the moved file keeps the same content, so the link keeps its meaning"})
            key = (written_at, link.target, suggested)
            # Several uses of one reference definition share a single written target.
            per_file = changes.setdefault(source, {})
            per_file[key] = 1 if link.defined_at else per_file.get(key, 0) + 1
    rewrites: dict[str, str] = {}
    for source, items in sorted(changes.items()):
        manual = [old for _, old, new in items if "%" in old or re.search(r"\s", new)]
        updated = None if manual else rewrite_link_targets(wiki.snapshot.text(source), items)
        if updated is None:
            findings.append(Finding("archive-rewrite-manual", "warning", source,
                                    "Links in this file cannot be rewritten mechanically (escaped, percent-encoded or ambiguous targets); --apply refuses. Edit them by hand: "
                                    + ", ".join(sorted({old for _, old, _ in items})), min(items)[0]))
        else:
            rewrites[source] = updated
    for checked_path in sorted(rewrites):
        if not relocation_path_safe(wiki.snapshot.root, checked_path):
            findings.append(Finding("archive-unsafe-path", "error", checked_path,
                                    "A rewrite path contains a symbolic link or leaves the project root"))
    source_hash = digest(b"".join(p.encode() + b"\0" + wiki.snapshot.read(p) for p in sorted(sources)))
    plan_inputs = {"destination": destination, "sources": source_hash, "manifest": manifest,
                   "rewrites": {p: [digest(wiki.snapshot.read(p)), digest(t.encode("utf-8"))] for p, t in rewrites.items()}}
    plan_hash = digest(json.dumps(plan_inputs, sort_keys=True, ensure_ascii=False).encode("utf-8"))
    return {"path": path, "is_directory": is_directory, "sources": sources, "destination": destination, "moved": moved,
            "findings": findings, "affected": affected, "rewrites": rewrites, "source_sha256": source_hash, "plan_sha256": plan_hash,
            "manifest": manifest, "other_files": others}


def directory_manifest(root: Path, path: str) -> list[list[str]]:
    """List every entry a directory move carries, read from the filesystem.

    Each entry is [relative path, kind, detail]: files carry their permission
    bits and content hash, symlinks their target, directories nothing (so empty
    directories count). Configured excludes do not apply, because the move does
    not apply them either.
    """
    entries: list[list[str]] = []
    for directory, dirs, files in os.walk(root / path, followlinks=False):
        here = Path(directory)
        for name in sorted(dirs + files):
            item = here / name
            relative = item.relative_to(root).as_posix()
            info = item.lstat()
            if stat.S_ISLNK(info.st_mode):
                entries.append([relative, "symlink", os.readlink(item)])
            elif stat.S_ISDIR(info.st_mode):
                entries.append([relative, "directory", ""])
            elif stat.S_ISREG(info.st_mode):
                hasher = hashlib.sha256()
                with open(item, "rb") as handle:
                    for block in iter(lambda: handle.read(1 << 20), b""):
                        hasher.update(block)
                entries.append([relative, "file", f"{stat.S_IMODE(info.st_mode):o}:{hasher.hexdigest()}"])
            else:
                entries.append([relative, "other", f"{stat.S_IFMT(info.st_mode):o}"])
    return sorted(entries)


def archive_destination_conflict(root: Path, destination: str) -> str | None:
    """Describe what on disk blocks the destination, including empty directories."""
    target = root / destination
    if os.path.lexists(target):
        return f"{destination} already exists" + (" as a directory" if target.is_dir() and not target.is_symlink() else "")
    for parent in PurePosixPath(destination).parents:
        if str(parent) == ".":
            continue
        candidate = root / parent.as_posix()
        if os.path.lexists(candidate) and not candidate.is_dir():
            return f"{parent.as_posix()} exists and is not a directory"
    return None


def archive_report(wiki: Wiki, path: str) -> dict:
    plan = archive_plan(wiki, path)
    return report(wiki, plan["findings"], "archive-preview", source=plan["path"], destination=plan["destination"],
                  source_kind="directory" if plan["is_directory"] else "document", documents_moved=sorted(plan["sources"]),
                  other_files_moved=plan["other_files"],
                  source_sha256=plan["source_sha256"], plan_sha256=plan["plan_sha256"], affected_links=plan["affected"],
                  writes=False, limitations=["whole-document or whole-directory only", "links inside a moved directory are kept as they are",
                                             "does not decide whether a topic is retired", "does not create redirects",
                                             "links in files outside the registered documents (code comments, excluded directories) are not found",
                                             f"run again with --apply --expect {plan['plan_sha256']} to perform exactly this plan"])


def archive_index_row(wiki: Wiki, plan: dict, summary: str) -> tuple[str, str] | Finding:
    """Return (index path, new index text) with one row appended to its first table."""
    index = wiki.archive_index or f"{wiki.require_archive_root()}/INDEX.md"
    if "\n" in summary or not summary.strip():
        return Finding("archive-index-unavailable", "error", index, "--summary must be one non-empty line")
    if not wiki.snapshot.is_file(index):
        return Finding("archive-index-unavailable", "error", index, "History index does not exist; create it or set archive_index, or omit --summary")
    text = plan["rewrites"].get(index, wiki.snapshot.text(index))
    lines = text.split("\n")
    start = next((i for i in range(len(lines) - 1)
                  if lines[i].lstrip().startswith("|") and re.match(r"^\s*\|?\s*:?-{3,}", lines[i + 1])), None)
    if start is None:
        return Finding("archive-index-unavailable", "error", index, "History index has no Markdown table to append to")
    end = start + 1
    while end + 1 < len(lines) and lines[end + 1].lstrip().startswith("|"):
        end += 1
    destination = plan["destination"]
    if plan["is_directory"]:
        readme = f"{destination}/README.md"
        destination = readme if f"{plan['path']}/README.md" in plan["sources"] else destination + "/"
        title_source = f"{plan['path']}/README.md" if f"{plan['path']}/README.md" in plan["sources"] else None
    else:
        title_source = plan["path"]
    title = PurePosixPath(plan["path"]).name
    if title_source:
        heading = re.search(r"^#\s+(.+)$", without_code(wiki.snapshot.text(title_source), inline=False), re.M)
        title = heading.group(1).strip() if heading else title
    link = posixpath.relpath(destination.rstrip("/"), posixpath.dirname(index) or ".") + ("/" if destination.endswith("/") else "")
    escape = lambda value: value.replace("|", "\\|")
    lines.insert(end + 1, f"| [{escape(title)}]({link}) | {escape(summary.strip())} |")
    return index, "\n".join(lines)


def write_replacing(target: Path, data: bytes) -> None:
    """Replace an existing file through a sibling temporary file, keeping its mode."""
    temporary = target.with_name(f".{target.name}.wiki-{os.getpid()}")
    with open(temporary, "xb") as handle:
        handle.write(data)
    os.chmod(temporary, stat.S_IMODE(target.stat().st_mode))
    os.replace(temporary, target)


def relocation_path_safe(root: Path, path: str) -> bool:
    """Reject symlink components, including dangling links, and escapes from root."""
    root = root.resolve()
    current = root
    for part in PurePosixPath(path).parts:
        current = current / part
        if current.is_symlink():
            return False
    return current.resolve().is_relative_to(root)


def move_file_without_overwrite(source: Path, destination: Path) -> None:
    """Atomically move an entry only when the destination does not exist.

    Without renameat2 the operation fails closed; a check followed by rename
    could overwrite an entry created by another writer in between.
    """
    libc = ctypes.CDLL(None, use_errno=True)
    rename = getattr(libc, "renameat2", None)
    if rename is None:
        info = source.lstat()
        if not stat.S_ISREG(info.st_mode):
            raise OSError(errno.ENOTSUP, "atomic no-replace rename is unavailable")
        identity = (info.st_dev, info.st_ino)
        os.link(source, destination, follow_symlinks=False)
        try:
            os.unlink(source)
        except OSError as exc:
            try:
                info = destination.lstat()
                if (info.st_dev, info.st_ino) != identity:
                    raise OSError("destination changed before hard-link cleanup")
                os.unlink(destination)
            except OSError as cleanup_error:
                error = OSError(f"{exc}; hard-link cleanup incomplete: {cleanup_error}")
                error.partial_destination = destination
                raise error from exc
            raise
        return
    rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
    rename.restype = ctypes.c_int
    if rename(-100, os.fsencode(source), -100, os.fsencode(destination), 1) != 0:
        code = ctypes.get_errno()
        raise OSError(code, os.strerror(code), str(destination))


def restore_bytes(target: Path, data: bytes, mode: int) -> None:
    """Restore one file without exposing a partially written rollback."""
    temporary = target.with_name(f".{target.name}.wiki-rollback-{os.getpid()}")
    with open(temporary, "xb") as handle:
        handle.write(data)
    os.chmod(temporary, mode)
    os.replace(temporary, target)


def archive_apply(wiki: Wiki, path: str, expect: str, summary: str | None) -> dict:
    root = wiki.require_archive_root()
    return relocation_apply(wiki, path, f"{root}/{relative_path(path)}", expect, kind="archive", summary=summary)


def relocation_apply(wiki: Wiki, path: str, destination: str, expect: str, kind: str = "archive", summary: str | None = None) -> dict:
    command = f"{kind}-apply"
    # archive_apply keeps calling archive_plan by name so its tests can observe the plan step.
    plan = archive_plan(wiki, path) if kind == "archive" else relocation_plan(wiki, path, destination, kind=kind)
    findings = list(plan["findings"])
    root = wiki.snapshot.root
    blocked = [f for f in findings if f.severity == "error" or f.rule == "archive-rewrite-manual"]
    if blocked:
        findings.append(Finding("archive-not-applied", "error", plan["path"], "The plan has errors or links that need manual edits; nothing was written"))
    if expect != plan["plan_sha256"]:
        findings.append(Finding("archive-plan-changed", "error", plan["path"],
                                f"Plan hash is {plan['plan_sha256']}, not {expect}; inputs changed since the preview. Preview again; nothing was written"))
    index_update = None
    if summary is not None:
        index_update = archive_index_row(wiki, plan, summary)
        if isinstance(index_update, Finding):
            findings.append(index_update)
            index_update = None
    extra = {"source": plan["path"], "destination": plan["destination"], "plan_sha256": plan["plan_sha256"], "affected_links": plan["affected"]}
    if any(f.severity == "error" for f in findings):
        return report(wiki, findings, command, writes=[], **extra)
    reread = set(plan["rewrites"]) | set(plan["sources"]) | ({index_update[0]} if index_update else set())
    changed = [p for p in sorted(reread) if (root / p).read_bytes() != wiki.snapshot.read(p)]
    if changed:
        findings.append(Finding("archive-plan-changed", "error", changed[0], "Files changed while the plan was being prepared: " + ", ".join(changed) + "; nothing was written"))
        return report(wiki, findings, command, writes=[], **extra)
    if plan["is_directory"] and directory_manifest(root, plan["path"]) != plan["manifest"]:
        findings.append(Finding("archive-plan-changed", "error", plan["path"],
                                "Files in the directory were added, removed or changed while the plan was being prepared; nothing was written"))
        return report(wiki, findings, command, writes=[], **extra)
    # Prepare every rewritten file before moving anything. The staging directory
    # lives inside the repository so os.replace remains on the same filesystem;
    # it is removed on both success and failure.
    targets: dict[str, bytes] = {}
    rewrite_targets: list[str] = []
    for changed_path, text in sorted(plan["rewrites"].items()):
        if index_update and changed_path == index_update[0]:
            continue
        final = plan["moved"](changed_path)
        targets[final] = text.encode("utf-8")
        rewrite_targets.append(final)
    if index_update:
        targets[index_update[0]] = index_update[1].encode("utf-8")
    backups: dict[str, tuple[bool, bytes, int]] = {}
    source_for_target = {plan["moved"](source_path): source_path for source_path in plan["sources"]}
    source_modes = {source_path: stat.S_IMODE((root / source_path).stat().st_mode) for source_path in plan["sources"]}
    stage_root: Path | None = None
    staged_files: dict[str, Path] = {}
    try:
        stage_root = Path(tempfile.mkdtemp(prefix=".wiki-archive-", dir=root))
        for number, (final, data) in enumerate(sorted(targets.items())):
            target = root / final
            if target.exists():
                if not target.is_file():
                    raise OSError(f"rewrite target is not a regular file: {final}")
                backups[final] = (True, target.read_bytes(), stat.S_IMODE(target.stat().st_mode))
                mode = backups[final][2]
            else:
                backups[final] = (False, b"", 0)
                source_path = source_for_target.get(final)
                mode = stat.S_IMODE((root / source_path).stat().st_mode) if source_path else 0o644
            staged = stage_root / str(number)
            with open(staged, "xb") as handle:
                handle.write(data)
            os.chmod(staged, mode)
            staged_files[final] = staged
    except (OSError, WikiError) as exc:
        if stage_root is not None:
            shutil.rmtree(stage_root, ignore_errors=True)
        findings.append(Finding("archive-prepare-failed", "error", plan["path"],
                                f"Could not prepare all archive writes; nothing was moved: {exc}"))
        return report(wiki, findings, command, writes=[], **extra)

    writes: list[str] = []
    partial_writes: list[str] = []
    source, destination = root / plan["path"], root / plan["destination"]
    created_dirs: list[tuple[Path, tuple[int, int]]] = []
    source_removed = False
    file_moved = False
    file_identity: tuple[int, int] | None = None
    applied_targets: list[str] = []
    applied_identities: dict[str, tuple[int, int]] = {}
    moved_entries: list[tuple[str, tuple[int, int]]] = []
    destination_created = False
    destination_identity: tuple[int, int] | None = None

    def ensure_parent(target: Path) -> None:
        missing: list[Path] = []
        parent = target.parent
        while parent != root and not parent.exists():
            missing.append(parent)
            parent = parent.parent
        for directory in reversed(missing):
            directory.mkdir()
            info = directory.lstat()
            created_dirs.append((directory, (info.st_dev, info.st_ino)))
            partial_writes.append(f"created directory {directory.relative_to(root)}")

    def remember_replaced(final: str) -> None:
        nonlocal file_identity
        info = (root / final).lstat()
        identity = (info.st_dev, info.st_ino)
        if final == plan["destination"]:
            file_identity = identity
        if posixpath.dirname(final) == plan["destination"]:
            name = posixpath.basename(final)
            for index, (entry, _) in enumerate(moved_entries):
                if entry == name:
                    moved_entries[index] = (entry, identity)
                    break

    def rollback() -> list[str]:
        errors: list[str] = []
        for final in reversed(applied_targets):
            target = root / final
            existed, data, mode = backups[final]
            try:
                info = target.lstat()
                if (info.st_dev, info.st_ino) != applied_identities[final] or not target.is_file() or target.is_symlink() or target.read_bytes() != targets[final]:
                    raise OSError("rewritten file changed after this operation")
                if existed:
                    restore_bytes(target, data, mode)
                    remember_replaced(final)
                elif final in source_for_target:
                    source_path = source_for_target[final]
                    restore_bytes(target, wiki.snapshot.read(source_path),
                                  source_modes[source_path])
                    remember_replaced(final)
                elif target.exists():
                    target.unlink()
            except OSError as exc:
                errors.append(f"restore {final}: {exc}")
        try:
            if file_moved:
                info = destination.lstat()
                if (info.st_dev, info.st_ino) != file_identity:
                    raise OSError("moved file changed before rollback")
                move_file_without_overwrite(destination, source)
            elif destination_created:
                info = destination.lstat()
                if (info.st_dev, info.st_ino) != destination_identity:
                    raise OSError("destination directory changed before rollback")
                if source_removed:
                    source.mkdir()
                for entry, identity in reversed(moved_entries):
                    item = destination / entry
                    info = item.lstat()
                    if (info.st_dev, info.st_ino) != identity:
                        raise OSError(f"moved entry changed: {item}")
                    move_file_without_overwrite(item, source / entry)
                destination.rmdir()
        except OSError as exc:
            partial_destination = getattr(exc, "partial_destination", None)
            if partial_destination is not None:
                partial_writes.append(f"created hard link {partial_destination.relative_to(root)} during rollback")
            errors.append(f"move {plan['destination']} back to {plan['path']}: {exc}")
        for directory, identity in sorted(created_dirs, key=lambda item: len(item[0].parts), reverse=True):
            try:
                info = directory.lstat()
                if (info.st_dev, info.st_ino) != identity:
                    raise OSError("directory changed before rollback")
                directory.rmdir()
            except OSError as exc:
                errors.append(f"remove created directory {directory}: {exc}")
        return errors

    try:
        for checked_path in sorted({plan["path"], plan["destination"]} | set(plan["rewrites"]) | set(targets)):
            if not relocation_path_safe(root, checked_path):
                raise OSError(f"unsafe relocation path: {checked_path}")
        ensure_parent(destination)
        if plan["is_directory"]:
            os.mkdir(destination)  # Fails if the target appeared since the plan was made.
            destination_created = True
            info = destination.lstat()
            destination_identity = (info.st_dev, info.st_ino)
            partial_writes.append(f"created directory {plan['destination']}")
            for entry in sorted(os.listdir(source)):
                item = source / entry
                info = item.lstat()
                move_file_without_overwrite(item, destination / entry)
                moved_entries.append((entry, (info.st_dev, info.st_ino)))
                partial_writes.append(f"moved {plan['path']}/{entry} -> {plan['destination']}/{entry}")
            os.rmdir(source)
            source_removed = True
        else:
            info = source.lstat()
            file_identity = (info.st_dev, info.st_ino)
            move_file_without_overwrite(source, destination)
            file_moved = True
        writes.append(f"moved {plan['path']} -> {plan['destination']}")
        for final, staged in staged_files.items():
            target = root / final
            if not relocation_path_safe(root, final):
                raise OSError(f"unsafe relocation path: {final}")
            ensure_parent(target)
            original = source_for_target.get(final, final)
            if not target.is_file() or target.is_symlink() or target.read_bytes() != wiki.snapshot.read(original):
                raise OSError(f"rewrite target changed: {final}")
            os.replace(staged, target)
            info = target.lstat()
            applied_identities[final] = (info.st_dev, info.st_ino)
            remember_replaced(final)
            applied_targets.append(final)
            if final in rewrite_targets:
                writes.append(f"rewrote links in {final}")
            elif index_update and final == index_update[0]:
                writes.append(f"added a row to {final}")
    except (OSError, WikiError) as exc:
        rollback_errors = rollback()
        partial_destination = getattr(exc, "partial_destination", None)
        if partial_destination is not None:
            partial_writes.append(f"created hard link {partial_destination.relative_to(root)}")
            rollback_errors.append(f"hard-link cleanup incomplete at {partial_destination}")
        findings.append(Finding("archive-apply-failed", "error", plan["path"],
                                f"Archive commit failed; rollback {'was incomplete' if rollback_errors else 'completed'}: {exc}"))
        if rollback_errors:
            findings.append(Finding("archive-rollback-failed", "error", plan["path"],
                                    "Rollback was incomplete: " + "; ".join(rollback_errors)))
        if stage_root is not None:
            shutil.rmtree(stage_root, ignore_errors=True)
        return report(wiki, findings, command, writes=partial_writes + writes if rollback_errors else [], **extra)
    if stage_root is not None:
        shutil.rmtree(stage_root, ignore_errors=True)
    try:
        after = Wiki(Snapshot(root), wiki.config_path)
        after_findings, _ = apply_baseline(after, check(after))
    except (OSError, WikiError) as exc:
        findings.append(Finding("archive-after-check-failed", "error", plan["path"],
                                f"Files were written but the post-move check could not run: {exc}"))
        return report(wiki, findings, command, writes=writes, after_check="incomplete", **extra)
    after_errors = sum(f.severity == "error" for f in after_findings)
    return report(wiki, findings + after_findings, command, writes=writes,
                  after_check="failed" if after_errors else "passed_with_warnings" if after_findings else "passed",
                  next_steps=["files are moved, not staged; stage the deletion and the new path together"], **extra)


def move_protected_source(wiki: Wiki, path: str) -> str | None:
    """Reason a path may not be moved, or None. Covers tool files and generated output."""
    protected = {p for p in (wiki.config_path, wiki.baseline_path, wiki.generated_index, wiki.archive_index) if p}
    if wiki.archive_root():
        protected.add(wiki.archive_index or f"{wiki.archive_root()}/INDEX.md")
    if any(under(item, path) for item in protected):
        return "it is or contains a configuration, baseline, generated index or history index file"
    if wiki.generated_index and (under(path, posixpath.dirname(wiki.generated_index) or wiki.generated_index) or under(posixpath.dirname(wiki.generated_index) or wiki.generated_index, path)):
        return f"it is inside the generated index directory {posixpath.dirname(wiki.generated_index)}"
    return None


def move_rejections(wiki: Wiki, path: str, destination: str) -> list[Finding]:
    """Move-only refusals checked before the shared relocation core runs.

    Destination paths outside the project root or with '..' already raise in
    relative_path; this adds the registered-root, protected-source, identity,
    archive-root and naming rules move needs on top of the shared checks.
    """
    findings: list[Finding] = []
    if not wiki.snapshot.exists(path):
        findings.append(Finding("move-invalid", "error", path, "Source does not exist"))
    if path == destination:
        findings.append(Finding("move-invalid", "error", path, "Source and destination are the same path"))
    if under(destination, path) and path != destination:
        findings.append(Finding("move-invalid", "error", destination, "Destination is inside the source directory"))
    protected = move_protected_source(wiki, path)
    if protected:
        findings.append(Finding("move-source-protected", "error", path, f"This file cannot be moved because {protected}"))
    module_docs = [m["docs"] for m in wiki.modules if m.get("docs")]
    in_module_docs = any(under(destination, docs) for docs in module_docs)
    if wiki.root_role(destination) is None and not in_module_docs:
        findings.append(Finding("move-destination-outside", "error", destination,
                                "Destination is not under any registered root (knowledge, process or archive) or module docs directory; move only between registered locations"))
    elif wiki.root_role(destination) == "archive":
        findings.append(Finding("move-use-archive", "error", destination,
                                "[agent] Destination is an archive root; use 'archive' instead so the move is recorded in the history index"))
    # Only a renamed file goes through new-path naming; a pure directory change keeps the name.
    if PurePosixPath(path).name != PurePosixPath(destination).name:
        findings.extend(naming_findings_for_path(destination))
    return findings


def move_report(wiki: Wiki, path: str, destination: str) -> dict:
    path, destination = relative_path(path), relative_path(destination)
    rejections = move_rejections(wiki, path, destination)
    if any(f.severity == "error" for f in rejections):
        return report(wiki, rejections, "move-preview", source=path, destination=destination, writes=False)
    plan = relocation_plan(wiki, path, destination, kind="move")
    return report(wiki, rejections + plan["findings"], "move-preview", source=plan["path"], destination=plan["destination"],
                  source_kind="directory" if plan["is_directory"] else "document", documents_moved=sorted(plan["sources"]),
                  other_files_moved=plan["other_files"],
                  source_sha256=plan["source_sha256"], plan_sha256=plan["plan_sha256"], affected_links=plan["affected"],
                  writes=False, limitations=["whole-document or whole-directory only", "links inside a moved directory are kept as they are",
                                             "does not create redirects",
                                             "links in files outside the registered documents (code comments, excluded directories) are not found",
                                             "if generated_index covers the moved files, run index --write afterwards to refresh it",
                                             f"run again with --apply --expect {plan['plan_sha256']} to perform exactly this plan"])


def move_apply(wiki: Wiki, path: str, destination: str, expect: str) -> dict:
    path, destination = relative_path(path), relative_path(destination)
    rejections = move_rejections(wiki, path, destination)
    if any(f.severity == "error" for f in rejections):
        return report(wiki, rejections, "move-apply", source=path, destination=destination, writes=[])
    result = relocation_apply(wiki, path, destination, expect, kind="move")
    if rejections:
        result["findings"] = [asdict(f) for f in rejections] + result["findings"]
        result["warnings"] += sum(f.severity == "warning" for f in rejections)
        if result["errors"] == 0 and result["warnings"]:
            result["result"] = "passed_with_warnings"
    return result


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def git_text(root: Path, *args: str) -> str:
    return run_git(root, "-c", "core.quotePath=false", *args).decode("utf-8", "surrogateescape")


def has_head(root: Path) -> bool:
    result = subprocess.run(["git", "-C", str(root), "rev-parse", "--verify", "-q", "HEAD"], capture_output=True, check=False)
    if result.returncode not in {0, 1}:
        raise WikiError("Git repository unavailable: " + result.stderr.decode("utf-8", "replace").strip())
    return result.returncode == 0


def parse_numstat(lines: list[str]) -> list[tuple[str, int]]:
    changes = []
    for line in lines:
        parts = line.split("\t", 2)
        if len(parts) == 3:
            added, deleted, path = parts
            size = (int(added) if added.isdigit() else 0) + (int(deleted) if deleted.isdigit() else 0)
            changes.append((path, size))
    return changes


def snapshot_numstat(snapshot: Snapshot) -> list[tuple[str, int]]:
    """Count staged line changes from the index and captured HEAD objects.

    Reading both sides from the Snapshot keeps a staged reminder tied to the
    index object captured when the command started. The count is only used for
    reminder thresholds, so it intentionally approximates Git's numstat for
    binary or unusual line endings.

    Paths whose captured (mode, object ID) is the same on both sides are skipped
    without reading content, so the cost follows the size of the change rather
    than the size of the repository. Symlinks and submodules are reported as
    changed paths with no line count; binary files count zero lines, as Git does.
    """
    if not snapshot.staged:
        return []
    changes: list[tuple[str, int]] = []
    for path in sorted(snapshot._index_files | set(snapshot.head_entries)):
        before_entry = snapshot.head_entries.get(path)
        after_entry = snapshot.entries.get(path)
        if before_entry == after_entry:
            continue
        if any(entry is not None and entry[0] not in REGULAR_FILE_MODES for entry in (before_entry, after_entry)):
            changes.append((path, 0))
            continue
        before = snapshot.head_read(path) if before_entry else b""
        after = snapshot.read(path) if after_entry else b""
        if b"\0" in before or b"\0" in after:
            changes.append((path, 0))
            continue
        old_lines = before.splitlines(keepends=True)
        new_lines = after.splitlines(keepends=True)
        added = deleted = 0
        matcher = difflib.SequenceMatcher(None, old_lines, new_lines, autojunk=False)
        for tag, old_start, old_end, new_start, new_end in matcher.get_opcodes():
            if tag in {"replace", "delete"}:
                deleted += old_end - old_start
            if tag in {"replace", "insert"}:
                added += new_end - new_start
        changes.append((path, added + deleted))
    return changes


def review_marks(values: list[str]) -> tuple[set[str], list[str]]:
    """Return watch names reviewed with a reason, and trailer values missing a reason."""
    reviewed: set[str] = set()
    incomplete: list[str] = []
    for value in values:
        names, _, reason = value.strip().partition(" ")
        if not names:
            continue
        if not reason.strip():
            incomplete.append(value.strip())
            continue
        reviewed.update(n for n in names.split(",") if n)
    return reviewed, incomplete


def message_trailers(root: Path, message_file: str) -> list[str]:
    try:
        text = Path(message_file).read_text(encoding="utf-8")
    except OSError as exc:
        raise WikiError(f"Cannot read commit message file: {exc.strerror}") from exc
    text = "".join(line for line in text.splitlines(keepends=True) if not line.startswith("#"))
    result = subprocess.run(["git", "-C", str(root), "interpret-trailers", "--parse"], input=text.encode("utf-8"),
                            capture_output=True, check=False)
    if result.returncode:
        raise WikiError("Cannot parse commit message trailers: " + result.stderr.decode("utf-8", "replace").strip())
    prefix = REVIEW_TRAILER.lower() + ":"
    return [line.split(":", 1)[1] for line in result.stdout.decode("utf-8", "replace").splitlines()
            if line.lower().startswith(prefix)]


def adoption_commits(wiki: Wiki) -> set[str]:
    """Full commit IDs that count as adoption commits (B1).

    An adoption commit is the first commit that added the configuration file
    (config_path, found with git log --diff-filter=A, oldest one kept), plus the
    commit named by adopted_at when it is set. Changes such a commit makes to a
    watch group's docs do not reset that group's count unless the commit message
    marks the group reviewed. When the configuration was never committed there is
    no adoption commit and behaviour is unchanged.
    """
    if wiki._adoption_commits is not None:
        return wiki._adoption_commits
    root = wiki.snapshot.root
    if not has_head(root):
        wiki._adoption_commits = set()
        return wiki._adoption_commits
    commits: set[str] = set()
    added = git_text(root, "log", "--diff-filter=A", "--format=%H", "--", wiki.config_path).split()
    if added:
        commits.add(added[-1])
    legacy = wiki.legacy()
    if legacy is not None:
        commits.add(legacy[0])
    wiki._adoption_commits = commits
    return commits


# A frontmatter line setting a mechanical field, e.g. "updated: 2026-10-28".
_MECHANICAL_FRONTMATTER = re.compile(r"^(?:updated|created|date|status|phase)\s*[:：]", re.I)
_LINK_PLACEHOLDER = "\x00LINK\x00"


def _normalize_document(text: str, status_line_substantial: bool) -> tuple[str, ...]:
    """Normalize mechanical values in their document context, preserving line order."""
    spans: list[tuple[int, int]] = []
    _parse_links(text, "", spans)
    for start, end_span in reversed(spans):
        text = text[:start] + _LINK_PLACEHOLDER + text[end_span:]
    lines = text.splitlines()
    end = None
    if lines and lines[0].lstrip("\ufeff").strip() == "---":
        end = next ( (i for i in range(1, len(lines))
                     if lines[i].strip() in ("---", "...")), None)
    status_index = status_line_index(text)
    normalized = []
    mechanical = False
    for i, line in enumerate(lines):
        if end is not None and 0 < i < end:
            if line and not line[0].isspace() and not line.startswith("#"):
                mechanical = bool(_MECHANICAL_FRONTMATTER.match(line))
                if mechanical:
                    key = re.split(r"[:：]", line, maxsplit=1)[0]
                    normalized.append(f"\x00FM\x00{key.strip().lower()}")
                    continue
            elif mechanical and (not line.strip() or line[0].isspace()):
                continue
        if i == status_index and (end is None or i > end) and not status_line_substantial:
            normalized.append("\x00STATUS\x00")
        else:
            normalized.append(line)
    return tuple(normalized)


def text_is_substantial(before: str, after: str, status_line_substantial: bool) -> bool:
    """Compare complete ordered texts after removing only mechanical differences."""
    return (_normalize_document(before, status_line_substantial) !=
            _normalize_document(after, status_line_substantial))


def substantial_paths_from_entries(before_entries: dict[str, tuple[str, str]],
                                   after_entries: dict[str, tuple[str, str]],
                                   read_before, read_after, candidates: set[str],
                                   status_line_substantial: bool, prepare=None) -> set[str]:
    """Classify changed paths from immutable blob entries, pairing mechanical moves."""
    if not candidates:
        return set()
    removed = {p for p in before_entries.keys() - after_entries.keys()
               if before_entries[p][0] in REGULAR_FILE_MODES}
    added = {p for p in after_entries.keys() - before_entries.keys()
             if after_entries[p][0] in REGULAR_FILE_MODES}
    moved: set[str] = set()

    def pair(key_before, key_after):
        sources: dict[object, list[str]] = {}
        destinations: dict[object, list[str]] = {}
        for path in sorted(removed - moved):
            sources.setdefault(key_before(path), []).append(path)
        for path in sorted(added - moved):
            destinations.setdefault(key_after(path), []).append(path)
        for key in sources.keys() & destinations.keys():
            for source, destination in zip(sources[key], destinations[key]):
                moved.update((source, destination))

    if removed and added:
        pair(lambda p: before_entries[p][1], lambda p: after_entries[p][1])
    remaining_removed, remaining_added = removed - moved, added - moved
    pair_needed = bool(remaining_removed and remaining_added and
                       candidates & (remaining_removed | remaining_added))
    if prepare is not None:
        prepare(remaining_removed if pair_needed else set(),
                remaining_added if pair_needed else set(), candidates - moved)
    if pair_needed:
        def normalized(data: bytes):
            return _normalize_document(data.decode("utf-8", "replace"), status_line_substantial)
        pair(lambda p: normalized(read_before(p)), lambda p: normalized(read_after(p)))
    hits: set[str] = set()
    for path in candidates - moved:
        before_entry = before_entries.get(path)
        after_entry = after_entries.get(path)
        if before_entry == after_entry or (before_entry and after_entry and
                before_entry[1] == after_entry[1] and
                before_entry[0] in REGULAR_FILE_MODES and after_entry[0] in REGULAR_FILE_MODES):
            continue
        if any(e is not None and e[0] not in REGULAR_FILE_MODES for e in (before_entry, after_entry)):
            hits.add(path)
            continue
        before = read_before(path).decode("utf-8", "replace") if before_entry else ""
        after = read_after(path).decode("utf-8", "replace") if after_entry else ""
        if text_is_substantial(before, after, status_line_substantial):
            hits.add(path)
    return hits


def substantial_paths_in_commit(root: Path, commit: str, candidates: set[str],
                                status_line_substantial: bool) -> set[str]:
    """Classify a commit's captured blob changes with the same rule as staged changes."""
    if not candidates:
        return set()
    raw = run_git(root, "diff-tree", "-r", "--raw", "-z", "--no-renames", "--relative", "--root", commit)
    fields = raw.split(b"\0")
    before: dict[str, tuple[str, str]] = {}
    after: dict[str, tuple[str, str]] = {}
    i = 0
    while i < len(fields):
        header = fields[i]
        i += 1
        if not header.startswith(b":"):
            continue
        if i >= len(fields):
            raise WikiError("Incomplete git diff-tree output")
        path = fields[i].decode("utf-8", "surrogateescape")
        i += 1
        old_mode, new_mode, old_oid, new_oid, _status = header[1:].decode("ascii").split()
        if old_mode != "000000":
            before[path] = (old_mode, old_oid)
        if new_mode != "000000":
            after[path] = (new_mode, new_oid)
    blobs: dict[str, bytes] = {}
    def prepare(removed, added, paths):
        wanted: set[str] = set()
        needed_paths = set()
        for path in paths:
            old, new = before.get(path), after.get(path)
            if old == new or any(e is not None and e[0] not in REGULAR_FILE_MODES for e in (old, new)):
                continue
            if old and new and old[1] == new[1]:
                continue
            needed_paths.add(path)
        for entries, selected in ((before, removed | needed_paths), (after, added | needed_paths)):
            wanted.update(entries[p][1] for p in selected if p in entries and
                          entries[p][0] in REGULAR_FILE_MODES)
        if not wanted:
            return
        ordered = sorted(wanted)
        result = subprocess.run(["git", "-C", str(root), "cat-file", "--batch"],
                                input=("\n".join(ordered) + "\n").encode("ascii"),
                                capture_output=True, check=False)
        if result.returncode:
            raise WikiError("Cannot read Git blobs: " + result.stderr.decode("utf-8", "replace").strip())
        output = result.stdout
        offset = 0
        for oid in ordered:
            end = output.find(b"\n", offset)
            if end < 0:
                raise WikiError("Incomplete Git batch header")
            header = output[offset:end].split()
            if len(header) != 3 or header[0].decode("ascii") != oid or header[1] != b"blob":
                raise WikiError("Invalid Git batch blob header")
            try:
                size = int(header[2])
            except ValueError as exc:
                raise WikiError("Invalid Git batch blob size") from exc
            offset = end + 1
            if size < 0 or offset + size >= len(output) or output[offset + size:offset + size + 1] != b"\n":
                raise WikiError("Incomplete Git batch blob")
            blobs[oid] = output[offset:offset + size]
            offset += size + 1
    def read(entries, path):
        return blobs[entries[path][1]]
    return substantial_paths_from_entries(before, after, lambda p: read(before, p),
                                          lambda p: read(after, p), candidates,
                                          status_line_substantial, prepare)



def staged_substantial_paths(snapshot: Snapshot, candidates: set[str],
                             status_line_substantial: bool) -> set[str]:
    """Classify changes using the captured HEAD and index entries and blobs."""
    return substantial_paths_from_entries(snapshot.head_entries, snapshot.entries,
                                          snapshot.head_read, snapshot.read, candidates,
                                          status_line_substantial)


def merge_document_substantial(documents: list[bytes], status_line_substantial: bool) -> bool:
    """Compare complete normalized documents against every parent of a merge."""
    from collections import Counter
    normalized = [_normalize_document(data.decode("utf-8", "replace"), status_line_substantial)
                  for data in documents]
    after = normalized[-1]
    added_sets = []
    deleted_sets = []
    for before in normalized[:-1]:
        added: set[int] = set()
        deleted: Counter[str] = Counter()
        for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, before, after, autojunk=False).get_opcodes():
            if tag != "equal":
                added.update(range(j1, j2))
                deleted.update(before[i1:i2])
        added_sets.append(added)
        deleted_sets.append(deleted)
    common_deleted = deleted_sets[0]
    for deleted in deleted_sets[1:]:
        common_deleted &= deleted
    return bool(set.intersection(*added_sets) or common_deleted)


def merge_integration_changes(root: Path, commit: str, parents: int, paths: set[str]):
    """Read one combined patch for watched paths; return changes and raw object IDs."""
    if not paths:
        return [], {}
    raw = run_git(root, "diff-tree", "--cc", "-r", "--raw", "-z", "-p", "--no-abbrev",
                  "--no-color", "--no-ext-diff", "--no-commit-id", "--no-renames", "--relative",
                  "--unified=0", commit, "--", *sorted(paths))
    headers, sep, patch = raw.partition(b"\0\0")
    if not sep:
        return [], {}
    fields = headers.split(b"\0")
    entries = []
    for header, name in zip(fields[::2], fields[1::2]):
        parts = header.lstrip(b":").split()
        if len(parts) != 2 * (parents + 1) + 1:
            raise WikiError("Invalid combined diff header")
        entries.append((name.decode("utf-8", "surrogateescape"),
                        [x.decode("ascii") for x in parts[:parents + 1]],
                        [x.decode("ascii") for x in parts[parents + 1:2 * (parents + 1)]]))
    sections = re.split(rb"(?m)^diff --(?:cc|combined) ", patch)[1:]
    patches = {}
    for section in sections:
        name, _, body = section.partition(b"\n")
        if name.startswith(b'"') and name.endswith(b'"'):
            # Git C-quotes control bytes and may octal-escape non-ASCII names.
            encoded = bytearray()
            i = 1
            escapes = {ord("n"): 10, ord("t"): 9, ord("r"): 13, ord("b"): 8,
                       ord("f"): 12, ord("v"): 11, ord("a"): 7, ord('"'): 34, ord("\\"): 92}
            while i < len(name) - 1:
                if name[i] != 92:
                    encoded.append(name[i])
                    i += 1
                elif i + 3 < len(name) and all(48 <= c <= 55 for c in name[i + 1:i + 4]):
                    encoded.append(int(name[i + 1:i + 4], 8))
                    i += 4
                elif i + 1 < len(name) - 1 and name[i + 1] in escapes:
                    encoded.append(escapes[name[i + 1]])
                    i += 2
                else:
                    raise WikiError("Invalid combined diff path")
            path = encoded.decode("utf-8", "surrogateescape")
        else:
            path = name.decode("utf-8", "surrogateescape")
        patches[path] = body
    result = []
    objects = {}
    for path, modes, oids in entries:
        section = patches.get(path, b"")
        objects[path] = (modes, oids)
        count = 0
        in_hunk = False
        for line in section.splitlines():
            if line.startswith(b"@@@"):
                in_hunk = True
            elif in_hunk and len(line) >= parents and line[:parents] in (b"+" * parents, b"-" * parents):
                count += 1
        special = any(mode not in REGULAR_FILE_MODES and mode != "000000" for mode in modes)
        binary = b"Binary files " in section or b"Binary file " in section
        if count or special or binary or len(set(modes)) > 1:
            result.append((path, 0 if special or binary else count))
    return result, objects


def merge_blob_contents(root: Path, objects: dict, paths: set[str]) -> dict[str, bytes]:
    """Read all regular merge document blobs in a single Git batch."""
    wanted = sorted({oid for path in paths for mode, oid in zip(*objects[path])
                     if mode in REGULAR_FILE_MODES and set(oid) != {"0"}})
    if not wanted:
        return {}
    result = subprocess.run(["git", "-C", str(root), "cat-file", "--batch"],
                            input=("\n".join(wanted) + "\n").encode("ascii"),
                            capture_output=True, check=False)
    if result.returncode:
        raise WikiError("Cannot read merge blobs: " + result.stderr.decode("utf-8", "replace").strip())
    blobs = {}
    offset = 0
    for oid in wanted:
        end = result.stdout.find(b"\n", offset)
        header = result.stdout[offset:end].split() if end >= 0 else []
        if len(header) != 3 or header[0].decode("ascii") != oid or header[1] != b"blob":
            raise WikiError("Invalid merge batch blob header")
        size = int(header[2])
        offset = end + 1
        if size < 0 or offset + size >= len(result.stdout) or result.stdout[offset + size:offset + size + 1] != b"\n":
            raise WikiError("Incomplete merge batch blob")
        blobs[oid] = result.stdout[offset:offset + size]
        offset += size + 1
    return blobs


def history_debt(wiki: Wiki) -> tuple[dict[str, dict], bool]:
    """Count code changes per watch since its docs last changed or were marked reviewed."""
    root = wiki.snapshot.root
    watches = wiki.remind["watch"]
    status = {w["name"]: {"commits": 0, "lines": 0, "since": None} for w in watches}
    if not has_head(root):
        return status, False
    adopted = adoption_commits(wiki)
    output = git_text(root, "log", f"-n{HISTORY_SCAN_LIMIT}", "--relative", "--no-renames", "--numstat",
                      f"--format=%x1e%H%x1f%P%x1f%(trailers:key={REVIEW_TRAILER},valueonly,separator=%x1d)")
    open_names = set(status)
    scanned = 0
    for record in output.split("\x1e")[1:]:
        scanned += 1
        header, _, body = record.partition("\n")
        commit, _, rest = header.partition("\x1f")
        parents, _, trailer_text = rest.partition("\x1f")
        merge = len(parents.split()) > 1
        reviewed, _ = review_marks(trailer_text.split("\x1d"))
        changes = parse_numstat(body.splitlines())
        objects = {}
        if merge and commit not in adopted:
            names = run_git(root, "diff-tree", "--cc", "-r", "--name-only", "-z",
                            "--no-commit-id", "--no-renames", "--relative", commit)
            combined = {p.decode("utf-8", "surrogateescape") for p in names.split(b"\0") if p}
            relevant = {path for path in combined if any(
                watch["name"] in open_names and
                (matches(path, watch["code"]) or matches(path, watch["docs"])) for watch in watches)}
            changes, objects = merge_integration_changes(root, commit, len(parents.split()), relevant)
        is_adoption = commit in adopted
        # Only classify docs of still-open groups, and skip the diff entirely on an
        # adoption commit: adoption changes never reset a group, only its Docs-Reviewed
        # trailer does. A mechanical or link-only change does not reset either (B2).
        touched: set[str] = set()
        if not is_adoption:
            doc_candidates = {path for path, _ in changes if any(
                watch["name"] in open_names and matches(path, watch["docs"]) for watch in watches)}
            if merge:
                blobs = merge_blob_contents(root, objects, doc_candidates)
                for path in doc_candidates:
                    modes, oids = objects[path]
                    if any(mode not in REGULAR_FILE_MODES and mode != "000000" for mode in modes):
                        touched.add(path)
                    elif merge_document_substantial([blobs.get(oid, b"") for oid in oids], False):
                        touched.add(path)
            else:
                touched = substantial_paths_in_commit(root, commit, doc_candidates, status_line_substantial=False)
        for watch in watches:
            name = watch["name"]
            if name not in open_names:
                continue
            if name in reviewed or any(matches(path, watch["docs"]) for path in touched):
                status[name]["since"] = commit[:12]
                open_names.discard(name)
                continue
            if is_adoption:
                # The adoption commit imports existing code; its changes are not drift,
                # so they are not counted. The scan still continues to earlier commits.
                continue
            code = [size for path, size in changes if matches(path, watch["code"])]
            if code:
                status[name]["commits"] += 1
                status[name]["lines"] += sum(code)
        if not open_names:
            break
    return status, bool(open_names) and scanned >= HISTORY_SCAN_LIMIT


def review_due_findings(wiki: Wiki, today=None) -> list[Finding]:
    """Knowledge pages whose 复核期限 has arrived, and check dates in the future."""
    import datetime
    today = today or datetime.date.today()
    findings: list[Finding] = []
    for path in wiki.documents():
        if wiki.role(path) == "archive":
            continue
        info = wiki.verification_of(path)
        if info["review_by"] and info["review_by"] <= today:
            findings.append(Finding("knowledge-review-due", "warning", path,
                                    f"[agent] 复核期限 {info['review_by']} has arrived. Check the page against the current code and sources, "
                                    "then add a 核对 entry and a new 复核期限; ask a person to review it if the path requires a human check."))
        future = [date for _, _, date in info["entries"] if date > today]
        if future:
            findings.append(Finding("verify-date-future", "warning", path, f"[agent] 核对 date {max(future)} is later than today"))
    return findings


def days_since_commit(wiki: Wiki, path: str) -> int | None:
    """Whole days since the last substantial commit touching path (B3).

    Walks at most STALE_SCAN_LIMIT commits back from HEAD. Adoption commits and
    commits whose only change to path is mechanical (link target, mechanical
    frontmatter field) are skipped, so an adoption import or a bulk frontmatter
    rewrite does not reset a plan's staleness. Unlike watch debt, a 状态 line
    change counts as substantial here: reconfirming a status updates the plan.

    Returns None when no commit has touched path. If every commit within the
    window is mechanical or an adoption commit, the oldest one in the window is
    used: a plan buried under that many mechanical commits is at least that old,
    and reporting it stale is safer than hiding it.
    """
    import time
    root = wiki.snapshot.root
    if not has_head(root):
        return None
    adopted = adoption_commits(wiki)
    output = git_text(root, "log", f"-n{STALE_SCAN_LIMIT}", "--format=%x1e%H%x1f%ct%x1f%P",
                      "--", path)
    records = [r for r in output.split("\x1e") if r.strip()]
    if not records:
        return None
    oldest_ct: int | None = None
    for record in records:
        commit, ct, parents = record.strip("\r\n ").split("\x1f", 2)
        oldest_ct = int(ct)
        if commit in adopted:
            continue
        is_merge = len(parents.split()) > 1
        if is_merge:
            changes, objects = merge_integration_changes(root, commit, len(parents.split()), {path})
            if path in {name for name, _ in changes}:
                modes, oids = objects[path]
                blobs = merge_blob_contents(root, objects, {path})
                changed = (any(mode not in REGULAR_FILE_MODES and mode != "000000" for mode in modes) or
                           merge_document_substantial([blobs.get(oid, b"") for oid in oids], True))
            else:
                changed = False
        else:
            changed = path in substantial_paths_in_commit(root, commit, {path}, status_line_substantial=True)
        if changed:
            return (int(time.time()) - int(ct)) // 86400
    return (int(time.time()) - oldest_ct) // 86400 if oldest_ct is not None else None


def stale_days_for_path(wiki: Wiki, path: str) -> int | None:
    cache = getattr(wiki, "_stale_days_cache", {})
    return cache[path] if path in cache else days_since_commit(wiki, path)


def inbox_findings(wiki: Wiki, staged_paths: list[str]) -> list[Finding]:
    """Inbox items left untriaged, finished but never archived, or without a status line."""
    findings: list[Finding] = []
    stale_days = wiki.remind["plan_stale_days"]
    for path in wiki.documents():
        if (wiki.role(path) == "archive" or not matches(path, wiki.remind["inbox"])
                or PurePosixPath(path).name.lower() == "readme.md"):
            continue
        raw, kind = wiki.status(path)
        if kind is None:
            findings.append(Finding("inbox-no-status", "warning", path,
                                    "[agent] Inbox item has no recognised status line below its title. Add '状态：待分诊。' "
                                    "or, once it is handled, '状态：已完成。'."))
        elif kind == "finished":
            findings.append(Finding("inbox-finished-not-archived", "warning", path,
                                    f"[agent] Inbox item status is '{raw}' but it is still under dev/. Check that its outcome section names "
                                    f"where it went, then archive it: preview with python3 scripts/wiki/wiki.py archive {path} --root ., "
                                    "then rerun with --apply --expect <plan_sha256>."))
        elif kind == "open" and path not in staged_paths:
            days = stale_days_for_path(wiki, path)
            if days is not None and days >= stale_days:
                findings.append(Finding("inbox-stale", "warning", path,
                                        f"[agent] Inbox item is still '{raw}' after {days} days without a commit (limit {stale_days}). "
                                        "Triage it: move it into a plan or current knowledge, mark it a duplicate or not to be done, "
                                        "record the outcome and archive it."))
    return findings


def human_claims_added(snapshot: Snapshot, path: str) -> list[str]:
    """human: names on lines that the staged version of path adds compared with HEAD.

    Agents must not write human: for themselves, and the tool cannot tell who wrote a line,
    so a new human: claim is shown to the user. Code spans and blocks are ignored, which
    keeps format examples such as `human:<名字>` out of the result.
    """
    from collections import Counter

    def claims(data: bytes) -> Counter:
        text = data.decode("utf-8", "replace")
        return Counter(line.strip() for line in without_code(text).splitlines() if HUMAN_ACTOR.search(line))

    head_entry = snapshot.head_entries.get(path)
    before = claims(snapshot.head_read(path)) if head_entry and head_entry[0] in REGULAR_FILE_MODES else Counter()
    after = claims(snapshot.read(path)) if snapshot.is_file(path) else Counter()
    names: list[str] = []
    for line in (after - before):
        for name in HUMAN_ACTOR.findall(line):
            if name not in names:
                names.append(name)
    return names


def plan_findings(wiki: Wiki, staged_paths: list[str]) -> list[Finding]:
    """Plans that look abandoned, or finished but never archived."""
    findings: list[Finding] = []
    stale_days = wiki.remind["plan_stale_days"]
    idle_days = wiki.remind["plan_idle_days"]
    for path in wiki.documents():
        if wiki.role(path) == "archive" or not wiki.is_plan(path):
            continue
        raw, kind = wiki.status(path)
        if raw is None and wiki.is_legacy(path):
            continue
        if raw is None:
            findings.append(Finding("plan-no-status", "warning", path, "[agent] Plan has no status line below its title, so its progress cannot be tracked. Add e.g. '状态：执行中。'"))
            continue
        if kind == "finished":
            findings.append(Finding("plan-finished-not-archived", "warning", path,
                                    f"[agent] Plan status is '{raw}' but it is still under dev/. Write remaining stable knowledge back to docs/ "
                                    f"and archive it: preview with python3 scripts/wiki/wiki.py archive {path} --root ., then rerun with --apply --expect <plan_sha256>."))
        elif kind == "active" and path not in staged_paths:
            days = stale_days_for_path(wiki, path)
            if days is not None and days >= stale_days:
                findings.append(Finding("plan-stale", "warning", path,
                                        f"[agent] Plan is '{raw}' but no commit has touched it for {days} days (limit {stale_days}). "
                                        "Update its progress, mark it blocked with the reason, or finish and archive it."))
        elif kind in {"draft", "blocked"} and path not in staged_paths:
            days = stale_days_for_path(wiki, path)
            if days is not None and days >= idle_days:
                findings.append(Finding("plan-idle", "warning", path,
                                        f"[agent] Plan has stayed '{raw}' for {days} days without a real change (limit {idle_days}). "
                                        "Decide whether to start it, cancel it, or write down what it is still waiting for."))
    return findings


def legacy_lifecycle(wiki: Wiki, findings: list[Finding]) -> list[Finding]:
    """Fold per-file lifecycle reminders on legacy plans and inbox items into one summary.

    Hundreds of old plans would otherwise repeat on every commit, and projects then relabel
    finished work to silence them. The summary keeps the count visible without the noise.
    """
    folded = {"plan-finished-not-archived", "plan-stale", "plan-idle", "inbox-finished-not-archived", "inbox-stale", "inbox-no-status"}
    kept: list[Finding] = []
    counts: dict[str, int] = {}
    for finding in findings:
        if finding.rule in folded and wiki.is_legacy(finding.path):
            counts[finding.rule] = counts.get(finding.rule, 0) + 1
        else:
            kept.append(finding)
    if counts:
        parts = ", ".join(f"{rule} {number}" for rule, number in sorted(counts.items()))
        kept.append(Finding("legacy-lifecycle", "warning", wiki.config_path,
                            f"Files that existed at adopted_at {wiki.legacy()[0][:12]} still need lifecycle work ({parts}). "
                            "They are not listed one by one; run python3 scripts/wiki/wiki.py remind --root . --all to list them, "
                            "then archive finished work or update the status a few at a time.", key=parts))
    return kept


def over(value: dict, limit: dict) -> bool:
    return bool(limit) and any(value[key] >= number for key, number in limit.items())


def hooks_not_installed_findings(wiki: Wiki) -> list[Finding]:
    """Warn once per ProjectWiki hook that is not effective in a Git work tree.

    Only runs outside --staged (a staged check runs from inside a hook). When the active
    hooks directory cannot be read as plain ProjectWiki hooks (not a Git work tree, or
    another manager's directory the tool cannot classify), hook_status returns a string
    and this gives nothing, to avoid false alarms. Reuses doctor's hook_status.
    """
    if wiki.snapshot.staged:
        return []
    hooks = hook_status(wiki)
    if not isinstance(hooks, dict):
        return []
    findings: list[Finding] = []
    for name, state in hooks.items():
        if state == "installed":
            continue
        findings.append(Finding("hooks-not-installed", "warning", name,
                                f"[user] Git {name} hook: {state}; commit-time checks and reminders do not run. "
                                f"Either run 'git config core.hooksPath scripts/wiki/hooks', or add "
                                f"'python3 scripts/wiki/wiki.py {HOOK_MARKERS[name]} --root .' "
                                f"(pass --message-file \"$1\" for commit-msg) to the existing {name} hook, as in "
                                f"scripts/wiki/README.md. Agents must not change git config themselves, so confirm this with the user."))
    return findings


def remind_report(wiki: Wiki, message_file: str | None = None, list_legacy: bool = False) -> dict:
    if wiki.remind is None:
        return report(wiki, [Finding(
            "remind-not-configured", "warning", wiki.config_path,
            "No remind section; commit-time documentation reminders are disabled")]
            + hooks_not_installed_findings(wiki), "remind")
    snap = wiki.snapshot
    config = wiki.remind
    findings: list[Finding] = hooks_not_installed_findings(wiki)
    status, truncated = history_debt(wiki)
    staged_changes: list[tuple[str, int]] = []
    reviewed: set[str] = set()
    if snap.staged:
        staged_changes = snapshot_numstat(snap)
        if message_file:
            reviewed, incomplete = review_marks(message_trailers(snap.root, message_file))
            for value in incomplete:
                findings.append(Finding("review-trailer-incomplete", "warning", message_file,
                                        f"[agent] '{REVIEW_TRAILER}: {value}' has no reason and does not reset the count. Write '{REVIEW_TRAILER}: <name> <reason>'."))
            known = {watch["name"] for watch in config["watch"]}
            unknown = sorted(name for name in reviewed if name not in known)
            if unknown:
                reviewed -= set(unknown)
                listed = ", ".join(sorted(known)) if known else "none"
                findings.append(Finding("review-trailer-unknown-group", "warning", message_file,
                                        f"[agent] '{REVIEW_TRAILER}' names {', '.join(unknown)}, which match no remind.watch group; "
                                        f"these names reset no count. Configured groups: {listed}.",
                                        key=",".join(unknown)))
    staged_paths = [path for path, _ in staged_changes]
    # A staged doc change resets a group only when it is substantial (B2): link-only,
    # mechanical-frontmatter or 状态-line edits do not count as updating the docs.
    staged_doc_candidates = {path for path in staged_paths
                             if any(matches(path, watch["docs"]) for watch in config["watch"])}
    staged_doc_substantial = staged_substantial_paths(snap, staged_doc_candidates, status_line_substantial=False) if snap.staged else set()
    for watch in config["watch"]:
        name = watch["name"]
        item = status[name]
        code = [size for path, size in staged_changes if matches(path, watch["code"])]
        docs_touched = any(matches(path, watch["docs"]) for path in staged_doc_substantial)
        if snap.staged and code and not docs_touched and name not in reviewed:
            findings.append(Finding("docs-not-updated", "warning", name,
                                    f"[info] Code under {', '.join(watch['code'])} changed but {', '.join(watch['docs'])} did not. "
                                    "If these changes make an existing document inaccurate, update that document in this commit. "
                                    "Below the cumulative review threshold, this notice does not require a review trailer."))
        if snap.staged and (docs_touched or name in reviewed):
            item.update(commits=0, lines=0, since="this commit")
        elif snap.staged and code:
            item["commits"] += 1
            item["lines"] += sum(code)
        due = over(item, config["threshold"])
        overdue = over(item, config["block_at"])
        counted = f"{item['commits']} commits and {item['lines']} changed lines since {item['since'] or 'the start of scanned history'}"
        if overdue:
            blocking = snap.staged and bool(code)
            findings.append(Finding("docs-review-overdue", "error" if blocking else "warning", name,
                                    f"[agent] {counted} reached block_at {config['block_at']}. Read the documents for {name}, "
                                    f"update them or add '{REVIEW_TRAILER}: {name} <reason>' to this commit message."))
        elif due:
            findings.append(Finding("docs-review-due", "warning", name,
                                    f"[agent] {counted} reached the reminder threshold {config['threshold']}. "
                                    f"Check that the documents for {name} still match the code; update them or add "
                                    f"'{REVIEW_TRAILER}: {name} <reason>' after reviewing them."))
    if snap.staged:
        current = [p for p in wiki.documents() if wiki.role(p) != "archive"]
        for path in staged_paths:
            if matches(path, config["ask_user"]):
                findings.append(Finding("ask-user", "warning", path,
                                        "[user] This file states requirements, principles or decisions. Unless the user asked for this change, "
                                        "confirm it with the user before committing; otherwise report it in your summary."))
            if matches(path, config["decisions"]):
                linking = []
                for source in current:
                    if source == path or source in staged_paths:
                        continue
                    for link in markdown_links(snap.text(source), source):
                        target, _, problem = resolve_link(source, link.target)
                        if not problem and target == path:
                            linking.append(source)
                            break
                listed = ", ".join(linking[:10]) + (" ..." if len(linking) > 10 else "")
                findings.append(Finding("decision-sync", "warning", path,
                                        "[agent] A decision record changed. Update current documents that still state the old conclusion in this commit. "
                                        + (f"Linking documents not staged: {listed}. " if linking else "No unstaged current document links to it. ")
                                        + "Also search the decision's key terms; restatements without links are not listed."))
            if path.lower().endswith((".md", ".markdown")) and wiki.root_role(path) != "archive":
                names = human_claims_added(snap, path)
                if names:
                    findings.append(Finding("human-claim-added", "warning", path,
                                            "[user] This commit adds lines naming " + ", ".join(f"human:{n}" for n in names)
                                            + " as checker, owner or signer. Agents must not write human: for themselves. Confirm that the "
                                            "named person did this, or change the entry to agent:<name>; report it in your summary.",
                                            key=",".join(names)))
    # Preserve each path's own Git history and 30-commit limit; bound parallel reads.
    from concurrent.futures import ThreadPoolExecutor
    paths = []
    for path in wiki.documents():
        if wiki.role(path) == "archive" or path in staged_paths:
            continue
        is_plan = wiki.is_plan(path)
        is_inbox = (matches(path, wiki.remind["inbox"]) and
                    PurePosixPath(path).name.lower() != "readme.md")
        if not is_plan and not is_inbox:
            continue
        raw, kind = wiki.status(path)
        if ((is_plan and raw is not None and kind in {"active", "draft", "blocked"}) or
                (is_inbox and kind == "open")):
            paths.append(path)
    # Populate the shared immutable adoption set before workers access it.
    if paths:
        adoption_commits(wiki)
    with ThreadPoolExecutor(max_workers=8) as executor:
        wiki._stale_days_cache = dict(zip(paths, executor.map(lambda p: days_since_commit(wiki, p), paths)))
    lifecycle = plan_findings(wiki, staged_paths) + inbox_findings(wiki, staged_paths)
    findings.extend(lifecycle if list_legacy else legacy_lifecycle(wiki, lifecycle))
    findings.extend(review_due_findings(wiki))
    active_plans = []
    for path in wiki.documents():
        if wiki.role(path) == "archive" or not wiki.is_plan(path):
            continue
        raw, kind = wiki.status(path)
        if kind in {"active", "blocked"}:
            entry = {"path": path, "status": raw, "owner": status_owner(snap.text(path))}
            if kind == "blocked":
                entry["reason"] = status_reason(snap.text(path))
            active_plans.append(entry)
    return report(wiki, findings, "remind", watch_status=[{"name": n, **v} for n, v in status.items()], active_plans=active_plans,
                  history_scan_limit_reached=truncated,
                  limitations=["counts come from Git history and the captured index only", "does not judge whether document content matches code",
                               "trailers in the pending message are read only when --message-file is given"])


def hook_status(wiki: Wiki) -> dict[str, str] | str:
    root = wiki.snapshot.root
    result = subprocess.run(["git", "-C", str(root), "rev-parse", "--git-path", "hooks"], capture_output=True, check=False)
    if result.returncode:
        return "unknown: not a Git repository"
    directory = (root / result.stdout.decode().strip()).resolve()
    states: dict[str, str] = {}
    for name, marker in HOOK_MARKERS.items():
        hook = directory / name
        if not hook.is_file():
            states[name] = "missing"
        elif not os.access(hook, os.X_OK):
            states[name] = "not executable"
        elif "wiki.py" not in (text := hook.read_text(encoding="utf-8", errors="replace")) or marker not in text:
            states[name] = "present but does not call wiki.py " + marker
        else:
            states[name] = "installed"
    return states


def doctor_report(wiki: Wiki) -> dict:
    findings = [Finding("hook-coverage", "warning", wiki.config_path,
                        "Other hook managers and build tasks are unknown: only the active Git hooks directory is inspected for ProjectWiki hooks")]
    roots = [r["path"] for r in wiki.roots if r["role"] == "archive"]
    ignore = wiki.snapshot.text(".ignore").splitlines() if ".ignore" in wiki.snapshot.files() else []
    missing = [root for root in roots if f"/{root}/" not in [line.strip() for line in ignore]]
    if missing:
        findings.append(Finding("archive-default-search", "warning", ".ignore", f"No exact root exclusion for: {', '.join(missing)}. Ordinary rg may include historical text."))
    hooks = hook_status(wiki)
    if isinstance(hooks, dict):
        for name, state in hooks.items():
            if state != "installed":
                findings.append(Finding("hook-not-installed", "warning", name, f"Git {name} hook: {state}. See scripts/wiki/README.md for installation."))
        if wiki.remind is None:
            findings.append(Finding("remind-not-configured", "warning", wiki.config_path, "No remind section; commit-time documentation reminders are disabled"))
    return report(wiki, findings, "doctor", hooks="unknown: other hook managers and build tasks are not inspected",
                  git_hooks=hooks,
                  archive_ignore="exact configured rules found" if not missing else "incomplete",
                  limits=["ignore rules affect compatible default searches, not access permissions", "git grep, arbitrary grep and explicit reads are not covered",
                          "a hook file being installed does not prove it runs; --no-verify and other Git clients can skip it"])


OKF_STATUS = {"current": "stable", "draft": "draft", "superseded": "deprecated", "retired": "deprecated"}
# 对应需求 and 范围 are reader conventions, not parsed lines, but they make poor descriptions too.
SKIP_FOR_DESCRIPTION = re.compile(
    rf"^(?:{kw('status')}|{kw('verified')}|{kw('review_by')}|{kw('covers_requirements')}|对应需求|范围|Scope)\s*[:：]", re.I)


def okf_type(wiki: Wiki, path: str) -> str:
    parts = PurePosixPath(path).parts
    if wiki.is_requirement_file(path):
        return "Requirements"
    if wiki.is_decision(path):
        return "Decision"
    for folder, name in (("guides", "Guide"), ("principles", "Principles"), ("standards", "Standard"),
                         ("design", "Design"), ("architecture", "Design"), ("reference", "Reference")):
        if folder in parts:
            return name
    if any(path == module["entry"] for module in wiki.modules):
        return "Module"
    if wiki.is_entry(path):
        return "Index"
    return "Document"


def okf_description(text: str) -> str | None:
    lines = without_code(text, inline=False).splitlines()
    for line in lines[1:]:
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "|", "-", "*", ">", "<")) or re.match(r"^\d+\.", stripped):
            continue
        if SKIP_FOR_DESCRIPTION.match(stripped):
            continue
        plain = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", stripped).replace("`", "")
        return plain if len(plain) <= 200 else plain[:199] + "…"
    return None


def okf_frontmatter(wiki: Wiki, path: str, text: str) -> str:
    title = re.search(r"^#\s+(.+)$", without_code(text, inline=False), re.M)
    fields = [("type", okf_type(wiki, path)), ("title", title.group(1).strip() if title else PurePosixPath(path).stem)]
    description = okf_description(text)
    if description:
        fields.append(("description", description))
    _, kind = wiki.status(path)
    if kind in OKF_STATUS:
        fields.append(("status", OKF_STATUS[kind]))
    lines = ["---"] + [f"{key}: {json.dumps(value, ensure_ascii=False)}" for key, value in fields]
    info = wiki.verification_of(path)
    if info["entries"]:
        lines.append("verified:")
        for kind_name, name, date in info["entries"]:
            lines.append(f"  - by: {json.dumps(kind_name + ':' + name, ensure_ascii=False)}")
            lines.append(f"    at: \"{date.isoformat()}T00:00:00Z\"")
    if info["review_by"]:
        lines.append(f"stale_after: \"{info['review_by'].isoformat()}T00:00:00Z\"")
    lines.append(f"okf_source: {json.dumps(path, ensure_ascii=False)}")
    return "\n".join(lines) + "\n---\n\n"


def export_okf(wiki: Wiki, out: Path) -> dict:
    root = wiki.snapshot.root
    target = out.resolve()
    if target == root or root in target.parents:
        raise WikiError("Output directory must be outside the project root")
    if target.exists() and (not target.is_dir() or any(target.iterdir())):
        raise WikiError(f"Output directory exists and is not empty: {target}")
    findings: list[Finding] = []
    planned: list[tuple[str, str]] = []
    for path in wiki.documents():
        if wiki.role(path) == "archive" or wiki.root_role(path) == "process":
            continue
        if PurePosixPath(path).name in {"index.md", "log.md"}:
            findings.append(Finding("okf-reserved-name", "warning", path, "OKF reserves this filename; exported unchanged and will be read as an index or log"))
        text = wiki.snapshot.text(path)
        if text.startswith("---\n"):
            findings.append(Finding("okf-existing-frontmatter", "warning", path, "Document already has frontmatter; exported unchanged without adding OKF fields"))
            planned.append((path, text))
        else:
            planned.append((path, okf_frontmatter(wiki, path, text) + text))
    for path, content in planned:
        destination = target / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")
    return report(wiki, findings, "export-okf", output=str(target), exported=len(planned),
                  limitations=["process and archive documents are not exported", "non-Markdown files are not copied",
                               "links to documents that were not exported stay as written and may be broken in the bundle",
                               "mapping targets OKF v0.2 and may change with the specification"])


def code_span(value: str) -> str:
    fence = "``" if "`" in value else "`"
    pad = " " if value.startswith("`") or value.endswith("`") else ""
    return f"{fence}{pad}{value}{pad}{fence}"


def git_state(root: Path) -> tuple[str | None, int | None]:
    """Return (short HEAD or "" before the first commit, uncommitted file count); (None, None) without Git."""
    try:
        run_git(root, "rev-parse", "--git-dir")
    except WikiError:
        return None, None
    try:
        head = git_text(root, "rev-parse", "--short=12", "--verify", "-q", "HEAD").strip()
    except WikiError:
        head = ""
    status = git_text(root, "status", "--porcelain", "--untracked-files=all", "--", ".")
    return head, sum(1 for line in status.splitlines() if line.strip())


def verify_report(wiki: Wiki, names: list[str]) -> dict:
    """Run the registered verification commands and describe the run as a Markdown record."""
    import datetime
    if wiki.verify is None:
        raise WikiError("verify requires a verify section with commands in the configuration")
    commands = wiki.verify["commands"]
    unknown = sorted(set(names) - {c["name"] for c in commands})
    if unknown:
        raise WikiError("Unknown verify command: " + ", ".join(unknown))
    selected = [c for c in commands if not names or c["name"] in names]
    skipped = [c["name"] for c in commands if c not in selected]
    root = wiki.snapshot.root
    for command in selected:
        if not (root / command["cwd"]).is_dir():
            raise WikiError(f"verify.commands[{command['name']}].cwd does not exist: {command['cwd']}")
    findings: list[Finding] = []
    today = datetime.date.today().isoformat()
    head, dirty = git_state(root)
    files = sorted(p for p in wiki.snapshot.files() if matches(p, wiki.verify["files"]) and wiki.snapshot.is_file(p))
    if wiki.verify["files"] and not files:
        findings.append(Finding("verify-files-unmatched", "warning", wiki.config_path, "verify.files matched no files; the record carries no file hashes"))
    before = {p: digest(wiki.snapshot.read(p)) for p in files}
    results = []
    for command in selected:
        started = time.monotonic()
        problem = None
        try:
            process = subprocess.run(command["run"], cwd=root / command["cwd"], env={**os.environ, **command["env"]},
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=command["timeout"], check=False)
            code, output = process.returncode, process.stdout
        except subprocess.TimeoutExpired as exc:
            code, output, problem = None, exc.stdout or b"", f"{command['timeout']} 秒后超时，已终止"
        except OSError as exc:
            code, output, problem = None, b"", f"无法启动：{exc.strerror or exc}"
        seconds = round(time.monotonic() - started, 1)
        printed = [line.strip()[:200] for line in output.decode("utf-8", "replace").splitlines() if line.strip()]
        count = command["summary_lines"]
        tail = printed[:count] if command["summary_from"] == "start" else printed[-count:]
        shown = " ".join([f"{k}={shlex.quote(v)}" for k, v in sorted(command["env"].items())] + [shlex.join(command["run"])])
        results.append({"name": command["name"], "command": shown, "cwd": command["cwd"], "exit_code": code,
                        "problem": problem, "seconds": seconds, "summary": tail, "summary_from": command["summary_from"],
                        **({"kind": command["kind"]} if command.get("kind") else {})})
        if code != 0:
            findings.append(Finding("verify-command-failed", "error", wiki.config_path,
                                    f"{command['name']}: {problem or f'exit code {code}'}", key=command["name"]))
    after = {p: digest((root / p).read_bytes()) if (root / p).is_file() else None for p in files}
    changed = [p for p in files if after[p] != before[p]]
    if changed:
        findings.append(Finding("verify-files-changed", "warning", wiki.config_path,
                                "Registered files changed while the commands ran; the hashes describe the state before the run: " + ", ".join(changed)))
    if head is None:
        state = "不是 Git 仓库"
    elif not head:
        state = f"尚无提交，有 {dirty} 个未提交的文件"
    else:
        state = f"HEAD {code_span(head)}，" + (f"相对 HEAD 有 {dirty} 个文件未提交（含未跟踪文件）" if dirty else "没有未提交的改动")
    lines = [f"{today} 用 `wiki.py verify` 运行，输入是工作区：{state}；配置 {code_span(wiki.config_path)} 的 sha256 前缀为 {code_span(wiki.config_hash[:12])}。", ""]
    for item in results:
        where = "" if item["cwd"] == "." else f"，在 {code_span(item['cwd'])} 下运行"
        verdict = "通过" if item["exit_code"] == 0 else "失败"
        detail = item["problem"] or f"退出码 {item['exit_code']}"
        line = f"- {code_span(item['name'])}：{code_span(item['command'])}{where}，{verdict}（{detail}，用时 {item['seconds']} 秒）。"
        if item["summary"]:
            label = ("输出第一行：" if len(item["summary"]) == 1 else "输出前几行：") if item["summary_from"] == "start" else \
                    ("输出最后一行：" if len(item["summary"]) == 1 else "输出最后几行：")
            line += label + "；".join(code_span(t) for t in item["summary"]) + "。"
        lines.append(line)
    if skipped:
        lines.append("- 未运行：" + "、".join(code_span(n) for n in skipped) + "。")
    if files:
        lines.append("- 文件 sha256 前缀（运行前）：" + "；".join(f"{code_span(p)} {code_span(before[p][:12])}" for p in files) + "。")
    if changed:
        lines.append("- 运行期间发生变化的文件：" + "、".join(code_span(p) for p in changed) + "。")
    code_tests_registered = any(command.get("kind") == "test" for command in selected)
    if not code_tests_registered:
        lines.append("- 未登记代码测试：本记录只证明所登记的检查命令，不证明代码。")
    return report(wiki, findings, "verify", markdown="\n".join(lines) + "\n", commands=results, skipped=skipped,
                  files={p: before[p] for p in files}, git_head=head, uncommitted_files=dirty,
                  code_tests_registered=code_tests_registered)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--version", action="version", version=VERSION)
    commands = result.add_subparsers(dest="command", required=True)
    for name in ("check", "check-new", "archive", "move", "index", "doctor", "remind", "baseline", "export-okf", "verify"):
        command = commands.add_parser(name)
        command.add_argument("--root", type=Path, default=Path.cwd())
        command.add_argument("--config", default=DEFAULT_CONFIG)
        command.add_argument("--json", action="store_true", help="Print structured report (index always emits JSON)")
        if name == "check":
            command.add_argument("--require-repos", action="store_true",
                                 help="Report incomplete when a registered repository or a linked submodule cannot be read")
        if name in {"check", "remind"}:
            command.add_argument("--staged", action="store_true", help="Read all inputs from captured Git index blobs")
        if name == "remind":
            command.add_argument("--message-file", help="Pending commit message, as passed to a commit-msg hook")
            command.add_argument("--all", action="store_true",
                                 help="List lifecycle reminders for files that existed at adopted_at one by one instead of one summary")
        if name == "export-okf":
            command.add_argument("--out", type=Path, required=True, help="Empty or missing directory outside the project root")
        if name in {"check-new", "archive"}:
            command.add_argument("path")
        if name == "archive":
            command.add_argument("--apply", action="store_true", help="Perform the previewed move and link rewrites")
            command.add_argument("--expect", help="plan_sha256 from the preview; required with --apply")
            command.add_argument("--summary", help="With --apply: one-line description appended to the history index table")
        if name == "move":
            command.add_argument("source")
            command.add_argument("destination")
            command.add_argument("--apply", action="store_true", help="Perform the previewed move and link rewrites")
            command.add_argument("--expect", help="plan_sha256 from the preview; required with --apply")
        if name == "index":
            mode = command.add_mutually_exclusive_group()
            mode.add_argument("--check", action="store_true", help="Compare stdout index with configured generated_index")
            mode.add_argument("--write", action="store_true", help="Write configured generated_index when it differs")
        if name == "verify":
            command.add_argument("names", nargs="*", help="Run only these registered commands; the record lists the others as not run")
    return result


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        snapshot = Snapshot(args.root, getattr(args, "staged", False))
        config_path = relative_path(args.config)
        if snapshot.staged and args.command in {"check", "remind"} \
                and config_path not in snapshot.entries and config_path not in snapshot.head_entries:
            result = config_not_committed_report(snapshot, args.command, config_path)
            output_report(result, args.json)
            return 0
        wiki = Wiki(snapshot, args.config)
        wiki.require_repos = getattr(args, "require_repos", False)
        if args.command == "check":
            findings, accepted = apply_baseline(wiki, check(wiki))
            result = report(wiki, findings, "check", documents_checked=len(wiki.documents()),
                            baseline=wiki.baseline_path, baseline_accepted=accepted)
            if wiki.requirements_coverage:
                result["coverage"]["requirements"] = wiki.requirements_coverage
        elif args.command == "baseline":
            if not wiki.baseline_path:
                raise WikiError("baseline requires a baseline path in configuration")
            fingerprints = sorted({f.fingerprint() for f in check(wiki) if f.severity == "error"})
            print(json.dumps({"version": 1, "findings": fingerprints}, ensure_ascii=False, indent=2) + "\n", end="")
            return 0
        elif args.command == "check-new":
            result = new_path_report(wiki, args.path)
        elif args.command == "archive":
            if args.apply:
                if not args.expect:
                    raise WikiError("--apply requires --expect <plan_sha256> from the preview")
                result = archive_apply(wiki, args.path, args.expect, args.summary)
            else:
                if args.expect or args.summary:
                    raise WikiError("--expect and --summary are used only with --apply")
                result = archive_report(wiki, args.path)
        elif args.command == "move":
            if args.apply:
                if not args.expect:
                    raise WikiError("--apply requires --expect <plan_sha256> from the preview")
                result = move_apply(wiki, args.source, args.destination, args.expect)
            else:
                if args.expect:
                    raise WikiError("--expect is used only with --apply")
                result = move_report(wiki, args.source, args.destination)
        elif args.command == "verify":
            result = verify_report(wiki, args.names)
            if not args.json:
                print(result["markdown"], end="")
                for item in result["findings"]:
                    print(f"{item['severity'].upper()} {item['rule']}: {item['message']}", file=sys.stderr)
                return 1 if result["errors"] else 0
        elif args.command == "doctor":
            result = doctor_report(wiki)
        elif args.command == "export-okf":
            result = export_okf(wiki, args.out)
        elif args.command == "remind":
            if args.message_file and not args.staged:
                raise WikiError("--message-file requires --staged")
            result = remind_report(wiki, args.message_file, list_legacy=args.all)
        else:
            encoded = json.dumps(index_data(wiki), ensure_ascii=False, indent=2, sort_keys=True) + "\n"
            if args.check:
                if not wiki.generated_index:
                    raise WikiError("index --check requires generated_index in configuration")
                if not wiki.snapshot.exists(wiki.generated_index) or wiki.snapshot.text(wiki.generated_index) != encoded:
                    result = report(wiki, [Finding("index-outdated", "error", wiki.generated_index, "Generated index differs; run index --write, then review the diff")], "index-check")
                    output_report(result, args.json)
                    return 1
            elif args.write:
                if not wiki.generated_index:
                    raise WikiError("index --write requires generated_index in configuration")
                target = wiki.snapshot.root / wiki.generated_index
                current = wiki.snapshot.text(wiki.generated_index) if wiki.snapshot.is_file(wiki.generated_index) else None
                if current != encoded:
                    if current is None:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with open(target, "x", encoding="utf-8", newline="") as handle:
                            handle.write(encoded)
                    else:
                        write_replacing(target, encoded.encode("utf-8"))
                output_report(report(wiki, [], "index-write", output=wiki.generated_index, written=current != encoded), args.json)
            else:
                print(encoded, end="")
            return 0
        output_report(result, args.json)
        return 1 if result["errors"] else 0
    except (WikiError, OSError, ValueError) as exc:
        result = {"command": args.command, "result": "incomplete", "tool_version": VERSION,
                  "snapshot": "git-index" if getattr(args, "staged", False) else "working-tree", "errors": 1, "warnings": 0,
                  "findings": [asdict(Finding("input-unavailable", "error", args.config, str(exc)))]}
        output_report(result, args.json)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
