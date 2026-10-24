# PC-ISSUE-130 — DeckController live edit state during autosave

Date: 2026-10-24

Priority: P1 candidate (`REC-P1-043`)

Status: **PASS for the narrowed JVM controller state contract; editor UI and overall freeze remain HOLD**

## Finding and scope

The existing concurrency regression held autosave inside persistence while a second edit arrived. It already checked that `edit()` returned quickly, the latest title was visible, and that the latest version was staged after the first write completed. It did not explicitly assert that the same card remained active and that the accepted text stayed dirty before the storage write could catch up.

Added two assertions to `DeckControllerConcurrencyTest.editingNeverWaitsForBackgroundAutosaveIo`: the current draft ID remains unchanged and `dirty` remains true while storage is blocked. The existing test then releases the write and verifies the latest title is staged. No production source changed.

`REC-P1-043` is signed PASS narrowly for the core controller reflecting accepted input on the active draft without waiting for background autosave I/O, retaining dirty state until the latest version is staged. The test uses fake persistence. Text-field rendering, focus/IME behavior, Compose wiring, SQLite durability, and device behavior are not claimed.

## Verification evidence

Focused command:

```text
.\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.core.DeckControllerConcurrencyTest.editingNeverWaitsForBackgroundAutosaveIo --offline --console=plain
```

Result: **1/1 PASS**. Log: `.local/android-test/reports/freeze-20261024/pc-issue-130-targeted.log`, SHA-256 `A6DF8B1298DF20686233F1FFA0F4DF397F3B1FF351D2B596C4816AC5F9B46A7C`.

Full command:

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL**, 467/467 JVM tests across 57 suites, zero failures/errors/skips; source boundary passed; Debug APK assembled; lint reported 0 errors and 58 warnings. Evidence:

- Full Gradle log: `.local/android-test/reports/freeze-20261024/pc-issue-130-full-gradle.log`, SHA-256 `F8DB2BA9E0ADE7AC75BE3045D61242CF5E575FEC98719F3809726D2239E649F9`.
- Full `DeckControllerConcurrencyTest` XML: `.local/android-test/reports/freeze-20261024/pc-issue-130-full-junit/TEST-dev.powerfulcalendar.core.DeckControllerConcurrencyTest.xml`, SHA-256 `6E7F00DBE0602FCE23BD0E6237C636EA42E65BF52FA5EBE03282EB97956C4391`.
- Lint XML: `.local/android-test/reports/freeze-20261024/pc-issue-130-full-junit/lint-results-debug.xml`, SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `E2EE9DF7C315E2BCD237457CEFC69B3ACF4D830F7D191447DDE724FD0E892A60`.
- Tests ran locally through Gradle/JVM. No emulator, physical device, UI, or live provider was exercised. The protected untracked `DraftDeckFeedbackTest.kt` participated in the full run and was not modified or staged.

## Git and freeze record

- Test commit `d838c9b6d6d7ecb8e2f6199a429d07eca0a86e12` — `test(草稿卡组): 验证编辑状态即时更新` — pushed to `origin/main`.
- This report and the ledger/handoff updates are committed separately.
- Structural-only ledger artifact: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-130.json`, SHA-256 `39340110A14157DEA49F09AF6206579C953D02D4355032D042C32CAD9A160A16`; ledger SHA-256 `2A02489CD27FF377E677ABDDFFFFA93190966CC53E38D6F738226CEB554D0338`. It validates 209-row structure and candidate counts only; `product_behavior_tested` is false.

After this sign-off, reconstructed totals are **163 PASS / 0 FAIL / 9 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED**; P0 `119/0/1/1/5`, P1 `44/0/8/29/1`. These rows remain unmapped to the original 209 case IDs. Core freeze remains **HOLD**.
