package com.holymeowlabs.catnipkiosk.cursor

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.input.Dir
import org.junit.Test

class CursorControllerTest {

    private val w = 1920f
    private val h = 1080f

    @Test
    fun startsAtTheCentre() {
        val c = CursorController(w, h)
        assertThat(c.x).isEqualTo(w / 2)
        assertThat(c.y).isEqualTo(h / 2)
    }

    @Test
    fun aTapMovesByTheBaseStep() {
        val c = CursorController(w, h)
        val step = c.move(Dir.RIGHT, heldMs = 0)
        assertThat(step.x).isEqualTo(w / 2 + c.baseStepPx)
        assertThat(step.y).isEqualTo(h / 2)
        assertThat(c.move(Dir.UP, heldMs = 0).y).isEqualTo(h / 2 - c.baseStepPx)
    }

    @Test
    fun holdingSpeedsUpToACap() {
        fun stepAfter(heldMs: Long) = CursorController(w, h).let { it.move(Dir.RIGHT, heldMs).x - w / 2 }
        assertThat(stepAfter(500)).isGreaterThan(stepAfter(0))
        assertThat(stepAfter(1_000)).isGreaterThan(stepAfter(500))
        assertThat(stepAfter(5_000)).isEqualTo(stepAfter(1_000))
    }

    @Test
    fun neverLeavesTheScreen() {
        val c = CursorController(w, h)
        repeat(500) { c.move(Dir.LEFT, 5_000); c.move(Dir.UP, 5_000) }
        assertThat(c.x).isEqualTo(0f)
        assertThat(c.y).isEqualTo(0f)
        repeat(500) { c.move(Dir.RIGHT, 5_000); c.move(Dir.DOWN, 5_000) }
        assertThat(c.x).isEqualTo(w - 1)
        assertThat(c.y).isEqualTo(h - 1)
    }

    @Test
    fun pushingAgainstAnEdgeScrollsInsteadOfMoving() {
        val c = CursorController(w, h)
        repeat(500) { c.move(Dir.RIGHT, 5_000) }
        val right = c.move(Dir.RIGHT, 0)
        assertThat(right.x).isEqualTo(w - 1)
        assertThat(right.scrollDx).isGreaterThan(0)
        assertThat(right.scrollDy).isEqualTo(0)

        repeat(500) { c.move(Dir.UP, 5_000) }
        val up = c.move(Dir.UP, 0)
        assertThat(up.y).isEqualTo(0f)
        assertThat(up.scrollDy).isLessThan(0)
    }

    @Test
    fun movingInsideTheScreenDoesNotScroll() {
        val step = CursorController(w, h).move(Dir.DOWN, 0)
        assertThat(step.scrollDx).isEqualTo(0)
        assertThat(step.scrollDy).isEqualTo(0)
    }
}
