package com.rifsxd.ksunext.ghostlock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Resolves a self-contained upstream GhostLock JSON profile for the exact uname -r. */
internal object GhostlockProfileConfiguration {
    private const val BUILTIN_DIRECTORY = "kernel_profiles"

    fun resolve(context: Context, release: String, mainCpu: Int = 0, consumerCpu: Int = 1): String {
        val index = readObject(context, "${BUILTIN_DIRECTORY}/index.json")
        require(index.optInt("schema_version") == 1) { "unsupported GhostLock profile index" }
        val entry = findProfile(index.optJSONArray("profiles"), release)
            ?: error("no GhostLock profile for kernel: $release")
        val builtin = readObject(context, "$BUILTIN_DIRECTORY/${entry.getString("file")}")
        require(builtin.optInt("schema_version") == 1) { "unsupported GhostLock profile" }
        require(builtin.optString("release") == release) { "GhostLock profile release mismatch" }
        val defaults = readObject(context, "$BUILTIN_DIRECTORY/defaults.json")
        val resolved = deepMerge(JSONObject().apply {
            put("release", release)
            put("execution", defaults.getJSONObject("execution"))
        }, builtin)
        resolved.put("schema_version", 1)
        resolved.getJSONObject("execution").put(
            "selected_cpus", JSONObject().apply {
                put("main", mainCpu)
                put("consumer", consumerCpu)
            },
        )
        validate(resolved, release)
        return resolved.toString()
    }

    private fun readObject(context: Context, path: String): JSONObject =
        context.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }

    private fun findProfile(profiles: JSONArray?, release: String): JSONObject? =
        (0 until (profiles?.length() ?: 0)).asSequence()
            .mapNotNull { profiles?.optJSONObject(it) }
            .firstOrNull { it.optString("release") == release }

    private fun deepMerge(base: JSONObject, override: JSONObject): JSONObject {
        override.keys().forEach { key ->
            val incoming = override.opt(key)
            val current = base.opt(key)
            if (incoming is JSONObject && current is JSONObject) {
                base.put(key, deepMerge(current, incoming))
            } else if (incoming != null && incoming !== JSONObject.NULL) {
                base.put(key, incoming)
            }
        }
        return base
    }

    private fun validate(profile: JSONObject, release: String) {
        require(profile.optString("release") == release) { "GhostLock profile release mismatch" }
        require(profile.optInt("kernel_major") in 5..6) { "invalid GhostLock kernel family" }
        require(profile.optLong("off_init_task") != 0L) { "missing GhostLock init_task offset" }
        require(profile.optLong("off_init_cred") != 0L) { "missing GhostLock init_cred offset" }
        require(profile.has("execution")) { "missing GhostLock execution settings" }
    }
}
