# PC-ISSUE-048 — Semantic no-op Save does not advance revision

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

SQLite v4 requires an explicit Save that makes no semantic change to avoid creating a new formal revision. The existing `PowerStoreTest.directEditorCheckpointsOnceAndNoChangeSaveClearsItWithoutRevision` directly exercises this behavior through `EditSession` and the isolated SQLite store.

## Change

No product or test source change was needed. The existing focused test was rerun on the current code/test baseline and `REC-P0-058` was signed PASS with one-to-one evidence. No UI behavior is claimed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.directEditorCheckpointsOnceAndNoChangeSaveClearsItWithoutRevision" --offline --rerun-tasks --console=plain` — **1/1 PASS**, zero failures/errors/skips.
- The test edits a formal event in memory, checkpoints the edit, discards that editing change, then Saves. It asserts `Saved(changed=false)` and the original formal revision. The test's checkpoint was cleared by the explicit discard before Save; it does not sign the separate Save-clears-active-checkpoint behavior.
- Targeted log: `.local/android-test/reports/freeze-20261023/pc-issue-048-no-op-save-targeted-20261023.log`, SHA-256 `6F959D8AFB206867FA9F78B43217BD63E523F0FC0675CD5743FD83DB47FBB526`.
- Targeted JUnit XML: `.local/android-test/reports/freeze-20261023/pc-issue-048-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, one test, zero failures/errors/skips; SHA-256 `A98DE9B228C55EC45A47E62301D5BC492E68ED749B5C3BE55D1837C6CEFC4FC6`.
- The latest complete Gradle run on this unchanged code/test baseline was **407/407 PASS** across 55 suites, with lint **0 errors / 55 warnings**. Its `PowerStoreTest` XML explicitly contains this exact test method (60 tests, zero failures/errors/skips). The full run was not repeated because this issue required no source change.
  - Log: `.local/android-test/reports/freeze-20261023/pc-issue-044-whole-snapshot-replacement-full-gradle-20261023.log`, SHA-256 `236ABC1658703D51D816D1D56E24ECB2A037F10E9DEEB87D0A65C79AA3CE0663`.
  - XML: `.local/android-test/reports/freeze-20261023/pc-issue-044-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `1F9E906660EE8FC700BE0030E176A1B0D6A871BE62A8A6EFA6BA00B829357047`.
  - Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

No Android emulator or physical device was used. No UI, database schema, business data, or protected untracked asset changed.

- Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-048.json`, SHA-256 `B5CC36E207EF5BBC234CF68B64D2A72BBB6386B04A03030B470597CEB6DBFC0C`; ledger SHA-256 `17BB6902F14D99EBCE329D88D94AED62C60EDB41F988976796B2281E604330CF`. It confirms 209 unique rows, the 126/82/1 priority partition, and 93/0/9/100/7 status counts. It checks structure and accounting only; `product_behavior_tested` is false.

## Candidate sign-off and limits

`REC-P0-058` is PASS in the reconstructed candidate ledger and remains `UNMAPPED` to the unavailable original 209-case checklist. Evidence covers the PowerStore/EditSession storage contract only. Overall core freeze remains **HOLD**.

## Git

There is no code/test source commit for this sign-off. The independent report and synchronized ledger/verdict/handoff documentation are committed and pushed in a separate Chinese conventional commit; the task closure records its SHA.
