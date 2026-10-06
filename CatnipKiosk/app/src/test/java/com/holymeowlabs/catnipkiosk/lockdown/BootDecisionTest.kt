package com.holymeowlabs.catnipkiosk.lockdown

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class BootDecisionTest {
    @Test
    fun startsOnlyWhenWantedConfiguredNotAlreadyHomeOrOwnerAndAllowedToStart() {
        val bools = listOf(false, true)
        for (startOnBoot in bools) for (configured in bools) for (isHome in bools)
            for (isOwner in bools) for (canStart in bools) {
                val expected = startOnBoot && configured && !isHome && !isOwner && canStart
                assertWithMessage("startOnBoot=$startOnBoot configured=$configured home=$isHome owner=$isOwner canStart=$canStart")
                    .that(BootDecision.shouldStart(startOnBoot, configured, isHome, isOwner, canStart))
                    .isEqualTo(expected)
            }
    }
}
