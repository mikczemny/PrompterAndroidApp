package com.mikczemny.prompter.ui.theme

enum class UiStyle { WORKBENCH, COMMODORE, WINDOWS_311, MANUSCRIPT, MODERN }

enum class UiPalette { ORIGINAL, BOTTLE_GOLD, PAPER, GRAPHITE, AMBER, ICE, PLUM }

/** A palette belongs to a style, so exploring another desktop never loses a favourite. */
data class AppearanceSelection(
    val style: UiStyle = UiStyle.MANUSCRIPT,
    val palettes: Map<UiStyle, UiPalette> = emptyMap(),
) {
    val palette: UiPalette get() = paletteFor(style)

    fun paletteFor(target: UiStyle): UiPalette = palettes[target] ?: defaultPalette(target)

    fun withStyle(target: UiStyle): AppearanceSelection = copy(style = target)

    fun withPalette(target: UiPalette): AppearanceSelection =
        copy(palettes = palettes + (style to target))

    fun encode(): Map<String, String> = buildMap {
        put(STYLE_KEY, style.name)
        UiStyle.entries.forEach { put(paletteKey(it), paletteFor(it).name) }
    }

    companion object {
        const val STYLE_KEY = "style"

        fun paletteKey(style: UiStyle): String = "palette_" + style.name

        fun defaultPalette(style: UiStyle): UiPalette =
            if (style == UiStyle.MANUSCRIPT) UiPalette.BOTTLE_GOLD else UiPalette.ORIGINAL

        /** Unknown names and wrong preference types can come from old builds or restores. */
        fun decode(stored: Map<String, *>): AppearanceSelection {
            val style = UiStyle.entries.firstOrNull { it.name == stored[STYLE_KEY] }
                ?: UiStyle.MANUSCRIPT
            val palettes = UiStyle.entries.associateWith { target ->
                UiPalette.entries.firstOrNull { it.name == stored[paletteKey(target)] }
                    ?: defaultPalette(target)
            }
            return AppearanceSelection(style, palettes)
        }
    }
}
