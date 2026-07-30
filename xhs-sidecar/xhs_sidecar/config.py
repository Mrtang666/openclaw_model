from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SidecarConfig:
    host: str
    port: int
    api_key: str
    data_dir: Path
    spider_root: Path
    spider_python: str
    worker_timeout_seconds: int
    worker_threads: int
    max_queued_jobs: int
    job_retention_hours: int
    author_hash_key: str
    collect_comments: bool
    comment_limit: int
    detail_max_attempts: int
    detail_retry_delay_ms: int

    @classmethod
    def from_env(cls) -> "SidecarConfig":
        module_root = Path(__file__).resolve().parents[1]
        spider_root_value = os.getenv("SPIDER_XHS_ROOT", "").strip()
        if not spider_root_value:
            raise ValueError("SPIDER_XHS_ROOT must point to an extracted Spider_XHS directory")
        return cls(
            host=os.getenv("XHS_SIDECAR_HOST", "127.0.0.1").strip() or "127.0.0.1",
            port=_integer("XHS_SIDECAR_PORT", 18081, 1, 65535),
            api_key=os.getenv("XHS_COLLECTOR_API_KEY", "").strip(),
            data_dir=Path(os.getenv("XHS_SIDECAR_DATA_DIR", str(module_root / "runtime" / "jobs"))).resolve(),
            spider_root=Path(spider_root_value).resolve(),
            spider_python=os.getenv("SPIDER_XHS_PYTHON", os.sys.executable).strip() or os.sys.executable,
            worker_timeout_seconds=_integer("XHS_SIDECAR_WORKER_TIMEOUT_SECONDS", 300, 10, 3600),
            worker_threads=_integer("XHS_SIDECAR_WORKER_THREADS", 1, 1, 8),
            max_queued_jobs=_integer("XHS_SIDECAR_MAX_QUEUED_JOBS", 20, 1, 1000),
            job_retention_hours=_integer("XHS_SIDECAR_JOB_RETENTION_HOURS", 168, 1, 8760),
            author_hash_key=os.getenv("XHS_AUTHOR_HASH_KEY", "openclaw-local-only"),
            collect_comments=_boolean("XHS_COLLECT_COMMENTS", True),
            comment_limit=_integer("XHS_COMMENT_LIMIT", 100, 0, 1000),
            detail_max_attempts=_integer("XHS_DETAIL_MAX_ATTEMPTS", 3, 1, 5),
            detail_retry_delay_ms=_integer("XHS_DETAIL_RETRY_DELAY_MS", 800, 0, 10000),
        )

    def validate(self) -> None:
        if self.host not in {"127.0.0.1", "::1", "localhost"} and len(self.api_key) < 16:
            raise ValueError("XHS_COLLECTOR_API_KEY must contain at least 16 characters for non-loopback binding")
        if self.host not in {"127.0.0.1", "::1", "localhost"} and len(self.author_hash_key) < 16:
            raise ValueError("XHS_AUTHOR_HASH_KEY must contain at least 16 characters for non-loopback binding")
        if not self.spider_root.is_dir():
            raise ValueError(f"SPIDER_XHS_ROOT does not exist: {self.spider_root}")
        if not (self.spider_root / "apis" / "xhs_pc_apis.py").is_file():
            raise ValueError(f"SPIDER_XHS_ROOT is not a Spider_XHS checkout: {self.spider_root}")
        self.data_dir.mkdir(parents=True, exist_ok=True)


def _integer(name: str, default: int, minimum: int, maximum: int) -> int:
    raw = os.getenv(name, "").strip()
    try:
        value = default if not raw else int(raw)
    except ValueError as exception:
        raise ValueError(f"{name} must be an integer") from exception
    if value < minimum or value > maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")
    return value


def _boolean(name: str, default: bool) -> bool:
    raw = os.getenv(name, "").strip().lower()
    if not raw:
        return default
    if raw in {"1", "true", "yes", "on"}:
        return True
    if raw in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"{name} must be true or false")
