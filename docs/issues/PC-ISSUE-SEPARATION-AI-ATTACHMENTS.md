# PC-ISSUE-SEPARATION-AI-ATTACHMENTS — Clear AI attachment entry points

Date: 2026-10-27

Status: **Host picker bridge and Clear/Studio entry-point slice verified by JVM and Chrome Mock. File selection, extraction/OCR completion, Android WebView, live provider behavior, physical-device behavior, and full Native/Skin parity remain NOT TESTED. SEP-09/SEP-10 remain NOT TESTED.**

## Root cause

The native AI flow already had Android-owned file and image pickers, local ICS detection/import, bounded text extraction with consent review, and image/OCR review. Clear's AI composer had no image/file actions, so the skin could not enter those existing flows. Directly passing URI, MIME type, filename, bytes, or OCR content into JavaScript would cross the Host trust boundary.

## Change

- Added closed-schema, payload-free pickAiFile and pickAiImage v3 Host actions. Extra payload fields are rejected.
- The Host launches the existing Android pickers only from the AI page and only when editor, deck, batch, AI proposal review, file/image review, and picker locks permit it. Picker and review state is exposed as two booleans only; chat send is denied while attachment selection/review is active.
- Selected files continue through the existing Host-owned ICS parser or bounded extraction/consent path. Images continue through the existing Host-owned image/OCR review. URI and content are not projected to the skin. Opening either picker does not write calendar data.
- Clear adds accessible Image and File controls, disables them during Host locks/review, and explains why they are unavailable.
- Skin Studio supports both actions through an explicit non-reading Mock. The Mock never opens a picker or reads, uploads, imports, or OCRs content.
- Updated the v3 Host Bridge contract and the SEP-09/SEP-10 parity notes. Overall parity rows remain NOT TESTED.

## Verification

Focused command:

    .gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebSkinHostProjectionTest --tests dev.powerfulcalendar.ui.v2.WebAiProposalReviewIntegrationTest

Result: **14/14 PASS** (11 projection tests and 3 integration tests; zero failures/errors/skips). Tests cover the closed action schema, actual Robolectric Android picker intents for file and image, busy/availability state, pending AI review lock, and unchanged store counters on picker launch.

Full offline gate:

    .gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain

Result: **585/585 JVM tests, 82 suites, zero failures/errors/skips, 88/88 Gradle tasks successful**; source boundary and Debug/AndroidTest APK assembly passed; lint reported **0 errors / 52 warnings**.

Skin Studio and source checks:

- Python suite: 42/42 PASS.
- Chrome CDP source E2E: accessible attachment controls, both explicit non-reading Mock actions, and disabled controls during AI proposal review all PASS.
- node --check passed for Clear app.js, v3-studio.js, and e2e_v3_source.cjs.
- Canonical Clear package verification passed: 8 files, 376661 bytes.

Evidence (local, under .local/android-test/reports/separation-20261027/ai-attachment-entry/):

- Full Gradle log SHA-256: 8EE258EA0CF691787F72293C67F539DBC2C7A2D7D6EBAE8DE7735D0C61916E48
- Focused Gradle log SHA-256: 3B9D7F20245F68A52645E3EF09B41DC87F76D9C3840BBCCD931D27BDF15AA85C
- Studio Python log SHA-256: 8000309F46CEBBF8ACF1A433BD7B165227675B53CD1E904C63F2BAD598D70BD0
- Chrome E2E log SHA-256: D1B9DAC5E58C859FCC0C9AA66F729843B986C8D44BFAB41E34815E7CD2C24C66
- Debug APK SHA-256: B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D
- AndroidTest APK SHA-256: 219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6
- Canonical .pcskin SHA-256: 0E17AABFE216E8ECB0775DAFB8EA66857E25687B09E1167B7C68E1D9FFD81623
- Lint XML SHA-256: 649A98F4109A9CDAAC7F73CA909EE9B58CD14C6DCC70ED9D8E65C8F88A0C5211

## Device and provider limits

The repository-local ADB command could not start the ADB daemon in this environment. No emulator or physical-device interaction was performed for this slice. The picker was validated as an Android intent under Robolectric, which is not Android WebView/device evidence. No live provider or selected attachment was used. File-selection completion, consent/OCR behavior through the skin, provider errors/retry, and end-to-end evidence-to-proposal behavior remain NOT TESTED. The physical-device tests remain owner-run.

## Git

Implementation commit 030c920584f19f04c2d696da89652010d7ecac5c (feat(skin): 接通 AI 附件 Host 选择器) is pushed to origin/feat/skin-first-separation.
