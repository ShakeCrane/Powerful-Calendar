"""Checks the Studio catalog against the implemented closed Android bridge."""
import json
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from host_binding_catalog import (binding_warnings, load_catalog,
                                  validate_action_parameters, validate_project_bindings)
from runtime_package_v3 import build_files, unpack, validate_project
from blank_project_v3 import blank_project as make_blank_project

REPO = ROOT.parents[1]
HOST = REPO / "app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt"
EDIT = REPO / "app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt"
APP = REPO / "app/src/main/java/dev/powerfulcalendar/ui/v2/PowerCalApp.kt"


def branch_names(path, start, marker, indent):
    lines = path.read_text(encoding="utf-8").splitlines()
    start_index = next(index for index, line in enumerate(lines) if start in line)
    marker_index = next(index for index in range(start_index, len(lines))
                        if marker in lines[index])
    marker_indent = len(lines[marker_index]) - len(lines[marker_index].lstrip())
    found, pending = set(), []
    for line in lines[marker_index + 1:]:
        line_indent = len(line) - len(line.lstrip())
        body = line.strip()
        if line_indent == marker_indent and body == "}":
            break
        if line_indent != indent or "->" not in body:
            if line_indent == indent:
                pending.extend(re.findall(r'"([A-Za-z][A-Za-z0-9.]*)"', body))
            continue
        pending.extend(re.findall(r'"([A-Za-z][A-Za-z0-9.]*)"', body.split("->", 1)[0]))
        found.update(pending)
        pending.clear()
    return found


def blank_project():
    return {
        "projectVersion": 3,
        "mode": "simple",
        "pages": [{"id": "page-timeline", "route": "TIMELINE", "title": "Timeline",
                   "components": [{"id": "today", "type": "button",
                                   "props": {"label": "Today"}, "children": []}]}],
    }


class HostBindingCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = load_catalog()
        cls.actions = {item["name"]: item for item in cls.catalog["actions"]}

    def test_actions_match_the_real_closed_host_decoder(self):
        actual = branch_names(HOST, "internal fun decodeBridgeAction",
                              "return when (name) {", 8)
        self.assertEqual(actual, set(self.actions))
        self.assertEqual(1, self.catalog["bridgeVersion"])
        for action in self.catalog["actions"]:
            self.assertEqual(1, action["version"])
            self.assertEqual("host.action", action["requiredCapability"])
            self.assertEqual("#/actionResult", action["resultSchema"])
            self.assertEqual("#/actionResult/errors", action["errors"])
            schema = action["parameterSchema"]
            self.assertEqual("object", schema["type"])
            self.assertFalse(schema["additionalProperties"])
            self.assertTrue(set(schema["required"]) <= set(schema["properties"]))

    def test_edit_command_names_match_the_real_wire_decoder(self):
        actual = branch_names(EDIT, "fun decode(raw: String)",
                              "return when(name) {", 12)
        commands = {item["name"]: item for item in self.catalog["editCommands"]["commands"]}
        self.assertEqual(actual, set(commands))
        for command in commands.values():
            self.assertEqual(1, command["version"])
            self.assertEqual("host.action", command["requiredCapability"])
            self.assertEqual("#/editCommands/result", command["resultSchema"])

    def test_state_fields_match_the_host_snapshot_and_are_source_linked(self):
        source = APP.read_text(encoding="utf-8")
        start = source.index("WebSkinBridgeRuntime.state = {")
        end = source.index("WebSkinBridgeRuntime.stateRevision++", start)
        state_lines = source[start:end].splitlines()
        base_indent = min(len(line) - len(line.lstrip()) for line in state_lines
                          if "JSONObject().put(\"bridgeVersion\"" in line)
        actual = set()
        for line in state_lines:
            indent = len(line) - len(line.lstrip())
            if indent <= base_indent + 4:
                actual.update(re.findall(r'\.put\("([A-Za-z][A-Za-z0-9]*)"', line))
        fields = {item["path"]: item for item in self.catalog["stateFrame"]["fields"]}
        self.assertEqual(actual, {path.split(".", 1)[0] for path in fields})
        self.assertEqual("host.read", self.catalog["stateFrame"]["capability"])
        self.assertTrue(all(type(item.get("bindable")) is bool for item in fields.values()))
        self.assertIn("WebSkinTimelineProjection.build", source)
        self.assertIn("WebSkinMonthProjection.build", source)

    def test_today_binding_is_closed_and_schema_checked(self):
        validate_action_parameters("today", {})
        validate_action_parameters("openAiSettings", {})
        with self.assertRaisesRegex(ValueError, "unknown parameter"):
            validate_action_parameters("openAiSettings", {"apiKey": "secret"})
        validate_action_parameters("panTimeline", {"deltaMinutes": 30})
        validate_action_parameters("clearAiTemporaryCache", {})
        with self.assertRaisesRegex(ValueError, "unknown Host action"):
            validate_action_parameters("inventedBusinessAction", {})
        with self.assertRaisesRegex(ValueError, "unknown parameter"):
            validate_action_parameters("clearAiTemporaryCache", {"path": "/private"})
        with self.assertRaisesRegex(ValueError, "must not be zero"):
            validate_action_parameters("panTimeline", {"deltaMinutes": 0})
        with self.assertRaisesRegex(ValueError, "unknown parameter"):
            validate_action_parameters("today", {"jump": True})

    def test_chat_message_actions_select_only_host_owned_history(self):
        validate_action_parameters("createDraftFromChatMessage", {
            "messageId": "message-1", "historyRevision": 4, "source": "MESSAGE_CONTENT"})
        validate_action_parameters("createDraftFromChatMessage", {
            "messageId": "message-1", "historyRevision": 4, "source": "PROPOSAL_SOURCE"})
        with self.assertRaisesRegex(ValueError, "catalogued value"):
            validate_action_parameters("createDraftFromChatMessage", {
                "messageId": "message-1", "historyRevision": 4, "source": "ARBITRARY_TEXT"})
        with self.assertRaisesRegex(ValueError, "unknown parameter"):
            validate_action_parameters("createDraftFromChatMessage", {
                "messageId": "message-1", "historyRevision": 4,
                "source": "MESSAGE_CONTENT", "text": "untrusted"})
        validate_action_parameters("copyChatMessage", {
            "messageId": "message-1", "historyRevision": 4})
        with self.assertRaisesRegex(ValueError, "unknown parameter"):
            validate_action_parameters("copyChatMessage", {
                "messageId": "message-1", "historyRevision": 4, "content": "untrusted"})

    def test_project_bindings_are_validated_without_automatic_capability_upgrade(self):
        project = blank_project()
        project["pages"][0]["components"][0]["props"]["hostAction"] = {
            "version": 1, "name": "today", "parameters": {}}
        project["pages"][0]["components"][0]["props"]["hostStateBinding"] = "timeline.anchorMillis"
        validate_project(project)
        self.assertEqual([
            "This project binds Host state but does not request host.read; bound values will be empty.",
            "This project binds Host actions but does not request host.action; Android Host will ignore those requests.",
        ], binding_warnings(project, []))
        self.assertEqual([], binding_warnings(project, ["host.read", "host.action"]))
        with self.assertRaisesRegex(Exception, "unknown Host action"):
            validate_project_bindings({**project, "pages": [{**project["pages"][0], "components": [
                {"id": "bad", "type": "button", "props": {"hostAction": {
                    "version": 1, "name": "publishEverything", "parameters": {}}}, "children": []}]}]})

    def test_build_preserves_explicit_read_only_capabilities_for_action_binding(self):
        manifest, files = make_blank_project()
        project = json.loads(files["project.json"].decode("utf-8"))
        button = project["pages"][0]["components"][0]
        button["type"] = "button"
        button["props"].update({
            "hostAction": {"version": 1, "name": "today", "parameters": {}},
            "hostStateBinding": "timeline.anchorMillis",
        })
        files["project.json"] = json.dumps(project, ensure_ascii=False).encode("utf-8")
        packaged = build_files(manifest, files)
        decoded_manifest, decoded_files = unpack(packaged)
        self.assertEqual(["host.read"], decoded_manifest["capabilities"])
        self.assertEqual("today", json.loads(decoded_files["project.json"])["pages"][0]
                         ["components"][0]["props"]["hostAction"]["name"])
        warnings = binding_warnings(project, decoded_manifest["capabilities"])
        self.assertEqual([
            "This project binds Host actions but does not request host.action; Android Host will ignore those requests."
        ], warnings)

    def test_retired_skin_catalog_actions_are_not_exposed_as_host_actions(self):
        names = set(self.actions)
        self.assertTrue({"selectSkin", "importSkin", "deleteSkin", "exportInstalledSkin"}.isdisjoint(names))


if __name__ == "__main__":
    unittest.main()
