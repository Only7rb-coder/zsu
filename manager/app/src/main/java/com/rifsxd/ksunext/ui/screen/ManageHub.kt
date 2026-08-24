package com.rifsxd.ksunext.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AddonsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BackupRestoreScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.rifsxd.ksunext.Natives
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.util.rootAvailable

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ManageHubScreen(navigator: DestinationsNavigator) {
    val rootPresent = remember { runCatching { rootAvailable() }.getOrDefault(false) }
    val managerReady = remember {
        runCatching { Natives.isManager && !Natives.requireNewKernel() }.getOrDefault(false)
    }
    val actionsAvailable = rootPresent && managerReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.manage),
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = stringResource(
                                if (actionsAvailable) R.string.manage_root_ready else R.string.manage_root_unavailable
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManageActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Layers,
                    title = stringResource(R.string.module),
                    summary = stringResource(R.string.manage_modules_summary),
                    enabled = actionsAvailable,
                    onClick = { navigator.navigate(ModuleScreenDestination) }
                )
                ManageActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AdminPanelSettings,
                    title = stringResource(R.string.superuser),
                    summary = stringResource(R.string.manage_superuser_summary),
                    enabled = actionsAvailable,
                    onClick = { navigator.navigate(SuperUserScreenDestination) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManageActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Extension,
                    title = stringResource(R.string.addons),
                    summary = stringResource(R.string.manage_addons_summary),
                    enabled = actionsAvailable,
                    onClick = { navigator.navigate(AddonsScreenDestination) }
                )
                ManageActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Backup,
                    title = stringResource(R.string.backup_restore),
                    summary = stringResource(R.string.manage_backup_summary),
                    enabled = actionsAvailable,
                    onClick = { navigator.navigate(BackupRestoreScreenDestination) }
                )
            }
        }
    }
}

@Composable
private fun ManageActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.05f))
                    .padding(9.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (enabled) summary else stringResource(R.string.manage_root_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
