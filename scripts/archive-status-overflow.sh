#!/usr/bin/env bash
# Move oldest blockquote post-notes from status.md into status-history-archive.md
# when the file exceeds the docs-health line limit (default 200).
# Does not rewrite the snapshot body — only trims leading `> **后注` lines.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATUS_FILE="$ROOT/dev/progress/status.md"
ARCHIVE_FILE="$ROOT/dev/progress/status-history-archive.md"
MAX_LINES=200
DRY_RUN=false

for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --max-lines=*) MAX_LINES="${arg#*=}" ;;
        --help|-h)
            echo "Usage: $0 [--dry-run] [--max-lines=N]"
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 2
            ;;
    esac
done

if [[ ! -f "$STATUS_FILE" ]]; then
    echo "ERROR: $STATUS_FILE not found." >&2
    exit 1
fi

python3 - "$STATUS_FILE" "$ARCHIVE_FILE" "$MAX_LINES" "$DRY_RUN" <<'PY'
import sys
from datetime import date
from pathlib import Path

status_path = Path(sys.argv[1])
archive_path = Path(sys.argv[2])
max_lines = int(sys.argv[3])
dry_run = sys.argv[4].lower() == "true"

text = status_path.read_text(encoding="utf-8")
if not text.startswith("---\n"):
    print("ERROR: status.md missing frontmatter", file=sys.stderr)
    sys.exit(1)
end = text.find("\n---\n", 4)
if end < 0:
    print("ERROR: status.md frontmatter not closed", file=sys.stderr)
    sys.exit(1)
fm = text[: end + 5]
if "> **后注" in fm:
    print(
        "ERROR: status.md frontmatter contains post-notes. "
        "Rewrite a real YAML frontmatter; do not park 后注 inside --- blocks.",
        file=sys.stderr,
    )
    sys.exit(1)
body = text[end + 5 :]
lines = body.splitlines(keepends=True)
total = len(text.splitlines())
if total <= max_lines:
    print(f"OK: {status_path} line count ({total}) is within limit ({max_lines}).")
    sys.exit(0)

overflow = total - max_lines
# 后注 runs are not necessarily at the head of the body: they accumulate under whatever
# section heading the tick was appended to, so a head-anchored scan reports "0 leading
# post-note lines" while 177 of them sit between two headings (observed 2026-09-13).
# Collect every 后注 line document-wide, in order; a `> …` continuation belongs to the
# note that opened the run. Convention stays newest-first, so archive from the tail.
note_idxs = []
in_run = False
for i, ln in enumerate(lines):
    s = ln.lstrip()
    if s.startswith("> **后注"):
        note_idxs.append(i)
        in_run = True
    elif in_run and s.startswith(">"):
        note_idxs.append(i)
    else:
        in_run = False

if len(note_idxs) < overflow:
    print(
        f"ERROR: need to remove {overflow} lines but only {len(note_idxs)} "
        "post-note lines exist anywhere in the body. Rewrite the snapshot body instead.",
        file=sys.stderr,
    )
    sys.exit(1)

# Archive whole notes from the tail until the snapshot is within max_lines.
notes = []
for i in note_idxs:
    if lines[i].lstrip().startswith("> **后注"):
        notes.append([i])
    else:
        notes[-1].append(i)
move_notes = []
removed = 0
for note in reversed(notes):
    if removed >= overflow:
        break
    move_notes.append(note)
    removed += len(note)
move_notes.reverse()
move_idxs = [i for note in move_notes for i in note]
moved = [lines[j] for j in move_idxs]
keep_set = set(move_idxs)
new_body_lines = [ln for j, ln in enumerate(lines) if j not in keep_set]
print(f"NOTICE: {status_path.name} has {total} lines (threshold: {max_lines}).")
print(f"Archiving {len(moved)} oldest post-note lines.")
if dry_run:
    print("DRY-RUN: Archival candidates identified. No files modified.")
    sys.exit(0)

header = (
    f"## 后注归档（{date.today().isoformat()} archive-status-overflow · "
    f"{len(moved)} 行）\n\n"
)
old_archive = archive_path.read_text(encoding="utf-8") if archive_path.exists() else ""
archive_path.write_text(header + "".join(moved) + "\n" + old_archive, encoding="utf-8")
status_path.write_text(fm + "".join(new_body_lines), encoding="utf-8")
new_total = len((fm + "".join(new_body_lines)).splitlines())
print(f"OK: {status_path.name} now {new_total} lines. Archive updated.")
PY
