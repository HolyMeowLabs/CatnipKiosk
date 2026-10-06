package com.holymeowlabs.catnipkiosk.settingsui

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = SettingsRepository.get(context)
    private val scope = MainScope()
    private val initial = KioskSettings(startUrl = "https://example.com/")

    @Before
    fun setUp() = runBlocking { repo.clearAll(); repo.saveSettings(initial) }

    @After
    fun tearDown() = runBlocking { scope.cancel(); repo.clearAll() }

    private fun show(isTv: Boolean) {
        val vm = SettingsViewModel(
            initial = initial,
            security = { null },
            hasher = PinHasher(iterations = 1_000),
            saveSettings = repo::saveSettings,
            saveSecurity = repo::saveSecurity,
            scope = scope,
            work = Dispatchers.Default,
        )
        compose.setContent { SettingsScreen(vm, isTv, onBack = {}, onExitApp = {}, onReloadNow = {}) }
    }

    @Test
    fun tvRailItemsAreReachedInOrderWithTheDpad() {
        show(isTv = true)
        compose.onNodeWithTag("rail_StartPage").requestFocus()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        SettingsSection.entries.drop(1).forEach { section ->
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitForIdle()
            compose.onNodeWithTag("rail_${section.name}").assertIsFocused()
        }
    }

    @Test
    fun togglingIncludeSubdomainsIsPersisted() = runBlocking {
        show(isTv = false)
        compose.onNodeWithTag("include_subdomains").performClick()
        compose.waitForIdle()
        var stored = repo.settings.first()
        repeat(50) {
            if (stored?.includeSubdomains == false) return@repeat
            delay(100)
            stored = repo.settings.first()
        }
        assertThat(stored?.includeSubdomains).isFalse()
    }
}
