# GhostLock profile synchronization

## Source and scope

The profile additions in this branch were translated from `YuKongA/ghostlock-app` at `pre-release` commit `3d4306c`, using its `docs/kernel_profiles/SUPPORTED_DEVICES.md` and profile HOCON assets as the source of device/kernel associations.

Fifteen profiles were added to the ZSU JSON catalog. The native JSON loader consumes ZSU's legacy flat-field format; the app's HOCON groups were mapped to that format, including the TCP/select/multicast route fields and the app's TCP-to-select fallback waiter shift. ZSU's own execution defaults remain authoritative: the standalone app's C++ tuning values were not copied into the older C runtime.

ZSU's native address-space conversion previously hard-coded `P0_PHYS_OFFSET`, even though supported app profiles can carry a different `kernel_phys_offset`. The profile transport, parser, and resolved address state now carry that optional value. Existing profiles omit it and continue to use the old target constant.

## Deliberate exclusions

- `5.15.189-android13-8-00004-g1c3825f8ac0a-ab14110541` (Sony Xperia 1 V SOG10) is excluded because its source profile explicitly says the full device gate is pending.
- `6.1.25-android14-11-maybe-dirty` (MEIZU 21) is excluded from the active catalog because the standalone profile selects `select_stack` without an explicit `compact_waiter` value. The legacy ZSU C path warns that a 6.1 profile without that layout flag will use the 6.6 waiter layout. We did not infer the missing layout bit.

## Verification status

- `make -C manager/ghostlock address-space-host-test profile-catalog-host-test` checks custom/default physical-base translation, index uniqueness and file/release consistency, required profile fields, and every fully resolved profile through the native JSON loader.
- These host checks are necessary but not a substitute for a ZSU build on Android and a real-device gate for each newly enabled profile. No Android SDK or attached device was available in the sandbox during this work. Do not treat this branch as device-validated solely because host checks pass.
