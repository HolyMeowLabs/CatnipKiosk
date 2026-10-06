package com.holymeowlabs.catnipkiosk.cursor

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/** A touch tap delivered straight to [view], so the page receives a real click with user activation. */
object SyntheticTap {
    fun dispatch(view: View, x: Float, y: Float) {
        val downAt = SystemClock.uptimeMillis()
        for ((action, at) in listOf(MotionEvent.ACTION_DOWN to downAt, MotionEvent.ACTION_UP to downAt + TAP_MS)) {
            val event = MotionEvent.obtain(downAt, at, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private const val TAP_MS = 50L
}
