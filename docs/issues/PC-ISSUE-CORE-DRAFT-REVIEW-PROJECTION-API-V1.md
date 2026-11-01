# PC-ISSUE-CORE-DRAFT-REVIEW-PROJECTION-API-V1 — Draft Deck renderer DTO boundary

Date: 2026-11-01 (China Standard Time) | Priority: P1
Status: **CODE COMPLETE; JVM/BUILD VERIFIED; DEVICE NOT TESTED**

## Goal and result

Close the remaining Core-type leak in the compiled Compose Draft Deck renderer context. The skin receives the existing versioned Draft/Review feature values and Host callbacks; it no longer receives `DeckController.DeckState`, `DraftForm`, or `TemporalKind`.

`DeckVisualContext` now exposes `DraftReviewSnapshotV1`, `EventFormV1`, and `EventKindV1`. The Host retains Core form conversion, publishability checks, DeckController access, API commands, formal commit review, and conflict/restore authorization. The separate Native Presentation Contract (`NativeDeckState` and its renderer) remains unchanged. Native fallback remains available.

## Implementation

- The context snapshot is sourced from the shared `DraftReviewFeatureApiV1`; the Host-local controller state is not passed to the renderer.
- `onEdit` accepts a complete `EventFormV1` and calls the revision-checked Draft/Review API command.
- `onConvert` accepts `EventKindV1`; the trusted Host maps it to the existing Core kind and invokes `TemporalConversion` before submitting the updated feature form.
- Commit availability is calculated in the Host from the existing Core form validator and passed as a boolean display capability.
- Runtime renderer call sites use API DTO fields. The Native fallback and forced Native route for conflict/restore state remain intact.
- No frozen business semantics, persistence format, API authorization, or parity acceptance row changed.

## Expected vs observed

- **Expected:** a compiled Draft Deck renderer receives versioned feature DTOs and Host-owned actions; it cannot name or receive the Core Deck state/form/kind types.
- **Observed:** the Compose regression confirms the state and editable form runtime classes are `DraftReviewSnapshotV1` and `EventFormV1`, and an edit callback updates the current draft through the shared API. Compilation and both source-boundary tasks pass. This source/JVM evidence does not establish packaged WebView, device, or Native/Clear parity.

## Verification

| Check | Result |
| --- | --- |
| New renderer DTO/edit regression | **1/1 PASS** |
| `GlassComposeTest` | **22/22 PASS**; includes Native fallback and Host-owned commit review coverage |
| Forced serial offline JVM gate | **846/846 tests; 134 XML reports; 0 failures, errors, or skips — PASS** |
| `:app:verifySkinSourceBoundary` and `:app:verifyNativeSkinBoundary` | **PASS** |
| `:app:assembleDebug` | **PASS** |
| `:app:lintDebug` | **PASS**, 0 errors / 53 warnings |
| `:app:compileDebugAndroidTestKotlin` | **PASS**; compiled only, not executed |
| `git diff --check` | **PASS** |
| Android instrumentation, packaged WebView, physical/OEM | **NOT TESTED**; ADB has no connected device and no AVD is configured |

Environment: Windows PowerShell, Gradle Wrapper 8.14.3, `ANDROID_HOME=E:\android-sdk`, offline dependencies, and one Gradle worker. The focused regression is `dev.powerfulcalendar.ui.v2.GlassComposeTest.runtimeDeckRendererReceivesVersionedReviewDtosAndEditsThroughApi`; the full `GlassComposeTest` class supplies the Native fallback and Host review checks. No layout changed, so no screenshot was generated.

Full gate command:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, **66,847,342 bytes**, SHA-256 `230A250836C8BEDE31C8C2092B5A7069F3ED8ADCE8602FA732EED9D3EBE47696`.

Ignored evidence under `.local/android-test/reports/core-draft-deck-projection-api-20261101/`:

- `glass-focused.log` — SHA-256 `D8A36C6728F819DD575470BF2DFF9532A5BD73A29C0C38128952A313638E806C`.
- `full-gate.log` — SHA-256 `38A3F6D5A61915F635FCEB714AE781686E649A4AE1000D47C3167DFF811960EB`.

## Remaining scope

Phase 1 remains open. Other runtime projections still reference Core models, including `MonthVisualContext` and `AiVisualContext`. Ordinary AI chat/provider/tool orchestration remains outside the shared Core Feature API. Native/Clear parity, packaged WebView behavior, and device/OEM behavior remain **NOT TESTED**; this report does not sign an acceptance-matrix row or authorize Skin cutover.

## Git delivery

Implementation branch: `codex/core-feature-api-event`. Code commit `db10d79950de899f45033c6837b5d99aa22ec549` follows `e90cbb4ea773ce7975ef092593d3563e80736dbc`. Documentation and evidence are maintained in the current Issue and the authoritative Core Feature API, roadmap, progress, README, UI contract, and parity matrix. Device parity and cutover remain outside this delivery.
