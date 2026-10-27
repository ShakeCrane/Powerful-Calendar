# PC-ISSUE-SEPARATION-AI-ATTACHMENT-REVIEW — selected-file redaction and proposal flow

Date: 2026-10-27

Status: **The bounded selected-text-file → privacy review → local AI proposal path passes Robolectric integration tests. This is not full SEP-09/SEP-10 parity. Android chooser UI, image/OCR, ICS flow, live provider, Android WebView/device behavior, proposal decision, and complete Native/Skin replay remain NOT TESTED.**

## Root cause and scope

The preceding attachment slice verified that Clear could ask the Android Host to launch its existing file/image pickers, and that the request did not write calendar data. It did not exercise a returned file through Host-owned extraction, user redaction, explicit submission, and proposal display. That was an evidence gap, not a demonstrated production defect.

This change adds one integration regression test. No production source or runtime behavior changed.

## Verified flow

`WebAiAttachmentFlowIntegrationTest.skinSelectedFileCanBeRedactedBeforeAiProposalAndDoesNotWriteCalendarData` drives the production `PowerCalApp` and `WebSkinBridgeRuntime` path. A recording `ActivityResultRegistry` captures the production `OpenDocument` contract and returns a synthetic `content://` URI backed by a local test `ContentProvider`; this exercises the result callback and Host intake, not the system chooser UI.

The Host shows the extracted text in its privacy review. The test removes `PRIVATE_ONLY=REMOVE_ME`, edits the review field, and explicitly sends the remaining text. A loopback HTTP stub returns one typed `CREATE/ACTIVITY` proposal. Assertions verify that:

- The actual outbound request contains the reviewed text and never contains the redacted secret.
- One typed CREATE proposal appears in the Host review projection.
- Draft count, formal-event count, identity count, and commit count remain unchanged before and after proposal generation.
- The one-time upload choice does not become remembered provider consent.
- Attachment controls remain locked while the proposal is unresolved.

The database and preference namespace are unique to each test run. The provider, endpoint, and model response are local synthetic fixtures; no live AI provider or external network is used.

## Test evidence

Focused command:

    .\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest

Result: **1/1 test PASS**, zero failures/errors/skips. The focused Gradle output ends with `BUILD SUCCESSFUL` (32 tasks).

Full offline command, run against the final test source with JDK 20.0.1 and Gradle 8.14.3:

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain

Result: **586/586 JVM tests, 83 suites, zero failures/errors/skips; 88/88 Gradle tasks successful.** Source-boundary verification, Debug APK assembly, AndroidTest APK assembly, and lint passed. Lint reported **0 errors / 52 warnings**.

The test is Robolectric API 35 integration evidence. No Android emulator or physical device was used. The prior attachment-entry report records that ADB could not be started in this environment; physical-device checks remain owner-run.

## Reproducible artifacts

Local evidence is under `.local/android-test/reports/separation-20261027/ai-attachment-followup/`:

- Focused Gradle output SHA-256: `9A455708E3F7873BB79E0494CB78B4D5777033BE66BAE79EBD5389AD8EB00B4D`
- Focused JUnit XML SHA-256: `34B0B1C463DBE882AA5255047CD645F3653867B7CFF2DC81A4EB2A87519E98E5`
- Full-gate result summary SHA-256: `9AF0AF877AAD13316709CFE1647BE4FB2C98B597F19FACB9C84E9184DE39B27E`
- Final test source SHA-256: `D6AB751E8EF69381245C17551A7DF7B0CCAF15EA79622AE50A44266C61C3C0F3`
- Lint XML SHA-256: `649A98F4109A9CDAAC7F73CA909EE9B58CD14C6DCC70ED9D8E65C8F88A0C5211`
- Debug APK SHA-256: `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D`
- AndroidTest APK SHA-256: `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`

The full-gate XML suite outputs remain under `app/build/test-results/testDebugUnitTest/` (ignored build output). The full Gradle run printed `BUILD SUCCESSFUL` and returned exit code 0.

## Remaining limits and parity status

This slice does not verify image upload or local OCR, ICS import, attachment denial/unsupported types, provider failure/retry/cancel behavior, app restart recovery, actual Android chooser UI, Android WebView, physical-device behavior, live provider behavior, or the full Native-versus-Skin feature matrix. It leaves the proposal unresolved and does not exercise publish/stage/reject/supplement from this attachment path.

SEP-09 and SEP-10 therefore remain **NOT TESTED** as full parity rows. S3 remains open.

## Git

Test-only commit `52b2d0b97b89d6aa990a8d767abbe8ae8b34a268` (`test(skin): 验证附件隐私脱敏链路`) is pushed to `origin/feat/skin-first-separation`.