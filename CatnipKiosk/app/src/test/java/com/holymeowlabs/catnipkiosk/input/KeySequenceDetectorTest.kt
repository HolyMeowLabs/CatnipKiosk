package com.holymeowlabs.catnipkiosk.input

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.input.Dir.DOWN
import com.holymeowlabs.catnipkiosk.input.Dir.LEFT
import com.holymeowlabs.catnipkiosk.input.Dir.RIGHT
import com.holymeowlabs.catnipkiosk.input.Dir.UP
import org.junit.Test

class KeySequenceDetectorTest {

    private val secret = listOf(UP, UP, DOWN, DOWN, LEFT, RIGHT, LEFT, RIGHT)

    /** Feeds keys 100 ms apart starting at [startMs]; returns each onKey result. */
    private fun KeySequenceDetector.feed(keys: List<Dir?>, startMs: Long = 0, gapMs: Long = 100) =
        keys.mapIndexed { i, k -> onKey(k, startMs + i * gapMs) }

    @Test
    fun exactSequenceCompletesOnTheLastKeyOnly() {
        val results = KeySequenceDetector().feed(secret)
        assertThat(results.dropLast(1)).doesNotContain(true)
        assertThat(results.last()).isTrue()
    }

    @Test
    fun extraLeadingUpStillMatches() {
        assertThat(KeySequenceDetector().feed(listOf(UP) + secret).last()).isTrue()
    }

    @Test
    fun noiseBeforeTheSequenceStillMatches() {
        assertThat(KeySequenceDetector().feed(listOf(LEFT, DOWN, RIGHT) + secret).last()).isTrue()
    }

    @Test
    fun gapLongerThanTwoSecondsResets() {
        val d = KeySequenceDetector()
        d.feed(secret.take(4))
        assertThat(d.feed(secret.drop(4), startMs = 300 + 2_001).last()).isFalse()
    }

    @Test
    fun gapOfExactlyTwoSecondsIsAllowed() {
        val d = KeySequenceDetector()
        d.feed(secret.take(4))
        assertThat(d.feed(secret.drop(4), startMs = 300 + 2_000).last()).isTrue()
    }

    @Test
    fun nonDirectionKeyResets() {
        val keys: List<Dir?> = secret.take(4) + listOf(null) + secret.drop(4)
        assertThat(KeySequenceDetector().feed(keys).last()).isFalse()
    }

    @Test
    fun wrongKeyMidSequenceDoesNotMatch() {
        val keys = secret.toMutableList().apply { this[5] = UP }
        assertThat(KeySequenceDetector().feed(keys).last()).isFalse()
    }

    @Test
    fun completionResetsSoTheWholeSequenceIsNeededAgain() {
        val d = KeySequenceDetector()
        assertThat(d.feed(secret).last()).isTrue()
        assertThat(d.onKey(RIGHT, 900)).isFalse()
        assertThat(d.feed(secret, startMs = 1_000).last()).isTrue()
    }
}
