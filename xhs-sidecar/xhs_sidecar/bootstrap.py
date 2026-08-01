from __future__ import annotations

import argparse
import shutil
import zipfile
from pathlib import Path, PurePosixPath


MAX_ARCHIVE_BYTES = 250 * 1024 * 1024
MAX_ARCHIVE_FILES = 5000


def extract_spider_archive(archive: Path, target: Path) -> Path:
    archive = archive.resolve()
    target = target.resolve()
    if not archive.is_file():
        raise ValueError(f"archive does not exist: {archive}")
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as source:
        members = source.infolist()
        if len(members) > MAX_ARCHIVE_FILES:
            raise ValueError("archive contains too many files")
        if sum(member.file_size for member in members) > MAX_ARCHIVE_BYTES:
            raise ValueError("archive is too large")
        for member in members:
            path = PurePosixPath(member.filename.replace("\\", "/"))
            if path.is_absolute() or ".." in path.parts:
                raise ValueError(f"unsafe archive member: {member.filename}")
        roots = {PurePosixPath(member.filename).parts[0] for member in members if member.filename}
        if len(roots) != 1:
            raise ValueError("Spider_XHS archive must contain one top-level directory")
        root_name = next(iter(roots))
        destination = target / root_name
        if destination.exists():
            if (destination / "apis" / "xhs_pc_apis.py").is_file():
                return destination
            raise ValueError(f"target already exists and is not Spider_XHS: {destination}")
        staging = target / f".{root_name}.extracting"
        if staging.exists():
            shutil.rmtree(staging)
        staging.mkdir()
        try:
            source.extractall(staging)
            extracted = staging / root_name
            if not (extracted / "apis" / "xhs_pc_apis.py").is_file():
                raise ValueError("archive is not a Spider_XHS project")
            extracted.replace(destination)
        finally:
            shutil.rmtree(staging, ignore_errors=True)
        return destination


def main() -> int:
    parser = argparse.ArgumentParser(description="Safely extract a Spider_XHS archive")
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument(
        "--target",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "runtime" / "vendor",
    )
    arguments = parser.parse_args()
    print(extract_spider_archive(arguments.archive, arguments.target))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
