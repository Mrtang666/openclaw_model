import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from xhs_sidecar.spider_worker import collect


class SpiderWorkerAuthenticationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.spider_root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _collect(self) -> dict:
        return collect(self.spider_root, "brand", 20, "", False, 0)

    def test_missing_cookie_returns_auth_missing(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            result = self._collect()

        self.assertEqual(result["status"], "FAILED")
        self.assertEqual(result["errorCode"], "AUTH_MISSING")
        self.assertIn("XHS_COOKIES", result["errorMessage"])

    def test_expired_login_returns_actionable_auth_error(self) -> None:
        with (
            patch.dict(os.environ, {"XHS_COOKIES": "test-cookie"}, clear=True),
            patch(
                "xhs_sidecar.spider_worker._bootstrap_api",
                side_effect=RuntimeError("bootstrap user/me failed: 登录已过期"),
            ),
        ):
            result = self._collect()

        self.assertEqual(result["status"], "FAILED")
        self.assertEqual(result["errorCode"], "AUTH_EXPIRED")
        self.assertIn("重新登录", result["errorMessage"])
        self.assertIn("XHS_COOKIES", result["errorMessage"])

    def test_unrelated_bootstrap_error_is_not_reported_as_auth_expired(self) -> None:
        with (
            patch.dict(os.environ, {"XHS_COOKIES": "test-cookie"}, clear=True),
            patch(
                "xhs_sidecar.spider_worker._bootstrap_api",
                side_effect=RuntimeError("upstream response format changed"),
            ),
        ):
            result = self._collect()

        self.assertEqual(result["status"], "FAILED")
        self.assertEqual(result["errorCode"], "BOOTSTRAP_FAILED")
        self.assertIn("response format changed", result["errorMessage"])


class SpiderWorkerCollectionReliabilityTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.spider_root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_retries_transient_detail_failure(self) -> None:
        api = _FakeApi(detail_failures=1)
        with (
            patch.dict(os.environ, {"XHS_COOKIES": "test-cookie"}, clear=True),
            patch("xhs_sidecar.spider_worker._bootstrap_api", return_value=api),
            patch("xhs_sidecar.spider_worker.normalize_note", return_value={"sourcePostId": "note-1"}),
        ):
            result = collect(self.spider_root, "brand", 1, "", False, 0, 3, 0)

        self.assertEqual(result["status"], "SUCCEEDED")
        self.assertEqual(api.detail_calls, 2)
        self.assertEqual(len(result["records"]), 1)

    def test_comment_failure_does_not_downgrade_note_collection(self) -> None:
        api = _FakeApi(comment_success=False)
        with (
            patch.dict(os.environ, {"XHS_COOKIES": "test-cookie"}, clear=True),
            patch("xhs_sidecar.spider_worker._bootstrap_api", return_value=api),
            patch("xhs_sidecar.spider_worker.normalize_note", return_value={"sourcePostId": "note-1"}),
        ):
            result = collect(self.spider_root, "brand", 1, "", True, 10, 1, 0)

        self.assertEqual(result["status"], "SUCCEEDED")
        self.assertTrue(result["complete"])


class _FakeApi:
    def __init__(self, detail_failures: int = 0, comment_success: bool = True) -> None:
        self.detail_failures = detail_failures
        self.comment_success = comment_success
        self.detail_calls = 0

    def search_some_note(self, query: str, limit: int):
        return True, "", [{"model_type": "note", "id": "note-1", "xsec_token": "token"}]

    def get_note_info(self, request_url: str):
        self.detail_calls += 1
        if self.detail_calls <= self.detail_failures:
            return False, "temporary failure", None
        return True, "", {"data": {"items": [{"id": "note-1"}]}}

    def get_note_all_comment(self, request_url: str):
        return self.comment_success, "comment unavailable", []


if __name__ == "__main__":
    unittest.main()
