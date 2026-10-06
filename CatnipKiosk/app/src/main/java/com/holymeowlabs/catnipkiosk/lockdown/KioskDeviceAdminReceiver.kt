package com.holymeowlabs.catnipkiosk.lockdown

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/** Device-owner admin, provisioned by the user with `adb shell dpm set-device-owner` (see the setup steps screen). */
class KioskDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, KioskDeviceAdminReceiver::class.java)
    }
}
