# PC-ISSUE-175 — Uniform outside timeline titles and restored AI/calendar bridge

Date: 2026-10-25. Status: CODE + JVM VERIFIED; live provider and physical/OEM acceptance remain HOLD.
Based on main commit 4325aae; see the subsequent fix commit for the resulting HEAD.

## Scope 1: Timeline titles

Prior to fix, Activity titles could shrink toward 10sp, a user-visible "inside/outside" toggle changed placement, and point-event titles (Alarm/Deadline) bypassed the measured greedy fold.

Changes:
- Single 13sp font size for ALL timeline event title drawings on both left and right wings; long labels are one-line ellipsized, not dynamically shrunk.
- Placement is always outside/above the corresponding event or point marker; remove inside/outside control and ignore the obsolete saved preference without touching other user settings.
- Alarm and Deadline labels now use the same measured, clipped screen-rectangle greedy fold as Activity. Each lane/wing maintains an independent visible lead; hidden titles do not suppress later candidates.
- Event boxes, marker geometry, original occurrence identity and full accessibility content descriptions remain unchanged.
- The visible outside label now supports clicking the original event and long-press dragging it, closing an actual focused test regression caught during implementation.

Tests: existing GreedyTitleFoldingTest and TimelineTitleComposeTest, new left-wing Alarm fold test; fixed-size text/ellipsizing and title hit/drag regressions.

## Scope 2: AI-to-calendar integration

Historical baseline: before commit 2692ca2, the chat stage sent the model-provided marker's current user source to a structured second stage. The later safety fix introduced a dated task snapshot, but automatic preview could be lost if a provider omitted/misclassified the private marker or if the user expressed an unambiguous calendar statement without a create verb. The original conservative data-source rule must not be reversed.

Changes:
- AiChatIntentRouting provides deterministic, bounded fallback for clear, user-authored calendar commands even if the chat provider omits an internal marker. Confirmations attach only to an existing safe task and a relevant assistant question, not to arbitrary chat.
- AiConversationTaskSnapshot can recognize a dated standalone schedule statement ONLY when the provider explicitly classified it as actionable. User messages keep original IDs, timestamps, timezone and corrections. No model "source" text is trusted for operation inputs.
- Opt-in read-only calendar context is now wired into the ordinary chat stage. The snapshot is bounded to 60 records, includes kind/ID/dates/recurrence and excludes private notes. No context is sent when shareContext is disabled; calendar read context is not an authorization to change events.
- The existing structured preview and explicit user review/commit flow remains the sole path to formal writes.

Regression tests: AiChatIntentRoutingTest (explicit command without marker, standalone explicit statement, ordinary casual mention, confirmation anchoring, unrelated confirmation), AiChatCalendarContextTest (privacy, bounded scope and read-only envelope), and existing chat client/multi-turn tests.

## Verified evidence

- Focused regression log: .local/android-test/reports/freeze-20261025/titles-ai-focused-r6.log (final context and title cases). Additional focused r2/r3/r4/r5 logs retained, including fixed failures.
- Full offline core gate: .local/android-test/reports/freeze-20261025/titles-ai-full-core-r1.log.
- Full commands: .\\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
- Result: **554/554 JVM PASS, 77 suites, 0 failures/errors/skips; Debug APK and source boundary PASS; Android lint 0 errors / 55 warnings.**
- Debug APK on current source: SHA256 22A1A757E4209596D704DF65ED78D2A0AA854DEA951D7257E492C50BEA16A7EF.

**Caveats:** No paid provider was called and no real provider request or human-device OEM test was executed. The earlier explicit AI Provider BLOCKED ledger rows remain BLOCKED. A concurrent untracked D05 AndroidTest source lacks a JUnit import, so the main checkout's AndroidTest build was NOT used for signoff; instead a clean isolated Git checkout was independently verified below. Do not stage or overwrite that file, the concurrently edited acceptance CSV, DraftDeckFeedbackTest.kt, design/, PC-OBS-005, or Python caches.


## Independent reproducibility check: committed source 4d7ec5d

A clean detached checkout of exact source SHA `4d7ec5de05ec97333c90c81b135471f9f929b94f` was built with all 88 offline Gradle tasks, including `:app:assembleDebugAndroidTest`, in isolation from concurrently edited/untracked files.

- **BUILD SUCCESSFUL: 88/88 tasks, 553/553 JVM tests, 76 suites; 0 failures/errors/skips.** The main checkout had 554 tests only because it included another agent's untracked `DraftDeckFeedbackTest.kt`.
- Debug app APK SHA-256: `22A1A757E4209596D704DF65ED78D2A0AA854DEA951D7257E492C50BEA16A7EF`; byte-for-byte matches the handed-off QA APK (66,167,593 bytes).
- AndroidTest APK SHA-256: `85F8A9E3BC4176946B63D8F66827F488B97C81E6A11CF3D42BACA69E7F0A4248`. Compilation only; no device/emulator test was run.
- Lint: 0 errors, 52 warnings.
- Log: `.local/android-test/reports/freeze-20261025/titles-ai-committed-isolated-full-gate.log`, SHA-256 `5BC25B6A10FBB4BB619C7798355E3FCC6A6E6C9109A2D974DB5DC09D086212D8`.
- The 76 JUnit XML files were saved in `titles-ai-committed-junit/`, and Lint XML in `titles-ai-committed-lint.xml`, under the same reports directory.
- This verifies reproducible build and unit-test behavior **only**. Real AI provider routing, OEM UI, physical-device accessibility and actual notification behavior remain separate, unsigned acceptance stages. CORE FREEZE = HOLD.

Core freeze remains HOLD, not GO. New APK is a debug QA candidate, not a signed release.