from __future__ import annotations

import re
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit


_TOKEN_PATTERN = re.compile(
    r"(?i)(xsec_token|web_session|id_token|a1|cookie)(\s*[:=]|%3D)[^&;\s]+"
)


def redact(value: object, maximum: int = 1000) -> str:
    text = "" if value is None else str(value)
    text = _TOKEN_PATTERN.sub(r"\1\2[REDACTED]", text)
    return text[:maximum]


def canonical_xhs_url(value: object, note_id: str = "") -> str:
    text = "" if value is None else str(value).strip()
    if not text and note_id:
        return f"https://www.xiaohongshu.com/explore/{note_id}"
    try:
        parsed = urlsplit(text)
    except ValueError:
        return redact(text, 2000)
    if parsed.hostname and parsed.hostname.lower().endswith("xiaohongshu.com"):
        return urlunsplit((parsed.scheme or "https", parsed.netloc, parsed.path, "", ""))
    return redact(text, 2000)


def access_xhs_url(value: object, note_id: str) -> str:
    text = "" if value is None else str(value).strip()
    if not text or not note_id:
        return ""
    try:
        parsed = urlsplit(text)
    except ValueError:
        return ""
    hostname = (parsed.hostname or "").lower()
    expected_path = f"/explore/{note_id}"
    if parsed.scheme.lower() != "https" or not (
        hostname == "xiaohongshu.com" or hostname.endswith(".xiaohongshu.com")
    ):
        return ""
    if parsed.path.rstrip("/") != expected_path:
        return ""
    allowed = {
        key: value
        for key, value in parse_qsl(parsed.query, keep_blank_values=False)
        if key in {"xsec_token", "xsec_source"} and value
    }
    if "xsec_token" not in allowed:
        return ""
    query = urlencode(allowed)
    return urlunsplit(("https", hostname, expected_path, query, ""))
