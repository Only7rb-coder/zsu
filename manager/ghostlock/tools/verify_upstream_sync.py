#!/usr/bin/env python3
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

PIN = "f44fa12a8f21340f2c31840c00d05baef69cc4f7"

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def releases_from_hocon(text: str) -> list[str]:
    return [item for item in re.findall(r'\{ release = "([^"]+)"', text)
            if not item.endswith("-template")]

def local_path(root: Path, relative: str) -> Path:
    if relative.startswith("profile-core/") or relative.startswith("app/"):
        return root / "manager" / relative
    return root / "manager/ghostlock" / relative

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--native-only", action="store_true")
    mode.add_argument("--catalog-only", action="store_true")
    mode.add_argument("--runtime-only", action="store_true")
    args = parser.parse_args()
    source = args.source.resolve()
    root = args.root.resolve()
    manifest = json.loads((root / "manager/ghostlock/upstream-sync.json").read_text())
    assert manifest["revision"] == PIN
    if not args.runtime_only:
        selected = sorted(manifest["files"].items())
        if args.native_only:
            selected = [item for item in selected if item[0].startswith("src/") or item[0] == "tools/cmp_disasm.py"]
        elif args.catalog_only:
            selected = [item for item in selected if item[0].startswith("profile-core/src/") or item[0].startswith("app/src/main/assets/kernel_profiles/")]
        for relative, expected in selected:
            upstream = source / relative
            local = local_path(root, relative)
            assert upstream.is_file(), f"missing upstream input: {relative}"
            assert local.is_file(), f"missing synchronized file: {relative}"
            assert digest(upstream) == digest(local) == expected, f"hash mismatch: {relative}"
        print(f"Verified {len(selected)} pinned source/asset files at {PIN}")
    if not args.native_only and not args.runtime_only:
        upstream_index = (source / "app/src/main/assets/kernel_profiles/index.conf").read_text()
        local_index = (root / "manager/app/src/main/assets/kernel_profiles/index.conf").read_text()
        upstream_releases = releases_from_hocon(upstream_index)
        local_releases = releases_from_hocon(local_index)
        assert len(upstream_releases) == len(set(upstream_releases)) == 79
        assert local_releases == upstream_releases, "profile index differs from upstream"
        print(f"Verified {len(local_releases)} upstream profiles at {PIN}")
    if not args.native_only and not args.catalog_only:
        forbidden = ["GHOSTLOCK_CORE", "GHOSTLOCK_CONSUMER_CORE", "--profile"]
        runner = root / "manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunner.kt"
        for token in forbidden:
            assert token not in runner.read_text(), f"obsolete contract {token} in GhostlockRunner.kt"
        print("Verified that the obsolete JSON/CPU launch contract is absent")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"upstream sync check failed: {error}", file=sys.stderr)
        raise SystemExit(1)
