## Post-v4 implementation update: runtime v2

As of 2026-10-21, the presentation host additionally interprets signed-by-integrity
(not author-signed) .pcskin v2 ZIP payloads: see [SKIN_PACK_V2.md](SKIN_PACK_V2.md).
They can live-switch ten existing screen surfaces with a strict built-in node/action
library, animation and route transitions. The ten compiled-skin surfaces and Native
fallback behavior remain. The Settings, Navigation, Memo, AI chat, Month, Timeline, and Draft Deck
value APIs are in `:skin-api`; their Native renderers are in `:native-skin`. The formal editor's
Host presentation context is also in `:skin-api`, with its Native renderer in `:native-skin`.
Native Chrome, Overlay, and Picker contexts and renderers also live in `:native-skin`. Core-backed Host
projections, lifecycle and action adapters remain in `:app`.

# PowerCal Visual Skin Contract v4 — as built

Updated 2026-10-21; Native profile plus Settings, Month, Timeline, Draft Deck, formal editor, AI input/proposal/batch review, Chrome, Overlay, and Picker module boundaries clarified 2026-10-30. This document records executable Android source interfaces, not a proposal or a promised future plugin format.

## Separation

The functional host exclusively owns persisted events, eventSeq/revision/occurrence identity, timezones/recurrence, AI interpretation and approval, authorization boundaries, reminder scheduling, credential storage, file/import/export, and DeckController serialized durable edits. Presentation code may design any Compose layout, drawing, typography, animation, gesture affordance and overlay, while invoking only the provided host actions. Skins do not independently persist state or claim successful authorization.

This ABI is intended for audited Kotlin source compiled into the same APK; it is not a sandbox for untrusted Kotlin, a dynamically loaded plugin, or an Android-installable executable ZIP.

The production `native` source-compiled profile explicitly registers adapters for all ten
surfaces. Each adapter uses the corresponding Native renderer (most through `context.Native()`; Chrome delegates to `NativeChromeSkin.Render`; Overlay delegates to `NativeOverlaySkin.Render`),
so the Native UI has a named presentation profile without creating a second screen implementation
or gaining Host capabilities. Clearing the compiled registry still restores the direct first-party
fallback. This profile is independent of `.pcskin` package selection and is not an executable or
Skin Studio package.

The `:skin-api` module currently contains the Settings and Navigation value contexts/DTOs, the
Native protected-profile display/actions, plus the display-only `NativeMemoRow`,
`NativeAiMessage`, `NativeAiBatchMode`, Native Month, Timeline, and Draft Deck models/appearance
slots. The `:native-skin` Android library contains `NativeToolsPage`,
`NativePowerNavigation`, `NativeMemoCenterScreen`, `NativeAiChatScreen`, `NativeMonthScreen`,
`NativeTimelineScreen`, `NativeDraftDeckScreen`, and `NativeDirectEventEditor`; `NativeChromeSkin`, `NativeOverlaySkin`, and
`NativePickerSkin` render root/notice, modal, and Android date/time picker surfaces; it also renders
the protected profile form through `NativeProtectedProfileSection`;
the Host supplies values, action callbacks, protected content slots, and presentation
shapes/icons. The
module direction is `:app → :native-skin → :skin-api`, with `:app → :skin-api` for Host adapters.
A Gradle boundary check rejects known Host/Core service references from `:native-skin`.

This is an incremental compile/module boundary, not a security sandbox or separate APK. The
Native Timeline, Draft Deck, formal event editor, and Chrome renderers are in `:native-skin`; their Host-owned projections and wrappers,
geometry/form adapters, controller lifecycle, and route adapters remain in `:app`. Native renderer
IDs and product behavior are unchanged.

`NativeDirectEventEditor` consumes `NativeDirectEventEditorContext` from `:skin-api` and reuses the
Native Draft Deck event form. The Host still owns the `EditSession`, recurrence-scope validation,
form conversion, picker requests, persistence, and save/exit/delete callbacks; the moved module
renders the Native editor shell and forwards explicit actions to those callbacks. This is a
Host-owned Native renderer contract, not an additional runtime-skin surface.

