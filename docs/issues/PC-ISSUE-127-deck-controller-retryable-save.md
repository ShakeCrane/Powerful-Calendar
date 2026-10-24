# PC-ISSUE-127 — DeckController save recovery and card navigation

Date: 2026-10-24

Priority: P1 candidate (`REC-P1-044`, `REC-P1-045`)

Status: **PASS for the narrowed JVM controller contracts; UI, lifecycle, durable-store and overall freeze remain HOLD**

## Finding and scope

The controller already refused to navigate after a failed stage and retained the accepted form as dirty state. Its concurrency test also checked that a storage exception exposed a retryable error while keeping the input. The coverage gap was that it stopped after the failure: it did not prove a later retry could save the same input and clear the error state.

The change adds assertions to `DeckControllerConcurrencyTest.storageFailureKeepsAcceptedInputDirtyAndPublishesRetryState`. After confirming the injected failure state, the test removes the failure, calls `flush()` again, and verifies the latest title is stored and `dirty`, `error`, and `busy` are cleared. No production source was changed.

`REC-P1-044` is signed PASS narrowly for the JVM controller behavior that stages the latest accepted form before changing cards and blocks navigation on stage failure. `REC-P1-045` is signed PASS narrowly for retained controller input, a retryable error state, and successful retry after the transient failure is removed. Both tests use fake `DeckPersistence`; SQLite durability, exit/lifecycle handling, retry UI, Android UI, and physical-device behavior are not claimed.

## Verification evidence

Focused command:

```text
.\gradlew.bat :app:testDebugUnitTest --tests dev.powerfulcalendar.core.DeckControllerConcurrencyTest.storageFailureKeepsAcceptedInputDirtyAndPublishesRetryState --offline --console=plain
```

Result: **1/1 selected test passed**. Log: `.local/android-test/reports/freeze-20261024/pc-issue-127-targeted.log`, SHA-256 `A6DF8B1298DF20686233F1FFA0F4DF397F3B1FF351D2B596C4816AC5F9B46A7C`.

Full command:

```text
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL**, 466/466 JVM tests across 57 suites, zero failures/errors/skips; source boundary passed; Debug APK assembled; lint reported 0 errors and 58 warnings. Evidence:

- Full Gradle log: `.local/android-test/reports/freeze-20261024/pc-issue-127-full-gradle.log`, SHA-256 `5048666A731DA92219D3B977A74DD4E68DED9F6DAF87DC7678D0993CC64F6700`.
- Full `DeckControllerConcurrencyTest` XML: `.local/android-test/reports/freeze-20261024/pc-issue-127-full-junit/TEST-dev.powerfulcalendar.core.DeckControllerConcurrencyTest.xml`, 8/8, SHA-256 `BBF0C6436C81992CDE0FBB3B52D2FF97B4C94D97FC0E996DCA3EC22C2EB0ACE8`.
- Lint XML: `.local/android-test/reports/freeze-20261024/pc-issue-127-full-junit/lint-results-debug.xml`, SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `E2EE9DF7C315E2BCD237457CEFC69B3ACF4D830F7D191447DDE724FD0E892A60`.
- Tests ran locally through Gradle/JVM. No emulator, physical device, UI, or live provider was exercised. The protected untracked `DraftDeckFeedbackTest.kt` participated in the full run and was not modified or staged.

## Git and freeze record

- Test commit `41c7f849438128960b6f453c44563141feb0d1e5` — `test(草稿卡组): 验证暂存失败后可恢复重试` — pushed to `origin/main`.
- This report and the ledger/handoff updates are committed separately.
- Structural-only ledger artifact: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-127.json`, SHA-256 `77CEBCB85D696C3A5121B78EE1AED29F611AA6DFBB86B7FD8642109B0FF6E5E6`; ledger SHA-256 `38E9BEE15710B9F59029E6C992B52BE1D48721A7320754994A044F08E16157C`. It validates 209-row structure and candidate counts only; `product_behavior_tested` is false.

After this sign-off, reconstructed totals are **160 PASS / 0 FAIL / 9 BLOCKED / 33 NOT TESTED / 7 SPEC UNRESOLVED**; P0 `119/0/1/1/5`, P1 `41/0/8/32/1`. The rows remain unmapped to the original 209 case IDs. Core freeze remains **HOLD**.
