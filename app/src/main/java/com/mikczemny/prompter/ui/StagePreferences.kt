package com.mikczemny.prompter.ui

import android.content.Context

/** A reading setup can be saved without tying it to an active take or script. */
data class ReadingSettings(
    val fontSize: Float = 44f,
    val marginPercent: Float = 8f,
    val lineSpacing: Float = 1.35f,
    val anchorFraction: Float = 0.15f,
    val brightness: Float = 1f,
    val mirror: Boolean = false,
    val useCountdown: Boolean = true,
    val buttonPosition: String = "CENTER",
    val remoteEnabled: Boolean = true,
    val volumeKeysEnabled: Boolean = false,
    val focusBandEnabled: Boolean = true,
) {
    companion object {
        fun defaults(mode: PrompterMode): ReadingSettings =
            ReadingSettings(focusBandEnabled = mode == PrompterMode.EXTERNAL)
    }
}

/** Corrupt or older preferences must never create an unreadable stage. */
fun ReadingSettings.sanitized(mode: PrompterMode): ReadingSettings {
    val defaults = ReadingSettings.defaults(mode)
    return copy(
        fontSize = fontSize.finiteOr(defaults.fontSize).coerceIn(24f, 96f),
        marginPercent = marginPercent.finiteOr(defaults.marginPercent).coerceIn(0f, 40f),
        lineSpacing = lineSpacing.finiteOr(defaults.lineSpacing).coerceIn(1f, 2f),
        anchorFraction = anchorFraction.finiteOr(defaults.anchorFraction).coerceIn(0.05f, 0.6f),
        brightness = brightness.finiteOr(defaults.brightness).coerceIn(0.15f, 1f),
        buttonPosition = buttonPosition.takeIf { it in BUTTON_POSITIONS } ?: defaults.buttonPosition,
    )
}

/** Each camera setup keeps its own layout when the user switches modes. */
class StagePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "stage_preferences",
        Context.MODE_PRIVATE,
    )

    fun load(mode: PrompterMode): ReadingSettings = decodeReadingSettings(mode, preferences.all)

    fun save(mode: PrompterMode, settings: ReadingSettings) {
        val editor = preferences.edit()
        encodeReadingSettings(mode, settings).forEach { (key, value) ->
            when (value) {
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }
}

// The codec is independent of Android so mode isolation and recovery from bad
// stored values can be checked on the JVM instead of needing a device.
internal fun encodeReadingSettings(
    mode: PrompterMode,
    settings: ReadingSettings,
): Map<String, Any> = with(settings.sanitized(mode)) {
    mapOf(
        preferenceKey(mode, "font_size") to fontSize,
        preferenceKey(mode, "margin_percent") to marginPercent,
        preferenceKey(mode, "line_spacing") to lineSpacing,
        preferenceKey(mode, "anchor_fraction") to anchorFraction,
        preferenceKey(mode, "brightness") to brightness,
        preferenceKey(mode, "mirror") to mirror,
        preferenceKey(mode, "countdown") to useCountdown,
        preferenceKey(mode, "button_position") to buttonPosition,
        preferenceKey(mode, "remote_enabled") to remoteEnabled,
        preferenceKey(mode, "volume_keys_enabled") to volumeKeysEnabled,
        preferenceKey(mode, "focus_band_enabled") to focusBandEnabled,
    )
}

internal fun decodeReadingSettings(
    mode: PrompterMode,
    values: Map<String, *>,
): ReadingSettings {
    val defaults = ReadingSettings.defaults(mode)
    fun number(name: String, fallback: Float): Float =
        (values[preferenceKey(mode, name)] as? Number)?.toFloat() ?: fallback
    fun flag(name: String, fallback: Boolean): Boolean =
        values[preferenceKey(mode, name)] as? Boolean ?: fallback

    return ReadingSettings(
        fontSize = number("font_size", defaults.fontSize),
        marginPercent = number("margin_percent", defaults.marginPercent),
        lineSpacing = number("line_spacing", defaults.lineSpacing),
        anchorFraction = number("anchor_fraction", defaults.anchorFraction),
        brightness = number("brightness", defaults.brightness),
        mirror = flag("mirror", defaults.mirror),
        useCountdown = flag("countdown", defaults.useCountdown),
        buttonPosition = values[preferenceKey(mode, "button_position")] as? String
            ?: defaults.buttonPosition,
        remoteEnabled = flag("remote_enabled", defaults.remoteEnabled),
        volumeKeysEnabled = flag("volume_keys_enabled", defaults.volumeKeysEnabled),
        focusBandEnabled = flag("focus_band_enabled", defaults.focusBandEnabled),
    ).sanitized(mode)
}

private fun preferenceKey(mode: PrompterMode, name: String): String = "${mode.name}.$name"

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

private val BUTTON_POSITIONS = setOf("LEFT", "CENTER", "RIGHT")
