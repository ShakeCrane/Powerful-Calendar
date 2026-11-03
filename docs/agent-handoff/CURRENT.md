PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
BASELINE: ac8d1555a1c177e600e72e4edb5b450189f1fc2c
CURRENT_TASK: R-05
TASK_STATUS: READY
LAST_COMPLETED_TASK: R-04 (PASS)
NEXT_TASK: R-05
NEXT_ACTION: Execute R-05 only
DEFERRED_TASKS: R-06 and R-07 remain deferred until dependent Skin parity is proven
PHASE: R
SEP_SIGNOFF: INCOMPLETE (historical parity remains unsigned)
PRODUCT_CUTOVER: HOLD

OWNER_OVERRIDE_2026_11_03:
  Decision: 历史皮肤不保留，正式 App 只保留一套默认 .pcskin；Native 仅用于安全恢复。
  R03: QA 迁移旧 Native/Clear/第三方/损坏选择后，仅清理测试应用私有 files/skin-packs 内的旧安装 .pcskin；不归档、不导出历史包，不保留多皮肤列表。
  Protected: Calendar Store、草稿、提醒、AI 历史、Studio 源工程、设计源稿、测试夹具、外部 SAF 文件、Git Worktree。
  Gate: 正式生产 Cutover 仍为 HOLD；R-03 删除仅在隔离 QA 应用私有目录验证。

OWNER_GOAL:
  Core: Owns business behavior, data, transactions, reminders, AI, permissions, and stable feature APIs.
  Skin: Owns normal presentation and requests Core capabilities through the approved APIs.
  Default_skin: 正式出厂只保留唯一默认皮肤（一套未美化的 .pcskin）；Native is the safety recovery path.
  Studio: Create a complete skin from a blank project, including components, bindings, assets, editing, preview, export, and Android runtime.
  Beautification: After separation acceptance, apply Claude design work through the Studio API without changing Core.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
PROTECTED_ASSETS: docs/agent-handoff/WORKTREE_DISPOSITION.md
ACCEPTANCE_MATRIX: docs/agent-handoff/SINGLE_SKIN_ACCEPTANCE.md

