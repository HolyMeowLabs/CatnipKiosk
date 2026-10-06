package com.holymeowlabs.catnipkiosk.lockdown

/** What Settings can offer for soft lockdown's Home app. */
enum class HomeOption {
    IS_HOME, CAN_REQUEST, UNAVAILABLE,

    /**
     * Google TV: the Home key opens the TV launcher even when CatnipKiosk holds the Home role
     * (observed on the Google TV emulator, API 36; re-check on real hardware).
     */
    TV_HOME_KEY_LEAVES;

    companion object {
        fun of(isTv: Boolean, isHomeApp: Boolean, canRequestHome: Boolean): HomeOption = when {
            isTv -> TV_HOME_KEY_LEAVES
            isHomeApp -> IS_HOME
            canRequestHome -> CAN_REQUEST
            else -> UNAVAILABLE
        }
    }
}
