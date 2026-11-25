package com.rifsxd.ksunext.ui.screen

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ghostlock.ExtractionResult
import com.rifsxd.ksunext.ghostlock.GhostlockAssetLoader
import com.rifsxd.ksunext.ghostlock.GhostlockAutoMode
import com.rifsxd.ksunext.ghostlock.GhostlockCpuPairCatalog
import com.rifsxd.ksunext.ghostlock.GhostlockExtractor
import com.rifsxd.ksunext.ghostlock.GhostlockKernelRelease
import com.rifsxd.ksunext.ghostlock.GhostlockManagerScanner
import com.rifsxd.ksunext.ghostlock.GhostlockProfileConfiguration
import com.rifsxd.ksunext.ghostlock.GhostlockProfileStore
import com.rifsxd.ksunext.ghostlock.GhostlockRunState
import com.rifsxd.ksunext.ghostlock.GhostlockRunner
import com.rifsxd.ksunext.ghostlock.GhostlockShizukuRunner
import com.rifsxd.ksunext.ui.util.rootAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

internal enum class GhostlockExecutionMode { DIRECT, SHIZUKU }

private object BlRootOperationStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operationJob: Job? = null

    val isRunning = mutableStateOf(false)
    val elapsedSeconds = mutableStateOf(0)
    val statusMessage = mutableStateOf("")
    private val nativeStatus = mutableStateOf("")

    private fun renderProgress(context: Context) {
        statusMessage.value = if (nativeStatus.value.isBlank()) {
            context.getString(R.string.ghostlock_progress, elapsedSeconds.value)
        } else {
            context.getString(
                R.string.ghostlock_progress_detail,
                elapsedSeconds.value,
                nativeStatus.value,
            )
        }
    }

    fun start(context: Context, mode: GhostlockExecutionMode) {
        if (isRunning.value || operationJob?.isActive == true) return
        val appContext = context.applicationContext
        isRunning.value = true
        elapsedSeconds.value = 0
        nativeStatus.value = ""
        renderProgress(appContext)
        operationJob = scope.launch {
            val timerJob = launch {
                while (isActive) {
                    delay(1000)
                    elapsedSeconds.value += 1
                    renderProgress(appContext)
                }
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    val release = GhostlockKernelRelease.current()
                    val cpuPair = GhostlockCpuPairCatalog.selected(appContext, release)
                    val profile = GhostlockProfileConfiguration.resolve(
                        appContext,
                        release,
                        cpuPair.main,
                        cpuPair.consumer,
                    )
                    val runState = GhostlockRunState(
                        File(appContext.filesDir, "ghostlock/.ghostlock_run_state.json"),
                    )
                    runState.reset()
                    fun postStatus(line: String) {
                        scope.launch(Dispatchers.Main.immediate) {
                            if (isRunning.value) {
                                nativeStatus.value = line.take(180)
                                renderProgress(appContext)
                            }
                        }
                    }
                    when (mode) {
                        GhostlockExecutionMode.DIRECT ->
                            GhostlockRunner.run(appContext, profile) { line -> postStatus(line) }

                        GhostlockExecutionMode.SHIZUKU -> {
                            val shizuku = runInterruptible {
                                GhostlockShizukuRunner(appContext).run(
                                    profile.bytes,
                                    GhostlockManagerScanner.selected(appContext),
                                    { line -> postStatus(line) },
                                    { step, status ->
                                        if (status == "disabled") runState.clear()
                                        else runState.apply(step, status)
                                    },
                                )
                            }
                            val message = if (shizuku.timedOut) {
                                appContext.getString(R.string.ghostlock_timeout)
                            } else {
                                "GhostLock exited with code ${shizuku.exitCode}"
                            }
                            GhostlockRunner.Result(
                                shizuku.exitCode == 0 && !shizuku.timedOut,
                                shizuku.timedOut,
                                message,
                            )
                        }
                    }
                }
                if (result.success) GhostlockAutoMode.onManualSuccess(appContext)
                statusMessage.value = if (result.success) {
                    appContext.getString(R.string.ghostlock_success)
                } else {
                    result.output.ifBlank {
                        appContext.getString(
                            if (result.timedOut) R.string.ghostlock_timeout
                            else R.string.ghostlock_failed,
                        )
                    }
                }
            } catch (error: Throwable) {
                statusMessage.value = error.message?.let { "BL Root exception: $it" }
                    ?: appContext.getString(R.string.ghostlock_failed)
            } finally {
                timerJob.cancel()
                isRunning.value = false
                operationJob = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun GhostlockScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val kernelRelease = remember { GhostlockKernelRelease.current() }
    val deviceName = remember { resolveDeviceName() }
    val socName = remember { resolveSocName() }
    val abiSupported = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    val profileStore = remember {
        GhostlockProfileStore(
            File(appContext.filesDir, "ghostlock/user_profiles"),
            GhostlockAssetLoader(appContext),
        )
    }
    var profileRevision by remember { mutableIntStateOf(0) }
    var importedProfiles by remember {
        mutableStateOf(emptyList<GhostlockProfileStore.StoredProfile>())
    }
    LaunchedEffect(profileRevision) {
        importedProfiles = withContext(Dispatchers.IO) { profileStore.list() }
    }
    var selectedProfileName by rememberSaveable { mutableStateOf(profileStore.selected(appContext)) }
    val builtinSupported = remember(kernelRelease) {
        GhostlockProfileConfiguration.hasBuiltin(appContext, kernelRelease)
    }
    val selectedImport = importedProfiles.firstOrNull { it.name == selectedProfileName }
    val kernelSupported = if (selectedProfileName == null) {
        builtinSupported
    } else {
        selectedImport?.releases?.contains(kernelRelease) == true
    }
    val cpuOptions = remember(kernelRelease) { GhostlockCpuPairCatalog.options(kernelRelease) }
    var selectedCpuPairIndex by rememberSaveable {
        mutableIntStateOf(
            cpuOptions.indexOfFirst {
                it.pair == GhostlockCpuPairCatalog.selected(context, kernelRelease, cpuOptions)
            }.coerceAtLeast(0),
        )
    }
    var cpuMenuExpanded by remember { mutableStateOf(false) }
    var executionMode by remember { mutableStateOf(GhostlockExecutionMode.DIRECT) }
    var executionModeOverridden by remember { mutableStateOf(false) }
    LaunchedEffect(kernelRelease, selectedProfileName, profileRevision) {
        val recommendShizuku = withContext(Dispatchers.IO) {
            runCatching {
                val pair = GhostlockCpuPairCatalog.selected(appContext, kernelRelease)
                GhostlockProfileConfiguration.resolve(
                    appContext,
                    kernelRelease,
                    pair.main,
                    pair.consumer,
                ).recommendShizuku
            }.getOrDefault(false)
        }
        if (!executionModeOverridden) {
            executionMode = if (recommendShizuku) {
                GhostlockExecutionMode.SHIZUKU
            } else {
                GhostlockExecutionMode.DIRECT
            }
        }
    }
    val canRun = abiSupported && kernelSupported
    val rootPresent = remember { runCatching { rootAvailable() }.getOrDefault(false) }

    val isRunning by BlRootOperationStore.isRunning
    val statusMessage by BlRootOperationStore.statusMessage
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var utilityRunning by remember { mutableStateOf(false) }
    var utilityMessage by remember { mutableStateOf("") }
    var pendingOverwrite by remember { mutableStateOf<ExtractionResult.OverwriteRequired?>(null) }
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var managerMenuExpanded by remember { mutableStateOf(false) }
    var managerCandidates by remember {
        mutableStateOf(emptyList<GhostlockManagerScanner.Candidate>())
    }
    var selectedManager by rememberSaveable {
        mutableStateOf(GhostlockManagerScanner.selected(appContext))
    }
    val scope = rememberCoroutineScope()
    val controlsBusy = isRunning || utilityRunning

    LaunchedEffect(executionMode) {
        if (executionMode == GhostlockExecutionMode.SHIZUKU) {
            managerCandidates = withContext(Dispatchers.IO) {
                runCatching { GhostlockManagerScanner.scan(appContext.packageManager) }
                    .getOrDefault(emptyList())
            }
        }
    }

    fun importProfile(uri: Uri) {
        if (controlsBusy) return
        utilityRunning = true
        utilityMessage = ""
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    val displayName = queryDisplayName(appContext, uri) ?: "profile.conf"
                    GhostlockExtractor.importProfile(appContext, uri, displayName)
                }
                utilityMessage = appContext.getString(R.string.ghostlock_imported, name)
                profileRevision += 1
            } catch (error: Exception) {
                utilityMessage = appContext.getString(
                    R.string.ghostlock_invalid_profile,
                    error.message ?: error.javaClass.simpleName,
                )
            } finally {
                utilityRunning = false
            }
        }
    }

    fun extract(action: suspend () -> ExtractionResult) {
        if (controlsBusy) return
        utilityRunning = true
        utilityMessage = ""
        scope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { action() }) {
                    is ExtractionResult.Stored -> {
                        utilityMessage = appContext.getString(R.string.ghostlock_extracted, result.name)
                        profileRevision += 1
                    }
                    is ExtractionResult.OverwriteRequired -> pendingOverwrite = result
                }
            } catch (error: Exception) {
                utilityMessage = error.message ?: appContext.getString(R.string.ghostlock_extraction_failed)
            } finally {
                utilityRunning = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importProfile(uri)
    }
    val extractionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        extract {
            val temporary = File(appContext.cacheDir, "ghostlock-input-${System.nanoTime()}.img")
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to open the selected boot image")
                GhostlockExtractor.extractLocal(appContext, temporary)
            } finally {
                temporary.delete()
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isRunning) showConfirmation = false },
            icon = { Icon(Icons.Filled.Security, contentDescription = null) },
            title = { Text(stringResource(R.string.ghostlock_confirm_title)) },
            text = { Text(stringResource(R.string.ghostlock_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        BlRootOperationStore.start(context, executionMode)
                    },
                    enabled = !controlsBusy,
                ) { Text(stringResource(R.string.ghostlock_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }, enabled = !controlsBusy) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingOverwrite?.let { pending ->
        AlertDialog(
            onDismissRequest = { if (!utilityRunning) pendingOverwrite = null },
            title = { Text(stringResource(R.string.ghostlock_overwrite_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ghostlock_overwrite_message,
                        pending.targetName,
                        pending.releases.joinToString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !utilityRunning,
                    onClick = {
                        utilityRunning = true
                        scope.launch {
                            try {
                                val name = withContext(Dispatchers.IO) {
                                    GhostlockExtractor.confirmOverwrite(appContext, pending)
                                }
                                utilityMessage = appContext.getString(R.string.ghostlock_overwritten, name)
                                profileRevision += 1
                                pendingOverwrite = null
                            } catch (error: Exception) {
                                utilityMessage = error.message
                                    ?: appContext.getString(R.string.ghostlock_extraction_failed)
                            } finally {
                                utilityRunning = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.ghostlock_overwrite_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !utilityRunning, onClick = { pendingOverwrite = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.ghostlock), fontWeight = FontWeight.Black)
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatusRow(
                        icon = if (kernelSupported) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        label = stringResource(R.string.ghostlock_kernel),
                        value = if (kernelSupported) {
                            stringResource(R.string.ghostlock_kernel_supported)
                        } else {
                            stringResource(R.string.ghostlock_kernel_unsupported)
                        },
                        valueColor = if (kernelSupported) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = kernelRelease,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    InfoRow(stringResource(R.string.ghostlock_device), deviceName)
                    InfoRow(stringResource(R.string.ghostlock_soc), socName)
                    StatusRow(
                        icon = Icons.Filled.Lock,
                        label = stringResource(R.string.ghostlock_root_state),
                        value = if (rootPresent) {
                            stringResource(R.string.ghostlock_rooted)
                        } else {
                            stringResource(R.string.ghostlock_non_rooted)
                        },
                    )
                }
            }

            Text(
                text = when {
                    !abiSupported -> stringResource(R.string.ghostlock_abi_unsupported_message)
                    kernelSupported -> stringResource(R.string.ghostlock_ready_message)
                    else -> stringResource(R.string.ghostlock_unsupported_message)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (canRun) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.ghostlock_profile_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Box {
                        TextButton(enabled = !controlsBusy, onClick = { profileMenuExpanded = true }) {
                            Text(
                                selectedImport?.name
                                    ?: stringResource(R.string.ghostlock_profile_builtin),
                            )
                        }
                        DropdownMenu(
                            expanded = profileMenuExpanded,
                            onDismissRequest = { profileMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                enabled = builtinSupported,
                                text = { Text(stringResource(R.string.ghostlock_profile_builtin)) },
                                onClick = {
                                    profileStore.select(appContext, null)
                                    selectedProfileName = null
                                    profileMenuExpanded = false
                                },
                            )
                            importedProfiles
                                .filter { kernelRelease in it.releases }
                                .forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.name) },
                                        onClick = {
                                            profileStore.select(appContext, profile.name)
                                            selectedProfileName = profile.name
                                            profileMenuExpanded = false
                                        },
                                    )
                                }
                        }
                    }
                    Text(
                        stringResource(R.string.ghostlock_profile_exact_release),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.ghostlock_cpu_pair_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Box {
                        TextButton(enabled = !controlsBusy, onClick = { cpuMenuExpanded = true }) {
                            Text(cpuOptions.getOrNull(selectedCpuPairIndex)?.label ?: "0,1")
                        }
                        DropdownMenu(
                            expanded = cpuMenuExpanded,
                            onDismissRequest = { cpuMenuExpanded = false },
                        ) {
                            cpuOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        selectedCpuPairIndex = index
                                        GhostlockCpuPairCatalog.save(context, option.pair)
                                        cpuMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.ghostlock_execution_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = executionMode == GhostlockExecutionMode.DIRECT,
                            enabled = !controlsBusy,
                            onClick = {
                                executionMode = GhostlockExecutionMode.DIRECT
                                executionModeOverridden = true
                            },
                            label = { Text(stringResource(R.string.ghostlock_mode_direct)) },
                        )
                        FilterChip(
                            selected = executionMode == GhostlockExecutionMode.SHIZUKU,
                            enabled = !controlsBusy,
                            onClick = {
                                executionMode = GhostlockExecutionMode.SHIZUKU
                                executionModeOverridden = true
                            },
                            label = { Text(stringResource(R.string.ghostlock_mode_shizuku)) },
                        )
                    }
                    if (executionMode == GhostlockExecutionMode.SHIZUKU) {
                        Text(
                            stringResource(R.string.ghostlock_shizuku_status),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Box {
                            TextButton(
                                enabled = !controlsBusy,
                                onClick = { managerMenuExpanded = true },
                            ) {
                                Text(
                                    selectedManager
                                        ?: stringResource(R.string.ghostlock_manager_automatic),
                                )
                            }
                            DropdownMenu(
                                expanded = managerMenuExpanded,
                                onDismissRequest = { managerMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ghostlock_manager_automatic)) },
                                    onClick = {
                                        GhostlockManagerScanner.select(appContext, null)
                                        selectedManager = null
                                        managerMenuExpanded = false
                                    },
                                )
                                managerCandidates.forEach { manager ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${manager.label} (${manager.packageName})" +
                                                    if (manager.hasKsud) " — ksud" else "",
                                            )
                                        },
                                        onClick = {
                                            GhostlockManagerScanner.select(appContext, manager.packageName)
                                            selectedManager = manager.packageName
                                            managerMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        TextButton(
                            enabled = !controlsBusy,
                            onClick = { GhostlockShizukuRunner(appContext).requestPermission() },
                        ) { Text(stringResource(R.string.ghostlock_shizuku_grant)) }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.ghostlock_profile_tools),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !controlsBusy,
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                        ) { Text(stringResource(R.string.ghostlock_import)) }
                        OutlinedButton(
                            enabled = !controlsBusy,
                            onClick = { extractionLauncher.launch(arrayOf("*/*")) },
                        ) { Text(stringResource(R.string.ghostlock_extract_local)) }
                    }
                    OutlinedTextField(
                        value = remoteUrl,
                        enabled = !controlsBusy,
                        onValueChange = { remoteUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.ghostlock_ota_url)) },
                    )
                    OutlinedButton(
                        enabled = !controlsBusy && remoteUrl.isNotBlank(),
                        onClick = {
                            extract { GhostlockExtractor.extractRemote(appContext, remoteUrl.trim()) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.ghostlock_extract_remote)) }
                }
            }

            Button(
                onClick = { showConfirmation = true },
                enabled = canRun && !controlsBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        if (isRunning) R.string.ghostlock_running else R.string.ghostlock_run,
                    ),
                )
            }

            if (utilityRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.ghostlock_utility_running))
                }
            }
            if (utilityMessage.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        utilityMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (statusMessage.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        style = if (isRunning) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.bodySmall,
                        fontFamily = if (isRunning) null else FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

private fun resolveDeviceName(): String =
    Build.MODEL.trim().takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        ?: Build.DEVICE.trim().takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        ?: "Unknown"

private fun resolveSocName(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val model = Build.SOC_MODEL.trim()
        if (model.isNotBlank() && !model.equals("unknown", ignoreCase = true)) {
            val manufacturer = Build.SOC_MANUFACTURER.trim()
            val vendor = if (manufacturer.contains("qualcomm", ignoreCase = true)) "QTI" else manufacturer
            return listOf(vendor, model).filter(String::isNotBlank).joinToString(" ")
        }
    }
    return Build.HARDWARE.trim().takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        ?: "Unknown"
}
