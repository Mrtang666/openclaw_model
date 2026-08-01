from __future__ import annotations

import base64
import hashlib
import hmac
import io
import json
import os
import sys
import threading
import time
import uuid
from dataclasses import asdict, dataclass, fields
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from .security import redact


_ASSOCIATED_DATA = b"openclaw-xhs-authorization-v1"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


@dataclass
class AuthorizationRecord:
    cookie: str
    status: str
    source: str
    account_nickname: str = ""
    account_red_id: str = ""
    updated_at: str = ""
    last_verified_at: str = ""
    last_error: str = ""
    consecutive_auth_failures: int = 0

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "AuthorizationRecord":
        allowed = {field.name for field in fields(cls)}
        return cls(**{key: value[key] for key in allowed if key in value})


class EncryptedAuthorizationStore:
    def __init__(self, path: Path, secret: str):
        if len(secret) < 16:
            raise ValueError("authorization encryption key must contain at least 16 characters")
        self._path = path
        master = hashlib.sha256(secret.encode("utf-8")).digest()
        self._encryption_key = hmac.new(master, b"encryption", hashlib.sha256).digest()
        self._authentication_key = hmac.new(master, b"authentication", hashlib.sha256).digest()
        self._lock = threading.RLock()
        self._path.parent.mkdir(parents=True, exist_ok=True)

    def load(self) -> AuthorizationRecord | None:
        with self._lock:
            if not self._path.is_file():
                return None
            envelope = json.loads(self._path.read_text(encoding="utf-8"))
            if envelope.get("version") != 1:
                raise ValueError("unsupported authorization state version")
            nonce = base64.b64decode(envelope["nonce"], validate=True)
            ciphertext = base64.b64decode(envelope["ciphertext"], validate=True)
            supplied_tag = base64.b64decode(envelope["tag"], validate=True)
            expected_tag = hmac.new(
                self._authentication_key,
                _ASSOCIATED_DATA + nonce + ciphertext,
                hashlib.sha256,
            ).digest()
            if not hmac.compare_digest(supplied_tag, expected_tag):
                raise ValueError("authorization state integrity check failed")
            payload = _xor(ciphertext, _key_stream(self._encryption_key, nonce, len(ciphertext)))
            return AuthorizationRecord.from_dict(json.loads(payload.decode("utf-8")))

    def save(self, record: AuthorizationRecord) -> None:
        payload = json.dumps(asdict(record), ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        nonce = os.urandom(32)
        ciphertext = _xor(payload, _key_stream(self._encryption_key, nonce, len(payload)))
        envelope = {
            "version": 1,
            "algorithm": "HMAC-SHA256-STREAM-ETM",
            "nonce": base64.b64encode(nonce).decode("ascii"),
            "ciphertext": base64.b64encode(ciphertext).decode("ascii"),
            "tag": base64.b64encode(hmac.new(
                self._authentication_key,
                _ASSOCIATED_DATA + nonce + ciphertext,
                hashlib.sha256,
            ).digest()).decode("ascii"),
        }
        with self._lock:
            temporary = self._path.with_suffix(self._path.suffix + ".tmp")
            temporary.write_text(json.dumps(envelope, separators=(",", ":")), encoding="utf-8")
            try:
                os.chmod(temporary, 0o600)
            except OSError:
                pass
            os.replace(temporary, self._path)

    def clear(self) -> None:
        with self._lock:
            if self._path.exists():
                self._path.unlink()


class AuthorizationUnavailableError(RuntimeError):
    pass


@dataclass
class _QrSession:
    session_id: str
    client: Any
    cookies: dict[str, Any]
    qr_id: str
    code: str
    qr_url: str
    expires_at_epoch: float


class AuthorizationManager:
    def __init__(
        self,
        store: EncryptedAuthorizationStore,
        spider_root: Path,
        failure_threshold: int = 2,
        qr_ttl_seconds: int = 180,
        initial_cookie: str = "",
        validator: Callable[[Path, str], dict[str, str]] | None = None,
        qr_client_factory: Callable[[Path], Any] | None = None,
    ):
        self._store = store
        self._spider_root = spider_root
        self._failure_threshold = max(1, failure_threshold)
        self._qr_ttl_seconds = max(60, qr_ttl_seconds)
        self._validator = validator or _validate_cookie
        self._qr_client_factory = qr_client_factory or _qr_client
        self._lock = threading.RLock()
        self._qr_sessions: dict[str, _QrSession] = {}
        if self._store.load() is None and initial_cookie.strip():
            _require_complete_cookie(initial_cookie)
            self._store.save(AuthorizationRecord(
                cookie=initial_cookie.strip(), status="CONFIGURED", source="ENV",
                updated_at=_now(),
            ))

    def status(self) -> dict[str, Any]:
        with self._lock:
            record = self._store.load()
            if record is None:
                return {
                    "status": "MISSING", "collectAllowed": False,
                    "requiresReauthorization": True, "source": "",
                    "accountNickname": "", "accountRedId": "", "updatedAt": "",
                    "lastVerifiedAt": "", "lastError": "", "consecutiveAuthFailures": 0,
                }
            return {
                "status": record.status,
                "collectAllowed": record.status != "EXPIRED",
                "requiresReauthorization": record.status == "EXPIRED",
                "source": record.source,
                "accountNickname": record.account_nickname,
                "accountRedId": record.account_red_id,
                "updatedAt": record.updated_at,
                "lastVerifiedAt": record.last_verified_at,
                "lastError": record.last_error,
                "consecutiveAuthFailures": record.consecutive_auth_failures,
            }

    def cookie_for_worker(self) -> str:
        with self._lock:
            record = self._store.load()
            if record is None:
                raise AuthorizationUnavailableError("尚未配置小红书账号授权")
            if record.status == "EXPIRED":
                raise AuthorizationUnavailableError("小红书账号授权已失效，请重新扫码授权")
            return record.cookie

    def update_cookie(self, cookie: str) -> dict[str, Any]:
        value = cookie.strip()
        _require_complete_cookie(value)
        account = self._validator(self._spider_root, value)
        now = _now()
        with self._lock:
            self._store.save(AuthorizationRecord(
                cookie=value, status="VALID", source="MANUAL",
                account_nickname=account.get("nickname", ""),
                account_red_id=account.get("redId", ""),
                updated_at=now, last_verified_at=now,
            ))
        return self.status()

    def validate(self) -> dict[str, Any]:
        with self._lock:
            record = self._store.load()
            if record is None:
                raise AuthorizationUnavailableError("尚未配置小红书账号授权")
            cookie = record.cookie
        try:
            account = self._validator(self._spider_root, cookie)
        except Exception as exception:
            self._record_auth_failure(redact(exception, 500))
            raise
        now = _now()
        with self._lock:
            record = self._store.load() or record
            record.status = "VALID"
            record.account_nickname = account.get("nickname", record.account_nickname)
            record.account_red_id = account.get("redId", record.account_red_id)
            record.last_verified_at = now
            record.last_error = ""
            record.consecutive_auth_failures = 0
            self._store.save(record)
        return self.status()

    def record_result(self, result: dict[str, Any]) -> None:
        code = str(result.get("errorCode", "")).strip().upper()
        if code == "AUTH_EXPIRED":
            self._record_auth_failure(str(result.get("errorMessage", "")))
        elif str(result.get("status", "")).strip().upper() in {"SUCCEEDED", "PARTIAL"}:
            with self._lock:
                record = self._store.load()
                if record is not None:
                    record.status = "VALID"
                    record.last_verified_at = _now()
                    record.last_error = ""
                    record.consecutive_auth_failures = 0
                    self._store.save(record)

    def clear(self) -> None:
        with self._lock:
            for session in self._qr_sessions.values():
                _close(session.client)
            self._qr_sessions.clear()
            self._store.clear()

    def start_qr(self) -> dict[str, Any]:
        client = self._qr_client_factory(self._spider_root)
        try:
            cookies = client.generate_init_cookies()
            success, message, qr_data = client.generate_qrcode(cookies)
            if not success or not qr_data:
                raise RuntimeError(message or "获取小红书登录二维码失败")
            cookies = qr_data["cookies"]
            success, message, cookies = client.check_qrcode_status(
                qr_data["qr_id"], qr_data["code"], cookies
            )
            if success or message != "请扫描二维码":
                raise RuntimeError(message or "二维码预检状态异常")
            client.ensure_webprofile(cookies)
            session_id = str(uuid.uuid4())
            expires_at = time.time() + self._qr_ttl_seconds
            session = _QrSession(
                session_id, client, cookies, str(qr_data["qr_id"]),
                str(qr_data["code"]), str(qr_data["qr_url"]), expires_at,
            )
            with self._lock:
                for old in self._qr_sessions.values():
                    _close(old.client)
                self._qr_sessions = {session_id: session}
            return {
                "sessionId": session_id, "status": "SCAN_REQUIRED",
                "message": "请使用小红书 App 扫描二维码",
                "qrImage": _qr_svg_data_url(session.qr_url),
                "expiresAt": datetime.fromtimestamp(expires_at, timezone.utc).isoformat().replace("+00:00", "Z"),
            }
        except Exception:
            _close(client)
            raise

    def poll_qr(self, session_id: str) -> dict[str, Any]:
        with self._lock:
            session = self._qr_sessions.get(session_id)
        if session is None:
            raise ValueError("二维码授权会话不存在或已经结束")
        if time.time() >= session.expires_at_epoch:
            self._finish_qr(session_id)
            return {"sessionId": session_id, "status": "EXPIRED", "message": "二维码已过期"}
        try:
            success, message, cookies = session.client.check_qrcode_status(
                session.qr_id, session.code, session.cookies
            )
            session.cookies = cookies
            if not success:
                status = "CONFIRM_REQUIRED" if message == "请确认登录" else "SCAN_REQUIRED"
                if message == "二维码已过期":
                    status = "EXPIRED"
                    self._finish_qr(session_id)
                return {"sessionId": session_id, "status": status, "message": message}
            verified, user_info, cookies = session.client.get_user_info(session.cookies)
            if not verified or user_info.get("guest") is not False:
                raise RuntimeError("扫码完成，但正式登录会话验证失败")
            cookie = session.client.cookies_to_str(cookies)
            _require_complete_cookie(cookie)
            now = _now()
            with self._lock:
                self._store.save(AuthorizationRecord(
                    cookie=cookie, status="VALID", source="QR",
                    account_nickname=str(user_info.get("nickname", "")),
                    account_red_id=str(user_info.get("red_id", "")),
                    updated_at=now, last_verified_at=now,
                ))
            self._finish_qr(session_id)
            return {
                "sessionId": session_id, "status": "AUTHORIZED",
                "message": "小红书账号授权成功", "authorization": self.status(),
            }
        except Exception:
            self._finish_qr(session_id)
            raise

    def _record_auth_failure(self, message: str) -> None:
        with self._lock:
            record = self._store.load()
            if record is None:
                return
            record.consecutive_auth_failures += 1
            record.last_error = redact(message, 500)
            if record.consecutive_auth_failures >= self._failure_threshold:
                record.status = "EXPIRED"
            self._store.save(record)

    def _finish_qr(self, session_id: str) -> None:
        with self._lock:
            session = self._qr_sessions.pop(session_id, None)
        if session is not None:
            _close(session.client)


def persist_worker_cookie(cookie: str) -> None:
    state_file = os.getenv("XHS_AUTH_STATE_FILE", "").strip()
    secret = os.getenv("XHS_AUTH_ENCRYPTION_KEY", "")
    if not state_file or len(secret) < 16 or not cookie.strip():
        return
    store = EncryptedAuthorizationStore(Path(state_file), secret)
    record = store.load()
    worker_cookie = (os.getenv("XHS_COOKIES") or os.getenv("COOKIES") or "").strip()
    if record is None or record.cookie == cookie.strip():
        return
    if worker_cookie and record.cookie != worker_cookie:
        # A newer QR/manual authorization won while this worker was running.
        return
    record.cookie = cookie.strip()
    record.updated_at = _now()
    store.save(record)


def _require_complete_cookie(cookie: str) -> None:
    fields = {
        part.split("=", 1)[0].strip(): part.split("=", 1)[1].strip()
        for part in cookie.split(";") if "=" in part
    }
    missing = [name for name in ("a1", "web_session") if not fields.get(name)]
    if missing:
        raise ValueError("Cookie 缺少必要字段：" + "、".join(missing))


def _validate_cookie(spider_root: Path, cookie: str) -> dict[str, str]:
    _add_spider_path(spider_root)
    from apis.xhs_pc_apis import XHS_Apis
    from xhs_utils.xhs_pc import XHSPcAuth

    auth = XHSPcAuth.from_cookie(cookie)
    try:
        success, message, response = XHS_Apis(auth).get_user_me()
        data = (response or {}).get("data") or {}
        if not success or data.get("guest") is not False:
            raise RuntimeError(message or "小红书登录会话验证失败")
        return {
            "nickname": str(data.get("nickname", "")),
            "redId": str(data.get("red_id", "")),
        }
    finally:
        auth.close()


def _qr_client(spider_root: Path) -> Any:
    _add_spider_path(spider_root)
    from apis.xhs_pc_login_apis import XHSLoginApi
    return XHSLoginApi()


def _add_spider_path(spider_root: Path) -> None:
    value = str(spider_root.resolve())
    if value not in sys.path:
        sys.path.insert(0, value)


def _qr_svg_data_url(value: str) -> str:
    import qrcode
    from qrcode.image.svg import SvgPathImage

    qr = qrcode.QRCode(box_size=8, border=3)
    qr.add_data(value)
    qr.make(fit=True)
    output = io.BytesIO()
    qr.make_image(image_factory=SvgPathImage).save(output)
    return "data:image/svg+xml;base64," + base64.b64encode(output.getvalue()).decode("ascii")


def _close(value: Any) -> None:
    close = getattr(value, "close", None)
    if callable(close):
        close()


def _key_stream(key: bytes, nonce: bytes, length: int) -> bytes:
    output = bytearray()
    counter = 0
    while len(output) < length:
        output.extend(hmac.new(
            key, nonce + counter.to_bytes(8, "big"), hashlib.sha256
        ).digest())
        counter += 1
    return bytes(output[:length])


def _xor(left: bytes, right: bytes) -> bytes:
    return bytes(a ^ b for a, b in zip(left, right))
