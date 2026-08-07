from __future__ import annotations

import hashlib
import hmac
from typing import Any, Iterable

from .security import access_xhs_url, canonical_xhs_url


def normalize_note(
    item: dict[str, Any],
    source_url: str,
    comments: Iterable[dict[str, Any]],
    author_hash_key: str,
    access_url: str = "",
) -> dict[str, Any]:
    card = _mapping(item.get("note_card"))
    note_id = _text(item.get("id") or card.get("note_id") or card.get("id"))
    user = _mapping(card.get("user"))
    interaction = _mapping(card.get("interact_info"))
    return {
        "note_id": note_id,
        "note_url": canonical_xhs_url(source_url, note_id),
        "access_url": access_xhs_url(access_url, note_id),
        "authorId": _author_key(user.get("user_id"), author_hash_key),
        "title": _text(card.get("title")),
        "desc": _text(card.get("desc")),
        "note_type": _text(card.get("type")),
        "tags": [
            _text(tag.get("name"))
            for tag in _mappings(card.get("tag_list"))
            if _text(tag.get("name"))
        ],
        "upload_time": card.get("time") or card.get("last_update_time"),
        "liked_count": interaction.get("liked_count", 0),
        "collected_count": interaction.get("collected_count", 0),
        "comment_count": interaction.get("comment_count", 0),
        "share_count": interaction.get("share_count", 0),
        "images": normalize_images(card.get("image_list") or item.get("image_list")),
        "comments": normalize_comments(comments, note_id, author_hash_key),
    }


def normalize_images(images: object) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    seen: set[str] = set()
    for image in _mappings(images):
        url = _text(
            image.get("url_default")
            or image.get("url_pre")
            or image.get("url")
        )
        if not url:
            for variant in _mappings(image.get("info_list")):
                url = _text(
                    variant.get("url")
                    or variant.get("url_default")
                    or variant.get("url_pre")
                )
                if url:
                    break
        if url and url not in seen:
            seen.add(url)
            result.append({"url": url})
    return result


def normalize_comments(
    comments: Iterable[dict[str, Any]],
    note_id: str,
    author_hash_key: str,
) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for comment in comments:
        if not isinstance(comment, dict):
            continue
        _append_comment(result, comment, note_id, "", author_hash_key)
    return result


def _append_comment(
    result: list[dict[str, Any]],
    comment: dict[str, Any],
    note_id: str,
    parent_id: str,
    author_hash_key: str,
) -> None:
    comment_id = _text(comment.get("id") or comment.get("comment_id"))
    user = _mapping(comment.get("user_info") or comment.get("user"))
    result.append(
        {
            "note_id": note_id,
            "comment_id": comment_id,
            "parent_comment_id": parent_id,
            "authorId": _author_key(user.get("user_id"), author_hash_key),
            "content": _text(comment.get("content")),
            "like_count": comment.get("like_count", 0),
            "create_time": comment.get("create_time"),
        }
    )
    for child in _mappings(comment.get("sub_comments") or comment.get("comments")):
        _append_comment(result, child, note_id, comment_id, author_hash_key)


def _author_key(value: object, secret: str) -> str:
    text = _text(value)
    if not text:
        return ""
    return hmac.new(secret.encode("utf-8"), text.encode("utf-8"), hashlib.sha256).hexdigest()


def _mapping(value: object) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _mappings(value: object) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


def _text(value: object) -> str:
    return "" if value is None else str(value).strip()
