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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.navigation.NavBackStackEntry
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
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
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

        val isManager = Natives.isManager
        if (isManager) install()

        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            intent.extras?.clear()
            intent = null
        }

        if(intent != null)
            handleIntent(intent)

        setContent {
            KernelSUTheme(amoledMode = amoledModeState.value) {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination
                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val navigator = navController.rememberDestinationsNavigator()

                val isManager = Natives.isManager
                val fullFeatured = remember(isManager) {
                    isManager && !Natives.requireNewKernel() && rootAvailable()
                }

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                val homeDestination = BottomBarDestination.entries.firstOrNull()
                val startRoute = homeDestination?.direction?.route

                if (homeDestination != null && startRoute != null) {
                    BackHandler(enabled = currentRoute != startRoute && currentRoute in bottomBarRoutes) {
                        navigator.navigate(homeDestination.direction) {
                            popUpTo(NavGraphs.root) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // Track the last bottom bar destination index for directional animations
                var lastBottomBarIndex by remember { mutableStateOf(0) }
                var isBottomBarNavigation by remember { mutableStateOf(false) }
                
                // Scroll state for bottom bar visibility
                val isScrollingDown = remember { mutableStateOf(false) }
                val scrollOffset = remember { mutableStateOf(0f) }
                val previousScrollOffset = remember { mutableStateOf(0f) }
                
                // Remember the last valid navbar selection (persists across navbar hide/show)
                val lastValidNavbarSelection = remember { mutableStateOf(0) }

                LaunchedEffect(zipUri, navigateLoc, moduleActionId) {
                    if (moduleActionId != null) {
                        navigator.navigate(ExecuteModuleActionScreenDestination(moduleActionId!!))
                        moduleActionId = null
                    }

                    if (!zipUri.isNullOrEmpty()) {
                        val uris = zipUri!!
                        val component = intent?.component?.className
                        val flashIt = when {
                            component?.endsWith("FlashAnyKernel") == true -> FlashIt.FlashAnyKernel(uris.first())
                            else -> FlashIt.FlashModules(uris)
                        }
                        
                        navigator.navigate(
                            FlashScreenDestination(flashIt = flashIt)
                        )
                        zipUri = null
                    }

                    if (zipUri.isNullOrEmpty() && navigateLoc != null) {
                        when (navigateLoc) {
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
                                defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                                    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar: slide based on index direction
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                                slideInHorizontally(initialOffsetX = { it * offsetSign }, animationSpec = tween(180))
                                            }
                                            // Detail page → bottom bar: slide in from left
                                            targetRoute in bottomBarRoutes && initialRoute !in bottomBarRoutes -> {
                                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(180))
                                            }
                                            // Bottom bar → detail page: slide in from right
                                            else -> slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(180))
                                        }
                                    }

                                    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar: slide out opposite direction
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                                slideOutHorizontally(targetOffsetX = { it * offsetSign }, animationSpec = tween(180))
                                            }
                                            // Bottom bar → detail page: slide out to left
                                            initialRoute in bottomBarRoutes && targetRoute !in bottomBarRoutes -> {
                                                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(180))
                                            }
                                            // Default
                                            else -> slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(180))
                                        }
                                    }

                                    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar pop: mirror of exit
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                                slideInHorizontally(initialOffsetX = { it * offsetSign }, animationSpec = tween(180))
                                            }
                                            // Returning from detail → bottom bar: slide in from left
                                            targetRoute in bottomBarRoutes -> {
                                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(180))
                                            }
                                            else -> slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(180))
                                        }
                                    }

                                    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar pop
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                                slideOutHorizontally(targetOffsetX = { it * offsetSign }, animationSpec = tween(180))
                                            }
                                            // Detail page closing: slide out to right
                                            initialRoute !in bottomBarRoutes -> {
                                                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(180))
                                            }
                                            else -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(180))
                                        }
                                    }
                                }
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
    val isManager = remember { Natives.isManager }
    val fullFeatured = remember(isManager) {
        isManager && !Natives.requireNewKernel() && rootAvailable()
    }
    val visibleDestinations = remember(fullFeatured) {
        val destinations = BottomBarDestination.entries.filter { fullFeatured || !it.rootRequired }
        if (fullFeatured) {
            destinations.sortedBy {
                when (it) {
                    BottomBarDestination.Home -> 0
                    BottomBarDestination.Module -> 1
                    BottomBarDestination.SuperUser -> 2
                    BottomBarDestination.Ghostlock -> 3
                    BottomBarDestination.Addons -> 4
                    BottomBarDestination.Settings -> 5
                }
            }
        } else {
            destinations
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isOnBackStack = visibleDestinations.map { destination ->
        navController.isRouteOnBackStackAsState(destination.direction).value
    }
    val exactMatch = visibleDestinations.indexOfFirst { it.direction.route == currentRoute }
    val selectedIndex = if (exactMatch != -1) exactMatch else isOnBackStack.indexOfLast { it }
    if (selectedIndex != -1) lastValidSelection.value = selectedIndex
    val effectiveSelectedIndex = lastValidSelection.value.coerceIn(0, visibleDestinations.lastIndex)

    fun navigateTo(destination: BottomBarDestination) {
        if (destination.direction.route == currentRoute) return
        navigator.navigate(destination.direction) {
            popUpTo(NavGraphs.root.startRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        NavigationBar(
            modifier = Modifier.height(76.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
            tonalElevation = 0.dp
        ) {
            visibleDestinations.forEachIndexed { index, destination ->
                NavigationBarItem(
                    selected = index == effectiveSelectedIndex,
                    onClick = { navigateTo(destination) },
                    icon = {
                        val icon = if (index == effectiveSelectedIndex) {
                            destination.iconSelected
                        } else {
                            destination.iconNotSelected
                        }
                        if (destination == BottomBarDestination.Ghostlock) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == effectiveSelectedIndex) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(destination.label),
                                    tint = if (index == effectiveSelectedIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = stringResource(destination.label)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(destination.label),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
