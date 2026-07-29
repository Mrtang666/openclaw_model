from __future__ import annotations

import argparse
import hmac
import json
import logging
import signal
import threading
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import unquote, urlsplit

from .config import SidecarConfig
from .jobs import JobCapacityError, JobManager
from .models import LinkResolveRequest, SearchRequest
from .runner import CollectionRunner, SubprocessSpiderRunner
from .store import FileJobStore


MAX_REQUEST_BYTES = 16 * 1024
JOB_PATH_PREFIX = "/internal/v1/jobs/"


def create_server(
    host: str,
    port: int,
    manager: JobManager,
    api_key: str = "",
    link_resolver: CollectionRunner | None = None,
) -> ThreadingHTTPServer:
    handler = _handler(manager, api_key, link_resolver)
    server = ThreadingHTTPServer((host, port), handler)
    server.daemon_threads = True
    return server


def _handler(
    manager: JobManager,
    api_key: str,
    link_resolver: CollectionRunner | None,
) -> type[BaseHTTPRequestHandler]:
    class SidecarHandler(BaseHTTPRequestHandler):
        server_version = "OpenClawXhsSidecar/0.1"
        sys_version = ""

        def do_GET(self) -> None:
            path = urlsplit(self.path).path
            if path == "/health":
                self._json(HTTPStatus.OK, {"status": "UP"})
                return
            if not self._authorized(api_key):
                return
            if not path.startswith(JOB_PATH_PREFIX):
                self._json(HTTPStatus.NOT_FOUND, _error("NOT_FOUND", "endpoint not found"))
                return
            job_id = unquote(path[len(JOB_PATH_PREFIX) :]).strip()
            job = manager.get(job_id)
            if job is None:
                self._json(HTTPStatus.NOT_FOUND, _error("JOB_NOT_FOUND", "collection job not found"))
                return
            self._json(HTTPStatus.OK, job.to_response())

        def do_POST(self) -> None:
            path = urlsplit(self.path).path
            if not self._authorized(api_key):
                return
            if path == "/internal/v1/links/resolve":
                if link_resolver is None:
                    self._json(HTTPStatus.SERVICE_UNAVAILABLE, _error("UNAVAILABLE", "link resolver is unavailable"))
                    return
                try:
                    request = LinkResolveRequest.parse(self._request_json())
                    self._json(HTTPStatus.OK, link_resolver.resolve_link(request))
                except (ValueError, json.JSONDecodeError) as exception:
                    self._json(HTTPStatus.BAD_REQUEST, _error("INVALID_REQUEST", str(exception)))
                except RuntimeError as exception:
                    self._json(HTTPStatus.SERVICE_UNAVAILABLE, _error("UNAVAILABLE", str(exception)))
                return
            if path != "/internal/v1/jobs/search":
                self._json(HTTPStatus.NOT_FOUND, _error("NOT_FOUND", "endpoint not found"))
                return
            try:
                request = SearchRequest.parse(self._request_json())
                job = manager.submit(request)
            except JobCapacityError as exception:
                self._json(HTTPStatus.SERVICE_UNAVAILABLE, _error("QUEUE_FULL", str(exception)))
                return
            except (ValueError, json.JSONDecodeError) as exception:
                self._json(HTTPStatus.BAD_REQUEST, _error("INVALID_REQUEST", str(exception)))
                return
            except RuntimeError as exception:
                self._json(HTTPStatus.SERVICE_UNAVAILABLE, _error("UNAVAILABLE", str(exception)))
                return
            self._json(HTTPStatus.ACCEPTED, {"jobId": job.job_id, "status": "SUBMITTED"})

        def _request_json(self) -> Any:
            try:
                content_length = int(self.headers.get("Content-Length", "0"))
            except ValueError as exception:
                raise ValueError("invalid Content-Length") from exception
            if content_length <= 0 or content_length > MAX_REQUEST_BYTES:
                raise ValueError(f"request body must be between 1 and {MAX_REQUEST_BYTES} bytes")
            content_type = self.headers.get_content_type()
            if content_type != "application/json":
                raise ValueError("Content-Type must be application/json")
            return json.loads(self.rfile.read(content_length).decode("utf-8"))

        def _authorized(self, expected: str) -> bool:
            if not expected:
                return True
            supplied = self.headers.get("X-Collector-Api-Key", "")
            if hmac.compare_digest(supplied, expected):
                return True
            self._json(HTTPStatus.UNAUTHORIZED, _error("UNAUTHORIZED", "invalid collector API key"))
            return False

        def _json(self, status: HTTPStatus, value: dict[str, Any]) -> None:
            payload = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            self.send_response(status.value)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, format_string: str, *arguments: object) -> None:
            logging.getLogger("xhs_sidecar.http").info(
                "%s - %s", self.address_string(), format_string % arguments
            )

    return SidecarHandler


def _error(code: str, message: str) -> dict[str, str]:
    return {"errorCode": code, "errorMessage": message}


def main() -> int:
    parser = argparse.ArgumentParser(description="OpenClaw Spider_XHS HTTP sidecar")
    parser.add_argument("--check", action="store_true", help="validate configuration without starting HTTP")
    arguments = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    config = SidecarConfig.from_env()
    config.validate()
    if arguments.check:
        logging.info("configuration is valid; Spider_XHS root=%s", config.spider_root)
        return 0

    store = FileJobStore(config.data_dir, config.job_retention_hours)
    runner = SubprocessSpiderRunner(config)
    manager = JobManager(
        store,
        runner,
        config.worker_threads,
        config.max_queued_jobs,
    )
    server = create_server(config.host, config.port, manager, config.api_key, runner)
    stop = threading.Event()

    def request_shutdown(*_: object) -> None:
        if not stop.is_set():
            stop.set()
            threading.Thread(target=server.shutdown, daemon=True).start()

    for signal_name in ("SIGINT", "SIGTERM"):
        if hasattr(signal, signal_name):
            signal.signal(getattr(signal, signal_name), request_shutdown)
    logging.info("XHS sidecar listening on http://%s:%s", config.host, config.port)
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        server.server_close()
        manager.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
