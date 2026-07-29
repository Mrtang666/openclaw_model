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


if __name__ == "__main__":
    unittest.main()
