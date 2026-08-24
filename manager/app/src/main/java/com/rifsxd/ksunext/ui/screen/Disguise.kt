package com.rifsxd.ksunext.ui.screen

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.rifsxd.ksunext.ui.component.rememberLoadingDialog
import com.rifsxd.ksunext.ui.util.DisguiseEngine
import com.rifsxd.ksunext.ui.util.LocalSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
private fun DisguiseTargetPickerDialog(
    targets: List<ApplicationInfo>,
    packageManager: PackageManager,
    selectedPackageName: String?,
    onSelect: (ApplicationInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredTargets = remember(targets, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) targets else targets.filter { app ->
            app.packageName.lowercase().contains(normalized) ||
                app.loadLabel(packageManager).toString().lowercase().contains(normalized)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Choose identity template", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Search by app name or package. This copies the visible identity only; it does not remove the selected app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search installed apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${filteredTargets.size} of ${targets.size} launchable apps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                if (filteredTargets.isEmpty()) {
                    Text(
                        "No matching launchable apps. Try another name or package.",
                        modifier = Modifier.padding(vertical = 28.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredTargets, key = { it.packageName }) { app ->
                            val isSelected = app.packageName == selectedPackageName
                            val iconBytes = remember(app.packageName) {
                                drawableToPng(app.loadIcon(packageManager))
                            }
                            val bitmap = remember(iconBytes) {
                                iconBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            }
                            Surface(
                                onClick = { onSelect(app) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    } else {
                                        Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(44.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            app.loadLabel(packageManager).toString(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

private fun suggestedPackageName(
    templatePackageName: String,
    currentPackageName: String,
    packageManager: PackageManager
): String {
    val suffixes = listOf("zsu", "manager", "clone", "copy")
    suffixes.forEach { suffix ->
        val candidate = "$templatePackageName.$suffix"
        val exists = runCatching {
            packageManager.getPackageInfo(candidate, 0)
            true
        }.getOrDefault(false)
        if (candidate != currentPackageName && candidate.length <= 240 && !exists) return candidate
    }
    val hash = Integer.toUnsignedString(templatePackageName.hashCode(), 16)
    return "com.zsu.disguise.app$hash"
}

private fun normalizeToPng(bytes: ByteArray): ByteArray? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return runCatching {
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }.getOrNull()
}

private fun drawableToPng(drawable: Drawable): ByteArray? {
    val size = maxOf(512, drawable.intrinsicWidth, drawable.intrinsicHeight)
    if (size <= 0) return null
    return runCatching {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }.getOrNull()
}

/**
 * ZSU "Disguise app" (Magisk Hide style): clone the manager with a custom
 * package name, version and icon, re-signed with the zsu key so the kernel
 * still trusts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun DisguiseScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()

    val pm = context.packageManager
    val curInfo = remember { pm.getPackageInfo(context.packageName, 0) }
    val curPkg = curInfo.packageName
    @Suppress("DEPRECATION") val curVc = curInfo.versionCode
    val curVn = curInfo.versionName ?: "15.0.000.2504111533"
    val curAppName = remember { pm.getApplicationLabel(context.applicationInfo).toString() }

    var pkg by rememberSaveable { mutableStateOf("com.zte.mifavor.variablewidget") }
    var appName by rememberSaveable { mutableStateOf("ZSU") }
    var versionName by rememberSaveable { mutableStateOf(curVn) }
    var versionCode by rememberSaveable { mutableStateOf((curVc + 1).toString()) }
    var iconBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pkgError by remember { mutableStateOf<String?>(null) }
    var selectedTargetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var targetPickerOpen by rememberSaveable { mutableStateOf(false) }
    var pendingTargetPackage by remember { mutableStateOf<String?>(null) }

    val disguiseTargets = remember {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != curPkg }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .toList()
    }
    val selectedTarget = disguiseTargets.firstOrNull { it.packageName == selectedTargetPackage }

    fun applyTarget(app: ApplicationInfo) {
        val label = app.loadLabel(pm).toString().ifBlank { app.packageName }
        selectedTargetPackage = app.packageName
        pkg = suggestedPackageName(app.packageName, curPkg, pm)
        appName = label

        iconBytes = drawableToPng(app.loadIcon(pm))
        pkgError = null
        targetPickerOpen = false
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input ->
                iconBytes = normalizeToPng(input.readBytes())
            }
        }
    }

    fun validPkg(p: String) = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$").matches(p)
    val targetInstallState by produceState<Boolean?>(initialValue = null, key1 = pkg) {
        value = if (pkg == curPkg) {
            true
        } else {
            withContext(Dispatchers.IO) {
                DisguiseEngine.isPackageInstalled(context, pkg)
            }
        }
    }

    fun startDisguise(targetPackageName: String) {
        val vc = versionCode.toLongOrNull() ?: (curVc + 1).toLong()
        scope.launch {
            loadingDialog.show()
            val result = withContext(Dispatchers.IO) {
                var apk: java.io.File? = null
                try {
                    apk = DisguiseEngine.disguise(
                        context,
                        DisguiseEngine.Params(targetPackageName, appName, versionName, vc, iconBytes)
                    )
                    val installed = DisguiseEngine.installAndVerifyViaRoot(apk, targetPackageName)
                    if (!installed) {
                        "Install failed or the new package was not visible to Package Manager. The original manager was kept."
                    } else {
                        val installedInfo = runCatching {
                            context.packageManager.getApplicationInfo(targetPackageName, 0)
                        }.getOrNull()
                        val installedLabel = installedInfo?.let { context.packageManager.getApplicationLabel(it).toString() }
                        val launchable = context.packageManager.getLaunchIntentForPackage(targetPackageName) != null
                        val identityVerified = installedInfo != null &&
                            installedLabel == appName &&
                            launchable
                        if (!identityVerified) {
                            "The package installed but verification failed (label or launch activity). The original manager was kept."
                        } else if (!DisguiseEngine.scheduleUninstallViaRoot(curPkg, delaySeconds = 5)) {
                            "New package $targetPackageName is verified, but removal of the original manager could not be scheduled. The original was kept."
                        } else {
                            "New package $targetPackageName is verified and launchable. The original manager will be removed in 5 seconds; open the new app from the launcher."
                        }
                    }
                } catch (e: Exception) {
                    "Disguise failed: ${e.message ?: "unknown error"}. The original manager was kept."
                } finally {
                    apk?.delete()
                }
            }
            loadingDialog.hide()
            snackBarHost.showSnackbar(result)
        }
    }

    if (targetPickerOpen) {
        DisguiseTargetPickerDialog(
            targets = disguiseTargets,
            packageManager = pm,
            selectedPackageName = selectedTargetPackage,
            onSelect = ::applyTarget,
            onDismiss = { targetPickerOpen = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Disguise app", fontWeight = FontWeight.Black) },
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
            Text(
                "Choose an installed app to copy its visible identity, then build a ZSU-backed disguised manager. You can still edit the package name, label, version, and icon below.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedCard(
                onClick = { targetPickerOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectedIcon = selectedTarget?.let { app ->
                        remember(app.packageName) { drawableToPng(app.loadIcon(pm)) }
                    }
                    val selectedBitmap = remember(selectedIcon) {
                        selectedIcon?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                    if (selectedBitmap != null) {
                        Image(
                            bitmap = selectedBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp)
                        )
                    } else {
                        Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(52.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            selectedTarget?.loadLabel(pm)?.toString() ?: "Choose an installed app",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            selectedTarget?.packageName ?: "Search by name or package",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { targetPickerOpen = true }) {
                        Text(if (selectedTarget == null) "Choose" else "Change")
                    }
                }
            }

            selectedTarget?.let { app ->
                Text(
                    "Template: ${app.loadLabel(pm)} (${app.packageName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "New target package: $pkg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = pkg,
                onValueChange = {
                    pkg = it.trim()
                    pkgError = if (validPkg(pkg) || pkg.isEmpty()) null else "Invalid package name (e.g. com.example.app)"
                },
                label = { Text("Package name") },
                isError = pkgError != null,
                supportingText = { pkgError?.let { Text(it) } ?: Text("Must be unused; current manager: $curPkg") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = versionName,
                onValueChange = { versionName = it.trim() },
                label = { Text("Version name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = appName,
                onValueChange = { appName = it },
                label = { Text("App name") },
                supportingText = { Text("Current: $curAppName") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = versionCode,
                onValueChange = { versionCode = it.filter(Char::isDigit) },
                label = { Text("Version code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (iconBytes != null) {
                    val bmp = remember(iconBytes) {
                        iconBytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    }
                    bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(56.dp)) }
                } else {
                    Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(56.dp))
                }
                OutlinedButton(onClick = dropUnlessResumed { iconPicker.launch("image/*") }) {
                    Text(if (iconBytes == null) "Pick icon image" else "Change icon image")
                }
            }

            Button(
                onClick = dropUnlessResumed {
                    if (!validPkg(pkg)) {
                        pkgError = "Invalid package name (e.g. com.example.app)"
                    } else if (pkg == curPkg) {
                        pkgError = "Choose a new package name; it cannot be the current ZSU package"
                    } else if (targetInstallState == null) {
                        pkgError = "Still checking whether this package is available"
                    } else if (targetInstallState == true) {
                        pkgError = "This package is already installed; choose a different target package"
                    } else {
                        pendingTargetPackage = pkg
                    }
                },
                enabled = validPkg(pkg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Install disguised app safely")
            }

            Text(
                "The selected app is used only as an identity template. Its package is not deleted. The new package is installed and verified first; only then is the original ZSU package removed automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    pendingTargetPackage?.let { targetPackageName ->
        AlertDialog(
            onDismissRequest = { pendingTargetPackage = null },
            title = { Text("Replace the current manager safely?") },
            text = {
                Text(
                    "ZSU will build and install $targetPackageName, verify its package, label, and launch activity, and only then remove the original $curPkg. The selected identity-template app is never deleted. If installation or verification fails, the original manager remains available."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = targetInstallState == false && targetPackageName == pkg,
                    onClick = {
                        pendingTargetPackage = null
                        startDisguise(targetPackageName)
                    }
                ) { Text("Install and continue") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTargetPackage = null }) { Text("Cancel") }
            }
        )
    }
}
