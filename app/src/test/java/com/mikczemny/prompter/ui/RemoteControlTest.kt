package com.mikczemny.prompter.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteControlTest {
    private val standardKeys = mapOf(
        KeyEvent.KEYCODE_SPACE to RemoteAction.TOGGLE,
        KeyEvent.KEYCODE_ENTER to RemoteAction.TOGGLE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to RemoteAction.TOGGLE,
        KeyEvent.KEYCODE_DPAD_UP to RemoteAction.PREVIOUS_LINE,
        KeyEvent.KEYCODE_DPAD_LEFT to RemoteAction.PREVIOUS_LINE,
        KeyEvent.KEYCODE_DPAD_DOWN to RemoteAction.NEXT_LINE,
        KeyEvent.KEYCODE_DPAD_RIGHT to RemoteAction.NEXT_LINE,
        KeyEvent.KEYCODE_PAGE_UP to RemoteAction.PREVIOUS_PAGE,
        KeyEvent.KEYCODE_PAGE_DOWN to RemoteAction.NEXT_PAGE,
        KeyEvent.KEYCODE_MOVE_HOME to RemoteAction.RESTART,
        KeyEvent.KEYCODE_H to RemoteAction.TOGGLE_CONTROLS,
    )

    @Test
    fun `a supported press performs exactly one action across down repeat and up`() {
        standardKeys.forEach { (keyCode, expected) ->
            val events = listOf(
                resolveRemoteKey(keyCode, KeyEvent.ACTION_DOWN),
                resolveRemoteKey(keyCode, KeyEvent.ACTION_DOWN, repeatCount = 1),
                resolveRemoteKey(keyCode, KeyEvent.ACTION_DOWN, repeatCount = 12),
                resolveRemoteKey(keyCode, KeyEvent.ACTION_UP),
            )

            assertTrue("key $keyCode must not leak into a focused button", events.all { it.handled })
            assertEquals(listOf(expected), events.mapNotNull { it.action })
        }
    }

    @Test
    fun `disabled remote leaves all events to Android`() {
        (standardKeys.keys + KeyEvent.KEYCODE_VOLUME_UP + KeyEvent.KEYCODE_VOLUME_DOWN).forEach { key ->
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
                val result = resolveRemoteKey(
                    key,
                    action,
                    remoteEnabled = false,
                    volumeKeysEnabled = true,
                )
                assertFalse(result.handled)
                assertNull(result.action)
            }
        }
    }

    @Test
    fun `modified shortcuts never trigger a stage command`() {
        standardKeys.keys.forEach { key ->
            val results = listOf(
                resolveRemoteKey(key, KeyEvent.ACTION_DOWN, ctrlPressed = true),
                resolveRemoteKey(key, KeyEvent.ACTION_DOWN, altPressed = true),
                resolveRemoteKey(key, KeyEvent.ACTION_DOWN, metaPressed = true),
                resolveRemoteKey(key, KeyEvent.ACTION_UP, ctrlPressed = true),
                resolveRemoteKey(key, KeyEvent.ACTION_UP, altPressed = true),
                resolveRemoteKey(key, KeyEvent.ACTION_UP, metaPressed = true),
            )
            assertTrue(results.none { it.handled || it.action != null })
        }
    }

    @Test
    fun `volume buttons control volume until explicitly opted in`() {
        val volumeKeys = mapOf(
            KeyEvent.KEYCODE_VOLUME_UP to RemoteAction.PREVIOUS_LINE,
            KeyEvent.KEYCODE_VOLUME_DOWN to RemoteAction.NEXT_LINE,
        )
        volumeKeys.forEach { (key, expected) ->
            assertFalse(resolveRemoteKey(key, KeyEvent.ACTION_DOWN).handled)
            assertFalse(resolveRemoteKey(key, KeyEvent.ACTION_UP).handled)

            val down = resolveRemoteKey(key, KeyEvent.ACTION_DOWN, volumeKeysEnabled = true)
            val repeat = resolveRemoteKey(key, KeyEvent.ACTION_DOWN, 1, volumeKeysEnabled = true)
            val up = resolveRemoteKey(key, KeyEvent.ACTION_UP, volumeKeysEnabled = true)
            assertEquals(expected, down.action)
            assertTrue(down.handled && repeat.handled && up.handled)
            assertNull(repeat.action)
            assertNull(up.action)
        }
    }

    @Test
    fun `unknown keys and multiple-character events remain unhandled`() {
        listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_MUTE).forEach { key ->
            assertFalse(resolveRemoteKey(key, KeyEvent.ACTION_DOWN).handled)
            assertFalse(resolveRemoteKey(key, KeyEvent.ACTION_UP).handled)
        }
        assertFalse(resolveRemoteKey(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_MULTIPLE).handled)
    }
}
