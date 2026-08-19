package com.rifsxd.ksunext.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.rifsxd.ksunext.ksuApp
import com.rifsxd.ksunext.ui.component.rememberLoadingDialog
import com.rifsxd.ksunext.ui.util.LocalSnackbarHost
import com.rifsxd.ksunext.ui.util.createRootShell
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * ZSU "Addons" tab: one-tap installers for common root-hiding module sets.
 * Modules are always fetched from the LATEST GitHub release (beta for Tricky Addon).
 */
private data class ModuleSpec(
    val label: String,
    val repo: String,
    val beta: Boolean = false,
    val preferredTag: String? = null,
    val fixedUrl: String? = null,
    val assetMatch: (String) -> Boolean
)

private const val OH_MY_KEYMINT_V1_2_0_URL =
    "https://github.com/Only7rb-coder/zsu/releases/download/v1.1.0/OhMyKeymint-release-arm64-v8a-1.2.0-cd84235.zip"

private val HIDE_UNLOCKED_MODULES = listOf(
    ModuleSpec("Zygisk Next", "Dr-TSNG/ZygiskNext") {
        it.endsWith(".zip") && it.contains("release")
    },
    ModuleSpec(
        "Oh My Keymint",
        "Only7rb-coder/zsu",
        preferredTag = "v1.1.0",
        fixedUrl = OH_MY_KEYMINT_V1_2_0_URL
    ) {
        it == "OhMyKeymint-release-arm64-v8a-1.2.0-cd84235.zip"
    },
    ModuleSpec("Tricky Addon (beta)", "KOWX712/Tricky-Addon-Update-Target-List", beta = true, preferredTag = "v5.0-beta.4") {
        it.endsWith(".zip")
    },
    ModuleSpec("HMA-OSS-zygisk", "frknkrc44/HMA-OSS") {
        it.endsWith(".zip") && it.contains("ZYGISK") && it.contains("release")
    }
)

private val HIDE_UNLOCKED_MODULES_2 = listOf(
    ModuleSpec("Zygisk Next", "Dr-TSNG/ZygiskNext") {
        it.endsWith(".zip") && it.contains("release")
    },
    ModuleSpec(
        "TEESimulator-RS v6.0.1-307",
        "Enginex0/TEESimulator-RS",
        preferredTag = "v6.0.1-307"
    ) {
        it == "TEESimulator-RS-v6.0.1-307-Release.zip"
    },
    ModuleSpec("Tricky Addon (beta)", "KOWX712/Tricky-Addon-Update-Target-List", beta = true, preferredTag = "v5.0-beta.4") {
        it.endsWith(".zip")
    },
    ModuleSpec("HMA-OSS-zygisk", "frknkrc44/HMA-OSS") {
        it.endsWith(".zip") && it.contains("ZYGISK") && it.contains("release")
    }
)

private val BRENE_MODULE = ModuleSpec("BRENE (susfs)", "rrr333nnn333/BRENE") {
    it.endsWith(".zip")
}

private const val GPS_SETTER_APK_URL =
    "https://github.com/Android1500/GpsSetter/releases/download/v1.2.9/app-release.apk"
private const val GPS_SPOOF_LSPOSED_URL =
    "https://github.com/Only7rb-coder/zsu/releases/download/v1.0.9/LSPosed-v1.9.2-it-7460-release.zip"

private object AddonInstaller {
    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\''")}'"

