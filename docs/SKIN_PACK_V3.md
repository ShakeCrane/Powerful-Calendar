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
bounded event identity/title/kind/geometry). `action` accepts only host-registered names such as
navigation, Today, and opening Drafts. Unknown versions, types, actions, oversized messages, and
unknown fields have no authority. Save, delete, conflict resolution, AI authorization, credentials,
backups, permission prompts, and confirmations remain native host surfaces.

The WebView disables file/content access, DOM database/geolocation, popups, mixed content and all
network loads. Requests are served only from `https://skin.powercal.local/`; CSP blocks connections,
frames, objects and non-package scripts. A load watchdog falls back to Compose. System reduced
motion is sent to the skin and package CSS must also honor `prefers-reduced-motion`.

## Authoring

```text
python tools/skin-studio/skin_studio.py serve --port 8765
python tools/skin-studio/skin_studio.py build-v3 --manifest manifest.json --source-dir source --out skin.pcskin
python tools/skin-studio/skin_studio.py validate skin.pcskin
```

Simple mode edits only base-declared material slots. Advanced mode edits the scene/layer position
metadata and complete HTML/CSS/JS source with a mocked bridge preview. Both modes save the same v3
source project; an imported advanced project remains a valid base for simple edits.
