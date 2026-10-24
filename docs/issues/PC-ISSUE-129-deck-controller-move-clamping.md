# PC-ISSUE-129 — DeckController previous/next movement clamps

Date: 2026-10-24

Priority: P1 candidate (`REC-P1-040`)

Status: **PASS for narrowed JVM controller movement; swipe gesture mapping and overall freeze remain HOLD**

## Finding and scope

The existing core movement test verified one-card forward/backward movement and clamping at the final card. It did not verify that repeated previous movement clamps at the first card.

Extended `DeckControllerTest.insideSwipeMovesExactlyOneCardAndClampsAtTheEdges` with two assertions: from the second card, `move(-1)` returns to the first; another `move(-1)` remains on the first. The existing assertions still verify that `move(+1)` advances one card and stays on the final card when called again. No production source changed.

`REC-P1-040` is signed PASS narrowly for `DeckController.move()` direction, one-card step, and first/last boundary clamping. The test calls the controller directly with fake persistence. Compose gesture recognition, actual swipe direction mapping, input priority, and device behavior are not claimed.

## Verification evidence

Focused command:

```text
.\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.core.DeckControllerTest.insideSwipeMovesExactlyOneCardAndClampsAtTheEdges --offline --console=plain
```

Result: **1/1 PASS**. Log: `.local/android-test/reports/freeze-20261024/pc-issue-129-targeted.log`, SHA-256 `A6DF8B1298DF20686233F1FFA0F4DF397F3B1FF351D2B596C4816AC5F9B46A7C`.

Full command:

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL**, 467/467 JVM tests across 57 suites, zero failures/errors/skips; source boundary passed; Debug APK assembled; lint reported 0 errors and 58 warnings. Evidence:

- Full Gradle log: `.local/android-test/reports/freeze-20261024/pc-issue-129-full-gradle.log`, SHA-256 `9238AB927FA1150497321E095BD3F07FAC7440CD4796137AC105406A3B795FA8`.
- Full `DeckControllerTest` XML: `.local/android-test/reports/freeze-20261024/pc-issue-129-full-junit/TEST-dev.powerfulcalendar.core.DeckControllerTest.xml`, 17/17, SHA-256 `4D7A4E0EC2183572E046F3B36A2E86BC9CA70E1463E4E344F0F73CEA9F1BB8B0`.
- Lint XML: `.local/android-test/reports/freeze-20261024/pc-issue-129-full-junit/lint-results-debug.xml`, SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `E2EE9DF7C315E2BCD237457CEFC69B3ACF4D830F7D191447DDE724FD0E892A60`.
- Tests ran locally through Gradle/JVM. No emulator, physical device, or UI was exercised. The protected untracked `DraftDeckFeedbackTest.kt` participated in the full run and was not modified or staged.

## Git and freeze record

- Test commit `07557fe62a7ac4506f38618028aff9439b86da95` — `test(草稿卡组): 覆盖反向导航起点夹止` — pushed to `origin/main`.
- This report and the ledger/handoff updates are committed separately.
- Structural-only ledger artifact: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-129.json`, SHA-256 `46B90076E13454DF792678500CA49F1D3452DBFB77825E5C567950BD06AF17D8`; ledger SHA-256 `0230EDFF3547F140680396A60984A0A70DA819FF0C30CD1EA5646C1E6D410E6D`. It validates 209-row structure and candidate counts only; `product_behavior_tested` is false.

After this sign-off, reconstructed totals are **162 PASS / 0 FAIL / 9 BLOCKED / 31 NOT TESTED / 7 SPEC UNRESOLVED**; P0 `119/0/1/1/5`, P1 `43/0/8/30/1`. These rows remain unmapped to the original 209 case IDs. Core freeze remains **HOLD**.
