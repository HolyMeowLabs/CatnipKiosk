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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
    private val server = MockWebServer()

    @Before
    fun setUp() = runBlocking {
        repo.clearAll()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setHeader("Content-Type", "text/html").setBody("<p>lobby</p>")
        }
        server.start()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() = runBlocking {
        scenario.close()
        server.shutdown()
        repo.clearAll()
    }

    private fun route(): Route? {
        var r: Route? = null
        scenario.onActivity { r = it.route }
        return r
    }

    @Test
    fun happyPathSavesLandsOnTheKioskAndLoadsTheStartPage() = runBlocking {
        compose.waitUntil(5_000) { route() == Route.Setup }
        compose.onNodeWithTag("setup_url").performTextInput("http://Localhost:${server.port}/lobby")
        compose.onNodeWithTag("setup_next").performClick()
        compose.onNodeWithTag("setup_next").performClick() // navigation defaults
        compose.onNodeWithTag("setup_pin").performTextInput("2468")
        compose.onNodeWithTag("setup_pin_confirm").performTextInput("2468")
        compose.onNodeWithTag("setup_next").performClick()

        compose.waitUntil(10_000) { route() == Route.Kiosk }
        assertThat(repo.settings.first()?.startUrl).isEqualTo("http://localhost:${server.port}/lobby")
        assertThat(repo.security.first()).isNotNull()
        // The kiosk must actually load the page it was just configured with.
        assertThat(server.takeRequest(10, TimeUnit.SECONDS)?.path).isEqualTo("/lobby")
    }
}
