package com.mikczemny.prompter.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** sRGB contrast, kept independent of Android so every shipped palette can be checked on JVM. */
internal fun contrastRatio(first: Long, second: Long): Double {
    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 255).toDouble() / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
    }
    val a = luminance(first)
    val b = luminance(second)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

internal fun readableStageAccent(argb: Long): Long {
    var candidate = argb
    // Setup accents can be dark ink on paper; the same ink needs a lighter counterpart on stage.
    while (contrastRatio(candidate, STAGE_CONTROL_BACKGROUND_ARGB) < 4.5) {
        fun lighten(shift: Int): Long = (((candidate shr shift) and 255) + 16).coerceAtMost(255)
        candidate = 0xFF000000 or (lighten(16) shl 16) or (lighten(8) shl 8) or lighten(0)
    }
    return candidate
}

internal const val STAGE_BACKGROUND_ARGB = 0xFF0B0B0CL
internal const val STAGE_CONTROL_BACKGROUND_ARGB = 0xFF252B2AL
