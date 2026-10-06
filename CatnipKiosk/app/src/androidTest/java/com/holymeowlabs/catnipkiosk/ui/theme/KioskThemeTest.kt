package com.holymeowlabs.catnipkiosk.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskThemeTest {
    @get:Rule
    val compose = createComposeRule()

    /** Plain Text inherits LocalContentColor; outside a Surface it is black on the dark ground. */
    @Test
    fun textInsideTheThemeIsLightOnTheDarkGround() {
        var content = Color.Unspecified
        compose.setContent { KioskTheme { content = LocalContentColor.current } }
        compose.waitForIdle()
        assertThat(content).isEqualTo(TextColor)
    }
}
