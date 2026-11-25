package com.rifsxd.ksunext.ghostlock

import com.ghostlock.app.data.ValueMap
import com.ghostlock.app.data.getLongAt
import com.ghostlock.app.data.route.RouteKind

/** Upstream's Android-side geometry and credential checks, without the editor UI. */
internal object GhostlockProfileGeometry {
    private val Routes = RouteKind.entries.map { it.token }
    private val CommonRequired = listOf(
        "offset.init_task", "offset.init_cred", "offset.root_task_group", "offset.selinux_enforcing",
        "task_struct.prio", "task_struct.pi_lock", "task_struct.pi_waiters", "task_struct.pi_blocked_on",
        "task_struct.cred", "task_struct.seccomp",
    )
    private val MulticastFields = listOf("buffer_size", "task_offset", "lock_offset")

    fun validate(profile: ValueMap, explicitRoute: String?, fallbackTo: String?): List<String> {
        val invalid = mutableSetOf<String>()
        fun value(path: String): Long? = profile.getLongAt(path)
        fun requireNonZero(vararg paths: String) {
            paths.forEach { path ->
                val current = value(path)
                if (current == null || current == 0L) invalid += path
            }
        }
        fun validateCompact(prefix: String) {
            val compact = value("$prefix.compact_waiter")
            if (compact == null || compact == 0L || compact < 0L || compact > 0xffL) {
                invalid += "$prefix.compact_waiter"
            }
        }
        fun validateShift(prefix: String) {
            val shift = value("$prefix.waiter_shift")
            if (shift == null || shift < Int.MIN_VALUE.toLong() || shift > Int.MAX_VALUE.toLong()) {
                invalid += "$prefix.waiter_shift"
            }
        }

        val route = explicitRoute?.takeIf { it in Routes }
        if (route == null) invalid += "route"
        requireNonZero(*CommonRequired.toTypedArray())
        if (value("kernel_major") !in listOf(5L, 6L)) invalid += "kernel_major"

        val copySize = value("cred.copy_size")
        if (copySize == null || copySize == 0L) invalid += "cred.copy_size"
        val usageOffset = value("cred.usage_offset") ?: 0L
        if (copySize != null && usageOffset + SizeofU32 > copySize) invalid += "cred.usage_offset"

        val capsCount = value("cred.caps_count")
        val capsOffset = value("cred.caps_offset")
        if (capsCount == null || capsCount == 0L) invalid += "cred.caps_count"
        if (copySize != null && capsCount != null && capsOffset != null &&
            capsOffset + capsCount * SizeofU64 > copySize
        ) {
            invalid += "cred.caps_offset"
            invalid += "cred.caps_count"
        }

        val refCount = value("cred.ref_count") ?: 0L
        if (refCount > 4) invalid += "cred.ref_count"
        for (index in 0 until 4) {
            if (index >= refCount) break
            val image = value("cred.ref${index}_image")
            if (image == null || image == 0L) invalid += "cred.ref${index}_image"
            val offset = value("cred.ref${index}_offset")
            if (copySize != null && offset != null && offset + SizeofU64 > copySize) {
                invalid += "cred.ref${index}_offset"
            }
        }

        val routePrefix = route?.let { "route.$it" } ?: "route"
        val fallbackPrefix = fallbackTo?.takeIf { it != "none" }?.let { "fallback.route.$it" }
        when (route) {
            "tcp_zerocopy" -> validateCompact(routePrefix)
            "select_stack" -> validateShift(routePrefix)
            "multicast_waiter" -> {
                validateMulticast(routePrefix, invalid, ::value)
                validateCompact(routePrefix)
                requireNonZero("kernelsnitch.mm_struct_sz", "offset.empty_zero_page", "cred.ref_count")
                if (copySize != null && copySize < 0xa0L) invalid += "cred.copy_size"
                val waiterOff = value("$routePrefix.waiter_off")
                if (waiterOff == null || waiterOff <= 0L || waiterOff > Int.MAX_VALUE.toLong()) {
                    invalid += "$routePrefix.waiter_off"
                }
                val bufferSize = value("$routePrefix.buffer_size")
                val lockOffset = value("$routePrefix.lock_offset")
                if (waiterOff != null && waiterOff > 0L && waiterOff <= Int.MAX_VALUE.toLong() &&
                    bufferSize != null && bufferSize in 0L..UInt.MAX_VALUE.toLong() &&
                    lockOffset != null && lockOffset in 0L..UInt.MAX_VALUE.toLong() &&
                    waiterOff + lockOffset + SizeofU64 > bufferSize
                ) {
                    invalid += "$routePrefix.waiter_off"
                    invalid += "$routePrefix.lock_offset"
                    invalid += "$routePrefix.buffer_size"
                }
            }
        }
        if (fallbackTo != null && fallbackTo != "none" && fallbackTo !in Routes) {
            invalid += "fallback.to"
        } else if (fallbackPrefix != null) {
            when (fallbackTo) {
                "tcp_zerocopy" -> validateCompact(fallbackPrefix)
                "select_stack" -> validateShift(fallbackPrefix)
                "multicast_waiter" -> validateMulticast(fallbackPrefix, invalid, ::value)
            }
        }
        return invalid.sorted()
    }

    private fun validateMulticast(
        prefix: String,
        invalid: MutableSet<String>,
        value: (String) -> Long?,
    ) {
        for (field in MulticastFields) {
            val current = value("$prefix.$field")
            if (current == null || current == 0L || current < 0L || current > UInt.MAX_VALUE.toLong()) {
                invalid += "$prefix.$field"
            }
        }
    }

    private const val SizeofU32 = 4L
    private const val SizeofU64 = 8L
}
