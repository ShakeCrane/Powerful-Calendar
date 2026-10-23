# PC-ISSUE-049 — Recurrence start bound preserves cycle anchor

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE TIME-MODEL REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Setting an effective recurrence start date after the original cycle anchor must not rephase the recurrence. `PowerCalMemoContractTest.recurrenceEffectiveDatesDoNotShiftCycleAnchor` directly tests projection with an `activeFromDate` later than the event's original anchor.

The test creates a daily Alarm anchored at 2026-10-01 09:00, with inclusive effective dates 2026-10-03 through 2026-10-05. Projection returns exactly those three local-date keys; asking for the next occurrence from 2026-10-02 09:00 returns 2026-10-03, while `anchorMillis()` remains the original instant.

This signs the time-model projection invariant. It does not test editing an existing rule through an editor or saving it to SQLite.

## Change

No product or test source change was needed. The existing focused regression was rerun on the current code/test baseline and `REC-P0-059` was narrowed to the exact behavior proved by the test, then marked PASS. No UI work or device behavior is claimed.

## Verification evidence

Environment: JVM/Robolectric API 34, deterministic `java.time` recurrence model; no Android device.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerCalMemoContractTest.recurrenceEffectiveDatesDoNotShiftCycleAnchor" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- Log: `.local/android-test/reports/freeze-20261023/pc-issue-049-start-bound-anchor-targeted-20261023.log`, SHA-256 `8CF359ED5B02675EF95C78DC74B5A7A12A453F7BB676F4E7BC60AAAF362972E2`.
- JUnit XML: `.local/android-test/reports/freeze-20261023/pc-issue-049-targeted-junit/TEST-dev.powerfulcalendar.core.PowerCalMemoContractTest.xml`, one test, zero failures/errors/skips; SHA-256 `23301D92460F6A020C5536EB078FCD7E05B69474EB478033AC0F483366C1C5C7`.
- The most recent complete Gradle regression on this unchanged code/test baseline was **407/407 PASS** across 55 suites, lint **0 errors / 55 warnings**. The full run was not repeated because this sign-off required no source change. Log: `.local/android-test/reports/freeze-20261023/pc-issue-044-whole-snapshot-replacement-full-gradle-20261023.log`, SHA-256 `236ABC1658703D51D816D1D56E24ECB2A037F10E9DEEB87D0A65C79AA3CE0663`.
- Debug APK SHA-256 from that full run: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

No editor/UI, emulator, physical device, schema, business data, or protected untracked asset changed.

- Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-049.json`, SHA-256 `0D24DAF4E21AFE6E62F6EC0519AC6BFD7979F7D07AC430E7BBA3EBA89F3EB83B`; ledger SHA-256 `D4806E0C6BC237D2A5BE1E0CE83C7336396DFCAD26CF5F7E786EA29A9CCF84F8`. It confirms 209 unique rows, the 126/82/1 priority partition, and 94/0/9/99/7 status counts. It is structural-only; `product_behavior_tested` is false.

## Candidate sign-off and limits

`REC-P0-059` is PASS in the reconstructed ledger and remains `UNMAPPED` to the unavailable original 209-case checklist. It covers recurrence projection only. Overall core freeze remains **HOLD**.

## Git

There is no code/test source commit for this sign-off. The independent report and synchronized ledger/verdict/handoff documentation are committed and pushed in a separate Chinese conventional commit; the task closure records its SHA.