## Ten replaceable presentation surfaces

| Surface | Typed contract | Default fallback |
| --- | --- | --- |
| Timeline | CompiledTimelineSkin / TimelineSkinContext | NativeTimelineScreen via Host adapter |
| Month | MonthVisualRenderer / MonthVisualContext | NativeMonthScreen |
| Memo | MemoVisualRenderer / MemoVisualContext | NativeMemoCenterScreen |
| AI chat | AiVisualRenderer / AiVisualContext | NativeAiChatScreen |
| Settings | SettingsVisualRenderer / SettingsVisualContext | NativeToolsPage |
| Draft Deck | DeckVisualRenderer / DeckVisualContext | NativeDraftDeckDialog |
| Navigation | NavigationVisualRenderer / NavigationVisualContext | NativePowerNavigation |
| Compose alert/window dialogs | OverlayVisualRenderer / OverlayVisualContext | NativeOverlaySkin via Host adapter |
| Date/time picker | PickerVisualRenderer / PickerVisualContext | NativePickerSkin via Host adapter; Android DatePickerDialog / TimePickerDialog |
| App root and message banner | ChromeVisualRenderer / ChromeVisualContext | NativeChromeSkin via Host adapter |

Every native fallback remains callable through context.Native(). All Compose AlertDialogs in the current PowerCal UI route through SkinAlertDialog; Draft Deck's full-screen Dialog routes through SkinWindowDialog; its platform pickers route through SkinPickers. Window/picker opening and dismissal callbacks remain supplied by their host, not invented by the skin.

Root Shell is an independent Chrome surface. The host children retain the BoxScope layout contract, and the Chrome renderer may introduce its own background, scope-wide theme, insets and surface arrangement via context.Body(modifier). It also styles ephemeral status notices without changing when they appear.

Settings exposes two additional protected composable slots: context.ProtectedProfile() and context.ProtectedAiCredentials(). These can be placed in arbitrary custom layouts without passing API keys or preference objects to renderer code. The Host owns profile preference reads/writes and supplies the name value and edit/save callbacks only to the built-in `NativeProtectedProfileSection`; custom renderers receive the opaque Host slot. The AI credentials form remains Host-rendered and its secret values do not enter `:skin-api` or `:native-skin`. Any other settings action is a supplied callback.

### Settings renderer data boundary (2026-10-30)

`SettingsVisualContext` is defined in `SettingsVisualContext.kt` and contains only display values,
skin identifiers, visual-effect values, Host action callbacks, and injected Compose content slots.
It does not retain `UiDiagnostics`, `TemporalRecord`, `PowerStore.AiSession`, `InstalledSkin`,
`GlassEffects`, `AiBatchRunner.Mode`, Android services, preference objects, or a direct reference to
`NativeToolsPage`. `Native()` and the two protected sections invoke Host-injected composable slots.

`SettingsDisplaySnapshot` carries the Settings facts needed by the Native fallback and compatible
renderers: pending/formal counts, sequence and identity counters, database version/name, reminder
permission and AI-lock/recovery flags, AI-batch activity, and up to 40 reminder display rows plus
three recurrence previews. A reminder row includes its event label and title, schedule result,
Android hint and latest execution text. These fields are local calendar data and are available to
the audited source-compiled renderer; they do not grant a Store, filesystem or mutation API.
`SettingsSkinChoice` contains only `id`, `name` and `builtIn`; export and delete actions receive an
ID so the Host resolves the authoritative installed package. `SettingsVisualEffects` contains
`blur`, `animationScale` and `reduceMotion` values.

### Native protected-profile presentation boundary (2026-10-30)

