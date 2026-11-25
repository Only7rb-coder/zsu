package com.rifsxd.ksunext.ghostlock

import com.ghostlock.app.data.HoconSupport

/** Encoding/decoding for the ordered native run-state document. */
object RunStateCodec {
    val Steps = listOf("w1a", "w1b", "w2a", "w2b", "w3a", "w3b", "w3c")

    const val NotStarted = "not_start"
    const val InProgress = "in_progress"
    const val Completed = "completed"

    fun initial(updatedAt: Long): String = encode(Steps.associateWith { NotStarted }, updatedAt)

    fun encode(steps: Map<String, String>, updatedAt: Long): String = buildString {
        append("{\n")
        append("  \"schema_version\": 1,\n")
        append("  \"updated_at\": ").append(updatedAt).append(",\n")
        append("  \"steps\": {\n")
        val entries = steps.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
            append("    \"").append(key).append("\": \"").append(value).append('"')
            if (index + 1 < entries.size) append(',')
            append('\n')
        }
        append("  }\n}\n")
    }

    fun parseStuckStep(text: String): String? {
        val root = HoconSupport.parseValue(text) as? Map<*, *> ?: return null
        val steps = root["steps"] as? Map<*, *> ?: return null
        return Steps.firstOrNull { (steps[it] as? String) == InProgress }
    }
}
