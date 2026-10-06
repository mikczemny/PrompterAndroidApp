package com.mikczemny.prompter.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSelectionTest {
    @Test
    fun freshInstallStartsWithBottleGreenAndGoldManuscript() {
        val selection = AppearanceSelection.decode(emptyMap<String, String>())
        assertEquals(UiStyle.MANUSCRIPT, selection.style)
        assertEquals(UiPalette.BOTTLE_GOLD, selection.palette)
    }

    @Test
    fun switchingDesktopsRestoresEachDesktopsPalette() {
        val selection = AppearanceSelection()
            .withPalette(UiPalette.PLUM)
            .withStyle(UiStyle.WORKBENCH)
            .withPalette(UiPalette.AMBER)
            .withStyle(UiStyle.WINDOWS_311)
            .withPalette(UiPalette.ICE)
        val restored = AppearanceSelection.decode(selection.encode())
        assertEquals(UiStyle.WINDOWS_311, restored.style)
        assertEquals(UiPalette.ICE, restored.palette)
        assertEquals(UiPalette.PLUM, restored.withStyle(UiStyle.MANUSCRIPT).palette)
        assertEquals(UiPalette.AMBER, restored.withStyle(UiStyle.WORKBENCH).palette)
        assertEquals(UiPalette.ORIGINAL, restored.withStyle(UiStyle.COMMODORE).palette)
    }

    @Test
    fun aStyleFirstOpenedUsesItsHistoricalPalette() {
        UiStyle.entries.filter { it != UiStyle.MANUSCRIPT }.forEach {
            assertEquals(UiPalette.ORIGINAL, AppearanceSelection().withStyle(it).palette)
        }
    }

    @Test
    fun unknownAndWronglyTypedValuesFallBackWithoutDiscardingValidPreferences() {
        val stored = mapOf(
            AppearanceSelection.STYLE_KEY to "REMOVED_THEME",
            AppearanceSelection.paletteKey(UiStyle.MANUSCRIPT) to true,
            AppearanceSelection.paletteKey(UiStyle.WORKBENCH) to "REMOVED_PALETTE",
            AppearanceSelection.paletteKey(UiStyle.COMMODORE) to UiPalette.GRAPHITE.name,
            AppearanceSelection.paletteKey(UiStyle.WINDOWS_311) to 42,
        )
        val restored = AppearanceSelection.decode(stored)
        assertEquals(UiStyle.MANUSCRIPT, restored.style)
        assertEquals(UiPalette.BOTTLE_GOLD, restored.palette)
        assertEquals(UiPalette.ORIGINAL, restored.paletteFor(UiStyle.WORKBENCH))
        assertEquals(UiPalette.GRAPHITE, restored.paletteFor(UiStyle.COMMODORE))
        assertEquals(UiPalette.ORIGINAL, restored.paletteFor(UiStyle.WINDOWS_311))
    }

    @Test
    fun explicitlyChoosingOriginalManuscriptSurvivesRestart() {
        val restored = AppearanceSelection.decode(
            AppearanceSelection().withPalette(UiPalette.ORIGINAL).encode(),
        )
        assertEquals(UiPalette.ORIGINAL, restored.palette)
    }
}
