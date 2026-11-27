# GhostLock synchronization verification

Pinned upstream revision: `f44fa12a8f21340f2c31840c00d05baef69cc4f7`.

Local toolchain: JDK `21.0.13.1+2`, Android SDK 36, build tools `36.1.0`, and NDK `29.0.14206865` under the task workspace.

| Gate | Command/device | Result | Evidence |
|---|---|---|---|
| Native host tests (26) | `make -C manager/ghostlock native-host-tests` | PENDING | The NDK's `make` is available, but no host `c++` compiler is installed; compilation stops before the first test. |
| Host attack dataflow | `make -C manager/ghostlock host-attack-dataflow-test` | PENDING | Requires the same unavailable host C++ compiler. |
| Profile core | `./gradlew :profile-core:test` | PASS | 18 tests, 0 failures and 0 errors. |
| Profile export | `./gradlew :profile-core:exportKernelProfiles` | PASS | All 79 non-template profiles exported as GLK1 documents. |
| Android unit tests | `./gradlew :app:testDebugUnitTest` | PASS | 27 tests, 0 failures and 0 errors; includes malformed/mismatched imports, out-of-range geometry, and resolution of every one of the 79 device profiles. |
| ARM64 native build/package | `./gradlew :app:buildGhostlockNative :app:packageGhostlockNative` | PASS | NDK build completed with no compiler warnings; packaged copy stripped successfully. |
| Packaged native executable | Inspect `manager/app/build/generated/ghostlock-jni/arm64-v8a/libghostlock.so` | PASS | 383,728-byte ELF64 AArch64 (`e_machine = 183`) executable. |
| Debug APK | `./gradlew :app:assembleDebug` | PENDING | App and its regular CMake libraries compile; `buildGhostlockExtract` stops on the unavailable Windows Rust host linker, so no APK is claimed. |
| lint-tidy | `make -C manager/ghostlock lint-tidy` | PASS | Exit 0 with zero unsuppressed user-code findings. |
| Eight-function disassembly | `python manager/ghostlock/tools/cmp_disasm.py <baseline> manager/ghostlock/build/native/ghostlock` | PENDING | ZSU's ARM64 binary is built, but a separately built pinned-upstream baseline is not available. |
| Cold-boot direct gate | Device/release/route | PENDING | No attached compatible device or device log is available. |
| Cold-boot Shizuku gate | Device/release/route | PENDING | No attached compatible device or device log is available. |
| Locked Rust extractor tests | `cargo test --manifest-path manager/ghostlock/tools/extract_rs/Cargo.toml --locked` | PENDING | Rust is installed locally, but neither the MSVC nor GNU Windows host setup provides a working complete linker toolchain; no tests ran. |
| Pinned source/asset parity | `python manager/ghostlock/tools/verify_upstream_sync.py --source <upstream> --root .` | PASS | 258 pinned source/asset files and 79 non-template profiles verified at the pinned commit; obsolete JSON/CPU launch contract is absent. |
| OTA adapter parity | Compare five Kotlin files after their package lines | PASS | All five files differ from upstream only in package name. |
| Repository engineering regressions | `python scripts/test_engineering_regressions.py` | PASS | All 14 tests passed, including Android Rust target and declared NDK 29 setup contracts for both Manager assembly workflows. |
| Manifest/resources and patch whitespace | XML parsers and `git diff --check` | PASS | Android manifest and English strings parsed; no whitespace errors. |
| APK contents | Inspect both v1.5.6 release APKs | PASS | Normal and spoofed APKs are valid ZIP archives; each contains the 383,728-byte AArch64 GhostLock executable and all 89 HOCON assets. |
| v1.5.6 release workflow | GitHub Actions run `45640381355` | PASS | Completed successfully at tag target `3bbc1a5b5d43d53758d3ceabc4605ffe91129440`. |
| Release asset integrity | Download release and verify `SHA256SUMS` | PASS | All 15 checksummed assets match; both APKs report version code 33391 and the expected normal/spoofed package metadata. |
| APK signatures and upgrade path | `apksigner verify --print-certs`; compare v1.5.4 | PASS with installation caveat | Both v1.5.6 signatures verify. Their CI-generated Android Debug certificates differ from v1.5.4, so the normal APK requires a clean manager install rather than an in-place upgrade. |
| SOG10 device gate | Sony Xperia 1 V / `5.15.189-android13-8-00004-g1c3825f8ac0a-ab14110541` | PENDING | Profile is bundled and host-resolves; on-device behavior is not verified. |

Release: https://github.com/Only7rb-coder/zsu/releases/tag/v1.5.6

Source/host verification completed where available; on-device behavior is not verified.
