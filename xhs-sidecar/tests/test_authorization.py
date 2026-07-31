import tempfile
import unittest
from os import environ
from pathlib import Path
from unittest.mock import patch

from xhs_sidecar.authorization import (
    AuthorizationManager,
    AuthorizationUnavailableError,
    EncryptedAuthorizationStore,
    persist_worker_cookie,
)


COOKIE = "a1=test-a1; web_session=test-session; gid=test-gid"


class AuthorizationStoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.state_file = self.root / "session.enc"
        self.store = EncryptedAuthorizationStore(self.state_file, "test-encryption-key-at-least-32-characters")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_imports_environment_cookie_without_writing_plaintext(self) -> None:
        manager = AuthorizationManager(
            self.store, self.root, initial_cookie=COOKIE,
            validator=lambda _root, _cookie: {"nickname": "测试账号", "redId": "red-1"},
        )

        self.assertEqual(manager.status()["status"], "CONFIGURED")
        self.assertEqual(manager.cookie_for_worker(), COOKIE)
        self.assertNotIn("test-session", self.state_file.read_text(encoding="utf-8"))

    def test_manual_update_validates_and_records_account(self) -> None:
        manager = AuthorizationManager(
            self.store, self.root,
            validator=lambda _root, _cookie: {"nickname": "测试账号", "redId": "red-1"},
        )

        status = manager.update_cookie(COOKIE)

        self.assertEqual(status["status"], "VALID")
        self.assertEqual(status["accountNickname"], "测试账号")
        self.assertEqual(status["source"], "MANUAL")

    def test_rejects_incomplete_cookie(self) -> None:
        manager = AuthorizationManager(self.store, self.root)

        with self.assertRaisesRegex(ValueError, "web_session"):
            manager.update_cookie("a1=only-a1")

    def test_opens_circuit_after_consecutive_auth_failures(self) -> None:
        manager = AuthorizationManager(self.store, self.root, failure_threshold=2, initial_cookie=COOKIE)

        manager.record_result({"status": "FAILED", "errorCode": "AUTH_EXPIRED", "errorMessage": "expired"})
        self.assertTrue(manager.status()["collectAllowed"])
        manager.record_result({"status": "FAILED", "errorCode": "AUTH_EXPIRED", "errorMessage": "expired"})

        self.assertEqual(manager.status()["status"], "EXPIRED")
        with self.assertRaises(AuthorizationUnavailableError):
            manager.cookie_for_worker()

    def test_qr_login_persists_verified_cookie(self) -> None:
        client = _FakeQrClient()
        manager = AuthorizationManager(
            self.store, self.root,
            qr_client_factory=lambda _root: client,
        )

        started = manager.start_qr()
        completed = manager.poll_qr(started["sessionId"])

        self.assertEqual(started["status"], "SCAN_REQUIRED")
        self.assertTrue(started["qrImage"].startswith("data:image/svg+xml;base64,"))
        self.assertEqual(completed["status"], "AUTHORIZED")
        self.assertEqual(manager.status()["accountNickname"], "扫码账号")
        self.assertEqual(manager.cookie_for_worker(), COOKIE)
        self.assertTrue(client.closed)

    def test_worker_persists_rotated_cookie_without_overwriting_new_login(self) -> None:
        manager = AuthorizationManager(
            self.store, self.root, initial_cookie=COOKIE,
            validator=lambda _root, _cookie: {"nickname": "测试账号", "redId": "red-1"},
        )
        rotated = "a1=test-a1; web_session=rotated-session"
        environment = {
            "XHS_AUTH_STATE_FILE": str(self.state_file),
            "XHS_AUTH_ENCRYPTION_KEY": "test-encryption-key-at-least-32-characters",
            "XHS_COOKIES": COOKIE,
        }
        with patch.dict(environ, environment, clear=True):
            persist_worker_cookie(rotated)
        self.assertEqual(manager.cookie_for_worker(), rotated)

        manager.update_cookie(COOKIE)
        with patch.dict(environ, {**environment, "XHS_COOKIES": rotated}, clear=True):
            persist_worker_cookie("a1=test-a1; web_session=stale-worker")
        self.assertEqual(manager.cookie_for_worker(), COOKIE)


class _FakeQrClient:
    def __init__(self) -> None:
        self.status_calls = 0
        self.closed = False

    def generate_init_cookies(self):
        return {"a1": "test-a1"}

    def generate_qrcode(self, cookies):
        return True, "成功", {
            "cookies": cookies, "qr_id": "qr-1", "code": "code-1",
            "qr_url": "xhsdiscover://login/test",
        }

    def check_qrcode_status(self, qr_id, code, cookies):
        self.status_calls += 1
        if self.status_calls == 1:
            return False, "请扫描二维码", cookies
        return True, "验证成功", {"a1": "test-a1", "web_session": "test-session", "gid": "test-gid"}

    def ensure_webprofile(self, cookies):
        return "gid"

    def get_user_info(self, cookies):
        return True, {"guest": False, "nickname": "扫码账号", "red_id": "red-qr"}, cookies

    def cookies_to_str(self, cookies):
        return "; ".join(f"{key}={value}" for key, value in cookies.items())

    def close(self):
        self.closed = True


if __name__ == "__main__":
    unittest.main()
