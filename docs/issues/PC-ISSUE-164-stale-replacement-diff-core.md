# PC-ISSUE-164 — Complete stale replacement diff data

Date: 2026-10-24

Severity: P0 core-data integrity

Status: **CORE DIFF DATA PASS / MOBILE PRESENTATION SPEC UNRESOLVED / FREEZE HOLD**

## Finding

`REC-P0-049` requires a full before/after diff before approving a stale whole-object replacement. The existing `DraftDiff.between` omitted changed recurrence rules, time zone, provenance, completed occurrence keys, reminder method, and reminder timing. It also selected temporal rows from the proposed kind only, so converting an Activity to a Deadline hid the old start/end values.

A new pre-fix JVM regression expected 14 changed fields and received only 8. This reproduces the omission without opening or changing any UI.

## Change

`DraftDiff` now compares all editable snapshot fields represented by `DraftForm` and `TemporalRecord`. It reports type-specific start/end/deadline/date fields on both sides of a conversion, includes reminder method/timing and source/zone, and represents completion keys plus every recurrence field, exception, and override. Set/map-backed recurrence details and completion keys are sorted and escaped so equivalent snapshots produce stable diff text.

No UI, database schema, or business data was changed.

## Verification

- Pre-fix reproduction: `TemporalProtocolTest.staleReplacementDiffIncludesEveryChangedSnapshotField` failed as expected; expected 14 labels, actual 8. Log SHA-256 `A2AAFF1C7935B99F219A25CA97462E6E17A2932A0E66E2A09896F25CBEE404A0`.
- Post-fix focused JVM regression: **1/1 PASS**. It checks all changed snapshot fields, stable output under reordered recurrence sets/maps, and removal of old Activity times when the proposal becomes a date-only Deadline. Log SHA-256 `4468161CD44E67D8171280867676294E599F98241DBB27B062A19A6A365ABE73`.
- Forced offline full Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 496/496 JVM tests across 67 suites, zero failures/errors/skips; 88 tasks executed; source boundary and Debug/AndroidTest APK assembly passed; lint 0 errors / 58 warnings. Full log SHA-256 `C2077EB6869C400A221707087621685AD1FAC4CE2B31AC03739E4466B83504CE`.
- `TemporalProtocolTest` XML SHA-256 `90CFBF11E93AA61478A51F46D559055A63A8B38A1B0864795C53CE1E1EC90C62`; Lint XML SHA-256 `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`.
- Debug APK SHA-256 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK SHA-256 `79C9A6D83010087116B50D79012732BA9A6329D7D9B394661AD1C351D38D2F75`.

## Scope and remaining gate

This verifies the core diff data only. The readability of the full diff on a mobile screen still needs the prototype review recorded in `REC-P0-049`; it remains **SPEC UNRESOLVED**. This work does not sign off stale approval presentation or any Android-device behavior. `PC-ISSUE-164` closes the discovered core-data omission, while the overall freeze remains **HOLD**.
