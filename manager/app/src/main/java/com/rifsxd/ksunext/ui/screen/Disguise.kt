package com.rifsxd.ksunext.ui.screen

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var pendingTargetPackage by remember { mutableStateOf<String?>(null) }

    val disguiseTargets = remember {
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != curPkg }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .toList()
    }
    val selectedTarget = disguiseTargets.firstOrNull { it.packageName == selectedTargetPackage }

    fun applyTarget(app: ApplicationInfo) {
        val label = app.loadLabel(pm).toString().ifBlank { app.packageName }
        selectedTargetPackage = app.packageName
        pkg = app.packageName
        appName = label
        iconBytes = drawableToPng(app.loadIcon(pm))
        pkgError = null
        targetMenuExpanded = false
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input ->
                iconBytes = normalizeToPng(input.readBytes())
            }
        }
    }

    fun validPkg(p: String) = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$").matches(p)
    val targetAlreadyInstalled by produceState(initialValue = false, key1 = pkg) {
        value = if (pkg == curPkg) {
            false
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
                try {
                    val apk = DisguiseEngine.disguise(
                        context,
                        DisguiseEngine.Params(targetPackageName, appName, versionName, vc, iconBytes)
                    )
                    val transactionStarted = DisguiseEngine.installAndRemoveOriginalViaRoot(
                        apk = apk,
                        targetPackageName = targetPackageName,
                        originalPackageName = curPkg
                    )
                    val verified = transactionStarted &&
                        DisguiseEngine.isPackageInstalled(context, targetPackageName) &&
                        context.packageManager.getLaunchIntentForPackage(targetPackageName) != null
                    apk.delete()
                    if (verified) {
                        "New package $targetPackageName verified. The original $curPkg will be removed shortly; open the new app from the launcher."
                    } else {
                        "Install verification failed. The original manager was kept. APK was removed after the failed verification."
                    }
                } catch (e: Exception) {
                    "Disguise failed: ${e.message ?: "unknown error"}"
                }
            }
            loadingDialog.hide()
            snackBarHost.showSnackbar(result)
        }
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

            Box {
                OutlinedButton(onClick = { targetMenuExpanded = true }) {
                    Text(selectedTarget?.loadLabel(pm)?.toString() ?: "Choose installed app")
                }
                DropdownMenu(
                    expanded = targetMenuExpanded,
                    onDismissRequest = { targetMenuExpanded = false }
                ) {
                    if (disguiseTargets.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No launchable user apps found") },
                            onClick = { targetMenuExpanded = false },
                            enabled = false
                        )
                    } else {
                        disguiseTargets.forEach { app ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(app.loadLabel(pm).toString())
                                        Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = { applyTarget(app) }
                            )
                        }
                    }
                }
            }

            selectedTarget?.let { app ->
                Text(
                    "Selected: ${app.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                supportingText = { pkgError?.let { Text(it) } ?: Text("Current: $curPkg") },
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
                    val bmp = remember(iconBytes) { BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes!!.size) }
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
                    if (targetAlreadyInstalled && targetPackageName == pkg) {
                        "The selected package is already installed. It will not be deleted automatically. Choose a different, unused package name before continuing."
                    } else {
                        "ZSU will build and install $targetPackageName, verify that it is launchable, and only then remove the original $curPkg. If installation or verification fails, the original manager remains available."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !(targetAlreadyInstalled && targetPackageName == pkg),
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
