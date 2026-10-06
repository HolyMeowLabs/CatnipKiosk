package com.holymeowlabs.catnipkiosk.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Edge cases beyond the blind-authored PinGateTest. */
class PinGateEdgeCaseTest {

    private val hasher = PinHasher(iterations = 1_000)

    @Test
    fun corruptStoredRecordNeverMatchesAndNeverThrows() {
        val good = hasher.create("1234")
        for (bad in listOf(
            good.copy(iterations = 0),
            good.copy(iterations = -1),
            good.copy(pinHashB64 = "%%% not base64 %%%"),
            good.copy(pinSaltB64 = ""),
        )) {
            assertThat(hasher.matches("1234", bad)).isFalse()
        }
    }

    private var now = 1_000_000L
    private val gate = PinGate(hasher) { now }
    private val stored = hasher.create("1234")

    @Test(timeout = 5_000)
    fun absurdIterationCountIsRejectedWithoutHashing() {
        // Hashing Int.MAX_VALUE rounds would hang for hours; a cap makes this return at once.
        assertThat(hasher.matches("1234", stored.copy(iterations = Int.MAX_VALUE))).isFalse()
    }

    @Test
    fun negativeStoredFailureCountStillLocksAfterFiveWrongPins() {
        var state = stored.copy(failedAttempts = -1_000_000)
        val results = (1..5).map { gate.attempt("0000", state, true).also { state = it.second }.first }
        assertThat(results).containsExactly(
            PinResult.Wrong(4), PinResult.Wrong(3), PinResult.Wrong(2), PinResult.Wrong(1), PinResult.LockedOut(now + 60_000),
        ).inOrder()
    }

    @Test
    fun storedFailureCountAtOrAboveTheLimitLocksOnTheNextWrongPin() {
        assertThat(gate.attempt("0000", stored.copy(failedAttempts = 99), true).first).isEqualTo(PinResult.LockedOut(now + 60_000))
    }

    @Test
    fun malformedPinsAreWrongAndStillCountTowardsTheLock() {
        var state = stored
        val inputs = listOf("", "12", "1".repeat(5_000_000), "abcd", "0000")
        val results = inputs.map { gate.attempt(it, state, true).also { r -> state = r.second }.first }
        assertThat(results.last()).isEqualTo(PinResult.LockedOut(now + 60_000))
    }

    @Test
    fun malformedPinWithLockoutOffIsWrongWithoutError() {
        assertThat(gate.attempt("", stored, false).first).isEqualTo(PinResult.Wrong(null))
    }

    @Test
    fun lockEndsExactlyAtItsUnlockTime() {
        val locked = stored.copy(lockedUntilEpochMs = now)
        assertThat(gate.attempt("1234", locked, true).first).isEqualTo(PinResult.Ok)
    }

    @Test
    fun wrongPinWithLockoutOffLeavesTheStoredStateUntouched() {
        val (_, after) = gate.attempt("0000", stored.copy(failedAttempts = 2), false)
        assertThat(after).isEqualTo(stored.copy(failedAttempts = 2))
    }

    @Test
    fun clockMovedBackPersistsTheClampedUnlockTime() {
        val locked = stored.copy(lockedUntilEpochMs = now + 3_600_000)
        val (result, after) = gate.attempt("1234", locked, true)
        assertThat(result).isEqualTo(PinResult.LockedOut(now + 60_000))
        assertThat(after.lockedUntilEpochMs).isEqualTo(now + 60_000)
    }

    @Test
    fun smallClockMoveBackDoesNotExtendTheLock() {
        val locked = stored.copy(lockedUntilEpochMs = now + 20_000)
        now -= 10_000
        assertThat(gate.attempt("1234", locked, true).first).isEqualTo(PinResult.LockedOut(locked.lockedUntilEpochMs))
    }

    @Test
    fun corruptRecordsThroughTheGateAreWrongNotCrashes() {
        for (bad in listOf(stored.copy(pinSaltB64 = "!!"), stored.copy(pinHashB64 = ""), stored.copy(iterations = 0))) {
            assertThat(gate.attempt("1234", bad, true).first).isEqualTo(PinResult.Wrong(4))
        }
    }

    @Test
    fun aStoredLockIsIgnoredOnceLockoutIsDisabled() {
        val now = 1_000_000L
        val locked = hasher.create("1234").copy(lockedUntilEpochMs = now + 30_000)
        assertThat(PinGate(hasher) { now }.attempt("1234", locked, lockoutEnabled = false).first).isEqualTo(PinResult.Ok)
    }

    @Test
    fun staleLockIsClearedSoMovingTheClockBackLaterCannotRelock() {
        var now = 1_000_000L
        val gate = PinGate(hasher) { now }
        var state = hasher.create("1234")
        repeat(PinGate.MAX_FAILS) { state = gate.attempt("0000", state, lockoutEnabled = true).second }
        val lockedUntil = state.lockedUntilEpochMs

        now = lockedUntil + 1
        state = gate.attempt("0000", state, lockoutEnabled = true).second

        now = lockedUntil - 10_000
        assertThat(gate.attempt("0000", state, lockoutEnabled = true).first).isEqualTo(PinResult.Wrong(3))
    }
}
