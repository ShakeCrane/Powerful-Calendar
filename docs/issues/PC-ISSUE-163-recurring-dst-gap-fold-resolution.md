# PC-ISSUE-163 — Deterministic DST resolution for recurring slots

Date: 2026-10-24

Severity: P0

Status: **PASS (narrow JVM recurrence-engine scope)**

## Approved rule

The owner selected these rules for generated recurrence instances:

- A local slot that does not exist during the spring DST gap is skipped. It still consumes its generated index/count, so later dates do not shift.
- A local slot that occurs twice during the fall DST fold uses the first instant, with the earlier offset.

The rule applies to recurrence expansion, lookup and next-occurrence calculation. It does not change parsing of a separately entered one-off local timestamp.

## Root cause and fix

`RecurrenceEngine.slotMillis()` used `LocalDateTime.atZone(zone)`. Java resolves a nonexistent local time by moving it forward through the DST gap, so the recurring 02:30 slot on 2026-03-08 in `America/New_York` incorrectly appeared as an occurrence at 03:30. The new pre-fix regression reproduced the extra `2026-03-08T02:30` key. The old fold behavior happened to choose the earlier offset, but that choice was implicit.

The recurrence resolver now uses `ZoneRules` explicitly. It tracks a comparable boundary for ordered scans separately from a real occurrence instant. A gap slot keeps its index and count position but has no occurrence instant; all natural-slot projections and `nextOccurrence()` skip it. An overlap selects `getValidOffsets(local).first()`, the earlier offset returned by the zone rules.

## Regression coverage

Added two JVM tests in `TemporalProtocolTest`:

- Spring gap: with a daily 02:30 series and `count=4`, the gap slot is absent, remains slot index 1, consumes the count, and the later keys stay on March 9 and March 10. `occurrenceForKey()` returns null for the gap; `nextOccurrence()` advances to March 9. The test checks normal and prioritized projection paths, plus a 30-minute Activity series.
- Fall fold: the 2026-11-01 01:30 instance resolves to the first `-04:00` instant. List projection, `occurrenceForKey()` and `nextOccurrence()` agree.

## Test evidence

- Before the fix, the corrected focused run failed the gap case: expected March 7, 9 and 10, but the implementation also emitted March 8 at the nonexistent 02:30 slot. The fold case passed. Log SHA-256 `B8B7230C3C3271EB784318D9A19D7D8EF72AFC2BEA43587160E1BDC0DFB9A57A`.
- The first test-harness attempt had a Kotlin compile error because the fixture's `startMillis` is nullable; the test was corrected with `requireNotNull`. That was a test-source issue, not a product failure. Log SHA-256 `ABFB7EB9EA2529DA89A33906917A987E7D2FC4AE6509620E709F64BDB3CFC19A`.
- After the fix, both focused tests passed. Log SHA-256 `0C3E4DC55BB66D2FEAFECA1FA8F451A319702284BEBBF981735DCC32ECAE1EAF`; final `TemporalProtocolTest` XML SHA-256 `DA3FBE97DC3F5060F0E64A15BE5CD6BD24999B8929980919CFC98FF045FCDBC5`.
- Full offline Gradle passed 494/494 JVM tests across 66 suites, 0 failures/errors/skips. Source-boundary verification and Debug/AndroidTest APK assembly passed; lint reported 0 errors and 58 warnings. Full log SHA-256 `E3BFDDF0B3F8349A671EEC5B7784A16063F2C6A2D8CFCDBE4B78914535C7B2E4`.
- Debug APK SHA-256 `71FC411D703513E84CFEB889341AC9C0CBF18A12A1DA7B6BA85F0CFCE57D366D`.
- Code/test commit `315492434e4ec57d308d3ca2b65b8488239a3aaa` (`fix(重复): 跳过DST缺失时刻并取首次回拨时刻`) is pushed.

## Acceptance and limits

`REC-P0-074` and `REC-P0-075` are PASS for the tested recurrence-engine paths. No Android scheduler delivery, UI rendering, emulator or physical-device behavior is claimed.

Structural-only ledger r28 records 210 active reconstructed rows (P0/P1/P2 144/65/1): 197 PASS, 0 FAIL, 8 BLOCKED, 2 NOT TESTED and 3 SPEC UNRESOLVED. Historical IDs mapped=0 and `product_behavior_tested=false`; the validator checks structure, not product behavior. Artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r28-pc-issue-163.json` SHA-256 `8F0964729F1814E6DA1BE4C5E13B81233EC59DF669C19BE403F10615158D083F`; ledger SHA-256 `B2A791BB207C74F9273E76D891023BC55E78638AF3FA67C1857542BFFC1AE630`. The overall core freeze remains **HOLD**.
