package com.holymeowlabs.catnipkiosk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmokeTest {
    @Test
    fun applicationIdIsTheRegisteredPackage() {
        assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.holymeowlabs.catnipkiosk")
    }
}
