package com.holymeowlabs.catnipkiosk.lockdown

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

enum class LockdownTier { SOFT, HARD }

/** How the kiosk can come back after a reboot. Provisional until the hardware spike (plan Task 2). */
enum class BootStrategy { OVERLAY_PERMISSION, HOME_ONLY }

class LockdownController(private val context: Context) {

    /** No overlay permission is declared, so only Home or device owner brings the kiosk back after boot. */
    val bootStrategy = BootStrategy.HOME_ONLY

    fun tier(): LockdownTier =
        if (context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)) {
            LockdownTier.HARD
        } else {
            LockdownTier.SOFT
        }

    fun isHomeApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Null where the device offers no way to choose a Home app (common on Google TV). */
    fun homeRoleRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java) ?: return null
            return if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) roles.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
        }
        val settings = Intent(Settings.ACTION_HOME_SETTINGS)
        return settings.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Only meaningful with [BootStrategy.OVERLAY_PERMISSION]; false while the permission is not declared. */
    fun canStartFromBackground(): Boolean =
        bootStrategy == BootStrategy.OVERLAY_PERMISSION && Settings.canDrawOverlays(context)
}
