package com.rifsxd.ksunext.ghostlock

import android.content.Context
import android.net.Uri
import com.rifsxd.ksunext.ghostlock.ota.OtaPayloadExtractor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal sealed interface ExtractionResult {
    data class Stored(val name: String) : ExtractionResult
    data class OverwriteRequired(
        val targetName: String,
        val text: String,
        val releases: List<String>,
    ) : ExtractionResult
}

/** Imports verbatim profiles and invokes the pinned boot/OTA extractor. */
internal object GhostlockExtractor {
    private const val MaxProfileBytes = 1024 * 1024
    private const val ExtractTimeoutSeconds = 1800L

    fun importProfile(context: Context, uri: Uri, displayName: String): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use(::readLimited)
            ?: throw IOException("Unable to open the selected profile")
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        val store = store(context)
        val entries = store.parseEntries(text)
        require(entries.any { (it["release"] as? String)?.isNotBlank() == true }) {
            "The selected document has no nonempty GhostLock release"
        }
        return store.save(displayName, text)
    }

    fun extractLocal(
        context: Context,
        input: File,
        onLog: (String) -> Unit = {},
    ): ExtractionResult {
        require(input.isFile) { "Boot image does not exist: ${input.absolutePath}" }
        val workDir = createWorkDirectory(context)
        return try {
            extractInDirectory(context, input, null, workDir, onLog)
        } finally {
            workDir.deleteRecursively()
        }
    }

    suspend fun extractRemote(
        context: Context,
        url: String,
        onLog: (String) -> Unit = {},
    ): ExtractionResult {
        require(url.startsWith("https://", true) || url.startsWith("http://", true)) {
            "OTA input must be an HTTP(S) URL"
        }
        val workDir = createWorkDirectory(context)
        return try {
            val partitions = OtaPayloadExtractor.extractPartitions(url, workDir, onLog)
            extractInDirectory(
                context,
                partitions.bootFile,
                partitions.xblConfigFile,
                workDir,
                onLog,
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    fun confirmOverwrite(context: Context, pending: ExtractionResult.OverwriteRequired): String {
        val store = store(context)
        val entries = store.parseEntries(pending.text)
        val releases = entries.mapNotNull { (it["release"] as? String)?.takeIf(String::isNotBlank) }
        require(releases.isNotEmpty() && releases == pending.releases) {
            "The extracted profile changed; extract it again"
        }
        require(store.rawText(pending.targetName) != null) {
            "The overwrite target no longer exists"
        }
        require(store.overwrite(pending.targetName, pending.text)) {
            "Unable to overwrite ${pending.targetName}"
        }
        return pending.targetName
    }

    private fun extractInDirectory(
        context: Context,
        input: File,
        xblConfig: File?,
        workDir: File,
        onLog: (String) -> Unit,
    ): ExtractionResult {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libextract.so")
        require(binary.isFile) { "GhostLock extractor is not present in this APK" }
        val output = File(workDir, "extracted.conf")
        val command = buildList {
            add(binary.absolutePath)
            add(input.absolutePath)
            if (xblConfig != null) {
                add("--xbl-config")
                add(xblConfig.absolutePath)
            }
            addAll(listOf("--format", "conf", "--out", output.absolutePath, "--work-dir", workDir.absolutePath))
        }
        val builder = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .apply {
                environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                environment()["TMPDIR"] = workDir.absolutePath
                environment()["HOME"] = workDir.absolutePath
            }
        onLog("Extracting GhostLock profile from ${input.name}")
        val result = runExtractor(builder, onLog)
        if (result.exitCode != 0 || !output.isFile) {
            throw IOException(
                "GhostLock extractor exited with code ${result.exitCode}\n${result.output}".trim(),
            )
        }
        require(output.length() <= MaxProfileBytes) { "Extracted profile exceeds 1 MiB" }
        return storeExtracted(store(context), output.readText(Charsets.UTF_8))
    }

    private fun storeExtracted(store: GhostlockProfileStore, text: String): ExtractionResult {
        val entries = store.parseEntries(text)
        val releases = entries.mapNotNull { (it["release"] as? String)?.takeIf(String::isNotBlank) }
        require(releases.isNotEmpty()) { "Extracted profile has no release" }
        val existing = releases.firstNotNullOfOrNull(store::findFileForRelease)
        return if (existing != null) {
            ExtractionResult.OverwriteRequired(existing.name, text, releases)
        } else {
            ExtractionResult.Stored(store.save("extracted.conf", text))
        }
    }

    private fun store(context: Context) = GhostlockProfileStore(
        File(context.filesDir, "ghostlock/user_profiles"),
        GhostlockAssetLoader(context),
    )

    private fun createWorkDirectory(context: Context): File {
        val directory = File(
            context.cacheDir,
            "ghostlock/extract-${System.currentTimeMillis()}-${System.nanoTime()}",
        )
        require(directory.mkdirs()) { "Unable to create private extraction directory" }
        return directory
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MaxProfileBytes) {
                throw IOException("GhostLock profile exceeds 1 MiB")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private fun runExtractor(builder: ProcessBuilder, onLog: (String) -> Unit): ProcessResult {
        val process = builder.start()
        val output = BoundedExtractorLog()
        val reader = thread(start = true, name = "ghostlock-extract-output", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.append(line)
                        runCatching { onLog(line) }
                    }
                }
            } catch (_: IOException) {
            }
        }
        var finished = false
        try {
            finished = process.waitFor(ExtractTimeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            reader.join(3000)
            val exitCode = if (finished) process.exitValue() else -1
            return ProcessResult(exitCode, output.text())
        } catch (error: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw error
        } finally {
            if (process.isAlive) process.destroyForcibly()
            reader.interrupt()
            runCatching { process.inputStream.close() }
            runCatching { process.outputStream.close() }
            runCatching { process.errorStream.close() }
            try {
                reader.join(3000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}

private class BoundedExtractorLog(private val limit: Int = 256 * 1024) {
    private val text = StringBuilder()
    private var truncated = false

    @Synchronized
    fun append(line: String) {
        if (truncated) return
        val remaining = limit - text.length
        if (line.length + 1 > remaining) {
            if (remaining > 0) text.append(line.take(remaining))
            text.append("\n[extractor output truncated]\n")
            truncated = true
        } else {
            text.append(line).append('\n')
        }
    }

    @Synchronized
    fun text(): String = text.toString().trim()
}
