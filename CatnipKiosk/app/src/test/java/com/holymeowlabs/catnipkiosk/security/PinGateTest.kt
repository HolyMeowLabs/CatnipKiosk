package com.holymeowlabs.catnipkiosk.security

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import org.junit.Test

class PinGateTest {

    private val hasher = PinHasher(iterations = 1_000)
    private val correctPin = "2468"
    private val wrongPin = "1357"

    private var now = 1_000_000L
    private val gate = PinGate(hasher) { now }

    private fun freshState(): SecurityState = hasher.create(correctPin)

    /** Applies [count] wrong attempts, returning the last result and the final state. */
    private fun failTimes(
        count: Int,
        start: SecurityState,
        lockoutEnabled: Boolean = true,
    ): Pair<PinResult, SecurityState> {
        var state = start
        var result: PinResult? = null
        repeat(count) {
            val (r, s) = gate.attempt(wrongPin, state, lockoutEnabled)
            result = r
            state = s
        }
        return result!! to state
    }

    // ---------------------------------------------------------------- PinRules

    @Test
    fun pinRules_acceptsFourDigits() {
        assertThat(PinRules.isValid("1234")).isTrue()
    }

    @Test
    fun pinRules_acceptsEightDigits() {
        assertThat(PinRules.isValid("12345678")).isTrue()
    }

    @Test
    fun pinRules_acceptsAllLengthsBetweenFourAndEight() {
        for (len in 4..8) {
            assertThat(PinRules.isValid("0".repeat(len))).isTrue()
        }
    }

    @Test
    fun pinRules_rejectsThreeDigits() {
        assertThat(PinRules.isValid("123")).isFalse()
    }

    @Test
    fun pinRules_rejectsNineDigits() {
        assertThat(PinRules.isValid("123456789")).isFalse()
    }

    @Test
    fun pinRules_rejectsLetters() {
        assertThat(PinRules.isValid("12a4")).isFalse()
        assertThat(PinRules.isValid("abcd")).isFalse()
    }

    @Test
    fun pinRules_rejectsSpaces() {
        assertThat(PinRules.isValid("12 34")).isFalse()
        assertThat(PinRules.isValid(" 1234")).isFalse()
        assertThat(PinRules.isValid("1234 ")).isFalse()
    }

    @Test
    fun pinRules_rejectsEmpty() {
        assertThat(PinRules.isValid("")).isFalse()
    }

    @Test
    fun pinRules_rejectsNonAsciiDigits() {
        assertThat(PinRules.isValid("١٢٣٤")).isFalse() // Arabic-Indic digits
        assertThat(PinRules.isValid("１２３４")).isFalse() // Fullwidth digits
    }

    @Test
    fun pinRules_rejectsSignsAndPunctuation() {
        assertThat(PinRules.isValid("-1234")).isFalse()
        assertThat(PinRules.isValid("12.34")).isFalse()
    }

    // ---------------------------------------------------------------- PinHasher

    @Test
    fun hasher_createThenMatches_samePin_isTrue() {
        val state = hasher.create(correctPin)
        assertThat(hasher.matches(correctPin, state)).isTrue()
    }

    @Test
    fun hasher_createThenMatches_differentPin_isFalse() {
        val state = hasher.create(correctPin)
        assertThat(hasher.matches(wrongPin, state)).isFalse()
        assertThat(hasher.matches("24680", state)).isFalse()
    }

    @Test
    fun hasher_twoCreatesOfSamePin_useDifferentSaltsAndHashes() {
        val a = hasher.create(correctPin)
        val b = hasher.create(correctPin)
        assertThat(a.pinSaltB64).isNotEqualTo(b.pinSaltB64)
        assertThat(a.pinHashB64).isNotEqualTo(b.pinHashB64)
    }

    @Test
    fun hasher_createDoesNotStorePlaintextPin() {
        val state = hasher.create(correctPin)
        assertThat(state.pinHashB64).doesNotContain(correctPin)
        assertThat(state.pinHashB64).isNotEqualTo(correctPin)
    }

    @Test
    fun hasher_createRecordsIterationsAndStartsClean() {
        val state = hasher.create(correctPin)
        assertThat(state.iterations).isEqualTo(1_000)
        assertThat(state.failedAttempts).isEqualTo(0)
        assertThat(state.lockedUntilEpochMs).isEqualTo(0L)
    }

    @Test
    fun hasher_newSalt_is16Bytes() {
        assertThat(hasher.newSalt().size).isEqualTo(16)
    }

    @Test
    fun hasher_newSalt_isRandom() {
        assertThat(hasher.newSalt().toList()).isNotEqualTo(hasher.newSalt().toList())
    }