    private fun findAsset(rel: JSONObject, spec: ModuleSpec): Pair<String, String>? {
        val assets = rel.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            if (spec.assetMatch(name)) {
                return a.getString("browser_download_url") to rel.getString("tag_name")
            }
        }
        return null
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("User-Agent", "ZSU-Manager")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw Exception("GitHub API HTTP $code for $url")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /** Returns (downloadUrl, tag) of the best asset of the latest (beta) release. */
    fun resolveLatest(spec: ModuleSpec): Pair<String, String> {
        spec.fixedUrl?.let { url ->
            return url to (spec.preferredTag ?: "fixed")
        }
        spec.preferredTag?.let { tag ->
            runCatching {
                JSONObject(httpGet("https://api.github.com/repos/${spec.repo}/releases/tags/$tag"))
            }.getOrNull()?.let { rel ->
                findAsset(rel, spec)?.let { return it }
            }
        }
        if (!spec.beta) {
            val rel = JSONObject(httpGet("https://api.github.com/repos/${spec.repo}/releases/latest"))
            findAsset(rel, spec)?.let { return it }
            throw Exception("No matching asset in latest release of ${spec.repo}")
        } else {
            val rels = JSONArray(httpGet("https://api.github.com/repos/${spec.repo}/releases?per_page=10"))
            spec.preferredTag?.let { wanted ->
                for (i in 0 until rels.length()) {
                    val rel = rels.getJSONObject(i)
                    if (rel.getString("tag_name") != wanted) continue
                    findAsset(rel, spec)?.let { return it }
                }
            }
            for (i in 0 until rels.length()) {
                val rel = rels.getJSONObject(i)
                if (!rel.getBoolean("prerelease")) continue
                findAsset(rel, spec)?.let { return it }
            }
            throw Exception("No beta release asset in ${spec.repo}")
        }
    }

