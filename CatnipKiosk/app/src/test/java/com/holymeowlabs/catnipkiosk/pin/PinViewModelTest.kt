package com.holymeowlabs.catnipkiosk.pin

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.security.PinGate
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinViewModelTest {

    private val hasher = PinHasher(iterations = 1_000)

    private inner class Harness(scope: TestScope, pin: String, lockout: Boolean = true) {
        val saves = mutableListOf<SecurityState>()
        val unlocks = mutableListOf<Unit>()
        val cancels = mutableListOf<Unit>()
        private val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val vm = PinViewModel(
            initial = hasher.create(pin),
            lockoutEnabled = lockout,
            gate = PinGate(hasher) { scope.currentTime },
            save = { saves += it },
            scope = scope.backgroundScope,
            nowMs = { scope.currentTime },
            work = dispatcher,
        )

        init {
            scope.backgroundScope.launch(dispatcher) { vm.unlocked.toList(unlocks) }
            scope.backgroundScope.launch(dispatcher) { vm.cancelled.toList(cancels) }
        }

        fun type(digits: String) = digits.forEach(vm::digit)
    }

    @Test
    fun eightDigitsSubmitAutomatically() = runTest {
        val h = Harness(this, "12345678")
        h.type("12345678")
        runCurrent()
        assertThat(h.unlocks).hasSize(1)
    }

    @Test
    fun shorterPinIsNeverSubmittedUntilOk() = runTest {
        val h = Harness(this, "1234")
        h.type("1234")
        runCurrent()
        assertThat(h.unlocks).isEmpty()
        assertThat(h.saves).isEmpty()
        h.vm.ok()
        runCurrent()
        assertThat(h.unlocks).hasSize(1)
    }

    @Test
    fun okWithFewerThanFourDigitsIsNotAnAttempt() = runTest {
        val h = Harness(this, "1234")
        h.type("123")
        h.vm.ok()
        runCurrent()
        assertThat(h.saves).isEmpty()
        assertThat(h.vm.state.value.digits).isEqualTo(3)
    }

    @Test
    fun wrongPinClearsEntryAndShowsRemainingAttempts() = runTest {
        val h = Harness(this, "1234")
        h.type("9999"); h.vm.ok(); runCurrent()
        assertThat(h.vm.state.value.digits).isEqualTo(0)
        assertThat(h.vm.state.value.error).isEqualTo(PinError.Wrong(attemptsLeft = 4))
        h.type("9998"); h.vm.ok(); runCurrent()
        assertThat(h.vm.state.value.error).isEqualTo(PinError.Wrong(attemptsLeft = 3))
        assertThat(h.unlocks).isEmpty()
    }

    @Test
    fun wrongPinWithoutLockoutShowsNoCount() = runTest {
        val h = Harness(this, "1234", lockout = false)
        h.type("9999"); h.vm.ok(); runCurrent()
        assertThat(h.vm.state.value.error).isEqualTo(PinError.Wrong(attemptsLeft = null))
    }

    @Test
    fun lockedOutShowsCountdownAndIgnoresDigitsUntilItEnds() = runTest {
        val h = Harness(this, "1234")
        repeat(5) { h.type("9999"); h.vm.ok(); runCurrent() }
        assertThat(h.vm.state.value.lockedUntilMs).isEqualTo(currentTime + 60_000)
        h.type("12")
        assertThat(h.vm.state.value.digits).isEqualTo(0)
        advanceTimeBy(60_001)
        h.type("1234"); h.vm.ok(); runCurrent()
        assertThat(h.unlocks).hasSize(1)
    }

    @Test
    fun everyAttemptPersistsTheReturnedState() = runTest {
        val h = Harness(this, "1234")
        h.type("9999"); h.vm.ok(); runCurrent()
        h.type("9998"); h.vm.ok(); runCurrent()
        h.type("1234"); h.vm.ok(); runCurrent()
        assertThat(h.saves.map { it.failedAttempts }).containsExactly(1, 2, 0).inOrder()
    }

    @Test
    fun deleteRemovesTheLastDigit() = runTest {
        val h = Harness(this, "1234")
        h.type("123")
        h.vm.delete()
        assertThat(h.vm.state.value.digits).isEqualTo(2)
    }

    @Test
    fun cancelKeepsALockoutVisible() = runTest {
        val h = Harness(this, "1234")
        repeat(5) { h.type("9999"); h.vm.ok(); runCurrent() }
        val until = h.vm.state.value.lockedUntilMs
        h.vm.cancel()
        h.vm.reset()
        assertThat(h.vm.state.value.lockedUntilMs).isEqualTo(until)
        h.type("1234")
        assertThat(h.vm.state.value.digits).isEqualTo(0)
    }

    @Test
    fun attemptInFlightWhenCancelledStillCountsTowardsTheNextAttempt() = runTest {
        val gateDispatcher = StandardTestDispatcher(testScheduler)
        val saves = mutableListOf<SecurityState>()
        val vm = PinViewModel(
            initial = hasher.create("1234"),
            lockoutEnabled = true,
            gate = PinGate(hasher) { currentTime },
            save = { saves += it },
            scope = backgroundScope,
            nowMs = { currentTime },
            work = gateDispatcher,
        )
        "9999".forEach(vm::digit); vm.ok() // hashing not yet run
        vm.cancel(); vm.reset()
        "9998".forEach(vm::digit)
        assertThat(vm.state.value.digits).isEqualTo(0) // still busy: ignored
        runCurrent()
        "9998".forEach(vm::digit); vm.ok(); runCurrent()
        assertThat(saves.map { it.failedAttempts }).containsExactly(1, 2).inOrder()
        assertThat(vm.state.value.error).isEqualTo(PinError.Wrong(attemptsLeft = 3))
    }

    @Test
    fun cancelClearsTheEntryAndReportsIt() = runTest {
        val h = Harness(this, "1234")
        h.type("12")
        h.vm.cancel()
        runCurrent()
        assertThat(h.vm.state.value.digits).isEqualTo(0)
        assertThat(h.cancels).hasSize(1)
    }
}
