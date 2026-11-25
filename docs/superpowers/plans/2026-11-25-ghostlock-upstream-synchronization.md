# GhostLock Upstream Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ZSU's obsolete GhostLock JSON/C integration with the pinned upstream C++23, HOCON, GLK1 v2, direct/Shizuku, and extraction contracts while preserving ZSU's UI and Auto Ghostlock safeguards.

**Architecture:** Copy upstream native, extractor, and profile-core implementations unchanged and verify their hashes. Put Android asset loading, imported-profile storage, process ownership, ZSU daemon selection, and Compose controls in small ZSU adapters. Direct and Shizuku execution share one framed process transport and one validated profile authority.

**Tech Stack:** C++23, Kotlin 2.2.0/JVM 21, Android Gradle Plugin 8.13.1, Typesafe Config, Gradle JVM module, Rust/Android ARM64 extractor, Shizuku 13.1.5, JUnit 4.13.2, Robolectric 4.17, Make/NDK 29.0.14206865.

## Global Constraints

- Upstream revision is exactly `f44fa12a8f21340f2c31840c00d05baef69cc4f7`.
- Upstream native implementation, profile-core Kotlin sources, and extractor Rust sources remain byte-identical; ZSU changes belong in adapters/build files.
- The runtime contract is GLK1 magic `0x0D000721`, version 2, four-byte big-endian framing for status-record app calls.
- All 79 upstream non-template profiles are bundled; templates never match devices; no existing ZSU profile is removed.
- `GhostlockKernelRelease.current()` and exact `uname -r` matching remain authoritative.
- Direct execution always uses ZSU's packaged `libksud.so`; no direct-run manager fallback is allowed.
- `GHOSTLOCK_CORE`, `GHOSTLOCK_CONSUMER_CORE`, `--profile <json>`, flat JSON profiles, and `defaults.json` are removed from the active path.
- Native output and KernelSU output use per-run files, not a synchronous stdout-to-UI race-path pipe.
- Only one direct, Shizuku, or automatic native operation may run at a time; no failed native attempt is automatically retried.
- Auto Ghostlock remains opt-in, one-shot per full boot, soft-reboot aware, and disabled after failure.
- SOG10 remains explicitly device-unverified even though its upstream profile is bundled.
- No push, merge, release, or device-success claim is permitted without the corresponding review/evidence.
- Execution is inline in the current session; do not dispatch subagents.
- Each command block runs in a fresh shell. Test blocks start at the workspace root unless they state otherwise; `git add`/`git commit` blocks start at the `zsu` repository root.

## File Structure

