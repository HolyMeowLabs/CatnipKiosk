package com.holymeowlabs.catnipkiosk.lockdown

/** What Settings › Exit app does: closing a kiosk that Android would reopen at once gets nowhere. */
enum class ExitPlan {
    CLOSE, OPEN_SYSTEM_SETTINGS;

    companion object {
        fun of(isHardLockdown: Boolean, isHomeApp: Boolean): ExitPlan =
            if (isHardLockdown || isHomeApp) OPEN_SYSTEM_SETTINGS else CLOSE
    }
}
