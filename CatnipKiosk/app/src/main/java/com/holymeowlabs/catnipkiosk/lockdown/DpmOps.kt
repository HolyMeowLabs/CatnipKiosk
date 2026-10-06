package com.holymeowlabs.catnipkiosk.lockdown

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.holymeowlabs.catnipkiosk.MainActivity

/** [DeviceOwnerOps] backed by DevicePolicyManager, acting on [activity]'s task. */
class DpmOps(private val activity: Activity) : DeviceOwnerOps {
    private val dpm = activity.getSystemService(DevicePolicyManager::class.java)
    private val admin = KioskDeviceAdminReceiver.component(activity)

    override val packageName: String = activity.packageName

    override fun isDeviceOwner() = dpm.isDeviceOwnerApp(packageName)

    override fun setLockTaskPackages(packages: List<String>) = dpm.setLockTaskPackages(admin, packages.toTypedArray())

    override fun disableLockTaskFeatures() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }
    }

    override fun setHomePreferred(preferred: Boolean) {
        if (preferred) {
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, ComponentName(activity, MainActivity::class.java))
        } else {
            dpm.clearPackagePersistentPreferredActivities(admin, packageName)
        }
    }

    override fun setStayOnWhilePluggedIn(on: Boolean) {
        // 7 = AC | USB | wireless.
        dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (on) "7" else "0")
    }

    override fun setSafeBootDisallowed(disallowed: Boolean) {
        if (disallowed) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        }
    }

    override fun setDebuggingDisallowed(disallowed: Boolean) {
        if (disallowed) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
    }

    override fun startLockTask() {
        val am = activity.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) activity.startLockTask()
    }

    override fun stopLockTask() {
        val am = activity.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) activity.stopLockTask()
    }

    /** Deprecated, but still the documented way for a device owner to release itself. */
    @Suppress("DEPRECATION")
    override fun clearDeviceOwner() = dpm.clearDeviceOwnerApp(packageName)
}
