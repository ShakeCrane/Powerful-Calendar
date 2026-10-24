# PC-ISSUE-PHASE3-D01B — Draft Deck actions hidden by the keyboard

Date: 2026-10-25
Priority: P1
Status: Fixed and verified on API 35 AVD; physical-device confirmation remains with the user.

## Root cause

`DraftDeck` already shortened its dialog when the software keyboard appeared, but `DeckCardBody` only made the whole card scrollable when its measured height was below 320dp. With the keyboard visible, the viewport was taller than that threshold. The long form fields had their own scroll container while the publish/discard row remained at the bottom of the card and outside the visible viewport.

This was reproduced on API 35 `emulator-5556` at 1080×1600: with the system IME open, both action bounds were `Rect(0,0,0,0)` before scrolling and the instrumented visibility assertion failed. At 1080×2400 the same failure did not reproduce, which explains why a standard-height-only check missed it.

## Change

`DraftDeck.kt` now treats an IME-visible viewport as compact and places the entire card body in one vertical scroll container. The inner form stops creating a competing scroll container in that mode. The existing no-IME compact threshold remains 320dp; an intermediate broader threshold was rejected because it regressed the established small-screen navigation-target test.

The regression test opens an isolated synthetic draft, enters a long notes field, invokes the real system keyboard, captures Compose bounds relative to the IME, scrolls to each action, and performs the actual publish or discard operation. The test removes only its own random SQLite database in `finally`.

## Verification

- API 35 AVD, standard 1080×2400: publish and discard **2/2 PASS**. After scrolling, action rows occupy y=1433–1559 and the IME begins at y=1580 (21px clearance).
- API 35 AVD, short 1080×1600: publish and discard **2/2 PASS**. After scrolling, publish is `Rect(553,644,980,770)`, discard is `Rect(100,644,527,770)`, and the IME begins at y=791 (21px clearance). Both operations changed only the isolated test database as expected.
- AI chat IME regression D01a: **1/1 PASS** on API 35 AVD; composer and send action stayed above the keyboard without overlapping the message area.
- Existing compact-screen Compose regression: **1/1 PASS** after retaining the original 320dp no-IME threshold.
- Full offline Gradle gate: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 522/522 JVM tests, 70 suites, zero failures/errors/skips, 88 tasks. Lint: zero errors, 56 warnings.
- The short and standard AVD UI runs used temporary display overrides and isolated random databases. Original installed app/test APK hashes were restored exactly (`290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1` and `4D04D8DF1CA9D91516146BD62E4F6E630E7CF946A1ABF1217F66C8E035965E8C`); the display returned to 1080×2400 at density 420. The existing app database filenames/count were unchanged. No physical-device result is claimed.

### Evidence

- Full Gradle log: `.local/android-test/reports/freeze-20261025/pc-phase1-review-20261025/d01b-full-gradle-final-pass.log` — SHA-256 `B0B265DD93B7722E9DA39D6048F7B7975B3BB3828632C973C001C5357281A5FF`.
- Short-viewport AVD run: `api35-avd-d01b-short-publish-discard-final.log` — SHA-256 `DA465482FDD07DBBF1DC74DAE8A4E28E45915DD65894C35565DBF363E01C4577`.
- Standard-viewport AVD run: `api35-avd-d01b-standard-publish-discard-final.log` — SHA-256 `19A546E617259FDB91F0C0A030C161EFEF10E703FA53437D03506D20BFE9EB5D`.
- D01a chat IME regression: `api35-avd-d01a-chat-ime-final.log` — SHA-256 `4E745962F30A515F545FD2D5936FE7278F4C5DD396163ECDA8B78A4DC8E2501E`.
- Combined measured IME/button geometry trace: `api35-ime-geometry-final-trace.log` — SHA-256 `A126603BC7795E29FE1B65F8A1E03A516786C4C7FEAC508A64986D956A000657`.

The evidence directory is `.local/android-test/reports/freeze-20261025/pc-phase1-review-20261025/`.

## Remaining boundary

AVD behavior is verified for standard and short viewport sizes with the installed system keyboard. Manufacturer-specific IME, navigation-bar, and real-device behavior remains for the user's physical-device pass. Core-freeze status remains HOLD until all independent gates are closed.
