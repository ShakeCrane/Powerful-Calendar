# PowerCal v3 skin: semantic option groups (additive bridge v1)

This is an **additive** host-side protocol extension. The skin ZIP schema remains v3,
the WebMessagePort message version remains 1, and existing actions and payload fields
remain valid. Existing installed skins with a static menu still render their static
menus, while skins consuming option groups automatically include later sibling options.

## Host-owned state

A `state` message may include `payload.optionGroups`:

```json
{
  "optionGroups": [
    {
      "id": "calendar.import",
      "label": "导入",
      "items": [
        {
          "id": "calendar.import.file",
          "label": "导入日历文件",
          "detail": "选择文件，由本地程序优先处理",
          "icon": "file",
          "enabled": true
        }
      ]
    }
  ]
}
```

The host owns membership, labels, semantic icon names, enabled state, and the exact
actions. The skin owns the layout, ordering of groups, color, typography and animation.
Render a group's `items` with one reusable option-row component rather than hardcoding
each action. An unknown additional group can be ignored; an item missing from state
is never guessed by the skin.

To invoke, send on the existing connected `MessagePort`:

```json
{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.import.file"}}
```

Only advertised/allowlisted option IDs are accepted. Unknown IDs, extra parameters
and non-string IDs are rejected before host dispatch. File access and Android runtime
permission remain host-owned, never granted to the WebView.

## Compatibility boundaries

- Existing v3 skins still load, even if they do not know about `optionGroups`.
- Existing static menus do not magically grow. The automatic sibling behavior only
  applies to skins that choose a reusable group renderer.
- `calendar.import.file` uses the existing file picker and local-first intake.
- `calendar.import.system` triggers the existing one-time system calendar snapshot
  with the same Android permission check.
- Native settings and emergency recovery remain available.
- The bridge does not grant the skin raw file access, dynamic method invocation,
  publishing authority, credentials, or access to PowerStore.
- A future sibling is an additive entry in `SemanticOptions`, **not** a new menu
  design or a schema version bump.

## Release test requirements

Check old v3 pack load, an empty/static pack, a pack rendering `calendar.import`,
a future additional sibling item, unknown action ID rejection, extra-field rejection,
and permission denial. Browser demo behavior alone is not an Android device test.
