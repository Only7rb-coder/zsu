#!/usr/bin/env python3
"""Validate the indexed JSON profile catalog and exercise the native loader."""
from __future__ import annotations

import argparse
import json
import subprocess
import tempfile
from pathlib import Path

ALLOWED = {
    "schema_version", "release", "execution", "symbols", "struct_fields",
    "kernel_major", "requires_shizuku", "kernel_phys_load", "kernel_phys_offset",
    "pselect_waiter_shift", "mcast_waiter_off", "mcast_buffer_size",
    "mcast_task_offset", "mcast_lock_offset", "mcast_fake_lock_offset",
    "mcast_fake_task_offset", "mcast_lock_slots_offset", "mcast_lock_slot_count",
    "mcast_lock_slot_stride", "kernelsnitch_collisions", "compact_waiter",
    "mm_struct_sz", "cred_copy_size", "cred_usage_offset", "cred_usage_value",
    "cred_caps_offset", "cred_caps_count", "cred_caps_value", "cred_ref_count",
    "cred_ref0_offset", "cred_ref1_offset", "cred_ref2_offset", "cred_ref3_offset",
    "cred_ref0_image", "cred_ref1_image", "cred_ref2_image", "cred_ref3_image",
    "off_init_task", "off_init_cred", "off_empty_zero_page", "off_mcast_fake_bss",
    "off_root_task_group", "off_selinux_enforcing", "off_selinux_blob_sizes",
    "off_security_hook_heads", "off_slide_nfulnl_logger", "off_slide_loggers_0_1",
    "off_slide_boot_id", "task_prio", "task_normal_prio", "task_sched_task_group",
    "task_pi_lock", "task_pi_waiters", "task_pi_top_task", "task_pi_blocked_on",
    "task_pid", "task_tgid", "task_atomic_flags", "task_real_cred", "task_cred",
    "task_comm", "task_tasks", "task_seccomp",
}


def deep_merge(base: dict, incoming: dict) -> dict:
    result = dict(base)
    for key, value in incoming.items():
        if isinstance(value, dict) and isinstance(result.get(key), dict):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--native-test", type=Path, required=True)
    args = parser.parse_args()

    index = json.loads((args.assets / "index.json").read_text(encoding="utf-8"))
    defaults = json.loads((args.assets / "defaults.json").read_text(encoding="utf-8"))
    if index.get("schema_version") != 1 or not isinstance(index.get("profiles"), list):
        raise SystemExit("invalid profile index schema")
    if defaults.get("schema_version") != 1 or not isinstance(defaults.get("execution"), dict):
        raise SystemExit("invalid profile defaults schema")

    seen_releases: set[str] = set()
    resolved_files: list[Path] = []
    with tempfile.TemporaryDirectory(prefix="ghostlock-profiles-") as temp:
        temp_dir = Path(temp)
        for i, entry in enumerate(index["profiles"]):
            release = entry.get("release")
            filename = entry.get("file")
            if not isinstance(release, str) or not release or release in seen_releases:
                raise SystemExit(f"invalid or duplicate indexed release: {release!r}")
            if not isinstance(filename, str) or Path(filename).name != filename:
                raise SystemExit(f"unsafe profile filename for {release!r}")
            seen_releases.add(release)
            profile_path = args.assets / filename
            if not profile_path.is_file():
                raise SystemExit(f"missing profile file: {filename}")
            profile = json.loads(profile_path.read_text(encoding="utf-8"))
            if profile.get("schema_version") != 1 or profile.get("release") != release:
                raise SystemExit(f"schema/release mismatch: {filename}")
            if profile.get("kernel_major") not in (5, 6):
                raise SystemExit(f"unsupported kernel_major: {filename}")
            if release.startswith("6.1.") and not profile.get("compact_waiter"):
                raise SystemExit(
                    f"legacy 6.1 runtime needs compact_waiter: {filename}"
                )
            for required in ("off_init_task", "off_init_cred"):
                if not isinstance(profile.get(required), int) or profile[required] <= 0:
                    raise SystemExit(f"missing or invalid {required}: {filename}")
            phys_offset = profile.get("kernel_phys_offset")
            if phys_offset is not None and (not isinstance(phys_offset, int) or phys_offset <= 0):
                raise SystemExit(f"invalid kernel_phys_offset: {filename}")
            unknown = set(profile) - ALLOWED
            if unknown:
                raise SystemExit(f"native parser does not recognize {filename}: {sorted(unknown)}")

            resolved = deep_merge({"release": release, "execution": defaults["execution"]}, profile)
            resolved["schema_version"] = 1
            resolved_path = temp_dir / f"{i:03d}.json"
            resolved_path.write_text(json.dumps(resolved), encoding="utf-8")
            resolved_files.append(resolved_path)

        subprocess.run([str(args.native_test), *(str(path) for path in resolved_files)], check=True)
    print(f"profile catalog validation passed: {len(seen_releases)} indexed profiles")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
