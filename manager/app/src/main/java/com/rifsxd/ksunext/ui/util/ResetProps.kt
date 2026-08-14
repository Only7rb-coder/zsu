package com.rifsxd.ksunext.ui.util

import com.topjohnwu.superuser.CallbackList
import java.util.ArrayList

/**
 * Standalone implementation of the deterministic runtime behavior from the
 * Sensitive Props module. It runs directly through ZSU's KernelSU root shell;
 * no module installation or module action is required.
 */
object ResetProps {
    data class Result(
        val success: Boolean,
        val changed: Int,
        val output: List<String>
    )

    private val script = """
        set -u
        RP="${'$'}(command -v resetprop 2>/dev/null || true)"
        for candidate in /data/adb/ksu/bin/resetprop /data/adb/magisk/resetprop /sbin/resetprop; do
            if [ -z "${'$'}RP" ] && [ -x "${'$'}candidate" ]; then RP="${'$'}candidate"; fi
        done
        if [ -z "${'$'}RP" ]; then
            echo "resetprop is not available in the KernelSU root environment"
            exit 127
        fi

        changed=0
        check_resetprop() {
            name="${'$'}1"
            value="${'$'}2"
            current="${'$'}(${'$'}RP -v "${'$'}name" 2>/dev/null || true)"
            if [ -n "${'$'}current" ] && [ "${'$'}current" != "${'$'}value" ]; then
                case "${'$'}name" in
                    persist.*) "${'$'}RP" -p -v "${'$'}name" "${'$'}value" >/dev/null 2>&1 ;;
                    *) "${'$'}RP" -n -v "${'$'}name" "${'$'}value" >/dev/null 2>&1 ;;
                esac
                if [ "${'$'}?" -eq 0 ]; then changed=${'$'}((changed + 1)); fi
            fi
        }

        replace_value_resetprop() {
            name="${'$'}1"
            search="${'$'}2"
            replacement="${'$'}3"
            current="${'$'}(${'$'}RP -v "${'$'}name" 2>/dev/null || true)"
            [ -z "${'$'}current" ] && return 0
            updated="${'$'}(printf '%s' "${'$'}current" | sed "s|${'$'}search|${'$'}replacement|g")"
            [ "${'$'}current" = "${'$'}updated" ] && return 0
            case "${'$'}name" in
                persist.*) "${'$'}RP" -p -v "${'$'}name" "${'$'}updated" >/dev/null 2>&1 ;;
                *) "${'$'}RP" -n -v "${'$'}name" "${'$'}updated" >/dev/null 2>&1 ;;
            esac
            if [ "${'$'}?" -eq 0 ]; then changed=${'$'}((changed + 1)); fi
        }

        maybe_resetprop() {
            name="${'$'}1"
            search="${'$'}2"
            replacement="${'$'}3"
            current="${'$'}(${'$'}RP -v "${'$'}name" 2>/dev/null || true)"
            [ -z "${'$'}current" ] && return 0
            case "${'$'}current" in
                *"${'$'}search"*) check_resetprop "${'$'}name" "${'$'}replacement" ;;
            esac
        }

        # Remove custom-ROM marker properties from the live property service.
        # The original module hex-patches Magisk's backing store; a standalone
        # KernelSU action cannot require magiskboot, so it deletes matching live
        # properties directly through resetprop instead.
        sensitive_patterns="LSPosed marketname custom.device modversion lineage aospa pixelexperience evolution pixelos pixelage crdroid crDroid aicp arter97 blu_spark cyanogenmod deathly elementalx franco hadeskernel morokernel noble optimus slimroms sultan aokp bharos calyxos divestos emteria.os grapheneos indus iodéos kali nethunter omnirom paranoid replicant resurrection rising remix shift volla icosa kirisakura infinity Infinity qemu Qemu"
        getprop | sed -n 's/^\[\([^]]*\)\].*/\1/p' | while IFS= read -r prop_name; do
            for marker in ${'$'}sensitive_patterns; do
                case "${'$'}prop_name" in
                    *"${'$'}marker"*)
                        "${'$'}RP" -n --delete "${'$'}prop_name" >/dev/null 2>&1 || true
                        break
                        ;;
                esac
            done
        done

        # Display and build identity cleanup.
        replace_value_resetprop ro.build.flavor "lineage_" ""
        replace_value_resetprop ro.build.flavor "userdebug" "user"
        replace_value_resetprop ro.build.display.id "lineage_" ""
        replace_value_resetprop ro.build.display.id "userdebug" "user"
        replace_value_resetprop ro.build.display.id "dev-keys" "release-keys"
        replace_value_resetprop vendor.camera.aux.packagelist "lineageos." ""
        replace_value_resetprop ro.build.version.incremental "eng." ""

        # Device-vendor fingerprint and lock-state corrections.
        check_resetprop ro.boot.flash.locked 1
        check_resetprop ro.boot.realme.lockstate 1
        check_resetprop ro.boot.realmebootstate green
        check_resetprop ro.boot.vbmeta.device_state locked
        check_resetprop vendor.boot.vbmeta.device_state locked
        check_resetprop ro.is_ever_orange 0
        check_resetprop vendor.boot.verifiedbootstate green
        check_resetprop ro.boot.veritymode enforcing
        check_resetprop ro.boot.verifiedbootstate green
        for prop in ro.boot.warranty_bit ro.warranty_bit ro.vendor.boot.warranty_bit ro.vendor.warranty_bit; do
            check_resetprop "${'$'}prop" 0
        done

        # Build-property normalization for all relevant partitions.
        for prefix in bootimage odm odm_dlkm oem product system system_ext vendor vendor_dlkm; do
            check_resetprop ro.${'$'}{prefix}.build.type user
            check_resetprop ro.${'$'}{prefix}.keys release-keys
            check_resetprop ro.${'$'}{prefix}.build.tags release-keys
            replace_value_resetprop ro.${'$'}{prefix}.build.version.incremental "eng." ""
        done

        # Conditional boot and region adjustments.
        for prop in ro.bootmode ro.boot.bootmode ro.boot.mode vendor.bootmode vendor.boot.bootmode vendor.boot.mode; do
            maybe_resetprop "${'$'}prop" recovery unknown
        done
        for prop in ro.boot.hwc ro.boot.hwcountry; do
            maybe_resetprop "${'$'}prop" CN GLOBAL
        done

        # Compatibility and device-state properties.
        check_resetprop sys.oem_unlock_allowed 0
        check_resetprop ro.oem_unlock_supported 0
        check_resetprop net.tethering.noprovisioning true
        check_resetprop init.svc.flash_recovery stopped
        check_resetprop ro.crypto.state encrypted
        check_resetprop ro.secure 1
        check_resetprop ro.secureboot.devicelock 1
        check_resetprop ro.secureboot.lockstate locked
        check_resetprop ro.force.debuggable 0
        check_resetprop ro.debuggable 0
        check_resetprop ro.adb.secure 1

        # The module sets these values in all three Android settings namespaces.
        for global_setting in hidden_api_policy hidden_api_policy_pre_p_apps hidden_api_policy_p_apps adb_enabled development_settings_enabled tether_dun_required; do
            settings delete global "${'$'}global_setting" >/dev/null 2>&1 || true
        done
        for namespace in global system secure; do
            settings put "${'$'}namespace" block_untrusted_touches 1 >/dev/null 2>&1 || true
        done

        echo "Reset Props completed: ${'$'}changed live properties changed"
    """.trimIndent()

    fun run(): Result {
        val output = ArrayList<String>()
        val callback = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                s?.trim()?.takeIf { it.isNotEmpty() }?.let(output::add)
            }
        }
        return try {
            val result = createRootShell(true).use { shell ->
                shell.newJob().add("sh -c ${'$'}{shellQuote(script)}").to(callback, callback).exec()
            }
            val changed = output.firstOrNull { it.contains("Reset Props completed:") }
                ?.substringAfter("completed:")
                ?.substringBefore("live")
                ?.trim()
                ?.toIntOrNull()
                ?: 0
            Result(result.isSuccess, changed, output.toList())
        } catch (e: Exception) {
            Result(false, 0, output + (e.message ?: "Reset Props failed"))
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
