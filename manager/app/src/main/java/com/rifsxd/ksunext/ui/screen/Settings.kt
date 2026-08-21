package com.rifsxd.ksunext.ui.screen

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.system.OsConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.rifsxd.ksunext.ui.LocalScrollState
import com.rifsxd.ksunext.ui.rememberScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.rifsxd.ksunext.BuildConfig
import com.rifsxd.ksunext.Natives
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.component.*
import com.rifsxd.ksunext.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private data class SettingsFeatureSnapshot(
    val suCompatStatus: String = "",
    val kernelUmountStatus: String = "",
    val adbRootStatus: String = "",
    val selinuxHideStatus: String = "",
    val sulogStatus: String = "",
    val sulogEnabled: Boolean = false,
    val avcSpoofStatus: String = ""
)

/**
 * @author weishu
 * @date 2023/1/1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current

    val isManager = Natives.isManager
    val ksuVersion = if (isManager) Natives.version else null

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val bottomBarScrollState = LocalScrollState.current
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null

    val aboutDialog = rememberCustomDialog {
        AboutDialog(it)
    }
    val loadingDialog = rememberLoadingDialog()

    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            loadingDialog.show()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                getBugreportFile(context).inputStream().use {
                    it.copyTo(output)
                }
            }
            loadingDialog.hide()
            snackBarHost.showSnackbar(context.getString(R.string.log_saved))
        }
    }

    var featureSnapshot by remember { mutableStateOf(SettingsFeatureSnapshot()) }

    LaunchedEffect(Unit) {
        val featureNames = listOf(
            "su_compat",
            "kernel_umount",
            "sulog",
            "adb_root",
            "selinux_hide",
            "avc_spoof"
        )
        val (statusResults, sulogPersisted) = getFeatureStatusBatch(
            features = featureNames,
            persistedFeature = "sulog"
        )
        featureSnapshot = SettingsFeatureSnapshot(
            suCompatStatus = statusResults["su_compat"].orEmpty(),
            kernelUmountStatus = statusResults["kernel_umount"].orEmpty(),
            adbRootStatus = statusResults["adb_root"].orEmpty(),
            selinuxHideStatus = statusResults["selinux_hide"].orEmpty(),
            sulogStatus = statusResults["sulog"].orEmpty(),
            sulogEnabled = sulogPersisted == 1L,
            avcSpoofStatus = statusResults["avc_spoof"].orEmpty()
        )
    }

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackbarHost(snackBarHost, modifier = Modifier.padding(bottom = navBarPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .let { modifier ->
                    if (bottomBarScrollConnection != null) {
                        modifier
                            .nestedScroll(bottomBarScrollConnection)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
                .padding(bottom = navBarPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ksuVersion != null) {
                SettingsSectionHeader(
                    title = "Kernel controls",
                    subtitle = "Compatibility, logging, and runtime behavior"
                )
                KernelFeaturesCard(
                    suCompatStatus = featureSnapshot.suCompatStatus,
                    kernelUmountStatus = featureSnapshot.kernelUmountStatus,
                    sulogStatusParam = featureSnapshot.sulogStatus,
                    isSulogEnabled = featureSnapshot.sulogEnabled,
                    onSulogEnabledChange = { enabled ->
                        featureSnapshot = featureSnapshot.copy(sulogEnabled = enabled)
                    },
                    adbRootStatus = featureSnapshot.adbRootStatus,
                    selinuxHideStatus = featureSnapshot.selinuxHideStatus,
                    avcSpoofStatus = featureSnapshot.avcSpoofStatus,
                    scope = scope
                )
                SettingsSectionHeader(
                    title = "Security & recovery",
                    subtitle = "Protection, profiles, backups, and advanced tools"
                )
                SecurityCard(
                    navigator = navigator,
                    loadingDialog = loadingDialog,
                    scope = scope,
                    context = context
                )
            }

            SettingsSectionHeader(
                title = "Application preferences",
                subtitle = "Updates, appearance, diagnostics, and manager settings"
            )
            AppSettingsCard(
                navigator = navigator,
                prefs = prefs,
                aboutDialog = aboutDialog,
                exportBugreportLauncher = exportBugreportLauncher,
                loadingDialog = loadingDialog,
                scope = scope,
                context = context
            )

            Spacer(Modifier)
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KernelFeaturesCard(
    suCompatStatus: String,
    kernelUmountStatus: String,
    sulogStatusParam: String,
    isSulogEnabled: Boolean,
    onSulogEnabledChange: (Boolean) -> Unit,
    adbRootStatus: String,
    selinuxHideStatus: String,
    avcSpoofStatus: String,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val suCompatSupported = suCompatStatus == "supported"
    val kernelUmountSupported = kernelUmountStatus == "supported"
    val sulogSupported = sulogStatusParam == "supported"
    val adbRootSupported = adbRootStatus == "supported"
    val selinuxHideSupported = selinuxHideStatus == "supported"
    // KernelSU reports module-managed features as "managed" even though the
    // native manager ioctl can still read and apply the feature state.
    val avcSpoofSupported = avcSpoofStatus == "supported" || avcSpoofStatus == "managed"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var umountChecked by rememberSaveable {
                mutableStateOf(Natives.isDefaultUmountModules())
            }

            SwitchItem(
                icon = Icons.Filled.FolderDelete,
                title = stringResource(R.string.settings_umount_modules_default),
                summary = stringResource(R.string.settings_umount_modules_default_summary),
                checked = umountChecked,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                if (Natives.setDefaultUmountModules(it)) {
                    umountChecked = it
                }
            }

            var isSuEnabled by rememberSaveable {
                mutableStateOf(Natives.isSuEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.RemoveModerator,
                title = stringResource(R.string.settings_enable_su),
                summary = if (suCompatSupported) {
                    stringResource(R.string.settings_enable_su_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isSuEnabled,
                enabled = suCompatSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (Natives.setSuEnabled(checked)) {
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("su_compat_mode", if (checked) 0 else 2) }
                    isSuEnabled = checked
                }
            }

            var isKernelUmountEnabled by rememberSaveable {
                mutableStateOf(Natives.isKernelUmountEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.RemoveCircle,
                title = stringResource(id = R.string.settings_enable_kernel_umount),
                summary = if (kernelUmountSupported) {
                    stringResource(id = R.string.settings_enable_kernel_umount_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isKernelUmountEnabled,
                enabled = kernelUmountSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (Natives.setKernelUmountEnabled(checked)) {
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("kernel_umount_mode", if (checked) 0 else 2) }
                    isKernelUmountEnabled = checked
                }
            }

            val sulogSummary = when (sulogStatusParam) {
                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                else -> stringResource(id = R.string.settings_sulog_summary)
            }
            SwitchItem(
                icon = Icons.AutoMirrored.Filled.Article,
                title = stringResource(id = R.string.settings_sulog),
                summary = sulogSummary,
                checked = isSulogEnabled,
                enabled = sulogSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                if (execKsud("feature set sulog ${if (checked) 1 else 0}", true)) {
                    execKsud("feature save", true)
                    onSulogEnabledChange(checked)
                }
            }

            var isAdbRootEnabled by rememberSaveable {
                mutableStateOf(Natives.isAdbRootEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.Usb,
                title = stringResource(id = R.string.settings_adb_root),
                summary = if (adbRootSupported) {
                    stringResource(id = R.string.settings_adb_root_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isAdbRootEnabled,
                enabled = adbRootSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (Natives.setAdbRootEnabled(checked)) {
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("adb_root_mode", if (checked) 0 else 2) }
                    com.topjohnwu.superuser.ShellUtils.fastCmd("setprop ctl.restart adbd")
                    isAdbRootEnabled = checked
                }
            }

            var isSelinuxHideEnabled by rememberSaveable {
                mutableStateOf(Natives.isSelinuxHideEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.Policy,
                title = stringResource(id = R.string.settings_selinux_hide),
                summary = if (selinuxHideSupported) {
                    stringResource(id = R.string.settings_selinux_hide_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isSelinuxHideEnabled,
                enabled = selinuxHideSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                scope.launch(Dispatchers.IO) {
                    val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val status = Natives.setSelinuxHideEnabled(checked)
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("selinux_hide_mode", if (checked) 0 else 2) }
                    isSelinuxHideEnabled = checked
                    when (status) {
                        0 -> {}
                        -OsConstants.EAGAIN -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.settings_selinux_hide_reboot_required,
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                        else -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.settings_selinux_hide_failed, status),
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            var isSoterDisabled by rememberSaveable {
                mutableStateOf(context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("disable_soter", false))
            }
            SwitchItem(
                icon = Icons.Filled.PhonelinkErase,
                title = "Disable Soter",
                summary = "Disable Soter attestation (best effort — requires kernel/ROM support)",
                checked = isSoterDisabled,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                scope.launch(Dispatchers.IO) {
                    val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    // Best effort: forwarded to ksud feature system; no-op where unsupported
                    execKsud("feature disable_soter", true)
                    execKsud("feature save", true)
                    val packageCmd = if (checked) {
                        "pm disable-user --user 0 com.tencent.soter.soterserver || pm disable com.tencent.soter.soterserver"
                    } else {
                        "pm enable --user 0 com.tencent.soter.soterserver || pm enable com.tencent.soter.soterserver"
                    }
                    createRootShell(true).use { shell ->
                        shell.newJob().add(packageCmd).exec()
                    }
                    prefsLocal.edit { putBoolean("disable_soter", checked) }
                    isSoterDisabled = checked
                }
            }

            var isAvcSpoofEnabled by rememberSaveable {
                mutableStateOf(Natives.isAvcSpoofEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.Shield,
                title = stringResource(id = R.string.settings_enable_avc_spoof),
                summary = when {
                    avcSpoofStatus == "managed" -> stringResource(id = R.string.settings_enable_avc_spoof_managed_summary)
                    avcSpoofSupported -> stringResource(id = R.string.settings_enable_avc_spoof_summary)
                    else -> stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isAvcSpoofEnabled,
                enabled = avcSpoofSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val result = Natives.setAvcSpoofEnabled(checked)
                // Some kernels report EBUSY (-16) when the requested hook state
                // is already held by the active handler. Treat it as success only
                // when a read-back confirms the requested state.
                val stateMatches = result == -16 && Natives.isAvcSpoofEnabled() == checked
                if (result == 0 || stateMatches) {
                    val saved = execKsud("feature save", true)
                    prefsLocal.edit { putInt("avc_spoof_mode", if (checked) 0 else 2) }
                    isAvcSpoofEnabled = checked
                    if (!saved) {
                        Toast.makeText(
                            context,
                            context.getString(
                                if (stateMatches) R.string.settings_enable_avc_spoof_already_active
                                else R.string.settings_enable_avc_spoof_save_failed
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_enable_avc_spoof_failed, result),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val resetPropsPrefs = remember {
                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            }
            var resetPropsEnabled by rememberSaveable {
                mutableStateOf(resetPropsPrefs.getBoolean(ResetProps.PREF_ENABLED, false))
            }
            var resetPropsRunning by remember { mutableStateOf(false) }
            SwitchItem(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.settings_reset_props),
                summary = when {
                    resetPropsRunning -> stringResource(R.string.settings_reset_props_running)
                    resetPropsEnabled -> stringResource(R.string.settings_reset_props_enabled_summary)
                    else -> stringResource(R.string.settings_reset_props_summary)
                },
                checked = resetPropsEnabled,
                enabled = !resetPropsRunning,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { enabled ->
                resetPropsEnabled = enabled
                resetPropsPrefs.edit { putBoolean(ResetProps.PREF_ENABLED, enabled) }
                if (enabled) {
                    resetPropsRunning = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { ResetProps.run() }
                        resetPropsRunning = false
                        val message = when {
                            !result.success -> context.getString(
                                R.string.settings_reset_props_failed,
                                result.diagnostic.ifBlank {
                                    "root access, KernelSU resetprop, or property permissions unavailable"
                                }
                            )
                            result.changed == 0 && result.failed == 0 -> context.getString(
                                R.string.settings_reset_props_no_changes
                            )
                            else -> context.getString(
                                R.string.settings_reset_props_success,
                                result.changed,
                                result.failed
                            )
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityCard(
    navigator: DestinationsNavigator,
    loadingDialog: LoadingDialogHandle,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var isSelinuxPermissive by rememberSaveable {
                mutableStateOf(getSelinuxEnforce() == false)
            }

            SwitchItem(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.set_selinux),
                summary = stringResource(R.string.set_selinux_summary),
                checked = isSelinuxPermissive,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val shouldEnforce = !checked
                if (setSelinuxEnforce(shouldEnforce)) {
                    isSelinuxPermissive = !shouldEnforce
                }
            }

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(AppProfileTemplateScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Fence, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_profile_template),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_profile_template_summary))
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(BackupRestoreScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Backup, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.backup_restore),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(DeveloperScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.DeveloperBoard, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.developer),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            if (Natives.isLkmMode) {
                UninstallItem(
                    navigator = navigator,
                    withLoading = { loadingDialog.withLoading(it) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun AppSettingsCard(
    navigator: DestinationsNavigator,
    prefs: android.content.SharedPreferences,
    aboutDialog: DialogHandle,
    exportBugreportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    loadingDialog: LoadingDialogHandle,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var checkUpdate by rememberSaveable {
                mutableStateOf(prefs.getBoolean("check_update", true))
            }

            SwitchItem(
                icon = Icons.Filled.Update,
                title = stringResource(R.string.settings_check_update),
                summary = stringResource(R.string.settings_check_update_summary),
                checked = checkUpdate,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                prefs.edit { putBoolean("check_update", it) }
                checkUpdate = it
            }

            var sleepModeEnabled by rememberSaveable {
                mutableStateOf(SleepMode.isEnabled(context))
            }
            SwitchItem(
                icon = Icons.Filled.Bedtime,
                title = stringResource(R.string.settings_sleep_mode),
                summary = stringResource(R.string.settings_sleep_mode_summary),
                checked = sleepModeEnabled,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { enabled ->
                sleepModeEnabled = enabled
                scope.launch(Dispatchers.IO) {
                    val applied = SleepMode.setEnabled(context, enabled)
                    if (!applied) {
                        withContext(Dispatchers.Main) {
                            sleepModeEnabled = false
                            Toast.makeText(
                                context,
                                R.string.settings_sleep_mode_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            AppliedDpiItem(
                prefs = prefs,
                context = context,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(CustomizationScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Palette, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.customization),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(DisguiseScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.VisibilityOff, null) },
                headlineContent = {
                    Text(
                        text = "Disguise app",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = {
                    Text(
                        text = "Clone with custom package, icon & version",
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            var showBottomsheet by remember { mutableStateOf(false) }

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showBottomsheet = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.BugReport, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.export_log),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            if (showBottomsheet) {
                ExportLogBottomSheet(
                    onDismiss = { showBottomsheet = false },
                    onSave = {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                        val current = LocalDateTime.now().format(formatter)
                        exportBugreportLauncher.launch("KernelSU_Next_bugreport_${current}.tar.gz")
                        showBottomsheet = false
                    },
                    onShare = {
                        scope.launch {
                            val bugreport = loadingDialog.withLoading {
                                withContext(Dispatchers.IO) {
                                    getBugreportFile(context)
                                }
                            }
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                bugreport
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_STREAM, uri)
                                setDataAndType(uri, "application/gzip")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, context.getString(R.string.send_log))
                            )
                        }
                        showBottomsheet = false
                    }
                )
            }

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { aboutDialog.show() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.ContactPage, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.about),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    }
}

@Composable
private fun AppliedDpiItem(
    prefs: android.content.SharedPreferences,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val systemDpi = remember { Resources.getSystem().displayMetrics.densityDpi.coerceIn(120, 640) }
    var dpi by rememberSaveable {
        mutableIntStateOf(
            (AppDensity.configuredDpi(context) ?: AppDensity.currentDpi(context))
                .coerceIn(120, 640)
        )
    }

    val smallDpi = (systemDpi * 0.75f).roundToInt().coerceIn(120, 640)
    val mediumDpi = systemDpi
    val bigDpi = (systemDpi * 1.25f).roundToInt().coerceIn(120, 640)
    val oversizeDpi = (systemDpi * 1.5f).roundToInt().coerceIn(120, 640)
    val presetLabels = listOf(
        stringResource(R.string.settings_dpi_small),
        stringResource(R.string.settings_dpi_medium),
        stringResource(R.string.settings_dpi_big),
        stringResource(R.string.settings_dpi_oversize)
    )

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.FormatSize, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_applied_dpi),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_applied_dpi_summary),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Slider(
            value = dpi.toFloat(),
            onValueChange = { dpi = it.roundToInt() },
            valueRange = 120f..640f,
            steps = 519,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            presetLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_dpi_customizable, dpi),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                prefs.edit { putInt(AppDensity.PREF_KEY, dpi) }
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_dpi_applied),
                    Toast.LENGTH_SHORT
                ).show()
                refreshActivity(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_dpi_apply))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportLogBottomSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        content = {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Box {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onSave,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                        }
                        Text(
                            text = stringResource(id = R.string.save_log),
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center.also {
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            }
                        )
                    }
                }
                Box {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                        }
                        Text(
                            text = stringResource(id = R.string.send_log),
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center.also {
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun UninstallItem(
    navigator: DestinationsNavigator,
    withLoading: suspend (suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uninstallConfirmDialog = rememberConfirmDialog()
    val showTodo = {
        Toast.makeText(context, "TODO", Toast.LENGTH_SHORT).show()
    }
    val uninstallDialog = rememberUninstallDialog { uninstallType ->
        scope.launch {
            val result = uninstallConfirmDialog.awaitConfirm(
                title = context.getString(uninstallType.title),
                content = context.getString(uninstallType.message)
            )
            if (result == ConfirmResult.Confirmed) {
                withLoading {
                    when (uninstallType) {
                        UninstallType.TEMPORARY -> showTodo()
                        UninstallType.PERMANENT -> navigator.navigate(
                            FlashScreenDestination(FlashIt.FlashUninstall)
                        )
                        UninstallType.RESTORE_STOCK_IMAGE -> navigator.navigate(
                            FlashScreenDestination(FlashIt.FlashRestore)
                        )
                        UninstallType.NONE -> Unit
                    }
                }
            }
        }
    }
    val uninstall = stringResource(id = R.string.settings_uninstall)
    ListItem(
        modifier = modifier.clickable { uninstallDialog.show() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(Icons.Filled.Delete, uninstall) },
        headlineContent = {
            Text(
                text = uninstall,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

enum class UninstallType(val title: Int, val message: Int, val icon: ImageVector) {
    TEMPORARY(
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message,
        Icons.Filled.Delete
    ),
    PERMANENT(
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message,
        Icons.Filled.DeleteForever
    ),
    RESTORE_STOCK_IMAGE(
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message,
        Icons.AutoMirrored.Filled.Undo
    ),
    NONE(0, 0, Icons.Filled.Delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberUninstallDialog(onSelected: (UninstallType) -> Unit): DialogHandle {
    return rememberCustomDialog { dismiss ->
        val options = listOf(
            // UninstallType.TEMPORARY,
            UninstallType.PERMANENT,
            UninstallType.RESTORE_STOCK_IMAGE
        )
        val listOptions = options.map {
            ListOption(
                titleText = stringResource(it.title),
                subtitleText = if (it.message != 0) stringResource(it.message) else null,
                icon = IconSource(it.icon)
            )
        }

        var selection = UninstallType.NONE
        ListDialog(
            state = rememberUseCaseState(visible = true, onFinishedRequest = {
                if (selection != UninstallType.NONE) {
                    onSelected(selection)
                }
            }, onCloseRequest = {
                dismiss()
            }),
            header = Header.Default(title = stringResource(R.string.settings_uninstall)),
            selection = ListSelection.Single(
                showRadioButtons = false,
                options = listOptions,
            ) { index, _ ->
                selection = options[index]
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
private fun SettingsPreview() {
    SettingScreen(EmptyDestinationsNavigator)
}
