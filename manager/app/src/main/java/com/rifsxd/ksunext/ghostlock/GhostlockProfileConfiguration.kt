package com.rifsxd.ksunext.ghostlock

import android.content.Context
import com.ghostlock.app.data.HoconSupport
import com.ghostlock.app.data.NativeProfileDocument
import com.ghostlock.app.data.ValueMap
import com.ghostlock.app.data.asValueMap
import com.ghostlock.app.data.profile.CpuPairView
import com.ghostlock.app.data.profile.ProfileMerger
import com.ghostlock.app.data.profile.ProfileResolver
import com.ghostlock.app.data.route.RouteKind

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

    fun hasBuiltin(context: Context, release: String): Boolean = runCatching {
        val loader = GhostlockAssetLoader(context)
        val index = parse(loader.load("$Directory/index.conf"))
        require((index["schema_version"] as? Number)?.toInt() == 1)
        val entries = index["profiles"] as? List<*> ?: error("missing profile index")
        entries.mapNotNull { it.asValueMap() }.any { it["release"] == release }
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
                require((profile["schema_version"] as? Number)?.toInt() == 1) {
                    "unsupported profile schema"
                }
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
        val presets = RouteKind.entries.mapNotNull { route ->
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
        is String -> route.takeIf { it.isNotEmpty() && it != "null" }
        is Map<*, *> -> route.keys.filterIsInstance<String>().firstOrNull {
            RouteKind.fromToken(it) != null
        }
        else -> null
    }
}
