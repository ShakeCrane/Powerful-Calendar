# PowerCal .pcskin v2 — Runtime UI Skin Protocol

Updated 2026-10-21. This is the directly importable ZIP skin format. It lets users switch
Compose layout/component trees, theme, entry animations and real page transitions at runtime,
without recompiling the upgraded Android app when switching packages.

## Start here

Ready-to-import example:

    tools/skin-studio/examples/Nebula-Flux-v2.pcskin

After upgrading the PowerCal host to the v2-capable APK once, go to Settings → Skin →
Import skin, choose the ZIP, then select Nebula Flux · Runtime v2.
You can switch back to PowerCal native whenever desired. The example is demonstrative
and is not a replacement of the original default skin.

Editable source files:

    tools/skin-studio/examples/nebula-flux-manifest.json
    tools/skin-studio/examples/nebula-flux-ui.json

Build and validate:

    python tools/skin-studio/skin_studio.py build-v2 --manifest tools/skin-studio/examples/nebula-flux-manifest.json --ui tools/skin-studio/examples/nebula-flux-ui.json --out my-skin.pcskin
    python tools/skin-studio/skin_studio.py validate my-skin.pcskin

The browser Skin Studio remains a v1 visual theme editor. For v2, edit ui.json in
any text editor or give it to an Agent, and use the builder above.

## Package structure

    my-skin.pcskin
      manifest.json   (schemaVersion: 2, runtimeUi: "ui.json")
      ui.json         (component trees, layout, motion and route transitions)
      images/...      (optional PNG/WebP as in v1)

The manifest SHA-256 map covers ui.json plus every declared image.
Each file and the entire inflated archive are bounded; unexpected entries, duplicate
ZIP names, unknown node types, unsafe host actions and corrupt hashes are rejected.

Resource hashes are integrity checks, not publisher signatures or proof of trusted authorship.
ZIP packages contain no Kotlin, JavaScript, executables, reflection expressions, native
libraries, downloaded fonts, or network capabilities.

## ui.json example

    {
      "format": "powercal-runtime-ui",
      "version": 1,
      "transition": "slide-left",
      "durationMs": 260,
      "screens": {
        "timeline": {
          "type": "column",
          "padding": 12,
          "children": [
            {"type": "text", "text": "MY DAY · {pending} drafts"},
            {"type": "row", "children": [
              {"type": "button", "text": "Today", "action": "today"},
              {"type": "button", "text": "Memo", "action": "memo"}]},
            {"type": "scroll", "grow": true, "children": [{"type": "events"}]},
            {"type": "button", "text": "Precise native ruler", "action": "native"}
          ]
        }
      }
    }

A package may define only the scenes it wants to replace. Any missing scene is provided
by the first-party renderer.

Ten scene identifiers: timeline, month, memo, ai, settings, deck, navigation,
overlay, picker, chrome.

Available structural nodes: column, row, box, card, scroll, text, button, spacer, native.

Specialized built-in widgets: events (timeline, month), todos (memo), chats and
chat-input (AI), skins/profile/credentials (settings), deck-actions (Deck), nav-items,
overlay-title/body/confirm/dismiss and window-content, picker-title/date/time/confirm/dismiss.

Each action identifier is validated against its screen-specific host capability whitelist.
For example: timeline supports today, memo, deck, month, settings and new-alarm,
new-activity, new-deadline, native. Settings supports import-skin, export-skin,
import/export-ics, backup, restore, notification settings and navigation, but never
raw credential access or direct calendar storage mutation.

Dynamic labels support fixed placeholders such as {pending}, {memo}, {count},
{title}, and {date}. No expression execution is allowed.

Node properties: children (max 30), padding (0-40 dp), gap (0-32 dp),
grow (weighted element in row/column), color (fixed safe Material surface roles),
motion (none / fade / slide-up / slide-left / scale), durationMs (0-600).
Whole-document budget: 120 KB, at most 384 nodes, at most 10 levels.
Nested vertical scroll containers are prohibited.

Page transitions use exactly the same five motion names. The host switches pages
using Compose AnimatedContent with real enter and exit animations, not a browser mock.
Per-node entry animations use AnimatedVisibility. Both honor reduced-motion preferences.
Animations are visual only, and cannot change event time coordinates or persistence state.

## Safety and recovery

- Chrome must render the native host body; nav must retain nav-items or native.
- Confirmation overlays must retain all host-provided confirm, dismiss, text and window slots,
  or fall back to the complete native dialog.
- Pickers must retain both date/time widgets, confirm, dismiss and title, or fall back
  to platform date/time selectors. No selected value is applied until explicit confirmation.
- The Deck never gives a ZIP the DeckController. Conflicts and restore requests use
  the original guarded native editor; every v2 Deck has a native escape hatch.
- Settings always has an independent native restore button, even if a user-designed
  layout otherwise hides skin management.
- The imported skin cannot read secrets, call PowerStore, invoke network, schedule alarms,
  or bypass the existing AI proposal/authorization flow.
- Old schemaVersion 1 ZIPs remain installable and retain their former static theme semantics.
- The unchanged native PowerCal Glass remains the undeletable fallback.

These rules are enforced both by the package build tool and by the Android parser.
Compile-time skin source can still implement unrestricted audited Compose UI, but importing
JSON is limited to this vetted component library. The format is not a general program
execution engine or a way to load arbitrary Android components from third parties.

## Known limits and release QA

Browser visual Skin Studio currently authors v1 layout plans and theme resources, not
v2 native Compose trees. v2 JSON editing requires an editor/Agent and the CLI builder.
The built-in events widget is a summary list, not a proportionally scaled time ruler;
the precise native timeline remains one click away. Additional widget/gesture kinds
require audited host interpreter changes, not arbitrary packaged code.

Tests:

    python -m unittest discover -s tools/skin-studio/tests -q
    node --check tools/skin-studio/app.js
    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain

Robolectric/Compose tests cover real v2 ZIP loading, runtime button callbacks,
native recovery, modal confirm/dismiss, navigation and date confirmation.
Physical-device screenshots, lifecycle/reduced-motion and TalkBack must still be verified
before publishing this example as an official visual skin.
