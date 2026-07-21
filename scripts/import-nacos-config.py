#!/usr/bin/env python3
"""Publish YAML configs under docker/nacos/init/DEFAULT_GROUP to local Nacos."""
from __future__ import annotations

import argparse
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DIR = ROOT / "docker" / "nacos" / "init" / "DEFAULT_GROUP"
ORIG_ZIP_DIR = ROOT / "backend" / "docs" / "nacos"


def publish(nacos: str, group: str, data_id: str, content: str) -> bool:
    url = f"{nacos.rstrip('/')}/nacos/v1/cs/configs"
    body = urllib.parse.urlencode(
        {
            "dataId": data_id,
            "group": group,
            "type": "yaml",
            "content": content,
        }
    ).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        text = resp.read().decode("utf-8", errors="replace").strip()
    ok = text == "true"
    print(f"  - {data_id}: {'OK' if ok else text}")
    return ok


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--nacos", default="http://localhost:8848")
    parser.add_argument("--group", default="DEFAULT_GROUP")
    parser.add_argument(
        "--also-original",
        action="store_true",
        help="Also import extracted original configs from /tmp or backend zip extract",
    )
    args = parser.parse_args()

    files: list[Path] = []
    if DEFAULT_DIR.is_dir():
        files.extend(sorted(DEFAULT_DIR.glob("*.yaml")))
        files.extend(sorted(DEFAULT_DIR.glob("*.yml")))

    # optional original extract cache
    orig = Path("/tmp/nacos_orig/DEFAULT_GROUP")
    if not orig.is_dir():
        # try windows temp
        orig = Path.home() / "AppData" / "Local" / "Temp" / "nacos_orig" / "DEFAULT_GROUP"

    override_ids = {p.name for p in files}
    if args.also_original and orig.is_dir():
        for p in sorted(orig.glob("*.y*ml")):
            if p.name not in override_ids:
                files.append(p)

    if not files:
        print(f"No config files found in {DEFAULT_DIR}", file=sys.stderr)
        return 1

    print(f"Publishing {len(files)} configs -> {args.nacos} group={args.group}")
    failed = 0
    for path in files:
        content = path.read_text(encoding="utf-8")
        if not publish(args.nacos, args.group, path.name, content):
            failed += 1
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
