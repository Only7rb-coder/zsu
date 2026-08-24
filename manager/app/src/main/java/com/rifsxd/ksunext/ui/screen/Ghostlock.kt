package com.rifsxd.ksunext.ui.screen

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ghostlock.GhostlockRunner
import com.rifsxd.ksunext.ui.util.rootAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun GhostlockScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kernelRelease = System.getProperty("os.version", "unknown")
    val abiSupported = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    val kernelSupported = GhostlockRunner.isKernelSupported(kernelRelease)
    val canRun = abiSupported && kernelSupported
    val rootPresent = runCatching { rootAvailable() }.getOrDefault(false)

    var isRunning by rememberSaveable { mutableStateOf(false) }
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var elapsedSeconds by rememberSaveable { mutableStateOf(0) }
    var statusMessage by rememberSaveable { mutableStateOf("") }

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
                        isRunning = true
                        elapsedSeconds = 0
                        statusMessage = context.getString(R.string.ghostlock_progress, 0)
                        scope.launch {
                            val timerJob = launch {
                                while (isActive) {
                                    delay(1000)
                                    elapsedSeconds += 1
                                    statusMessage = context.getString(R.string.ghostlock_progress, elapsedSeconds)
                                }
                            }
                            val runJob = async(Dispatchers.IO) {
                                GhostlockRunner.run(context)
                            }
                            val result = runJob.await()
                            timerJob.cancel()
                            isRunning = false
                            statusMessage = when {
                                result.success -> context.getString(R.string.ghostlock_success)
                                result.timedOut -> context.getString(R.string.ghostlock_timeout)
                                else -> context.getString(R.string.ghostlock_failed)
                            }
                        }
                    },
                    enabled = !isRunning
                ) { Text(stringResource(R.string.ghostlock_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }, enabled = !isRunning) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ghostlock),
                        fontWeight = FontWeight.Black
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        }
                    )
                    Text(
                        text = kernelRelease,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    StatusRow(
                        icon = Icons.Filled.Lock,
                        label = stringResource(R.string.ghostlock_root_state),
                        value = if (rootPresent) {
                            stringResource(R.string.ghostlock_rooted)
                        } else {
                            stringResource(R.string.ghostlock_non_rooted)
                        }
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
                color = if (canRun) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Button(
                onClick = { showConfirmation = true },
                enabled = canRun && !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        if (isRunning) R.string.ghostlock_running else R.string.ghostlock_run
                    )
                )
            }

            if (statusMessage.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
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
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
    }
}
