package com.holymeowlabs.catnipkiosk.lockdown

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BootOptionTest {
    private fun of(
        startOnBoot: Boolean = true,
        startsAsHome: Boolean = false,
        isDeviceOwner: Boolean = false,
        canStartFromBackground: Boolean = false,
        permissionScreenAvailable: Boolean = true,
    ) = BootOption.of(startOnBoot, startsAsHome, isDeviceOwner, canStartFromBackground, permissionScreenAvailable)

    @Test
    fun offWhenTheToggleIsOff() {
        assertThat(of(startOnBoot = false, canStartFromBackground = true)).isEqualTo(BootOption.OFF)
    }

    @Test
    fun homeOrDeviceOwnerStartsByItself() {
        assertThat(of(startsAsHome = true)).isEqualTo(BootOption.AUTOMATIC)
        assertThat(of(isDeviceOwner = true)).isEqualTo(BootOption.AUTOMATIC)
    }

    @Test
    fun grantedPermissionIsReady() {
        assertThat(of(canStartFromBackground = true)).isEqualTo(BootOption.READY)
    }

    @Test
    fun missingPermissionOffersTheSettingsScreenOrAdb() {
        assertThat(of()).isEqualTo(BootOption.NEEDS_PERMISSION)
        assertThat(of(permissionScreenAvailable = false)).isEqualTo(BootOption.NEEDS_ADB)
    }
}