`NativeProtectedProfileSection` renders the personal-profile card and its single-line name field.
`NativeProtectedProfileDisplay` contains the name shown in the built-in Native settings form;
`NativeProtectedProfileActions` contains edit/save callbacks. `HostProtectedProfileSection` in
`:app` owns the preference object,
initial read, and explicit save. The Native module receives no Android `Context`, preferences,
credential value or persistence service. The existing 80-character input cap and save-on-button
behavior are covered by Compose regressions. The AI endpoint/model/key settings remain Host-owned.

The Settings, Navigation, Memo, AI chat, Month, Timeline, and Draft Deck display API types compile in
`:skin-api`; their Native renderers compile in `:native-skin`. Memo, Month, Timeline, and Draft Deck
renderers receive display values and Host callbacks. Opaque row/card keys resolve in Host code to
the authoritative owner and, where supported, the original occurrence. Chrome and Overlay contexts/renderers are provided by `:native-skin` to keep Compose UI dependencies with presentation code. This module boundary is not
a sandbox. Core-backed projections, DeckController lifecycle/commands, picker operations, and
renderer adapters remain in `:app`.

### Month display boundary (2026-10-30)

`NativeMonthScreen` consumes `NativeMonthRow` values, `LocalDate`/`YearMonth`, callbacks, and
`NativeMonthAppearance` visual tokens/Compose slots. It has no Core or Store reference. The Host
uses the existing `displayOccurrences` and `overlapsDayWindow` rules to project the current
month's event rows, date-only deadlines, cross-midnight visibility, summaries, and labels. Each
opaque row key maps in the Host to its owner record and original recurrence occurrence key before
the existing open callback runs. The projection map is replaced when the month changes, so stale
row keys cannot resolve to a prior month's action target. Native month navigation, date selection,
create/open actions, AI-lock display, and picker dialogs remain Host-authorized through callbacks
and injected slots.

### Timeline display boundary (2026-10-30)

`NativeTimelineScreen` consumes `NativeTimelineEvent`, camera/state values, action callbacks,
`NativeTimelineGeometry`, and Host-injected Native appearance/dialog/image slots from `:skin-api`.
The renderer has no Core or Store reference. The `:app` projection retains occurrence expansion,
true-time lane assignment, display summaries, event ownership and original recurrence keys; an
opaque row key resolves each renderer action against the current projection. The geometry adapter
delegates to the existing `TimelineCoordinate` calculations, keeping frozen time geometry
authoritative. Timeline gestures, independently scrolling wings, title measurement/folding, event
hit targets, accessibility labels, and the existing axis reset remain in the Native renderer.
Current callbacks, event time geometry, titles, and route behavior are unchanged. Per-instance
recurrence dragging remains refused by the existing Host behavior until that separate editor
capability is implemented.

### AI chat display boundary (2026-10-30)

`NativeAiChatScreen` is compiled in `:native-skin` and consumes `NativeAiMessage` plus the Native
batch-mode enum from `:skin-api`. The Host projects provider messages into those display values,
maps batch actions back to the existing `AiBatchRunner`, owns clipboard access, and injects proposal
review and avatar content through composable slots. AI interpretation, proposal authorization and
commit behavior remain in `:app`; message text and a proposal-source string are display/input values,
not authorization. Native code has no direct provider, Core, Store or Android clipboard dependency.

### AI image consent and OCR review boundary (2026-10-30)

`NativeAiImageConsentReview`, `NativeAiOcrTextReview`, and `NativeAiFileReview` are rendered by
`:native-skin` through display-only `:skin-api` dialog slots. The Host supplies display text,
preflight summaries, busy/consent/import state and callbacks; it retains the selected URI, file
access and size checks, OCR service, ICS parsing and import decision, consent persistence, AI
provider request, state transitions and submit/cancel behavior. The Host also selects the skin-aware
dialog shell. No URI, file resolver, recognizer, provider client, Core object or persistence service
crosses into the Native renderer. This is a Native source-module boundary only; Android/device
behavior is not implied by the source move.

### Native skin catalog confirmation boundary (2026-10-30)

