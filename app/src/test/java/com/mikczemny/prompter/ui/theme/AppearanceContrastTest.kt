package com.mikczemny.prompter.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceContrastTest {
    @Test
    fun allDesktopAndControlTextPairsMeetNormalTextContrast() {
        UiStyle.entries.forEach { style ->
            UiPalette.entries.forEach { palette ->
                listOf(false, true).forEach { dark ->
                    val spec = paletteSpec(style, palette, dark)
                    val pairs = mapOf(
                        "body" to (spec.text to spec.surface),
                        "raised body" to (spec.text to spec.surfaceRaised),
                        "muted" to (spec.muted to spec.surface),
                        "raised muted" to (spec.muted to spec.surfaceRaised),
                        "primary button" to (spec.onAccent to spec.accent),
                        "tonal button" to (spec.onAccentSoft to spec.accentSoft),
                        "title bar" to (spec.onTitleBar to spec.titleBar),
                        "desktop" to (spec.onDesktop to spec.desktop),
                    )
                    pairs.forEach { (name, pair) ->
                        val ratio = contrastRatio(pair.first, pair.second)
                        assertTrue("$style / $palette / dark=$dark / $name: $ratio", ratio >= 4.5)
                    }
                }
            }
        }
    }

    @Test
    fun stageAccentIsReadableOnEveryStageSurface() {
        UiStyle.entries.forEach { style ->
            UiPalette.entries.forEach { palette ->
                listOf(false, true).forEach { dark ->
                    val accent = paletteSpec(style, palette, dark).stageAccent
                    listOf(STAGE_BACKGROUND_ARGB, 0xFF16191A, STAGE_CONTROL_BACKGROUND_ARGB).forEach { background ->
                        assertTrue("$style / $palette on stage", contrastRatio(accent, background) >= 4.5)
                    }
                    assertTrue("$style / $palette start label", contrastRatio(accent, 0xFF101510) >= 4.5)
                }
            }
        }
    }

    @Test
    fun explicitColourPalettesDoNotChangeWhenAndroidDarkModeChanges() {
        UiStyle.entries.forEach { style ->
            UiPalette.entries.filter { it != UiPalette.ORIGINAL }.forEach { palette ->
                assertEquals(paletteSpec(style, palette, false), paletteSpec(style, palette, true))
            }
        }
    }

    @Test
    fun contrastUsesLinearSrgbRatherThanAveragingChannels() {
        assertEquals(21.0, contrastRatio(0xFFFFFFFF, 0xFF000000), 0.001)
        assertEquals(1.0, contrastRatio(0xFF224333, 0xFF224333), 0.001)
    }
}
