import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from xhs_sidecar.models import CollectionJob, SearchRequest
from xhs_sidecar.store import FileJobStore


class FileJobStoreTests(unittest.TestCase):
    def test_recovers_interrupted_job_as_failed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            job = CollectionJob(
                job_id="job-1", request=SearchRequest("品牌", 20), status="RUNNING"
            )
            (root / "job-1.json").write_text(
                json.dumps(job.to_storage(), ensure_ascii=False), encoding="utf-8"
            )

            recovered = FileJobStore(root).get("job-1")

            self.assertIsNotNone(recovered)
            self.assertEqual(recovered.status, "FAILED")
            self.assertEqual(recovered.error_code, "SIDECAR_RESTARTED")

    def test_removes_expired_terminal_jobs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            job = CollectionJob(
                job_id="job-old", request=SearchRequest("品牌", 20), status="SUCCEEDED"
            )
            job.updated_at = (
                datetime.now(timezone.utc) - timedelta(hours=2)
            ).isoformat().replace("+00:00", "Z")
            (root / "job-old.json").write_text(
                json.dumps(job.to_storage(), ensure_ascii=False), encoding="utf-8"
            )

            store = FileJobStore(root, retention_hours=1)

            self.assertIsNone(store.get("job-old"))


if __name__ == "__main__":
    unittest.main()
