"""Exact-file, same-origin-resource preview for editable v3 skin packages.

The document is sandboxed in the browser; the editor owns the mock host port.
No source files are written to disk. All exposed URLs are opaque, short-lived
capabilities on the already loopback-only Skin Studio HTTP server.
"""
from __future__ import annotations
import mimetypes
import re
import secrets
import threading
import time

from runtime_package_v3 import build_files, unpack

_lock = threading.RLock()
_sessions = {}
MAX_SESSIONS = 6
TTL_SECONDS = 1800
_ROUTE = re.compile(r"^/preview/([a-f0-9]{32})/(web|assets)/([A-Za-z0-9][A-Za-z0-9_./-]{0,119})$")


def publish(manifest, files):
    # Do not serve what would fail at export/import: the preview contract IS the pack.
    packed = build_files(manifest, files)
    _, checked_files = unpack(packed)
    now = time.monotonic()
    token = secrets.token_hex(16)
    with _lock:
        for key, (_, expiry) in list(_sessions.items()):
            if expiry < now:
                del _sessions[key]
        while len(_sessions) >= MAX_SESSIONS:
            del _sessions[next(iter(_sessions))]
        _sessions[token] = (checked_files, now + TTL_SECONDS)
    return f"/preview/{token}/web/index.html"


def resolve(path):
    match = _ROUTE.fullmatch(path)
    if not match:
        return None
    token, folder, relative = match.groups()
    # Do not normalize traversal: paths must already be canonical.
    if ".." in relative.split("/"):
        return None
    with _lock:
        slot = _sessions.get(token)
        if slot is None or slot[1] < time.monotonic():
            return None
        raw = slot[0].get(folder + "/" + relative)
    if raw is None:
        return None
    extension = relative.rsplit(".", 1)[-1].lower()
    mime = {
        "html": "text/html; charset=utf-8",
        "css": "text/css; charset=utf-8",
        "js": "text/javascript; charset=utf-8",
        "json": "application/json; charset=utf-8",
        "svg": "image/svg+xml",
        "woff2": "font/woff2",
        "woff": "font/woff",
        "ttf": "font/ttf",
        "png": "image/png",
        "webp": "image/webp",
    }.get(extension, "application/octet-stream")
    return mime, raw
