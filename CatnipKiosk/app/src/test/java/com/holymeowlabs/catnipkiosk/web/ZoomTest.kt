package com.holymeowlabs.catnipkiosk.web

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZoomTest {
    @Test
    fun hundredPercentUsesWebViewsDefaultScale() {
        assertThat(Zoom.initialScalePercent(100, density = 2f)).isEqualTo(0)
    }

    @Test
    fun otherZoomsAreScaledByScreenDensity() {
        assertThat(Zoom.initialScalePercent(150, density = 2f)).isEqualTo(300)
        assertThat(Zoom.initialScalePercent(50, density = 1.5f)).isEqualTo(75)
    }
}