LATEST_VERIFIED_EVIDENCE:
  - "R-04 PASS from 88c43ac22345bf729305f50aaba1dc4ff65228a2: removed the Native Tools skin catalogue and its page-only callbacks; preserved Host/SAF/runtime compatibility callbacks required by Clear/v2 until R-05/R-06. No package or Store data was deleted or changed."
  - "Forced serial JVM rerun: 966/966, 0 failures/errors/skips. Skin and Native source boundaries PASS; Debug APK, Lint (0 errors / 56 warnings), and AndroidTest Kotlin compilation PASS. Production Debug APK SHA-256 33E831A4A194A72FA35A0B626C58183597DA7FB8120DDB1E2E432ACCF5B66431."
  - "API 35 isolated AVD PowerCal_ChatGPT_QA35_20261103 / emulator-5584: WebView renderer fault recovery test 1/1 PASS. Native recovery page omitted installed-skin import/export controls; ICS import, backup/restore and actionable notification/exact-alarm controls remained. Formal Store, pending, commit, identity and sequence snapshots matched. QA app ID dev.powerfulcalendar.d05qar04; APK SHA-256 E2954B09FD676229DC3E7D2D217CE6052C7503C7354C46845FFC8D57D994C225."
  - "R-04 evidence: .local/android-test/reports/R-04-20261104/ (Gradle logs, device identity, failure/retry notes); exact AndroidTest XML and lint report remain under app/build/reports and app/build/outputs. NativeSkinCatalogReview stays while PowerCalApp's Clear/v2 host actions still call it; Clear Settings catalogue is R-05. Product Cutover remains HOLD."
  - "G-01 PASS: clean independent Worktree at the fetched integration baseline."
  - "G-02 PASS: 29 Worktrees inventoried; protected changes retained; ledger commit f4c0f6c6d9e31c6bfc4d84dfc0937f550a32e92a."
  - "G-03 PASS: UTF-8, task-pointer, history-preservation, scope, and Git diff checks passed; documentation-only commit recorded in this branch."
  - "G-04 PASS against tested SHA 1e57da5cbb19b4b1348fe390174f629c8871fc47: both source boundaries PASS; Gradle gate PASS; JVM 946/946, 0 failures/errors/skips; Debug APK, lint, and AndroidTest Kotlin compile PASS."
  - "Lint: 54 warnings, 0 errors; Kotlin compiler emitted 16 warnings; native-library strip emitted 1 packaging warning. No Gradle/test failures."
  - "Skin Studio: Python 68/68; four requested JS syntax checks PASS; Node title-folding tests 10/10; browser source roundtrip E2E PASS."
  - "APK SHA-256 68f5f8308a6204c45662f4b39e5c4a4564d98f161953beef0db33b0742042af6. APK contains exactly one .pcskin; source/APK package SHA-256 both ad0202fc6b9f6341cd0cb9bc1937dc4dd6fe2eda42082cea252e0c8f3f13d403 (9 files, 397275 bytes)."
  - "API 35 QA AVD PowerCal_ChatGPT_QA35_20261103 booted; Debug and AndroidTest APK installs both PASS using adb install -r. Instrumentation execution NOT TESTED."
  - "Evidence: .local/android-test/reports/single-default-G-04-20261103/ (environment, Gradle, Studio, package, device, and install logs); lint detail at app/build/reports/lint-results-debug.xml."
  - "G-05 PASS: 16 acceptance items map all 16 original SEP rows; each item is PARTIAL overall pending its declared evidence. Original SEP records are unchanged."
  - "G-05 identifies no newly confirmed FAIL; SSA-10 physical/OEM reminder evidence remains BLOCKED (REC-P1-086). Android interaction tests were NOT TESTED in G-05; other physical checks were NOT TESTED. Historical scoped device passes remain previous evidence only."
  - "R-01 PASS at d26d31b0f4935e0060271aa97070f511d4785c4f: runtime dispatch, startup choice, package/SAF effects, edit checkpoint, and Native recovery audited; appendix added to SINGLE_SKIN_ACCEPTANCE.md. Static path/task/source checks and diff validation passed; no Android or physical test run was required. KEEP_HOST/RECOVERY/DEFAULT_SKIN/TEST_COMPAT and retirement scopes mapped to R-02–R-07."

  - "R-02 PASS on source baseline b6f111eb5eadcef31d6c353ddb601df495b53383 plus R-02 changes: explicit QA-only fresh factory policy; production restore remains opt-in and revalidates. Full Gradle gate PASS: JVM 953/953, skin/native boundaries, debug APK, lint (55 warnings, 0 errors), AndroidTest Kotlin compile. Targeted SkinPackageManagerTest 23/23."
  - "API 35 isolated AVD PowerCal_ChatGPT_QA35_20261103, emulator-5584, application ID dev.powerfulcalendar.d05qar02: Android instrumentation 4/4 executions PASS (fresh WebView, corrupt-factory Native fallback, two force-stop cold WebView restarts). Native fallback also verified on an additional cold Activity launch. Store table snapshots remained unchanged for the tested boot actions."
  - "Production debug APK SHA-256 4ab2d8915793dae697266285b7cd2b5774cf3a074c545168505fbe9c8ab9b504; isolated QA APK SHA-256 486938d9e9c87184005ac7c9af50a5444c0a4861aed102ae488066b548477321. Logs: .local/android-test/reports/R-02-20261103/ within this worktree. Initial SDK-path, snapshot-helper and PowerShell argument failures are retained in the logs; corrected reruns passed."

  - "R-03 PASS against source baseline 9da0eda4e5dd44b839825b74d365b1219d143db5 plus R-03 changes: explicit isolated QA migration runs before Activity; pending/complete preference commits protect resumable deletion. On failure the QA caller must retry before normal startup. No production startup calls migration; product cutover remains HOLD."
  - "R-03 full Gradle gate PASS: JVM 966/966, migration class 13/13, both source boundaries, debug APK, lint (56 warnings, 0 errors), AndroidTest Kotlin compile. Production debug APK from the full gate SHA-256 4ed97c462cebc17b538800fe98e8e950cc941b86cb02b102e682270e8d6773f1; later production rebuild SHA-256 29b6684a64405cf573261099dd22c8f3a01119e55cb79cadb71bbd9fd16ffe16. APK contains one factory .pcskin, SHA-256 ad0202fc6b9f6341cd0cb9bc1937dc4dd6fe2eda42082cea252e0c8f3f13d403."
  - "API 35 AVD PowerCal_ChatGPT_QA35_20261103, emulator-5584, isolated application ID dev.powerfulcalendar.d05qar03: 23 successful instrumentation logs; six additional attempts aborted due emulator disconnections or a test package requiring reinstall, with retries passing (seven aborted log files include one copy). Fresh, historical Native, historical Clear and unknown selection each passed preparation plus two process cold starts; legacy packages to Clear, corrupt factory to Native, real recurrence/exception/completion/draft/reminder Store snapshots, commit/delete failures, symlink containment and cross-process pending cleanup retry also passed. Only the isolated QA app data was cleared. QA APK SHA-256 1582d72837d0679b19d4212fe531a65a67a205481eabdf36d63cc16bcbb19347. Logs: .local/android-test/reports/R-03-20261104/ within this worktree."

BLOCKERS: R-03 has no task blocker; Physical/OEM reminder evidence (REC-P1-086) remains BLOCKED; simultaneous-alarm behavior remains unresolved in the frozen contract. Historical SEP parity remains unsigned and product Cutover stays HOLD. G-04 emulator -list-avds returned no entries although adb confirmed the running QA AVD; Android instrumentation was not run in G-04 or G-05.
