package com.holymeowlabs.catnipkiosk.settingsui

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.ScheduledReload
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import com.holymeowlabs.catnipkiosk.setup.Scheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val hasher = PinHasher(iterations = 1_000)

    private inner class Harness(scope: TestScope, initial: KioskSettings = KioskSettings(startUrl = "https://example.com/")) {
        val savedSettings = mutableListOf<KioskSettings>()
        val savedSecurity = mutableListOf<SecurityState>()
        var security: SecurityState = hasher.create("1234")
        val vm = SettingsViewModel(
            initial = initial,
            security = { security },
            hasher = hasher,
            saveSettings = { savedSettings += it },
            saveSecurity = { savedSecurity += it; security = it },
            scope = scope.backgroundScope,
            work = UnconfinedTestDispatcher(scope.testScheduler),
        )
    }

    @Test
    fun updatePersistsTheLatestSettings() = runTest {
        val h = Harness(this)
        h.vm.update { it.copy(keepScreenOn = false) }
        h.vm.update { it.copy(scheduledReload = ScheduledReload.EveryMinutes(30)) }
        runCurrent()
        assertThat(h.vm.settings.value.keepScreenOn).isFalse()
        assertThat(h.savedSettings.last()).isEqualTo(h.vm.settings.value)
    }

    @Test
    fun invalidExtraDomainIsRejectedAndNotStored() = runTest {
        val h = Harness(this)
        assertThat(h.vm.addExtraDomain("bad_host.example")).isFalse()
        assertThat(h.vm.addExtraDomain("not a domain")).isFalse()
        assertThat(h.vm.settings.value.extraDomains).isEmpty()
    }

    @Test
    fun extraDomainIsNormalisedAndNotAddedTwice() = runTest {
        val h = Harness(this)
        assertThat(h.vm.addExtraDomain(" https://Login.Example.NET/x ")).isTrue()
        assertThat(h.vm.addExtraDomain("login.example.net")).isTrue()
        assertThat(h.vm.settings.value.extraDomains).containsExactly("login.example.net")
        h.vm.removeExtraDomain("login.example.net")
        assertThat(h.vm.settings.value.extraDomains).isEmpty()
    }

    @Test
    fun zoomIsClampedAndSnappedToStepsOfTen() = runTest {
        val h = Harness(this)
        h.vm.setZoom(37); assertThat(h.vm.settings.value.zoomPercent).isEqualTo(50)
        h.vm.setZoom(260); assertThat(h.vm.settings.value.zoomPercent).isEqualTo(200)
        h.vm.setZoom(124); assertThat(h.vm.settings.value.zoomPercent).isEqualTo(120)
        h.vm.setZoom(125); assertThat(h.vm.settings.value.zoomPercent).isEqualTo(130)
    }

    @Test
    fun startUrlGoesThroughStartUrlInput() = runTest {
        val h = Harness(this)
        assertThat(h.vm.setStartUrl("not a url")).isFalse()
        assertThat(h.vm.settings.value.startUrl).isEqualTo("https://example.com/")
        assertThat(h.vm.setStartUrl("Dashboard.Example.com/lobby")).isTrue()
        assertThat(h.vm.settings.value.startUrl).isEqualTo("https://dashboard.example.com/lobby")
    }

    @Test
    fun startUrlUsesTheChosenScheme() = runTest {
        val h = Harness(this)
        assertThat(h.vm.setStartUrl("10.0.0.5:8123", Scheme.HTTP)).isTrue()
        assertThat(h.vm.settings.value.startUrl).isEqualTo("http://10.0.0.5:8123/")
    }

    @Test
    fun changePinRejectsAWrongOldPin() = runTest {
        val h = Harness(this)
        assertThat(h.vm.changePin("9999", "5678", "5678")).isEqualTo(ChangePinResult.WrongOldPin)
        assertThat(h.savedSecurity).isEmpty()
    }

    @Test
    fun changePinRejectsAMismatchedConfirmation() = runTest {
        val h = Harness(this)
        assertThat(h.vm.changePin("1234", "5678", "5679")).isEqualTo(ChangePinResult.Mismatch)
        assertThat(h.savedSecurity).isEmpty()
    }

    @Test
    fun changePinRejectsANewPinOutsideTheRules() = runTest {
        val h = Harness(this)
        assertThat(h.vm.changePin("1234", "12", "12")).isEqualTo(ChangePinResult.InvalidNewPin)
        assertThat(h.savedSecurity).isEmpty()
    }

    @Test
    fun changePinStoresANewSaltedHash() = runTest {
        val h = Harness(this)
        val old = h.security
        assertThat(h.vm.changePin("1234", "5678", "5678")).isEqualTo(ChangePinResult.Ok)
        val new = h.savedSecurity.single()
        assertThat(new.pinSaltB64).isNotEqualTo(old.pinSaltB64)
        assertThat(new.pinHashB64).isNotEqualTo(old.pinHashB64)
        assertThat(hasher.matches("5678", new)).isTrue()
        assertThat(hasher.matches("1234", new)).isFalse()
    }
}
