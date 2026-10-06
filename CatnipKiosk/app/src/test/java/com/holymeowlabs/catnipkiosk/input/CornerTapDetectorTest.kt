package com.holymeowlabs.catnipkiosk.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CornerTapDetectorTest {

    private fun detector() = CornerTapDetector(zoneSizePx = 200f)

    @Test
    fun fiveTapsInZoneWithinThreeSecondsTriggerOnTheFifth() {
        val d = detector()
        val results = (0 until 5).map { d.onTap(10f, 10f, it * 500L) }
        assertThat(results).containsExactly(false, false, false, false, true).inOrder()
    }

    @Test
    fun tapsSpreadOverMoreThanThreeSecondsDoNotTrigger() {
        val d = detector()
        assertThat((0 until 5).map { d.onTap(10f, 10f, it * 760L) }.last()).isFalse()
    }

    @Test
    fun tapOutsideZoneResetsTheCount() {
        val d = detector()
        repeat(4) { d.onTap(10f, 10f, it * 100L) }
        d.onTap(500f, 500f, 450)
        assertThat(d.onTap(10f, 10f, 500)).isFalse()
    }

    @Test
    fun zoneEdgeIsInsideAndJustBeyondIsOutside() {
        val inside = detector()
        assertThat((0 until 5).map { inside.onTap(199.9f, 0f, it * 100L) }.last()).isTrue()
        val outside = detector()
        assertThat((0 until 5).map { outside.onTap(200f, 0f, it * 100L) }.last()).isFalse()
    }

    @Test
    fun aSixthTapAfterTriggeringStartsANewCount() {
        val d = detector()
        repeat(5) { d.onTap(10f, 10f, it * 100L) }
        assertThat(d.onTap(10f, 10f, 600)).isFalse()
    }
}