`NativeSkinCatalogReview` renders the confirmation copy and buttons for activating or deleting an
installed skin. Its `:skin-api` dialog model contains only the review kind, display strings and
callbacks. `PowerCalApp` retains the installed-skin object and ID, edit-safety check before
activation, registry selection/deletion, refresh and result reporting; it also supplies the
skin-aware dialog shell. Native presentation cannot select or delete a skin without the Host action
callback. This source move does not change the Native fallback or enable a skin automatically.

### Native legacy-data confirmation boundary (2026-10-30)

`NativeLegacyDataReview` displays the warnings and confirm/cancel controls for read-only legacy
database migration and explicit restoration of flat backup v2. Its `:skin-api` contract carries
only the review kind and dialog slots. `PowerCalApp` retains the original backup text, importer,
restore parser, empty-database and integrity checks, reminder reconciliation, state refresh and all
mutating callbacks. The Native module has no database or restore capability. This presentation move
does not establish the broader SEP-14 disaster-recovery gate.

### Native AI stuck-batch release review boundary (2026-10-30)

`NativeAiStuckBatchReleaseReview` renders the force-release warning, Host-computed button enabled
state, and confirm/cancel controls. `PowerCalApp` retains the current AI session, lock epoch,
stop-age eligibility check, database release call and post-release reconciliation/status. The Native
renderer has no session identifier or store capability; confirmation only invokes the existing Host
callback. This does not weaken the ten-minute guard or change batch authorization semantics.

### Draft Deck display boundary (2026-10-30)

`NativeDraftDeckScreen` consumes `NativeDeckState`, `NativeDeckForm`, card/search/conflict display
rows, Host callbacks, and `NativeDeckAppearance` Compose slots from `:skin-api`. The renderer has
no Core, Store, Controller, provider, Android picker, or app-theme dependency. The Host adapter in
`:app` retains the single `DeckController` observer, serial command/flush/commit/discard/undo/
conflict/restore flow, field validation and `TemporalConversion`, Core-backed search projection,
and date/time picker interpretation. Native form edits are mapped onto the current Host form so
unexposed fields such as source provenance, completion keys, and recurrence metadata are preserved.
The standalone Native new-event editor and formal editor reuse the same module form renderer; their
edit sessions, save/exit decisions, scoped authorization, and outer Host confirmation surfaces stay
in `:app`. This slice establishes a source-module boundary; it does not sign SEP-08 or Native/Clear
functional parity, Android WebView/device behavior, or Skin Studio support.

### Native Chrome display boundary (2026-10-30)

`ChromeVisualContext` and `ChromeVisualRenderer` are provided with `:native-skin`; the Host
continues to own `SkinAppShell` selection, web/runtime fallback, `RuntimeSkinPickerHost`, and
notice lifecycle. `NativeChromeSkin` renders the root Box with the established background and
status/navigation/IME insets. It renders notices with the existing Material colors, 14 dp chip
radius, padding, and polite live-region semantics. The explicit Native profile delegates to this
module renderer, and the direct Native recovery path calls the same implementation. This is a
source-module boundary; it does not complete SEP-12, move Android pickers, or assert device/
TalkBack parity.

### Native Overlay display boundary (2026-10-30)

`OverlayVisualContext` and `OverlayVisualRenderer` are provided by `:native-skin`; `NativeOverlaySkin` mounts Material3 `AlertDialog` and Compose `Dialog` using the exact Host-provided content slots, action callbacks, `Modifier`, and `DialogProperties`. `SkinnableOverlays.kt` remains the Host adapter that selects the Native/compiled/runtime renderer and owns open/dismiss timing. The module renderer receives no Core, Store, controller, or authorization object. Native visuals and confirmation behavior are unchanged. This source-module move does not complete SEP-12 or prove device/TalkBack parity.

## Contract sources

