#!/usr/bin/env python3
"""Validate reconstructed core-freeze ledger structure, not product behavior."""

from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LEDGER = ROOT / "docs" / "CORE_FREEZE_ACCEPTANCE_LEDGER.csv"
REQUIRED_FIELDS = {
    "case_id",
    "record_kind",
    "candidate_priority",
    "priority_basis",
    "historical_id_mapping",
    "source_locator",
    "environment",
    "action",
    "expected",
    "actual",
    "evidence",
    "source_git_sha",
    "apk_sha256",
    "status",
}
ACTIVE_KINDS = {"RECONSTRUCTED"}
ALLOWED_KINDS = ACTIVE_KINDS | {"RETIRED"}
ALLOWED_PRIORITIES = {"P0", "P1", "P2", "OUT_OF_SCOPE"}
ALLOWED_STATUSES = {
    "PASS",
    "FAIL",
    "BLOCKED",
    "NOT TESTED",
    "SPEC UNRESOLVED",
    "RETIRED",
}
NOT_TESTED_GUIDE = "docs/issues/PC-QA-GATE-001-209-case-ledger.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_ledger(path: Path) -> tuple[list[dict[str, str]], str]:
    raw = path.read_bytes()
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fields = set(reader.fieldnames or [])
        require(REQUIRED_FIELDS <= fields, f"missing fields: {sorted(REQUIRED_FIELDS - fields)}")
        rows = list(reader)
    return rows, hashlib.sha256(raw).hexdigest().upper()


def validate(rows: list[dict[str, str]]) -> dict[str, object]:
    require(rows, "ledger has no data rows")
    ids = [row["case_id"].strip() for row in rows]
    require(len(ids) == len(set(ids)), "duplicate case_id")
    require("HIST-UNCLASSIFIED-001" not in ids, "historical placeholder must not be an acceptance row")

    for row in rows:
        case_id = row["case_id"].strip()
        require(case_id, "empty case_id")
        kind = row["record_kind"].strip()
        priority = row["candidate_priority"].strip()
        status = row["status"].strip()
        require(kind in ALLOWED_KINDS, f"{case_id}: unsupported record_kind {kind!r}")
        require(priority in ALLOWED_PRIORITIES, f"{case_id}: unsupported priority {priority!r}")
        require(status in ALLOWED_STATUSES, f"{case_id}: unsupported status {status!r}")
        require(row["historical_id_mapping"].strip() == "UNMAPPED", f"{case_id}: reconstructed row claims a historical mapping")
        for field in ("priority_basis", "source_locator", "environment", "action", "expected", "actual", "evidence"):
            require(row[field].strip(), f"{case_id}: empty {field}")

        if kind == "RECONSTRUCTED":
            require(priority in {"P0", "P1", "P2"}, f"{case_id}: active reconstructed row has non-risk priority")
            require("Risk review 2026-10-24:" in row["priority_basis"], f"{case_id}: missing current risk rationale")
        else:
            require(priority == "OUT_OF_SCOPE", f"{case_id}: retired row must be OUT_OF_SCOPE")
            require(status == "RETIRED", f"{case_id}: retired row must have RETIRED status")
            require("Retired by owner-confirmed scope" in row["priority_basis"], f"{case_id}: missing retirement basis")

        if status == "NOT TESTED":
            require("NOT TESTED" in row["actual"], f"{case_id}: untested actual must be explicit")
            require("no targeted run" in row["evidence"].lower(), f"{case_id}: untested evidence must disclose missing targeted run")
            require(NOT_TESTED_GUIDE in row["evidence"], f"{case_id}: untested evidence must link to the open-gap register")

    by_id = {row["case_id"]: row for row in rows}
    for number in range(21, 31):
        case_id = f"REC-P1-{number:03d}"
        require(case_id in by_id, f"missing greedy-title acceptance row {case_id}")
        row = by_id[case_id]
        require(row["record_kind"] == "RECONSTRUCTED", f"{case_id}: title-folding row must remain active")
        require(row["candidate_priority"] == "P1", f"{case_id}: title-folding row must remain P1")
        require(row["status"] == "PASS", f"{case_id}: title-folding sign-off is not PASS")

    require(by_id.get("REC-P1-013", {}).get("status") == "RETIRED", "accepted pale-gray stripe row must be retired")
    require(by_id.get("REC-P1-013", {}).get("record_kind") == "RETIRED", "retired stripe must not count as active reconstructed acceptance")
    require(by_id.get("REC-P2-001", {}).get("status") == "PASS", "PC-ISSUE-135 reconstructed recovery row is missing or not PASS")
    require(by_id.get("REC-P1-083", {}).get("status") == "PASS", "PC-ISSUE-136 reconstructed chat-history row is missing or not PASS")

    status_counts = collections.Counter(row["status"] for row in rows)
    priority_counts = collections.Counter(
        row["candidate_priority"] for row in rows if row["record_kind"] in ACTIVE_KINDS
    )
    return {
        "product_behavior_tested": False,
        "historical_ids_mapped": 0,
        "historical_209_equivalence_claimed": False,
        "rows": len(rows),
        "active_reconstructed_rows": sum(row["record_kind"] in ACTIVE_KINDS for row in rows),
        "retired_rows": [row["case_id"] for row in rows if row["record_kind"] == "RETIRED"],
        "counts_by_priority_active": dict(sorted(priority_counts.items())),
        "counts_by_status": dict(sorted(status_counts.items())),
        "not_tested_ids": sorted(row["case_id"] for row in rows if row["status"] == "NOT TESTED"),
        "blocked_ids": sorted(row["case_id"] for row in rows if row["status"] == "BLOCKED"),
        "spec_unresolved_ids": sorted(row["case_id"] for row in rows if row["status"] == "SPEC UNRESOLVED"),
        "failed_ids": sorted(row["case_id"] for row in rows if row["status"] == "FAIL"),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ledger", type=Path, default=DEFAULT_LEDGER)
    parser.add_argument("--json-out", type=Path, help="write a structural-only report; existing output is never overwritten")
    args = parser.parse_args()
    try:
        rows, digest = read_ledger(args.ledger)
        result = validate(rows)
        result["ledger_path"] = str(args.ledger.resolve())
        result["ledger_sha256"] = digest
        result["validator"] = Path(__file__).relative_to(ROOT).as_posix()
        if args.json_out:
            if args.json_out.exists():
                raise FileExistsError(f"refusing to overwrite {args.json_out}")
            args.json_out.parent.mkdir(parents=True, exist_ok=True)
            args.json_out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result, indent=2))
        return 0
    except (OSError, ValueError, csv.Error) as error:
        print(f"VALIDATION FAILED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
