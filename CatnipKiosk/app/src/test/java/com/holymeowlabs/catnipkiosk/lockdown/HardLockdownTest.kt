package com.holymeowlabs.catnipkiosk.lockdown

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HardLockdownTest {

    /** Records calls and models the resulting device state. */
    private class FakeOps(var deviceOwner: Boolean = true) : DeviceOwnerOps {
        val calls = mutableListOf<String>()
        var allowlist = emptyList<String>()
        var home = false
        var stayOn = false
        var safeBoot = false
        var lockTaskRunning = false
        var featuresNone = false
        var debugBlocked = false

        override val packageName = "com.holymeowlabs.catnipkiosk"
        override fun isDeviceOwner() = deviceOwner
        override fun setLockTaskPackages(packages: List<String>) { calls += "packages"; allowlist = packages }
        override fun disableLockTaskFeatures() { calls += "features"; featuresNone = true }
        override fun setHomePreferred(preferred: Boolean) { calls += "home=$preferred"; home = preferred }
        override fun setStayOnWhilePluggedIn(on: Boolean) { calls += "stayOn=$on"; stayOn = on }
        override fun setSafeBootDisallowed(disallowed: Boolean) { calls += "safeBoot=$disallowed"; safeBoot = disallowed }
        override fun startLockTask() { calls += "start"; check(packageName in allowlist) { "not allowlisted" }; lockTaskRunning = true }
        override fun stopLockTask() { calls += "stop"; lockTaskRunning = false }
        override fun setDebuggingDisallowed(disallowed: Boolean) { calls += "debug=$disallowed"; debugBlocked = disallowed }
        override fun clearDeviceOwner() { calls += "clearOwner"; deviceOwner = false }
    }

    @Test
    fun applyLocksTheDeviceToTheKiosk() {
        val ops = FakeOps()
        HardLockdown(ops).apply()
        assertThat(ops.allowlist).containsExactly(ops.packageName)
        assertThat(ops.featuresNone).isTrue()
        assertThat(ops.home).isTrue()
        assertThat(ops.stayOn).isTrue()
        assertThat(ops.safeBoot).isTrue()
        assertThat(ops.debugBlocked).isTrue()
        assertThat(ops.lockTaskRunning).isTrue()
        assertThat(ops.calls.last()).isEqualTo("start")
    }

    @Test
    fun debugBuildsKeepUsbDebuggingSoTheyCanBeTestedOverAdb() {
        val ops = FakeOps()
        HardLockdown(ops, blockDebugging = false).apply()
        assertThat(ops.debugBlocked).isFalse()
        assertThat(ops.lockTaskRunning).isTrue()
    }

    @Test
    fun applyDoesNothingWithoutDeviceOwner() {
        val ops = FakeOps(deviceOwner = false)
        HardLockdown(ops).apply()
        assertThat(ops.calls).isEmpty()
    }

    @Test
    fun removeReversesEverythingAndReleasesDeviceOwnerLast() {
        val ops = FakeOps()
        HardLockdown(ops).apply()
        ops.calls.clear()
        HardLockdown(ops).remove()
        assertThat(ops.lockTaskRunning).isFalse()
        assertThat(ops.allowlist).isEmpty()
        assertThat(ops.home).isFalse()
        assertThat(ops.stayOn).isFalse()
        assertThat(ops.safeBoot).isFalse()
        assertThat(ops.debugBlocked).isFalse()
        assertThat(ops.deviceOwner).isFalse()
        assertThat(ops.calls.first()).isEqualTo("stop")
        assertThat(ops.calls.last()).isEqualTo("clearOwner")
    }

    @Test
    fun suspendingForTheSessionOnlyStopsLockTask() {
        val ops = FakeOps()
        HardLockdown(ops).apply()
        ops.calls.clear()
        HardLockdown(ops).suspendForSession()
        assertThat(ops.calls).containsExactly("stop")
        assertThat(ops.deviceOwner).isTrue()
    }
}