    @Test
    fun hasher_hash_is32Bytes() {
        assertThat(hasher.hash(correctPin, hasher.newSalt()).size).isEqualTo(32)
    }

    @Test
    fun hasher_hash_isDeterministicForSamePinAndSalt() {
        val salt = hasher.newSalt()
        assertThat(hasher.hash(correctPin, salt).toList())
            .isEqualTo(hasher.hash(correctPin, salt).toList())
    }

    @Test
    fun hasher_hash_dependsOnSalt() {
        assertThat(hasher.hash(correctPin, ByteArray(16) { 1 }).toList())
            .isNotEqualTo(hasher.hash(correctPin, ByteArray(16) { 2 }).toList())
    }

    @Test
    fun hasher_hash_dependsOnIterationCount() {
        val salt = hasher.newSalt()
        assertThat(PinHasher(iterations = 1_000).hash(correctPin, salt).toList())
            .isNotEqualTo(PinHasher(iterations = 2_000).hash(correctPin, salt).toList())
    }

    @Test
    fun hasher_matches_usesIterationsStoredInState() {
        val state = PinHasher(iterations = 1_000).create(correctPin)
        val otherHasher = PinHasher(iterations = 2_000)
        assertThat(otherHasher.matches(correctPin, state)).isTrue()
        assertThat(otherHasher.matches(wrongPin, state)).isFalse()
    }

    // ---------------------------------------------------------------- PinGate: correct PIN

    @Test
    fun gate_correctPin_returnsOk() {
        val (result, _) = gate.attempt(correctPin, freshState(), lockoutEnabled = true)
        assertThat(result).isEqualTo(PinResult.Ok)
    }

    @Test
    fun gate_correctPin_withLockoutDisabled_returnsOk() {
        val (result, _) = gate.attempt(correctPin, freshState(), lockoutEnabled = false)
        assertThat(result).isEqualTo(PinResult.Ok)
    }

    @Test
    fun gate_correctPin_afterSomeFailures_resetsFailedAttemptsToZero() {
        val (_, afterFails) = failTimes(3, freshState())
        assertThat(afterFails.failedAttempts).isEqualTo(3)

        val (result, state) = gate.attempt(correctPin, afterFails, lockoutEnabled = true)
        assertThat(result).isEqualTo(PinResult.Ok)
        assertThat(state.failedAttempts).isEqualTo(0)
    }

    @Test
    fun gate_correctPin_resetsCounter_soNextWrongIsWrong4() {
        val (_, afterFails) = failTimes(4, freshState())
        val (_, afterOk) = gate.attempt(correctPin, afterFails, lockoutEnabled = true)

        val (result, _) = gate.attempt(wrongPin, afterOk, lockoutEnabled = true)
        assertThat(result).isEqualTo(PinResult.Wrong(4))
    }

    @Test
    fun gate_correctPin_preservesHashSaltAndIterations() {
        val start = freshState()
        val (_, state) = gate.attempt(correctPin, start, lockoutEnabled = true)
        assertThat(state.pinHashB64).isEqualTo(start.pinHashB64)
        assertThat(state.pinSaltB64).isEqualTo(start.pinSaltB64)
        assertThat(state.iterations).isEqualTo(start.iterations)
    }

    // ---------------------------------------------------------------- PinGate: lockout disabled

    @Test
    fun gate_wrongPin_lockoutDisabled_returnsWrongWithNullCount() {
        val (result, _) = gate.attempt(wrongPin, freshState(), lockoutEnabled = false)
        assertThat(result).isEqualTo(PinResult.Wrong(null))
    }

    @Test
    fun gate_wrongPin_lockoutDisabled_neverLocksAfterManyTries() {
        var state = freshState()
        repeat(PinGate.MAX_FAILS * 4) {
            val (result, next) = gate.attempt(wrongPin, state, lockoutEnabled = false)
            assertThat(result).isEqualTo(PinResult.Wrong(null))
            assertThat(next.lockedUntilEpochMs).isAtMost(now)
            state = next
        }
        val (result, _) = gate.attempt(correctPin, state, lockoutEnabled = false)
        assertThat(result).isEqualTo(PinResult.Ok)
    }

    // ---------------------------------------------------------------- PinGate: lockout enabled

    @Test
    fun gate_wrongPins_lockoutEnabled_countDownAttemptsBeforeLock() {
        var state = freshState()
        val expected = listOf(4, 3, 2, 1)
        for (remaining in expected) {
            val (result, next) = gate.attempt(wrongPin, state, lockoutEnabled = true)
            assertThat(result).isEqualTo(PinResult.Wrong(remaining))
            state = next
        }
        assertThat(state.failedAttempts).isEqualTo(4)
        assertThat(state.lockedUntilEpochMs).isAtMost(now)
    }

