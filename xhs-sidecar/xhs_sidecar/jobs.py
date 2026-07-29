from __future__ import annotations

import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from typing import Any

from .models import CollectionJob, SearchRequest, utc_now
from .runner import CollectionRunner
from .security import redact
from .store import FileJobStore


class JobManager:
    def __init__(
        self,
        store: FileJobStore,
        runner: CollectionRunner,
        worker_threads: int,
        max_queued_jobs: int = 20,
    ):
        self._store = store
        self._runner = runner
        self._executor = ThreadPoolExecutor(
            max_workers=worker_threads, thread_name_prefix="xhs-sidecar-worker"
        )
        self._closed = threading.Event()
        self._capacity = threading.BoundedSemaphore(worker_threads + max_queued_jobs)

    def submit(self, request: SearchRequest) -> CollectionJob:
        if self._closed.is_set():
            raise RuntimeError("sidecar is shutting down")
        self._store.cleanup_expired()
        if not self._capacity.acquire(blocking=False):
            raise JobCapacityError("sidecar job queue is full")
        job = CollectionJob(job_id=str(uuid.uuid4()), request=request, status="SUBMITTED")
        try:
            self._store.save(job)
            self._executor.submit(self._execute, job.job_id)
        except Exception:
            self._capacity.release()
            raise
        return job

    def get(self, job_id: str) -> CollectionJob | None:
        return self._store.get(job_id)

    def close(self) -> None:
        self._closed.set()
        close_runner = getattr(self._runner, "close", None)
        if callable(close_runner):
            close_runner()
        self._executor.shutdown(wait=False, cancel_futures=True)

    def _execute(self, job_id: str) -> None:
        try:
            job = self._store.get(job_id)
            if job is None:
                return
            job.status = "RUNNING"
            self._store.save(job)
            try:
                result = self._runner.run(job.request)
                self._apply_result(job, result)
            except Exception as exception:  # Preserve a terminal response for Java polling.
                job.status = "FAILED"
                job.complete = False
                job.error_code = "RUNNER_FAILED"
                job.error_message = redact(exception)
                job.collected_at = utc_now()
            self._store.save(job)
        finally:
            self._capacity.release()

    def _apply_result(self, job: CollectionJob, result: dict[str, Any]) -> None:
        status = str(result.get("status", "FAILED")).strip().upper()
        if status not in {"SUCCEEDED", "PARTIAL", "FAILED"}:
            raise ValueError(f"worker returned unsupported terminal status: {status}")
        records = result.get("records", [])
        if not isinstance(records, list) or not all(isinstance(item, dict) for item in records):
            raise ValueError("worker records must be an array of objects")
        job.status = status
        job.complete = bool(result.get("complete", status == "SUCCEEDED"))
        job.next_cursor = str(result.get("nextCursor", "")).strip()[:128]
        job.records = records[: job.request.limit]
        job.error_code = str(result.get("errorCode", "")).strip()[:64]
        job.error_message = redact(result.get("errorMessage", ""))
        job.collected_at = str(result.get("collectedAt", "")).strip() or utc_now()


class JobCapacityError(RuntimeError):
    pass
