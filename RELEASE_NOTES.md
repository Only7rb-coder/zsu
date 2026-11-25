# ZSU v1.5.5

## GhostLock upstream synchronization

- Synchronize GhostLock with upstream `YuKongA/ghostlock-app` commit `f44fa12a8f21340f2c31840c00d05baef69cc4f7`.
- Include the complete catalog of 79 exact-release device profiles and preserve the GLK1 v2 profile contract.
- Support GhostLock through direct root execution and Shizuku, with the upstream native engine packaged for Android arm64.
- Support profile import and OTA payload extraction while rejecting malformed imports, invalid geometry, and incomplete profile data instead of applying unsafe partial state.
- Preserve the existing Auto Ghostlock behavior and ZSU integration.

## Build and verification

- Install the required `aarch64-linux-android` Rust target in both Manager build workflows.
- Verify 258 pinned upstream files and all 79 device profiles against the recorded upstream revision.
- Pass 13 engineering regression tests, 45 Android/JVM unit tests, native packaging, profile export, and native static analysis checks.
- Build the release APKs and kernel artifacts through the GitHub Actions release workflow and publish verified SHA-256 checksums.

Direct-root, Shizuku, and SOG10 behavior still requires testing on physical devices; successful builds and host tests do not establish on-device compatibility.