    @Test
    fun gate_fifthConsecutiveWrong_locksForSixtySeconds() {
        val (result, state) = failTimes(PinGate.MAX_FAILS, freshState())
        assertThat(result).isEqualTo(PinResult.LockedOut(now + PinGate.LOCK_MS))
        assertThat(state.lockedUntilEpochMs).isEqualTo(now + PinGate.LOCK_MS)
    }

    @Test
    fun gate_constantsMatchRequirement() {
        assertThat(PinGate.MAX_FAILS).isEqualTo(5)
        assertThat(PinGate.LOCK_MS).isEqualTo(60_000L)
    }

    @Test
    fun gate_whileLocked_correctPinIsRefusedAndStateUnchanged() {
        val (_, locked) = failTimes(PinGate.MAX_FAILS, freshState())
        now += 10_000

        val (result, state) = gate.attempt(correctPin, locked, lockoutEnabled = true)
        assertThat(result).isInstanceOf(PinResult.LockedOut::class.java)
        assertThat((result as PinResult.LockedOut).untilEpochMs).isEqualTo(locked.lockedUntilEpochMs)
        assertThat(state).isEqualTo(locked)
    }

    @Test
    fun gate_whileLocked_wrongPinIsRefusedAndStateUnchanged() {
        val (_, locked) = failTimes(PinGate.MAX_FAILS, freshState())
        now += 10_000

        val (result, state) = gate.attempt(wrongPin, locked, lockoutEnabled = true)
        assertThat(result).isInstanceOf(PinResult.LockedOut::class.java)
        assertThat(state).isEqualTo(locked)
    }

    @Test
    fun gate_lockPersistedInState_survivesNewGateInstance() {
        val (_, locked) = failTimes(PinGate.MAX_FAILS, freshState())
        now += 1_000
        val afterReboot = PinGate(PinHasher(iterations = 1_000)) { now }

        val (result, state) = afterReboot.attempt(correctPin, locked, lockoutEnabled = true)
        assertThat(result).isInstanceOf(PinResult.LockedOut::class.java)
        assertThat(state).isEqualTo(locked)
    }

    @Test
    fun gate_afterLockExpires_nextWrongStartsFreshCount() {
        val (_, locked) = failTimes(PinGate.MAX_FAILS, freshState())
        now = locked.lockedUntilEpochMs + 1

        val (result, state) = gate.attempt(wrongPin, locked, lockoutEnabled = true)
        assertThat(result).isEqualTo(PinResult.Wrong(4))
        assertThat(state.failedAttempts).isEqualTo(1)
    }

    @Test
    fun gate_afterLockExpires_correctPinIsOk() {
        val (_, locked) = failTimes(PinGate.MAX_FAILS, freshState())
        now = locked.lockedUntilEpochMs + 1

        val (result, state) = gate.attempt(correctPin, locked, lockoutEnabled = true)
        assertThat(result).isEqualTo(PinResult.Ok)
        assertThat(state.failedAttempts).isEqualTo(0)
    }

    @Test
    fun gate_clockMovedBackDuringLock_remainingLockClampedToSixtySeconds() {
        val (_, locked) = failTimes(PinGate.MAX_FAILS, freshState())
        now -= 3_600_000L // one hour back

        val (result, _) = gate.attempt(correctPin, locked, lockoutEnabled = true)
        assertThat(result).isInstanceOf(PinResult.LockedOut::class.java)
        val until = (result as PinResult.LockedOut).untilEpochMs
        assertThat(until).isAtMost(now + PinGate.LOCK_MS)
        assertThat(until).isGreaterThan(now)
    }

    @Test
    fun gate_clockMovedForwardPastUnlockTime_correctPinIsOk() {
        val (_, locked) = failTimes(PinGate.MAX_FAILS, freshState())
        now += 24L * 3_600_000L // a day forward

        val (result, _) = gate.attempt(correctPin, locked, lockoutEnabled = true)
        assertThat(result).isEqualTo(PinResult.Ok)
    }

    // ---------------------------------------------------------------- PinGate: immutability

    @Test
    fun gate_neverMutatesInputState() {
        val inputs = mutableListOf<Pair<SecurityState, SecurityState>>()
        var state = freshState()

        fun step(pin: String, lockout: Boolean) {
            val snapshot = state.copy()
            val (_, next) = gate.attempt(pin, state, lockout)
            inputs += state to snapshot
            state = next
        }

        step(wrongPin, true)
        step(correctPin, true)
        repeat(PinGate.MAX_FAILS) { step(wrongPin, true) }
        step(correctPin, true) // while locked
        step(wrongPin, false)
        now += PinGate.LOCK_MS * 2
        step(correctPin, true)

        for ((original, snapshot) in inputs) {
            assertThat(original).isEqualTo(snapshot)
        }
    }
}
