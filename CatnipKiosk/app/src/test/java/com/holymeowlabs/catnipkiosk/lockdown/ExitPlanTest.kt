package com.holymeowlabs.catnipkiosk.lockdown

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExitPlanTest {
    @Test
    fun whenTheKioskWouldJustReopenExitGoesToSystemSettings() {
        assertThat(ExitPlan.of(isHardLockdown = true, isHomeApp = false)).isEqualTo(ExitPlan.OPEN_SYSTEM_SETTINGS)
        assertThat(ExitPlan.of(isHardLockdown = false, isHomeApp = true)).isEqualTo(ExitPlan.OPEN_SYSTEM_SETTINGS)
    }

    @Test
    fun otherwiseExitJustCloses() {
        assertThat(ExitPlan.of(isHardLockdown = false, isHomeApp = false)).isEqualTo(ExitPlan.CLOSE)
    }
}
