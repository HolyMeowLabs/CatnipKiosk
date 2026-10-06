package com.holymeowlabs.catnipkiosk.input

/**
 * Detects the tablet's secret settings gesture: [requiredTaps] taps within
 * [windowMs] inside the square top-left zone. Observes only; touches still
 * reach the page.
 */
class CornerTapDetector(
    private val zoneSizePx: Float,
    private val requiredTaps: Int = 5,
    private val windowMs: Long = 3_000,
) {
    private val taps = ArrayDeque<Long>()

    fun onTap(x: Float, y: Float, atMs: Long): Boolean {
        if (x >= zoneSizePx || y >= zoneSizePx) {
            taps.clear()
            return false
        }
        taps.addLast(atMs)
        while (taps.isNotEmpty() && atMs - taps.first() > windowMs) taps.removeFirst()
        if (taps.size < requiredTaps) return false
        taps.clear()
        return true
    }
}
