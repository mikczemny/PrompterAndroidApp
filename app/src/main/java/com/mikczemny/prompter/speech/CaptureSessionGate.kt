package com.mikczemny.prompter.speech

/**
 * Keeps a microphone session reserved from model preparation through resource
 * cleanup. A stop is terminal for its token: only [finish] permits another
 * start, so a delayed download or focus callback cannot revive an old session.
 *
 * The capture owner also synchronizes on this gate when publishing resources
 * and callbacks, keeping those side effects in the same order as stop/start.
 * This class has no Android dependencies so those races can be tested on the JVM.
 */
internal class CaptureSessionGate {
    class Session internal constructor()

    private class Active(val session: Session) {
        var listening = false
        var stopping = false
        var resetPending = false
    }

    private var active: Active? = null

    val isActive: Boolean
        get() = synchronized(this) { active != null }

    val isListening: Boolean
        get() = synchronized(this) { active?.let { it.listening && !it.stopping } == true }

    @Synchronized
    fun begin(): Session? {
        if (active != null) return null
        return Session().also { active = Active(it) }
    }

    /** Starting the microphone and accepting a stop must share one lock. */
    @Synchronized
    fun startListening(session: Session, startMicrophone: () -> Unit): Boolean {
        val current = active ?: return false
        if (current.session !== session || current.stopping || current.listening) return false
        startMicrophone()
        current.listening = true
        return true
    }

    @Synchronized
    fun mayContinue(session: Session): Boolean =
        active?.let { it.session === session && !it.stopping } == true

    @Synchronized
    fun isListening(session: Session): Boolean =
        active?.let { it.session === session && it.listening && !it.stopping } == true

    @Synchronized
    fun requestStop(): Session? {
        val session = active?.session ?: return null
        return session.takeIf { requestStop(it) }
    }

    /** A focus callback for an earlier session must not stop its replacement. */
    @Synchronized
    fun requestStop(session: Session): Boolean {
        val current = active ?: return false
        if (current.session !== session || current.stopping) return false
        current.stopping = true
        current.resetPending = false
        return true
    }

    @Synchronized
    fun requestReset() {
        active?.takeUnless { it.stopping }?.resetPending = true
    }

    @Synchronized
    fun consumeReset(session: Session): Boolean {
        val current = active ?: return false
        if (current.session !== session || current.stopping ||
            !current.listening || !current.resetPending
        ) return false
        current.resetPending = false
        return true
    }

    /** Suppress a result computed while the user was jumping or stopping. */
    @Synchronized
    fun mayEmit(session: Session): Boolean =
        active?.let {
            it.session === session && it.listening && !it.stopping && !it.resetPending
        } == true

    /** Called only after every resource belonging to the session is released. */
    @Synchronized
    fun finish(session: Session): Boolean {
        if (active?.session !== session) return false
        active = null
        return true
    }
}
