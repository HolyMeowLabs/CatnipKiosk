package com.holymeowlabs.catnipkiosk.lockdown

/** What Settings says about starting after a restart, and what it offers to fix it. */
enum class BootOption {
    OFF, AUTOMATIC, READY, NEEDS_PERMISSION, NEEDS_ADB;

    companion object {
        fun of(
            startOnBoot: Boolean,
            startsAsHome: Boolean,
            isDeviceOwner: Boolean,
            canStartFromBackground: Boolean,
            permissionScreenAvailable: Boolean,
        ): BootOption = when {
            !startOnBoot -> OFF
            startsAsHome || isDeviceOwner -> AUTOMATIC
            canStartFromBackground -> READY
            permissionScreenAvailable -> NEEDS_PERMISSION
            else -> NEEDS_ADB
        }
    }
}
