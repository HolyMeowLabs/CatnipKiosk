package com.holymeowlabs.catnipkiosk.lockdown

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeOptionTest {
    @Test
    fun tvNeverOffersTheHomeRoleBecauseTheHomeKeyIgnoresIt() {
        // Observed on the Google TV emulator: with the role held, Home still opens the TV launcher.
        assertThat(HomeOption.of(isTv = true, isHomeApp = false, canRequestHome = true)).isEqualTo(HomeOption.TV_HOME_KEY_LEAVES)
        assertThat(HomeOption.of(isTv = true, isHomeApp = true, canRequestHome = true)).isEqualTo(HomeOption.TV_HOME_KEY_LEAVES)
    }

    @Test
    fun tabletOffersTheRoleOrConfirmsIt() {
        assertThat(HomeOption.of(isTv = false, isHomeApp = true, canRequestHome = true)).isEqualTo(HomeOption.IS_HOME)
        assertThat(HomeOption.of(isTv = false, isHomeApp = false, canRequestHome = true)).isEqualTo(HomeOption.CAN_REQUEST)
        assertThat(HomeOption.of(isTv = false, isHomeApp = false, canRequestHome = false)).isEqualTo(HomeOption.UNAVAILABLE)
    }
}
