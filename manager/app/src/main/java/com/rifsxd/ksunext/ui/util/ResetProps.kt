package com.rifsxd.ksunext.ui.util

import com.topjohnwu.superuser.CallbackList
import java.util.ArrayList

/**
 * Standalone implementation of the deterministic runtime behavior from the
 * Sensitive Props module. It runs directly through ZSU's KernelSU root shell;
 * no module installation or module action is required.
 */
object ResetProps {
    const val PREF_ENABLED = "reset_props_enabled"

    data class Result(
        val success: Boolean,
        val changed: Int,
        val failed: Int,
        val output: List<String>
    )

    private val script = """
        set -u
        RP="${'$'}(for candidate in /data/adb/ksu/bin/resetprop /sbin/resetprop /data/adb/magisk/resetprop; do
            if [ -x "${'$'}candidate" ]; then printf '%s' "${'$'}candidate"; break; fi
        done)"
        if [ -z "${'$'}RP" ]; then RP="${'$'}(command -v resetprop 2>/dev/null || true)"; fi
        if [ -z "${'$'}RP" ]; then
            echo "resetprop is not available in the KernelSU root environment"
            exit 127
        fi
        echo "Using resetprop: ${'$'}RP"

        changed=0
        failed=0

        read_prop() {
            getprop "${'$'}1" 2>/dev/null | tr -d '\r'
        }

        write_prop() {
            name="${'$'}1"
            value="${'$'}2"
            before="${'$'}(read_prop "${'$'}name")"
            [ -z "${'$'}before" ] && return 0
            [ "${'$'}before" = "${'$'}value" ] && return 0

            if case "${'$'}name" in
                persist.*) "${'$'}RP" -n -p "${'$'}name" "${'$'}value" >/dev/null 2>&1 ;;
                *) "${'$'}RP" -n "${'$'}name" "${'$'}value" >/dev/null 2>&1 ;;
            esac
            then
                after="${'$'}(read_prop "${'$'}name")"
                if [ "${'$'}after" = "${'$'}value" ]; then
                    changed=${'$'}((changed + 1))
                else
                    failed=${'$'}((failed + 1))
                    echo "Verification failed: ${'$'}name remained '${'$'}after'"
                fi
            else
                failed=${'$'}((failed + 1))
                echo "Write failed: ${'$'}name"
            fi
        }

        delete_prop() {
            name="${'$'}1"
            before="${'$'}(read_prop "${'$'}name")"
            [ -z "${'$'}before" ] && return 0
            if "${'$'}RP" -n -d "${'$'}name" >/dev/null 2>&1 && [ -z "${'$'}(read_prop "${'$'}name")" ]; then
                changed=${'$'}((changed + 1))
            else
                failed=${'$'}((failed + 1))
                echo "Delete failed: ${'$'}name"
            fi
        }

        replace_value() {
            name="${'$'}1"
            search="${'$'}2"
            replacement="${'$'}3"
            before="${'$'}(read_prop "${'$'}name")"
            [ -z "${'$'}before" ] && return 0
            after="${'$'}(printf '%s' "${'$'}before" | sed "s|${'$'}search|${'$'}replacement|g")"
            [ "${'$'}before" = "${'$'}after" ] && return 0
            write_prop "${'$'}name" "${'$'}after"
        }

        maybe_replace=""
        maybe_resetprop() {
            name="${'$'}1"
            search="${'$'}2"
            replacement="${'$'}3"
            current="${'$'}(read_prop "${'$'}name")"
            [ -z "${'$'}current" ] && return 0
            case "${'$'}current" in
                *"${'$'}search"*) write_prop "${'$'}name" "${'$'}replacement" ;;
            esac
        }

        # Remove custom-ROM marker properties from the live property service.
        # The original module hex-patches Magisk's backing store; a standalone
        # KernelSU action cannot require magiskboot, so delete matching live
        # properties directly through resetprop instead.
        sensitive_patterns="LSPosed marketname custom.device modversion lineage aospa pixelexperience evolution pixelos pixelage crdroid crDroid aicp arter97 blu_spark cyanogenmod deathly elementalx franco hadeskernel morokernel noble optimus slimroms sultan aokp bharos calyxos divestos emteria.os grapheneos indus iodéos kali nethunter omnirom paranoid replicant resurrection rising remix shift volla icosa kirisakura infinity Infinity qemu Qemu"
        for prop_name in ${'$'}(getprop | sed -n 's/^\[\([^]]*\)\].*/\1/p'); do
            for marker in ${'$'}sensitive_patterns; do
                case "${'$'}prop_name" in
                    *"${'$'}marker"*) delete_prop "${'$'}prop_name"; break ;;
                esac
            done
        done

        # Display and build identity cleanup.
        replace_value ro.build.flavor "lineage_" ""
        replace_value ro.build.flavor "userdebug" "user"
        replace_value ro.build.display.id "lineage_" ""
        replace_value ro.build.display.id "userdebug" "user"
        replace_value ro.build.display.id "dev-keys" "release-keys"
        replace_value vendor.camera.aux.packagelist "lineageos." ""
        replace_value ro.build.version.incremental "eng." ""

        # Device-vendor fingerprint and lock-state corrections.
        write_prop ro.boot.flash.locked 1
        write_prop ro.boot.realme.lockstate 1
        write_prop ro.boot.realmebootstate green
        write_prop ro.boot.vbmeta.device_state locked
        write_prop vendor.boot.vbmeta.device_state locked
        write_prop ro.is_ever_orange 0
        write_prop vendor.boot.verifiedbootstate green
        write_prop ro.boot.veritymode enforcing
        write_prop ro.boot.verifiedbootstate green
        for prop in ro.boot.warranty_bit ro.warranty_bit ro.vendor.boot.warranty_bit ro.vendor.warranty_bit; do
            write_prop "${'$'}prop" 0
        done

        # Build-property normalization for all relevant partitions.
        for prefix in bootimage odm odm_dlkm oem product system system_ext vendor vendor_dlkm; do
            write_prop ro.${'$'}{prefix}.build.type user
            write_prop ro.${'$'}{prefix}.keys release-keys
            write_prop ro.${'$'}{prefix}.build.tags release-keys
            replace_value ro.${'$'}{prefix}.build.version.incremental "eng." ""
        done

        # Conditional boot and region adjustments.
        for prop in ro.bootmode ro.boot.bootmode ro.boot.mode vendor.bootmode vendor.boot.bootmode vendor.boot.mode; do
            maybe_resetprop "${'$'}prop" recovery unknown
        done
        for prop in ro.boot.hwc ro.boot.hwcountry; do
            maybe_resetprop "${'$'}prop" CN GLOBAL
        done

        # Compatibility and device-state properties.
        write_prop sys.oem_unlock_allowed 0
        write_prop ro.oem_unlock_supported 0
        write_prop net.tethering.noprovisioning true
        write_prop init.svc.flash_recovery stopped
        write_prop ro.crypto.state encrypted
        write_prop ro.secure 1
        write_prop ro.secureboot.devicelock 1
        write_prop ro.secureboot.lockstate locked
        write_prop ro.force.debuggable 0
        write_prop ro.debuggable 0
        write_prop ro.adb.secure 1

        # The module sets these values in all three Android settings namespaces.
        for global_setting in hidden_api_policy hidden_api_policy_pre_p_apps hidden_api_policy_p_apps adb_enabled development_settings_enabled tether_dun_required; do
            settings delete global "${'$'}global_setting" >/dev/null 2>&1 || true
        done
        for namespace in global system secure; do
            settings put "${'$'}namespace" block_untrusted_touches 1 >/dev/null 2>&1 || true
        done

        echo "Reset Props completed: ${'$'}changed changed, ${'$'}failed failed"
        [ "${'$'}failed" -eq 0 ]
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
            val summary = output.firstOrNull { it.startsWith("Reset Props completed:") }
            val changed = summary
                ?.substringAfter("completed:")
                ?.substringBefore("changed")
                ?.trim()
                ?.toIntOrNull()
                ?: 0
            val failed = summary
                ?.substringAfter("changed,")
                ?.substringBefore("failed")
                ?.trim()
                ?.toIntOrNull()
                ?: if (result.isSuccess) 0 else 1
            Result(result.isSuccess && failed == 0, changed, failed, output.toList())
        } catch (e: Exception) {
            Result(false, 0, 1, output + (e.message ?: "Reset Props failed"))
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
