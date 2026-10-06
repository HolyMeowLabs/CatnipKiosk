package com.holymeowlabs.catnipkiosk.pin

import com.holymeowlabs.catnipkiosk.security.PinGate
import com.holymeowlabs.catnipkiosk.security.PinResult
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PinError {
    /** [attemptsLeft] is null when lockout is off. */
    data class Wrong(val attemptsLeft: Int?) : PinError
}

/** Exposes how many digits are entered, never the digits. */
data class PinUi(val digits: Int = 0, val error: PinError? = null, val lockedUntilMs: Long? = null)

/**
 * Holds the [SecurityState] in memory and processes one attempt at a time, persisting each
 * returned state; it never re-reads the store between attempts. A lockout is shown only from
 * PinGate's LockedOut result, so a stored lock from a moved wall clock is clamped first.
 */
class PinViewModel(
    initial: SecurityState,
    private val lockoutEnabled: Boolean,
    private val gate: PinGate,
    private val save: suspend (SecurityState) -> Unit,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    /** Hashing is deliberately slow; it runs here, off the main thread. */
    private val work: CoroutineDispatcher,
) {
    private var security = initial
    private val entry = StringBuilder()
    private var busy = false

    private val _state = MutableStateFlow(PinUi())
    val state: StateFlow<PinUi> = _state

    private val _unlocked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unlocked: SharedFlow<Unit> = _unlocked

    private val _cancelled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cancelled: SharedFlow<Unit> = _cancelled

    fun digit(d: Char) {
        if (busy || isLocked() || entry.length >= MAX_DIGITS || d !in '0'..'9') return
        entry.append(d)
        _state.value = PinUi(digits = entry.length)
        // Auto-submit only at the maximum, so the moment of submission never reveals a shorter PIN's length.
        if (entry.length == MAX_DIGITS) submit()
    }

    fun delete() {
        if (busy || entry.isEmpty()) return
        entry.setLength(entry.length - 1)
        _state.value = _state.value.copy(digits = entry.length)
    }

    fun ok() {
        if (busy || isLocked() || entry.length < MIN_DIGITS) return
        submit()
    }

    /**
     * Called each time the PIN screen opens. One instance serves the Activity's lifetime: a fresh
     * one per opening would start from a stale stored state and could lose an in-flight attempt.
     */
    fun reset() {
        if (!busy) entry.clear()
        _state.value = PinUi(digits = entry.length, lockedUntilMs = _state.value.lockedUntilMs)
    }

    /**
     * False once the stored credential or lockout setting is no longer the one this instance
     * checks against (PIN changed, setup redone); the owner must then replace it, or the old PIN
     * would keep working. Its own saves change only the counters, which keeps it current.
     */
    fun isCurrentFor(stored: SecurityState?, lockoutEnabled: Boolean): Boolean =
        stored != null && lockoutEnabled == this.lockoutEnabled &&
            stored.pinHashB64 == security.pinHashB64 &&
            stored.pinSaltB64 == security.pinSaltB64 &&
            stored.iterations == security.iterations

    fun cancel() {
        entry.clear()
        _state.value = PinUi(lockedUntilMs = _state.value.lockedUntilMs)
        _cancelled.tryEmit(Unit)
    }

    /** Like PinGate, clamps to at most a minute from now, so a wall clock set back can't extend the lock. */
    private fun isLocked(): Boolean {
        val until = _state.value.lockedUntilMs ?: return false
        val now = nowMs()
        val clamped = minOf(until, now + PinGate.LOCK_MS)
        if (clamped != until) {
            _state.value = _state.value.copy(lockedUntilMs = clamped)
            // Clamp the stored lock too, or the next attempt would be refused for another minute.
            if (security.lockedUntilEpochMs > clamped) {
                security = security.copy(lockedUntilEpochMs = clamped)
                val toSave = security
                scope.launch { save(toSave) }
            }
        }
        return clamped > now
    }

    private fun submit() {
        busy = true
        val pin = entry.toString()
        entry.clear()
        scope.launch {
            val (result, next) = withContext(work) { gate.attempt(pin, security, lockoutEnabled) }
            security = next
            save(next)
            _state.value = when (result) {
                PinResult.Ok -> PinUi()
                is PinResult.Wrong -> PinUi(error = PinError.Wrong(result.attemptsBeforeLock))
                is PinResult.LockedOut -> PinUi(lockedUntilMs = result.untilEpochMs)
            }
            busy = false
            if (result == PinResult.Ok) _unlocked.tryEmit(Unit)
        }
    }

    private companion object {
        const val MIN_DIGITS = 4
        const val MAX_DIGITS = 8
    }
}
