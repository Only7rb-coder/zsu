#!/bin/bash

set -euo pipefail

# Build and install the ZSU manager APK.
# The Android SDK, NDK, Rust cross toolchain, and signing configuration must
# already be available. Import supported androidX-X.X_kernelsu.ko drivers into
# userspace/ksud/bin/aarch64 before building an LKM-enabled package.

export BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android="--sysroot=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot -I$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/aarch64-linux-android -I$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include"

CROSS_NO_WARNINGS=0 cross build --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml

mkdir -p manager/app/src/main/jniLibs/arm64-v8a/
cp userspace/ksud/target/aarch64-linux-android/release/ksud manager/app/src/main/jniLibs/arm64-v8a/libzsu.so

cd manager
./setup.sh
cd ..

apks=(manager/app/build/outputs/apk/release/ZSU_*-release.apk)
if [[ ${#apks[@]} -ne 1 || ! -f ${apks[0]} ]]; then
    printf 'Expected exactly one ZSU release APK; found %d\n' "${#apks[@]}" >&2
    exit 1
fi
adb install -r "${apks[0]}"
