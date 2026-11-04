# PC-ISSUE-SEPARATION-F09-BACK-EDITOR-CHECKPOINT

- **Date:** 2026-11-04
- **Status:** Resolved in F-09 code; overall F-09 remains BLOCKED by TalkBack traversal evidence
- **Base:** `e78829a3889f2edafea267440f6ded6f86b42fa2`
- **Fix commit:** `3932427a`
- **Evidence:** `.local/android-test/reports/F-09-20261104/manifest.md`

## Finding and resolution

On the isolated API 35 AVD, pressing Android Back from a Clear formal editor could return the page to Timeline while leaving the Host editor open. Two independent Compose BackHandlers competed: the page navigation path could handle Back before the active Web editor had checkpointed. This also risked leaving dirty editor state outside the durable pending-draft path.

`PowerCalApp` now has one Android Back handler. Clear dialogs receive first refusal. If the Host reports an open Web editor or draft deck, the handler calls `prepareSkinChange()` and publishes the updated state before page navigation. A failed checkpoint shows an error and keeps the user in the editor. Otherwise Back returns to Timeline or Native recovery according to the existing Host route.

The regression checks show that a clean existing Memo editor closes with no Store changes; a dirty new event becomes exactly one pending draft without a formal event write; and Back from Month, Memo, and AI returns to Timeline without Store changes.

## Verification

- `ClearNavigationBackInstrumentedTest`: 4/4 methods PASS on `PowerCal_Wave2_QA35_20261104` / `emulator-5554`.
- `WebSkinRendererRecoveryInstrumentedTest`: 3/3 methods PASS, including renderer death and injected JavaScript failure with pending-draft recovery to Native.
- Full JVM gate: 970/970, 0 failures/errors/skips. Skin and Native source boundaries, APK assemble, Lint, and AndroidTest Kotlin compile PASS.
- Debug APK SHA-256: `4baebe7a02073cfde91e87e117c1a3f1305cac69822a3836acbbcbbd288e37f5`.

TalkBack focus traversal was not proven by these cases and remains a separate F-09 blocker. No Core business behavior was changed.
