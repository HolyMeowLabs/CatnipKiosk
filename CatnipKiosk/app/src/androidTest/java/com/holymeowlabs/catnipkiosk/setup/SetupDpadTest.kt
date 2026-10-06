package com.holymeowlabs.catnipkiosk.setup

import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.ui.theme.KioskTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TV users must be able to leave a text field with the D-pad. */
@RunWith(AndroidJUnit4::class)
class SetupDpadTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theUrlFieldHasFocusWhenTheWizardOpens() {
        val vm = SetupViewModel(false, PinHasher(iterations = 1_000), {}, {}, MainScope(), Dispatchers.Default)
        compose.setContent {
            KioskTheme {
                val state by vm.state.collectAsState()
                SetupWizard(vm, state)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("setup_url").assertIsFocused()
    }

    @Test
    fun dpadDownLeavesTheUrlFieldForNext() {
        val vm = SetupViewModel(false, PinHasher(iterations = 1_000), {}, {}, MainScope(), Dispatchers.Default)
        compose.setContent {
            KioskTheme {
                val state by vm.state.collectAsState()
                SetupWizard(vm, state)
            }
        }
        compose.onNodeWithTag("setup_url").requestFocus()
        compose.onNodeWithTag("setup_url").performTextInput("example.com")
        // While the TV keyboard is up it owns the D-pad; the user closes it (Back) first.
        compose.runOnUiThread {
            val imm = compose.activity.getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
        compose.onNodeWithTag("setup_next").assertIsFocused()
    }
}
