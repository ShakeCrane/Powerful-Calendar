# Skin Studio AI Automation Interface v1

Status: **implemented for validated `.pcskin` v3 inspection, editing, building and validation**.
This interface makes source, assets, `project.json`, and package metadata callable by
scripts and agents. It does not claim complete visual Studio editing or Native/Skin
functional parity.

## Entry point and output contract

Run commands from the repository root with Python 3:

```powershell
python tools/skin-studio/skin_studio.py inspect-v3 path\to\skin.pcskin
python tools/skin-studio/skin_studio.py read-v3 --file path\to\skin.pcskin --path web\app.js
python tools/skin-studio/skin_studio.py edit-v3 --file path\to\skin.pcskin --operations operations.json --out path\to\skin-edited.pcskin
python tools/skin-studio/skin_studio.py build-v3 --manifest manifest.json --source-dir source --out path\to\skin-built.pcskin --json
python tools/skin-studio/skin_studio.py validate path\to\skin-edited.pcskin --json
```

`inspect-v3`, `read-v3`, and `edit-v3` emit one UTF-8 JSON object on stdout. Their
errors emit a JSON object on stderr and return exit code 2. A successful `edit-v3`
returns package SHA-256, output path, resulting manifest, and before/after hashes for
every changed file. The input and output package paths must differ. Output creation is
exclusive: an existing destination is never overwritten; select a new path.

`build-v3 --json` and `validate --json` emit one UTF-8 JSON receipt on stdout. Build
receipts contain the output path, package SHA-256, byte and source-file counts, and the
validated manifest. Validation receipts contain package SHA-256, byte count, runtime
version, skin identity and version-specific file/scene/asset counts. Execution failures emit
`{"schemaVersion":1,"command":"…","ok":false,"error":"…"}` on stderr and return
exit code 2. `build-v3` validates and reimports its generated package before publishing
it, then atomically creates a new output path; it never overwrites an existing file.
Without `--json`, successful build/validate commands retain their human-readable output.

## Inspection and source reads

`inspect-v3` first validates the complete package using the production v3 validator.
Its response contains:

- `manifest` and `manifestSha256` (the SHA-256 of canonical UTF-8 JSON with sorted
  keys and compact separators);
- the full parsed `project.json` object, so agents can inspect and query its structure;
- package and inflated sizes; and
- a path-sorted file index with size, SHA-256, and UTF-8/binary classification.

Use `read-v3 --file <skin> --path <package-path>` to read one file. Recognized source
extensions return UTF-8 text when valid; binary/unknown extensions and invalid UTF-8
return Base64. This is extension-based because arbitrary binary data can itself happen
to form valid UTF-8. Each response contains exact byte count and SHA-256. Paths are
package-relative POSIX paths under `web/` or `assets/`, or the root `project.json`.

## Transactional edit document

The operation file is UTF-8 JSON with exactly one top-level field:

```json
{
  "operations": [
    {
      "op": "write",
      "path": "web/custom.css",
      "encoding": "utf-8",
      "content": ".card { border-radius: 18px; }\n",
      "expectedAbsent": true
    }
  ]
}
```

An operation batch contains 1–128 operations. It is applied to an in-memory package,
then the **entire final package** is validated and built with `runtime_package_v3` before
any output is created. A failure leaves the input package unchanged and creates no
output package. The final archive retains the existing v3 size, path, resource, remote
URL, capability and manifest validation rules.

Supported operations:

| Operation | Fields and behavior |
|---|---|
| `write` | `path`, `encoding` (`utf-8` or `base64`), `content`, and exactly one guard: `expectedSha256` for replacing an existing file, or `expectedAbsent: true` for creating a new file. |
| `delete` | `path` and the file's `expectedSha256`. Required runtime files cannot be deleted. |
| `move` | `from`, unused `to`, and source `expectedSha256`; package references are not rewritten automatically. |
| `json-patch` | `path: "project.json"`, current file `expectedSha256`, and a JSON Patch list. Supported ops are `add`, `remove`, `replace`, `move`, `copy`, and `test`; paths use RFC 6901 JSON Pointers. |
| `manifest-patch` | current `expectedSha256` from `inspect-v3`, and JSON Patch ops. `format`, `schemaVersion`, `entrypoint`, `project`, and `sha256` are managed and cannot be changed. Changing `capabilities` additionally requires `allowCapabilityChange: true`; the package is still subject to Android Host capability enforcement. |

Unknown top-level fields in v3 manifests are preserved as opaque metadata through
`inspect-v3`, `edit-v3`, build, and re-import. The Android Host ignores those fields;
only the explicitly validated `capabilities` list can affect access, and that list
still rejects unsupported values.

Each edit has a SHA precondition to reject stale work. When applying multiple changes
to one JSON file, combine them in one `json-patch` operation. Filesystem names are
validated; traversal, absolute paths, backslashes, and `manifest.json` writes are
rejected. Limits are 128 package operations, 256 JSON Patch operations per JSON file,
96 package source files, 600 KB per text file, 3 MB per binary file, and the v3 archive
limits enforced by the production validator.

## Agent workflow

1. Run `inspect-v3` and keep its `packageSha256`, file hashes, and `manifestSha256` as
   the revision snapshot.
2. Run `read-v3` for each source or binary asset that needs inspection. Decode binary
   files only when needed; preserve their bytes if they are not being changed.
3. Construct a bounded operation JSON. Use JSON Patch for component/project structure,
   source writes for HTML/CSS/JS/JSON, and Base64 for binary assets.
4. Run `edit-v3` to a **new** output path. A stale guard or invalid final package
   returns a structured error and does not create the output.
5. For source-directory projects, run `build-v3 --json` to create a validated package;
   for edited packages, run `validate --json` on the exported `.pcskin`. Both return
   hashes and counts; build refuses an existing output path.
6. Inspect/read the result again and compare intended
   changes and hashes. Use the Studio browser preview and Android WebView/device
   acceptance for visual and runtime claims.

### Re-editing an Android-installed v3 skin

In Android Settings, export the installed third-party skin with the system file
picker, then pass that `.pcskin` to `inspect-v3`, `read-v3`, and `edit-v3` above.
The v3 editor preserves package files, opaque Manifest metadata, capability
declarations, and optional fields it does not change; the final package is
validated and re-imported before the output file is created.

The exported package keeps its existing Manifest ID. Android intentionally
rejects installing another package with an already installed ID. To keep both
versions side by side, use a guarded `manifest-patch` operation to assign a new
ID before export. The current browser editor and `*-v3` operations do not edit
installed v1/v2 packages; their Android export paths do not imply v3 Studio
compatibility.

The interface enables arbitrary v3 source/asset file CRUD and generic JSON tree edits;
it does not impose a formal page/component schema on `project.json`. The browser Studio
also exposes direct project JSON editing, supported UTF-8 file creation/deletion, and
arbitrary asset add/replace/delete with text-reference guards; this is not a semantic
component-tree editor. Host capabilities remain requests declared by the skin and never
constitute user approval. Browser Mocks and successful package validation do not prove
Android behavior, accessibility, device compatibility, or Core/Host/Skin parity. Full
visual component editing, Host Bridge parity, default-skin cutover, and physical-device
acceptance remain separate open work.

## Automated verification

```powershell
python -m unittest discover -s tools/skin-studio/tests -p test_project_ops_v3.py -v
python -m unittest discover -s tools/skin-studio/tests -v
python tools/skin-studio/build_default_clear.py --verify
python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk
```

The APK check requires a previously built Debug APK and verifies that its packaged
`assets/powercal-clear-default.pcskin` is byte-identical to the canonical source bundle.
