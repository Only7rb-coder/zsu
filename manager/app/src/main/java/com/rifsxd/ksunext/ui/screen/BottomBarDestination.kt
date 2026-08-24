package com.rifsxd.ksunext.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.ramcosta.composedestinations.generated.destinations.GhostlockScreenDestination
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ManageHubScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.rifsxd.ksunext.R

enum class BottomBarDestination(
    val direction: DirectionDestinationSpec,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
) {
    Home(HomeScreenDestination, R.string.home, Icons.Filled.Home, Icons.Outlined.Home, false),
    Manage(ManageHubScreenDestination, R.string.manage, Icons.Filled.Tune, Icons.Outlined.Tune, false),
    Ghostlock(GhostlockScreenDestination, R.string.ghostlock, Icons.Filled.Lock, Icons.Outlined.Lock, false),
    Settings(SettingScreenDestination, R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings, false)
}
