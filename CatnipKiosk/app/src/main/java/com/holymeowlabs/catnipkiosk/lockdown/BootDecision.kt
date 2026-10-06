package com.holymeowlabs.catnipkiosk.lockdown

object BootDecision {
    /**
     * Should BootReceiver start the kiosk? Never when the app is Home or device owner: those
     * devices boot into it anyway, and starting it again would launch it twice.
     */
    fun shouldStart(
        startOnBoot: Boolean,
        configured: Boolean,
        isHome: Boolean,
        isDeviceOwner: Boolean,
        canStartFromBackground: Boolean,
    ): Boolean = startOnBoot && configured && !isHome && !isDeviceOwner && canStartFromBackground
}
