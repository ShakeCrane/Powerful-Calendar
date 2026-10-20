# PowerCal Skin Package ABI v1

## Purpose and boundary

PowerCal skin packages modify presentation only: colors, semantic axis colors, typography scale,
card corners, time-point line thickness, and optional PNG/WebP image slots.
They cannot contain code, scripts, custom XML drawables, network endpoints, tools or permission
instructions. No skin modifies calendar schema, recurrence, AI approval, reminder scheduling, or
event identity. Missing slots fall back to the original Compose rendering.

The built-in, undeletable **PowerCal 原生玻璃** is always shown in Settings > 皮肤.
"导出原生皮肤" writes a complete editable and importable .pcskin ZIP template for Claude.
It is independent of the app's native calendar backup and release signature.

## Archive layout

    manifest.json
    images/logo-light.png        (optional)
    images/cover-dark.webp       (optional)

A .pcskin archive is a ZIP. Max compressed size 10MB, max inflated 12MB, 24 entries.
Manifest max 80KB; each bitmap max 3MB and 4096x4096 or 8 million decoded pixels.
Only manifest.json and lowercase ASCII images/name.png or images/name.webp are allowed.
All referenced images MUST include exact SHA-256 hashes; undeclared extras and duplicate
paths are rejected. The SHA-256 detects corruption, not creator authenticity.

## Valid minimal manifest

{
  "format": "powercal-skin",
  "schemaVersion": 1,
  "id": "my-first-skin",
  "name": "My First Skin",
  "light": {
    "primary": "#2457D6",
    "onPrimary": "#FFFFFF",
    "alarm": "#3D5FD8",
    "activity": "#177A63",
    "deadline": "#A8501C"
  },
  "dark": {
    "primary": "#9DB8FF",
    "onPrimary": "#0B2A66",
    "alarm": "#9DB8FF",
    "activity": "#7FD6C2",
    "deadline": "#FFB68C"
  },
  "layout": {
    "cardRadiusDp": 28,
    "barRadiusDp": 20,
    "pointLineDp": 2,
    "fontScale": 1.0
  },
  "imagesLight": {},
  "imagesDark": {},
  "sha256": {}
}

Skin ID: 3–48 chars, starts lowercase ASCII alphanumeric and then lowercase ASCII
alphanumeric / underscore / hyphen. Importing the reserved powercal-default is prohibited.
Names are 1–60 characters. Duplicate installed IDs require explicit deletion first.

With images, for example add a ZIP entry images/logo.png, set
imagesLight {"BRAND_MARK": "images/logo.png"}, and put
sha256 {"images/logo.png": "the 64 hexadecimal chars of the SHA256 of PNG bytes"}.

## Image slot identifiers

| Name | Purpose |
| --- | --- |
| TIMELINE_BACKGROUND | timeline wallpaper |
| DECK_BACKDROP | pending draft deck background |
| MONTH_BACKGROUND | month calendar wallpaper |
| COMPLETION_EMBLEM | draft publication feedback |
| BRAND_MARK | app brand/toolbar illustration |
| ALARM_MARKER | alarm time-point decoration |
| ACTIVITY_BADGE | interval activity decoration |
| DEADLINE_MARKER | deadline point decoration |
| AI_AVATAR | AI message/avatar illustration |
| SETTINGS_ICON | setting navigation icon |

Slots may be omitted. All labels and controls remain native Compose widgets for
accessibility; no skin artwork can modify interaction semantics.

## Palette names

primary, onPrimary, primaryContainer, onPrimaryContainer,
secondary, onSecondary, tertiary, onTertiary, background, onBackground,
surface, onSurface, surfaceContainerLow, surfaceContainer,
surfaceContainerHigh, outline, alarm, activity, deadline, nowLine,
gridLine, glassFill, glassBorder, glassHighlight.

Colors must be #RRGGBB or #AARRGGBB. Primary/onPrimary,
surface/onSurface, background/onBackground MUST be supplied as pairs.
Use high contrast, especially for semantic lines and small text in dark mode.

## Geometric tokens and typography

- cardRadiusDp: 12..40, default 28
- barRadiusDp: 8..32, default 20
- pointLineDp: 1..5, default 2
- fontScale: 0.85..1.2, default 1

Version 1 supports font scale and native font families only. External TTF/OTF fonts,
custom vector shape scripting, animation scripts and arbitrary third-party libraries
are NOT part of this ABI; adding those would require a separately reviewed extension.

## Storage, safety and upgrade

Settings can import, select, delete with confirmation, and export the original.
ZIPs reside in private files/skin-packs, separate from the schema v3 native DB.
The app persists only a selected skin ID and verifies the package again at restart.
An invalid/missing package resets to bundled native Glass, never a blank screen.
Deleting a selected pack first selects the original and never touches event data.
The native BackupEnvelope v1 currently does not include installed skin ZIPs;
export and restore them separately. Every archive is treated as untrusted input.

For Claude: start from the exported original .pcskin template, add a balanced
light/dark palette, PNG/WebP images with exact checksums and conservative font
scaling, then verify on-device import, switch, restart, dark mode, low-RAM mode,
timeline, AI chat, month, pending deck and commit feedback. Preserve all functional
semantics and check the rights/provenance of third-party artwork.
