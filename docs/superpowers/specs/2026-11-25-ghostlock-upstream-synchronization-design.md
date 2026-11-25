# ZSU GhostLock upstream synchronization design

Date: 2026-11-25
Status: Approved design, pending written-spec review

## Purpose

Update ZSU's integrated GhostLock to the native engine, profile contract, and runtime paths of `YuKongA/ghostlock-app`, while retaining ZSU's manager identity, Compose interface, confirmation flow, and Auto Ghostlock safeguards. The result must not be a profile-only refresh or a second standalone GhostLock application.

The authoritative upstream revision is:

- `f44fa12a8f21340f2c31840c00d05baef69cc4f7`

## Current incompatibility

ZSU currently ships an older C native core, flat JSON profiles, shared JSON execution defaults, and a `--profile <json>` native entry point. It also passes CPU selections through `GHOSTLOCK_CORE` and `GHOSTLOCK_CONSUMER_CORE` and synchronously reads native stdout into the UI.

The pinned upstream revision uses a C++23 native engine, HOCON profile assets, GLK1 wire version 2, and the `--ghostlock-app-call` or `--load-prebuilt-profile <bin>` entry points. Status-record execution requires a four-byte big-endian length followed by the GLK1 document on stdin; stdin remains open for status acknowledgements. CPU selection is encoded in the resolved profile. Native output and the KernelSU handoff log are tailed from files so pipe or Binder backpressure does not stall the native route.

The old ZSU launch path cannot execute the pinned upstream native binary. Keeping it as a fallback would preserve two divergent profile and execution contracts, so this migration replaces it.

## Scope and feature parity

This change covers:

- The upstream native GhostLock engine and native support code
- The upstream reusable Kotlin profile model, resolver, merger, HOCON support, and GLK1 codec
- All upstream built-in profile assets and shared execution configuration
- Direct execution through ZSU's own `ksud`
- Explicitly selected Shizuku execution and its user service
- Built-in and imported HOCON profile resolution
- Local image and remote OTA profile extraction using the upstream extractor and OTA helpers
- Manager discovery and selection where the Shizuku manager handoff requires it
- ZSU UI adapters for these execution paths, status, errors, and profile selection
- Upstream and ZSU host tests, Android builds, native static checks, and disassembly comparison

It does not copy upstream's standalone application, visual identity, navigation, localization set, hidden debug interface, or advanced profile editor. Imported profiles are stored and resolved verbatim; ZSU does not need upstream's editing-session UI to execute them. The production ZSU UI does not expose upstream's force-attack test switch.

## Catalog policy

The upstream index contains 79 non-template device profiles. ZSU currently contains 69, all of which remain present upstream. The synchronized catalog therefore adds these 10 profiles and removes no existing ZSU entry:

- `5.10.236-android12-9-00014-gb97c62c4e7d1-ab13436420`
- `5.15.189-android13-8-00004-g1c3825f8ac0a-ab14110541`
- `6.1.78-android14-11-gb6577b760481-ab12075023`
- `6.1.138-android14-11-g7b9e94e37cfa-mi`
- `6.1.157-android14-11-g27b608536f86-ab14702606`
- `6.1.162-android14-11-g65896c4edca1-ab15242664`
- `6.6.30-android15-8-g6b55ce738535-4k`
- `6.6.82-android15-8-g076ac12bde16-ab13715361-4k`
- `6.6.89-android15-8-g6bf8136549d7-abogki463261049-4k`
- `6.6.118-android15-8-o-ga031df45165f-4k`

Template entries remain authoring assets and never count as device matches. The SOG10 profile is included for upstream catalog parity, but its upstream warning that the full device gate is pending must remain visible in synchronization documentation and must not be replaced with a device-verified claim. Catalog inclusion alone is not a support guarantee.

## Architecture

### 1. Native engine and build adapter

Replace ZSU's old native implementation with the pinned upstream `src` tree. Keep upstream native implementation files, including `src/core` and `kernelsnitch`, byte-identical to the pinned revision. ZSU-specific NDK discovery, output naming, Gradle integration, and packaging live in Makefiles or build adapters rather than patched exploit code.

Package the ARM64 executable as `libghostlock.so` using ZSU's existing native-library packaging convention. Build it as C++23 with the upstream compiler and linker requirements. Remove obsolete C sources, the JSON parser, generated target-specific offset headers, and JSON-only host tests when their replacements are present; do not leave them reachable from the build.

Retain the upstream Apache-2.0 license and relevant notices. Record the upstream revision and parity-check procedure in `manager/ghostlock/PROFILE_SYNC.md`.

### 2. Reusable profile core

Add a Gradle `profile-core` module containing the pinned upstream profile-core Kotlin sources without source modifications. Preserve its tests. Build-file changes needed to locate ZSU's assets and build directories are adapter changes, not changes to the profile algorithm.

The app consumes this module for HOCON parsing, profile merging and validation, route selection, execution defaults, component identifiers, and GLK1 v2 encoding/decoding. ZSU must not reimplement the binary layout in an unrelated JSON or Kotlin model.

