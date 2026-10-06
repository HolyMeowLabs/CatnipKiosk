package com.holymeowlabs.catnipkiosk.cursor

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.input.Dir
import org.junit.Test

class CursorKeysTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Test
    fun arrowsMoveTheCursorAndNeverReachThePage() {
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_DPAD_LEFT, down, cursorActive = true))
            .isEqualTo(CursorKeys.Action.Move(Dir.LEFT))
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_DPAD_LEFT, up, cursorActive = true))
            .isEqualTo(CursorKeys.Action.Consume)
    }

    @Test
    fun okAndEnterTapAtTheCursor() {
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_DPAD_CENTER, down, cursorActive = true)).isEqualTo(CursorKeys.Action.Tap)
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_ENTER, down, cursorActive = true)).isEqualTo(CursorKeys.Action.Tap)
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_DPAD_CENTER, up, cursorActive = true)).isEqualTo(CursorKeys.Action.Consume)
    }

    @Test
    fun otherKeysAndCursorOffPassThrough() {
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_BACK, down, cursorActive = true)).isEqualTo(CursorKeys.Action.PassThrough)
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_5, down, cursorActive = true)).isEqualTo(CursorKeys.Action.PassThrough)
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_DPAD_LEFT, down, cursorActive = false)).isEqualTo(CursorKeys.Action.PassThrough)
        assertThat(CursorKeys.decide(KeyEvent.KEYCODE_DPAD_CENTER, down, cursorActive = false)).isEqualTo(CursorKeys.Action.PassThrough)
    }
}
