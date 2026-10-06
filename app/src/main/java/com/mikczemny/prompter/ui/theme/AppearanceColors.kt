package com.mikczemny.prompter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Every foreground is paired with its actual background, including coloured controls. */
internal data class PaletteSpec(
    val desktop: Long,
    val surface: Long,
    val surfaceRaised: Long,
    val text: Long,
    val muted: Long,
    val accent: Long,
    val onAccent: Long,
    val accentSoft: Long,
    val onAccentSoft: Long,
    val titleBar: Long,
    val onTitleBar: Long,
    val outline: Long,
    val bevelLight: Long,
    val bevelDark: Long,
    val dark: Boolean,
) {
    val stageAccent: Long get() = readableStageAccent(accent)
    val onDesktop: Long get() = if (contrastRatio(text, desktop) >= 4.5) text
        else if (contrastRatio(0xFFFFFFFF, desktop) >= 4.5) 0xFFFFFFFF else 0xFF101510
}

internal fun paletteSpec(style: UiStyle, palette: UiPalette, dark: Boolean): PaletteSpec = when (palette) {
    UiPalette.BOTTLE_GOLD -> PaletteSpec(
        desktop = 0xFF071C16, surface = 0xFF102B22, surfaceRaised = 0xFF19382B,
        text = 0xFFF7F0DB, muted = 0xFFB8C8BD, accent = 0xFFDBBF70, onAccent = 0xFF172218,
        accentSoft = 0xFF244535, onAccentSoft = 0xFFF1DA97,
        titleBar = 0xFF224333, onTitleBar = 0xFFEFDA99, outline = 0xFF7E9067,
        bevelLight = 0xFF668564, bevelDark = 0xFF06140F, dark = true,
    )
    UiPalette.PAPER -> PaletteSpec(
        desktop = 0xFFEFEDE7, surface = 0xFFFFFDF8, surfaceRaised = 0xFFEAE7DE,
        text = 0xFF252D29, muted = 0xFF59645E, accent = 0xFF345947, onAccent = 0xFFFFFFFF,
        accentSoft = 0xFFDFE8D9, onAccentSoft = 0xFF284738,
        titleBar = 0xFF345947, onTitleBar = 0xFFFFFDF8, outline = 0xFF879388,
        bevelLight = 0xFFFFFFFF, bevelDark = 0xFF727B71, dark = false,
    )
    UiPalette.GRAPHITE -> PaletteSpec(
        desktop = 0xFF151A1D, surface = 0xFF242A2D, surfaceRaised = 0xFF323B40,
        text = 0xFFEDF0EF, muted = 0xFFB4C1C5, accent = 0xFFCBD7DA, onAccent = 0xFF20282B,
        accentSoft = 0xFF3B4A52, onAccentSoft = 0xFFEEF3F3,
        titleBar = 0xFF323C41, onTitleBar = 0xFFEDF0EF, outline = 0xFF829397,
        bevelLight = 0xFF697E86, bevelDark = 0xFF0B0F11, dark = true,
    )
    UiPalette.AMBER -> PaletteSpec(
        desktop = 0xFF1B1107, surface = 0xFF281A0C, surfaceRaised = 0xFF382A15,
        text = 0xFFFFE6B0, muted = 0xFFCEBB98, accent = 0xFFF4B45C, onAccent = 0xFF281807,
        accentSoft = 0xFF5B3E1D, onAccentSoft = 0xFFFFE6B0,
        titleBar = 0xFF73460F, onTitleBar = 0xFFFFE8BD, outline = 0xFFB78A4C,
        bevelLight = 0xFF937445, bevelDark = 0xFF120B05, dark = true,
    )
    UiPalette.ICE -> PaletteSpec(
        desktop = 0xFFDCE7EC, surface = 0xFFF4FAFC, surfaceRaised = 0xFFE4EEF2,
        text = 0xFF1B3342, muted = 0xFF50616E, accent = 0xFF235B78, onAccent = 0xFFFFFFFF,
        accentSoft = 0xFFC2DDE8, onAccentSoft = 0xFF153D52,
        titleBar = 0xFF235B78, onTitleBar = 0xFFF4FAFC, outline = 0xFF6B8290,
        bevelLight = 0xFFFFFFFF, bevelDark = 0xFF657D8A, dark = false,
    )
    UiPalette.PLUM -> PaletteSpec(
        desktop = 0xFF211626, surface = 0xFF302035, surfaceRaised = 0xFF423049,
        text = 0xFFF4E8F1, muted = 0xFFCCB9CA, accent = 0xFFE1B2D7, onAccent = 0xFF35203D,
        accentSoft = 0xFF59385F, onAccentSoft = 0xFFF9E7F6,
        titleBar = 0xFF59385F, onTitleBar = 0xFFF9E7F6, outline = 0xFFA27DA3,
        bevelLight = 0xFF9F7DA0, bevelDark = 0xFF160B1A, dark = true,
    )
    UiPalette.ORIGINAL -> when (style) {
        UiStyle.WORKBENCH -> PaletteSpec(
            desktop = 0xFF0055AA, surface = 0xFFF2F2EA, surfaceRaised = 0xFFDDE0DA,
            text = 0xFF16222B, muted = 0xFF414F55, accent = 0xFF0055AA, onAccent = 0xFFFFFFFF,
            accentSoft = 0xFFFFAC4C, onAccentSoft = 0xFF20211B,
            titleBar = 0xFF0055AA, onTitleBar = 0xFFFFFFFF, outline = 0xFF17394D,
            bevelLight = 0xFFFFFFFF, bevelDark = 0xFF132F41, dark = false,
        )
        UiStyle.COMMODORE -> PaletteSpec(
            desktop = 0xFF6B5CB0, surface = 0xFF30256E, surfaceRaised = 0xFF40318D,
            text = 0xFFE0D9FF, muted = 0xFFC1B5F2, accent = 0xFFCCC2FF, onAccent = 0xFF251B59,
            accentSoft = 0xFF4D3D97, onAccentSoft = 0xFFE4DFFF,
            titleBar = 0xFF30256E, onTitleBar = 0xFFE0D9FF, outline = 0xFFB3A3EB,
            bevelLight = 0xFFB3A3EB, bevelDark = 0xFF20184C, dark = true,
        )
        UiStyle.WINDOWS_311 -> PaletteSpec(
            desktop = 0xFF008080, surface = 0xFFC0C0C0, surfaceRaised = 0xFFD4D4D4,
            text = 0xFF111111, muted = 0xFF414141, accent = 0xFF000080, onAccent = 0xFFFFFFFF,
            accentSoft = 0xFFE4E4E4, onAccentSoft = 0xFF151515,
            titleBar = 0xFF000080, onTitleBar = 0xFFFFFFFF, outline = 0xFF505050,
            bevelLight = 0xFFFFFFFF, bevelDark = 0xFF404040, dark = false,
        )
        UiStyle.MANUSCRIPT -> if (dark) PaletteSpec(
            desktop = 0xFF171C1A, surface = 0xFF202723, surfaceRaised = 0xFF303C34,
            text = 0xFFECEFE7, muted = 0xFFA6B3A9, accent = 0xFFB8D4B9, onAccent = 0xFF22392A,
            accentSoft = 0xFF334C3A, onAccentSoft = 0xFFE2EBDD,
            titleBar = 0xFF202723, onTitleBar = 0xFFECEFE7, outline = 0xFF687A6D,
            bevelLight = 0xFF596C5D, bevelDark = 0xFF101611, dark = true,
        ) else PaletteSpec(
            desktop = 0xFFEFEDE7, surface = 0xFFFFFDF8, surfaceRaised = 0xFFEDF2E9,
            text = 0xFF252D29, muted = 0xFF65706B, accent = 0xFF345947, onAccent = 0xFFFFFFFF,
            accentSoft = 0xFFEDF2E9, onAccentSoft = 0xFF345947,
            titleBar = 0xFFFFFDF8, onTitleBar = 0xFF252D29, outline = 0xFF849181,
            bevelLight = 0xFFFFFFFF, bevelDark = 0xFF8B978A, dark = false,
        )
        UiStyle.MODERN -> if (dark) PaletteSpec(
            desktop = 0xFF11140F, surface = 0xFF191F1B, surfaceRaised = 0xFF2B352D,
            text = 0xFFE1E4DB, muted = 0xFFBDC9BE, accent = 0xFF7EE787, onAccent = 0xFF00390F,
            accentSoft = 0xFF245E32, onAccentSoft = 0xFFCDF8CB,
            titleBar = 0xFF191F1B, onTitleBar = 0xFFE1E4DB, outline = 0xFF819484,
            bevelLight = 0xFF526956, bevelDark = 0xFF0B130D, dark = true,
        ) else PaletteSpec(
            desktop = 0xFFF0F4ED, surface = 0xFFF7FBF1, surfaceRaised = 0xFFE4EBDD,
            text = 0xFF191D17, muted = 0xFF52604F, accent = 0xFF1B7638, onAccent = 0xFFFFFFFF,
            accentSoft = 0xFFCBE9C4, onAccentSoft = 0xFF153E20,
            titleBar = 0xFFF7FBF1, onTitleBar = 0xFF191D17, outline = 0xFF788873,
            bevelLight = 0xFFFFFFFF, bevelDark = 0xFF75806F, dark = false,
        )
    }
}

