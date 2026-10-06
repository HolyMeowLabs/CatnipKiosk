package com.holymeowlabs.catnipkiosk.setup

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.NavMode
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val hasher = PinHasher(iterations = 1_000)

    private inner class Harness(scope: TestScope, isTv: Boolean = false) {
        val settings = mutableListOf<KioskSettings>()
        val security = mutableListOf<SecurityState>()
        val done = mutableListOf<Unit>()
        private val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val vm = SetupViewModel(
            isTv = isTv,
            hasher = hasher,
            saveSettings = { settings += it },
            saveSecurity = { security += it },
            scope = scope.backgroundScope,
            work = dispatcher,
        )

        init {
            scope.backgroundScope.launch(dispatcher) { vm.done.toList(done) }
        }

        fun toPinStep(url: String = "example.com/lobby") {
            vm.setUrl(url); vm.next()
            vm.next()
        }
    }

    @Test
    fun invalidStartUrlCannotAdvance() = runTest {
        val h = Harness(this)
        h.vm.setUrl("not a url")
        h.vm.next()
        assertThat(h.vm.state.value.step).isEqualTo(SetupStep.StartUrl)
        assertThat(h.vm.state.value.urlError).isTrue()
    }

    @Test
    fun validStartUrlAdvancesAndShowsTheAllowedDomain() = runTest {
        val h = Harness(this)
        h.vm.setUrl("Dashboard.Example.com/lobby")
        assertThat(h.vm.state.value.allowedDomain).isEqualTo("dashboard.example.com")
        h.vm.next()
        assertThat(h.vm.state.value.step).isEqualTo(SetupStep.Navigation)
    }

    @Test
    fun invalidExtraDomainBlocksTheNavigationStep() = runTest {
        val h = Harness(this)
        h.vm.setUrl("example.com"); h.vm.next()
        h.vm.setExtraDomains("login.example.net, bad_host.example")
        h.vm.next()
        assertThat(h.vm.state.value.step).isEqualTo(SetupStep.Navigation)
        assertThat(h.vm.state.value.extraDomainsError).isTrue()
    }

    @Test
    fun pinMismatchBlocksFinishAndSavesNothing() = runTest {
        val h = Harness(this)
        h.toPinStep()
        h.vm.setPin("1234"); h.vm.setPinConfirm("1243")
        h.vm.finish(); runCurrent()
        assertThat(h.vm.state.value.pinError).isEqualTo(PinEntryError.Mismatch)
        assertThat(h.settings).isEmpty()
        assertThat(h.security).isEmpty()
        assertThat(h.done).isEmpty()
    }

    @Test
    fun pinOutsideTheRulesBlocksFinish() = runTest {
        val h = Harness(this)
        h.toPinStep()
        h.vm.setPin("123"); h.vm.setPinConfirm("123")
        h.vm.finish(); runCurrent()
        assertThat(h.vm.state.value.pinError).isEqualTo(PinEntryError.Invalid)
        assertThat(h.security).isEmpty()
    }

    @Test
    fun finishSavesOneSettingsAndOneSecurityThenCompletes() = runTest {
        val h = Harness(this, isTv = true)
        h.vm.setUrl("example.com/lobby"); h.vm.next()
        h.vm.setNavMode(NavMode.PAGE_ONLY)
        h.vm.setIncludeSubdomains(false)
        h.vm.setExtraDomains(" Login.Example.NET \n*.cdn.example.org")
        h.vm.next()
        h.vm.setPin("246810"); h.vm.setPinConfirm("246810")
        h.vm.finish(); runCurrent()

        assertThat(h.settings).containsExactly(
            KioskSettings.defaults("https://example.com/lobby", isTv = true).copy(
                navMode = NavMode.PAGE_ONLY,
                includeSubdomains = false,
                extraDomains = listOf("login.example.net", "cdn.example.org"),
            ),
        )
        assertThat(h.security).hasSize(1)
        assertThat(hasher.matches("246810", h.security.single())).isTrue()
        assertThat(h.done).hasSize(1)
    }

    @Test
    fun backReturnsToThePreviousStep() = runTest {
        val h = Harness(this)
        h.toPinStep()
        h.vm.back()
        assertThat(h.vm.state.value.step).isEqualTo(SetupStep.Navigation)
    }
}
