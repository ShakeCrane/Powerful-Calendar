# PC-ISSUE-070 — Enforce calendar-context opt-out at the AI client boundary

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-123`)

Status: **CLIENT OPT-OUT BOUNDARY PASS / PER-REQUEST UI AND LIVE MODEL NOT TESTED / OVERALL FREEZE HOLD**

## Finding

`AiClient.interpret` accepted a calendar-context list and serialized it without checking `AiSettings.shareContext`. Existing callers applied opt-in checks, but an accidental caller omission could still send event details when the persistent sharing setting was disabled.

## Change

- `AiClient.interpret` now uses the supplied calendar context in the outgoing prompt only when `settings.shareContext` is enabled.
- The original context list remains available to the local `AiResponseParser`; this change limits network disclosure without removing local target matching.
- Added `AiChatClientTest.interpretDoesNotSendCalendarContextWhenSharingIsDisabled`, which supplies a private event despite opt-out and inspects the request sent to a loopback stub.
- No UI, database, user data, or design asset changed. The existing caller-side per-request scope filter remains responsible for the precise set shared after opt-in.

## Verification

Environment: Robolectric API 34 and local loopback HTTP stub. No external model/network service or Android device was used.

- Focused test `AiChatClientTest.interpretDoesNotSendCalendarContextWhenSharingIsDisabled`: **1/1 PASS**, zero failures/errors. Log `.local/android-test/reports/freeze-20261024/pc-issue-070-ai-context-optout-targeted-20261024.log`, SHA-256 `48CEBC303D01FEA86733AFE9DD7368202FCB8667BF287550D039A894A8DD475C`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-070-targeted-junit/TEST-dev.powerfulcalendar.ai.AiChatClientTest.xml`, SHA-256 `B7324507C67D4EA41621BC3389133C7CC4F61FD0E5DA7CA251D2AA04D191C4F5`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, **422/422** tests across 55 suites, zero failures/errors/skips, lint 0 errors / 55 warnings. The run includes 421 tracked tests and one protected, untracked test; that protected file was not modified or staged. Log `.local/android-test/reports/freeze-20261024/pc-issue-070-full-gradle-20261024.log`, SHA-256 `35AACA17F04FCDCA7F0DE095C83123B0E70BD5F900656AB925CE6588C3C84CC4`; full `AiChatClientTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-070-full-junit/TEST-dev.powerfulcalendar.ai.AiChatClientTest.xml`, SHA-256 `53B6778219F326AF61685B68DC8538C2A9A0A43BE10BB0D610526CB746A786CA`.
- Debug APK SHA-256: `9C56BAEFD17D0EBF1D43BFE25BF02592C3EF20C108E92CD117EE03A6FD0BC134`.

`REC-P0-123` is PASS for the client boundary that honors the persistent context-sharing opt-out. The test does not verify the caller's per-request consent UI, precise allowed scope when sharing is on, or real provider behavior. The candidate remains `UNMAPPED` to the absent historical checklist, and overall freeze remains **HOLD**.

## Git

- Code/test commit `ad2286864d4b89ce51e7c9dd0fae3508bd52b817` (`fix(AI): 关闭共享时阻止发送日历上下文`) is pushed to `origin/main`.
- This report and the synchronized ledger/verdict updates are committed separately.
