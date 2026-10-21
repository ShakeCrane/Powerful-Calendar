# Powerful Calendar — Visual Skin SDK Acceptance Record

Date: 2026-10-21. Scope: core source-compiled visual skin SDK, **not** release QA for a particular future design.

## As-built

- 10 swappable presentation surfaces: Timeline, Month, Memo, AI, Settings, Deck, Navigation, Overlay, Picker, Chrome. MainActivity installs the default native compiled profile; app host forwards route and modal rendering into the registry.
- Separate first-party Native() fallback preserved for each surface; sample profile demonstrates alternative whole-screen layouts and modal dialog design.
- All current Compose AlertDialog call sites in PowerCalApp, TimelineScreen, MonthScreen and DraftDeck are routed through the overlay adapter. Legacy DraftDeck full-screen Dialog and platform time/date pickers have typed adapters. Root view and short-lived messages have Chrome adapters.
- Sensitive settings controls are host-owned composables placeable by a compiled skin (ProtectedProfile and ProtectedAiCredentials); no credential data is exposed in SettingsVisualContext.
- Draft Controller is not shared with arbitrary renderer implementations; commands remain serialized in host, edits debounced, explicit close flushes, conflict/restore handled as host callback intents.
- Browser Studio creates portable data ZIP and editable visual design project. Source generator creates all 10 Kotlin renderer classes, registers a profile, and optionally emits a design brief.

## Tests and evidence

Passed on the connected Windows desktop with the Android toolchain:
- Python unittest discovery in tools/skin-studio/tests: 19 passed including ZIP validation, Studio HTTP, codegen and static architecture invariants.
- node --check tools/skin-studio/app.js: passed.
- Gradle :app:verifySkinSourceBoundary, :app:testDebugUnitTest, :app:assembleDebug, :app:lintDebug: BUILD SUCCESSFUL.
- Fresh, activated, design-derived generated profile (qa-contract) compiled with Gradle; source, blueprint and profile activation were restored/removed afterward.
- Git diff --check clean before staging, and original profile remains native.

## Safety and nonclaims

Compiled Kotlin renderer is trusted application source, **not** a permissionless untrusted code sandbox. Build gate scans direct disallowed service references but source review is still required. Browser Studio is not a native Compose renderer, and .pcstudio.json does not automatically compile into a finished visual design.

No new custom skin has been certified by live-device visual regression, TalkBack, performance metrics or lifecycle stress testing during this phase. The integrated native fallback is the existing supported path. OS-owned permission dialogs and file-pickers remain outside skin control.

Acceptance of this record means the SDK and entry points are ready to begin the default-skin design cycle, not that a specific new default visual style has been designed or shipped.
