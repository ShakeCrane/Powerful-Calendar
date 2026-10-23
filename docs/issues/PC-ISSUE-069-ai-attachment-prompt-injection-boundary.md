# PC-ISSUE-069 — Keep attachment instructions untrusted at the AI request boundary

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-124`)

Status: **CLIENT REQUEST BOUNDARY PASS / LIVE MODEL COMPLIANCE NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The reconstructed acceptance ledger requires hostile instructions embedded in an attachment to remain untrusted data. Existing AI safety tests covered model-generated proposal hints and malformed structured responses, but did not inspect the actual `AiClient.analyzeEvidence` request roles and payload framing.

## Change

- Added `AiChatClientTest.analyzeEvidenceKeepsInjectedAttachmentInUntrustedUserContent` with a local loopback HTTP stub.
- The test verifies the fixed system message says extracted file content is untrusted and its instructions must not be followed; the malicious text remains inside the user-role attachment delimiters.
- It also verifies no `tools` or `tool_choice` are supplied and the analysis response returns no proposal source in this case.
- Test-only change; no production, UI, schema, user data, or design asset changed.

## Verification

Environment: Robolectric API 34 and a local loopback HTTP stub. No external model/network service or Android device was used.

- Focused test `AiChatClientTest.analyzeEvidenceKeepsInjectedAttachmentInUntrustedUserContent`: **1/1 PASS**, zero failures/errors. Log `.local/android-test/reports/freeze-20261024/pc-issue-069-attachment-untrusted-targeted-20261024.log`, SHA-256 `DF967CCE7EAD6B14F318607A1F642BEB0BEACC1628A100CB1AA29E08E2BC945B`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-069-targeted-junit/TEST-dev.powerfulcalendar.ai.AiChatClientTest.xml`, SHA-256 `E492A189C43A585FA587046435033B52437263F10DDEE6F6A841C447BCC8D729`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, **421/421** tests across 55 suites, zero failures/errors/skips, lint 0 errors / 55 warnings. The run includes 420 tracked tests and one protected, untracked test; that protected file was not modified or staged. Log `.local/android-test/reports/freeze-20261024/pc-issue-069-full-gradle-20261024.log`, SHA-256 `F4EFE9D371D0FBF9DFAB367212C365CDFD6E41DEE3242206A68F39C2026FCB59`; full `AiChatClientTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-069-full-junit/TEST-dev.powerfulcalendar.ai.AiChatClientTest.xml`, SHA-256 `F06173002CD53D22B6319EAAD86DB8BA84C1018067007118B57EFCF975880F1B`.
- Debug APK SHA-256: `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`.

`REC-P0-124` is PASS for the client request-boundary contract only. A prompt and role boundary do not prove that a live model will ignore an injection; model/provider behavior remains untested. The candidate remains `UNMAPPED` to the absent historical checklist, and overall freeze remains **HOLD**.

## Git

- Test-only commit `926f9b2098d9fa529ba8887625b37f135ed1602c` (`test(AI): 验证附件指令保持不可信输入`) is pushed to `origin/main`.
- This report and the synchronized ledger/verdict updates are committed separately.
