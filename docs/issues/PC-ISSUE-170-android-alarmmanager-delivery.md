# PC-ISSUE-170 — API 35 Android AlarmManager delivery probe

Date: 2026-10-24

Status: **API 35 AVD subcase PASS / REC-P0-012 remains scoped PASS / core freeze HOLD**

## Scope

`REC-P0-012` already had an isolated Robolectric test for the reminder delivery handler, but that test called the handler directly and did not establish that Android `AlarmManager` could deliver a `PendingIntent` to the running app process. This issue adds a virtual-device probe for that evidence gap. No production source, manifest, or calendar database changed.

On API 35 `emulator-5556`, the instrumented test created one synthetic tracked alarm in a uniquely named SQLite database under the product app's cache directory. It scheduled a real `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)` broadcast `PendingIntent` for a random action. A non-exported dynamic probe receiver registered in the product process received the OS delivery and called the production `ReminderReceiver.handle()` with the isolated database context.

The probe deliberately does **not** claim coverage of the production manifest component's `ReminderReceiver.onReceive()` wrapper or manifest resolution. It establishes OS AlarmManager dispatch into the product process and the production delivery handler; physical/OEM behavior and doze-specific timing remain unverified.

## Results

- Focused AndroidJUnitRunner: **1/1 PASS**, elapsed `68.578s`.
- Trace: API `35`; synthetic `eventSeq=968254649`; scheduled at `1792845021227`; nominal trigger at `1792845060000`; callback received at `1792845089335` (29.335 seconds after the nominal trigger); handler result `execution=FIRED;notification=true`.
- The test reopened the isolated store and confirmed `lastExecution=FIRED` and `completedKeys` remained empty. The notification was active before cleanup.
- The test canceled its PendingIntent and notification and deleted its uniquely named database, SQLite sidecars, result file, and cache directory. Post-run inspection found the product cache empty, no matching pending alarm, and no active notification match for notification ID `255649`.
- `POST_NOTIFICATIONS` started denied, was temporarily granted for this test, then revoked and verified denied. `SCHEDULE_EXACT_ALARM` remained at app-op `default`.
- Full forced-offline Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**; 496/496 JVM tests across 67 suites, 0 failures/errors/skips; all 88 tasks executed; lint 0 errors / 58 warnings.

Evidence SHA-256:

- Focused AndroidJUnitRunner output: `5315100F6B37DD5415B0D71BB56A5D7324738C370B5396710E9BBB0AC5C85C84`.
- Device state, exact logcat delivery trace, and post-run cleanup/permission checks: `3AF33A778F861F765CE0D82B5AD892A6E20251FCBCB30555ED7CF0B086C1ED61`.
- Full Gradle log: `1079FAEC6CE85C420268DBB7B2384CA57CD4BC346AE625D073C6912B96052B1C`.
- Test source: `4BE7E6E6949F32C87B2C11FCD61F306DD6B71326437F63178BD3C8A1D27C0888`.
- Debug APK: `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK: `3C7A2EB5FB9EB86FD5238DC947CA322842379E3C277F9CA6334EDE569B95B26E`.

Detailed logs are under `.local/android-test/reports/freeze-20261024/pc-issue-170-alarmmanager-delivery/`.

Earlier harness iterations failed during test-package permission or private-directory setup before any alarm was scheduled. They were test-fixture failures, not product behavior failures; target notification permission was restored after those attempts. The final run above is the first successful OS-delivery result.

## Acceptance ledger and remaining limits

`REC-P0-012` remains PASS: the existing completion-isolation assertions are now supplemented by this API 35 OS-dispatch subcase. This does not sign off the manifest receiver wrapper, physical devices, OEM scheduling, doze behavior, notification-shade gestures, or long-duration stability. The overall freeze remains **HOLD**.

Structural-only ledger r39 records 211 rows / 210 active reconstructed, 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `AF328E0699039CBAD064B0D115A9148AD57FB5C30A4F2552844D79EC8ED23022`; ledger SHA-256 `AC66D9950F8FF95129018843B1EE23297C41783BEFDFCE3430AE4D0B30CA4246`. The flag describes the structural validator and does not negate the separate test evidence above.

## Commit

Test-only commit `93c08635b6a4b1c81b7c60f3f7194e6df1178577` (`test(提醒): 验证 API 35 AlarmManager 投递`) is pushed to `origin/main`.
