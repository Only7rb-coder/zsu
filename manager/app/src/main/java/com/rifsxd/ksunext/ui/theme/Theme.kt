package com.rifsxd.ksunext.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = PRIMARY_LIGHT,
    onPrimary = Color(0xFF2B0A10),
    primaryContainer = Color(0xFF5A1E29),
    onPrimaryContainer = Color(0xFFFFDADF),
    secondary = PRIMARY_DARK,
    onSecondary = Color(0xFF321016),
    secondaryContainer = Color(0xFF3A2026),
    onSecondaryContainer = Color(0xFFFFDDE0),
    tertiary = Color(0xFFEABB8C),
    onTertiary = Color(0xFF412A12),
    tertiaryContainer = Color(0xFF563C24),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = APP_DARK_BACKGROUND,
    onBackground = Color(0xFFF8F0F1),
    surface = APP_DARK_SURFACE,
    onSurface = Color(0xFFF8F0F1),
    surfaceVariant = APP_DARK_SURFACE_ELEVATED,
    onSurfaceVariant = Color(0xFFD8C5C8),
    surfaceContainerLow = Color(0xFF121014),
    surfaceContainer = APP_DARK_SURFACE,
    surfaceContainerLowest = APP_DARK_BACKGROUND,
    surfaceContainerHigh = APP_DARK_SURFACE_ELEVATED,
    surfaceContainerHighest = Color(0xFF2D1D23),
    outline = APP_DARK_OUTLINE,
    outlineVariant = Color(0xFF3D2B31),
    surfaceTint = PRIMARY_LIGHT,
    inverseSurface = Color(0xFFF8F0F1),
    inverseOnSurface = Color(0xFF21191A),
    inversePrimary = PRIMARY
)

private val LightColorScheme = lightColorScheme(
    primary = PRIMARY,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF4B111B),
    secondary = PRIMARY_LIGHT,
    onSecondary = Color(0xFF321016),
    secondaryContainer = Color(0xFFFFE8EA),
    onSecondaryContainer = Color(0xFF4B1A21),
    tertiary = Color(0xFF795735),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2C1603),
    background = APP_LIGHT_BACKGROUND,
    onBackground = Color(0xFF21191A),
    surface = APP_LIGHT_SURFACE,
    onSurface = Color(0xFF21191A),
    surfaceVariant = APP_LIGHT_SURFACE_ELEVATED,
    onSurfaceVariant = Color(0xFF655457),
    surfaceContainerLow = Color(0xFFFFFBFB),
    surfaceContainer = APP_LIGHT_SURFACE,
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = APP_LIGHT_SURFACE_ELEVATED,
    surfaceContainerHighest = Color(0xFFF1DFE1),
    outline = Color(0xFF8A6D72),
    outlineVariant = Color(0xFFE2BFC6),
    surfaceTint = PRIMARY,
    inverseSurface = Color(0xFF21191A),
    inverseOnSurface = Color(0xFFF8F0F1),
    inversePrimary = PRIMARY_LIGHT
)

fun Color.blend(other: Color, ratio: Float): Color {
    val inverse = 1f - ratio
    return Color(
        red = red * inverse + other.red * ratio,
        green = green * inverse + other.green * ratio,
        blue = blue * inverse + other.blue * ratio,
        alpha = alpha
    )
}

@Composable
fun KernelSUTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Keep the brand palette by default; callers may opt into wallpaper colors.
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        amoledMode && darkTheme && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamicScheme = dynamicDarkColorScheme(context)
            dynamicScheme.copy(
                background = AMOLED_BLACK,
                surface = AMOLED_BLACK,
                surfaceVariant = dynamicScheme.surfaceVariant.blend(AMOLED_BLACK, 0.6f),
                surfaceContainer = dynamicScheme.surfaceContainer.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerLow = dynamicScheme.surfaceContainerLow.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerLowest = dynamicScheme.surfaceContainerLowest.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerHigh = dynamicScheme.surfaceContainerHigh.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerHighest = dynamicScheme.surfaceContainerHighest.blend(AMOLED_BLACK, 0.6f)
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        amoledMode && darkTheme -> {
            DarkColorScheme.copy(
                background = AMOLED_BLACK,
                surface = AMOLED_BLACK,
                surfaceVariant = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainer = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerLow = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerLowest = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerHigh = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerHighest = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    SystemBarStyle(
        darkMode = darkTheme
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
private fun SystemBarStyle(
    darkMode: Boolean,
    statusBarScrim: Color = Color.Transparent,
    navigationBarScrim: Color = Color.Transparent,
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                statusBarScrim.toArgb(),
                statusBarScrim.toArgb(),
            ) { darkMode },
            navigationBarStyle = when {
                darkMode -> SystemBarStyle.dark(
                    navigationBarScrim.toArgb()
                )

                else -> SystemBarStyle.light(
                    navigationBarScrim.toArgb(),
                    navigationBarScrim.toArgb(),
                )
            }
        )
    }
}
