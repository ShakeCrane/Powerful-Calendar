# PC-ISSUE-128 — DeckController completion counts

Date: 2026-10-24

Priority: P1 candidate (`REC-P1-046`)

Status: **PASS for narrowed JVM controller counts; timeline-camera restoration and overall freeze remain HOLD**

## Finding and scope

Existing controller tests checked completion after publishing the final card and showed one published item. They did not check a session that both publishes and discards drafts. This left the controller's published/discarded completion summary without a mixed-action assertion.

Added `DeckControllerTest.completionReportsPublishedAndDiscardedCountsForTheSession`. It publishes the first of two drafts, advances the test clock through the controller's one-second terminal-action guard, discards the final draft, and asserts the empty-queue completion reports `published=1` and `discarded=1`. No production code changed.

The initial focused attempt failed on `discard()` because the test attempted two terminal actions at the same fake-clock instant. The controller correctly rejected the second action under its existing interval guard. Adding the existing `tick()` helper between actions made the test represent the allowed user-action interval; the focused test then passed. This was a test-fixture timing issue, not a product defect.

`REC-P1-046` is signed PASS narrowly for DeckController's published/discarded counts after the queue becomes empty. The “restore previous timeline camera” portion is UI behavior and is excluded; fake persistence does not establish SQLite statistics. No UI, emulator, physical device, or production source was changed.

## Verification evidence

Initial focused attempt: **1 failure** at the second `assertTrue`, caused by the test clock not advancing. Log: `.local/android-test/reports/freeze-20261024/pc-issue-128-targeted.log`, SHA-256 `76F434F80549AFBCFBDC3AFBB9F497B75C056497B879E9A7032A3708C6C7B3B9`.

Corrected focused command:

```text
.\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.core.DeckControllerTest.completionReportsPublishedAndDiscardedCountsForTheSession --offline --console=plain
```

Result: **1/1 PASS**. Log: `.local/android-test/reports/freeze-20261024/pc-issue-128-targeted-after-clock.log`, SHA-256 `4B7209A14E46C328736FA66F11D487D0E1BAAA0A0389C14A02D01FDE457E08D1`.

Full command:

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL**, 467/467 JVM tests across 57 suites, zero failures/errors/skips; source boundary passed; Debug APK assembled; lint reported 0 errors and 58 warnings. Evidence:

- Full Gradle log: `.local/android-test/reports/freeze-20261024/pc-issue-128-full-gradle.log`, SHA-256 `2DD2527881F19E93110ACD7079F090C68CC861F5F8DE108C510A9FBCA0E3D8D6`.
- Full `DeckControllerTest` XML: `.local/android-test/reports/freeze-20261024/pc-issue-128-full-junit/TEST-dev.powerfulcalendar.core.DeckControllerTest.xml`, 17/17, SHA-256 `277408CF246C43DD079ADA38CA6B32834BC71D344E09EB8D2BEA05E9D2CADC09`.
- Lint XML: `.local/android-test/reports/freeze-20261024/pc-issue-128-full-junit/lint-results-debug.xml`, SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `E2EE9DF7C315E2BCD237457CEFC69B3ACF4D830F7D191447DDE724FD0E892A60`.
- Tests ran locally through Gradle/JVM. The protected untracked `DraftDeckFeedbackTest.kt` participated in the full run and was not modified or staged.

## Git and freeze record

- Test commit `5c741192727721cc26831a01fb9b9c9cd9b2b408` — `test(草稿卡组): 校验完成摘要计数` — pushed to `origin/main`.
- This report and the ledger/handoff updates are committed separately.
- Structural-only ledger artifact: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-128.json`, SHA-256 `C04462462D939BA4BFB4765FB6960E1219741F0C46A280836E567D1E460B0A44`; ledger SHA-256 `015CE8D4281738348B6BAFE9780D1912F0EF172998695228ADB0BF4929BDA878`. It validates 209-row structure and candidate counts only; `product_behavior_tested` is false.

After this sign-off, reconstructed totals are **161 PASS / 0 FAIL / 9 BLOCKED / 32 NOT TESTED / 7 SPEC UNRESOLVED**; P0 `119/0/1/1/5`, P1 `42/0/8/31/1`. The rows remain unmapped to the original 209 case IDs. Core freeze remains **HOLD**.