Replace `kernel_profiles/index.json`, flat `.json` profiles, and `defaults.json` with upstream's `index.conf`, device `.conf` files, shared credential/kernelsnitch includes, execution tuning, and route presets. The asset index remains the single source of truth for built-in support.

### 3. ZSU profile repository

Implement a thin Android adapter around the reusable profile core. Its responsibilities are:

- Read bundled assets and imported documents off the main thread
- Match `GhostlockKernelRelease.current()` exactly against the selected built-in or imported profile
- Preserve `Os.uname().release` as the authoritative running-kernel value; the Java property remains only the existing unusual-environment fallback
- Resolve shared execution defaults, route/fallback geometry, physical-address settings, and component selection through upstream profile-core code
- Encode the selected main and consumer CPUs in `execution.selected_cpus`
- Return one validated GLK1 v2 byte array for direct or Shizuku execution
- Reject unresolved routes, missing required fields, invalid geometry, schema mismatches, and index/file release mismatches before native execution

Availability checks must resolve and validate the same document used for execution. A raw asset's presence is not sufficient to display a kernel as runnable.

### 4. Direct native runner

Replace `GhostlockRunner`'s old process contract with the upstream app-call protocol:

1. Create the per-run working and log state.
2. Resolve the exact running release and selected CPU pair into GLK1 v2 bytes.
3. Require the packaged `libghostlock.so` and ZSU's packaged `libksud.so`.
4. Start `libghostlock.so --ghostlock-app-call --enable-status-record`.
5. Write a four-byte big-endian payload length followed by the GLK1 bytes to stdin, flush them, and keep stdin open for acknowledgements.
6. Set `GHOSTLOCK_KSUD` to ZSU's own `libksud.so` and `GHOSTLOCK_KSU_LOG` to a fresh per-run path.
7. Redirect native output to a file and tail complete lines from that file and the per-run KernelSU log.
8. Consume status-marker lines, persist their state, and write the upstream status ACK line to stdin. Ordinary lines remain user-facing logs.
9. On exit or timeout, stop tailing, perform a final log drain, close process resources, and return a structured result.

Do not pass `GHOSTLOCK_CORE` or `GHOSTLOCK_CONSUMER_CORE`. Do not invoke `--profile`, use the removed JSON files, or silently retry through `--load-prebuilt-profile`.

Direct runs always use ZSU's own `ksud`; they never silently select another installed root manager. Manager discovery is for the explicit Shizuku/manager path, not a direct-run fallback.

### 5. Status, logging, and watchdog

Persist the ordered upstream run steps (`w1a`, `w1b`, `w2a`, `w2b`, `w3a`, `w3b`, `w3c`) with `not_start`, `in_progress`, and `completed` states. Writes should be flushed and synchronized so the last in-progress step survives an application or device crash. Clear the state when native disables status recording after an ACK timeout; a missing ACK must not deadlock the native run.

Use a unique KernelSU log path for every attempt so stale markers cannot satisfy the handoff probe. Preserve a final `last-run.log` or equivalent archived result for diagnosis. The UI may display a bounded current status line, but complete logs belong in files rather than an unbounded in-memory stream.

Retain the 300-second outer watchdog, graceful termination followed by forcible termination, interruption cleanup, explicit timeout reporting, signal-aware exit diagnostics, and the specific exit-137/resource-pressure message. File tailers and process references must be cleaned up on success, failure, timeout, and coroutine interruption.

### 6. Shizuku execution

Integrate the upstream Shizuku API/provider, AIDL callbacks, user service, and runner behind ZSU adapters. Add only the required manifest components and package visibility declarations.

Shizuku is an explicit execution choice. Profiles with `recommend_shizuku = 1` may preselect it for a new UI session, as upstream does, but the user can turn it off for that session. Permission denial, service startup failure, or a missing selected manager returns an error; it does not silently fall back to direct execution after the user chose Shizuku.

The Shizuku service receives the already resolved GLK1 document and uses the same framing, status, logging, timeout, and cleanup contract as direct execution. Binder callbacks convey status and completion, not a blocking stream of native stdout. Add `com.zsu.zsu` as the highest-priority built-in manager candidate and package-visibility entry so ZSU can select itself. An explicitly selected manager is ordered first, followed by ZSU and then upstream's known-manager candidates; direct ZSU execution remains pinned to ZSU's own daemon.

### 7. Imported profiles and extraction

Add ZSU UI actions for importing a HOCON document, selecting a built-in or imported profile, and extracting a profile from supported local images or a remote OTA URL. Use upstream's user-document parsing, legacy conversion where required, extractor binary, and remote OTA partition helper rather than accepting arbitrary flat JSON.

Imported source documents are stored verbatim. Extraction runs off the main thread with bounded process execution and private working files. Invalid output, a missing release, unsupported schema, missing sidecar fields, and extractor failure produce explicit errors. Existing imported documents are not overwritten without an explicit overwrite decision. Temporary extraction files are removed on every path.

