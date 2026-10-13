package com.rifsxd.ksunext.ghostlock

import android.content.Context
import android.content.Context.MODE_PRIVATE
import java.io.File

/** The two CPUs GhostLock pins its main and consumer workers to. */
data class GhostlockCpuPair(val main: Int, val consumer: Int) {
    override fun toString(): String = "$main,$consumer"
}

data class GhostlockCpuPairOption(
    val pair: GhostlockCpuPair,
    val label: String,
)

/** Mirrors upstream GhostLock's frequency-clustered CPU-pair picker. */
object GhostlockCpuPairCatalog {
    private const val PREFERENCES = "ghostlock_prefs"
    private const val CPU_PAIR_KEY = "cpu_pair"
    private const val CPU_PAIR_EXPLICIT_KEY = "cpu_pair_explicit"
    private val defaultPair = GhostlockCpuPair(0, 1)
    private val verifiedPairs = mapOf(
        // Upstream's verified Red Magic 8 Pro profile uses a cross-cluster
        // pair. It must not be replaced by the generic 0,1 fallback.
        "5.15.167-android13-8-00017-gb1f32b310a30-ab12826353" to
            GhostlockCpuPair(3, 4),
    )

    fun options(kernelRelease: String? = null): List<GhostlockCpuPairOption> {
        val onlineCpus = parseCpuList(readSysFile("/sys/devices/system/cpu/online"))
        val options = onlineCpus
            .mapNotNull { cpu ->
                readMaxFrequency(cpu).takeIf { it > 0L }?.let { frequency -> cpu to frequency }
            }
            .groupBy({ it.second }, { it.first })
            .toSortedMap(compareByDescending { it })
            .flatMap { (frequency, cpus) ->
                cpus.sorted().chunked(2)
                    .filter { it.size == 2 }
                    .map { pair ->
                        val cores = GhostlockCpuPair(pair[0], pair[1])
                        GhostlockCpuPairOption(cores, "${cores} · ${formatFrequency(frequency)}")
                    }
            }
            .distinctBy { it.pair }
            .toMutableList()

        verifiedPairs[kernelRelease]?.let { pair ->
            if (pair.main in onlineCpus && pair.consumer in onlineCpus &&
                options.none { it.pair == pair }
            ) {
                options.add(0, GhostlockCpuPairOption(pair, "$pair · verified profile"))
            }
        }

        if (options.none { it.pair == defaultPair }) {
            val frequency = readMaxFrequency(defaultPair.main)
            val suffix = frequency.takeIf { it > 0L }?.let { " · ${formatFrequency(it)}" }.orEmpty()
            options += GhostlockCpuPairOption(defaultPair, "$defaultPair$suffix")
        }
        return options
    }

    fun selected(
        context: Context,
        kernelRelease: String? = null,
        options: List<GhostlockCpuPairOption> = options(kernelRelease),
    ): GhostlockCpuPair {
        val preferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val saved = preferences
            .getString(CPU_PAIR_KEY, null)
            ?.let(::parsePair)
        val verified = verifiedPairs[kernelRelease]
            ?.takeIf { pair -> options.any { it.pair == pair } }
        val savedWasExplicit = preferences.getBoolean(CPU_PAIR_EXPLICIT_KEY, false)
        /* Builds before the verified NX729J profile existed persisted the
         * generic 0,1 fallback without recording whether it was user-selected.
         * Treat that exact legacy value as stale; otherwise it masks the
         * upstream-verified 3,4 pair indefinitely. A current explicit choice
         * still wins, including an intentional 0,1 selection. */
        val staleLegacyDefault = saved == defaultPair && !savedWasExplicit && verified != null
        if (staleLegacyDefault) {
            preferences.edit()
                .putString(CPU_PAIR_KEY, verified.toString())
                .putBoolean(CPU_PAIR_EXPLICIT_KEY, false)
                .apply()
        }
        return saved?.takeIf { pair -> options.any { it.pair == pair } && !staleLegacyDefault }
            ?: verified
            ?: options.firstOrNull { it.pair == defaultPair }?.pair
            ?: options.firstOrNull()?.pair
            ?: defaultPair
    }

    fun save(context: Context, pair: GhostlockCpuPair) {
        context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(CPU_PAIR_KEY, pair.toString())
            .putBoolean(CPU_PAIR_EXPLICIT_KEY, true)
            .apply()
    }

    internal fun parseCpuList(value: String): List<Int> = value.split(',')
        .flatMap { part ->
            val bounds = part.trim().split('-', limit = 2).mapNotNull(String::toIntOrNull)
            when (bounds.size) {
                1 -> listOf(bounds[0])
                2 -> (bounds[0]..bounds[1]).toList()
                else -> emptyList()
            }
        }
        .filter { it >= 0 }
        .distinct()

    private fun readMaxFrequency(cpu: Int): Long {
        val base = "/sys/devices/system/cpu/cpu$cpu/cpufreq/"
        return sequenceOf("cpuinfo_max_freq", "scaling_max_freq")
            .mapNotNull { name -> readSysFile(base + name).toLongOrNull() }
            .firstOrNull { it > 0L }
            ?: -1L
    }

    private fun readSysFile(path: String): String = runCatching {
        File(path).takeIf(File::isFile)?.useLines { it.firstOrNull()?.trim().orEmpty() }.orEmpty()
    }.getOrDefault("")

    private fun formatFrequency(khz: Long): String = when {
        khz >= 1_000_000L -> "${khz / 1_000_000L}.${(khz % 1_000_000L) / 100_000L} GHz"
        khz >= 1_000L -> "${khz / 1_000L} MHz"
        else -> "$khz kHz"
    }

    private fun parsePair(value: String): GhostlockCpuPair? {
        val parts = value.split(',', limit = 2).mapNotNull(String::toIntOrNull)
        return parts.takeIf { it.size == 2 && it.all { cpu -> cpu >= 0 } }
            ?.let { GhostlockCpuPair(it[0], it[1]) }
    }
}
