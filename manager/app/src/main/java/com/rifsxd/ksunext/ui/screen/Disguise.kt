package com.rifsxd.ksunext.ui.screen

import android.graphics.BitmapFactory
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

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input -> iconBytes = input.readBytes() }
        }
    }

    fun validPkg(p: String) = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$").matches(p)

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
                "Clone this app with a new identity. The clone is signed with the ZSU key, so the kernel keeps trusting it. After installing, uninstall the old app.",
                style = MaterialTheme.typography.bodyMedium
            )

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
                        return@dropUnlessResumed
                    }
                    val vc = versionCode.toLongOrNull() ?: (curVc + 1).toLong()
                    scope.launch {
                        loadingDialog.show()
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val apk = DisguiseEngine.disguise(
                                    context,
                                    DisguiseEngine.Params(pkg, appName, versionName, vc, iconBytes)
                                )
                                if (DisguiseEngine.installViaRoot(apk)) {
                                    apk.delete()
                                    "Disguised app installed: $pkg — uninstall this old app now"
                                } else {
                                    "Install failed (pm install rejected). APK kept at ${apk.absolutePath}"
                                }
                            } catch (e: Exception) {
                                "Disguise failed: ${e.message}"
                            }
                        }
                        loadingDialog.hide()
                        snackBarHost.showSnackbar(result)
                    }
                },
                enabled = validPkg(pkg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Build & install disguised app")
            }
        }
    }
}
