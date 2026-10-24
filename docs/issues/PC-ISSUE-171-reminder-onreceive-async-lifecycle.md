# PC-ISSUE-171 — ReminderReceiver asynchronous broadcast lifecycle

Date: 2026-10-24

Status: **Robolectric API 34 wrapper subcase PASS / REC-P0-012 remains PASS / core freeze HOLD**

## Scope

PC-ISSUE-170 established that API 35 `AlarmManager` can deliver a `PendingIntent` to the product process and that the production reminder handler records `FIRED` without completing the tracked event. This follow-up exercises the production `ReminderReceiver.onReceive()` and its `goAsync()`/`PendingResult` completion path against an isolated database.

The test uses Robolectric API 34's `ShadowBroadcastReceiver` to dispatch a synthetic `ACTION_FIRE` to the production receiver with a framework-style `PendingResult`. It does not resolve or launch the production manifest component and is not a real OS broadcast. PC-ISSUE-170 remains the separate API 35 OS AlarmManager delivery evidence. Neither test establishes physical/OEM behavior.

## Results

- Focused JVM regression `ReminderCompletionIsolationTest.receiverOnReceiveAsyncKeepsEventIncomplete`: **1/1 PASS**, zero failures/errors/skips.
- The Robolectric receiver shadow confirmed `goAsync()` was entered; the test waited for the associated `PendingResult` future to finish before reading the isolated store.
- The production handler recorded `FIRED`, removed the consumed schedule intent, preserved empty completion keys, and left the event revision unchanged.
- The test canceled the synthetic alarm and notification and deleted its uniquely named temporary database. No product source or persistent application data changed.
- Full forced-offline Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**; 497/497 JVM tests across 67 suites, 0 failures/errors/skips; all 88 tasks executed; lint 0 errors / 58 warnings.

Evidence SHA-256:

- Final focused Robolectric test log: `910116087C1346F09CA42385BE3BB8C2AD8FCF52C83081F07A35A1EC1B2BAA1D`.
- Focused JUnit XML: `FB2392D980D0C9FEF72E247CE4203DC9A49A19B5B73C46C57127641DBE42E46B`.
- Full Gradle log: `8911CC859F7418CE501C66F2D1B4665735F00175AB5E10EB055E1879F668F404`.
- Test source: `988E13A24E3C6E9B0B9A413D3952F1C05D4D45AB15291E4412724502290561F6`.
- Debug APK: `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK: `3C7A2EB5FB9EB86FD5238DC947CA322842379E3C277F9CA6334EDE569B95B26E`.

The first standalone attempt stopped before receiver dispatch because its Robolectric database path was 269 characters and the sandbox directory was not created. The test name was shortened and the shared fixture now explicitly creates the temporary database directory; the final targeted and full runs passed. The initial harness log SHA-256 is `F81F0F02379C96DF76DA72641D7B1623549E6E5FEB40ABADC97145DE169FB9E1`.

Detailed logs are under `.local/android-test/reports/freeze-20261024/pc-issue-171-reminder-wrapper/`.

## Acceptance ledger and remaining limits

`REC-P0-012` remains PASS with complementary evidence: PC-ISSUE-170 covers real API 35 AlarmManager delivery to the product process and the production handler; this issue covers production `onReceive/goAsync` and `PendingResult` completion under Robolectric. Production manifest resolution, a real system dispatch to the manifest receiver, notification-shade gestures, Doze/OEM scheduling, physical devices, and long-duration stability remain unverified. The overall core freeze remains **HOLD**.

Structural-only ledger r40 records 211 rows / 210 active reconstructed, 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `C4C13745AEE22C9D209C1E6B6FCCD14E6847B4C503A4C886097A59210F5AC736`; ledger SHA-256 `E426C141D8C3DEDD15AD0EE2170518B32B369E6B0570A62096AB6635488D535A`. The flag describes structural validation and is not this test's result.

## Commit

Test-only commit `3bcb6ef86b313ca33d7df77155c04dc70f8b3318` (`test(提醒): 覆盖异步 receiver 生命周期`) is pushed to `origin/main`.
