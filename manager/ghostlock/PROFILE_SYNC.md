# GhostLock upstream synchronization

## Pinned source

ZSU synchronizes `YuKongA/ghostlock-app` at commit `f44fa12a8f21340f2c31840c00d05baef69cc4f7`.

The native C++23 engine, JVM profile-core sources, Rust extractor, disassembly comparator, and HOCON catalog are covered by `upstream-sync.json`. Verify them with:

```bash
python manager/ghostlock/tools/verify_upstream_sync.py \
  --source <path-to-pinned-upstream-checkout> --root .
```

The catalog contains 79 non-template, exact-`uname -r` profiles: 69 direct recommendations and 10 Shizuku recommendations. It also contains four templates and shared/index documents, for 89 packaged `.conf` files. Templates never match a device. No previously bundled ZSU profile is removed.

## Runtime contract

- The old flat JSON profile, `defaults.json`, `--profile`, `GHOSTLOCK_CORE`, and `GHOSTLOCK_CONSUMER_CORE` launch contract is removed from the active path.
- Built-in and explicitly selected imported HOCON documents merge through the pinned profile core and encode as GLK1 magic `0x0D000721`, version 2.
- Native execution uses `--ghostlock-app-call --enable-status-record` and a four-byte big-endian frame on stdin. Stdin remains open for status acknowledgements.
- Direct execution requires ZSU's own packaged `libksud.so`; it never falls back to another manager. Shizuku uses an explicit manager choice first, then ZSU and the remaining known managers.
- Direct, Shizuku, and automatic operations are serialized in the app process. A native attempt is never automatically retried.
- Auto Ghostlock remains opt-in, one-shot per full boot, soft-reboot aware, and disabled after failure.

## Device status

The Sony Xperia 1 V SOG10 profile `5.15.189-android13-8-00004-g1c3825f8ac0a-ab14110541` is bundled exactly as upstream, but its full device gate remains pending. Bundling a profile is not a claim that it is device-verified.

All direct, Shizuku, and profile-specific device gates remain pending without physical-device evidence. See `VERIFICATION.md` for actual host results and unavailable toolchains.

## Licensing

`LICENSE-APACHE-2.0` is retained unchanged with SHA-256 `1eb85fc97224598dad1852b5d6483bbcf0aa8608790dcc657a5a2a761ae9c8c6`.
