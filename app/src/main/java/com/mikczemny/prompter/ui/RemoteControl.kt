package com.mikczemny.prompter.ui

import android.view.KeyEvent

enum class RemoteAction {
    TOGGLE,
    PREVIOUS_LINE,
    NEXT_LINE,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    RESTART,
    TOGGLE_CONTROLS,
}

data class RemoteKeyResult(
    val handled: Boolean,
    val action: RemoteAction? = null,
)

fun handleRemoteKey(
    event: KeyEvent,
    remoteEnabled: Boolean = true,
    volumeKeysEnabled: Boolean = false,
): RemoteKeyResult = resolveRemoteKey(
    keyCode = event.keyCode,
    eventAction = event.action,
    repeatCount = event.repeatCount,
    ctrlPressed = event.isCtrlPressed,
    altPressed = event.isAltPressed,
    metaPressed = event.isMetaPressed,
    remoteEnabled = remoteEnabled,
    volumeKeysEnabled = volumeKeysEnabled,
)

/**
 * HID remotes and pedals arrive as ordinary keys. Only the initial down event
 * performs an action, but its repeat and release must also be consumed so a
 * focused Compose button cannot receive the second half of the same press.
 */
fun resolveRemoteKey(
    keyCode: Int,
    eventAction: Int,
    repeatCount: Int = 0,
    ctrlPressed: Boolean = false,
    altPressed: Boolean = false,
    metaPressed: Boolean = false,
    remoteEnabled: Boolean = true,
    volumeKeysEnabled: Boolean = false,
): RemoteKeyResult {
    if (!remoteEnabled || ctrlPressed || altPressed || metaPressed) return UNHANDLED_KEY
    if (eventAction != KeyEvent.ACTION_DOWN && eventAction != KeyEvent.ACTION_UP) {
        return UNHANDLED_KEY
    }

    val action = when (keyCode) {
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> RemoteAction.TOGGLE

        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteAction.PREVIOUS_LINE

        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteAction.NEXT_LINE

        KeyEvent.KEYCODE_PAGE_UP -> RemoteAction.PREVIOUS_PAGE
        KeyEvent.KEYCODE_PAGE_DOWN -> RemoteAction.NEXT_PAGE
        KeyEvent.KEYCODE_MOVE_HOME -> RemoteAction.RESTART
        KeyEvent.KEYCODE_H -> RemoteAction.TOGGLE_CONTROLS

        // Volume buttons keep their normal job unless the user explicitly
        // enables the mapping for a shutter remote or a pedal using these keys.
        KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeysEnabled) RemoteAction.PREVIOUS_LINE else null
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeysEnabled) RemoteAction.NEXT_LINE else null
        else -> null
    } ?: return UNHANDLED_KEY

    return RemoteKeyResult(
        handled = true,
        action = action.takeIf { eventAction == KeyEvent.ACTION_DOWN && repeatCount == 0 },
    )
}

private val UNHANDLED_KEY = RemoteKeyResult(handled = false)
