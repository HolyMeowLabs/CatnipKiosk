package com.holymeowlabs.catnipkiosk.setup

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.MainActivity
import com.holymeowlabs.catnipkiosk.app.Route
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on the tablet AVD. */
@RunWith(AndroidJUnit4::class)
class SetupWizardTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = SettingsRepository.get(context)
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() = runBlocking {
        repo.clearAll()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() = runBlocking {
        scenario.close()
        repo.clearAll()
    }

    private fun route(): Route? {
        var r: Route? = null
        scenario.onActivity { r = it.route }
        return r
    }

    @Test
    fun happyPathSavesAndLandsOnTheKiosk() = runBlocking {
        compose.waitUntil(5_000) { route() == Route.Setup }
        compose.onNodeWithTag("setup_url").performTextInput("Example.com/lobby")
        compose.onNodeWithTag("setup_next").performClick()
        compose.onNodeWithTag("setup_next").performClick() // navigation defaults
        compose.onNodeWithTag("setup_pin").performTextInput("2468")
        compose.onNodeWithTag("setup_pin_confirm").performTextInput("2468")
        compose.onNodeWithTag("setup_next").performClick()

        compose.waitUntil(10_000) { route() == Route.Kiosk }
        assertThat(repo.settings.first()?.startUrl).isEqualTo("https://example.com/lobby")
        assertThat(repo.security.first()).isNotNull()
    }
}
