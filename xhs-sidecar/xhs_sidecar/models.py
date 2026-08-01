from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any


TERMINAL_STATUSES = {"SUCCEEDED", "PARTIAL", "FAILED"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


@dataclass
class SearchRequest:
    query: str
    limit: int
    cursor: str = ""

    @classmethod
    def parse(cls, value: Any) -> "SearchRequest":
        if not isinstance(value, dict):
            raise ValueError("request body must be a JSON object")
        query = str(value.get("query", "")).strip()
        cursor = str(value.get("cursor", "")).strip()
        if not query:
            raise ValueError("query is required")
        if len(query) > 200:
            raise ValueError("query must not exceed 200 characters")
        if len(cursor) > 128:
            raise ValueError("cursor must not exceed 128 characters")
        try:
            limit = int(value.get("limit", 20))
        except (TypeError, ValueError) as exception:
            raise ValueError("limit must be an integer") from exception
        if limit < 1 or limit > 100:
            raise ValueError("limit must be between 1 and 100")
        return cls(query=query, limit=limit, cursor=cursor)


@dataclass
class LinkResolveRequest:
    note_id: str
    query: str
    limit: int = 100

    @classmethod
    def parse(cls, value: Any) -> "LinkResolveRequest":
        if not isinstance(value, dict):
            raise ValueError("request body must be a JSON object")
        note_id = str(value.get("noteId", value.get("note_id", ""))).strip()
        query = str(value.get("query", "")).strip()
        if not note_id or len(note_id) > 191:
            raise ValueError("noteId is required and must not exceed 191 characters")
        if not query or len(query) > 200:
            raise ValueError("query is required and must not exceed 200 characters")
        try:
            limit = int(value.get("limit", 100))
        except (TypeError, ValueError) as exception:
            raise ValueError("limit must be an integer") from exception
        if limit < 1 or limit > 100:
            raise ValueError("limit must be between 1 and 100")
        return cls(note_id=note_id, query=query, limit=limit)


@dataclass
class CollectionJob:
    job_id: str
    request: SearchRequest
    status: str = "PENDING"
    complete: bool = False
    next_cursor: str = ""
    records: list[dict[str, Any]] = field(default_factory=list)
    error_code: str = ""
    error_message: str = ""
    collected_at: str = field(default_factory=utc_now)
    created_at: str = field(default_factory=utc_now)
    updated_at: str = field(default_factory=utc_now)

    def to_storage(self) -> dict[str, Any]:
        value = asdict(self)
        value["request"] = asdict(self.request)
        return value

    @classmethod
    def from_storage(cls, value: dict[str, Any]) -> "CollectionJob":
        data = dict(value)
        data["request"] = SearchRequest(**data["request"])
        return cls(**data)

    def to_response(self) -> dict[str, Any]:
        return {
            "jobId": self.job_id,
            "status": self.status,
            "complete": self.complete,
            "nextCursor": self.next_cursor,
            "records": self.records,
            "errorCode": self.error_code,
            "errorMessage": self.error_message,
            "collectedAt": self.collected_at,
        }
