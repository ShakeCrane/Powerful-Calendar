# PC-TECH-006 — Lint warning risk classification

Date: 2026-10-24

Priority: P2

Status: **Static classification complete; Android 16 orientation compatibility remains unverified. Core freeze remains HOLD.**

## Baseline and evidence

This review classifies the existing `lintDebug` output; it makes no source, dependency, manifest, or resource changes.

- Code/test baseline: `477ddf44f1199e3316c5981e0a640c41efcdd416`.
- Full Gradle regression: **BUILD SUCCESSFUL**, 469/469 JVM tests across 58 suites, zero failures/errors/skips; source boundary and Debug/Android test APK assembly passed; lint 0 errors / 58 warnings.
- Full log: `.local/android-test/reports/freeze-20261024/pc-issue-134-full-gradle.log`, SHA-256 `935FC910ACB6663693564E5B5D323A018FC49A41B800BB4EA6DDA9C025CE726B`.
- Lint XML: `.local/android-test/reports/freeze-20261024/pc-issue-134-artifacts/lint-results-debug.xml`, SHA-256 `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47`.
- Build settings: `compileSdk=36`, `targetSdk=35`, `minSdk=26`; Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`.

The XML contains exactly 58 issues, all with severity `Warning`; it contains no lint errors.

## Classification

| Category | Lint IDs and count | Risk and disposition |
|---|---|---|
| Platform and target compatibility | `OldTargetApi` 1, `DiscouragedApi` 1, `LockedOrientationActivity` 1, `UnusedAttribute` 1 — **4** | `targetSdk=35` receives the target-version compatibility warning. The two orientation warnings point to the intentional portrait lock required by the product. Lint warns fixed orientation can be ignored in most cases starting with Android 16; this run did not verify API 36 orientation behavior. The API 33 back-callback attribute is ignored below API 33 and is intentional. |
| Dependency update suggestions | `AndroidGradlePluginVersion` 1, `GradleDependency` 3, `NewerVersionAvailable` 2 — **6** | Suggestions name Gradle 8.14.6, Activity Compose 1.13.0, Core KTX 1.19.1, AndroidX Test Core 1.7.0, coroutines 1.11.0, and Robolectric 4.17. These are version notices, not evidence of a vulnerability or a defect. No dependency was upgraded; each upgrade needs its own compatibility regression. |
| Compose conventions | `ComposableNaming` 12, `ModifierParameter` 2 — **14** | Naming and parameter-order guidance. No runtime or persistence failure is demonstrated. No UI code was changed. |
| KTX migration suggestions | `UseKtx` — **19** | Style suggestions across preferences and URI/color conversions. Avoid a bulk rewrite: for example, `ImportCheckpoint` deliberately uses synchronous `commit()` and must retain that durability behavior if ever migrated. |
| Launcher/resource analysis | `UnusedResources` 6, `IconLauncherShape` 6, `IconLocation` 1 — **13** | The manifest selects `@drawable/powercal_brand`; launcher alternatives and a reminder-channel string are reported as unused, while icon-shape notices describe assets. No asset or resource was deleted or restyled. |
| Redundant SDK condition | `ObsoleteSdkInt` — **2** | One API guard checks SDK 26 although `minSdk=26`; the other identifies `mipmap-anydpi-v26` as redundant. These are cleanup suggestions, not demonstrated behavior failures. |

Counts reconcile to **58**. Lint is a static rule set, not a security scanner or product-behavior proof; zero lint errors does not establish absence of defects.

## Decision and remaining risk

No warning provided evidence of a current core data-integrity, AI authorization, or reminder-delivery defect. The orientation warning is material to the stated portrait-only requirement: the manifest requests portrait, but the cited API 36 platform behavior has not been exercised in this run. It remains an explicit compatibility risk rather than a claimed pass. The user has excluded UI work from this execution, so this report does not alter or test orientation behavior.

No source, test, dependency, manifest, icon, or other design asset was modified. No tests were rerun for this report-only classification; the cited full regression is the existing PC-ISSUE-134 run. Overall freeze remains **HOLD**.

## Git

This report and the freeze handoff references are committed separately from the PC-ISSUE-134 code/test commit. Protected untracked test/design/observation assets remain untouched and unstaged.
