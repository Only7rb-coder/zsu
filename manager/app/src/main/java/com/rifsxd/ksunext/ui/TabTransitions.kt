package com.rifsxd.ksunext.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.rifsxd.ksunext.ui.screen.BottomBarDestination

/** The same motion applies to tab taps, horizontal swipes, and Back-to-Home. */
internal object TabTransitions : NavHostAnimatedDestinationStyle() {
    private val tabRoutes = BottomBarDestination.entries.map { it.direction.route }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.direction(): Int =
        tabMotionDirection(initialState.destination.route, targetState.destination.route, tabRoutes)

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val direction = direction()
        val fade = fadeIn(tween(240, easing = FastOutSlowInEasing))
        if (direction == 0) {
            fade
        } else {
            fade + slideInHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                initialOffsetX = { width -> direction * width / 5 },
            )
        }
    }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val direction = direction()
        val fade = fadeOut(tween(180, easing = FastOutSlowInEasing))
        if (direction == 0) {
            fade
        } else {
            fade + slideOutHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                targetOffsetX = { width -> -direction * width / 5 },
            )
        }
    }
}
