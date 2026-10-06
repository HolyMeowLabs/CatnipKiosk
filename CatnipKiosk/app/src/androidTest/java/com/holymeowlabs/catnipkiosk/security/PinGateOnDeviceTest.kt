package com.holymeowlabs.catnipkiosk.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the gate against the device's own crypto provider, which differs from the desktop JVM's. */
@RunWith(AndroidJUnit4::class)
class PinGateOnDeviceTest {
    private val hasher = PinHasher(iterations = 1_000)

    @Test
    fun emptyPinIsWrongOnTheDeviceProvider() {
        val gate = PinGate(hasher) { 1_000_000L }
        assertThat(gate.attempt("", hasher.create("1234"), true).first).isEqualTo(PinResult.Wrong(4))
    }
}
