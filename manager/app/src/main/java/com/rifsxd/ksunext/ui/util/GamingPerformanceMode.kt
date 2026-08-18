package com.rifsxd.ksunext.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.rifsxd.ksunext.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object GamingPerformanceMode {
    const val PREF_ENABLED = "gaming_performance_mode_enabled"
    const val PREF_PACKAGES = "gaming_performance_mode_packages"

    fun isEnabled(context: Context): Boolean = context
        .getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putBoolean(PREF_ENABLED, enabled) }
    }

    fun selectedPackages(context: Context): Set<String> = context
        .getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getStringSet(PREF_PACKAGES, emptySet())
        ?.toSet()
        ?: emptySet()

    fun setSelectedPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putStringSet(PREF_PACKAGES, packages.toSet()) }
    }

    data class LaunchableApp(
        val packageName: String,
        val label: String
    )

    fun installedLaunchableApps(context: Context): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
                LaunchableApp(
                    packageName = packageName,
                    label = appInfo.loadLabel(context.packageManager).toString()
                        .ifBlank { packageName }
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LaunchableApp::label)
                .thenBy { it.packageName })
            .toList()
    }
}

@Composable
fun GamingPerformanceModeItem(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var enabled by rememberSaveable { mutableStateOf(prefs.getBoolean(GamingPerformanceMode.PREF_ENABLED, false)) }
    var selectedPackages by remember {
        mutableStateOf(
            prefs.getStringSet(GamingPerformanceMode.PREF_PACKAGES, emptySet())?.toSet() ?: emptySet()
        )
    }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
            headlineContent = {
                Text(
                    text = context.getString(R.string.settings_gaming_performance_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(context.getString(R.string.settings_gaming_performance_mode_summary))
            },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        prefs.edit { putBoolean(GamingPerformanceMode.PREF_ENABLED, it) }
                    }
                )
            }
        )

        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showAppPicker = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Spacer(Modifier.size(24.dp)) },
            headlineContent = {
                Text(context.getString(R.string.settings_gaming_performance_mode_apps))
            },
            supportingContent = {
                Text(
                    if (selectedPackages.isEmpty()) {
                        context.getString(R.string.settings_gaming_performance_mode_apps_none)
                    } else {
                        context.getString(
                            R.string.settings_gaming_performance_mode_apps_selected,
                            selectedPackages.size
                        )
                    },
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            trailingContent = {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        )
    }

    if (showAppPicker) {
        GamingPerformanceModeAppPicker(
            selectedPackages = selectedPackages,
            onDismiss = { showAppPicker = false },
            onSave = { packages ->
                selectedPackages = packages
                GamingPerformanceMode.setSelectedPackages(context, packages)
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun GamingPerformanceModeAppPicker(
    selectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<GamingPerformanceMode.LaunchableApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            GamingPerformanceMode.installedLaunchableApps(context)
        }
        isLoading = false
    }
    var query by rememberSaveable { mutableStateOf("") }
    var checkedPackages by remember { mutableStateOf(selectedPackages.toSet()) }
    val filteredApps = remember(query, apps) {
        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isEmpty()) apps else apps.filter {
            it.label.lowercase(Locale.getDefault()).contains(normalized) ||
                it.packageName.lowercase(Locale.getDefault()).contains(normalized)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.settings_gaming_performance_mode_apps)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(context.getString(R.string.settings_gaming_performance_mode_apps_search)) }
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                if (isLoading) {
                    Text(
                        text = context.getString(R.string.settings_gaming_performance_mode_apps_loading),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in checkedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checkedPackages = if (checked) {
                                        checkedPackages - app.packageName
                                    } else {
                                        checkedPackages + app.packageName
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    checkedPackages = if (value) {
                                        checkedPackages + app.packageName
                                    } else {
                                        checkedPackages - app.packageName
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, maxLines = 1)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(checkedPackages) }) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(context.getString(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}
