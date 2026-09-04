package com.mikczemny.prompter.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProtocolTest {
    @Test
    fun commandsRoundTrip() {
        val commands = listOf(
            RemoteCommand.StartRecording,
            RemoteCommand.StopRecording,
            RemoteCommand.Ping,
        )
        commands.forEach { assertEquals(it, RemoteProtocol.decode(RemoteProtocol.encode(it))) }
    }

    @Test
    fun rejectsUnknownCommands() {
        assertNull(RemoteProtocol.decode("DELETE_ALL"))
    }

    @Test
    fun pairingCodeMustHaveSixDigits() {
        assertTrue(RemoteProtocol.validPairingCode("123456"))
        assertFalse(RemoteProtocol.validPairingCode("12345"))
        assertFalse(RemoteProtocol.validPairingCode("12A456"))
    }
}
