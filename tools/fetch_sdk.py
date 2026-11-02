#!/usr/bin/env python3
"""Fetch the public, version-pinned QVAC Maven archive. Does not download models."""
import hashlib
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.23.0"
URL = f"https://github.com/tetherto/qvac/releases/download/sdk-v{VERSION}/qvac-sdk-android-{VERSION}-maven.zip"
SHA256 = "a97ca0a5bf16ba49aefea688dfabd1d396ea174375e4fe3c9a25e0ade594c1d0"
archive = ROOT / "vendor" / "qvac-sdk.zip"
archive.parent.mkdir(exist_ok=True)
if not archive.exists():
    subprocess.run(["curl", "--fail", "--location", "--retry", "2", "--output", str(archive), URL], check=True)
if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
    raise SystemExit("SDK checksum mismatch. Remove vendor/qvac-sdk.zip and retry.")
destination = ROOT / "vendor" / "qvac"
with zipfile.ZipFile(archive) as source:
    for entry in source.infolist():
        if entry.filename.startswith("/") or ".." in Path(entry.filename).parts:
            raise SystemExit("Unsafe archive entry")
    source.extractall(destination)
print(f"QVAC {VERSION} ready at {destination}")
