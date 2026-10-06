package com.holymeowlabs.catnipkiosk.cursor

import com.holymeowlabs.catnipkiosk.input.Dir
import kotlin.math.roundToInt

data class CursorStep(val x: Float, val y: Float, val scrollDx: Int, val scrollDy: Int)

/**
 * D-pad cursor position in view pixels. Steps ramp up while a key is held; the cursor never
 * leaves the view, and pushing past an edge turns the step into a scroll in that direction.
 */
class CursorController(val widthPx: Float, val heightPx: Float) {
    /** One press moves about 1% of the screen width. */
    val baseStepPx: Float = (widthPx / 100f).coerceAtLeast(1f)

    var x: Float = widthPx / 2
        private set
    var y: Float = heightPx / 2
        private set

    private val maxX get() = widthPx - 1
    private val maxY get() = heightPx - 1

    fun move(dir: Dir, heldMs: Long): CursorStep {
        val ramp = heldMs.coerceIn(0, RAMP_MS).toFloat() / RAMP_MS
        val step = baseStepPx * (1 + ramp * (MAX_SPEEDUP - 1))
        val (dx, dy) = when (dir) {
            Dir.LEFT -> -step to 0f
            Dir.RIGHT -> step to 0f
            Dir.UP -> 0f to -step
            Dir.DOWN -> 0f to step
        }
        val wantX = x + dx
        val wantY = y + dy
        x = wantX.coerceIn(0f, maxX)
        y = wantY.coerceIn(0f, maxY)
        // Whatever the clamp removed becomes scroll, so holding against an edge keeps scrolling.
        return CursorStep(x, y, scrollDx = (wantX - x).roundToInt(), scrollDy = (wantY - y).roundToInt())
    }

    private companion object {
        const val RAMP_MS = 1_000L
        const val MAX_SPEEDUP = 4f
    }
}
