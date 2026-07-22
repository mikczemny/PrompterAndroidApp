package com.mikczemny.prompter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand green, kept close to the original accent so the launcher icon and the
// in-app "live" colour still read as the same product.
private val Green40 = Color(0xFF1B7F3B)
private val Green80 = Color(0xFF7EE787)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF14682C),
    onPrimaryContainer = Color(0xFF9BF7A2),
    secondary = Color(0xFFB6CCB6),
    onSecondary = Color(0xFF223424),
    surface = Color(0xFF11140F),
    onSurface = Color(0xFFE1E4DB),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE1E4DB),
    outline = Color(0xFF8B9389),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9BF7A2),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    surface = Color(0xFFF7FBF1),
    onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    background = Color(0xFFF7FBF1),
    onBackground = Color(0xFF191D17),
    outline = Color(0xFF727970),
    error = Color(0xFFBA1A1A),
)

/**
 * Colours for the reading stage. Deliberately outside the Material scheme: a
 * teleprompter is light-on-black whatever the system theme or the user's
 * wallpaper says, because the surface is read at distance and often reflected
 * in glass, where any stray brightness behind the text costs contrast.
 */
object StageColors {
    val Background = Color(0xFF0B0B0C)
    val Foreground = Color(0xFFE7E7EA)
    val Muted = Color(0xFFB9B9BD)
    val Panel = Color(0xFF161618)
    val PanelRaised = Color(0xFF232327)

    /** Text outside the focus band, and text already spoken. */
    val Dimmed = Color(0xFF55555A)
    val Go = Color(0xFF2E9E4F)
    val Stop = Color(0xFFD32F2F)
    val Live = Color(0xFF7EE787)
}

/**
 * [dynamicColor] follows the Material You convention of deriving the palette
 * from the user's wallpaper on Android 12+. It applies to the setup screens
 * only — the prompter stage always uses [StageColors].
 */
@Composable
fun PrompterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
