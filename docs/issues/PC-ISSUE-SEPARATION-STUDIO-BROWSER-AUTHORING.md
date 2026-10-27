# PC-ISSUE-SEPARATION — Browser Studio project and asset authoring

Date: 2026-10-27
Status: **browser authoring slice PASS; SEP-15 / S5 remains open**

## Root cause

The v3 CLI already supported machine-readable project inspection and transactional
file/project edits, but the browser Studio could only edit its fixed source panes and
additional UTF-8 files. A human using the Studio could not directly edit `project.json`,
create/delete arbitrary source files, or add/replace arbitrary binary assets. The README
still described these operations as wholly unavailable.

## Change

- The advanced source panel now includes `project.json`, text-file create/delete actions,
  and an arbitrary resource-file picker. Source and asset changes update the same
  `sourceFiles` collection consumed by preview and export.
- Valid `project.json` edits preserve unknown fields and are written to the package;
  malformed JSON remains recoverable in the editor and blocks preview/export until
  repaired. Basic v3 project, slot, and layer validation runs before applying the edit.
- Text files can be created under safe `web/` or `assets/` paths only when their
  extension is supported by the text editor. Required entry files and `project.json`
  cannot be deleted. Deletion scans UTF-8 sources for package paths, relative paths and
  basenames, and refuses deletion while it finds a reference.
- Arbitrary safe-named assets can be added or explicitly replaced. Selected bytes are
  preserved; replacements require confirmation. Existing image header, decode,
  dimension, per-file size and package file-count limits remain enforced.
- No formal page/component schema was added. Raw JSON structure is editable, but the
  Studio still lacks a semantic component-tree editor and full visual page reassembly.

## Regression evidence

Commands run from the separation worktree:

```powershell
python -m unittest discover -s tools/skin-studio/tests -p test_v3_source_editing.py -v
python -m unittest discover -s tools/skin-studio/tests -v
node --check tools/skin-studio/v3-studio.js
node --check tools/skin-studio/tests/e2e_v3_source.cjs
node tools/skin-studio/tests/e2e_v3_source.cjs
python tools/skin-studio/build_default_clear.py --verify
```

- Focused source-editing tests: **4/4 PASS**. Covers exact source/binary round trip,
  capability preservation, custom `project.json` fields, project editing, invalid JSON
  recovery, text create/delete, binary add/replace and rejection of an unsupported text
  extension.
- Full Skin Studio Python suite: **50/50 PASS**, zero failures/errors. Includes local
  HTTP, v1/v2/v3 packaging, preview, native recovery, project operations and source
  editing.
- Real Chrome Headless Studio E2E: **PASS**. It edited project structure and source,
  created/deleted text files, added/replaced binary assets, verified referenced source
  and asset deletion guards, previewed the edited source, exported/re-imported the v3
  package, and edited the re-imported project again. Existing recurrence and AI checks
  in the same E2E ran against explicit non-writing Studio Mocks.
- Clear bundle verification: **PASS**, 8 files / 376,661 bytes, SHA-256
  `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`.
- Syntax checks and `git diff --check`: **PASS**.

Detailed logs are under
`.local/android-test/reports/separation-20261027/studio-editor-authoring/`:

| Evidence | SHA-256 |
| --- | --- |
| Focused source-editing tests | `57CA5A732D4F1F687E3823D683521F8D861C050C97735B53B621F9DBBE31FCD6` |
| Full Studio suite | `C7F297E2CD5AA161F55BFC9497C0C855A2084F229A8ACE29E90D6B84992CBE54` |
| Chrome E2E | `443BF1EAC32CB8CA0F18A76364EDF108E01403FE2747E90885DEF94E269E9727` |
| Clear bundle verification | `9D3DB0782CE3C89198D6E27DA51650D8E7AB8BDD143783500D712FDA4444D82F` |

## Limits and next work

The Chrome E2E uses the browser Studio and its non-writing Mock Host. This issue did not
run Android WebView, an emulator, or a physical device and does not claim runtime visual
parity. Text-reference scanning is conservative but not a JavaScript/CSS dependency
parser; generated or computed references may need manual inspection before deletion.
Project JSON remains generic and has no formal page/component model. SEP-15 / S5, full
AI design workflow, Android/WebView acceptance, Native/Skin replay and the overall
Skin-First separation objective remain open.

## Commit

- Implementation and regression: `151960f` — `feat(studio): 支持完整项目源码与资源编辑`.
- Pushed to `origin/feat/skin-first-separation`.
