from __future__ import annotations

import json
import os
import subprocess
import tempfile
import threading
from pathlib import Path
from typing import Any, Protocol

from .authorization import AuthorizationManager
from .config import SidecarConfig
from .models import LinkResolveRequest, SearchRequest
from .security import redact


class CollectionRunner(Protocol):
    def run(self, request: SearchRequest) -> dict[str, Any]: ...

    def resolve_link(self, request: LinkResolveRequest) -> dict[str, Any]: ...


class SubprocessSpiderRunner:
    def __init__(self, config: SidecarConfig, authorization: AuthorizationManager | None = None):
        self._config = config
        self._authorization = authorization
        self._module_root = Path(__file__).resolve().parents[1]
        self._lock = threading.Lock()
        self._processes: set[subprocess.Popen[str]] = set()

    def run(self, request: SearchRequest) -> dict[str, Any]:
        return self._invoke(
            {"operation": "search", "query": request.query, "limit": request.limit, "cursor": request.cursor},
            include_comments=True,
        )

    def resolve_link(self, request: LinkResolveRequest) -> dict[str, Any]:
        return self._invoke(
            {"operation": "resolve_link", "noteId": request.note_id, "query": request.query, "limit": request.limit},
            include_comments=False,
        )

    def _invoke(self, payload: dict[str, Any], include_comments: bool) -> dict[str, Any]:
        with tempfile.TemporaryDirectory(prefix="openclaw-xhs-") as temporary:
            root = Path(temporary)
            input_path = root / "request.json"
            output_path = root / "result.json"
            input_path.write_text(
                json.dumps(payload, ensure_ascii=False),
                encoding="utf-8",
            )
            command = [
                self._config.spider_python,
                "-m",
                "xhs_sidecar.spider_worker",
                "--input",
                str(input_path),
                "--output",
                str(output_path),
                "--spider-root",
                str(self._config.spider_root),
                "--comment-limit",
                str(self._config.comment_limit),
                "--detail-max-attempts",
                str(self._config.detail_max_attempts),
                "--detail-retry-delay-ms",
                str(self._config.detail_retry_delay_ms),
            ]
            if include_comments and self._config.collect_comments:
                command.append("--collect-comments")
            try:
                process = subprocess.Popen(
                    command,
                    cwd=self._module_root,
                    env=self._worker_environment(),
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                )
                with self._lock:
                    self._processes.add(process)
                try:
                    stdout, stderr = process.communicate(timeout=self._config.worker_timeout_seconds)
                except subprocess.TimeoutExpired as exception:
                    process.kill()
                    process.communicate()
                    raise RuntimeError("Spider_XHS worker timed out") from exception
                finally:
                    with self._lock:
                        self._processes.discard(process)
            except OSError as exception:
                raise RuntimeError(f"unable to start Spider_XHS worker: {redact(exception)}") from exception
            if process.returncode != 0 or not output_path.is_file():
                detail = redact(stderr or stdout or "worker returned no result")
                raise RuntimeError(f"Spider_XHS worker failed: {detail}")
            value = json.loads(output_path.read_text(encoding="utf-8"))
            if not isinstance(value, dict):
                raise RuntimeError("Spider_XHS worker result must be a JSON object")
            if self._authorization is not None:
                self._authorization.record_result(value)
            return value

    def close(self) -> None:
        with self._lock:
            processes = list(self._processes)
        for process in processes:
            if process.poll() is None:
                process.terminate()

    def _worker_environment(self) -> dict[str, str]:
        allowed = {
            "PATH",
            "PATHEXT",
            "SYSTEMROOT",
            "WINDIR",
            "COMSPEC",
            "TEMP",
            "TMP",
            "USERPROFILE",
            "APPDATA",
            "LOCALAPPDATA",
            "HOME",
            "LANG",
            "HTTP_PROXY",
            "HTTPS_PROXY",
            "NO_PROXY",
            "XHS_COOKIES",
            "COOKIES",
            "XHS_AUTHOR_HASH_KEY",
        }
        environment = {key: value for key, value in os.environ.items() if key.upper() in allowed}
        if self._authorization is not None:
            environment["XHS_COOKIES"] = self._authorization.cookie_for_worker()
            environment.pop("COOKIES", None)
            environment["XHS_AUTH_STATE_FILE"] = str(self._config.auth_state_file)
            environment["XHS_AUTH_ENCRYPTION_KEY"] = self._config.auth_encryption_key
        environment["XHS_AUTHOR_HASH_KEY"] = self._config.author_hash_key
        existing_python_path = os.getenv("PYTHONPATH", "").strip()
        environment["PYTHONPATH"] = str(self._module_root) + (
            os.pathsep + existing_python_path if existing_python_path else ""
        )
        return environment
