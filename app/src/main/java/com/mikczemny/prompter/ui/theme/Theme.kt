package com.mikczemny.prompter.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Immutable
data class AppAppearance internal constructor(
    val selection: AppearanceSelection,
    val colors: AppearanceColors,
    val stage: StagePalette,
    val selectStyle: (UiStyle) -> Unit,
    val selectPalette: (UiPalette) -> Unit,
    val systemDarkTheme: Boolean = true,
) {
    val style: UiStyle get() = selection.style
    val palette: UiPalette get() = selection.palette
}

private val defaultSpec = paletteSpec(UiStyle.MANUSCRIPT, UiPalette.BOTTLE_GOLD, dark = true)

val LocalAppearance = staticCompositionLocalOf {
    AppAppearance(AppearanceSelection(), AppearanceColors(defaultSpec), StagePalette(defaultSpec), {}, {})
}

/** Separate from reading settings: changing a desktop never edits scripts or the active take. */
private class AppearanceStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance_v1", Context.MODE_PRIVATE)

    fun load(): AppearanceSelection = AppearanceSelection.decode(preferences.all)

    fun save(selection: AppearanceSelection) {
        preferences.edit().apply {
            selection.encode().forEach { (key, value) -> putString(key, value) }
        }.apply()
    }
}

/**
 * Explicit desktop choices take priority over wallpaper colours. The previous signature is
 * retained so callers can upgrade without rebuilding navigation or recreating the Activity.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PrompterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { AppearanceStore(context) }
    var selection by remember(store) { mutableStateOf(store.load()) }
    val spec = remember(selection.style, selection.palette, darkTheme) {
        paletteSpec(selection.style, selection.palette, darkTheme)
    }
    val appearance = remember(selection, spec, darkTheme) {
        AppAppearance(
            selection = selection,
            colors = AppearanceColors(spec),
            stage = StagePalette(spec),
            selectStyle = { style ->
                selection = selection.withStyle(style)
                store.save(selection)
            },
            selectPalette = { palette ->
                selection = selection.withPalette(palette)
                store.save(selection)
            },
            systemDarkTheme = darkTheme,
        )
    }
    val colors = appearance.colors
    val colorScheme = remember(spec) {
        (if (colors.isDark) darkColorScheme() else lightColorScheme()).copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.onAccentSoft,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.accentSoft,
            onSecondaryContainer = colors.onAccentSoft,
            tertiary = colors.accent,
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.accentSoft,
            onTertiaryContainer = colors.onAccentSoft,
            background = colors.desktop,
            onBackground = colors.onDesktop,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.muted,
            surfaceTint = Color.Transparent,
            surfaceDim = colors.surface,
            surfaceBright = colors.surfaceRaised,
            surfaceContainerLowest = colors.desktop,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceRaised,
            outline = colors.outline,
            outlineVariant = colors.outline.copy(alpha = 0.5f),
            inverseSurface = colors.text,
            inverseOnSurface = colors.surface,
            inversePrimary = colors.onAccent,
            error = if (colors.isDark) Color(0xFFFFB4AB) else Color(0xFFAC2525),
            onError = if (colors.isDark) Color(0xFF4A0808) else Color.White,
            errorContainer = if (colors.isDark) Color(0xFF6C1818) else Color(0xFFFFDAD6),
            onErrorContainer = if (colors.isDark) Color(0xFFFFDAD6) else Color(0xFF410002),
        )
    }
    val typography = remember(selection.style) { typographyFor(selection.style) }
    val radius = when (selection.style) {
        UiStyle.WORKBENCH, UiStyle.COMMODORE, UiStyle.WINDOWS_311 -> 0.dp
        UiStyle.MANUSCRIPT -> 4.dp
        UiStyle.MODERN -> 18.dp
    }
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(radius),
                small = RoundedCornerShape(radius),
                medium = RoundedCornerShape(radius),
                large = RoundedCornerShape(radius),
                extraLarge = RoundedCornerShape(radius),
            ),
            content = content,
        )
    }
}

private fun typographyFor(style: UiStyle): Typography {
    val base = Typography()
    val body = when (style) {
        UiStyle.WORKBENCH, UiStyle.COMMODORE -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val heading = if (style == UiStyle.MANUSCRIPT) FontFamily.Serif else body
    val label = if (style == UiStyle.MANUSCRIPT) FontFamily.Monospace else body
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = heading),
        displayMedium = base.displayMedium.copy(fontFamily = heading),
        displaySmall = base.displaySmall.copy(fontFamily = heading),
        headlineLarge = base.headlineLarge.copy(fontFamily = heading),
        headlineMedium = base.headlineMedium.copy(fontFamily = heading),
        headlineSmall = base.headlineSmall.copy(fontFamily = heading),
        titleLarge = base.titleLarge.copy(fontFamily = heading),
        titleMedium = base.titleMedium.copy(fontFamily = heading),
        titleSmall = base.titleSmall.copy(fontFamily = heading),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = label),
        labelMedium = base.labelMedium.copy(fontFamily = label),
        labelSmall = base.labelSmall.copy(fontFamily = label),
    )
}
