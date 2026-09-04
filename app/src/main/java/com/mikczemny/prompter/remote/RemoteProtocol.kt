package com.mikczemny.prompter.remote

/**
 * Small text protocol used only on the local network. Keeping parsing Android-free
 * makes malformed or unexpected commands testable before sockets are introduced.
 */
sealed interface RemoteCommand {
    data object StartRecording : RemoteCommand
    data object StopRecording : RemoteCommand
    data object Ping : RemoteCommand
}

object RemoteProtocol {
    const val VERSION = 1

    fun encode(command: RemoteCommand): String = when (command) {
        RemoteCommand.StartRecording -> "START"
        RemoteCommand.StopRecording -> "STOP"
        RemoteCommand.Ping -> "PING"
    }

    fun decode(value: String): RemoteCommand? = when (value.trim().uppercase()) {
        "START" -> RemoteCommand.StartRecording
        "STOP" -> RemoteCommand.StopRecording
        "PING" -> RemoteCommand.Ping
        else -> null
    }

    fun validPairingCode(value: String): Boolean =
        value.length == PAIRING_CODE_LENGTH && value.all(Char::isDigit)

    private const val PAIRING_CODE_LENGTH = 6
}
