# PowerCal self-contained skin package v3

Schema v3 is an importable Web presentation package. It does not load Kotlin, Dex, native code,
remote URLs, Android files, credentials, `Context`, `PowerStore`, or arbitrary Java objects.
Schema v1 palette/image packages, schema v2 declarative packages, and the ten trusted compiled
Compose renderer routes remain supported.

## Archive

Required files are `manifest.json`, `project.json`, `web/index.html`, `web/style.css`, and
`web/app.js`. Optional local assets live under `assets/`. The manifest declares
`format: powercal-skin`, `schemaVersion: 3`, the local entrypoint/project, `fullCanvas`, bounded
capabilities, and a SHA-256 digest for every non-manifest entry. The complete base source is in the
archive; a Compose class name or an external parent is not a base.

Limits: 10 MB compressed, 16 MB inflated, 97 ZIP entries including the manifest, 600 KB per text
file, 3 MB per binary asset. Duplicate paths, traversal, symlinks, undeclared files, remote HTML
resources, iframes/objects, unknown capabilities, and incomplete hashes are rejected. Installation
uses a temporary file, fsync, bounded readback, whole-ZIP hash comparison, revalidation, and atomic
rename. Failed activation returns to the native skin.

## Host bridge v1

Messages are JSON objects `{version:1,type,payload}` over a transferred `WebMessagePort`.
`state` exposes only a presentation projection (page, lock/reduced-motion state, pending count, and
at most 300 bounded event identity/title/kind/geometry records). `action` is a closed, typed set:
navigation, Today, opening Drafts, or `{name:"openEvent",eventSeq,expectedRevision}`. The host
re-reads that event, requires the presented revision to still match, checks the global writer gate,
and only then opens the existing native editor. Event creation is intentionally not bridged because
v3 must not create an initial draft as a side effect. Unknown versions, types, actions, extra fields,
non-numeric/non-positive identifiers, oversized messages, and stale revisions have no authority.
Save, delete, conflict resolution, AI authorization, credentials,
backups, permission prompts, and confirmations remain native host surfaces.

The WebView disables file/content access, DOM database/geolocation, popups, mixed content and all
network loads. Requests and completed top-level pages are accepted only at the exact
`https://skin.powercal.local/` origin (no alternate scheme, host, credentials, or port); CSP blocks connections,
frames, objects and non-package scripts. A load watchdog falls back to Compose. System reduced
motion is sent to the skin and package CSS must also honor `prefers-reduced-motion`.

## Activation, example scope and native recovery

Installing a skin adds it to the list but **does not select it**. Manual skin
switches show a host-owned confirmation warning explaining the recovery gesture.
Settings/Tools always bypass a Web skin and are rendered natively. There is no
extra fixed Settings icon forced into the imported visual design.

Emergency rollback is a hidden Android Activity-level recognizer, observing all
touch events *before* a WebView or Compose skin: rapidly alternate eight brief
taps between two distinct on-screen locations (four taps per finger).
On success, PowerCal immediately persists the built-in native skin selection,
without deleting event data or installed skins, then opens native Settings.
Single-location taps, long presses, slow tapping, drags and unstable locations
cannot trigger the recognizer. Hardware Back from a Web timeline remains a
secondary route into native Settings.

The host keeps status/navigation bar safe areas outside the Web viewport.
Main-frame load errors and renderer crashes revert the persistent selection to native.

The examples/v3 package is a **development/demo skin**, not a complete replacement
for PowerCal's native Glass interface or the planned final default skin. Its HTML controls
demonstrate only the currently exposed bridge actions. Importing this demo is optional;
it should never become the default merely because the package was imported.

## Authoring

```text
python tools/skin-studio/skin_studio.py serve --port 8765
python tools/skin-studio/skin_studio.py build-v3 --manifest manifest.json --source-dir source --out skin.pcskin
python tools/skin-studio/skin_studio.py validate skin.pcskin
```

Simple mode edits only base-declared material slots. Advanced mode edits the scene/layer position
metadata and complete HTML/CSS/JS source with a mocked bridge preview. Both modes save the same v3
source project; an imported advanced project remains a valid base for simple edits.
