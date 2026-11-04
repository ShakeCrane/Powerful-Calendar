"""Versioned Studio view of the implemented Android Web Skin Host contract."""
from __future__ import annotations

import datetime as _datetime
import json
import re
import uuid
from pathlib import Path


CATALOG_PATH = Path(__file__).with_name("host_binding_catalog_v1.json")


def load_catalog() -> dict:
    return json.loads(CATALOG_PATH.read_text(encoding="utf-8"))


def _type_matches(value, expected: str) -> bool:
    if expected == "string":
        return type(value) is str
    if expected == "integer":
        return type(value) is int
    if expected == "number":
        return type(value) in (int, float) and type(value) is not bool
    if expected == "boolean":
        return type(value) is bool
    if expected == "array":
        return type(value) is list
    if expected == "object":
        return type(value) is dict
    return False


def _validate_value(value, schema: dict, path: str) -> None:
    expected = schema.get("type")
    if expected and not _type_matches(value, expected):
        raise ValueError(f"{path} must be {expected}")
    if "enum" in schema and value not in schema["enum"]:
        raise ValueError(f"{path} is not a catalogued value")
    if expected in ("integer", "number"):
        if "minimum" in schema and value < schema["minimum"]:
            raise ValueError(f"{path} is below its minimum")
        if "maximum" in schema and value > schema["maximum"]:
            raise ValueError(f"{path} is above its maximum")
        if schema.get("not") == 0 and value == 0:
            raise ValueError(f"{path} must not be zero")
    if expected == "string":
        if len(value) < schema.get("minLength", 0):
            raise ValueError(f"{path} is too short")
        if len(value) > schema.get("maxLength", 2**31):
            raise ValueError(f"{path} is too long")
        if schema.get("forbidden") == "ISO control characters" and any(
                ord(char) < 32 or ord(char) == 127 for char in value):
            raise ValueError(f"{path} contains a control character")
        pattern = schema.get("pattern")
        if pattern and re.fullmatch(pattern, value) is None:
            raise ValueError(f"{path} has an invalid format")
        if schema.get("format") == "date":
            try:
                parsed = _datetime.date.fromisoformat(value)
            except ValueError as exc:
                raise ValueError(f"{path} must be an ISO date") from exc
            if parsed.isoformat() != value:
                raise ValueError(f"{path} must be a canonical ISO date")
            if parsed.year < schema.get("minimumYear", 1) or parsed.year > schema.get("maximumYear", 9999):
                raise ValueError(f"{path} year is outside the Host range")
        if schema.get("format") == "uuid-canonical":
            try:
                if str(uuid.UUID(value)) != value:
                    raise ValueError
            except ValueError as exc:
                raise ValueError(f"{path} must be a canonical UUID") from exc
        if "minimumYear" in schema and schema.get("pattern", "").startswith("^"):
            year = int(value[:4]) if len(value) >= 4 and value[:4].isdigit() else -1
            if not schema["minimumYear"] <= year <= schema["maximumYear"]:
                raise ValueError(f"{path} year is outside the Host range")
    if expected == "array":
        if len(value) > schema.get("maxItems", 2**31):
            raise ValueError(f"{path} has too many items")
        if schema.get("uniqueItems") and len(value) != len({json.dumps(item, sort_keys=True) for item in value}):
            raise ValueError(f"{path} contains duplicate items")
        item_schema = schema.get("items", {})
        for index, item in enumerate(value):
            _validate_value(item, item_schema, f"{path}[{index}]")


def validate_action_parameters(name: str, parameters: dict) -> None:
    actions = {item["name"]: item for item in load_catalog()["actions"]}
    action = actions.get(name)
    if action is None:
        raise ValueError(f"unknown Host action: {name}")
    schema = action["parameterSchema"]
    if type(parameters) is not dict:
        raise ValueError("Host action parameters must be an object")
    properties = schema["properties"]
    if any(key not in properties for key in parameters):
        raise ValueError(f"{name} contains an unknown parameter")
    missing = set(schema["required"]) - set(parameters)
    if missing:
        raise ValueError(f"{name} is missing required parameter(s): {', '.join(sorted(missing))}")
    for key, value in parameters.items():
        _validate_value(value, properties[key], f"{name}.{key}")
    if name == "resolveAiProposal":
        decision, indices = parameters["decision"], parameters["indices"]
        if (decision == "supplement") != (len(indices) == 0):
            raise ValueError("resolveAiProposal requires [] only for supplement and indices otherwise")


def validate_project_bindings(project: dict) -> None:
    catalog = load_catalog()
    actions = {item["name"] for item in catalog["actions"]}
    state_fields = {item["path"] for item in catalog["stateFrame"]["fields"]
                    if item.get("bindable") is True}

    def visit(nodes):
        for node in nodes:
            props = node.get("props", {})
            binding = props.get("hostAction")
            if binding is not None:
                if node.get("type") not in ("button", "date-button"):
                    raise ValueError("Host action bindings are supported only on button components")
                if type(binding) is not dict or set(binding) != {"version", "name", "parameters"}:
                    raise ValueError("hostAction must contain exactly version, name, and parameters")
                if binding["version"] != catalog["bridgeVersion"] or type(binding["version"]) is not int:
                    raise ValueError("hostAction bridge version is unsupported")
                if binding["name"] not in actions:
                    raise ValueError(f"unknown Host action: {binding['name']}")
                validate_action_parameters(binding["name"], binding["parameters"])
            state_binding = props.get("hostStateBinding")
            if state_binding is not None and state_binding not in state_fields:
                raise ValueError(f"unknown or non-bindable Host state path: {state_binding}")
            visit(node.get("children", []))

    for page in project.get("pages", []):
        visit(page.get("components", []))


def binding_warnings(project: dict, capabilities) -> list[str]:
    """Report missing declarations without ever adding capabilities to a package."""
    capabilities = set(capabilities or [])
    needs_read = False
    needs_action = False

    def visit(nodes):
        nonlocal needs_read, needs_action
        for node in nodes:
            props = node.get("props", {})
            needs_read |= "hostStateBinding" in props
            needs_action |= "hostAction" in props
            visit(node.get("children", []))

    for page in project.get("pages", []):
        visit(page.get("components", []))
    warnings = []
    if needs_read and "host.read" not in capabilities:
        warnings.append("This project binds Host state but does not request host.read; bound values will be empty.")
    if needs_action and "host.action" not in capabilities:
        warnings.append("This project binds Host actions but does not request host.action; Android Host will ignore those requests.")
    return warnings
