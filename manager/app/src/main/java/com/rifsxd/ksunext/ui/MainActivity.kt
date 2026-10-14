package com.rifsxd.ksunext.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AddonsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppProfileScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppProfileTemplateScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CustomizationScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DeveloperScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DisguiseScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.MetaModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleRepoScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SulogScreenDestination
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.rifsxd.ksunext.Natives
import com.rifsxd.ksunext.ksuApp
import com.rifsxd.ksunext.ui.screen.BottomBarDestination
import com.rifsxd.ksunext.ui.screen.FlashIt
import com.rifsxd.ksunext.ui.theme.KernelSUTheme
import com.rifsxd.ksunext.ui.util.*
import com.rifsxd.ksunext.ui.viewmodel.ModuleViewModel
import com.rifsxd.ksunext.ui.viewmodel.SuperUserViewModel

data class ScrollState(
    val isScrollingDown: MutableState<Boolean>,
    val scrollOffset: MutableState<Float>,
    val previousScrollOffset: MutableState<Float>
)

val LocalScrollState = compositionLocalOf<ScrollState?> { null }

@Composable
fun rememberScrollConnection(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                
                // Update scroll offset
                val newOffset = scrollOffset.value + delta
                scrollOffset.value = newOffset
                
                // Calculate the scroll delta from previous offset
                val scrollDelta = previousScrollOffset.value - newOffset
                
                // Only update direction if scroll delta exceeds threshold
                if (abs(scrollDelta) > threshold) {
                    isScrollingDown.value = scrollDelta > 0
                    previousScrollOffset.value = newOffset
                }
                
                return Offset.Zero
            }
            
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Reset offset tracking after fling
                previousScrollOffset.value = scrollOffset.value
                return super.onPostFling(consumed, available)
            }
        }
    }
}

fun Modifier.horizontalSwipeNavigator(
    currentRoute: String?,
    destinations: List<BottomBarDestination>,
    onNavigate: (Int) -> Unit
): Modifier = pointerInput(currentRoute) {

    var totalDrag = 0f

    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            totalDrag += dragAmount
        },
        onDragEnd = {
            val threshold = 150f

            if (kotlin.math.abs(totalDrag) > threshold) {
                val currentIndex = destinations.indexOfFirst {
                    it.direction.route == currentRoute
                }

                if (currentIndex == -1) return@detectHorizontalDragGestures

                if (totalDrag < 0) {
                    val next = (currentIndex + 1)
                        .coerceAtMost(destinations.lastIndex)
                    if (next != currentIndex) onNavigate(next)
                } else {
                    val prev = (currentIndex - 1)
                        .coerceAtLeast(0)
                    if (prev != currentIndex) onNavigate(prev)
                }
            }
        }
    )
}

fun Modifier.trackScroll(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): Modifier {
    val scrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            
            // Update scroll offset
            val newOffset = scrollOffset.value + delta
            scrollOffset.value = newOffset
            
            // Calculate the scroll delta from previous offset
            val scrollDelta = previousScrollOffset.value - newOffset
            
            // Only update direction if scroll delta exceeds threshold
            if (abs(scrollDelta) > threshold) {
                isScrollingDown.value = scrollDelta > 0
                previousScrollOffset.value = newOffset
            }
            
            return Offset.Zero
        }
        
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Reset offset tracking after fling
            previousScrollOffset.value = scrollOffset.value
            return super.onPostFling(consumed, available)
        }
    }
    
    return this.nestedScroll(scrollConnection)
}

class MainActivity : ComponentActivity() {

