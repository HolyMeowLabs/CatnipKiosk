package com.holymeowlabs.catnipkiosk.lockdown

/** The device-policy operations hard lockdown needs; [DpmOps] is the real implementation. */
interface DeviceOwnerOps {
    val packageName: String
    fun isDeviceOwner(): Boolean
    fun setLockTaskPackages(packages: List<String>)
    fun disableLockTaskFeatures()
    fun setHomePreferred(preferred: Boolean)
    fun setStayOnWhilePluggedIn(on: Boolean)
    fun setSafeBootDisallowed(disallowed: Boolean)
    fun setDebuggingDisallowed(disallowed: Boolean)
    fun startLockTask()
    fun stopLockTask()
    fun clearDeviceOwner()
}

/**
 * Spec §8 hard lockdown. Factory reset is deliberately left allowed: it is the recovery path
 * for a forgotten PIN.
 */
class HardLockdown(private val ops: DeviceOwnerOps, private val blockDebugging: Boolean = true) {

    /** Idempotent; does nothing unless the app is device owner. */
    fun apply() {
        if (!ops.isDeviceOwner()) return
        ops.setLockTaskPackages(listOf(ops.packageName))
        ops.disableLockTaskFeatures()
        ops.setHomePreferred(true)
        ops.setStayOnWhilePluggedIn(true)
        ops.setSafeBootDisallowed(true)
        // USB debugging would let anyone with a cable and adb leave the kiosk. Debug builds keep it,
        // because blocking it also cuts the adb connection their tests run over.
        if (blockDebugging) ops.setDebuggingDisallowed(true)
        ops.startLockTask()
    }

    /** Undoes everything, releasing device owner last so the app can then be uninstalled normally. */
    fun remove() {
        ops.stopLockTask()
        ops.setHomePreferred(false)
        ops.setSafeBootDisallowed(false)
        ops.setDebuggingDisallowed(false)
        ops.setStayOnWhilePluggedIn(false)
        ops.setLockTaskPackages(emptyList())
        ops.clearDeviceOwner()
    }

    /** Settings › Exit app: leave lock task for this session only; policies stay in place. */
    fun suspendForSession() = ops.stopLockTask()
}
