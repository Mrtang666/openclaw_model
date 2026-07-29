from __future__ import annotations

import json
import os
import threading
from datetime import datetime, timedelta, timezone
from pathlib import Path

from .models import CollectionJob, utc_now


class FileJobStore:
    def __init__(self, root: Path, retention_hours: int = 168):
        self._root = root
        self._retention = timedelta(hours=max(1, retention_hours))
        self._root.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._recover_interrupted_jobs()
        self.cleanup_expired()

    def save(self, job: CollectionJob) -> None:
        with self._lock:
            job.updated_at = utc_now()
            target = self._path(job.job_id)
            temporary = target.with_suffix(".tmp")
            temporary.write_text(
                json.dumps(job.to_storage(), ensure_ascii=False, separators=(",", ":")),
                encoding="utf-8",
            )
            os.replace(temporary, target)

    def get(self, job_id: str) -> CollectionJob | None:
        if not _valid_job_id(job_id):
            return None
        with self._lock:
            path = self._path(job_id)
            if not path.is_file():
                return None
            try:
                return CollectionJob.from_storage(json.loads(path.read_text(encoding="utf-8")))
            except (OSError, ValueError, TypeError, KeyError):
                return None

    def cleanup_expired(self) -> int:
        removed = 0
        cutoff = datetime.now(timezone.utc) - self._retention
        with self._lock:
            for path in self._root.glob("*.json"):
                try:
                    value = json.loads(path.read_text(encoding="utf-8"))
                    status = str(value.get("status", ""))
                    updated = _instant(value.get("updated_at"))
                    if status in {"SUCCEEDED", "PARTIAL", "FAILED"} and updated < cutoff:
                        path.unlink()
                        removed += 1
                except (OSError, ValueError, TypeError):
                    continue
        return removed

    def _recover_interrupted_jobs(self) -> None:
        for path in self._root.glob("*.json"):
            try:
                job = CollectionJob.from_storage(json.loads(path.read_text(encoding="utf-8")))
            except (OSError, ValueError, TypeError, KeyError):
                continue
            if job.status in {"PENDING", "SUBMITTED", "RUNNING"}:
                job.status = "FAILED"
                job.complete = False
                job.error_code = "SIDECAR_RESTARTED"
                job.error_message = "sidecar restarted before the collection job completed"
                self.save(job)

    def _path(self, job_id: str) -> Path:
        return self._root / f"{job_id}.json"


def _valid_job_id(value: str) -> bool:
    return bool(value) and len(value) <= 64 and all(character.isalnum() or character == "-" for character in value)


def _instant(value: object) -> datetime:
    text = "" if value is None else str(value).strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    parsed = datetime.fromisoformat(text)
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