    var zipUri by mutableStateOf<ArrayList<Uri>?>(null)
    enum class NavigateLocation { SUPERUSER, MODULES, SETTINGS }
    var navigateLoc by mutableStateOf<NavigateLocation?>(null)
    var moduleActionId by mutableStateOf<String?>(null)
    var amoledModeState = mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())

    val moduleViewModel: ModuleViewModel by viewModels()
    val superUserViewModel: SuperUserViewModel by viewModels()

    override fun attachBaseContext(newBase: Context?) {
        val localizedContext = newBase?.let { LocaleHelper.applyLanguage(it) }
        super.attachBaseContext(localizedContext?.let { AppDensity.apply(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Sleep Mode force-stops the manager; clear its inactive state on the next launch.
        lifecycleScope.launch(Dispatchers.IO) {
            SleepMode.wakeIfNeeded(this@MainActivity)
        }

        lifecycleScope.launch {
            if (superUserViewModel.appList.isEmpty()) {
                superUserViewModel.fetchAppList()
            }
            if (moduleViewModel.moduleList.isEmpty()) {
                moduleViewModel.fetchModuleList()
            }
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        try {
            val prefsInit = getSharedPreferences("settings", MODE_PRIVATE)
            amoledModeState.value = prefsInit.getBoolean("enable_amoled", false)
        } catch (_: Exception) {}

        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        if (isManager) runCatching { install() }

        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            intent.extras?.clear()
            intent = null
        }

        if(intent != null)
            handleIntent(intent)

        setContent {
            KernelSUTheme(darkTheme = true, amoledMode = amoledModeState.value) {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination
                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val navigator = navController.rememberDestinationsNavigator()

                val isManager = runCatching { Natives.isManager }.getOrDefault(false)
                val fullFeatured = remember(isManager) {
                    isManager &&
                        runCatching { !Natives.requireNewKernel() }.getOrDefault(false) &&
                        runCatching { rootAvailable() }.getOrDefault(false)
                }

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                val homeDestination = BottomBarDestination.entries.firstOrNull()
                val startRoute = homeDestination?.direction?.route
                // Remember the last valid navbar selection (persists across navbar hide/show)
                val lastValidNavbarSelection = rememberSaveable { mutableIntStateOf(0) }

                if (homeDestination != null && startRoute != null) {
                    BackHandler(enabled = currentRoute != startRoute && currentRoute in bottomBarRoutes) {
                        lastValidNavbarSelection.value = 0
                        navigator.navigate(homeDestination.direction) {
                            popUpTo(NavGraphs.root) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // Scroll state for bottom bar visibility
                val isScrollingDown = remember { mutableStateOf(false) }
                val scrollOffset = remember { mutableStateOf(0f) }
                val previousScrollOffset = remember { mutableStateOf(0f) }

                LaunchedEffect(currentRoute) {
                    // A hidden dock belongs to the old screen's scroll state.
                    isScrollingDown.value = false
                    scrollOffset.value = 0f
                    previousScrollOffset.value = 0f
                }

                LaunchedEffect(zipUri, navigateLoc, moduleActionId) {
                    val pendingModuleId = moduleActionId?.takeIf { it.isNotBlank() }
                    if (pendingModuleId != null) {
                        navigator.navigate(ExecuteModuleActionScreenDestination(pendingModuleId))
                        moduleActionId = null
                    }

                    val pendingUris = zipUri?.takeIf { it.isNotEmpty() }
                    if (pendingUris != null) {
                        val component = intent?.component?.className
                        val firstUri = pendingUris.firstOrNull()
                        if (firstUri != null) {
                            val flashIt = when {
                                component?.endsWith("FlashAnyKernel") == true -> FlashIt.FlashAnyKernel(firstUri)
                                else -> FlashIt.FlashModules(pendingUris)
                            }
                            navigator.navigate(
                                FlashScreenDestination(flashIt = flashIt)
                            )
                        }
                        zipUri = null
                    }

                    val pendingLocation = navigateLoc
                    if (pendingUris == null && pendingLocation != null) {
                        when (pendingLocation) {
                            NavigateLocation.SUPERUSER -> navigator.navigate(SuperUserScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            NavigateLocation.MODULES -> navigator.navigate(ModuleScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            NavigateLocation.SETTINGS -> navigator.navigate(SettingScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            else -> { /* no-op for exhaustiveness */ }
                        }
                        navigateLoc = null
                    }
                }

                val showBottomBar = when (currentDestination?.route) {
                    FlashScreenDestination.route -> false // Hide for FlashScreenDestination
                    ExecuteModuleActionScreenDestination.route -> false // Hide for ExecuteModuleActionScreen
                    else -> !isScrollingDown.value
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalSnackbarHost provides snackBarHostState,
                            LocalScrollState provides ScrollState(
                                isScrollingDown = isScrollingDown,
                                scrollOffset = scrollOffset,
                                previousScrollOffset = previousScrollOffset
                            )
                        ) {
                            val visibleDestinations = remember(fullFeatured) {
                                BottomBarDestination.entries.filter { fullFeatured || !it.rootRequired }
                            }

                            fun navigateToIndex(index: Int) {
                                val destination = visibleDestinations.getOrNull(index) ?: return
                                if (destination.direction.route == currentRoute) return
                                lastValidNavbarSelection.value = index

                                navigator.navigate(destination.direction) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }

                            val hostModifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .horizontalSwipeNavigator(
                                    currentRoute = currentRoute,
                                    destinations = visibleDestinations,
                                    onNavigate = { navigateToIndex(it) }
                                )

                            DestinationsNavHost(
                                modifier = hostModifier,
                                navGraph = NavGraphs.root,
                                navController = navController,
                                defaultTransitions = TabTransitions
                            )
                        }
                        
                        // Floating Bottom Bar as overlay
                        AnimatedVisibility(
                            visible = showBottomBar,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            BottomBar(navController, lastValidNavbarSelection)
                        }
                    }
                }
            }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putBoolean("enable_amoled", enabled).apply()
        } catch (_: Exception) {}
        amoledModeState.value = enabled
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val shortcutType = intent.getStringExtra("shortcut_type")
        if (shortcutType == "module_action") {
            moduleActionId = intent.getStringExtra("module_id")
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                zipUri =
                    intent.data?.let { arrayListOf(it) }
                        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra("uris", Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra("uris")
                        }
            }

            "ACTION_SETTINGS" -> navigateLoc = NavigateLocation.SETTINGS
            "ACTION_SUPERUSER" -> navigateLoc = NavigateLocation.SUPERUSER
            "ACTION_MODULES" -> navigateLoc = NavigateLocation.MODULES
            else -> { /* ignore other actions */ }
        }
    }
}

@Composable
private fun BottomBar(
    navController: NavHostController,
    lastValidSelection: MutableState<Int>
) {
    val navigator = navController.rememberDestinationsNavigator()
    val isManager = remember { runCatching { Natives.isManager }.getOrDefault(false) }
    val fullFeatured = remember(isManager) {
        isManager &&
            runCatching { !Natives.requireNewKernel() }.getOrDefault(false) &&
            runCatching { rootAvailable() }.getOrDefault(false)
    }
    val visibleDestinations = remember(fullFeatured) {
        BottomBarDestination.entries.filter { fullFeatured || !it.rootRequired }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val exactMatch = visibleDestinations.indexOfFirst { it.direction.route == currentRoute }
    val previousRoute = navController.previousBackStackEntry?.destination?.route
    val previousRootMatch = visibleDestinations.indexOfFirst {
        destinationRouteMatches(previousRoute, it.direction.route)
    }
    val nestedRoutesByTab = remember {
        listOf(
            // Home can open the stock installer directly.
            setOf(InstallScreenDestination.route),
            // Manage owns all root-only manager workflows and their detail screens.
            setOf(
                ModuleScreenDestination.route,
                ModuleRepoScreenDestination.route,
                MetaModuleScreenDestination.route,
                AddonsScreenDestination.route,
                SuperUserScreenDestination.route,
                SulogScreenDestination.route,
                AppProfileScreenDestination.route,
                ExecuteModuleActionScreenDestination.route,
            ),
            // BL Root currently has no separate child destination.
            emptySet(),
            // Settings owns these screens. Backup/restore is intentionally omitted
            // because it can be opened from both Settings and Manage.
            setOf(
                AppProfileTemplateScreenDestination.route,
                DeveloperScreenDestination.route,
                CustomizationScreenDestination.route,
                DisguiseScreenDestination.route,
            )
        )
    }
    val nestedMatch = nestedTabIndexForRoute(currentRoute, nestedRoutesByTab)
        ?.let { nestedIndex -> visibleDestinations.getOrNull(nestedIndex)?.let { nestedIndex } }
    val resolvedNestedMatch = nestedMatch ?: previousRootMatch.takeIf { exactMatch < 0 }
    LaunchedEffect(exactMatch, resolvedNestedMatch) {
        when {
            resolvedNestedMatch != null && resolvedNestedMatch >= 0 -> {
                lastValidSelection.value = resolvedNestedMatch
            }
            // Home can be emitted transiently while a nested destination's
            // transition is starting. Only root tabs to the right update the
            // remembered selection here; explicit tab taps update Home below.
            exactMatch > 0 -> lastValidSelection.value = exactMatch
        }
    }
    val effectiveSelectedIndex = when {
        exactMatch >= 0 -> exactMatch
        resolvedNestedMatch != null && resolvedNestedMatch >= 0 -> resolvedNestedMatch
        else -> {
        lastValidSelection.value.coerceIn(0, visibleDestinations.lastIndex)
        }
    }

    fun navigateTo(destination: BottomBarDestination) {
        if (destination.direction.route == currentRoute) return
        lastValidSelection.value = visibleDestinations.indexOf(destination)
        navigator.navigate(destination.direction) {
            popUpTo(NavGraphs.root.startRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val safeInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
    val dockColor = MaterialTheme.colorScheme.surfaceContainerLow
    val backdropColor = MaterialTheme.colorScheme.background
    val backdropHeight = 106.dp + with(LocalDensity.current) { safeInsets.getBottom(this).toDp() }

    // The backdrop blocks page text behind the dock. System insets live outside
    // the fixed-height NavigationBar, rather than stealing the icon/label space.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to backdropColor.copy(alpha = 0f),
                    0.22f to backdropColor,
                    1f to backdropColor,
                    endY = with(LocalDensity.current) { backdropHeight.toPx() }
                )
            )
            .windowInsetsPadding(safeInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = dockColor,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            NavigationBar(
                modifier = Modifier.height(80.dp),
                containerColor = dockColor,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                visibleDestinations.forEachIndexed { index, destination ->
                    val selected = index == effectiveSelectedIndex
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "tabIconScale"
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.iconSelected else destination.iconNotSelected,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.label),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
