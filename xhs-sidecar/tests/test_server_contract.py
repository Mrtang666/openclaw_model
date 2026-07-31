import json
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from xhs_sidecar.jobs import JobManager
from xhs_sidecar.models import SearchRequest
from xhs_sidecar.server import create_server
from xhs_sidecar.store import FileJobStore


class FakeRunner:
    def run(self, request: SearchRequest) -> dict:
        return {
            "status": "SUCCEEDED",
            "complete": True,
            "nextCursor": "",
            "records": [{"note_id": "note-1", "title": request.query}],
            "errorCode": "",
            "errorMessage": "",
            "collectedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }

    def resolve_link(self, request) -> dict:
        return {
            "status": "FOUND",
            "accessUrl": f"https://www.xiaohongshu.com/explore/{request.note_id}?xsec_token=test&xsec_source=pc_search",
            "errorCode": "",
            "errorMessage": "",
        }


class FakeAuthorization:
    def __init__(self) -> None:
        self.cleared = False

    def status(self) -> dict:
        return {"status": "VALID", "collectAllowed": True, "accountNickname": "测试账号"}

    def cookie_for_worker(self) -> str:
        return "a1=test; web_session=test"

    def update_cookie(self, cookie: str) -> dict:
        if "web_session=" not in cookie:
            raise ValueError("Cookie 缺少必要字段：web_session")
        return self.status()

    def validate(self) -> dict:
        return self.status()

    def start_qr(self) -> dict:
        return {"sessionId": "qr-1", "status": "SCAN_REQUIRED", "qrImage": "data:image/svg+xml;base64,test"}

    def poll_qr(self, session_id: str) -> dict:
        return {"sessionId": session_id, "status": "CONFIRM_REQUIRED", "message": "请确认登录"}

    def clear(self) -> None:
        self.cleared = True

class ServerContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        store = FileJobStore(Path(self.temporary.name))
        self.manager = JobManager(store, FakeRunner(), worker_threads=1, max_queued_jobs=2)
        self.runner = FakeRunner()
        self.authorization = FakeAuthorization()
        self.server = create_server(
            "127.0.0.1", 0, self.manager, "test-api-key", self.runner, self.authorization
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.manager.close()
        self.thread.join(timeout=2)
        self.temporary.cleanup()

    def test_submit_and_poll_match_java_contract(self) -> None:
        submission = self._request(
            "/internal/v1/jobs/search",
            method="POST",
            body={"query": "品牌 A", "limit": 20},
        )
        self.assertEqual(submission["status"], "SUBMITTED")
        self.assertTrue(submission["jobId"])

        deadline = time.monotonic() + 2
        result = {}
        while time.monotonic() < deadline:
            result = self._request(f"/internal/v1/jobs/{submission['jobId']}")
            if result["status"] in {"SUCCEEDED", "PARTIAL", "FAILED"}:
                break
            time.sleep(0.01)

        self.assertEqual(result["status"], "SUCCEEDED")
        self.assertTrue(result["complete"])
        self.assertEqual(result["records"][0]["note_id"], "note-1")
        self.assertIn("collectedAt", result)

    def test_rejects_missing_api_key(self) -> None:
        request = urllib.request.Request(
            self.base_url + "/internal/v1/jobs/search",
            data=b'{"query":"brand","limit":20}',
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with self.assertRaises(urllib.error.HTTPError) as context:
            urllib.request.urlopen(request, timeout=2)
        self.assertEqual(context.exception.code, 401)

    def test_health_does_not_require_api_key(self) -> None:
        with urllib.request.urlopen(self.base_url + "/health", timeout=2) as response:
            self.assertEqual(json.load(response), {"status": "UP"})

    def test_resolves_link_without_collecting_note_detail(self) -> None:
        result = self._request(
            "/internal/v1/links/resolve",
            method="POST",
            body={"noteId": "note-1", "query": "brand", "limit": 100},
        )
        self.assertEqual(result["status"], "FOUND")
        self.assertIn("xsec_token=test", result["accessUrl"])

    def test_exposes_sanitized_authorization_management_contract(self) -> None:
        status = self._request("/internal/v1/auth/status")
        qr = self._request("/internal/v1/auth/qr", method="POST")
        polled = self._request("/internal/v1/auth/qr/qr-1")

        self.assertEqual(status["status"], "VALID")
        self.assertNotIn("cookie", json.dumps(status).lower())
        self.assertEqual(qr["status"], "SCAN_REQUIRED")
        self.assertEqual(polled["status"], "CONFIRM_REQUIRED")

    def _request(self, path: str, method: str = "GET", body: dict | None = None) -> dict:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers={
                "Content-Type": "application/json",
                "X-Collector-Api-Key": "test-api-key",
            },
            method=method,
        )
        with urllib.request.urlopen(request, timeout=2) as response:
            return json.load(response)


if __name__ == "__main__":
    unittest.main()
