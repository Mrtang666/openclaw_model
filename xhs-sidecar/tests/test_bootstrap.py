import tempfile
import unittest
import zipfile
from pathlib import Path

from xhs_sidecar.bootstrap import extract_spider_archive


class BootstrapTests(unittest.TestCase):
    def test_extracts_valid_spider_archive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "spider.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("Spider_XHS/apis/xhs_pc_apis.py", "# test")

            extracted = extract_spider_archive(archive, root / "runtime")

            self.assertTrue((extracted / "apis" / "xhs_pc_apis.py").is_file())

    def test_rejects_path_traversal(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "unsafe.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("../outside.txt", "unsafe")

            with self.assertRaises(ValueError):
                extract_spider_archive(archive, root / "runtime")


if __name__ == "__main__":
    unittest.main()
