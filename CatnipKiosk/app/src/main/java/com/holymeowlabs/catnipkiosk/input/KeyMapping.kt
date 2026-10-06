package com.holymeowlabs.catnipkiosk.input

import android.view.KeyEvent

object KeyMapping {
    fun toDir(keyCode: Int): Dir? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> Dir.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Dir.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Dir.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Dir.RIGHT
        else -> null
    }
}
