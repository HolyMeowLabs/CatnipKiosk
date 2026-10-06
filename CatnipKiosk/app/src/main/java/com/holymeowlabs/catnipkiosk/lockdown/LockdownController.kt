package com.holymeowlabs.catnipkiosk.lockdown

import android.app.UiModeManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class LockdownTier { SOFT, HARD }

/** How the kiosk can come back after a reboot. Provisional until the hardware spike (plan Task 2). */
enum class BootStrategy { OVERLAY_PERMISSION, HOME_ONLY }

class LockdownController(private val context: Context) {

    /** "Display over other apps" lets the boot receiver start the kiosk when it isn't Home. */
    val bootStrategy = BootStrategy.OVERLAY_PERMISSION

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

    fun canStartFromBackground(): Boolean =
        bootStrategy == BootStrategy.OVERLAY_PERMISSION && Settings.canDrawOverlays(context)

    /** Android's "Display over other apps" screen for this app; null where the device has none (some TVs). */
    fun overlayPermissionIntent(): Intent? =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            .takeIf { it.resolveActivity(context.packageManager) != null }

    fun isTv(): Boolean =
        context.getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    /** Google TV opens its own launcher at boot and on Home even when CatnipKiosk holds the Home role. */
    fun startsAsHome(): Boolean = isHomeApp() && !isTv()
}
