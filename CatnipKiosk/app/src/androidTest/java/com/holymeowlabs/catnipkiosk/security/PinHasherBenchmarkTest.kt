package com.holymeowlabs.catnipkiosk.security

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures one PIN hash at the default iteration count. Target on the slowest
 * supported device: median 100–300 ms (fast enough to feel instant, slow enough
 * to make brute force of a stolen hash expensive). Results go to logcat under
 * the tag below; the assertion only guards against an unusable setting.
 */
@RunWith(AndroidJUnit4::class)
class PinHasherBenchmarkTest {

    @Test
    fun medianHashTimeAtDefaultIterations() {
        val hasher = PinHasher()
        val salt = hasher.newSalt()
        repeat(3) { hasher.hash("12345678", salt) }

        val timesMs = (1..10).map {
            val start = SystemClock.elapsedRealtimeNanos()
            hasher.hash("12345678", salt)
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        }.sorted()
        val median = (timesMs[4] + timesMs[5]) / 2

        Log.i(
            TAG,
            "iterations=${PinHasher.DEFAULT_ITERATIONS} median=%.1fms min=%.1fms max=%.1fms device=%s/%s api=%d abi=%s"
                .format(median, timesMs.first(), timesMs.last(), Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.first()),
        )
        assertThat(median).isLessThan(2_000.0)
    }

    private companion object {
        const val TAG = "PinHasherBenchmark"
    }
}