@Immutable
data class AppearanceColors internal constructor(internal val spec: PaletteSpec) {
    val desktop = Color(spec.desktop)
    val onDesktop = Color(spec.onDesktop)
    val surface = Color(spec.surface)
    val surfaceRaised = Color(spec.surfaceRaised)
    val text = Color(spec.text)
    val muted = Color(spec.muted)
    val accent = Color(spec.accent)
    val onAccent = Color(spec.onAccent)
    val accentSoft = Color(spec.accentSoft)
    val onAccentSoft = Color(spec.onAccentSoft)
    val titleBar = Color(spec.titleBar)
    val onTitleBar = Color(spec.onTitleBar)
    val outline = Color(spec.outline)
    val bevelLight = Color(spec.bevelLight)
    val bevelDark = Color(spec.bevelDark)
    val isDark = spec.dark
}

/** Stage text remains light on black even when the surrounding desktop is paper-white. */
@Immutable
data class StagePalette internal constructor(private val spec: PaletteSpec) {
    val Background = Color(STAGE_BACKGROUND_ARGB)
    val Foreground = Color(0xFFE7E7EA)
    val Muted = Color(0xFFB9B9BD)
    val Panel = Color(0xFF16191A)
    val PanelRaised = Color(STAGE_CONTROL_BACKGROUND_ARGB)
    val Dimmed = Color(0xFF55555A)
    val Go = Color(spec.stageAccent)
    val OnGo = Color(0xFF101510)
    val Stop = Color(0xFFB52626)
    val OnStop = Color.White
    val Live = Color(spec.stageAccent)
    val Accent = Color(spec.stageAccent)
    val OnAccent = Color(if (contrastRatio(spec.stageAccent, 0xFF101510) >= 4.5) 0xFF101510 else 0xFFFFFFFF)
    val Outline = Color(spec.stageAccent).copy(alpha = 0.55f)
}
