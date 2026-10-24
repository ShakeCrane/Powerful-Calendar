# PC-ISSUE-138 — Unified Todo projection core sign-off

Date: 2026-10-24 | Current risk: P0 | Reconstructed keys: REC-P1-034 / REC-P1-035 / REC-P1-036 | Status: PASS within the narrowed core projection boundaries

This report signs the local Todo query, completion-filtering and source-semantics boundaries. It does not sign Compose presentation, keyboard interaction, physical-device behavior, or OEM behavior. Overall core freeze remains HOLD.

## Requirement source and finding

The source is CORE_CONTRACT_FREEZE_2026-10-21.md, invariant 4 (unified incomplete-todo query), invariants 9–10, and PRODUCT_BASELINE_G1-G11.md, §5. The risk review classifies all three reconstructed keys as P0 because a wrong Todo projection can hide unfinished work or misrepresent an event's kind/time.

The reconstructed ledger had no one-to-one test for these projection outcomes. Existing PowerStore completion tests established stored completion state but did not directly assert how TodoProjection.entries includes, excludes, and maps the resulting formal records.

## Test method

- Robolectric API 35 with synthetic TemporalRecord values at a fixed Asia/Shanghai time.
- Query ALARM, ACTIVITY, date-only DEADLINE, dated MEMO and undated MEMO entries. Also supply completed, disabled and tracking-disabled records.
- Create and complete one synthetic undated MEMO in a uniquely named PowerStore SQLite database; close and reopen that database before checking the default remaining projection, completed projection and undo behavior.
- No Compose screen was launched. No Android emulator, physical device, provider, user calendar database or pre-existing business record was used.

## Results

- REC-P1-034 — PASS (narrowed core projection). The default projection returned unfinished, enabled, tracking-enabled ALARM, ACTIVITY, DEADLINE and MEMO records in deterministic time order. It omitted completed, disabled and untracked records.
- REC-P1-035 — PASS (narrowed persistence/projection). Completion survived SQLite reopen and removed the MEMO from the default remaining projection. The formal event remained present and retained MEMO kind; the completed view showed it, and undo returned it to the remaining projection.
- REC-P1-036 — PASS (narrowed projection contract). Each entry retained its original event record, kind and date/time fields. A date-only DEADLINE retained DATE precision with no deadlineMillis; an undated MEMO retained a null start. This does not verify screen rendering.

The focused Robolectric class passed 2/2 tests, zero failures/errors/skips. The complete offline Gradle regression passed 471/471 JVM tests across 59 suites, zero failures/errors/skips. Source-boundary verification, Debug APK assembly, AndroidTest APK assembly and lint passed; lint reported 0 errors / 58 warnings. The aggregate includes one pre-existing protected untracked test source, DraftDeckFeedbackTest.kt; it was not modified or staged for this Issue.

## Corrected test-runner attempt

The first focused invocation failed before any product assertion. Robolectric attempted to open a synthetic SQLite path of 264 characters on Windows; the path exceeded the platform's traditional 260-character limit and raised SQLiteCantOpenDatabaseException. The test method and database name were shortened. The corrected focused invocation then passed 2/2. The first-run log is retained and is not counted as a product failure or a passing test.

## Evidence

- Initial focused log, including the 264-character temporary-path error: .local/android-test/reports/freeze-20261024/pc-issue-138-todo-projection-targeted.log; SHA-256 B7D9D1F6193CBDDFAC6DE59980315A0BFE03C708543B7A923C7220BAC9E2BC27.
- Corrected focused log: .local/android-test/reports/freeze-20261024/pc-issue-138-todo-projection-targeted-r2.log; SHA-256 1EBE0CB6315FE229326DF1D57BF29371A7632A61F45D4EF0AB85E06D6C028AF7.
- Focused JUnit XML: .local/android-test/reports/freeze-20261024/pc-issue-138-artifacts/TodoProjectionTest-targeted-r2.xml; SHA-256 6EAA67A276D584D0DF05A4F8A7060BE4885D36EDB5510099A30D5D0108C92336.
- Full regression command: .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain.
- Full Gradle log: .local/android-test/reports/freeze-20261024/pc-issue-138-full-gradle-r1.log; SHA-256 E50624CDF5EA42C27D06F06020E0FEE9F1E4AAE850418DA486836A4605200545.
- Full TodoProjectionTest XML: .local/android-test/reports/freeze-20261024/pc-issue-138-artifacts/TodoProjectionTest-full-r1.xml; SHA-256 9A9015D017283A6823BC5BCD409ABB395389F518F1A5F566A751F1CF3F2A4842.
- Lint XML: .local/android-test/reports/freeze-20261024/pc-issue-138-artifacts/lint-results-debug-r1.xml; SHA-256 2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47.
- Debug APK SHA-256: 35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4.
- AndroidTest APK SHA-256: D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A.
- Machine-readable run summary: .local/android-test/reports/freeze-20261024/pc-issue-138-artifacts/pc-issue-138-full-run-summary.json; SHA-256 982A5B0C3DB9BFE95FE6C5FB722056657B864D66F05240F94A2FEF797E90E411.
- Source/test commit: 58816435bbf401978daa28322e177100faeb38cc. No production code changed.

## Sign-off boundary

The three ledger rows now describe only the tested core projection and SQLite semantics. TodoScreen visual presentation, input-method/accessibility interaction and device/OEM behavior remain unverified. The P0 projection cases are signed within the recorded scope; this does not close unrelated P0 specifications, provider or external-device gates, and does not change the overall HOLD verdict.