Package the ARM64 extractor as `libextract.so` and keep its Rust source and lockfile at the pinned upstream content. Remote OTA handling must use the existing ZSU network-security policy and must report download/extraction failures rather than substituting a different kernel's profile.

### 8. ZSU UI and Auto Ghostlock

Retain the existing ZSU GhostLock screen, CPU-pair selector, confirmation dialog, elapsed time, status display, and settings entry. Add only the controls needed for execution mode, profile import/selection/extraction, and manager selection when relevant.

Profile lookup and all native, Shizuku, file, and extraction work remain off the main thread. Recomposition must not repeatedly parse assets. Only one operation may own the native route at a time across direct, Shizuku, and automatic execution.

A successful manual run continues to arm Auto Ghostlock without enabling it silently. Automatic execution remains explicitly opt-in and one-shot per full boot. Preserve boot-count handling so a userspace/soft reboot does not trigger a new attempt. Duplicate boot delivery must not queue duplicate work. On the next boot, an attempt left marked running by process death disables automatic execution. A completed native failure, timeout, or Shizuku failure disables automatic execution and clears its pending state. Neither the runner nor the Shizuku service performs an automatic retry.

## Failure behavior

The integration fails closed before native execution when the ABI, exact release, selected profile, GLK1 encoding, native binary, required daemon, or selected execution service is unavailable or invalid.

During execution:

- Malformed or missing GLK1 input is a native failure, not a reason to try the legacy JSON path.
- Status acknowledgement failure disables status recording according to upstream behavior; it does not stall the route indefinitely.
- Timeout and interruption terminate the owned process and clean up tailers, callbacks, and temporary resources.
- Nonzero exits preserve native and KernelSU logs and map to the existing ZSU failure UI, including exit 137 and other signal exits.
- No failure path starts an immediate automatic second attempt.
- A failed automatic attempt disables Auto Ghostlock until the user explicitly enables it again after the armed-state requirements are met.

## Verification

### Source and protocol parity

- Compare native implementation and profile-core source hashes with upstream `f44fa12a8f21340f2c31840c00d05baef69cc4f7`; every difference must be confined to documented ZSU adapters or build files.
- Verify the 79-profile index, asset/file/release consistency, template exclusion, shared include resolution, and absence of ZSU-only profile loss.
- Run upstream profile-core tests and exporter agreement tests. Confirm generated documents decode as GLK1 magic `0x0D000721`, version 2, with the expected root-child/backend component identifiers.
- Test framing with empty, malformed, oversized, and valid payloads; verify big-endian length, open stdin, status ACKs, status disable handling, and final log draining.

### Native and Android checks

- Run the upstream native host suite with all 26 registered tests.
- Run the upstream `lint-tidy` checks and required eight-function `cmp_disasm` comparison against a documented baseline built from the same pinned source and compatible toolchain settings.
- Build `libghostlock.so` and `libextract.so` for ARM64 with zero warnings treated as errors where upstream requires it.
- Run profile-core, extractor, and relevant Android unit tests, then assemble and analyze the ZSU APK to confirm both native libraries, HOCON assets, Shizuku components, licenses, and no obsolete JSON execution assets are packaged.
- Run ZSU's complete engineering regression suite, jailbreak configuration validation, whitespace checks, shell syntax checks, and relevant formatter/static-analysis checks.

Android and kernel CI builds may run only after the reviewed change is authorized for remote execution. No push, merge, or release is part of implementing this design without that review.

### Device gate

Host tests and APK assembly do not prove that GhostLock works on a device. Before describing any route as device-proven, archive its logs and run the upstream-required cold-boot, fixed-CPU, single-route gate. Cover both direct and Shizuku paths where enabled, status persistence/recovery, explicit cancellation or timeout behavior, success followed by armed-but-not-enabled Auto Ghostlock, full versus soft reboot, and disable-after-failure.

Each newly included profile remains device-unverified until its matching hardware/firmware gate passes. SOG10 specifically remains pending its full device gate. If no compatible physical device is attached, the deliverable is verified source and build evidence plus a pending device matrix, not a claim of on-device success.

## Acceptance criteria

The implementation is code-complete when:

- ZSU executes only the pinned GLK1 v2 native/profile contract; the old JSON launch path is gone.
- All 79 upstream device profiles and every existing ZSU profile are represented, with templates excluded from matching.
- Direct, Shizuku, imported-profile, and extraction paths use the same validated profile authority and have explicit failure behavior.
- ZSU's own daemon is mandatory for direct runs, and no path automatically retries a failed native attempt.
- Existing Auto Ghostlock opt-in, boot-generation, and failure-disable rules remain intact.
- Native/profile parity checks and all available host/build checks pass, with any unavailable toolchain or device check reported as pending.
- The result remains local and unpushed until presented for review.

## Non-goals

- Replacing ZSU with the standalone GhostLock app or copying its visual design
- Adding a second root authorization system or changing ZSU kernel/KMI interfaces
- Adding an advanced profile editor or exposing force-attack testing in the production UI
- Claiming that all profiles are safe or functional without device-specific evidence
- Bundling unrelated root-manager features or releasing a new ZSU version automatically