    fun download(url: String, dest: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ZSU-Manager")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw Exception("Download HTTP $code")
            dest.outputStream().use { out -> conn.inputStream.copyTo(out) }
        } finally {
            conn.disconnect()
        }
        if (dest.length() < 1024) throw Exception("Download too small, aborting")
        val magic = ByteArray(2)
        dest.inputStream().use { input ->
            if (input.read(magic) != 2 || magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) {
                throw Exception("Downloaded file is not a valid APK/ZIP")
            }
        }
    }

    /** Installs a downloaded APK through the root package manager. */
    fun installApk(apk: File, onLog: (String) -> Unit): Boolean {
        val source = shellQuote(apk.absolutePath)
        val target = shellQuote("/data/local/tmp/${apk.name}")
        val command = "status=1; if cp $source $target && chmod 644 $target; then " +
                "pm install -r -d $target; status=" + '$' + "?; fi; rm -f $target; exit " + '$' + "status"
        val cb = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let(onLog) }
        }
        val result = createRootShell(true).use { shell ->
            shell.newJob().add(command).to(cb, cb).exec()
        }
        return result.isSuccess
    }

    /** Runs `ksud module install <zip>` in a root shell, streaming output to [onLog]. */
    fun flashModuleZip(zip: File, onLog: (String) -> Unit): Boolean {
        val ksud = ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
        val cb = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let(onLog) }
        }
        val result = createRootShell(true).use { shell ->
            shell.newJob().add("$ksud module install ${zip.absolutePath}").to(cb, cb).exec()
        }
        return result.isSuccess
    }

    /** Writes every installed package name into Tricky Store's target.txt (select all apps). */
    fun selectAllAppsInTrickyTarget(onLog: (String) -> Unit): Boolean {
        val cmd = "mkdir -p /data/adb/tricky_store && " +
                "pm list packages | sed 's/^package://' | sort -u > /data/adb/tricky_store/target.txt && " +
                "wc -l < /data/adb/tricky_store/target.txt"
        val out = ArrayList<String>()
        val result = Shell.cmd(cmd).to(out, out).exec()
        out.lastOrNull()?.let { onLog("target.txt: $it apps selected") }
        return result.isSuccess
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AddonsScreen(navigator: DestinationsNavigator) {
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()

    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }

    fun appendLog(s: String) { log += s + "\n" }

    fun runInstall(title: String, modules: List<ModuleSpec>, afterAll: ((() -> Unit)?) = null) {
        if (busy) return
        scope.launch {
            busy = true
            log = ""
            loadingDialog.show()
            var failed = 0
            withContext(Dispatchers.IO) {
                modules.forEach { spec ->
                    try {
                        appendLog("» ${spec.label}: resolving latest ${if (spec.beta) "beta " else ""}release…")
                        val (url, tag) = AddonInstaller.resolveLatest(spec)
                        appendLog("  ${spec.label} $tag — downloading…")
                        val zip = File(
                            ksuApp.cacheDir,
                            "addon_${spec.label.replace(Regex("[^A-Za-z0-9._-]"), "_")}.zip"
                        )
                        AddonInstaller.download(url, zip)
                        appendLog("  installing…")
                        val ok = AddonInstaller.flashModuleZip(zip) { appendLog("  $it") }
                        zip.delete()
                        if (ok) appendLog("✓ ${spec.label} installed") else { failed++; appendLog("✗ ${spec.label} install FAILED") }
                    } catch (e: Exception) {
                        failed++
                        appendLog("✗ ${spec.label}: ${e.message}")
                    }
                }
                afterAll?.invoke()
            }
            loadingDialog.hide()
            busy = false
            snackBarHost.showSnackbar(if (failed == 0) "$title: done" else "$title: $failed item(s) failed — see log")
        }
    }

    fun runGpsSpoof() {
        if (busy) return
        scope.launch {
            busy = true
            log = ""
            loadingDialog.show()
            var failed = 0
            withContext(Dispatchers.IO) {
                val apk = File(ksuApp.cacheDir, "gps-setter-v1.2.9.apk")
                try {
                    appendLog("» GPS Setter v1.2.9: downloading APK…")
                    AddonInstaller.download(GPS_SETTER_APK_URL, apk)
                    appendLog("  installing APK through root…")
                    if (AddonInstaller.installApk(apk) { appendLog("  $it") }) {
                        appendLog("✓ GPS Setter APK installed")
                    } else {
                        failed++
                        appendLog("✗ GPS Setter APK install FAILED")
                    }
                } catch (e: Exception) {
                    failed++
                    appendLog("✗ GPS Setter APK: ${e.message}")
                } finally {
                    apk.delete()
                }

                val module = File(ksuApp.cacheDir, "LSPosed-v1.9.2-it-7460-release.zip")
                try {
                    appendLog("» LSPosed IT v1.9.2 (7460): downloading module…")
                    AddonInstaller.download(GPS_SPOOF_LSPOSED_URL, module)
                    appendLog("  installing module through ksud…")
                    if (AddonInstaller.flashModuleZip(module) { appendLog("  $it") }) {
                        appendLog("✓ LSPosed module installed")
                    } else {
                        failed++
                        appendLog("✗ LSPosed module install FAILED")
                    }
                } catch (e: Exception) {
                    failed++
                    appendLog("✗ LSPosed module: ${e.message}")
                } finally {
                    module.delete()
                }
            }
            loadingDialog.hide()
            busy = false
            snackBarHost.showSnackbar(
                if (failed == 0) "GPS Spoof: done" else "GPS Spoof: $failed item(s) failed — see log"
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Addons", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed { navigator.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = dropUnlessResumed {
                    runInstall("Hide bundle", HIDE_UNLOCKED_MODULES) {
                        appendLog("» Tricky Addon: selecting ALL apps in target.txt…")
                        if (AddonInstaller.selectAllAppsInTrickyTarget { appendLog("  $it") }) {
                            appendLog("✓ target.txt updated")
                        } else {
                            appendLog("✗ target.txt update failed (Tricky Store missing?)")
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Hide for unlocked bootloader devices 1") }

            Button(
                onClick = dropUnlessResumed {
                    runInstall("Hide bundle 2", HIDE_UNLOCKED_MODULES_2) {
                        appendLog("» Tricky Addon: selecting ALL apps in target.txt…")
                        if (AddonInstaller.selectAllAppsInTrickyTarget { appendLog("  $it") }) {
                            appendLog("✓ target.txt updated")
                        } else {
                            appendLog("✗ target.txt update failed (Tricky Store missing?)")
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Hide for unlocked bootloader devices 2") }

            Button(
                onClick = dropUnlessResumed { runGpsSpoof() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("GPS Spoof") }

            Button(
                onClick = dropUnlessResumed { runInstall("BRENE", listOf(BRENE_MODULE)) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Install BRENE for susfs users") }

            if (log.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = log,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
