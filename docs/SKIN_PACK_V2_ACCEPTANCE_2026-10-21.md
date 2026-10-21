# PowerCal .pcskin v2 Runtime Acceptance — 2026-10-21

Project root: E:\Project\Powerful-Calendar (Windows desktop).
This record distinguishes the verified delivery from future native-device design approval.

## Delivered

1. Android SkinPackageManager decodes both v1 (static theme) and v2 (static theme plus a bounded, hashed ui.json) ZIP packages.
2. A source-compiled declarative interpreter maps validated UI trees to ten host presentation surfaces: timeline, month, Memo, AI, Settings, Deck, Navigation, Overlays, Root Chrome, Date/Time Picker.
3. Declarative structural nodes support row/column/box/card/scroll, text/buttons, limited safe domain widgets and host action capabilities.
4. Motion is configured in the ZIP for node entry animation and genuine host route enter/exit; respects reduce-motion settings. No animation falsifies time duration or persistence success.
5. Protected profile/API settings and guarded Deck/AI workflows remain hosted by existing first-party code. Bad skins cannot hide mandatory modal controls, entire app body or native settings restore action.
6. Python stdlib runtime_package.py and the unified skin_studio.py build-v2/validate subcommands create, import and verify distributable ZIPs from editable JSON and optional assets.
7. Checked-in valid example: tools/skin-studio/examples/Nebula-Flux-v2.pcskin and the two editable source JSON files.

## Verification

- Python unittest discovery, 27 tests passed, including v2 ZIP build/revalidate, corruption, invalid actions, missing modal buttons, abusive recursion, invalid cross-page widget and legacy v1 compatibility.
- Android Robolectric SkinPackageManager v2 tests passed: valid UI, tampered archive, forbidden action, missing recovery, nested UI limits and installation/selection/restart/default fallback.
- Android Compose test loads the real checked-in Nebula Flux ZIP and exercises new timeline tree and host actions, native fallback, modal confirmation, picker confirmation and navigation/Chrome slots. Passed.
- Gradle verifySkinSourceBoundary, testDebugUnitTest, assembleDebug and lintDebug: BUILD SUCCESSFUL.
- Live v2 package mode uses the exact same selected ZIP persistence as current static skins. The default native skin remains immutable.

## Outstanding validation and boundaries

- No device-side execution was performed; Android Debug Bridge was not available at the inspected local SDK path. Release-grade on-device interaction/IME/accessibility/performance smoke tests remain outstanding.
- This is an importable constrained runtime UI DSL, not arbitrary Compose/Kotlin, JavaScript, untrusted code execution or a pixel-accurate browser visual editor.
- Updating the host APK is required once to learn v2, then changing skins does NOT require rebuilding it.
- The Nebula Flux example is a demonstration skin, not the newly approved PowerCal default design.
- The exact full source-compiled skin SDK remains available for new drawing primitives or gestures that the runtime interpreter does not expose.

See docs/SKIN_PACK_V2.md for format, builder usage and authoring API.
