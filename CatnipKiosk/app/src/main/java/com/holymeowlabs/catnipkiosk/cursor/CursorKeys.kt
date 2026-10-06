package com.holymeowlabs.catnipkiosk.cursor

import android.view.KeyEvent
import com.holymeowlabs.catnipkiosk.input.Dir
import com.holymeowlabs.catnipkiosk.input.KeyMapping

object CursorKeys {
    sealed interface Action {
        data object PassThrough : Action
        data object Consume : Action
        data object Tap : Action
        data class Move(val dir: Dir) : Action
    }

    /** In cursor mode arrows and OK belong to the cursor (both key down and up); nothing else is taken. */
    fun decide(keyCode: Int, action: Int, cursorActive: Boolean): Action {
        if (!cursorActive) return Action.PassThrough
        val dir = KeyMapping.toDir(keyCode)
        val isOk = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        return when {
            dir == null && !isOk -> Action.PassThrough
            action != KeyEvent.ACTION_DOWN -> Action.Consume
            dir != null -> Action.Move(dir)
            else -> Action.Tap
        }
    }
}
