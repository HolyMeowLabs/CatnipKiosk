package com.holymeowlabs.catnipkiosk.web

import kotlin.math.roundToInt

/**
 * Zoom is applied as WebView's initial page scale (spec §7; provisional until the hardware spike).
 * WebView's initial scale is a percentage of physical pixels, so the setting is multiplied by the
 * screen density; 100 % keeps WebView's own default (0).
 */
object Zoom {
    fun initialScalePercent(zoomPercent: Int, density: Float): Int =
        if (zoomPercent == 100) 0 else (zoomPercent * density).roundToInt()
}
