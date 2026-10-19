# GhostLock profile synchronization

## Source and scope

The active catalog is synchronized from `YuKongA/ghostlock-app` at the current `pre-release` profile set through commit `72387d5`, using `docs/kernel_profiles/SUPPORTED_DEVICES.md` and the profile HOCON assets as the source of device/kernel associations.

Eight verified profiles were added to the ZSU JSON catalog:

- Sharp AQUOS R8 SH-R80 — `5.15.178-android13-8-00025-g7feb0b0b7cd8-ab14134651`
- ASUS Zenfone 10 — `5.15.206-android13-8-00056-g3f87b8704e9e-ab15683575`
- BOOX Onyx NoteAir6C — `6.1.134-android14-11-maybe-dirty`
- Redmi Note 14 Pro 5G / POCO X7 — `6.1.138-android14-11-g51f8c580613d-ab13911623`
- Nothing Phone (3a) Pro — `6.1.157-android14-11-g82d681c9b06b-ab14634535`
- MEIZU 21 — `6.1.25-android14-11-maybe-dirty`
- TECNO POVA 7 / LJ6 — `6.12.38-android16-5-gb575a0b6e647-ab14355190-4k`
- vivo X200 Pro — `6.6.30-android15-8-g24ffe62b0a0e-abogki387229724-4k`

The native JSON loader consumes ZSU's legacy flat-field format. The upstream HOCON groups were mapped to that format, including route geometry, physical address overrides, and the TCP/select fallback waiter shift. ZSU's shared execution defaults remain authoritative unless a profile explicitly overrides a value.

ZSU's native address-space conversion carries the optional `kernel_phys_offset` from a profile. Existing profiles omit it and continue to use the old target constant; the imported MTK and Qualcomm profiles retain their measured physical bases.

Profiles may omit the `execution` object because ZSU supplies shared execution defaults. Availability checks must resolve and validate the merged profile, not validate the raw JSON object.

## Deliberate exclusions

- `5.15.189-android13-8-00004-g1c3825f8ac0a-ab14110541` (Sony Xperia 1 V SOG10) remains excluded because its source profile explicitly says the full device gate is pending.
- The MEIZU 21 profile is now enabled because the current upstream profile supplies the compact waiter route geometry required by the legacy ZSU C path.

## Route hang protection

The native PI-race coordinator now enforces `execution.race.route_done_timeout_ms` using `CLOCK_MONOTONIC`. If a kernel route fails to signal completion before the profile deadline, the native process fail-stops instead of waiting indefinitely for the manager's outer watchdog. The default is 30 seconds because the route-specific waits are sub-second to a few seconds; profiles can override it when a device-specific route window is known.

## Verification status

- `make -C manager/ghostlock address-space-host-test profile-catalog-host-test` checks physical-base translation, index uniqueness and file/release consistency, required profile fields, and every fully resolved profile through the native JSON loader.
- The catalog validator resolves profiles without an `execution` object against shared defaults before native loading.
- Host checks are necessary but not a substitute for an Android build and a real-device gate for each newly enabled profile. No Android SDK or attached device was available in the sandbox during this work. Do not treat this branch as device-validated solely because host checks pass.
