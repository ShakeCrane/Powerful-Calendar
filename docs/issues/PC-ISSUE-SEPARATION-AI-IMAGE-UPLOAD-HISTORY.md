# PC-ISSUE-SEPARATION-AI-IMAGE-UPLOAD-HISTORY — image consent and chat persistence

Date: 2026-10-27

Status: **This bounded Robolectric integration slice passes. No production source changed. SEP-09 and SEP-10 remain NOT TESTED as full Native/Skin parity rows; S3 remains open.**

## Scope and finding

The preceding attachment report covered selected text-file extraction, redaction, explicit submission and a pending typed proposal. It did not cover the image result path or raw-image privacy in persisted chat history. This change adds those regression checks and a direct chat-history replacement regression.

The first full-suite run exposed a Windows Robolectric host issue: Android `AtomicFile` commits by replacing its base file with the `.new` file, while Java `File.renameTo` on this Windows host does not replace an existing target. That failure did not demonstrate a production Android defect. The test now uses a Robolectric-only `ShadowAtomicFileReplaceOnWindows` backed by `Files.move(..., REPLACE_EXISTING)` to model the Android replacement contract; no production storage code changed.

A subsequent full run also caught a test-side race: the test read the history file directly while the asynchronous chat-history write/replace was in progress. The test now waits, with a bounded timeout, for the expected persisted contents to become readable. The final full suite passes with this synchronization.

## Verified behavior

`WebAiAttachmentFlowIntegrationTest.skinImageRequiresExplicitUploadConsentAndNeverStoresRawImageInChatHistory` drives the production `PowerCalApp` and `WebSkinBridgeRuntime` path using a synthetic one-pixel PNG, a local `ContentProvider`, a recorded Android picker result and a loopback HTTP model stub.

- Returning an image from the picker does not submit it. Cancelling the consent prompt also sends no request and leaves calendar-store counters unchanged.
- After the explicit “发送原图给 AI” action, the local stub receives the selected image as a PNG data URI. The request is local and synthetic; no live model or external service is used.
- The Host projection and persisted conversation retain the consent marker and assistant response. Neither the decoded chat history nor the serialized history file contains the image's base64 bytes.
- Clearing chat empties the Host projection and durably replaces the private history file with `[]`; formal events, drafts, identities and commit counts remain unchanged.
- `chatHistoryStoreReplacesLongerSnapshotWithShorterAndEmptyHistory` verifies that a shorter snapshot replaces old content and that an empty snapshot replaces it with `[]`.

The Windows shadow is confined to this Robolectric test class. It models file replacement but is not evidence of Android filesystem crash behavior. The independent selected-text-file redaction/proposal flow remains covered by `skinSelectedFileCanBeRedactedBeforeAiProposalAndDoesNotWriteCalendarData`.

## Test evidence

Focused command, run three times after the synchronization fix:

    .\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest --tests dev.powerfulcalendar.ai.AiConversationTaskSnapshotTest.persistedChatRetainsEvidenceIdsTimesAndReconstructsSameTaskRevision

Each run passed **4/4 tests** (3 attachment-flow tests plus 1 persisted-conversation snapshot test), with zero failures/errors/skips. Logs:

- `attachment-class-repeat-fixed-1.log`, SHA-256 `4F83E65658AE1A1BBD1907B4CEEA5960475D28AF0A5655173963329CB64A510A`
- `attachment-class-repeat-fixed-2.log`, SHA-256 `869F3AE15FA15845A8AFD57B2589EA9C742778B762027846BC77415A81A624B5`
- `attachment-class-repeat-fixed-3.log`, SHA-256 `5045300FBCC9F2008290BE2EA757DCF153B4A8330D54549D30F6C91AAAB3AB78`

Full offline gate:

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain

Final result: **BUILD SUCCESSFUL; 588/588 JVM tests, 83 suites, zero failures/errors/skips; 88/88 Gradle tasks successful.** Source-boundary verification, Debug APK, AndroidTest APK and lint passed. Lint reported **0 errors / 52 warnings**. Final log SHA-256: `203CBEC9A015ACADD9C63853B161677DFB9E15B3A1D9CA3055A211D31DECAAED`.

The earlier full-gate attempt before the bounded file-read wait had 1 failure in 588 tests (Windows file lock during a direct test read). Its diagnostic log SHA-256 is `0BF4D37896AC90C0439BF7822EFE577D46501D33BDA1DC232983E282401A1DDE`; it is retained and is not counted as passing evidence. The final run above followed the test-harness fix.

## Reproducible artifacts

All logs and test artifacts are under `.local/android-test/reports/separation-20261027/ai-attachment-followup/` (ignored local evidence):

- Final attachment integration JUnit XML SHA-256: `DE9288C90AE9422FAB854353C2BA8812068CE606BAB2092984275F07D169ACD7`
- Persisted conversation snapshot JUnit XML SHA-256: `F1D35E50D6A8DC1BA786CDAC0C4B9A1FF7303CC04C7259004028481E890A4076`
- Test source SHA-256: `BE1A1AC41FA833F6EB47A687FFB4AFEFC13483FABD633859A2E373EF49634770`
- Debug APK SHA-256: `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D`
- AndroidTest APK SHA-256: `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`

No emulator binary or Android system image is available in this SDK environment. Actual Android chooser UI, Android WebView/device behavior, OCR, ICS, live-provider errors/retries and physical/OEM behavior are **NOT TESTED**. The user performs physical-device testing. SEP-09 and SEP-10 remain **NOT TESTED** as complete parity rows.

## Git

The test-only change and narrow SEP-09 evidence update were committed as `327a9aa9604d83bffa0154f795faeabb3913e22a` (`test(skin): 验证图片上传授权与聊天历史隐私`) and pushed to `origin/feat/skin-first-separation`.
