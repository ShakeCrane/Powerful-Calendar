"""Exact-file, same-origin-resource preview for editable v3 skin packages.

The document is sandboxed in the browser; the editor owns the mock host port.
No source files are written to disk. All exposed URLs are opaque, short-lived
capabilities on the already loopback-only Skin Studio HTTP server.
"""
from __future__ import annotations
import re
import secrets
import threading
import time
from urllib.parse import unquote, urlsplit

from runtime_package_v3 import build_files, unpack

_lock = threading.RLock()
_sessions = {}
MAX_SESSIONS = 6
TTL_SECONDS = 1800
_ROUTE = re.compile(r"^/preview/([a-f0-9]{32})/(web|assets)/([A-Za-z0-9][A-Za-z0-9_./-]{0,119})$")
_ROOT_RESOURCE = re.compile(r"^/(?:web|assets)/[A-Za-z0-9][A-Za-z0-9_./-]{0,119}$|^/project\.json$")
_SESSION_HOST = re.compile(r"^([a-f0-9]{32})\.localhost:(\d+)$", re.IGNORECASE)
PREVIEW_CSP = (
    "default-src 'self'; img-src 'self' data:; "
    "style-src 'self' 'unsafe-inline'; script-src 'self'; "
    "connect-src 'none'; frame-src 'none'; form-action 'none'; "
    "object-src 'none'; base-uri 'none'"
)


def _decode_url_path(path):
    """Decode URL path escapes once, matching Android Uri.path before package lookup."""
    try:
        return unquote(path, errors="strict")
    except UnicodeDecodeError:
        return None


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


def _resolve_file(token, package_path):
    if ".." in package_path.split("/") or "." in package_path.split("/"):
        return None
    with _lock:
        slot = _sessions.get(token)
        if slot is None or slot[1] < time.monotonic():
            return None
        raw = slot[0].get(package_path)
    if raw is None:
        return None
    extension = package_path.rsplit(".", 1)[-1].lower()
    mime = {
        "html": "text/html; charset=utf-8",
        "css": "text/css; charset=utf-8",
        "js": "application/javascript; charset=utf-8",
        "json": "application/json; charset=utf-8",
        "svg": "image/svg+xml",
        "woff2": "font/woff2",
        "woff": "font/woff",
        "ttf": "font/ttf",
        "png": "image/png",
        "webp": "image/webp",
    }.get(extension, "application/octet-stream")
    return mime, raw


def resolve(path):
    path = _decode_url_path(path)
    if path is None:
        return None
    match = _ROUTE.fullmatch(path)
    if not match:
        return None
    token, folder, relative = match.groups()
    # Do not normalize traversal: paths must already be canonical.
    return _resolve_file(token, folder + "/" + relative)


def resolve_root_relative(path, referer, request_host):
    """Resolve legacy package-root URLs using the active preview document's Referer.

    The current browser preview uses a tokenized ``*.localhost`` host, which handles
    even requests without Referer. This fallback remains for older same-host preview
    callers and never normalizes or rewrites package paths.
    """
    path = _decode_url_path(path)
    if path is None:
        return None
    if path == "/":
        package_path = "web/index.html"
    elif _ROOT_RESOURCE.fullmatch(path):
        package_path = path.removeprefix("/")
    else:
        return None
    if ".." in package_path.split("/") or "." in package_path.split("/"):
        return None
    if not referer or not request_host:
        return None
    source = urlsplit(referer)
    if (source.scheme != "http" or source.netloc.lower() != request_host.lower()
            or source.query or source.fragment):
        return None
    preview_path = _decode_url_path(source.path)
    preview = _ROUTE.fullmatch(preview_path or "")
    if not preview:
        return None
    token = preview.group(1)
    return _resolve_file(token, package_path)


def preview_token_from_host(host, port):
    match = _SESSION_HOST.fullmatch((host or "").lower())
    if not match or int(match.group(2)) != int(port):
        return None
    return match.group(1).lower()


def resolve_for_session(token, path):
    if not re.fullmatch(r"[a-f0-9]{32}", token or ""):
        return None
    path = _decode_url_path(path)
    if path is None:
        return None
    if path == "/":
        package_path = "web/index.html"
    elif _ROOT_RESOURCE.fullmatch(path):
        package_path = path.removeprefix("/")
    else:
        return None
    if ".." in package_path.split("/") or "." in package_path.split("/"):
        return None
    return _resolve_file(token, package_path)


def browser_url(path, port):
    match = _ROUTE.fullmatch(path)
    if not match or match.group(2) != "web" or match.group(3) != "index.html":
        raise ValueError("Preview entrypoint is not a canonical v3 HTML route")
    token = match.group(1)
    return f"http://{token}.localhost:{int(port)}{path}"