- `manager/ghostlock/src/**`: pinned upstream native engine, tests, Makefile, and clang-tidy policy.
- `manager/ghostlock/Makefile`: thin delegation to `src/Makefile` using `build/` outputs.
- `manager/ghostlock/tools/extract_rs/**`: pinned Rust extractor.
- `manager/ghostlock/tools/cmp_disasm.py`: pinned eight-function disassembly comparator.
- `manager/ghostlock/tools/verify_upstream_sync.py`: ZSU hash/catalog/obsolete-path regression gate.
- `manager/ghostlock/upstream-sync.json`: pinned revision and expected SHA-256 manifest.
- `manager/profile-core/**`: pinned upstream JVM profile implementation and tests plus ZSU Gradle paths.
- `manager/app/src/main/assets/kernel_profiles/**`: pinned HOCON index, includes, presets, templates, and 79 device profiles.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockAssetLoader.kt`: asset include expansion.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProfileStore.kt`: verbatim imported documents and explicit selection.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/LegacyGhostlockProfileConverter.kt`: upstream legacy conversion adapted only in package name.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProfileConfiguration.kt`: built-in/imported merge, validation, and GLK1 encoding.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunState.kt`: durable ordered status state and ACK markers.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProcess.kt`: framed native process, file tailing, watchdog, and cleanup.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunner.kt`: direct execution and ZSU result mapping.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockManagerScanner.kt`: ZSU-first explicit manager resolution.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockShizukuRunner.kt`: permission, bind, callback, and handshake lifecycle.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockUserService.kt`: shell-UID/seccomp gate and shared native transport.
- `manager/app/src/main/aidl/com/rifsxd/ksunext/ghostlock/*.aidl`: asynchronous log, status, and completion IPC.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockExtractor.kt`: local/remote extraction and verbatim storage.
- `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/ota/*.kt`: upstream OTA helper sources adapted only in package name.
- `manager/app/src/main/java/com/rifsxd/ksunext/ui/screen/Ghostlock.kt`: existing screen plus execution/profile controls.
- `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/*Test.kt`: resolver, transport, manager, store, and safety tests.

---

### Task 1: Pin and verify the native engine

**Files:**
- Create: `manager/ghostlock/tools/verify_upstream_sync.py`
- Create: `manager/ghostlock/upstream-sync.json`
- Replace: `manager/ghostlock/src/**` with `references/ghostlock-app/src/**`
- Replace: `manager/ghostlock/Makefile`
- Create: `manager/ghostlock/tools/cmp_disasm.py`
- Modify: `manager/app/build.gradle.kts` native input and output paths

**Interfaces:**
- Produces: `python manager/ghostlock/tools/verify_upstream_sync.py --source <upstream> --root <zsu>` returning zero only when pinned native hashes and catalog counts match.
- Produces: `make -C manager/ghostlock ghostlock` output at `manager/ghostlock/build/native/ghostlock`.

- [ ] **Step 1: Write the failing parity gate**

Implement the gate with these exact checks:

```python
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
```

The manifest generation command is used in Step 3 after copying the pinned files:

```bash
python -c 'import hashlib,json,pathlib; s=pathlib.Path("references/ghostlock-app"); roots=["src","tools/extract_rs","profile-core/src","app/src/main/assets/kernel_profiles"]; files={str(p.relative_to(s)).replace("\\","/"):hashlib.sha256(p.read_bytes()).hexdigest() for r in roots for p in (s/r).rglob("*") if p.is_file()}; files["tools/cmp_disasm.py"]=hashlib.sha256((s/"tools/cmp_disasm.py").read_bytes()).hexdigest(); pathlib.Path("zsu/manager/ghostlock/upstream-sync.json").write_text(json.dumps({"revision":"f44fa12a8f21340f2c31840c00d05baef69cc4f7","files":files},indent=2)+"\n")'
```

- [ ] **Step 2: Run the native gate before synchronization**

Run from the workspace:

```bash
python zsu/manager/ghostlock/tools/verify_upstream_sync.py --source references/ghostlock-app --root zsu --native-only
```

Expected: FAIL because the manifest is absent or the old synchronized files do not match.

- [ ] **Step 3: Copy the exact native implementation and add the thin root Makefile**

```bash
rm -rf zsu/manager/ghostlock/src
mkdir -p zsu/manager/ghostlock/tools
cp -a references/ghostlock-app/src zsu/manager/ghostlock/src
cp -a references/ghostlock-app/tools/cmp_disasm.py zsu/manager/ghostlock/tools/cmp_disasm.py
```

Use this complete root `manager/ghostlock/Makefile`:

```makefile
.PHONY: all ghostlock native-host-tests host-attack-dataflow-test lint-tidy clean

all: ghostlock

ghostlock:
	$(MAKE) -C src ghostlock

native-host-tests:
	$(MAKE) -C src native-host-tests

host-attack-dataflow-test:
	$(MAKE) -C src host-attack-dataflow-test

lint-tidy:
	$(MAKE) -C src lint-tidy

clean:
	$(MAKE) -C src clean
```

Update the app native task now so every later APK build uses the new engine:

```kotlin
val ghostlockBinary = ghostlockDir.resolve("build/native/ghostlock")

tasks.register<Exec>("buildGhostlockNative") {
    workingDir(ghostlockDir)
    commandLine("make", "ghostlock", "API=35")
    inputs.files(fileTree(ghostlockDir) {
        include("Makefile")
        include("src/Makefile")
        include("src/.clang-tidy")
        include("src/**/*.c")
        include("src/**/*.h")
        include("src/**/*.cpp")
        include("src/**/*.hpp")
    })
    outputs.file(ghostlockBinary)
}
```

Generate the manifest with the command in Step 1. Never edit a file listed in the manifest to make a ZSU test pass; adapt the caller instead.

- [ ] **Step 4: Run native host gates**

```bash
make -C zsu/manager/ghostlock native-host-tests
make -C zsu/manager/ghostlock host-attack-dataflow-test
python zsu/manager/ghostlock/tools/verify_upstream_sync.py --source references/ghostlock-app --root zsu --native-only
```

Expected: all 26 registered native host tests pass, the dataflow harness passes, and the native-only parity gate reports the pinned native/tool file count.

- [ ] **Step 5: Commit**

```bash
git add manager/ghostlock manager/app/build.gradle.kts
git commit -m "ghostlock: synchronize pinned native engine"
```

---

### Task 2: Add profile-core and the complete HOCON catalog

**Files:**
- Create: `manager/profile-core/build.gradle.kts`
- Create: `manager/profile-core/src/**` copied from upstream `profile-core/src/**`
- Modify: `manager/settings.gradle.kts`
- Modify: `manager/build.gradle.kts`
- Modify: `manager/gradle/libs.versions.toml`
- Replace: `manager/app/src/main/assets/kernel_profiles/**`
- Modify: `manager/app/build.gradle.kts`

**Interfaces:**
- Produces: Gradle project `:profile-core` with `test` and `exportKernelProfiles` tasks.
- Consumes: upstream profile sources and assets without algorithm edits.

- [ ] **Step 1: Copy sources/tests/assets and add the module**

```bash
mkdir -p zsu/manager/profile-core
cp -a references/ghostlock-app/profile-core/src zsu/manager/profile-core/src
rm -rf zsu/manager/app/src/main/assets/kernel_profiles
cp -a references/ghostlock-app/app/src/main/assets/kernel_profiles zsu/manager/app/src/main/assets/kernel_profiles
```

Add to `manager/settings.gradle.kts`:

```kotlin
include(":app")
include(":profile-core")
```

Add to the root plugins block and version catalog:

```kotlin
alias(libs.plugins.kotlin.jvm) apply false
```

```toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

Use this complete `manager/profile-core/build.gradle.kts`:

```kotlin
@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.typesafe:config:1.4.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.register<JavaExec>("exportKernelProfiles") {
    description = "Serialize bundled HOCON kernel profiles into v2 .bin documents"
    group = "build"
    val profilesDir = rootProject.layout.projectDirectory.dir("app/src/main/assets/kernel_profiles")
    val outputDir = rootProject.layout.buildDirectory.dir("kernel-profiles")
    inputs.dir(profilesDir)
    outputs.dir(outputDir)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghostlock.app.data.profile.ProfileExporter")
    args(
        profilesDir.asFile.absolutePath,
        outputDir.get().asFile.absolutePath,
        outputDir.get().asFile.absolutePath,
    )
}
```

Add app dependencies and Android-resource support for Robolectric:

```kotlin
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":profile-core"))
    implementation("com.typesafe:config:1.4.9")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
```

- [ ] **Step 2: Run profile tests and export**

```bash
cd zsu/manager
./gradlew :profile-core:test :profile-core:exportKernelProfiles
```

Expected: all upstream profile-core tests pass and export reports exactly 79 profiles.

- [ ] **Step 3: Verify catalog parity and native/profile binary agreement**

```bash
cd ..
python ghostlock/tools/verify_upstream_sync.py --source ../references/ghostlock-app --root . --native-only
python ghostlock/tools/verify_upstream_sync.py --source ../references/ghostlock-app --root . --catalog-only
make -C ghostlock native-host-tests
```

Expected: native-only and catalog-only parity gates pass; the exported documents retain GLK1 magic `0x0D000721`, version `2`, frontend `1`, and backend `1` in the existing native/profile tests.

- [ ] **Step 4: Commit**

```bash
git add manager/settings.gradle.kts manager/build.gradle.kts manager/gradle/libs.versions.toml manager/profile-core manager/app/build.gradle.kts manager/app/src/main/assets/kernel_profiles manager/ghostlock/upstream-sync.json
git commit -m "ghostlock: add upstream profile core and catalog"
```

---

### Task 3: Resolve built-in and imported profiles into GLK1 v2

**Files:**
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockAssetLoader.kt` from upstream `AssetConfigLoader.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/LegacyGhostlockProfileConverter.kt` from upstream `LegacyProfileConverter.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProfileStore.kt` from upstream `UserProfileStore.kt`
- Replace: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProfileConfiguration.kt`
- Test: `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/GhostlockProfileConfigurationTest.kt`

**Interfaces:**
- Produces: `data class ResolvedGhostlockProfile(val release: String, val bytes: ByteArray, val recommendShizuku: Boolean)`.
- Produces: `GhostlockProfileConfiguration.resolve(context, release, mainCpu, consumerCpu): ResolvedGhostlockProfile`.
- Preserves: `GhostlockProfileConfiguration.hasProfile(context, release): Boolean`.

- [ ] **Step 1: Copy the three storage/parsing adapters**

Copy upstream `AssetConfigLoader.kt`, `LegacyProfileConverter.kt`, and `UserProfileStore.kt`; change only their package to `com.rifsxd.ksunext.ghostlock`, rename the classes/objects to the ZSU filenames, and update their internal references. Keep include expansion, verbatim storage, filename sanitization, duplicate handling, and legacy conversion logic unchanged.

- [ ] **Step 2: Write failing resolver tests**

Use Robolectric's application context and include at least these concrete tests:

```kotlin
@Test fun exactReleaseProducesVersionTwoGlk1WithSelectedCpus() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val release = "6.12.23-android16-5-gf1bdb13583da-ab13761046-4k"
    val resolved = GhostlockProfileConfiguration.resolve(context, release, 4, 5)
    val decoded = NativeProfileDocument.fromBinary(resolved.bytes)!!
    assertEquals(release, decoded.release)
    assertEquals(4u, decoded.execution.recommendedMainCpu)
    assertEquals(5u, decoded.execution.recommendedConsumerCpu)
    assertEquals(0x0D000721.toInt(), ByteBuffer.wrap(resolved.bytes).order(ByteOrder.LITTLE_ENDIAN).int)
    assertEquals(2.toShort(), ByteBuffer.wrap(resolved.bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(4))
}

@Test fun unknownAndTemplateReleasesFailClosed() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertFalse(GhostlockProfileConfiguration.hasProfile(context, "6.12-template"))
    assertFalse(GhostlockProfileConfiguration.hasProfile(context, "not-a-kernel"))
}

@Test fun resolverDoesNotUseLegacyJsonAssets() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertThrows(FileNotFoundException::class.java) { context.assets.open("kernel_profiles/index.json") }
}
```

- [ ] **Step 3: Run the tests before replacing the resolver**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.GhostlockProfileConfigurationTest'
```

Expected: compilation or assertion failure because the old resolver returns JSON text and the old assets are still referenced.

- [ ] **Step 4: Replace the resolver with one upstream-core authority**

The complete public surface and resolution sequence is:

```kotlin
package com.rifsxd.ksunext.ghostlock

import android.content.Context
import com.ghostlock.app.data.HoconSupport
import com.ghostlock.app.data.NativeProfileDocument
import com.ghostlock.app.data.ValueMap
import com.ghostlock.app.data.asValueMap
import com.ghostlock.app.data.profile.CpuPairView
import com.ghostlock.app.data.profile.ProfileMerger
import com.ghostlock.app.data.profile.ProfileResolver

internal data class ResolvedGhostlockProfile(
    val release: String,
    val bytes: ByteArray,
    val recommendShizuku: Boolean,
)

internal object GhostlockProfileConfiguration {
    private const val Directory = "kernel_profiles"

    fun hasProfile(context: Context, release: String): Boolean = runCatching {
        resolve(context, release, 0, 1)
        true
    }.getOrDefault(false)

    fun resolve(
        context: Context,
        release: String,
        mainCpu: Int = 0,
        consumerCpu: Int = 1,
    ): ResolvedGhostlockProfile {
        require(mainCpu >= 0 && consumerCpu >= 0 && mainCpu != consumerCpu) { "invalid CPU pair" }
        val loader = GhostlockAssetLoader(context)
        val index = parse(loader.load("$Directory/index.conf"))
        require((index["schema_version"] as? Number)?.toInt() == 1) { "unsupported profile schema" }
        val entries = index["profiles"] as? List<*> ?: error("missing profile index")
        val entry = entries.mapNotNull { it.asValueMap() }.firstOrNull { it["release"] == release }
        val builtin = entry?.let {
            val file = it["file"] as? String ?: error("missing profile file")
            parse(loader.load("$Directory/$file")).also { profile ->
                require(profile["release"] == release) { "profile release mismatch" }
            }
        }
        val store = GhostlockProfileStore(
            java.io.File(context.filesDir, "ghostlock/user_profiles"),
            loader,
        )
        val imported = store.loadEntry(release, store.selected(context))
        require(builtin != null || imported != null) { "no GhostLock profile for kernel: $release" }
        val tuning = parse(loader.load("$Directory/execution-tuning.conf"))["execution"].asValueMap()
        val presets = com.ghostlock.app.data.route.RouteKind.entries.mapNotNull { route ->
            parse(loader.load("$Directory/execution-${route.token.replace('_', '-')}.conf"))
                .asValueMap()?.get("execution").asValueMap()?.get("routes").asValueMap()
                ?.get(route.token).asValueMap()?.let { route.token to it }
        }.toMap()
        val merged = ProfileMerger.resolveMerged(
            deviceRelease = release,
            builtin = builtin,
            imported = imported,
            overrides = null,
            tuningExecution = tuning,
            pair = CpuPairView(mainCpu, consumerCpu),
            routePresets = presets,
        )
        val route = routeName(merged)
        val fallback = (merged["fallback"].asValueMap()?.get("to") as? String)
        val errors = ProfileResolver.validateMerged(merged, route, fallback)
        require(errors.isEmpty()) { errors.joinToString { "${it.fieldPath}: ${it.message}" } }
        val document = NativeProfileDocument.from(release, route, fallback) { path ->
            ProfileResolver.nativeValue(merged, route, fallback, path)
        }
        val bytes = document.toBinary()
        check(NativeProfileDocument.fromBinary(bytes) != null) { "GLK1 round trip failed" }
        return ResolvedGhostlockProfile(release, bytes, document.recommendShizuku == 1u)
    }

    private fun parse(text: String): ValueMap =
        HoconSupport.parseValue(text).asValueMap() ?: error("profile is not an object")

    private fun routeName(profile: ValueMap): String? = when (val route = profile["route"]) {
        is String -> route
        is Map<*, *> -> route.keys.filterIsInstance<String>().firstOrNull {
            com.ghostlock.app.data.route.RouteKind.fromToken(it) != null
        }
        else -> null
    }
}
```

Add these explicit selection functions to the store; never auto-select the newest import. `ConfigError.fieldPath` and `ConfigError.message` are the pinned upstream properties used above.

```kotlin
fun selected(context: Context): String? =
    context.getSharedPreferences("ghostlock_profiles", Context.MODE_PRIVATE)
        .getString("selected_profile", null)?.takeIf(String::isNotBlank)

fun select(context: Context, name: String?) {
    context.getSharedPreferences("ghostlock_profiles", Context.MODE_PRIVATE)
        .edit().apply {
            if (name.isNullOrBlank()) remove("selected_profile") else putString("selected_profile", name)
        }.commit()
}
```

- [ ] **Step 5: Run resolver and upstream profile tests**

```bash
cd zsu/manager
./gradlew :profile-core:test :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.GhostlockProfileConfigurationTest'
```

Expected: PASS, including exact release, CPU encoding, template rejection, and GLK1 round trip.

- [ ] **Step 6: Commit**

```bash
git add manager/app/src/main/java/com/rifsxd/ksunext/ghostlock manager/app/src/test/java/com/rifsxd/ksunext/ghostlock
git commit -m "ghostlock: resolve validated GLK1 profiles"
```

---

### Task 4: Implement framed process transport and durable status

**Files:**
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunState.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/RunStateCodec.kt` copied from upstream without codec changes
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProcess.kt`
- Test: `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/GhostlockRunStateTest.kt`
- Test: `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/GhostlockProcessTest.kt`

**Interfaces:**
- Produces: `GhostlockProcess.run(binary: File, ksud: File, profile: ByteArray, workDir: File, onLog: (String) -> Unit, onStatus: (String, String) -> Unit): GhostlockProcess.Result`.
- Produces: `Result(exitCode: Int, timedOut: Boolean, output: String)`; `exitCode == 0 && !timedOut` is success.
- Produces: `GhostlockRunState.apply(step, status)`, `clear()`, and `stuckStep()` with upstream step order.

- [ ] **Step 1: Write failing framing/status tests**

```kotlin
private class CloseTrackingOutputStream : OutputStream() {
    private val delegate = ByteArrayOutputStream()
    var closed = false
    override fun write(value: Int) = delegate.write(value)
    override fun close() { closed = true }
    fun bytes(): ByteArray = delegate.toByteArray()
}

@Test fun frameIsBigEndianAndLeavesChannelOpen() {
    val payload = byteArrayOf(1, 2, 3, 4)
    val output = CloseTrackingOutputStream()
    GhostlockProcess.writeFrame(output, payload)
    assertArrayEquals(byteArrayOf(0, 0, 0, 4, 1, 2, 3, 4), output.bytes())
    assertFalse(output.closed)
}

@Test fun emptyFrameHasZeroLengthAndStaysOpen() {
    val output = CloseTrackingOutputStream()
    GhostlockProcess.writeFrame(output, ByteArray(0))
    assertArrayEquals(byteArrayOf(0, 0, 0, 0), output.bytes())
    assertFalse(output.closed)
}

@Test fun oversizedFrameIsRejectedBeforeWriting() {
    val output = CloseTrackingOutputStream()
    assertThrows(IllegalArgumentException::class.java) {
        GhostlockProcess.writeFrame(output, ByteArray(1024 * 1024 + 1))
    }
    assertEquals(0, output.bytes().size)
}

@Test fun statusLinesAreConsumedAndAcknowledged() {
    val output = ByteArrayOutputStream()
    val events = mutableListOf<Pair<String, String>>()
    assertTrue(GhostlockProcess.handleStatusLine("\u001eGLK_STATUS w1a in_progress", output) { step, status ->
        events += step to status
    })
    assertEquals(listOf("w1a" to "in_progress"), events)
    assertEquals("\u001eGLK_STATUS_ACK\n", output.toString(Charsets.UTF_8))
}

@Test fun disabledStatusClearsStateWithoutAcknowledgement() {
    val output = ByteArrayOutputStream()
    val events = mutableListOf<Pair<String, String>>()
    assertTrue(GhostlockProcess.handleStatusLine("\u001eGLK_STATUS_DISABLED", output) { step, status ->
        events += step to status
    })
    assertEquals(listOf("" to "disabled"), events)
    assertEquals(0, output.size())
}

@Test fun stuckStepUsesUpstreamOrder() {
    val state = GhostlockRunState(File.createTempFile("ghostlock-state", ".json"))
    state.reset()
    state.apply("w2a", "in_progress")
    state.apply("w1a", "in_progress")
    assertEquals("w1a", state.stuckStep())
}
```

The close-tracking delegate proves the production guarantee that `writeFrame` never calls `close` or `use`; no test-only open flag is added to production code.

- [ ] **Step 2: Run tests and confirm failure**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.Ghostlock*Test'
```

Expected: FAIL because transport and run-state types do not exist.

- [ ] **Step 3: Implement run state and process transport**

`GhostlockRunState` must contain these exact constants and state transitions:

```kotlin
internal class GhostlockRunState(private val file: File) {
    private val lock = Any()
    private val steps = linkedMapOf<String, String>()

    fun reset() = synchronized(lock) {
        Steps.forEach { steps[it] = NotStarted }
        persistLocked()
    }

    fun apply(step: String, status: String) = synchronized(lock) {
        if (step in steps) {
            steps[step] = status
            persistLocked()
        }
    }

    fun clear() {
        file.delete()
    }

    fun stuckStep(): String? = synchronized(lock) {
        Steps.firstOrNull { steps[it] == InProgress }
    }

    private fun persistLocked() {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            out.write(RunStateCodec.encode(steps, System.currentTimeMillis()).toByteArray())
            out.flush()
            out.fd.sync()
        }
    }

    companion object {
        val Steps = listOf("w1a", "w1b", "w2a", "w2b", "w3a", "w3b", "w3c")
        const val NotStarted = "not_start"
        const val InProgress = "in_progress"
        const val Completed = "completed"
    }
}
```

Copy upstream `RunStateCodec.kt` as a private ZSU adapter so the JSON schema is not rewritten. `GhostlockProcess` must use these exact protocol functions and lifecycle:

```kotlin
internal object GhostlockProcess {
    private const val TimeoutSeconds = 300L
    private const val StatusMarker = "\u001eGLK_STATUS"
    private const val StatusAck = "\u001eGLK_STATUS_ACK\n"
    private const val StatusDisabled = "\u001eGLK_STATUS_DISABLED"

    data class Result(val exitCode: Int, val timedOut: Boolean, val output: String)

    @Throws(IOException::class)
    fun writeFrame(output: OutputStream, payload: ByteArray) {
        require(payload.size <= 1024 * 1024) { "GhostLock profile exceeds 1 MiB" }
        val length = payload.size
        output.write(byteArrayOf(
            (length ushr 24).toByte(), (length ushr 16).toByte(),
            (length ushr 8).toByte(), length.toByte(),
        ))
        output.write(payload)
        output.flush()
    }

    fun handleStatusLine(
        line: String,
        output: OutputStream,
        onStatus: (String, String) -> Unit,
    ): Boolean {
        if (!line.startsWith(StatusMarker)) return false
        if (line.contains(StatusDisabled)) {
            onStatus("", "disabled")
            return true
        }
        val parts = line.removePrefix(StatusMarker).trim().split(' ')
        if (parts.size >= 2) {
            onStatus(parts[0], parts[1])
            output.write(StatusAck.toByteArray(Charsets.UTF_8))
            output.flush()
        }
        return true
    }
}
```

The `run` implementation must start with `--ghostlock-app-call --enable-status-record`, set `GHOSTLOCK_HOME`, `TMPDIR`, `HOME`, `GHOSTLOCK_KSUD`, and a unique `GHOSTLOCK_KSU_LOG`, redirect merged native output to `.ghostlock_native.log`, call `writeFrame` without closing stdin, and tail both files using `RandomAccessFile` offsets that advance only after newline-complete lines. Wait 300 seconds; on timeout call `destroy()`, wait five seconds, then `destroyForcibly()`. Interrupt and join tailers, perform one final complete-line drain, close streams, and include native plus KernelSU logs in `Result.output`. Preserve exit 137 and signal-aware formatting in ZSU's runner.

- [ ] **Step 4: Run transport and native status tests**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.Ghostlock*Test'
cd ..
make -C ghostlock native-host-tests
```

Expected: Kotlin framing/status tests and upstream native `run_state_test` pass.

- [ ] **Step 5: Commit**

```bash
git add manager/app/src/main/java/com/rifsxd/ksunext/ghostlock manager/app/src/test/java/com/rifsxd/ksunext/ghostlock
git commit -m "ghostlock: add framed native process transport"
```

---

### Task 5: Replace direct execution and preserve automatic-run safeguards

**Files:**
- Replace: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunner.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockOperationLock.kt`
- Test: `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/GhostlockRunnerContractTest.kt`

**Interfaces:**
- Preserves: `GhostlockRunner.run(context, onOutput): GhostlockRunner.Result` for direct/manual and automatic runs.
- Preserves: `GhostlockRunner.isKernelSupported(context, release)`.
- Produces: process-wide `GhostlockOperationLock.tryAcquire(): Closeable?` used by direct, Shizuku, and automatic paths.

- [ ] **Step 1: Add failing source/runtime contracts**

```kotlin
@Test fun directRunUsesZsuDaemonAndAppCall() {
    val source = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunner.kt").readText()
    assertTrue(source.contains("getKsuDaemonFile()"))
    assertFalse(source.contains("GHOSTLOCK_CORE"))
    assertFalse(source.contains("GHOSTLOCK_CONSUMER_CORE"))
    assertFalse(source.contains("\"--profile\""))
}

@Test fun failedRunHasNoRetryLoop() {
    val runner = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunner.kt").readText()
    val process = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProcess.kt").readText()
    assertFalse(runner.contains("runOnce(context"))
    assertFalse(Regex("for\\s*\\(.*attempt").containsMatchIn(process))
}
```

- [ ] **Step 2: Run the contract and watch it fail on the old runner**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.GhostlockRunnerContractTest'
```

Expected: FAIL on obsolete CPU variables, `--profile`, or the old `runOnce` wrapper.

- [ ] **Step 3: Implement the direct adapter**

Use this exact run sequence in `GhostlockRunner.run`:

```kotlin
fun run(context: Context, onOutput: (String) -> Unit = {}): Result {
    GhostlockOperationLock.tryAcquire()?.use {
        val workDir = File(context.filesDir, "ghostlock")
        if (!workDir.isDirectory && !workDir.mkdirs()) {
            return Result(false, false, "Unable to create GhostLock working directory")
        }
        val binary = File(context.applicationInfo.nativeLibraryDir, "libghostlock.so")
        val ksud = getKsuDaemonFile()
        if (!binary.isFile) return Result(false, false, "GhostLock payload is not present in this APK")
        if (!ksud.isFile) return Result(false, false, "ZSU ksud is not present in this APK")
        val release = GhostlockKernelRelease.current()
        val pair = GhostlockCpuPairCatalog.selected(context, release)
        val profile = try {
            GhostlockProfileConfiguration.resolve(context, release, pair.main, pair.consumer)
        } catch (error: Exception) {
            return Result(false, false, "Unable to resolve GhostLock profile: ${error.message}")
        }
        val runState = GhostlockRunState(File(workDir, ".ghostlock_run_state.json"))
        runState.reset()
        val native = try {
            GhostlockProcess.run(binary, ksud, profile.bytes, workDir, onOutput) { step, status ->
                if (status == "disabled") runState.clear() else runState.apply(step, status)
            }
        } catch (error: Exception) {
            return Result(false, false, "Unable to start GhostLock: ${error.message}")
        }
        val prefix = when {
            native.timedOut -> "GhostLock timed out after 300 seconds"
            native.exitCode == 137 -> "GhostLock was killed by SIGKILL (137), usually Android memory/process pressure; reboot before retrying"
            native.exitCode > 128 -> "GhostLock was terminated by signal ${native.exitCode - 128} (exit ${native.exitCode})"
            else -> "GhostLock exited with code ${native.exitCode}"
        }
        val output = listOf(prefix, native.output).filter(String::isNotBlank).joinToString("\n")
        runCatching { File(workDir, "last-run.log").writeText(output) }
        return Result(!native.timedOut && native.exitCode == 0, native.timedOut, output)
    }
    return Result(false, false, "Another GhostLock operation is already running")
}
```

The process lock uses one `ReentrantLock(true)` and returns `null` when `tryLock()` fails. The closeable implementation unlocks exactly once. Auto Ghostlock continues calling the same direct `run`; its existing failure/disable behavior must remain unchanged.

- [ ] **Step 4: Run direct and Auto Ghostlock regressions**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.*'
cd ..
python ../scripts/test_engineering_regressions.py
python ghostlock/tools/verify_upstream_sync.py --source ../references/ghostlock-app --root . --runtime-only
```

Expected: all GhostLock tests and all existing engineering regressions pass, and the runtime-only parity gate confirms that the old JSON/CPU launch contract is absent. No automatic retry or changed opt-in behavior is introduced.

- [ ] **Step 5: Commit**

```bash
git add manager/app/src/main/java/com/rifsxd/ksunext/ghostlock manager/app/src/test/java/com/rifsxd/ksunext/ghostlock
git commit -m "ghostlock: migrate direct execution to GLK1"
```

---

### Task 6: Add explicit Shizuku execution with ZSU-first manager resolution

**Files:**
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockManagerScanner.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockShizukuRunner.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockUserService.kt`
- Create: `manager/app/src/main/aidl/com/rifsxd/ksunext/ghostlock/IGhostlockCallback.aidl`
- Create: `manager/app/src/main/aidl/com/rifsxd/ksunext/ghostlock/IGhostlockStatusCallback.aidl`
- Create: `manager/app/src/main/aidl/com/rifsxd/ksunext/ghostlock/IGhostlockUserService.aidl`
- Modify: `manager/app/src/main/AndroidManifest.xml`
- Modify: `manager/app/build.gradle.kts`
- Test: `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/GhostlockManagerScannerTest.kt`

**Interfaces:**
- Produces: `class GhostlockShizukuRunner(private val context: Context)` with blocking `run(profile: ByteArray, managerPackage: String?, onLog: (String) -> Unit, onStatus: (String, String) -> Unit): GhostlockShizukuRunner.Result`; callers invoke it from `Dispatchers.IO`/`runInterruptible`.
- Produces: `data class Result(val exitCode: Int, val timedOut: Boolean)` so service timeouts remain distinguishable from ordinary nonzero exits.
- Produces: asynchronous AIDL callbacks; native execution never blocks on Binder log delivery.
- Consumes: Task 4 `GhostlockProcess.run` and Task 5 operation lock.

- [ ] **Step 1: Write manager-order and no-fallback tests**

```kotlin
@Test fun zsuIsFirstAndExplicitChoiceWins() {
    assertEquals("com.zsu.zsu", GhostlockManagerScanner.resolvePackages(null).first())
    assertEquals(
        "org.bakasu.bakasu",
        GhostlockManagerScanner.resolvePackages("org.bakasu.bakasu").first(),
    )
    assertEquals(
        "com.zsu.zsu",
        GhostlockManagerScanner.resolvePackages("org.bakasu.bakasu")[1],
    )
}

@Test fun duplicateManagersAreRemoved() {
    val packages = GhostlockManagerScanner.resolvePackages("com.zsu.zsu")
    assertEquals(packages.size, packages.distinct().size)
}
```

- [ ] **Step 2: Add dependencies, AIDL, and manifest declarations**

```kotlin
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")
```

```aidl
// IGhostlockCallback.aidl
package com.rifsxd.ksunext.ghostlock;
oneway interface IGhostlockCallback {
    void onLog(String line);
    void onComplete(int exitCode, boolean timedOut);
}
```

```aidl
// IGhostlockStatusCallback.aidl
package com.rifsxd.ksunext.ghostlock;
interface IGhostlockStatusCallback { void onStatus(String step, String status); }
```

```aidl
// IGhostlockUserService.aidl
package com.rifsxd.ksunext.ghostlock;
import com.rifsxd.ksunext.ghostlock.IGhostlockCallback;
import com.rifsxd.ksunext.ghostlock.IGhostlockStatusCallback;
interface IGhostlockUserService {
    void destroy() = 16777114;
    void runExploit(in byte[] profileBlob, @nullable String managerPackage,
        IGhostlockCallback callback, IGhostlockStatusCallback statusCallback) = 2;
}
```

Add the Shizuku provider with `${applicationId}.shizuku`, non-exported `GhostlockUserService`, `moe.shizuku.privileged.api` in `<queries>`, and the upstream manager packages plus `com.zsu.zsu`. Do not add `QUERY_ALL_PACKAGES` unless manager discovery is shown to require it; built-in package queries are sufficient for the selection list.

- [ ] **Step 3: Implement service and runner**

The service uses `AtomicBoolean` to reject overlap, requires `Process.myUid() == Process.SHELL_UID`, requires `/proc/self/status` to contain `(?m)^Seccomp:\s*0$`, resolves `libksud.so` through `GhostlockManagerScanner.resolvePackages(managerPackage)`, fails if none exists, and invokes `GhostlockProcess.run` on a daemon thread in `/data/local/tmp/zsu-ghostlock`. Its callbacks only forward log/status/completion; cleanup and the 300-second watchdog remain inside the shared transport.

`GhostlockShizukuRunner.run` acquires `GhostlockOperationLock` in the app process before checking/binding Shizuku and holds that same permit until native completion, connection failure, timeout, or interruption/cancellation. The service's separate `AtomicBoolean` protects the service process. The runner is deliberately blocking rather than suspending while it owns the fair `ReentrantLock`, so acquisition and release occur on one IO thread; UI cancellation uses `runInterruptible`, and the interruption handler unbinds and calls `destroy` on the bound service:

```kotlin
data class Result(val exitCode: Int, val timedOut: Boolean)

fun run(
    profile: ByteArray,
    managerPackage: String?,
    onLog: (String) -> Unit,
    onStatus: (String, String) -> Unit,
): Result {
    val permit = GhostlockOperationLock.tryAcquire() ?: run {
        onLog("Another GhostLock operation is already running")
        return Result(1, false)
    }
    permit.use {
        if (!ready()) return Result(1, false)
        repeat(3) { attempt ->
            when (val result = connectAndRunOnce(profile, managerPackage, onLog, onStatus)) {
                is ConnectionResult.Completed -> return result.value
                ConnectionResult.Retryable -> if (attempt < 2) onLog("Retrying Shizuku UserService connection")
            }
        }
        return Result(1, false)
    }
}
```

`ConnectionResult` is a private sealed interface with `Completed(value: Result)` and `Retryable`. `connectAndRunOnce` uses a main-looper `Handler`, `CountDownLatch`, and atomic terminal-state flag. Only connection absence/disconnection before native completion maps to `Retryable`; any native completion maps to `Completed(Result(exitCode, timedOut))`, including nonzero and timeout results. Each handshake is capped at three seconds, while native execution remains bounded by the shared 300-second watchdog. Use `Shizuku.UserServiceArgs(ComponentName(context.packageName, GhostlockUserService::class.java.name)).daemon(false).processNameSuffix("ghostlock").debuggable(false).version(BuildConfig.VERSION_CODE)`. Unbind exactly once on every terminal path. A user-selected Shizuku failure never calls direct `GhostlockRunner.run`.

- [ ] **Step 4: Run tests and manifest/AIDL compilation**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.GhostlockManagerScannerTest' :app:assembleDebug
```

Expected: manager tests pass and AIDL/manifest/APK compilation succeeds. If the NDK or SDK is unavailable, record the exact missing tool and keep the APK gate pending rather than claiming a build.

- [ ] **Step 5: Commit**

```bash
git add manager/app/src/main manager/app/build.gradle.kts
git commit -m "ghostlock: integrate explicit Shizuku execution"
```

---

### Task 7: Add profile import and local/remote OTA extraction

**Files:**
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockExtractor.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/ota/ZipFileUtils.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/ota/OtaPayloadExtractor.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/ota/PayloadBinUtils.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/ota/XzDecoder.kt`
- Create: `manager/app/src/main/java/com/rifsxd/ksunext/ghostlock/ota/HttpRangeReader.kt`
- Create: `manager/ghostlock/tools/extract_rs/**` copied from upstream
- Modify: `manager/app/build.gradle.kts`
- Test: `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/GhostlockProfileStoreTest.kt`

**Interfaces:**
- Produces: `GhostlockExtractor.importProfile(context, uri, displayName): String` returning the stored filename.
- Produces: `GhostlockExtractor.extractLocal(context, input: File): ExtractionResult` and `suspend extractRemote(context, url: String, onLog): ExtractionResult`.
- Produces: `confirmOverwrite(context, pending: ExtractionResult.OverwriteRequired): String` as the only extraction overwrite entry point.
- Consumes: verbatim `GhostlockProfileStore` and the packaged `libextract.so`.

```kotlin
sealed interface ExtractionResult {
    data class Stored(val name: String) : ExtractionResult
    data class OverwriteRequired(
        val targetName: String,
        val text: String,
        val releases: List<String>,
    ) : ExtractionResult
}
```

- [ ] **Step 1: Write failing verbatim-store and path-safety tests**

```kotlin
@Test fun importedTextIsStoredVerbatim() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val loader = GhostlockAssetLoader(context)
    val store = GhostlockProfileStore(File(context.cacheDir, "profiles"), loader)
    val text = "release = \"example\"\nschema_version = 1\n"
    val name = store.save("profile.conf", text)
    assertEquals(text, store.rawText(name))
}

@Test fun unsafeNamesCannotEscapeProfileDirectory() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = GhostlockProfileStore(File(context.cacheDir, "profiles"), GhostlockAssetLoader(context))
    val name = store.save("../../outside.conf", "release = \"example\"\n")
    assertFalse(name.contains("/") || name.contains("\\"))
    assertEquals("example", store.releasesOf(name).single())
}
```

- [ ] **Step 2: Copy pinned OTA/Rust code and add build dependencies**

```bash
mkdir -p zsu/manager/ghostlock/tools
cp -a references/ghostlock-app/tools/extract_rs zsu/manager/ghostlock/tools/extract_rs
```

Copy the five OTA Kotlin files, changing only the package to `com.rifsxd.ksunext.ghostlock.ota`. Add:

```kotlin
implementation("org.apache.commons:commons-compress:1.28.0")
```

Add a `buildGhostlockExtract` task using the pinned Cargo lockfile, `aarch64-linux-android`, and NDK API 34 clang/AR. Its exact command/environment and output are:

```kotlin
commandLine("cargo", "build", "--release", "--locked", "--target", "aarch64-linux-android")
environment("CC_aarch64_linux_android", clang34.absolutePath)
environment("AR_aarch64_linux_android", llvmAr.absolutePath)
environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", clang34.absolutePath)
environment("RUSTFLAGS", "-C force-unwind-tables=no -C link-arg=-Wl,--icf=all")
outputs.file(ghostlockDir.resolve("build/extract/aarch64-linux-android/release/ghostlock-extract"))
```

Set Cargo's target directory to `manager/ghostlock/build/extract`, track `tools/extract_rs/src/**`, `Cargo.toml`, and `Cargo.lock` as inputs, package the output as `libextract.so` under generated `arm64-v8a` JNI libraries, and make native merge tasks depend on both native binaries. Resolve `clang34`/`llvmAr` from the configured NDK's host prebuilt bin directory, including Windows `.cmd`/`.exe` suffixes.

- [ ] **Step 3: Implement import/extract adapter**

`importProfile` reads the content URI as UTF-8 with a 1 MiB limit, parses entries through `GhostlockProfileStore.parseEntries`, requires at least one nonempty `release`, and saves the original bytes verbatim. It does not select the imported document automatically.

Local extraction starts `libextract.so <input> --format conf --out <private-output> --work-dir <private-work>`. `extractRemote` is suspend because it first calls `OtaPayloadExtractor.extractPartitions(url, workDir, onLog)` and passes `ExtractedPartitions.bootFile` to the same command; `xblConfigFile` is cleanup state, not a substitute boot input. Use the shared bounded-process utility with an 1800-second extraction timeout and captured output because extraction is outside the native race path. On exit 0, parse and validate the output before saving; otherwise throw an error containing extractor exit/log output. Delete temporary boot, xbl, output, and parsed files in `finally`.

Map the store result explicitly so no overlap is silently written:

```kotlin
private fun storeExtracted(
    store: GhostlockProfileStore,
    text: String,
): ExtractionResult {
    val entries = store.parseEntries(text)
    val releases = entries.mapNotNull { (it["release"] as? String)?.takeIf(String::isNotBlank) }
    require(releases.isNotEmpty()) { "extracted profile has no release" }
    val existing = releases.firstNotNullOfOrNull(store::findFileForRelease)
    return if (existing != null) {
        ExtractionResult.OverwriteRequired(existing.name, text, releases)
    } else {
        ExtractionResult.Stored(store.save("extracted.conf", text))
    }
}
```

`confirmOverwrite` accepts only `OverwriteRequired`, revalidates `text`, calls `store.overwrite(targetName, text)`, and throws if the target disappeared or the write failed. The UI must call it only from an explicit confirmation dialog; neither extraction nor confirmation automatically selects the document.

- [ ] **Step 4: Run store, extractor, and Rust tests**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.GhostlockProfileStoreTest'
cargo test --manifest-path ghostlock/tools/extract_rs/Cargo.toml --locked
```

Expected: store tests pass and the pinned Rust test suite passes without modifying its source or lockfile.

- [ ] **Step 5: Commit**

```bash
git add manager/app/src/main manager/app/src/test manager/app/build.gradle.kts manager/ghostlock/tools/extract_rs manager/ghostlock/upstream-sync.json
git commit -m "ghostlock: add profile import and extraction"
```

---

### Task 8: Connect the existing ZSU GhostLock screen

**Files:**
- Modify: `manager/app/src/main/java/com/rifsxd/ksunext/ui/screen/Ghostlock.kt`
- Modify: `manager/app/src/main/res/values/strings.xml`
- Test: `manager/app/src/test/java/com/rifsxd/ksunext/ghostlock/GhostlockUiContractTest.kt`

**Interfaces:**
- Consumes: direct `GhostlockRunner.run`, Shizuku runner, import/store/extract adapters, and existing CPU/confirmation components.
- Produces: one user-selected execution mode per manual run; automatic runs always use direct execution.

- [ ] **Step 1: Add failing UI safety contracts**

```kotlin
@Test fun automaticAndDirectActionsRemainDistinctFromShizuku() {
    val source = File("src/main/java/com/rifsxd/ksunext/ui/screen/Ghostlock.kt").readText()
    assertTrue(source.contains("GhostlockShizukuRunner"))
    assertTrue(source.contains("GhostlockRunner.run"))
    assertFalse(Regex("onFailure[\\s\\S]{0,200}GhostlockRunner\\.run").containsMatchIn(source))
}
```

- [ ] **Step 2: Add explicit controls and operation wiring**

Keep the existing screen and add:

```kotlin
enum class GhostlockExecutionMode { DIRECT, SHIZUKU }

// In BlRootOperationStore.start(context, mode), resolve the same profile once.
val release = GhostlockKernelRelease.current()
val cpuPair = GhostlockCpuPairCatalog.selected(appContext, release)
val profile = GhostlockProfileConfiguration.resolve(appContext, release, cpuPair.main, cpuPair.consumer)
val runState = GhostlockRunState(File(appContext.filesDir, "ghostlock/.ghostlock_run_state.json"))
runState.reset()
fun postStatus(line: String) {
    scope.launch(Dispatchers.Main.immediate) {
        if (isRunning.value) {
            nativeStatus.value = line.take(180)
            renderProgress(appContext)
        }
    }
}
val result = when (mode) {
    GhostlockExecutionMode.DIRECT -> GhostlockRunner.run(appContext, profile) { line -> postStatus(line) }
    GhostlockExecutionMode.SHIZUKU -> {
        val shizuku = runInterruptible {
            GhostlockShizukuRunner(appContext).run(
                profile.bytes,
                GhostlockManagerScanner.selected(appContext),
                { line -> postStatus(line) },
                { step, status ->
                    if (status == "disabled") runState.clear() else runState.apply(step, status)
                },
            )
        }
        val message = if (shizuku.timedOut) {
            "GhostLock timed out after 300 seconds"
        } else {
            "GhostLock exited with code ${shizuku.exitCode}"
        }
        GhostlockRunner.Result(shizuku.exitCode == 0 && !shizuku.timedOut, shizuku.timedOut, message)
    }
}
```

Do not resolve twice with different CPUs: expose `GhostlockRunner.run(context, profile: ResolvedGhostlockProfile, onOutput)` and have the original `run(context, onOutput)` delegate to it for automatic callers. Add a Shizuku toggle seeded from `profile.recommendShizuku` once per screen session, with a user override that wins until the screen is recreated. Add built-in/imported profile selection, import and extraction launchers, manager dropdown only when Shizuku is selected, and an overwrite confirmation when requested by Task 7. The selector may choose the exact running release's built-in entry or an imported document containing that release; choosing another kernel's built-in entry never relaxes exact `uname -r` matching.

All launchers call `Dispatchers.IO`; Compose state updates return to `Main.immediate`. Disable every run/import/extract control while an operation owns the route. Keep the existing confirmation dialog immediately before either execution mode. Do not add force-attack controls or an advanced profile editor.

Add concise English strings for mode, Shizuku status, import, extraction, manager, invalid profile, and overwrite confirmation. Reuse existing ZSU error/success/timeout strings where their meaning is unchanged.

- [ ] **Step 3: Run UI and all GhostLock tests**

```bash
cd zsu/manager
./gradlew :app:testDebugUnitTest --tests 'com.rifsxd.ksunext.ghostlock.*' :app:assembleDebug
```

Expected: tests and APK build pass; no direct fallback follows a Shizuku failure and only one operation is enabled.

- [ ] **Step 4: Commit**

```bash
git add manager/app/src/main manager/app/src/test
git commit -m "ghostlock: expose synchronized runtime in ZSU"
```

---

### Task 9: Build packaging, licensing, and synchronization documentation

**Files:**
- Modify: `manager/app/build.gradle.kts`
- Modify: `manager/ghostlock/PROFILE_SYNC.md`
- Create: `manager/ghostlock/VERIFICATION.md`

**Interfaces:**
- Produces: APK containing ARM64 `libghostlock.so`, `libextract.so`, HOCON assets, Shizuku service/provider, and Apache-2.0 notices.
- Produces: reproducible verification record with explicit pending items.

- [ ] **Step 1: Verify native Gradle wiring and package both binaries**

Verify that Task 1 already points `buildGhostlockNative` at `make ghostlock`, tracks `src/Makefile`, `src/.clang-tidy`, and all native C/C++ inputs, and reads `ghostlock/build/native/ghostlock`. Complete the package copy to generated `arm64-v8a/libghostlock.so`; strip only that packaged copy with the NDK `llvm-strip` and retain the unstripped `build/native/ghostlock` for disassembly. Verify that Task 7's extractor output is copied as `libextract.so`, and attach both packaging tasks to native merge tasks rather than JVM-only unit tests. Do not modify `LICENSE-APACHE-2.0`: its SHA-256 already matches upstream (`1eb85fc97224598dad1852b5d6483bbcf0aa8608790dcc657a5a2a761ae9c8c6`).

- [ ] **Step 2: Rewrite synchronization notes with exact evidence**

`PROFILE_SYNC.md` must state the pinned revision, 79/69/10 catalog counts, GLK1 v2 replacement, removed JSON/CPU environment contract, ZSU-own-ksud rule, SOG10 pending gate, and the command used for hash verification. Remove claims about the deleted native JSON loader and old host-test targets.

`VERIFICATION.md` uses this exact result table and records actual outputs, never anticipated passes:

```markdown
| Gate | Command/device | Result | Evidence |
|---|---|---|---|
| Native host tests (26) | `make -C manager/ghostlock native-host-tests` | PENDING | |
| Host attack dataflow | `make -C manager/ghostlock host-attack-dataflow-test` | PENDING | |
| Profile core | `./gradlew :profile-core:test` | PENDING | |
| Android unit tests | `./gradlew :app:testDebugUnitTest` | PENDING | |
| ARM64 native/APK build | `./gradlew :app:assembleDebug` | PENDING | |
| lint-tidy | `make -C manager/ghostlock lint-tidy` | PENDING | |
| Eight-function disassembly | `python manager/ghostlock/tools/cmp_disasm.py <baseline> manager/ghostlock/build/native/ghostlock` | PENDING | |
| Cold-boot direct gate | Device/release/route | PENDING | |
| Cold-boot Shizuku gate | Device/release/route | PENDING | |
```

- [ ] **Step 3: Inspect the actual APK**

```bash
cd zsu/manager
./gradlew :app:assembleDebug
python -c 'import zipfile,glob; p=glob.glob("app/build/outputs/apk/debug/*.apk")[0]; z=zipfile.ZipFile(p); names=z.namelist(); assert "lib/arm64-v8a/libghostlock.so" in names; assert "lib/arm64-v8a/libextract.so" in names; assert "assets/kernel_profiles/index.conf" in names; assert "assets/kernel_profiles/index.json" not in names; count=len([n for n in names if n.startswith("assets/kernel_profiles/") and n.endswith(".conf")]); assert count == 89, count; print(p, count)'
```

Expected: both ARM64 libraries and HOCON assets are present, `index.json` is absent, and exactly 89 `.conf` files are present (79 devices + 4 templates + 6 shared/index files).

- [ ] **Step 4: Commit**

```bash
git add manager/app/build.gradle.kts manager/ghostlock
git commit -m "ghostlock: document and verify synchronized packaging"
```

---

### Task 10: Final parity, disassembly, and device-evidence gate

**Files:**
- Modify: `manager/ghostlock/VERIFICATION.md`
- Modify: `docs/superpowers/specs/2026-11-25-ghostlock-upstream-synchronization-design.md` status only after written/device acceptance
- No production changes unless a failing gate identifies a specific defect with its own failing regression test

**Interfaces:**
- Consumes: every prior task.
- Produces: final evidence matrix; pending device/toolchain gates remain explicitly pending.

- [ ] **Step 1: Run all available host and repository gates**

From `zsu/manager`:

```bash
./gradlew :profile-core:test :profile-core:exportKernelProfiles :app:testDebugUnitTest :app:assembleDebug
cd ..
make -C ghostlock native-host-tests
make -C ghostlock host-attack-dataflow-test
python ../scripts/test_engineering_regressions.py
node ../scripts/validate-jailbreak-manager-config.mjs
git diff --check
git ls-files '*.sh' -z | xargs -0 -n1 bash -n
```

Expected: every command exits zero. If a required SDK/NDK/host C++ toolchain is absent, record the exact blocker and do not mark that gate passed.

- [ ] **Step 2: Run source parity and zero-warning static checks**

```bash
python ghostlock/tools/verify_upstream_sync.py --source ../references/ghostlock-app --root .
make -C ghostlock lint-tidy
cargo test --manifest-path ghostlock/tools/extract_rs/Cargo.toml --locked
```

Expected: pinned hashes and all 79 profiles match; lint/tidy and locked extractor tests pass.

- [ ] **Step 3: Build an upstream baseline and compare all eight attack functions**

```bash
make -C ../references/ghostlock-app/src clean ghostlock
make -C ghostlock clean ghostlock
python ghostlock/tools/cmp_disasm.py ../references/ghostlock-app/build/native/ghostlock ghostlock/build/native/ghostlock
```

Expected: every target is `IDENTICAL (strict)`. If the same pinned source/toolchain produces an annotation-only difference, stop and document function, bytes, relative order, and reviewed justification; do not claim strict identity.

- [ ] **Step 4: Record device status truthfully**

For each available supported device, start from a cold boot, fix the CPU pair, execute one route without any parallel attempt, and archive native, KernelSU, run-state, exit, and reboot evidence. Test direct and Shizuku separately. Verify success arms but does not enable Auto Ghostlock, full boot schedules one attempt, soft reboot schedules none, and failure disables it.

Without an attached compatible device, leave every device row `PENDING` and state: `Source/host verification completed where available; on-device behavior is not verified.`

- [ ] **Step 5: Review and commit final evidence**

```bash
git diff --check
git status --short
git add manager/ghostlock/VERIFICATION.md
git commit -m "ghostlock: record synchronization verification"
```

Present the final diff, commits, test evidence, and pending device gates. Do not push or release until the user reviews and explicitly authorizes that operation.
