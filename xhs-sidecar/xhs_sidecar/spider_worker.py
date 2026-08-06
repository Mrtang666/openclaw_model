from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .normalizer import normalize_note
from .security import redact


def main() -> int:
    arguments = _arguments()
    try:
        request = json.loads(arguments.input.read_text(encoding="utf-8"))
        if request.get("operation") == "resolve_link":
            result = resolve_link(
                spider_root=arguments.spider_root,
                note_id=str(request["noteId"]),
                query=str(request["query"]),
                limit=int(request["limit"]),
            )
        else:
            result = collect(
                spider_root=arguments.spider_root,
                query=str(request["query"]),
                limit=int(request["limit"]),
                cursor=str(request.get("cursor", "")),
                collect_comments=arguments.collect_comments,
                comment_limit=arguments.comment_limit,
                detail_max_attempts=arguments.detail_max_attempts,
                detail_retry_delay_ms=arguments.detail_retry_delay_ms,
            )
    except Exception as exception:  # Worker boundary: return a normalized failure to the parent.
        result = _result(
            "FAILED", False, [], "WORKER_FAILED", redact(exception), ""
        )
    arguments.output.write_text(
        json.dumps(result, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    return 0


def collect(
    spider_root: Path,
    query: str,
    limit: int,
    cursor: str,
    collect_comments: bool,
    comment_limit: int,
    detail_max_attempts: int = 3,
    detail_retry_delay_ms: int = 800,
) -> dict[str, Any]:
    cookies = (os.getenv("XHS_COOKIES") or os.getenv("COOKIES") or "").strip()
    if not cookies:
        return _result(
            "FAILED",
            False,
            [],
            "AUTH_MISSING",
            "未配置小红书授权 Cookie，请设置 XHS_COOKIES 后重启 Sidecar",
            "",
        )
    offset = _cursor_offset(cursor)
    spider_path = str(spider_root.resolve())
    if spider_path not in sys.path:
        sys.path.insert(0, spider_path)
    _quiet_third_party_logging()

    try:
        api = _bootstrap_with_retry(cookies)
    except Exception as exception:
        message = redact(exception)
        if _is_auth_expired(message):
            return _result(
                "FAILED",
                False,
                [],
                "AUTH_EXPIRED",
                "小红书授权 Cookie 已失效，请重新登录后更新 XHS_COOKIES 并重启 Sidecar",
                "",
            )
        return _result("FAILED", False, [], "BOOTSTRAP_FAILED", message, "")
    success, message, search_items = api.search_some_note(query, offset + limit)
    if not success:
        _persist_api_cookie(api)
        return _result("FAILED", False, [], "SEARCH_FAILED", redact(message), "")

    candidates = [
        item
        for item in search_items or []
        if isinstance(item, dict) and item.get("model_type") == "note"
    ][offset : offset + limit]
    records: list[dict[str, Any]] = []
    failures: list[str] = []
    author_hash_key = os.getenv("XHS_AUTHOR_HASH_KEY", "openclaw-local-only")
    for candidate in candidates:
        note_id = str(candidate.get("id", "")).strip()
        token = str(candidate.get("xsec_token", "")).strip()
        if not note_id:
            failures.append("search result missing note id")
            continue
        source_url = f"https://www.xiaohongshu.com/explore/{note_id}"
        request_url = source_url
        if token:
            request_url += f"?xsec_token={token}&xsec_source=pc_search"
        detail_success, detail_message, detail = _get_note_with_retry(
            api, request_url, detail_max_attempts, detail_retry_delay_ms
        )
        items = _detail_items(detail)
        if not detail_success or not items:
            failures.append(f"{note_id}: {redact(detail_message, 300)}")
            continue
        comments: list[dict[str, Any]] = []
        if collect_comments and comment_limit > 0:
            comment_success, comment_message, raw_comments = api.get_note_all_comment(request_url)
            if comment_success and isinstance(raw_comments, list):
                comments = raw_comments[:comment_limit]
        records.append(normalize_note(items[0], source_url, comments, author_hash_key, request_url))

    if failures and records:
        _persist_api_cookie(api)
        return _result("PARTIAL", False, records, "PARTIAL_COLLECTION", "; ".join(failures), "")
    if failures and not records:
        _persist_api_cookie(api)
        return _result("FAILED", False, [], "DETAIL_COLLECTION_FAILED", "; ".join(failures), "")
    _persist_api_cookie(api)
    return _result("SUCCEEDED", True, records, "", "", "")


def _get_note_with_retry(
    api: Any, request_url: str, max_attempts: int, retry_delay_ms: int
) -> tuple[bool, str, Any]:
    success = False
    message = ""
    detail: Any = None
    attempts = max(1, min(max_attempts, 5))
    for attempt in range(attempts):
        try:
            success, message, detail = api.get_note_info(request_url)
        except Exception as exception:
            success, message, detail = False, redact(exception, 300), None
        if success and _detail_items(detail):
            return True, message, detail
        if attempt + 1 < attempts and retry_delay_ms > 0:
            time.sleep(min(retry_delay_ms, 10000) / 1000)
    return success, message, detail


def resolve_link(spider_root: Path, note_id: str, query: str, limit: int) -> dict[str, Any]:
    cookies = (os.getenv("XHS_COOKIES") or os.getenv("COOKIES") or "").strip()
    if not cookies:
        return _link_result("FAILED", "", "AUTH_MISSING", "XHS_COOKIES is not configured")
    spider_path = str(spider_root.resolve())
    if spider_path not in sys.path:
        sys.path.insert(0, spider_path)
    _quiet_third_party_logging()
    try:
        api = _bootstrap_api(cookies)
    except Exception as exception:
        message = redact(exception)
        code = "AUTH_EXPIRED" if _is_auth_expired(message) else "BOOTSTRAP_FAILED"
        return _link_result("FAILED", "", code, message)
    success, message, search_items = api.search_some_note(query, limit)
    if not success:
        _persist_api_cookie(api)
        return _link_result("FAILED", "", "SEARCH_FAILED", redact(message))
    for candidate in search_items or []:
        if not isinstance(candidate, dict) or candidate.get("model_type") != "note":
            continue
        if str(candidate.get("id", "")).strip() != note_id:
            continue
        token = str(candidate.get("xsec_token", "")).strip()
        if not token:
            break
        access_url = (
            f"https://www.xiaohongshu.com/explore/{note_id}"
            f"?xsec_token={token}&xsec_source=pc_search"
        )
        from .security import access_xhs_url

        _persist_api_cookie(api)
        return _link_result("FOUND", access_xhs_url(access_url, note_id), "", "")
    _persist_api_cookie(api)
    return _link_result("NOT_FOUND", "", "LINK_NOT_FOUND", "note was not present in refreshed search results")


def _bootstrap_api(cookies: str) -> Any:
    from apis.xhs_pc_apis import XHS_Apis
    from xhs_utils.xhs_pc import XHSPcAuth

    auth = XHSPcAuth.from_cookie(cookies)
    return XHS_Apis(auth).bootstrap()


def _bootstrap_with_retry(cookies: str) -> Any:
    """Retry transient bootstrap failures such as DNS/connect timeouts."""
    attempts = _bounded_int_env("XHS_BOOTSTRAP_MAX_ATTEMPTS", 3, 1, 5)
    delay_seconds = _bounded_int_env("XHS_BOOTSTRAP_RETRY_DELAY_SECONDS", 1, 0, 10)
    last_error: Exception | None = None
    for attempt in range(attempts):
        try:
            return _bootstrap_api(cookies)
        except Exception as exception:
            last_error = exception
            message = redact(exception, 500)
            if _is_auth_expired(message) or attempt + 1 >= attempts:
                raise
            if delay_seconds:
                time.sleep(delay_seconds)
    raise RuntimeError("bootstrap failed") from last_error


def _bounded_int_env(name: str, default: int, minimum: int, maximum: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError:
        return default
    return max(minimum, min(value, maximum))


def _is_auth_expired(message: str) -> bool:
    normalized = message.casefold()
    indicators = (
        "登录已过期",
        "登录过期",
        "登录失效",
        "请先登录",
        "请重新登录",
        "未登录",
        "not logged in",
        "login expired",
        "session expired",
        "cookie expired",
        "invalid cookie",
        "unauthorized",
    )
    return any(indicator in normalized for indicator in indicators)


def _persist_api_cookie(api: Any) -> None:
    try:
        from .authorization import persist_worker_cookie
        cookie = str(getattr(getattr(api, "auth", None), "cookies", ""))
        persist_worker_cookie(cookie)
    except Exception:
        # Collection results must not fail because local session persistence failed.
        return


def _detail_items(value: object) -> list[dict[str, Any]]:
    if not isinstance(value, dict):
        return []
    data = value.get("data")
    if not isinstance(data, dict) or not isinstance(data.get("items"), list):
        return []
    return [item for item in data["items"] if isinstance(item, dict)]


def _cursor_offset(value: str) -> int:
    if not value:
        return 0
    try:
        offset = int(value)
    except ValueError as exception:
        raise ValueError("cursor must be a non-negative integer") from exception
    if offset < 0 or offset > 10_000:
        raise ValueError("cursor must be between 0 and 10000")
    return offset


def _result(
    status: str,
    complete: bool,
    records: list[dict[str, Any]],
    error_code: str,
    error_message: str,
    next_cursor: str,
) -> dict[str, Any]:
    return {
        "status": status,
        "complete": complete,
        "nextCursor": next_cursor,
        "records": records,
        "errorCode": error_code,
        "errorMessage": redact(error_message),
        "collectedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }


def _link_result(status: str, access_url: str, error_code: str, error_message: str) -> dict[str, Any]:
    return {
        "status": status,
        "accessUrl": access_url,
        "errorCode": error_code,
        "errorMessage": redact(error_message),
    }


def _quiet_third_party_logging() -> None:
    try:
        from loguru import logger

        logger.remove()
    except (ImportError, ValueError):
        pass


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--spider-root", type=Path, required=True)
    parser.add_argument("--collect-comments", action="store_true")
    parser.add_argument("--comment-limit", type=int, default=100)
    parser.add_argument("--detail-max-attempts", type=int, default=3)
    parser.add_argument("--detail-retry-delay-ms", type=int, default=800)
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(main())
