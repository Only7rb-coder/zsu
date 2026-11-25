# GhostLock synchronization verification

Pinned upstream revision: `f44fa12a8f21340f2c31840c00d05baef69cc4f7`.

| Gate | Command/device | Result | Evidence |
|---|---|---|---|
| Native host tests (26) | `make -C manager/ghostlock native-host-tests` | PENDING | `make` is not installed on the current host. |
| Host attack dataflow | `make -C manager/ghostlock host-attack-dataflow-test` | PENDING | `make` is not installed on the current host. |
| Profile core | `./gradlew :profile-core:test` | PENDING | Gradle stops before compilation: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` |
| Android unit tests | `./gradlew :app:testDebugUnitTest` | PENDING | Same missing Java runtime; no Kotlin test result is claimed. |
| ARM64 native/APK build | `./gradlew :app:assembleDebug` | PENDING | Same missing Java runtime; no APK was produced. |
| lint-tidy | `make -C manager/ghostlock lint-tidy` | PENDING | `make` is not installed on the current host. |
| Eight-function disassembly | `python manager/ghostlock/tools/cmp_disasm.py <baseline> manager/ghostlock/build/native/ghostlock` | PENDING | Fresh upstream and ZSU ARM64 binaries are unavailable. |
| Cold-boot direct gate | Device/release/route | PENDING | No attached compatible device or device log is available. |
| Cold-boot Shizuku gate | Device/release/route | PENDING | No attached compatible device or device log is available. |
| Locked Rust extractor tests | `cargo test --manifest-path manager/ghostlock/tools/extract_rs/Cargo.toml --locked` | PENDING | `cargo` is not installed on the current host. |
| Pinned source/asset parity | `python manager/ghostlock/tools/verify_upstream_sync.py --source <upstream> --root .` | PASS | 258 pinned source/asset files and 79 non-template profiles verified at the pinned commit. |
| OTA adapter parity | Compare five Kotlin files after their package lines | PASS | All five files differ from upstream only in package name. |
| Repository engineering regressions | `python scripts/test_engineering_regressions.py` | PASS | All 12 tests passed. |
| Manifest/resources and patch whitespace | XML parsers and `git diff --check` | PASS | Android manifest and English strings parsed; no whitespace errors. |
| APK contents | Inspect ARM64 libraries and 89 HOCON assets | PENDING | Requires a successful APK build. |
| SOG10 device gate | Sony Xperia 1 V / `5.15.189-android13-8-00004-g1c3825f8ac0a-ab14110541` | PENDING | Profile is bundled; on-device behavior is not verified. |

Source/host verification completed where available; on-device behavior is not verified.
