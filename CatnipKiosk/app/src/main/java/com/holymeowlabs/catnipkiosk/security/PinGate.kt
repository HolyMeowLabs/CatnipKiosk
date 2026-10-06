package com.holymeowlabs.catnipkiosk.security

import com.holymeowlabs.catnipkiosk.settings.SecurityState
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinRules {
    /** 4 to 8 ASCII digits. */
    fun isValid(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }
}

/** Salted PBKDF2-HMAC-SHA256. The iteration count travels with each stored hash. */
class PinHasher(private val iterations: Int = DEFAULT_ITERATIONS) {
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray): ByteArray = hash(pin, salt, iterations)

    fun create(pin: String): SecurityState {
        val salt = newSalt()
        return SecurityState(
            pinHashB64 = encoder.encodeToString(hash(pin, salt)),
            pinSaltB64 = encoder.encodeToString(salt),
            iterations = iterations,
        )
    }

    fun matches(pin: String, state: SecurityState): Boolean {
        // Android's PBKDF2 provider throws on an empty password, so malformed input never reaches it.
        if (!PinRules.isValid(pin)) return false
        val expected = decodeOrNull(state.pinHashB64) ?: return false
        val salt = decodeOrNull(state.pinSaltB64) ?: return false
        if (state.iterations !in 1..MAX_ITERATIONS) return false
        return MessageDigest.isEqual(hash(pin, salt, state.iterations), expected)
    }

    private fun hash(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeOrNull(text: String): ByteArray? =
        try {
            decoder.decode(text).takeIf { it.isNotEmpty() }
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        /**
         * Chosen for a ~100–300 ms verify (PinHasherBenchmarkTest). Provisional: measured on the
         * Android TV x86_64 emulator only; re-measure on real TV hardware before release.
         */
        const val DEFAULT_ITERATIONS: Int = 60_000
        /** Stored counts above this are treated as corrupt rather than hashed for hours. */
        const val MAX_ITERATIONS: Int = 10_000_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()
    }
}

sealed interface PinResult {
    data object Ok : PinResult
    /** [attemptsBeforeLock] is null when lockout is disabled. */
    data class Wrong(val attemptsBeforeLock: Int?) : PinResult
    data class LockedOut(val untilEpochMs: Long) : PinResult
}

class PinGate(private val hasher: PinHasher, private val nowMs: () -> Long) {

    /** Returns the result and the state to persist. Never mutates [state]. */
    fun attempt(pin: String, state: SecurityState, lockoutEnabled: Boolean): Pair<PinResult, SecurityState> {
        val now = nowMs()
        if (lockoutEnabled && state.lockedUntilEpochMs > now) {
            // Clamp in case the wall clock moved back during the lock.
            val until = minOf(state.lockedUntilEpochMs, now + LOCK_MS)
            return PinResult.LockedOut(until) to state.copy(lockedUntilEpochMs = until)
        }
        if (hasher.matches(pin, state)) {
            return PinResult.Ok to state.copy(failedAttempts = 0, lockedUntilEpochMs = 0)
        }
        if (!lockoutEnabled) return PinResult.Wrong(null) to state

        // Locking resets failedAttempts, so an expired lock already starts a fresh count.
        val fails = state.failedAttempts.coerceIn(0, MAX_FAILS - 1) + 1
        return if (fails >= MAX_FAILS) {
            val until = now + LOCK_MS
            PinResult.LockedOut(until) to state.copy(failedAttempts = 0, lockedUntilEpochMs = until)
        } else {
            PinResult.Wrong(MAX_FAILS - fails) to state.copy(failedAttempts = fails, lockedUntilEpochMs = 0)
        }
    }

    companion object {
        const val MAX_FAILS = 5
        const val LOCK_MS = 60_000L
    }
}
