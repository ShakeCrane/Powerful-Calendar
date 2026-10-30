## Post-v4 implementation update: runtime v2

As of 2026-10-21, the presentation host additionally interprets signed-by-integrity
(not author-signed) .pcskin v2 ZIP payloads: see [SKIN_PACK_V2.md](SKIN_PACK_V2.md).
They can live-switch ten existing screen surfaces with a strict built-in node/action
library, animation and route transitions. The v4 compiled skin interfaces and native
fallback contract remain available and unchanged.

# PowerCal Visual Skin Contract v4 — as built

Updated 2026-10-21; Native profile clarification added 2026-10-30. This document records executable Android source interfaces, not a proposal or a promised future plugin format.

## Separation

The functional host exclusively owns persisted events, eventSeq/revision/occurrence identity, timezones/recurrence, AI interpretation and approval, authorization boundaries, reminder scheduling, credential storage, file/import/export, and DeckController serialized durable edits. Presentation code may design any Compose layout, drawing, typography, animation, gesture affordance and overlay, while invoking only the provided host actions. Skins do not independently persist state or claim successful authorization.

This ABI is intended for audited Kotlin source compiled into the same APK; it is not a sandbox for untrusted Kotlin, a dynamically loaded plugin, or an Android-installable executable ZIP.

The production `native` source-compiled profile explicitly registers adapters for all ten
surfaces. Each adapter delegates to that surface's existing `context.Native()` implementation,
so the Native UI has a named presentation profile without creating a second screen implementation
or gaining Host capabilities. Clearing the compiled registry still restores the direct first-party
fallback. This profile is independent of `.pcskin` package selection and is not an executable or
Skin Studio package.

## Ten replaceable presentation surfaces

| Surface | Typed contract | Default fallback |
| --- | --- | --- |
| Timeline | CompiledTimelineSkin / TimelineSkinContext | original TimelineScreen |
| Month | MonthVisualRenderer / MonthVisualContext | NativeMonthScreen |
| Memo | MemoVisualRenderer / MemoVisualContext | NativeMemoCenterScreen |
| AI chat | AiVisualRenderer / AiVisualContext | NativeAiChatScreen |
| Settings | SettingsVisualRenderer / SettingsVisualContext | NativeToolsPage |
| Draft Deck | DeckVisualRenderer / DeckVisualContext | NativeDraftDeckDialog |
| Navigation | NavigationVisualRenderer / NavigationVisualContext | NativePowerNavigation |
| Compose alert/window dialogs | OverlayVisualRenderer / OverlayVisualContext | Material3 AlertDialog / Compose Dialog |
| Date/time picker | PickerVisualRenderer / PickerVisualContext | Android native DatePickerDialog / TimePickerDialog |
| App root and message banner | ChromeVisualRenderer / ChromeVisualContext | first-party viewport and message Surface |

Every native fallback remains callable through context.Native(). All Compose AlertDialogs in the current PowerCal UI route through SkinAlertDialog; Draft Deck's full-screen Dialog routes through SkinWindowDialog; its platform pickers route through SkinPickers. Window/picker opening and dismissal callbacks remain supplied by their host, not invented by the skin.

Root Shell is an independent Chrome surface. The host children retain the BoxScope layout contract, and the Chrome renderer may introduce its own background, scope-wide theme, insets and surface arrangement via context.Body(modifier). It also styles ephemeral status notices without changing when they appear.

Settings exposes two additional protected composable slots: context.ProtectedProfile() and context.ProtectedAiCredentials(). These can be placed in arbitrary custom layouts without passing API keys or local profile preferences to renderer code. The native settings form uses the same host controls. Any other settings action is a supplied callback.

## Contract sources

In app/src/main/java/dev/powerfulcalendar/ui/v2:
- CompiledSkinTimeline.kt — timeline context, yOf geometry, default preview
- PresentationSkinRuntime.kt — route registry, settings controls and navigation
- skins/NativeVisualSkin.kt — explicit Native profile adapters for the ten presentation surfaces
- native/ — first-party Native timeline, month, memo, AI, Deck, navigation and Settings UI implementations; `PowerCalApp.kt` remains the route/Host orchestrator
- SkinnableDraftDeck.kt — controller adaptation with autosave, serialized guarded operations
- SkinnableOverlays.kt — Compose Alert and Window slots
- SkinnablePickers.kt — date/time imperative picker intents
- SkinnableChrome.kt — root viewport and message banner
- NativePowerNavigation.kt — stock navigation
- skins/ActiveVisualSkin.kt — single source-compiled profile selection and registration
- skins/StudioExampleDeck.kt, skins/StudioExampleOverlay.kt, skins/StudioExamplePicker.kt, skins/StudioExampleChrome.kt — runnable reference implementations

A renderer's Kotlin id must be nonempty and unique among installed surfaces. Some contracts are internal to one Android source module, not promised as external AAR binary ABI. No skin source code should need PowerCalApp.kt or MainActivity.kt edits.

## Invariants

- Time-axis ACTIVITY edges must use the same yOf(start) and yOf(end) projection, never minimum visual height used as fake duration. ALARM and DEADLINE are instants; date-only deadlines are calendar dates.
- Recurrence occurrence keys, eventSeq, revision, timezones and DST are stable core identities. A renderer must not silently turn an occurrence edit into a whole-series write.
- An AI chat message is not a proposal authorization. Review and batch commands remain owned by the host. Destructive actions cannot be executed from skin internals.
- A custom Deck sees state and host callbacks only, never DeckController or PowerStore. Edits are debounced and flushed before leaving, commit/undo/restore are serialized in the domain controller. Conflict and AI lock states must be represented.
- Text fields must remain accessible during IME, large type and landscape; reduce motion, TalkBack semantics, action focus and usable touch targets are required.
- Canvas animation may change apparent transition or opacity, never exact event time or verified persistence status.
- In source-level skin code, accessing local files, credentials, AI clients, reminder scheduling or the database directly is prohibited. Gradle :app:verifySkinSourceBoundary is an automated guard but does not replace security review.

## Three different user artifacts

1. Portable .pcskin v1: bounded ZIP with light/dark palette, dimensions and image slots. Can be installed in the app. NO executable animations or alternate Compose layout.
2. Skin Studio .pcstudio.json: visual blueprint for six design scenes, layout zones, slot order, motion and images; browser editor can save/reopen it. NOT directly Android-executable.
3. Trusted source-compiled Kotlin skin: ten full visual surface renderers, each consuming a typed context. This is the only current way to fully rearrange native Android UI and motion. Requires rebuild and audited source.

Scaffold:

    python tools/skin-studio/new_compiled_skin.py aurora-glass --activate --design "C:\path\file.pcstudio.json"

Generated code calls context.Native() on every surface, so it builds safely before visual edits. The optional DESIGN_BRIEF.md is a plan for a human or Agent to implement, not a generated final layout.

## Verification and honesty boundary

Run:

    python -m unittest discover -s tools/skin-studio/tests -v
    node --check tools/skin-studio/app.js
    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain

Production native default and compiled example registration/recovery are covered by unit tests. Browser mockups are NOT screenshots of real Compose. A brand-new visual skin additionally needs Android-device testing for dialog gestures, IME, navigation, reduced motion, TalkBack, permissions, recurrence, one-minute event geometry, DST and restore flows. External Android OS permission screens and OS file chooser remain system-controlled and are not claimed to be reskinnable.

The architecture and ten source-level routing points are implemented. It does NOT mean that Skin Studio's design JSON automatically converts to Compose or that every future bespoke design has already passed real-device QA. Distinguish architecture acceptance from a specific skin's visual release acceptance.
