package com.holymeowlabs.catnipkiosk.input

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeyMappingTest {
    @Test
    fun dpadArrowsMapToDirections() {
        assertThat(KeyMapping.toDir(KeyEvent.KEYCODE_DPAD_UP)).isEqualTo(Dir.UP)
        assertThat(KeyMapping.toDir(KeyEvent.KEYCODE_DPAD_DOWN)).isEqualTo(Dir.DOWN)
        assertThat(KeyMapping.toDir(KeyEvent.KEYCODE_DPAD_LEFT)).isEqualTo(Dir.LEFT)
        assertThat(KeyMapping.toDir(KeyEvent.KEYCODE_DPAD_RIGHT)).isEqualTo(Dir.RIGHT)
    }

    @Test
    fun otherKeysMapToNothing() {
        assertThat(KeyMapping.toDir(KeyEvent.KEYCODE_DPAD_CENTER)).isNull()
        assertThat(KeyMapping.toDir(KeyEvent.KEYCODE_BACK)).isNull()
        assertThat(KeyMapping.toDir(KeyEvent.KEYCODE_5)).isNull()
    }
}
