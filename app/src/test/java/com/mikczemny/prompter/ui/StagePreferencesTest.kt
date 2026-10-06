package com.mikczemny.prompter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagePreferencesTest {
    @Test
    fun `fresh modes get readable defaults with a focus band only for external glass`() {
        val selfie = decodeReadingSettings(PrompterMode.SELFIE, emptyMap<String, Any>())
        val external = decodeReadingSettings(PrompterMode.EXTERNAL, emptyMap<String, Any>())

        assertEquals(ReadingSettings.defaults(PrompterMode.SELFIE), selfie)
        assertEquals(ReadingSettings.defaults(PrompterMode.EXTERNAL), external)
        assertFalse(selfie.focusBandEnabled)
        assertTrue(external.focusBandEnabled)
        assertEquals(external.copy(focusBandEnabled = false), selfie)
    }

    @Test
    fun `saving both modes preserves all settings independently`() {
        val selfie = ReadingSettings(
            fontSize = 58f,
            marginPercent = 12f,
            lineSpacing = 1.5f,
            anchorFraction = 0.3f,
            brightness = 0.8f,
            mirror = true,
            useCountdown = false,
            buttonPosition = "LEFT",
            remoteEnabled = false,
            volumeKeysEnabled = true,
            focusBandEnabled = false,
        )
        val external = ReadingSettings(
            fontSize = 66f,
            marginPercent = 25f,
            lineSpacing = 1.8f,
            anchorFraction = 0.5f,
            brightness = 0.95f,
            buttonPosition = "RIGHT",
            focusBandEnabled = true,
        )
        val values = encodeReadingSettings(PrompterMode.SELFIE, selfie) +
            encodeReadingSettings(PrompterMode.EXTERNAL, external)

        assertEquals(selfie, decodeReadingSettings(PrompterMode.SELFIE, values))
        assertEquals(external, decodeReadingSettings(PrompterMode.EXTERNAL, values))
    }

    @Test
    fun `a saved selfie setup does not replace an unused external preset`() {
        val values = encodeReadingSettings(
            PrompterMode.SELFIE,
            ReadingSettings(fontSize = 90f, mirror = true, volumeKeysEnabled = true),
        )

        assertEquals(
            ReadingSettings.defaults(PrompterMode.EXTERNAL),
            decodeReadingSettings(PrompterMode.EXTERNAL, values),
        )
    }

    @Test
    fun `non-finite dimensions fall back before reaching Compose`() {
        val settings = ReadingSettings(
            fontSize = Float.NaN,
            marginPercent = Float.POSITIVE_INFINITY,
            lineSpacing = Float.NEGATIVE_INFINITY,
            anchorFraction = Float.NaN,
            brightness = Float.NEGATIVE_INFINITY,
        ).sanitized(PrompterMode.EXTERNAL)

        assertEquals(ReadingSettings.defaults(PrompterMode.EXTERNAL), settings)
    }

    @Test
    fun `finite out-of-range values are clamped and an unknown button position recovers`() {
        val low = ReadingSettings(
            fontSize = -10f,
            marginPercent = -2f,
            lineSpacing = 0f,
            anchorFraction = -1f,
            brightness = 0f,
            buttonPosition = "UNKNOWN",
        ).sanitized(PrompterMode.EXTERNAL)
        assertEquals(24f, low.fontSize, 0f)
        assertEquals(0f, low.marginPercent, 0f)
        assertEquals(1f, low.lineSpacing, 0f)
        assertEquals(0.05f, low.anchorFraction, 0f)
        assertEquals(0.15f, low.brightness, 0f)
        assertEquals("CENTER", low.buttonPosition)

        val high = ReadingSettings(
            fontSize = 999f,
            marginPercent = 100f,
            lineSpacing = 10f,
            anchorFraction = 4f,
            brightness = 8f,
        ).sanitized(PrompterMode.EXTERNAL)
        assertEquals(96f, high.fontSize, 0f)
        assertEquals(40f, high.marginPercent, 0f)
        assertEquals(2f, high.lineSpacing, 0f)
        assertEquals(0.6f, high.anchorFraction, 0f)
        assertEquals(1f, high.brightness, 0f)
    }

    @Test
    fun `wrong stored types and malformed enum values recover field by field`() {
        val values = mapOf(
            "SELFIE.font_size" to "very large",
            "SELFIE.margin_percent" to Float.NaN,
            "SELFIE.line_spacing" to Float.POSITIVE_INFINITY,
            "SELFIE.brightness" to false,
            "SELFIE.button_position" to "MIDDLE",
            "SELFIE.remote_enabled" to "true",
            "SELFIE.focus_band_enabled" to 1,
            "SELFIE.mirror" to true,
            "EXTERNAL.font_size" to 85f,
        )

        assertEquals(
            ReadingSettings.defaults(PrompterMode.SELFIE).copy(mirror = true),
            decodeReadingSettings(PrompterMode.SELFIE, values),
        )
    }

    @Test
    fun `bad settings are normalized on save as well as on load`() {
        val invalid = ReadingSettings(
            fontSize = Float.NaN,
            marginPercent = 90f,
            brightness = -1f,
            buttonPosition = "",
        )
        val values = encodeReadingSettings(PrompterMode.EXTERNAL, invalid)

        assertEquals(44f, values["EXTERNAL.font_size"] as Float, 0f)
        assertEquals(40f, values["EXTERNAL.margin_percent"] as Float, 0f)
        assertEquals(0.15f, values["EXTERNAL.brightness"] as Float, 0f)
        assertEquals("CENTER", values["EXTERNAL.button_position"])
        assertEquals(
            invalid.sanitized(PrompterMode.EXTERNAL),
            decodeReadingSettings(PrompterMode.EXTERNAL, values),
        )
    }
}
