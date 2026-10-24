# PC-ISSUE-164 — Complete stale replacement diff and mobile review

Date: 2026-10-25

Severity: P0 core-data integrity

Status: **CORE DIFF DATA PASS / MOBILE PRESENTATION PASS / REC-P0-049 PASS / FREEZE HOLD**

## Finding

`REC-P0-049` requires a full before/after diff before approving a stale whole-object replacement. The existing `DraftDiff.between` omitted changed recurrence rules, time zone, provenance, completed occurrence keys, reminder method, and reminder timing. It also selected temporal rows from the proposed kind only, so converting an Activity to a Deadline hid the old start/end values.

A pre-fix JVM regression expected 14 changed fields and received only 8. A follow-up review then compared the core diff contract with the actual `snapshotRows` consumed by the conflict dialog: the UI exposed 19 rows, omitting tracking state, completed occurrence slots, reminder method/timing, and the recurrence active-date bounds. The dialog had scroll support and an explicit confirmation action, but had no API-device evidence proving the full snapshot was visible and reachable.

## Change

`DraftDiff` compares all editable snapshot fields represented by `DraftForm` and `TemporalRecord`. It reports type-specific start/end/deadline/date fields on both sides of a conversion, includes reminder method/timing and source/zone, and represents completion keys plus every recurrence field, exception, and override. Set/map-backed recurrence details and completion keys are sorted and escaped so equivalent snapshots produce stable diff text.

The mobile snapshot now presents 25 rows, including the six missing business fields, and renders reminder modes, recurrence frequency, and weekdays as Chinese labels. No database schema or user business data changed.

## Verification

- Pre-fix reproduction: `TemporalProtocolTest.staleReplacementDiffIncludesEveryChangedSnapshotField` failed as expected; expected 14 labels, actual 8. Log SHA-256 `A2AAFF1C7935B99F219A25CA97462E6E17A2932A0E66E2A09896F25CBEE404A0`.
- Post-fix focused JVM regression: **1/1 PASS**. It checks all changed snapshot fields, stable output under reordered recurrence sets/maps, and removal of old Activity times when the proposal becomes a date-only Deadline. Log SHA-256 `4468161CD44E67D8171280867676294E599F98241DBB27B062A19A6A365ABE73`.
- Forced offline full Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 496/496 JVM tests across 67 suites, zero failures/errors/skips; 88 tasks executed; source boundary and Debug/AndroidTest APK assembly passed; lint 0 errors / 58 warnings. Full log SHA-256 `C2077EB6869C400A221707087621685AD1FAC4CE2B31AC03739E4466B83504CE`.
- `TemporalProtocolTest` XML SHA-256 `90CFBF11E93AA61478A51F46D559055A63A8B38A1B0864795C53CE1E1EC90C62`; Lint XML SHA-256 `44D47F58BAA079ADCD898DD75FD052E2E4069E1322BE0F69299819BC9A97694F`.
- Debug APK SHA-256 `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK SHA-256 `79C9A6D83010087116B50D79012732BA9A6329D7D9B394661AD1C351D38D2F75`.

## Mobile presentation closure (2026-10-25)

- Added `GlassPresentationTest.snapshotRowsExposeReminderModesTrackingAndCompletedOccurrenceSlots`; the conversion regression now requires all 25 rows, including recurrence active dates. Focused UI suites are included in the forced full run below.
- `ConflictReviewInstrumentedTest.fullBeforeAndAfterSnapshotIsScrollableAndRequiresExplicitConfirmation` ran against the final app and AndroidTest APKs on API 35 `emulator-5560` — **1/1 PASS**. It opens the production Compose deck/dialog with an isolated random SQLite database, verifies all 25 rows and both before/after values are scroll-reachable, verifies formal data is unchanged before approval, and verifies one revision is created only after tapping “确认整份覆盖”. The temporary database is removed in `finally`.
- Screen captures show the initial rows and the bottom of the scrolled diff with “保留待处理” and “确认整份覆盖” still reachable. Local evidence: `.local/android-test/reports/freeze-20261025/pc-issue-164-mobile-review/conflict-review-top-localized.png` SHA-256 `BF809CEE2E7E4E90848F280DE07DD9AD19553C20121AEA78C93F78952380BB94`; bottom capture `conflict-review-bottom-localized.png` SHA-256 `3E5663A4CB863D3D1D1A2FD333EFB1F488B0F9727147569D1D7CD41BCD7B0761`.
- Final forced offline Gradle command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain` — **541/541 JVM tests, 74 suites, 0 failures/errors/skips; 88/88 tasks; lint 0 errors / 56 warnings**. Log SHA-256 `3BCA1C55B5350479176B05FDF84D771352AEE4CD396322CF4D08ECD5141ED95E`.
- Final API 35 AndroidJUnitRunner log SHA-256 `3AB94505AB84610B68A5148971C685063F5DC68371055DAB08497AAC29EDBB8E`. APK bytes pulled back from `emulator-5560` match the final build: Debug SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614`; AndroidTest SHA-256 `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`.
- Screenshot inspection confirmed the 25-row dialog remains readable while scrolling and keeps both actions available. This is API 35 virtual-device evidence; physical/OEM review remains outside this report and is handled by the user.

## Scope and remaining gate

The core diff data and the full mobile before/after presentation required by `REC-P0-049` now pass in JVM and API 35 Compose verification. This closes PC-ISSUE-164 and that reconstructed P0 row. It does not close the overall PowerCal freeze; unrelated blocked, untested, and device-owned acceptance items remain.

## Commit and current matrix state

Core diff commit `f959bccb36b5a66d8cf2667fb0d213071f833d4d` and mobile presentation/test commit `9f9e82ae583a5d582e5b8b4307db98ec5606f149` are pushed to `origin/main`. The current matrix row is `REC-P0-049 PASS`; this report update and the corresponding ledger/progress/verdict update are committed separately.

The prior r32 counts above are the historical ledger snapshot from the core-only repair and predate this mobile closure. Current structural validation reports 218 rows (217 active reconstructed, 1 retired): 204 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 1 SPEC UNRESOLVED / 1 RETIRED; priorities P0/P1/P2 = 145/71/1; historical IDs mapped = 0. Current ledger SHA-256 `19002F65EA072B2B6FD266F61DF895DF9F0DDF316D0F0E6F38272EB3D94426FB`; structural validator artifact SHA-256 `B5C65D501F31958D0FA557994CF007AB6A56DD64EDBF696AF58B24011B51BA52`. The validator checks matrix structure only; product evidence is the JVM/API 35 test evidence recorded above.