Source module map:
- `app/src/main/java/dev/powerfulcalendar/ui/v2/CompiledSkinTimeline.kt` — timeline context, yOf geometry, default preview
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/SettingsVisualContext.kt` — Settings/Navigation value contexts, Native profile display/actions and injected Host/Native slots
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeMemoRow.kt` — display-only Memo row contract
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeAiChatModel.kt` — AI chat/batch action, image/OCR input, proposal/batch authorization and stuck-batch release display DTOs, plus dialog slots; no Core, session identity or AI parser objects cross the boundary
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeSkinCatalogModel.kt` — display-only activation/deletion review kind and dialog slots; skin IDs and mutation services remain in Host
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeLegacyDataReviewModel.kt` — legacy migration/restore review kind and dialog slots; backup data and recovery authority remain in Host
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeMonthModel.kt` — Native month display row and appearance slots
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeTimelineModel.kt` — Native timeline event/state/action/geometry and appearance values
- `skin-api/src/main/java/dev/powerfulcalendar/ui/v2/NativeDeckModel.kt` — Native deck/form display DTOs, callbacks and appearance slots
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeToolsPage.kt` — Native Settings renderer
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeProtectedProfileSection.kt` — Native protected-profile form renderer; preference reads and writes remain in Host
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativePowerNavigation.kt` — Native bottom navigation
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/MemoCenterScreen.kt` — Native Memo renderer consuming only value rows and opaque action keys
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/AiChatScreen.kt` — Native AI chat renderer consuming display DTOs and Host callbacks/slots
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeAiProposalReview.kt` — Native proposal-review card renderer consuming display-only rows and Host action callbacks
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeAiBatchAuthorizationReview.kt` — Native frozen-batch authorization review renderer with Host-provided dialog shell and callbacks
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeAiStuckBatchReleaseReview.kt` — Native force-release warning and button state renderer with Host-provided eligibility and callbacks
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeAiInputReview.kt` — Native image-consent, OCR-text and extracted-file review dialogs with Host-provided dialog shell and callbacks
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeSkinCatalogReview.kt` — Native skin activation/deletion confirmation display with Host-provided dialog shell and callbacks
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeLegacyDataReview.kt` — Native legacy database migration/flat backup confirmation display with Host-provided dialog shell and callbacks
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/MonthScreen.kt` — Native month renderer consuming display DTOs and Host callbacks/slots
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeTimelineScreen.kt` — Native timeline renderer consuming display DTOs and Host callbacks/slots
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeDraftDeckScreen.kt` — Native deck and shared event-form renderer consuming display DTOs
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PresentationSkinRuntime.kt` — route registry, Host projection, settings controls and slots
- `app/src/main/java/dev/powerfulcalendar/ui/v2/MemoProjection.kt` — Core-backed occurrence projection and display-only Native row mapping
- `app/src/main/java/dev/powerfulcalendar/ui/v2/NativeAiProjection.kt` — AI message, proposal and batch-review display projection plus Host batch mode mapping
- `app/src/main/java/dev/powerfulcalendar/ui/v2/AiProposalReview.kt` — thin Host adapter projecting AI proposals and binding authorization callbacks to the Native renderer
- `app/src/main/java/dev/powerfulcalendar/ui/v2/AiBatchAuthorizationReview.kt` — Host adapter retaining the frozen `DraftCard` snapshot and skin-aware dialog selection while projecting display-only batch review data
- `app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt` — Host orchestration of AI file/image operations, authorization, stuck-batch release eligibility, installed-skin lifecycle and legacy recovery; Native review surfaces receive display values and action callbacks only
- `app/src/main/java/dev/powerfulcalendar/ui/v2/HostProtectedSettings.kt` — Host-owned AI credential form and profile preference adapter
- `app/src/main/java/dev/powerfulcalendar/ui/v2/NativeMonthProjection.kt` — Core-backed month projection and opaque recurrence action mapping
- `app/src/main/java/dev/powerfulcalendar/ui/v2/NativeTimelineProjection.kt` — Core-backed occurrence/lane projection and geometry adapter
- `app/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/TimelineScreen.kt` — existing app route adapter into the Native Timeline module
- `app/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeVisualSkin.kt` — explicit Native profile adapters for all ten surfaces
- `app/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/DraftDeck.kt` — DeckController Host adapter, Core projection and picker interpretation
- `app/src/main/java/dev/powerfulcalendar/ui/v2/DirectEventEditor.kt` — formal-edit session and confirmation Host surface using the shared Native form renderer
- `app/src/main/java/dev/powerfulcalendar/ui/v2/SkinnableDraftDeck.kt` — controller adaptation with autosave, serialized guarded operations
- `app/src/main/java/dev/powerfulcalendar/ui/v2/SkinnableOverlays.kt` — Host renderer selection and action-slot binding
- `app/src/main/java/dev/powerfulcalendar/ui/v2/SkinnablePickers.kt` — date/time imperative picker intents
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/ChromeVisualContext.kt` — typed root/notice context and renderer contract
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/OverlayVisualContext.kt` — modal display slots and overlay renderer contract
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeOverlaySkin.kt` — Native AlertDialog and full-window Dialog renderer
- `native-skin/src/main/java/dev/powerfulcalendar/ui/v2/nativeui/NativeChromeSkin.kt` — Native root shell and accessible notice renderer
- `app/src/main/java/dev/powerfulcalendar/ui/v2/SkinnableChrome.kt` — Host renderer selection and notice lifecycle
- `app/src/main/java/dev/powerfulcalendar/ui/v2/skins/ActiveVisualSkin.kt` — single source-compiled profile selection and registration
- `app/src/main/java/dev/powerfulcalendar/ui/v2/skins/StudioExampleDeck.kt`, `StudioExampleOverlay.kt`, `StudioExamplePicker.kt`, `StudioExampleChrome.kt` — runnable reference implementations

A renderer's Kotlin id must be nonempty and unique among installed surfaces. Some contracts are internal to one Android source module, not promised as external AAR binary ABI. No skin source code should need PowerCalApp.kt or MainActivity.kt edits.

## Invariants

- Time-axis ACTIVITY edges must use the same yOf(start) and yOf(end) projection, never minimum visual height used as fake duration. ALARM and DEADLINE are instants; date-only deadlines are calendar dates.
- Recurrence occurrence keys, eventSeq, revision, timezones and DST are stable core identities. A renderer must not silently turn an occurrence edit into a whole-series write.
- An AI chat message is not a proposal authorization. The Host owns AI interpretation, proposal validation, authorization, and commit; Native review renderers receive only display DTOs and callbacks. Destructive actions cannot be executed from skin internals.
- A custom Deck sees state and host callbacks only, never DeckController or PowerStore. Edits are debounced and flushed before leaving, commit/undo/restore are serialized in the domain controller. Conflict and AI lock states must be represented.
- Text fields must remain accessible during IME, large type and landscape; reduce motion, TalkBack semantics, action focus and usable touch targets are required.
- Canvas animation may change apparent transition or opacity, never exact event time or verified persistence status.
- In source-level skin code, accessing local files, credentials, AI clients, reminder scheduling or the database directly is prohibited. `:app:verifySkinSourceBoundary` and `:app:verifyNativeSkinBoundary` are static guards, not security sandboxes or substitutes for review.

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
    .\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain

Production native default and compiled example registration/recovery are covered by unit tests. Browser mockups are NOT screenshots of real Compose. A brand-new visual skin additionally needs Android-device testing for dialog gestures, IME, navigation, reduced motion, TalkBack, permissions, recurrence, one-minute event geometry, DST and restore flows. External Android OS permission screens and OS file chooser remain system-controlled and are not claimed to be reskinnable.

The architecture and ten source-level routing points are implemented. It does NOT mean that Skin Studio's design JSON automatically converts to Compose or that every future bespoke design has already passed real-device QA. Distinguish architecture acceptance from a specific skin's visual release acceptance.
